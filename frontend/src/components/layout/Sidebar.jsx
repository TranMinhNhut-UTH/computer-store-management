import { destinationPath, navigationGroups } from '../../app/navigation.js'
import { NavigationIcon } from './NavigationIcon.jsx'

export function Sidebar({ selectedId, onSelect, collapsed = false, permissions = [], canViewReports = false }) {
  const groups = navigationGroups.map(group => ({
    ...group,
    items: group.items.filter(item => item.id === 'overview'
      || (item.id === 'reports' ? canViewReports : item.permission && permissions.includes(item.permission))),
  })).filter(group => group.items.length > 0)
  return <><div className="csm-sidebar-brand"><span className="brand-mark" aria-hidden="true">CSM</span>{!collapsed && <strong>Computer Store<br />Management</strong>}</div>
    <nav id="application-navigation" aria-label="Điều hướng ứng dụng">{groups.map(group => <div className="csm-nav-group" role="group" aria-label={group.label} key={group.label}>
      {!collapsed && <p aria-hidden="true">{group.label}</p>}
      {group.items.map(item => <a href={destinationPath(item.id)} key={item.id} data-nav-id={item.id} aria-label={item.label} title={collapsed ? item.label : undefined} aria-current={selectedId === item.id ? 'page' : undefined} onClick={event => {
        if (event.defaultPrevented || event.button !== 0 || event.metaKey || event.ctrlKey || event.shiftKey || event.altKey) return
        event.preventDefault()
        onSelect(item.id)
      }}>
        <NavigationIcon name={item.id} />{!collapsed && <span className="csm-nav-label">{item.label}</span>}
      </a>)}
    </div>)}</nav>
  </>
}
