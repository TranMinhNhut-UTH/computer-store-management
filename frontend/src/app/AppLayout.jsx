import { useEffect, useRef, useState } from 'react'
import { Sidebar } from '../components/layout/Sidebar.jsx'
import { Topbar } from '../components/layout/Topbar.jsx'
import { Card } from '../components/ui/Card.jsx'
import { Button } from '../components/ui/Button.jsx'
import { StatusBadge } from '../components/ui/StatusBadge.jsx'
import { PageHeader } from '../components/ui/PageHeader.jsx'
import { EmptyState } from '../components/ui/EmptyState.jsx'
import { navigationGroups } from './navigation.js'
import './shell.css'
import '../components/ui/ui.css'

export function AppLayout({ user, onLogout, loggingOut, error, notice }) {
  const [selectedId, setSelectedId] = useState('overview')
  const [drawerOpen, setDrawerOpen] = useState(false)
  const drawer = useRef(null)
  const closeButton = useRef(null)
  const heading = useRef(null)
  const current = navigationGroups.flatMap(group => group.items).find(item => item.id === selectedId)

  useEffect(() => {
    if (!drawerOpen) return
    const previous = document.activeElement
    const dialog = drawer.current
    dialog.showModal()
    closeButton.current.focus()
    const priorOverflow = document.body.style.overflow
    document.body.style.overflow = 'hidden'
    const wide = window.matchMedia('(min-width: 901px)')
    const resize = () => { if (wide.matches) setDrawerOpen(false) }
    wide.addEventListener('change', resize)
    return () => {
      wide.removeEventListener('change', resize)
      dialog.close()
      document.body.style.overflow = priorOverflow
      if (wide.matches) heading.current?.focus()
      else if (previous?.isConnected) previous.focus()
    }
  }, [drawerOpen])

  function select(id) {
    setSelectedId(id)
    setDrawerOpen(false)
    // After dialog cleanup restores its opener, move focus to the newly selected content.
    requestAnimationFrame(() => heading.current?.focus())
  }

  return <div className="csm-shell" data-app-shell="true">
    <a className="csm-skip-link" href="#main-content">Đến nội dung chính</a>
    <aside className="csm-desktop-sidebar"><Sidebar selectedId={selectedId} onSelect={select} /></aside>
    <dialog ref={drawer} id="mobile-navigation" className="csm-drawer" aria-label="Điều hướng ứng dụng" onCancel={event => { event.preventDefault(); setDrawerOpen(false) }} onKeyDown={event => { if (event.key === 'Escape') { event.preventDefault(); setDrawerOpen(false) } }} onClick={event => { if (event.target === event.currentTarget) setDrawerOpen(false) }}>
      <div className="csm-drawer-content"><Button ref={closeButton} variant="secondary" className="csm-close-menu" onClick={() => setDrawerOpen(false)}>Đóng điều hướng</Button><Sidebar selectedId={selectedId} onSelect={select} /></div>
    </dialog>
    <div className="csm-shell-body"><Topbar title={current.label} username={user.username} menuOpen={drawerOpen} onMenu={() => setDrawerOpen(true)} onLogout={onLogout} loggingOut={loggingOut} />
      <main id="main-content" className="csm-main" tabIndex={-1} ref={heading} aria-labelledby="page-title">
        {error && <div className="message message-error" role="alert">{error}</div>}{notice && <p className="message message-success" role="status">{notice}</p>}
        <PageHeader title={current.label} description="Không gian chức năng của Computer Store Management." actions={<StatusBadge>Chưa triển khai</StatusBadge>} />
        <Card aria-label={current.label}><EmptyState title="Chức năng chưa triển khai" description={`Mục ${current.label} hiện chỉ dùng để điều hướng. Chưa có dữ liệu hoặc thao tác nghiệp vụ.`} /></Card>
      </main>
    </div>
  </div>
}
