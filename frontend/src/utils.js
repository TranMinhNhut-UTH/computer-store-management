export const formatVND = (n) =>
  new Intl.NumberFormat('vi-VN', { style: 'currency', currency: 'VND', maximumFractionDigits: 0 }).format(n || 0);

export const formatDateTime = (iso) => (iso ? new Date(iso).toLocaleString('vi-VN') : '—');

export const formatDate = (iso) => (iso ? new Date(iso).toLocaleDateString('vi-VN') : '—');

export const initials = (name = '') =>
  name.trim().split(/\s+/).slice(-2).map((w) => w[0]?.toUpperCase()).join('') || 'CS';

/** Tạo query string, bỏ qua giá trị rỗng. */
export const qs = (params) => {
  const sp = new URLSearchParams();
  Object.entries(params).forEach(([k, v]) => {
    if (v !== undefined && v !== null && v !== '') sp.set(k, v);
  });
  const s = sp.toString();
  return s ? `?${s}` : '';
};

export const ROLE_LABELS = {
  QUAN_LY: 'Quản lý',
  LE_TAN: 'Lễ tân',
  THO: 'Thợ kỹ thuật',
  KE_TOAN: 'Kế toán',
  THU_KHO: 'Thủ kho',
  KHACH_HANG: 'Khách hàng',
};

export const passwordProblem = (pw) =>
  pw.length < 8 || !/[A-Za-zÀ-ỹ]/.test(pw) || !/\d/.test(pw)
    ? 'Mật khẩu cần ít nhất 8 ký tự, gồm cả chữ và số.'
    : '';
