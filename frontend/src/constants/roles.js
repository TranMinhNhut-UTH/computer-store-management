// src/constants/roles.js

export const ROLES = {
  GUEST: 'GUEST',
  KHACH_HANG: 'KHACH_HANG',
  LE_TAN: 'LE_TAN',
  THO: 'THO',
  KE_TOAN: 'KE_TOAN',
  THU_KHO: 'THU_KHO',
  QUAN_LY: 'QUAN_LY',
};

export const ROLE_CONFIG = {
  [ROLES.GUEST]: {
    code: ROLES.GUEST,
    label: 'Khách vãng lai',
    badge: 'b-gray',
    description: 'Xem sản phẩm, Build PC, gửi liên hệ, mua hàng không cần tài khoản.',
    permissions: ['view_products', 'use_build_pc', 'guest_checkout'],
  },
  [ROLES.KHACH_HANG]: {
    code: ROLES.KHACH_HANG,
    label: 'Khách hàng',
    badge: 'b-blue',
    description: 'Quản lý tài khoản, giỏ hàng, tra cứu đơn hàng, bảo hành & đánh giá.',
    permissions: ['manage_profile', 'manage_addresses', 'place_orders', 'track_warranty', 'request_return'],
  },
  [ROLES.LE_TAN]: {
    code: ROLES.LE_TAN,
    label: 'Lễ tân',
    badge: 'b-blue',
    description: 'Tiếp nhận khách, tạo đơn quầy, tiếp nhận máy sửa/bảo hành, xác nhận đơn online.',
    permissions: ['reception_desk', 'create_counter_order', 'confirm_online_order', 'manage_appointments'],
  },
  [ROLES.THO]: {
    code: ROLES.THO,
    label: 'Thợ kỹ thuật',
    badge: 'b-amber',
    description: 'Xem việc được giao, cập nhật trạng thái lắp ráp/sửa chữa, ghi nhận linh kiện.',
    permissions: ['view_tech_jobs', 'update_job_status', 'report_spare_parts', 'onsite_service'],
  },
  [ROLES.KE_TOAN]: {
    code: ROLES.KE_TOAN,
    label: 'Kế toán',
    badge: 'b-green',
    description: 'Quản lý hóa đơn điện tử, đối soát thanh toán, công nợ & báo cáo tài chính.',
    permissions: ['manage_invoices', 'reconcile_payments', 'view_financial_reports', 'manage_debt'],
  },
  [ROLES.THU_KHO]: {
    code: ROLES.THU_KHO,
    label: 'Thủ kho',
    badge: 'b-green',
    description: 'Nhập/xuất kho, kiểm kê, quản lý nhà cung cấp, xử lý cảnh báo tồn kho thấp.',
    permissions: ['manage_inventory', 'import_export_stock', 'manage_suppliers', 'stock_audit'],
  },
  [ROLES.QUAN_LY]: {
    code: ROLES.QUAN_LY,
    label: 'Quản lý (Admin)',
    badge: 'b-red',
    description: 'Toàn quyền hệ thống, quản lý nhân viên, duyệt giảm giá & xem toàn bộ báo cáo.',
    permissions: ['full_access', 'manage_staff', 'approve_discounts', 'system_config', 'view_all_reports'],
  },
};