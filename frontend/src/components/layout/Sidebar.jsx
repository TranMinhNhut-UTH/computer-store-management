import { navigationGroups } from '../../app/navigation.js'
import { NavigationIcon } from './NavigationIcon.jsx'

export function Sidebar({ selectedId, onSelect, collapsed = false }) {
  return <><div className="csm-sidebar-brand"><span className="brand-mark" aria-hidden="true">CSM</span>{!collapsed && <strong>Computer Store<br />Management</strong>}</div>
    <nav id="application-navigation" aria-label="Điều hướng ứng dụng">{navigationGroups.map(group => <div className="csm-nav-group" role="group" aria-label={group.label} key={group.label}>
      {!collapsed && <p aria-hidden="true">{group.label}</p>}
      {group.items.map(item => <button type="button" key={item.id} data-nav-id={item.id} aria-label={item.label} title={collapsed ? item.label : undefined} aria-current={selectedId === item.id ? 'page' : undefined} onClick={() => onSelect(item.id)}>
        <NavigationIcon name={item.id} />{!collapsed && <span className="csm-nav-label">{item.label}</span>}
      </button>)}
    </div>)}</nav>
    {!collapsed && <p className="csm-nav-note">Chỉ điều hướng. Quyền thao tác sẽ được xác định bởi Backend khi từng chức năng được triển khai.</p>}</>
}
