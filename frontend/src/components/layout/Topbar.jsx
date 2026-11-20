import { ThemeToggle } from '../../theme/ThemeToggle.jsx'
import { Button } from '../ui/Button.jsx'

export function Topbar({ title, username, onMenu, menuOpen, onLogout, loggingOut }) {
  return <header className="csm-topbar"><Button className="csm-menu-toggle" variant="secondary" aria-label="Mở điều hướng" aria-expanded={menuOpen} aria-controls="mobile-navigation" onClick={onMenu}>☰</Button><span className="csm-topbar-title">{title}</span><div className="csm-topbar-actions"><ThemeToggle /><span className="csm-username">{username}</span><Button variant="secondary" onClick={onLogout} loading={loggingOut}>{loggingOut ? 'Đang đăng xuất…' : 'Đăng xuất'}</Button></div></header>
}
