import { useEffect, useRef, useState } from 'react'
import { customerClient } from '../../api/customers.js'
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

const fields = [['code', 'Mã khách hàng', 50], ['fullName', 'Họ tên', 150]]
const titles = { detail: 'Chi tiết khách hàng', create: 'Thêm khách hàng', edit: 'Sửa khách hàng' }
const permissions = { create: 'CUSTOMER_CREATE', edit: 'CUSTOMER_UPDATE' }

function errorText(error) {
  switch (error.status) {
    case 400: return 'Dữ liệu không hợp lệ. Kiểm tra mã và họ tên khách hàng.'
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
  const [validation, setValidation] = useState({})
  const form = useRef(null)
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
    setValidation({})
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
    const code = values.code?.trim() ?? ''
    const name = values.fullName?.trim() ?? ''
    const errors = {}
    if (!/^[A-Za-z0-9._-]{1,50}$/.test(code)) errors.code = 'Nhập mã từ 1 đến 50 ký tự hợp lệ.'
    if (!name || name.length > 150) errors.fullName = 'Nhập họ tên từ 1 đến 150 ký tự.'
    setValidation(errors)
    setFailure(null)
    if (Object.keys(errors).length) {
      form.current.elements.namedItem(Object.keys(errors)[0])?.focus()
      return
    }
    pending.current = true
    setSaving(true)
    setFailure(null)
    try {
      if (action === 'create') await client.create(values)
      else await client.update(record.id, values)
      setDialog(null)
      setList({ loading: true })
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
      <RecordFilters label="Tìm kiếm khách hàng" statusId="customer-active" searchLabel="Tìm theo mã hoặc tên"
        maxLength={200} search={search} active={query.active} changed={Boolean(search || query.q || query.active)}
        onSearch={changeSearch} onApply={apply} onReset={() => { setSearch(''); apply('', '') }} />
      {list.loading && <LoadingState label="Đang tải khách hàng…" />}
      {list.error && <ListError status={list.error.status} message={errorText(list.error)}
        onRetry={() => setReload(count => count + 1)} />}
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
        <RecordPagination result={result} noun="khách hàng" onPage={page => setQuery(previous => ({ ...previous, page }))} />
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
      {(action === 'create' || (action === 'edit' && record)) && <form ref={form} onSubmit={submit} noValidate aria-busy={saving}>
        <fieldset disabled={saving}>{fields.map(([key, label, maxLength]) =>
              <TextInput key={key} label={label} name={key} maxLength={maxLength}
                required={key === 'code' || key === 'fullName'} value={values[key] ?? ''}
                error={validation[key] || (failure?.fields?.includes(key) ? 'Giá trị chưa hợp lệ.' : undefined)}
                data-initial-focus={key === 'code' || undefined}
                onChange={event => {
                  setValues({ ...values, [key]: event.target.value })
                  setValidation(previous => ({ ...previous, [key]: undefined }))
                }} />
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
