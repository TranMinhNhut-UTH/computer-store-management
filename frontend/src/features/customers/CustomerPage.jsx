import { useEffect, useRef, useState } from 'react'
import { customerClient } from '../../api/customers.js'
import { Button } from '../../components/ui/Button.jsx'
import { Card } from '../../components/ui/Card.jsx'
import { EmptyState } from '../../components/ui/EmptyState.jsx'
import { LoadingState } from '../../components/ui/LoadingState.jsx'
import { PageHeader } from '../../components/ui/PageHeader.jsx'
import { StatusBadge } from '../../components/ui/StatusBadge.jsx'
import { TextInput } from '../../components/ui/TextInput.jsx'
import { formatVietnameseDate } from '../../format/vietnameseDate.js'
import { CategoryDialog } from '../categories/CategoryDialog.jsx'
import '../categories/categories.css'

const fields = [['code', 'Mã khách hàng', 50], ['fullName', 'Họ tên', 150]]
const titles = { detail: 'Chi tiết khách hàng', create: 'Thêm khách hàng', edit: 'Sửa khách hàng' }
const permissions = { create: 'CUSTOMER_CREATE', edit: 'CUSTOMER_UPDATE' }

function errorText(error) {
  switch (error.status) {
    case 400: return 'Dữ liệu không hợp lệ. Kiểm tra mã, thông tin liên hệ và độ dài các trường.'
    case 401: return 'Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại.'
    case 403: return 'Tài khoản hiện không có quyền thực hiện thao tác này.'
    case 404: return 'Không tìm thấy khách hàng.'
    case 409: return 'Mã khách hàng đã được sử dụng.'
    case 0: return 'Không thể kết nối đến máy chủ.'
    default: return 'Chưa thể xử lý yêu cầu. Vui lòng thử lại.'
  }
}

export function CustomerPage({ client = customerClient }) {
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
    setList(previous => ({ result: previous.result, loading: true }))
    client.list(query, { signal: controller.signal }).then(data => {
      if (controller.signal.aborted) return
      if (query.page > 0 && query.page >= data.totalPages) {
        setQuery({ ...query, page: Math.max(0, data.totalPages - 1) })
      } else setList({ result: data })
    }).catch(error => { if (!controller.signal.aborted) setList({ error }) })
    return () => controller.abort()
  }, [client, query, reload])

  useEffect(() => () => { clearTimeout(searchTimer.current); detailRequest.current?.abort() }, [])

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
      setValues({ code: record.code, fullName: record.fullName })
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
    if (!/^[A-Za-z0-9._-]+$/.test(values.code?.trim() ?? '') || !values.fullName?.trim()) {
      setFailure({ status: 400 })
      return
    }
    pending.current = true
    setSaving(true)
    setFailure(null)
    try {
      if (action === 'create') await client.create(values)
      else await client.update(record.id, values)
      setDialog(null)
      setNotice('Đã lưu hồ sơ khách hàng.')
      setReload(count => count + 1)
    } catch (error) { setFailure(error) }
    finally { pending.current = false; setSaving(false) }
  }

  const action = dialog?.action
  const record = dialog?.record
  const status = item => item.active ? 'Đang sử dụng' : 'Ngừng sử dụng'

  return <div className="category-page" id="customer-page" tabIndex={-1}>
    <PageHeader title="Quản lý khách hàng" description="Tra cứu hồ sơ khách hàng theo mã, họ tên và trạng thái."
      actions={allowed('create') && <Button onClick={() => open('create')}>Thêm khách hàng</Button>} />
    {notice && <p role="status" className="message message-success">{notice}</p>}
    <Card aria-label="Danh sách khách hàng">
      <form className="category-filters" onSubmit={event => { event.preventDefault(); apply(search) }}>
        <TextInput label="Tìm mã hoặc tên" value={search} maxLength={200} onChange={event => {
          const value = event.target.value
          setSearch(value)
          clearTimeout(searchTimer.current)
          searchTimer.current = setTimeout(() => apply(value), 350)
        }} />
        <div className="csm-field"><label htmlFor="customer-active">Trạng thái</label>
          <select id="customer-active" value={query.active} onChange={event => apply(search, event.target.value)}>
            <option value="">Tất cả</option><option value="true">Đang sử dụng</option><option value="false">Ngừng sử dụng</option>
          </select>
        </div>
        <Button variant="secondary" onClick={() => { setSearch(''); apply('', '') }}>Đặt lại</Button>
      </form>
      {list.loading && <LoadingState label="Đang tải khách hàng…" />}
      {list.error && <div role="alert" className="category-error"><p>{errorText(list.error)}</p>
        <Button variant="secondary" onClick={() => setReload(count => count + 1)}>Thử lại</Button></div>}
      {!list.loading && result?.items.length === 0 && <EmptyState title="Không có khách hàng" description="Thử đổi từ khóa hoặc trạng thái." />}
      {result?.items.length > 0 && <>
        <div className="category-table-region" role="region" aria-label="Bảng khách hàng" tabIndex={0} aria-busy={list.loading}>
          <table className="category-table">
            <caption className="category-sr-only">Khách hàng</caption>
            <thead><tr>{['Mã / Họ tên', 'Trạng thái', 'Cập nhật', 'Thao tác'].map(label => <th key={label} scope="col">{label}</th>)}</tr></thead>
            <tbody>{result.items.map(item => <tr key={item.id}>
              <th scope="row"><span className="category-code">{item.code}</span>{item.fullName}</th>
              <td><StatusBadge variant={item.active ? 'success' : 'error'}>{status(item)}</StatusBadge></td>
              <td className="category-timestamps"><time dateTime={item.updatedAt}>{formatVietnameseDate(item.updatedAt, { withTime: true })}</time></td>
              <td><div className="category-row-actions">
                <Button variant="secondary" aria-label={`Xem ${item.code}`} onClick={() => open('detail', item.id)}>Xem</Button>
                {allowed('edit') && <Button variant="secondary" aria-label={`Sửa ${item.code}`} onClick={() => open('edit', item.id)}>Sửa</Button>}
              </div></td>
            </tr>)}</tbody>
          </table>
        </div>
        <nav className="category-pagination" aria-label="Phân trang khách hàng">
          <p>Trang {result.page + 1}/{result.totalPages} · {result.totalElements} khách hàng</p>
          <div className="category-row-actions">
            <Button variant="secondary" disabled={list.loading || result.page === 0}
              onClick={() => setQuery({ ...query, page: result.page - 1 })}>Trang trước</Button>
            <Button variant="secondary" disabled={list.loading || result.page + 1 >= result.totalPages}
              onClick={() => setQuery({ ...query, page: result.page + 1 })}>Trang sau</Button>
          </div>
        </nav>
      </>}
    </Card>
    {dialog && <CategoryDialog title={titles[action]} titleId="customer-dialog-title" fallbackFocusId="customer-page"
      busy={saving} ready={!dialog.loading} onClose={close}>
      {dialog.loading && <LoadingState label="Đang tải khách hàng…" />}
      {failure && <p role="alert" className="message message-error">{errorText(failure)}</p>}
      {action === 'detail' && record && <>
        <dl>{fields.map(([key, label]) => <div key={key}><dt>{label}</dt><dd>{record[key]}</dd></div>)}
          <div><dt>Trạng thái</dt><dd>{status(record)}</dd></div>
        </dl>
        <Button data-initial-focus variant="secondary" onClick={close}>Đóng chi tiết</Button>
      </>}
      {(action === 'create' || (action === 'edit' && record)) && <form onSubmit={submit} aria-busy={saving}>
        <fieldset disabled={saving}>{fields.map(([key, label, maxLength]) =>
              <TextInput key={key} label={label} name={key} maxLength={maxLength}
                required={key === 'code' || key === 'fullName'} value={values[key] ?? ''}
                error={failure?.fields?.includes(key) ? 'Giá trị chưa hợp lệ.' : undefined}
                data-initial-focus={key === 'code' || undefined}
                onChange={event => setValues({ ...values, [key]: event.target.value })} />
          )}</fieldset>
        <footer>
          <Button variant="secondary" disabled={saving} onClick={close}>Hủy</Button>
          <Button type="submit" loading={saving} disabled={!allowed(action) || [401, 403, 404].includes(failure?.status)}>
            Lưu hồ sơ
          </Button>
        </footer>
      </form>}
    </CategoryDialog>}
  </div>
}
