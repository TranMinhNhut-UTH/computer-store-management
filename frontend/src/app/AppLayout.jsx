import { useEffect, useRef, useState, useSyncExternalStore } from 'react'
import { Sidebar } from '../components/layout/Sidebar.jsx'
import { Topbar } from '../components/layout/Topbar.jsx'
import { Card } from '../components/ui/Card.jsx'
import { Button } from '../components/ui/Button.jsx'
import { StatusBadge } from '../components/ui/StatusBadge.jsx'
import { PageHeader } from '../components/ui/PageHeader.jsx'
import { EmptyState } from '../components/ui/EmptyState.jsx'
import { currentDestination, navigateTo, subscribeNavigation } from './navigation.js'
import { CategoryPage } from '../features/categories/CategoryPage.jsx'
import { ProductPage } from '../features/products/ProductPage.jsx'
import { SupplierPage } from '../features/suppliers/SupplierPage.jsx'
import { CustomerPage } from '../features/customers/CustomerPage.jsx'
import { ReportsPage } from '../features/reports/ReportsPage.jsx'
import { reportsClient } from '../api/reports.js'
import './shell.css'
import '../components/ui/ui.css'

const businessPages = { categories: CategoryPage, products: ProductPage, suppliers: SupplierPage, customers: CustomerPage, reports: ReportsPage }

const tabletQuery = '(min-width: 768px)'
const desktopQuery = '(min-width: 1024px)'

function getViewport() {
  if (window.matchMedia(desktopQuery).matches) return 'desktop'
  return window.matchMedia(tabletQuery).matches ? 'tablet' : 'mobile'
}

function subscribeViewport(update) {
  const queries = [tabletQuery, desktopQuery].map(query => window.matchMedia(query))
  queries.forEach(query => query.addEventListener('change', update))
  return () => queries.forEach(query => query.removeEventListener('change', update))
}

export function AppLayout({ user, onLogout, loggingOut, error, notice }) {
  const viewport = useSyncExternalStore(subscribeViewport, getViewport, () => 'mobile')
  const mobile = viewport === 'mobile'
  const [desktopCollapsed, setDesktopCollapsed] = useState(false)
  const [tabletExpanded, setTabletExpanded] = useState(false)
  const collapsed = viewport === 'tablet' ? !tabletExpanded : desktopCollapsed
  const current = useSyncExternalStore(subscribeNavigation, currentDestination)
  const selectedId = current.id
  const BusinessPage = businessPages[selectedId]
  const [canViewReports, setCanViewReports] = useState(false)
  const [drawerOpen, setDrawerOpen] = useState(false)
  const drawer = useRef(null)
  const closeButton = useRef(null)
  const heading = useRef(null)

  useEffect(() => {
    if (window.location.pathname === '/login') navigateTo('overview', true)
  }, [])

  useEffect(() => {
    const controller = new AbortController()
    reportsClient.canViewOverview({ signal: controller.signal }).then(
      allowed => { if (!controller.signal.aborted) setCanViewReports(allowed) },
      () => { if (!controller.signal.aborted) setCanViewReports(false) },
    )
    return () => controller.abort()
  }, [user.userId])

  useEffect(() => {
    if (!mobile) setDrawerOpen(false)
  }, [mobile])

  useEffect(() => {
    if (!mobile || !drawerOpen) return
    const previous = document.activeElement
    const dialog = drawer.current
    dialog.showModal()
    closeButton.current.focus()
    const priorOverflow = document.body.style.overflow
    document.body.style.overflow = 'hidden'
    return () => {
      dialog.close()
      document.body.style.overflow = priorOverflow
      if (getViewport() !== 'mobile') heading.current?.focus()
      else if (previous?.isConnected) previous.focus()
    }
  }, [drawerOpen, mobile])

  function toggleNavigation() {
    if (mobile) setDrawerOpen(open => !open)
    else if (viewport === 'tablet') setTabletExpanded(expanded => !expanded)
    else setDesktopCollapsed(value => !value)
  }

  function select(id) {
    navigateTo(id)
    setDrawerOpen(false)
    // After dialog cleanup restores its opener, move focus to the newly selected content.
    requestAnimationFrame(() => heading.current?.focus())
  }

  return <div className={`csm-shell${!mobile && collapsed ? ' csm-shell--collapsed' : ''}`} data-app-shell="true" data-viewport={viewport}>
    <a className="csm-skip-link" href="#main-content">Đến nội dung chính</a>
    {!mobile && <aside className="csm-desktop-sidebar"><Sidebar selectedId={selectedId} onSelect={select} collapsed={collapsed} canViewReports={canViewReports} /></aside>}
    {mobile && <dialog ref={drawer} id="mobile-navigation" className="csm-drawer" aria-label="Điều hướng ứng dụng" onCancel={event => { event.preventDefault(); setDrawerOpen(false) }} onKeyDown={event => { if (event.key === 'Escape') { event.preventDefault(); setDrawerOpen(false) } }} onClick={event => { if (event.target === event.currentTarget) setDrawerOpen(false) }}>
      {drawerOpen && <div className="csm-drawer-content"><Button ref={closeButton} variant="secondary" className="csm-close-menu" onClick={() => setDrawerOpen(false)}>Đóng điều hướng</Button><Sidebar selectedId={selectedId} onSelect={select} canViewReports={canViewReports} /></div>}
    </dialog>}
    <div className="csm-shell-body"><Topbar title={current.label} username={user.username} menuOpen={mobile ? drawerOpen : !collapsed} menuIsDialog={mobile} onMenu={toggleNavigation} onLogout={onLogout} loggingOut={loggingOut} />
      <main id="main-content" className="csm-main" tabIndex={-1} ref={heading} aria-labelledby="page-title">
        {error && <div className="message message-error" role="alert">{error}</div>}{notice && <p className="message message-success" role="status">{notice}</p>}
        {BusinessPage ? <BusinessPage /> : <>
          <PageHeader title={current.label} description="Không gian chức năng của Computer Store Management." actions={<StatusBadge>Chưa triển khai</StatusBadge>} />
          <Card aria-label={current.label}><EmptyState title="Chức năng chưa triển khai" description={`Mục ${current.label} hiện chỉ dùng để điều hướng. Chưa có dữ liệu hoặc thao tác nghiệp vụ.`} /></Card>
        </>}
      </main>
    </div>
  </div>
}
