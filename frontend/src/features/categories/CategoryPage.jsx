import { useEffect, useRef, useState } from 'react'
import { categoryClient } from '../../api/categories.js'
import { Button } from '../../components/ui/Button.jsx'
import { Card } from '../../components/ui/Card.jsx'
import { PageHeader } from '../../components/ui/PageHeader.jsx'
import { StatusBadge } from '../../components/ui/StatusBadge.jsx'
import { TextInput } from '../../components/ui/TextInput.jsx'
import { LoadingState } from '../../components/ui/LoadingState.jsx'
import { EmptyState } from '../../components/ui/EmptyState.jsx'
import { formatVietnameseDate } from '../../format/vietnameseDate.js'
import { CategoryDialog } from './CategoryDialog.jsx'
import { categoryValues, validateCategory } from './validation.js'
import './categories.css'

export function categoryError(error, action) {
  switch (error.status) {
    case 400: return 'Dữ liệu chưa hợp lệ. Vui lòng kiểm tra các trường và giới hạn ký tự.'
    case 401: return 'Phiên đăng nhập đã kết thúc. Vui lòng đăng nhập lại.'
    case 403: return action
      ? 'Truy cập bị từ chối. Bạn có thể thiếu quyền hoặc phiên bảo mật đã thay đổi.'
      : 'Truy cập bị từ chối. Bạn chưa được cấp quyền xem danh mục.'
    case 404: return 'Danh mục không còn tồn tại. Hãy đóng hộp thoại và tải lại danh sách.'
    case 409: return action === 'deactivate'
      ? 'Không thể ngừng sử dụng danh mục. Nếu còn sản phẩm tham chiếu, Backend hiện từ chối thao tác này; trạng thái không hợp lệ cũng có thể gây xung đột.'
      : 'Mã danh mục có thể đã tồn tại hoặc dữ liệu đang xung đột. Vui lòng kiểm tra lại.'
    case 0: return 'Không thể kết nối đến máy chủ. Vui lòng thử lại sau.'
    default: return 'Chưa thể xử lý yêu cầu. Vui lòng thử lại sau.'
  }
}
const blank = { code: '', name: '', description: '' }
const searchDelay = 350

export function CategoryPage({ client = categoryClient }) {
  const [query, setQuery] = useState({ q: '', active: '', page: 0, size: 20 })
  const [search, setSearch] = useState('')
  const searchTimer = useRef(null)
  const [refresh, setRefresh] = useState(0)
  const [result, setResult] = useState(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState(null)
  const [notice, setNotice] = useState('')
  const [modal, setModal] = useState(null)
  const [values, setValues] = useState(blank)
  const [fields, setFields] = useState({})
  const [modalError, setModalError] = useState(null)
  const [busy, setBusy] = useState(false)
  const inFlight = useRef(false)
  const [detailLoading, setDetailLoading] = useState(false)
  const [detailReady, setDetailReady] = useState(false)
  const detailRequest = useRef(null)
  const form = useRef(null)

  useEffect(() => {
    const controller = new AbortController()
    // Retain the last successful page during refresh so the dialog's focus-return
    // button stays enabled. The loading/error branches still hide stale rows.
    setLoading(true); setError(null)
    client.list(query, { signal: controller.signal }).then(data => {
      if (!controller.signal.aborted) {
        if (query.page > 0 && query.page >= data.totalPages) setQuery(previous =>
          previous === query ? { ...previous, page: Math.max(0, data.totalPages - 1) } : previous)
        else setResult(data)
      }
    }).catch(failure => { if (!controller.signal.aborted) setError(failure) })
      .finally(() => { if (!controller.signal.aborted) setLoading(false) })
    return () => controller.abort()
  }, [client, query, refresh])
  useEffect(() => () => detailRequest.current?.abort(), [])
  useEffect(() => () => clearTimeout(searchTimer.current), [])

  function applyFilters(value, status) {
    clearTimeout(searchTimer.current)
    const q = value.trim()
    setNotice('')
    setQuery(previous => {
      const active = status ?? previous.active
      if (q === previous.q && active === previous.active) return previous
      return { ...previous, q, active, page: 0 }
    })
  }

  function changeSearch(value) {
    setSearch(value)
    clearTimeout(searchTimer.current)
    searchTimer.current = setTimeout(() => applyFilters(value), searchDelay)
  }

  function close() {
    if (inFlight.current) return
    detailRequest.current?.abort()
    setModal(null); setDetailLoading(false)
  }
  function openCreate() {
    setValues(blank); setFields({}); setModalError(null); setNotice(''); setModal({ action: 'create' })
  }
  async function openRecord(record, action) {
    const controller = new AbortController()
    detailRequest.current?.abort(); detailRequest.current = controller
    setModal({ action, record }); setValues(blank); setDetailReady(false); setDetailLoading(true); setModalError(null); setFields({}); setNotice('')
    try {
      const fresh = await client.detail(record.id, { signal: controller.signal })
      if (!controller.signal.aborted) {
        setModal({ action, record: fresh })
        setValues({ code: fresh.code, name: fresh.name, description: fresh.description ?? '' })
        setDetailReady(true)
      }
    } catch (failure) { if (!controller.signal.aborted) setModalError(failure) }
    finally { if (!controller.signal.aborted) setDetailLoading(false) }
  }
  async function submit(event) {
    event.preventDefault()
    if (inFlight.current || detailLoading || (modal.action !== 'create' && !detailReady)) return
    const validation = modal.action === 'deactivate' ? {} : validateCategory(values)
    setFields(validation); setModalError(null)
    if (Object.keys(validation).length) {
      form.current.querySelector(`[name="${Object.keys(validation)[0]}"]`)?.focus()
      return
    }
    inFlight.current = true; setBusy(true)
    try {
      if (modal.action === 'deactivate') await client.deactivate(modal.record.id)
      else if (modal.action === 'edit') await client.update(modal.record.id, categoryValues(values))
      else await client.create(categoryValues(values))
      setModal(null)
      setNotice(modal.action === 'deactivate' ? 'Đã ngừng sử dụng danh mục.' : 'Đã lưu danh mục.')
      setRefresh(value => value + 1)
    } catch (failure) {
      setModalError(failure)
      if (failure.status === 400) setFields(Object.fromEntries((failure.fields ?? []).map(field => [field, 'Giá trị chưa hợp lệ. Vui lòng kiểm tra lại.'])))
    } finally { inFlight.current = false; setBusy(false) }
  }
  const denied = error?.status === 401 || error?.status === 403
  return <div className="category-page">
    <PageHeader title="Quản lý danh mục" description="Tra cứu và quản lý thông tin danh mục sản phẩm."
      actions={<Button id="category-create" onClick={openCreate} disabled={(loading && !result) || Boolean(error)}>Tạo danh mục</Button>} />
    {notice && <p role="status" className="message message-success">{notice}</p>}
    <Card aria-label="Danh sách danh mục">
      <form className="category-filters" aria-label="Tìm kiếm danh mục" onSubmit={event => {
        event.preventDefault()
        applyFilters(search)
      }}>
        <TextInput label="Tìm theo mã hoặc tên" name="q" value={search} onChange={event => changeSearch(event.target.value)}
          onKeyDown={event => {
            if (event.key !== 'Enter' || event.nativeEvent.isComposing) return
            event.preventDefault()
            applyFilters(event.currentTarget.value)
          }} maxLength={120} />
        <div className="csm-field"><label htmlFor="category-active">Trạng thái</label><select id="category-active" value={query.active} onChange={event => applyFilters(search, event.target.value)}>
          <option value="">Tất cả</option><option value="true">Đang sử dụng</option><option value="false">Ngừng sử dụng</option>
        </select></div>
        <Button variant="secondary" disabled={!search && !query.q && !query.active} onClick={() => {
          setSearch('')
          applyFilters('', '')
        }}>Đặt lại</Button>
      </form>
      {loading ? <LoadingState label="Đang tải danh mục…" /> : error ? <div className="category-error" role="alert">
        <h2>{denied ? 'Chưa thể truy cập danh mục' : 'Không tải được danh sách'}</h2><p>{categoryError(error)}</p>
        <Button variant="secondary" onClick={() => setRefresh(value => value + 1)}>Thử lại</Button>
      </div> : result?.items.length === 0 ? <EmptyState title="Không có danh mục" description="Chưa có dữ liệu phù hợp với điều kiện tìm kiếm." /> : result && <>
        <div className="category-table-region" role="region" aria-label="Bảng danh mục, cuộn ngang để xem thêm" tabIndex={0}>
          <table className="category-table"><caption className="category-sr-only">Danh mục sản phẩm</caption><thead><tr>
            <th scope="col">Mã / Tên danh mục</th><th scope="col">Mô tả</th><th scope="col">Trạng thái</th><th scope="col">Cập nhật / Tạo</th><th scope="col">Thao tác</th>
          </tr></thead><tbody>{result.items.map(record => <tr key={record.id}>
            <th scope="row"><span className="category-code">{record.code}</span><strong>{record.name}</strong></th>
            <td>{record.description || '—'}</td><td><StatusBadge variant={record.active ? 'success' : 'error'}>{record.active ? 'Đang sử dụng' : 'Ngừng sử dụng'}</StatusBadge></td>
            <td className="category-timestamps">
              <time dateTime={record.updatedAt}>{formatVietnameseDate(record.updatedAt, { withTime: true })}</time>
              <small className="csm-muted">Tạo: <time dateTime={record.createdAt}>{formatVietnameseDate(record.createdAt)}</time></small>
            </td>
            <td><div className="category-row-actions"><Button variant="secondary" onClick={() => openRecord(record, 'edit')} aria-label={`Sửa danh mục ${record.code}`}>Sửa</Button>
              {record.active && <Button variant="secondary" className="category-deactivate" onClick={() => openRecord(record, 'deactivate')} aria-label={`Ngừng sử dụng danh mục ${record.code}`}>Ngừng sử dụng</Button>}</div></td>
          </tr>)}</tbody></table>
        </div>
      </>}
      {!loading && !error && result && <nav className="category-pagination" aria-label="Phân trang danh mục">
        <p role="status">{result.totalElements} danh mục · Trang {result.totalPages ? result.page + 1 : 0}/{result.totalPages}</p>
        <div className="category-row-actions"><Button variant="secondary" disabled={result.page === 0} onClick={() => setQuery({ ...query, page: query.page - 1 })}>Trang trước</Button>
          <Button variant="secondary" disabled={result.page + 1 >= result.totalPages} onClick={() => setQuery({ ...query, page: query.page + 1 })}>Trang sau</Button></div>
      </nav>}
    </Card>
    {modal && <CategoryDialog title={modal.action === 'create' ? 'Tạo danh mục' : modal.action === 'edit' ? 'Sửa danh mục' : 'Ngừng sử dụng danh mục'} busy={busy} ready={!detailLoading} onClose={close}>
      {detailLoading ? <LoadingState label="Đang tải thông tin danh mục…" /> : <form ref={form} noValidate onSubmit={submit} aria-busy={busy} aria-describedby={modalError ? 'category-modal-error' : undefined}>
        {modalError && <p id="category-modal-error" role="alert" className="message message-error">{categoryError(modalError, modal.action)}</p>}
        {/* Failed detail reads must never submit stale list data. */}
        {(modal.action === 'create' || detailReady) && (modal.action === 'deactivate' ? <p>Ngừng sử dụng <strong>{modal.record.name}</strong> ({modal.record.code})? Danh mục sẽ ngừng sử dụng nhưng không bị xóa khỏi hệ thống. Bạn vẫn có thể xem lại bằng bộ lọc Ngừng sử dụng.</p> : <fieldset disabled={busy}>
          <TextInput data-initial-focus label="Mã danh mục" name="code" value={values.code} required error={fields.code} hint="Tối đa 50 ký tự: A–Z, số, dấu chấm, gạch dưới hoặc gạch ngang." onChange={event => setValues({ ...values, code: event.target.value })} />
          <TextInput label="Tên danh mục" name="name" value={values.name} required error={fields.name} hint="Tối đa 120 ký tự." onChange={event => setValues({ ...values, name: event.target.value })} />
          <div className="csm-field"><label htmlFor="category-description">Mô tả</label><textarea id="category-description" name="description" rows={4} value={values.description} onChange={event => setValues({ ...values, description: event.target.value })} aria-invalid={Boolean(fields.description) || undefined} aria-describedby="category-description-help" />
            <p id="category-description-help" className={fields.description ? 'csm-field-error' : 'csm-muted'}>{fields.description || 'Tối đa 500 ký tự. Để trống để xóa mô tả khi cập nhật.'}</p></div>
        </fieldset>)}
        <footer><Button data-initial-focus={modal.action === 'deactivate' ? true : undefined} variant="secondary" disabled={busy} onClick={close}>Hủy</Button>
          <Button type="submit" loading={busy} disabled={modalError?.status === 401 || modalError?.status === 403 || modalError?.status === 404 || (modal.action !== 'create' && !detailReady)}>{modal.action === 'deactivate' ? 'Xác nhận ngừng sử dụng' : 'Lưu danh mục'}</Button></footer>
      </form>}
    </CategoryDialog>}
  </div>
}
