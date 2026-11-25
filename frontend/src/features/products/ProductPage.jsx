import { useEffect, useRef, useState } from 'react'
import { productClient } from '../../api/products.js'
import { categoryClient } from '../../api/categories.js'
import { Button } from '../../components/ui/Button.jsx'
import { Card } from '../../components/ui/Card.jsx'
import { EmptyState } from '../../components/ui/EmptyState.jsx'
import { LoadingState } from '../../components/ui/LoadingState.jsx'
import { PageHeader } from '../../components/ui/PageHeader.jsx'
import { ListError } from '../../components/ui/ListError.jsx'
import { RecordFilters } from '../../components/ui/RecordFilters.jsx'
import { RecordPagination } from '../../components/ui/RecordPagination.jsx'
import { StatusBadge } from '../../components/ui/StatusBadge.jsx'
import { TextInput } from '../../components/ui/TextInput.jsx'
import { CategoryDialog } from '../categories/CategoryDialog.jsx'
import { formatVietnameseDate } from '../../format/vietnameseDate.js'
import { productValues, validateProduct } from './productForm.js'
import './products.css'

const blank = { sku: '', name: '', categoryId: '', brandName: '', modelCode: '', description: '', minStockLevel: '0', sellingPrice: '' }
const titles = { create: 'Tạo sản phẩm', detail: 'Chi tiết sản phẩm', edit: 'Sửa sản phẩm', price: 'Đổi giá bán', deactivate: 'Ngừng kinh doanh' }
const permissionByAction = { edit: 'PRODUCT_UPDATE', price: 'PRODUCT_PRICE_UPDATE', deactivate: 'PRODUCT_DEACTIVATE' }
const digits = new Intl.NumberFormat('vi-VN')
const money = value => `${digits.format(BigInt(value))} ₫`

function errorMessage(failure, action) {
  switch (failure.status) {
    case 400: return 'Dữ liệu chưa hợp lệ. Vui lòng kiểm tra các trường.'
    case 401: return 'Phiên đăng nhập đã kết thúc. Vui lòng đăng nhập lại.'
    case 403: return 'Truy cập bị từ chối. Tài khoản hiện không có quyền thực hiện thao tác này.'
    case 404: return 'Không tìm thấy sản phẩm hoặc danh mục đã chọn.'
    case 409: return action === 'create'
      ? 'Chưa thể tạo sản phẩm. Kiểm tra SKU, danh mục và điều kiện khởi tạo tồn kho.'
      : 'Dữ liệu đang xung đột. Kiểm tra SKU hoặc trạng thái danh mục rồi thử lại.'
    case 0: return 'Không thể kết nối đến máy chủ.'
    default: return 'Chưa thể xử lý yêu cầu. Vui lòng thử lại.'
  }
}

function CategoryPicker({ client, current, value, onChange, error }) {
  const [query, setQuery] = useState({ search: '', page: 0 })
  const [attempt, retry] = useState(0)
  const [settled, setSettled] = useState(null)
  const { search, page } = query
  const response = settled?.query === query && settled.attempt === attempt ? settled : null
  const result = response?.result
  const failure = response?.failure

  useEffect(() => {
    const controller = new AbortController()
    const { search, page } = query
    client.list({ active: 'true', q: search.trim(), page, size: 20 }, { signal: controller.signal }).then(result => {
      if (controller.signal.aborted) return
      if (page > 0 && page >= result.totalPages) {
        setQuery(previous => previous === query ? { ...query, page: Math.max(0, result.totalPages - 1) } : previous)
      } else {
        setSettled({ query, attempt, result })
      }
    }).catch(failure => {
      if (!controller.signal.aborted) setSettled({ query, attempt, failure })
    })
    return () => controller.abort()
  }, [client, query, attempt])

  function setPage(page) { setQuery(previous => ({ ...previous, page })) }

  const choices = result?.items ?? []
  const chosen = choices.some(item => item.id === value)
  return <div className="csm-field">
    <TextInput label="Tìm danh mục đang sử dụng" value={search} maxLength={120}
      onChange={event => setQuery({ search: event.target.value, page: 0 })} />
    <label htmlFor="product-category">Danh mục</label>
    <select id="product-category" name="categoryId" value={value} onChange={onChange} aria-invalid={Boolean(error) || undefined}
      aria-describedby={error ? 'product-category-error' : undefined}>
      <option value="">Chọn danh mục</option>
      {value && !chosen && <option value={value}>{current?.id === value ? current.name : `Danh mục #${value}`}</option>}
      {choices.map(item => <option key={item.id} value={item.id}>{item.code} · {item.name}</option>)}
    </select>
    {error && <p id="product-category-error" className="csm-field-error">{error}</p>}
    {failure ? <ListError status={failure.status} message={errorMessage(failure)} onRetry={() => retry(count => count + 1)} />
      : !result ? <p role="status">Đang tải danh mục…</p> : <>
      {choices.length === 0 && <p>Không tìm thấy danh mục đang sử dụng.</p>}
      {result.totalPages > 1 && <div className="product-actions">
        <Button variant="secondary" disabled={page === 0} onClick={() => setPage(page - 1)}>Danh mục trước</Button>
        <Button variant="secondary" disabled={page + 1 >= result.totalPages} onClick={() => setPage(page + 1)}>Danh mục sau</Button>
      </div>}
    </>}
  </div>
}

export function ProductPage({ client = productClient, categories = categoryClient, permissions = [] }) {
  const [search, setSearch] = useState('')
  const [query, setQuery] = useState({ q: '', active: '', page: 0, size: 20 })
  const [refresh, refreshList] = useState(0)
  const [list, setList] = useState(null)
  const [modal, setModal] = useState(null)
  const [values, setValues] = useState(blank)
  const [fields, setFields] = useState({})
  const [failure, setFailure] = useState(null)
  const [notice, setNotice] = useState('')
  const [saving, setSaving] = useState(false)
  const pendingWrite = useRef(false)
  const detail = useRef(null)
  const timer = useRef(null)
  const form = useRef(null)
  const settled = list?.query === query && list.refresh === refresh
  const result = settled ? list.result : null
  const error = settled ? list.error : null
  const canPreviewCreate = Boolean(result && permissions.includes('PRODUCT_CREATE') && permissions.includes('PRODUCT_PRICE_UPDATE'))

  function canWrite(action) {
    return Boolean(permissionByAction[action] && result?.allowedActions?.includes(permissionByAction[action]))
  }

  useEffect(() => {
    const controller = new AbortController()
    client.list(query, { signal: controller.signal }).then(result => {
      if (controller.signal.aborted) return
      if (query.page > 0 && query.page >= result.totalPages) {
        setQuery(previous => previous === query ? { ...query, page: Math.max(0, result.totalPages - 1) } : previous)
      } else setList({ query, refresh, result })
    }).catch(error => {
      if (!controller.signal.aborted) setList({ query, refresh, error })
    })
    return () => controller.abort()
  }, [client, query, refresh])
  useEffect(() => () => { clearTimeout(timer.current); detail.current?.abort() }, [])

  function apply(value, active) {
    clearTimeout(timer.current)
    const q = value.trim()
    setQuery(previous => {
      const status = active ?? previous.active
      return q === previous.q && status === previous.active ? previous : { ...previous, q, active: status, page: 0 }
    })
  }
  function changeSearch(value) {
    setSearch(value)
    clearTimeout(timer.current)
    timer.current = setTimeout(() => apply(value), 350)
  }
  function change(field, value) { setValues(previous => ({ ...previous, [field]: value })) }

  async function open(action, id) {
    if (action === 'create' ? !canPreviewCreate : action !== 'detail' && !canWrite(action)) return
    detail.current?.abort()
    setFields({}); setFailure(null); setNotice(''); setValues(blank)
    if (action === 'create') { setModal({ action }); return }
    const controller = new AbortController()
    detail.current = controller
    setModal({ action, loading: true })
    try {
      const record = await client.detail(id, { signal: controller.signal })
      if (controller.signal.aborted) return
      setValues({ ...blank, ...Object.fromEntries(Object.keys(blank).map(key => [key, record[key] ?? ''])) })
      setModal({ action, record })
    } catch (error) {
      if (!controller.signal.aborted) { setFailure(error); setModal({ action }) }
    }
  }

  function close() {
    if (pendingWrite.current) return
    detail.current?.abort()
    setModal(null)
  }
  async function submit(event) {
    event.preventDefault()
    const { action, record } = modal
    // Create has no allowed action while DEP-001 is open.
    if (!canWrite(action) || !record || pendingWrite.current) return
    const data = productValues(values)
    const errors = validateProduct(data, action)
    setFields(errors); setFailure(null)
    if (Object.keys(errors).length) {
      form.current.elements.namedItem(Object.keys(errors)[0])?.focus()
      return
    }
    pendingWrite.current = true; setSaving(true)
    try {
      if (action === 'edit') await client.update(record.id, data)
      else if (action === 'price') await client.updatePrice(record.id, data.sellingPrice)
      else await client.deactivate(record.id)
      setModal(null)
      setNotice(action === 'deactivate' ? 'Đã ngừng kinh doanh sản phẩm.' : 'Đã lưu sản phẩm.')
      refreshList(value => value + 1)
    } catch (error) {
      setFailure(error)
      if (error.status === 400) setFields(Object.fromEntries((error.fields ?? []).map(field => [field, 'Giá trị chưa hợp lệ.'])))
    } finally { pendingWrite.current = false; setSaving(false) }
  }

  const action = modal?.action
  const fieldsVisible = action === 'create' || Boolean(modal?.record)
  const info = action === 'create' || action === 'edit'
  const blocked = !canWrite(action)
  function field(name, label, props = {}) {
    return <TextInput label={label} name={name} value={values[name]} error={fields[name]}
      onChange={event => change(name, event.target.value)} {...props} />
  }

  return <div className="product-page">
    <PageHeader title="Quản lý sản phẩm" description="Tra cứu SKU, danh mục, giá bán và trạng thái kinh doanh."
      actions={canPreviewCreate && <Button id="product-create" variant="secondary" onClick={() => open('create')}>Xem biểu mẫu tạo</Button>} />
    {notice && <p role="status" className="message message-success">{notice}</p>}
    <Card aria-label="Danh sách sản phẩm">
      <RecordFilters label="Tìm kiếm sản phẩm" statusId="product-active" searchLabel="Tìm SKU, tên, hãng hoặc model"
        searchName="product-search" maxLength={200} search={search} active={query.active}
        activeLabel="Đang kinh doanh" inactiveLabel="Ngừng kinh doanh" changed={Boolean(search || query.q || query.active)}
        onSearch={changeSearch} onApply={apply} onReset={() => { setSearch(''); apply('', '') }} />
      {!settled ? <LoadingState label="Đang tải sản phẩm…" /> : error ? <ListError status={error.status} message={errorMessage(error)}
        onRetry={() => refreshList(value => value + 1)} /> : result.items.length === 0 ? <EmptyState title="Không có sản phẩm" description="Chưa có kết quả phù hợp." /> : <>
        <div className="product-table-scroll" role="region" aria-label="Bảng sản phẩm có thể cuộn ngang" tabIndex={0}>
          <table className="product-table">
            <caption className="product-sr-only">Sản phẩm</caption>
            <thead><tr>{['SKU / Tên', 'Danh mục', 'Giá bán', 'Ngưỡng tồn thấp', 'Trạng thái', 'Cập nhật', 'Thao tác'].map(label => <th scope="col" key={label}>{label}</th>)}</tr></thead>
            <tbody>{result.items.map(record => <tr key={record.id}>
              <th scope="row"><small>{record.sku}</small><br />{record.name}</th>
              <td>{record.categoryName}</td><td>{money(record.sellingPrice)}</td>
              <td>{digits.format(BigInt(record.minStockLevel))}</td>
              <td><StatusBadge variant={record.active ? 'success' : 'error'}>{record.active ? 'Đang kinh doanh' : 'Ngừng kinh doanh'}</StatusBadge></td>
              <td><time dateTime={record.updatedAt}>{formatVietnameseDate(record.updatedAt, { withTime: true })}</time></td>
              <td><div className="product-actions">
                <Button variant="secondary" aria-label={`Xem sản phẩm ${record.sku}`} onClick={() => open('detail', record.id)}>Xem</Button>
                {canWrite('edit') && <Button variant="secondary" aria-label={`Sửa sản phẩm ${record.sku}`} onClick={() => open('edit', record.id)}>Sửa</Button>}
                {canWrite('price') && <Button variant="secondary" aria-label={`Đổi giá bán ${record.sku}`} onClick={() => open('price', record.id)}>Đổi giá</Button>}
                {record.active && canWrite('deactivate') && <Button variant="secondary" className="product-danger"
                  aria-label={`Ngừng kinh doanh ${record.sku}`} onClick={() => open('deactivate', record.id)}>Ngừng kinh doanh</Button>}
              </div></td>
            </tr>)}</tbody>
          </table>
        </div>
        <RecordPagination result={result} noun="sản phẩm" onPage={page => setQuery(previous => ({ ...previous, page }))} />
      </>}
    </Card>
    {modal && <CategoryDialog title={titles[action]} titleId="product-dialog-title" fallbackFocusId="page-title"
      busy={saving} ready={!modal.loading} onClose={close}>
      {modal.loading ? <LoadingState label="Đang tải sản phẩm…" /> : <>
        {failure && <p role="alert" className="message message-error">{errorMessage(failure, action)}</p>}
        {action === 'detail' && modal.record ? <>
          <dl className="product-detail">
            <dt>SKU</dt><dd>{modal.record.sku}</dd><dt>Tên</dt><dd>{modal.record.name}</dd>
            <dt>Danh mục</dt><dd>{modal.record.categoryName}</dd><dt>Hãng</dt><dd>{modal.record.brandName || '—'}</dd>
            <dt>Model</dt><dd>{modal.record.modelCode || '—'}</dd><dt>Mô tả</dt><dd>{modal.record.description || '—'}</dd>
            <dt>Giá bán</dt><dd>{money(modal.record.sellingPrice)}</dd><dt>Ngưỡng tồn thấp</dt><dd>{digits.format(BigInt(modal.record.minStockLevel))}</dd>
            <dt>Trạng thái</dt><dd>{modal.record.active ? 'Đang kinh doanh' : 'Ngừng kinh doanh'}</dd>
            <dt>Ngày tạo</dt><dd>{formatVietnameseDate(modal.record.createdAt)}</dd>
          </dl>
          <Button data-initial-focus variant="secondary" onClick={close}>Đóng chi tiết</Button>
        </> : fieldsVisible && <form ref={form} onSubmit={submit} noValidate aria-busy={saving}>
          {action === 'create' && <p role="status">Chưa thể tạo sản phẩm trong khi chờ khởi tạo số dư tồn kho (DEP-001). Biểu mẫu chưa gửi dữ liệu.</p>}
          {action === 'deactivate' ? <p><strong>{modal.record.name}</strong> sẽ ngừng kinh doanh và vẫn được lưu trong lịch sử.</p>
            : <fieldset disabled={saving}>
              {info && <>
                {field('sku', 'SKU', { required: true, maxLength: 64, 'data-initial-focus': true })}
                {field('name', 'Tên sản phẩm', { required: true, maxLength: 200 })}
                <CategoryPicker client={categories} current={modal.record && { id: modal.record.categoryId, name: modal.record.categoryName }}
                  value={values.categoryId} error={fields.categoryId} onChange={event => change('categoryId', event.target.value)} />
                {field('brandName', 'Hãng', { maxLength: 100 })}
                {field('modelCode', 'Model', { maxLength: 100 })}
                <div className="csm-field"><label htmlFor="product-description">Mô tả</label>
                  <textarea id="product-description" name="description" rows={3} value={values.description}
                    aria-invalid={Boolean(fields.description) || undefined} aria-describedby={fields.description ? 'product-description-error' : undefined}
                    onChange={event => change('description', event.target.value)} />
                  {fields.description && <p id="product-description-error" className="csm-field-error">{fields.description}</p>}
                </div>
                {field('minStockLevel', 'Ngưỡng tồn thấp', { required: true, inputMode: 'numeric', maxLength: 19 })}
              </>}
              {(action === 'price' || action === 'create') && field('sellingPrice', 'Giá bán (₫)', { required: true, inputMode: 'numeric', 'data-initial-focus': action === 'price' || undefined })}
            </fieldset>}
          <footer>
            <Button data-initial-focus={action === 'deactivate' || undefined} variant="secondary" disabled={saving} onClick={close}>Hủy</Button>
            <Button type="submit" disabled={blocked || [401, 403, 404].includes(failure?.status)} loading={saving}>
              {action === 'deactivate' ? 'Xác nhận ngừng kinh doanh' : action === 'price' ? 'Lưu giá bán' : 'Lưu sản phẩm'}
            </Button>
          </footer>
        </form>}
      </>}
    </CategoryDialog>}
  </div>
}
