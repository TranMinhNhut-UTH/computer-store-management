// Navigation metadata only. These entries do not grant permissions or call business APIs.
export const navigationGroups = [
  { label: 'Không gian làm việc', items: [{ id: 'overview', label: 'Tổng quan' }] },
  { label: 'Dữ liệu danh mục', items: [
    { id: 'categories', label: 'Danh mục' }, { id: 'products', label: 'Sản phẩm' },
    { id: 'suppliers', label: 'Nhà cung cấp' }, { id: 'customers', label: 'Khách hàng' },
  ] },
  { label: 'Giao dịch & kho', items: [
    { id: 'inventory', label: 'Kho hàng' }, { id: 'purchases', label: 'Nhập hàng' },
    { id: 'sales', label: 'Bán hàng' }, { id: 'payments', label: 'Thanh toán' },
    { id: 'returns', label: 'Trả hàng' }, { id: 'refunds', label: 'Hoàn tiền' },
    { id: 'stocktake', label: 'Kiểm kê' }, { id: 'adjustments', label: 'Điều chỉnh tồn kho' },
  ] },
  { label: 'Quản lý', items: [{ id: 'employees', label: 'Nhân viên' }, { id: 'reports', label: 'Báo cáo' }] },
]
