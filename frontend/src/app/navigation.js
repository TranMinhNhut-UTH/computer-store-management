// Navigation metadata only. These entries do not grant permissions or call business APIs.
export const navigationGroups = [
  { label: 'Không gian làm việc', items: [{ id: 'overview', label: 'Tổng quan' }] },
  { label: 'Dữ liệu danh mục', items: [
    { id: 'categories', label: 'Danh mục', permission: 'CATEGORY_READ' }, { id: 'products', label: 'Sản phẩm', permission: 'PRODUCT_READ' },
    { id: 'suppliers', label: 'Nhà cung cấp', permission: 'SUPPLIER_READ' }, { id: 'customers', label: 'Khách hàng', permission: 'CUSTOMER_READ' },
  ] },
  { label: 'Giao dịch & kho', items: [
    { id: 'inventory', label: 'Kho hàng' }, { id: 'purchases', label: 'Nhập hàng' },
    { id: 'sales', label: 'Bán hàng' }, { id: 'payments', label: 'Thanh toán' },
    { id: 'returns', label: 'Trả hàng' }, { id: 'refunds', label: 'Hoàn tiền' },
    { id: 'stocktake', label: 'Kiểm kê' }, { id: 'adjustments', label: 'Điều chỉnh tồn kho' },
  ] },
  { label: 'Quản lý', items: [{ id: 'employees', label: 'Nhân viên' }, { id: 'reports', label: 'Báo cáo' }] },
]

const destinations = navigationGroups.flatMap(group => group.items)
const navigationEvent = 'csm:navigate'

export function destinationPath(id) {
  return id === 'overview' ? '/dashboard' : `/${id}`
}

export function currentDestination() {
  const path = window.location.pathname.replace(/\/$/, '')
  return destinations.find(item => destinationPath(item.id) === path) ?? destinations[0]
}

export function subscribeNavigation(update) {
  window.addEventListener('popstate', update)
  window.addEventListener(navigationEvent, update)
  return () => {
    window.removeEventListener('popstate', update)
    window.removeEventListener(navigationEvent, update)
  }
}

export function navigateTo(id, replace = false) {
  const path = destinationPath(id)
  if (window.location.pathname === path && !window.location.search && !window.location.hash) return
  if (replace) window.history.replaceState(null, '', path)
  else window.history.pushState(null, '', path)
  window.dispatchEvent(new window.Event(navigationEvent))
}
