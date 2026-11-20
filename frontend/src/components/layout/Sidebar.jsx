import { navigationGroups } from '../../app/navigation.js'

export function Sidebar({ selectedId, onSelect }) {
  return <><div className="csm-sidebar-brand"><span className="brand-mark" aria-hidden="true">CSM</span><strong>Computer Store<br />Management</strong></div>
    <nav aria-label="Điều hướng ứng dụng">{navigationGroups.map(group => <div className="csm-nav-group" key={group.label}><p>{group.label}</p>{group.items.map(item => <button type="button" key={item.id} data-nav-id={item.id} aria-current={selectedId === item.id ? 'page' : undefined} onClick={() => onSelect(item.id)}><span>{item.label}</span><small>Chưa triển khai</small></button>)}</div>)}</nav>
    <p className="csm-nav-note">Chỉ điều hướng. Quyền thao tác sẽ được xác định bởi Backend khi từng chức năng được triển khai.</p></>
}
