import { ThemeToggle } from '../../theme/ThemeToggle.jsx'
import { Button } from '../ui/Button.jsx'
import { NavigationIcon } from './NavigationIcon.jsx'

export function Topbar({ title, username, onMenu, menuOpen, menuIsDialog, onLogout, loggingOut, logoutDisabled }) {
  const menuLabel = menuIsDialog ? 'Mở điều hướng' : menuOpen ? 'Thu gọn điều hướng' : 'Mở rộng điều hướng'
  return <header className="csm-topbar"><Button className="csm-menu-toggle" variant="secondary" aria-label={menuLabel} title={menuLabel} aria-expanded={menuOpen} aria-controls={menuIsDialog ? 'mobile-navigation' : 'application-navigation'} aria-haspopup={menuIsDialog ? 'dialog' : undefined} onClick={onMenu}><NavigationIcon name="menu" /></Button><span className="csm-topbar-title">{title}</span><div className="csm-topbar-actions"><ThemeToggle /><span className="csm-username">{username}</span><Button variant="secondary" onClick={onLogout} loading={loggingOut} disabled={logoutDisabled}>{loggingOut ? 'Đang đăng xuất…' : 'Đăng xuất'}</Button></div></header>
}
