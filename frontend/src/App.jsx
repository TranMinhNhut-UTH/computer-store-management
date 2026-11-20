import { useEffect, useRef, useState } from 'react'
import { ApiError, authClient } from './api/auth.js'
import { ThemeToggle } from './theme/ThemeToggle.jsx'

function errorMessage(error, action) {
  if (error instanceof ApiError) {
    if (error.status === 401) return action === 'login'
      ? 'Không thể đăng nhập. Vui lòng kiểm tra tên đăng nhập và mật khẩu.'
      : 'Phiên đăng nhập đã kết thúc. Vui lòng đăng nhập lại.'
    if (error.status === 400) return 'Vui lòng kiểm tra lại tên đăng nhập và mật khẩu.'
    if (error.status === 403) return 'Phiên bảo mật đã thay đổi. Vui lòng thử lại.'
    if (error.status === 0) return 'Không thể kết nối đến máy chủ. Vui lòng thử lại sau.'
  }
  return 'Chưa thể hoàn tất yêu cầu. Vui lòng thử lại sau.'
}

function ArrowIcon() {
  return <svg viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" strokeWidth="1.7" aria-hidden="true"><path d="M5 12h14m-6-6 6 6-6 6" /></svg>
}

export default function App() {
  const [user, setUser] = useState(null)
  const [initialized, setInitialized] = useState(false)
  const [busy, setBusy] = useState('checking')
  const [error, setError] = useState('')
  const [notice, setNotice] = useState('')
  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')
  const [showPassword, setShowPassword] = useState(false)
  const operationInFlight = useRef(false)

  async function loadSession(signal) {
    setBusy('checking')
    setError('')
    try {
      const current = await authClient.getCurrentUser({ signal })
      await authClient.refreshCsrf({ signal })
      if (signal?.aborted) return
      setUser(current)
      setInitialized(true)
    } catch (failure) {
      if (signal?.aborted) return
      setError(errorMessage(failure, 'session'))
      setInitialized(false)
    } finally {
      if (!signal?.aborted) setBusy(null)
    }
  }

  useEffect(() => {
    const controller = new AbortController()
    loadSession(controller.signal)
    return () => controller.abort()
  }, [])

  async function handleLogin(event) {
    event.preventDefault()
    if (operationInFlight.current || busy || !initialized) return
    operationInFlight.current = true
    setBusy('login')
    setError('')
    setNotice('')
    const credentials = { username, password }
    setPassword('')
    setShowPassword(false)
    try {
      await authClient.login(credentials)
      const current = await authClient.getCurrentUser()
      if (!current) throw new ApiError(401)
      setUser(current)
      setNotice('Đăng nhập thành công.')
      try { await authClient.refreshCsrf() } catch {
        setError('Đã đăng nhập nhưng chưa thể làm mới phiên bảo mật. Vui lòng thử lại trước khi tiếp tục.')
      }
    } catch (failure) {
      setUser(null)
      setError(errorMessage(failure, 'login'))
    } finally {
      credentials.password = ''
      operationInFlight.current = false
      setBusy(null)
    }
  }

  async function handleLogout() {
    if (operationInFlight.current || busy) return
    operationInFlight.current = true
    setBusy('logout')
    setError('')
    setNotice('')
    try {
      await authClient.logout()
      setUser(null)
      setPassword('')
      setNotice('Bạn đã đăng xuất.')
      try { await authClient.refreshCsrf() } catch {
        setInitialized(false)
        setError('Đã đăng xuất. Chưa thể kết nối lại để chuẩn bị phiên đăng nhập mới.')
      }
    } catch (failure) {
      if (failure instanceof ApiError && failure.status === 401) setUser(null)
      setError(errorMessage(failure, 'logout'))
    } finally {
      operationInFlight.current = false
      setBusy(null)
    }
  }

  return (
    <main className="auth-page">
      <section className="brand-panel" aria-labelledby="app-title">
        <div className="brand-lockup"><span className="brand-mark" aria-hidden="true">CS</span><span>Không gian làm việc</span></div>
        <div className="brand-copy">
          <p className="eyebrow">DÀNH CHO NHÂN VIÊN</p>
          <h1 id="app-title">Computer Store<br />Management<span className="title-dot">.</span></h1>
          <p className="brand-description">Đăng nhập để tiếp tục vào không gian làm việc của cửa hàng.</p>
        </div>
        <div className="brand-footer"><span className="small-rule" />Một điểm bắt đầu cho ngày làm việc.</div>
      </section>
      <section className="form-panel" aria-labelledby="form-title">
        <div className="theme-toolbar"><ThemeToggle /></div>
        <div className="auth-card">
          <span className="section-label">COMPUTER STORE</span>
          <h2 id="form-title">{user ? 'Chào mừng trở lại' : 'Đăng nhập'}</h2>
          <p className="form-description">{user ? 'Phiên đăng nhập của bạn đang hoạt động.' : 'Sử dụng tài khoản nhân viên để tiếp tục.'}</p>
          {busy === 'checking' && <p className="checking-message" role="status"><span className="spinner" aria-hidden="true" />Đang kiểm tra phiên đăng nhập…</p>}
          {error && <div id="auth-error" className="message message-error" role="alert">{error}</div>}
          {notice && <p className="message message-success" role="status">{notice}</p>}
          {!initialized && !busy ? (
            <button className="primary-button" type="button" onClick={() => loadSession()}>Thử kết nối lại<ArrowIcon /></button>
          ) : user ? (
            <div className="signed-in">
              <div className="user-summary"><span className="user-avatar" aria-hidden="true">{user.username.slice(0, 1).toUpperCase()}</span><div><span className="user-caption">Đã đăng nhập với tài khoản</span><strong>{user.username}</strong></div></div>
              <button className="primary-button" type="button" disabled={Boolean(busy)} onClick={handleLogout}>{busy === 'logout' ? <><span className="spinner" aria-hidden="true" />Đang đăng xuất…</> : <>Đăng xuất<ArrowIcon /></>}</button>
            </div>
          ) : (
            <form onSubmit={handleLogin} aria-busy={busy === 'login'} aria-describedby={error ? 'auth-error' : undefined}>
              <fieldset disabled={Boolean(busy) || !initialized}>
                <div className="field-group">
                  <label htmlFor="username">Tên đăng nhập</label>
                  <input id="username" name="username" type="text" autoComplete="username" autoCapitalize="none" spellCheck={false} required value={username} onChange={event => setUsername(event.target.value)} placeholder="Nhập tên đăng nhập" />
                </div>
                <div className="field-group">
                  <label htmlFor="password">Mật khẩu</label>
                  <div className="password-field">
                    <input id="password" name="password" type={showPassword ? 'text' : 'password'} autoComplete="current-password" required value={password} onChange={event => setPassword(event.target.value)} placeholder="Nhập mật khẩu" />
                    <button className="password-toggle" type="button" aria-label={showPassword ? 'Ẩn mật khẩu' : 'Hiện mật khẩu'} aria-pressed={showPassword} onClick={() => setShowPassword(value => !value)}>{showPassword ? 'Ẩn' : 'Hiện'}</button>
                  </div>
                </div>
                <button className="primary-button" type="submit">{busy === 'login' ? <><span className="spinner" aria-hidden="true" />Đang đăng nhập…</> : <>Đăng nhập<ArrowIcon /></>}</button>
              </fieldset>
            </form>
          )}
          <p className="support-note">Cần hỗ trợ tài khoản? Liên hệ quản trị viên cửa hàng.</p>
        </div>
        <p className="page-footer">Computer Store Management</p>
      </section>
    </main>
  )
}
