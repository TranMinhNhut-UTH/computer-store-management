import { useEffect, useRef, useState } from 'react'
import { supplierClient } from '../../api/suppliers.js'
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
import { formatVietnameseDate } from '../../format/vietnameseDate.js'
import { CategoryDialog } from '../categories/CategoryDialog.jsx'
import '../categories/categories.css'

const fields = [
  ['code', 'Mã nhà cung cấp', 50], ['name', 'Tên nhà cung cấp', 200],
  ['phone', 'Điện thoại', 30, 'tel'], ['email', 'Email', 254, 'email'],
  ['address', 'Địa chỉ', 500], ['notes', 'Ghi chú', 65_535],
]
const titles = { detail: 'Chi tiết nhà cung cấp', create: 'Thêm nhà cung cấp', edit: 'Sửa nhà cung cấp', deactivate: 'Ngừng sử dụng' }
const permissions = { create: 'SUPPLIER_CREATE', edit: 'SUPPLIER_UPDATE', deactivate: 'SUPPLIER_DEACTIVATE' }

function errorText(error, action) {
  switch (error.status) {
    case 400: return 'Dữ liệu không hợp lệ. Kiểm tra mã, thông tin liên hệ và độ dài các trường.'
    case 401: return 'Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại.'
    case 403: return 'Tài khoản hiện không có quyền thực hiện thao tác này.'
    case 404: return 'Không tìm thấy nhà cung cấp.'
    case 409: return action === 'deactivate'
      ? 'Không thể ngừng sử dụng khi còn phiếu nhập nháp.'
      : 'Mã nhà cung cấp đã được sử dụng.'
    case 0: return 'Không thể kết nối đến máy chủ.'
    default: return 'Chưa thể xử lý yêu cầu. Vui lòng thử lại.'
  }
}

export function SupplierPage({ client = supplierClient }) {
  const [search, setSearch] = useState('')
  const [query, setQuery] = useState({ q: '', active: '', page: 0 })
  const [reload, setReload] = useState(0)
  const [list, setList] = useState({ loading: true })
  const [dialog, setDialog] = useState(null)
  const [values, setValues] = useState({})
  const [failure, setFailure] = useState(null)
  const [notice, setNotice] = useState('')
  const [saving, setSaving] = useState(false)
  const pending = useRef(false)
  const detailRequest = useRef(null)
  const searchTimer = useRef(null)
  const result = list.result

  useEffect(() => {
    const controller = new AbortController()
    setList({ loading: true })
    client.list(query, { signal: controller.signal }).then(data => {
      if (controller.signal.aborted) return
      if (query.page > 0 && query.page >= data.totalPages) {
        setQuery(previous => previous === query ? { ...previous, page: Math.max(0, data.totalPages - 1) } : previous)
      } else setList({ result: data })
    }).catch(error => { if (!controller.signal.aborted) setList({ error }) })
    return () => controller.abort()
  }, [client, query, reload])

  useEffect(() => () => { clearTimeout(searchTimer.current); detailRequest.current?.abort() }, [])

  function changeSearch(value) {
    setSearch(value)
    clearTimeout(searchTimer.current)
    searchTimer.current = setTimeout(() => apply(value), 350)
  }

  function apply(value, active) {
    clearTimeout(searchTimer.current)
    const q = value.trim()
    setQuery(previous => {
      const next = active ?? previous.active
      return q === previous.q && next === previous.active ? previous : { q, active: next, page: 0 }
    })
  }

  function allowed(action) { return result?.allowedActions?.includes(permissions[action]) === true }

  async function open(action, id) {
    if (action !== 'detail' && !allowed(action)) return
    detailRequest.current?.abort()
    setFailure(null)
    setNotice('')
    setValues({})
    if (action === 'create') { setDialog({ action }); return }
    const controller = new AbortController()
    detailRequest.current = controller
    setDialog({ action, loading: true })
    try {
      const record = await client.detail(id, { signal: controller.signal })
      if (controller.signal.aborted) return
      setValues(Object.fromEntries(fields.map(([key]) => [key, record[key] ?? ''])))
      setDialog({ action, record })
    } catch (error) {
      if (!controller.signal.aborted) { setFailure(error); setDialog({ action }) }
    }
  }

  function close() {
    if (pending.current) return
    detailRequest.current?.abort()
    setDialog(null)
  }

  async function submit(event) {
    event.preventDefault()
    const { action, record } = dialog
    if (pending.current || !allowed(action) || (action !== 'create' && !record)) return
    pending.current = true
    setSaving(true)
    setFailure(null)
    try {
      if (action === 'create') await client.create(values)
      else if (action === 'edit') await client.update(record.id, values)
      else await client.deactivate(record.id)
      setDialog(null)
      setList({ loading: true })
      setNotice(action === 'deactivate' ? 'Đã ngừng sử dụng nhà cung cấp.' : 'Đã lưu nhà cung cấp.')
      setReload(count => count + 1)
    } catch (error) { setFailure(error) }
    finally { pending.current = false; setSaving(false) }
  }

  const action = dialog?.action
  const record = dialog?.record
  const status = item => item.active ? 'Đang sử dụng' : 'Ngừng sử dụng'

  return <div className="category-page" id="supplier-page" tabIndex={-1}>
    <PageHeader title="Quản lý nhà cung cấp" description="Tra cứu thông tin liên hệ và trạng thái sử dụng."
      actions={allowed('create') && <Button onClick={() => open('create')}>Thêm nhà cung cấp</Button>} />
    {notice && <p role="status" className="message message-success">{notice}</p>}
    <Card aria-label="Danh sách nhà cung cấp">
      <RecordFilters label="Tìm kiếm nhà cung cấp" statusId="supplier-active" searchLabel="Tìm theo mã hoặc tên"
        maxLength={200} search={search} active={query.active} changed={Boolean(search || query.q || query.active)}
        onSearch={changeSearch} onApply={apply} onReset={() => { setSearch(''); apply('', '') }} />
      {list.loading && <LoadingState label="Đang tải nhà cung cấp…" />}
      {list.error && <ListError status={list.error.status} message={errorText(list.error)}
        onRetry={() => setReload(count => count + 1)} />}
      {!list.loading && result?.items.length === 0 && <EmptyState title="Không có nhà cung cấp" description="Thử đổi từ khóa hoặc trạng thái." />}
      {result?.items.length > 0 && <>
        <div className="category-table-region" role="region" aria-label="Bảng nhà cung cấp" tabIndex={0} aria-busy={list.loading}>
          <table className="category-table">
            <caption className="category-sr-only">Nhà cung cấp</caption>
            <thead><tr>{['Mã / Tên', 'Liên hệ', 'Trạng thái', 'Cập nhật', 'Thao tác'].map(label => <th key={label} scope="col">{label}</th>)}</tr></thead>
            <tbody>{result.items.map(item => <tr key={item.id}>
              <th scope="row"><span className="category-code">{item.code}</span>{item.name}</th>
              <td>{item.phone || '—'}<br />{item.email || '—'}</td>
              <td><StatusBadge variant={item.active ? 'success' : 'error'}>{status(item)}</StatusBadge></td>
              <td className="category-timestamps"><time dateTime={item.updatedAt}>{formatVietnameseDate(item.updatedAt, { withTime: true })}</time></td>
              <td><div className="category-row-actions">
                <Button variant="secondary" aria-label={`Xem ${item.code}`} onClick={() => open('detail', item.id)}>Xem</Button>
                {allowed('edit') && <Button variant="secondary" aria-label={`Sửa ${item.code}`} onClick={() => open('edit', item.id)}>Sửa</Button>}
                {item.active && allowed('deactivate') && <Button variant="secondary" className="category-deactivate"
                  aria-label={`Ngừng sử dụng ${item.code}`} onClick={() => open('deactivate', item.id)}>Ngừng sử dụng</Button>}
              </div></td>
            </tr>)}</tbody>
          </table>
        </div>
        <RecordPagination result={result} noun="nhà cung cấp" onPage={page => setQuery(previous => ({ ...previous, page }))} />
      </>}
    </Card>
    {dialog && <CategoryDialog title={titles[action]} titleId="supplier-dialog-title" fallbackFocusId="supplier-page"
      busy={saving} ready={!dialog.loading} onClose={close}>
      {dialog.loading && <LoadingState label="Đang tải nhà cung cấp…" />}
      {failure && <p role="alert" className="message message-error">{errorText(failure, action)}</p>}
      {action === 'detail' && record && <>
        <dl>{fields.map(([key, label]) => <div key={key}><dt>{label}</dt><dd>{record[key] || '—'}</dd></div>)}
          <div><dt>Trạng thái</dt><dd>{status(record)}</dd></div>
        </dl>
        <Button data-initial-focus variant="secondary" onClick={close}>Đóng chi tiết</Button>
      </>}
      {(action === 'create' || (action !== 'detail' && record)) && <form onSubmit={submit} aria-busy={saving}>
        {action === 'deactivate'
          ? <p><strong>{record.name}</strong> sẽ ngừng sử dụng. Nhà cung cấp và lịch sử phiếu nhập vẫn được lưu.</p>
          : <fieldset disabled={saving}>{fields.map(([key, label, maxLength, type]) =>
            key === 'notes' ? <div key={key} className="csm-field"><label htmlFor="supplier-notes">{label}</label>
              <textarea id="supplier-notes" name={key} rows={3} maxLength={maxLength} value={values[key] ?? ''}
                onChange={event => setValues({ ...values, [key]: event.target.value })} /></div>
              : <TextInput key={key} label={label} name={key} maxLength={maxLength} type={type ?? 'text'}
                required={key === 'code' || key === 'name'} value={values[key] ?? ''}
                error={failure?.fields?.includes(key) ? 'Giá trị chưa hợp lệ.' : undefined}
                data-initial-focus={key === 'code' || undefined}
                onChange={event => setValues({ ...values, [key]: event.target.value })} />
          )}</fieldset>}
        <footer>
          <Button data-initial-focus={action === 'deactivate' || undefined} variant="secondary" disabled={saving} onClick={close}>Hủy</Button>
          <Button type="submit" loading={saving} disabled={!allowed(action) || [401, 403, 404].includes(failure?.status)}>
            {action === 'deactivate' ? 'Xác nhận ngừng sử dụng' : 'Lưu nhà cung cấp'}
          </Button>
        </footer>
      </form>}
    </CategoryDialog>}
  </div>
}
