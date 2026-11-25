import { useEffect, useRef, useState } from 'react'
import { staffClient, staffRoles } from '../../api/staff.js'
import { Button } from '../../components/ui/Button.jsx'
import { Card } from '../../components/ui/Card.jsx'
import { EmptyState } from '../../components/ui/EmptyState.jsx'
import { ListError } from '../../components/ui/ListError.jsx'
import { LoadingState } from '../../components/ui/LoadingState.jsx'
import { PageHeader } from '../../components/ui/PageHeader.jsx'
import { RecordFilters } from '../../components/ui/RecordFilters.jsx'
import { RecordPagination } from '../../components/ui/RecordPagination.jsx'
import { StatusBadge } from '../../components/ui/StatusBadge.jsx'
import { TextInput } from '../../components/ui/TextInput.jsx'
import { CategoryDialog } from '../categories/CategoryDialog.jsx'
import { formatVietnameseDate } from '../../format/vietnameseDate.js'
import '../categories/categories.css'
import './staff.css'

const titles = {
  view: 'Chi tiết tài khoản', create: 'Tạo tài khoản', edit: 'Sửa thông tin', roles: 'Gán vai trò',
  lock: 'Khóa tài khoản', unlock: 'Mở khóa tài khoản',
}
const required = {
  view: [], create: ['EMPLOYEE_CREATE', 'EMPLOYEE_ROLE_ASSIGN'], edit: ['EMPLOYEE_UPDATE'],
  roles: ['EMPLOYEE_ROLE_ASSIGN'], lock: ['EMPLOYEE_LOCK'], unlock: ['EMPLOYEE_UNLOCK'],
}
const blank = { username: '', fullName: '', email: '', phone: '', password: '', roles: [] }
const roleLabels = { ADMIN: 'Quản trị viên', MANAGER: 'Quản lý', SALES_STAFF: 'Nhân viên bán hàng', WAREHOUSE_STAFF: 'Nhân viên kho' }

function message(error) {
  switch (error.status) {
    case 400: return 'Dữ liệu chưa hợp lệ. Kiểm tra lại các trường.'
    case 401: return 'Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại.'
    case 403: return 'Tài khoản hiện không có quyền thực hiện thao tác này.'
    case 404: return 'Không tìm thấy tài khoản nhân viên.'
    case 409: return 'Không thể lưu thay đổi. Kiểm tra tên đăng nhập, email hoặc điều kiện giữ quản trị viên đang hoạt động.'
    case 0: return 'Không thể kết nối đến máy chủ.'
    default: return 'Chưa thể xử lý yêu cầu. Vui lòng thử lại.'
  }
}

function validate(action, values) {
  const errors = {}
  if (action === 'create') {
    if (!/^[A-Za-z0-9._-]{1,100}$/.test(values.username.trim())) errors.username = 'Nhập tên đăng nhập hợp lệ.'
    if (!values.password.trim() || new TextEncoder().encode(values.password).length > 72) {
      errors.password = 'Nhập mật khẩu từ 1 đến 72 byte UTF-8.'
    }
  }
  if (action === 'create' || action === 'edit') {
    if (!values.fullName.trim() || values.fullName.length > 150) errors.fullName = 'Nhập họ tên từ 1 đến 150 ký tự.'
    if (values.email && (values.email.length > 254 || !/^[^\s@]+@[^\s@]+$/.test(values.email.trim()))) {
      errors.email = 'Nhập email hợp lệ hoặc để trống.'
    }
    if (values.phone.length > 30) errors.phone = 'Tối đa 30 ký tự.'
  }
  if ((action === 'create' || action === 'roles') && !values.roles.length) errors.roles = 'Chọn ít nhất một vai trò.'
  return errors
}

export function StaffPage({ client = staffClient, currentUserId }) {
  const [search, setSearch] = useState('')
  const [query, setQuery] = useState({ q: '', active: '', page: 0 })
  const [refresh, setRefresh] = useState(0)
  const [list, setList] = useState(null)
  const done = list?.query === query && list.refresh === refresh
  const result = done ? list.result : null
  const listError = done ? list.error : null
  const [dialog, setDialog] = useState(null)
  const [values, setValues] = useState(blank)
  const [fields, setFields] = useState({})
  const [failure, setFailure] = useState(null)
  const [saving, setSaving] = useState(false)
  const [notice, setNotice] = useState('')
  const timer = useRef(null)
  const details = useRef(null)
  const writing = useRef(false)
  const form = useRef(null)

  useEffect(() => {
    const controller = new AbortController()
    client.list(query, { signal: controller.signal }).then(result => {
      if (controller.signal.aborted) return
      if (query.page > 0 && query.page >= result.totalPages) {
        setQuery(current => current === query ? { ...query, page: Math.max(0, result.totalPages - 1) } : current)
      } else setList({ query, refresh, result })
    }).catch(error => { if (!controller.signal.aborted) setList({ query, refresh, error }) })
    return () => controller.abort()
  }, [client, query, refresh])
  useEffect(() => () => { clearTimeout(timer.current); details.current?.abort() }, [])

  function apply(value, active) {
    clearTimeout(timer.current)
    setNotice('')
    const q = value.trim()
    setQuery(previous => {
      const status = active ?? previous.active
      return q === previous.q && status === previous.active ? previous : { q, active: status, page: 0 }
    })
  }

  function type(value) {
    setSearch(value)
    clearTimeout(timer.current)
    timer.current = setTimeout(() => apply(value), 350)
  }

  function allowed(action) {
    return required[action]?.every(code => result?.allowedActions.includes(code)) === true
  }

  async function open(action, id) {
    if (!allowed(action)) return
    details.current?.abort()
    setFields({}); setFailure(null); setNotice(''); setValues(blank)
    if (action === 'create') { setDialog({ action }); return }
    const request = new AbortController()
    details.current = request
    setDialog({ action, loading: true })
    try {
      const item = await client.detail(id, { signal: request.signal })
      if (request.signal.aborted) return
      setValues({ ...blank, fullName: item.fullName, email: item.email ?? '', phone: item.phone ?? '', roles: item.roles })
      setDialog({ action, item })
    } catch (error) {
      if (!request.signal.aborted) { setFailure(error); setDialog({ action }) }
    }
  }

  function close() {
    if (writing.current) return
    details.current?.abort()
    setValues(blank)
    setDialog(null)
  }

  async function submit(event) {
    event.preventDefault()
    const { action, item } = dialog
    if (writing.current || !allowed(action) || (action !== 'create' && !item)) return
    const invalid = validate(action, values)
    setFields(invalid); setFailure(null)
    const first = Object.keys(invalid)[0]
    if (first) {
      form.current.querySelector('[name="' + first + '"]')?.focus()
      return
    }
    writing.current = true
    setSaving(true)
    const input = values
    try {
      if (action === 'create') {
        setValues({ ...values, password: '' })
        await client.create(input)
      } else if (action === 'edit') await client.update(item.id, input)
      else if (action === 'roles') await client.assignRoles(item.id, input.roles)
      else await client.setActive(item.id, action === 'unlock')
      setDialog(null)
      setValues(blank)
      setNotice(action === 'roles' ? 'Đã cập nhật vai trò.' : action === 'lock' ? 'Đã khóa tài khoản.'
        : action === 'unlock' ? 'Đã mở khóa tài khoản.' : 'Đã lưu tài khoản nhân viên.')
      setRefresh(previous => previous + 1)
    } catch (error) {
      setFailure(error)
      if (error.status === 400) setFields(Object.fromEntries((error.fields ?? []).map(field => [field, 'Giá trị chưa hợp lệ.'])))
    } finally { writing.current = false; setSaving(false) }
  }

  const action = dialog?.action
  const item = dialog?.item
  const profileForm = action === 'create' || action === 'edit'
  const roleForm = action === 'create' || action === 'roles'
  const selfAdmin = action === 'roles' && item && item.id === currentUserId && item.roles.includes('ADMIN')
  function field(name, label, props = {}) {
    return <TextInput label={label} name={name} value={values[name]} error={fields[name]}
      onChange={event => {
        setValues({ ...values, [name]: event.target.value })
        setFields(previous => ({ ...previous, [name]: undefined }))
      }} {...props} />
  }

  return <section className="category-page staff-page">
    <PageHeader title="Quản lý nhân viên" description="Tra cứu, cập nhật thông tin và quản lý quyền truy cập tài khoản."
      actions={allowed('create') && <Button onClick={() => open('create')}>Tạo tài khoản</Button>} />
    {notice && <p className="message message-success" role="status">{notice}</p>}
    <Card aria-label="Danh sách tài khoản">
      <RecordFilters label="Tìm kiếm nhân viên" statusId="staff-active" searchLabel="Tìm theo tên đăng nhập hoặc họ tên"
        maxLength={150} search={search} active={query.active} activeLabel="Đang hoạt động" inactiveLabel="Đã khóa"
        changed={Boolean(search || query.q || query.active)} onSearch={type} onApply={apply}
        onReset={() => { setSearch(''); apply('', '') }} />
      {!done ? <LoadingState label="Đang tải tài khoản…" /> : listError ? <ListError status={listError.status}
        message={message(listError)} onRetry={() => setRefresh(count => count + 1)} />
        : result.items.length === 0 ? <EmptyState title="Không có tài khoản phù hợp" description="Thử thay đổi từ khóa hoặc trạng thái." /> : <>
          <div className="category-table-region" role="region" aria-label="Bảng tài khoản nhân viên" tabIndex={0}>
            <table className="category-table">
              <caption className="category-sr-only">Tài khoản nhân viên</caption>
              <thead><tr>{['Tên đăng nhập / Họ tên', 'Vai trò', 'Trạng thái', 'Cập nhật', 'Thao tác'].map(label => <th scope="col" key={label}>{label}</th>)}</tr></thead>
              <tbody>{result.items.map(employee => <tr key={employee.id}>
                <th scope="row"><span className="category-code">{employee.username}</span>{employee.fullName}</th>
                <td>{employee.roles.map(role => roleLabels[role]).join(', ') || '—'}</td>
                <td><StatusBadge variant={employee.active ? 'success' : 'error'}>{employee.active ? 'Đang hoạt động' : 'Đã khóa'}</StatusBadge></td>
                <td className="category-timestamps"><time dateTime={employee.updatedAt}>{formatVietnameseDate(employee.updatedAt, { withTime: true })}</time></td>
                <td><div className="category-row-actions">
                  <Button variant="secondary" aria-label={'Xem ' + employee.username} onClick={() => open('view', employee.id)}>Xem</Button>
                  {allowed('edit') && <Button variant="secondary" aria-label={'Sửa ' + employee.username} onClick={() => open('edit', employee.id)}>Sửa</Button>}
                  {allowed('roles') && <Button variant="secondary" aria-label={'Vai trò của ' + employee.username}
                    onClick={() => open('roles', employee.id)}>Vai trò</Button>}
                  {employee.id !== currentUserId && allowed(employee.active ? 'lock' : 'unlock') && <Button variant="secondary"
                    className={employee.active ? 'category-deactivate' : undefined}
                    aria-label={(employee.active ? 'Khóa ' : 'Mở khóa ') + employee.username}
                    onClick={() => open(employee.active ? 'lock' : 'unlock', employee.id)}>{employee.active ? 'Khóa' : 'Mở khóa'}</Button>}
                </div></td>
              </tr>)}</tbody>
            </table>
          </div>
          <RecordPagination result={result} noun="tài khoản" onPage={page => setQuery(previous => ({ ...previous, page }))} />
        </>}
    </Card>
    {dialog && <CategoryDialog title={titles[action]} titleId="staff-dialog-title" fallbackFocusId="page-title"
      busy={saving} ready={!dialog.loading} onClose={close}>
      {dialog.loading && <LoadingState label="Đang tải thông tin tài khoản…" />}
      {failure && <p className="message message-error" role="alert">{message(failure)}</p>}
      {action === 'view' && item && <>
        <dl><dt>Tên đăng nhập</dt><dd>{item.username}</dd><dt>Họ tên</dt><dd>{item.fullName}</dd>
          <dt>Email</dt><dd>{item.email || '—'}</dd><dt>Điện thoại</dt><dd>{item.phone || '—'}</dd>
          <dt>Vai trò</dt><dd>{item.roles.map(role => roleLabels[role]).join(', ') || '—'}</dd>
          <dt>Trạng thái</dt><dd>{item.active ? 'Đang hoạt động' : 'Đã khóa'}</dd></dl>
        <Button variant="secondary" data-initial-focus onClick={close}>Đóng chi tiết</Button>
      </>}
      {(action === 'create' || (action !== 'view' && item)) && <form ref={form} onSubmit={submit} noValidate aria-busy={saving}>
        {action === 'lock' || action === 'unlock'
          ? <p>{action === 'lock' ? 'Khóa' : 'Mở khóa'} tài khoản <strong>{item.username}</strong>? Lịch sử giao dịch vẫn được lưu.</p>
          : <fieldset disabled={saving}>
            {action === 'create' && field('username', 'Tên đăng nhập', { required: true, maxLength: 100, autoComplete: 'off', 'data-initial-focus': true })}
            {profileForm && <>
              {field('fullName', 'Họ tên', { required: true, maxLength: 150, 'data-initial-focus': action === 'edit' || undefined })}
              {field('email', 'Email', { type: 'email', maxLength: 254 })}
              {field('phone', 'Điện thoại', { type: 'tel', maxLength: 30 })}
            </>}
            {action === 'create' && field('password', 'Mật khẩu ban đầu', { required: true, type: 'password', autoComplete: 'new-password' })}
            {roleForm && <fieldset className="staff-roles" aria-describedby={fields.roles ? 'staff-roles-error' : undefined}>
              <legend>Vai trò</legend>
              {staffRoles.map((role, index) => <label key={role}>
                <input type="checkbox" name="roles" value={role} checked={values.roles.includes(role)}
                  disabled={selfAdmin && role === 'ADMIN'} data-initial-focus={action === 'roles' && index === (selfAdmin ? 1 : 0) || undefined}
                  onChange={event => {
                    setValues({ ...values, roles: event.target.checked ? [...values.roles, role] : values.roles.filter(code => code !== role) })
                    setFields(previous => ({ ...previous, roles: undefined }))
                  }} />
                {roleLabels[role]}
              </label>)}
              {fields.roles && <p id="staff-roles-error" className="csm-field-error">{fields.roles}</p>}
            </fieldset>}
          </fieldset>}
        <footer>
          <Button variant="secondary" disabled={saving} data-initial-focus={action === 'lock' || action === 'unlock' || undefined}
            onClick={close}>Hủy</Button>
          <Button type="submit" loading={saving} disabled={!allowed(action) || [401, 403, 404].includes(failure?.status)}>
            {action === 'lock' ? 'Xác nhận khóa' : action === 'unlock' ? 'Xác nhận mở khóa' : 'Lưu'}
          </Button>
        </footer>
      </form>}
    </CategoryDialog>}
  </section>
}
