import React, { useCallback, useEffect, useState } from 'react';
import { get, patch, post, put } from '../api';
import { Empty, Field, Modal, Pager, Spinner } from '../components/ui';
import { ROLE_LABELS, formatDateTime, passwordProblem, qs } from '../utils';

const STAFF_ROLES = ['QUAN_LY', 'LE_TAN', 'THO', 'KE_TOAN', 'THU_KHO'];
const SIZE = 10;
const NEW = { fullName: '', email: '', phoneNumber: '', password: '', roleCode: 'LE_TAN' };

export default function StaffPage({ go, busy }) {
  const [tab, setTab] = useState('staff'); // 'staff' | 'roles'
  const [filters, setFilters] = useState({ search: '', roleCode: '', status: '' });
  const [page, setPage] = useState(1);
  const [data, setData] = useState(null);
  const [loadErr, setLoadErr] = useState('');
  const [form, setForm] = useState(null);
  const [formErr, setFormErr] = useState('');

  const load = useCallback(() => {
    setLoadErr('');
    get(`/api/staff${qs({ ...filters, page, size: SIZE })}`).then(setData).catch((e) => setLoadErr(e.message));
  }, [filters, page]);
  useEffect(() => {
    if (tab === 'staff') load();
  }, [load, tab]);

  const setF = (k) => (e) => { setFilters({ ...filters, [k]: e.target.value }); setPage(1); };
  const set = (k) => (e) => setForm({ ...form, [k]: e.target.value });

  async function save(e) {
    e.preventDefault();
    setFormErr('');
    let d;
    if (form.id) {
      d = await go(() => put(`/api/staff/${form.id}`, { fullName: form.fullName.trim(), phoneNumber: form.phoneNumber?.trim() || null, roleCode: form.roleCode }));
    } else {
      const problem = passwordProblem(form.password);
      if (problem) return setFormErr(problem);
      d = await go(() => post('/api/staff', {
        fullName: form.fullName.trim(), email: form.email.trim(), phoneNumber: form.phoneNumber.trim() || null,
        password: form.password, roleCode: form.roleCode,
      }));
    }
    if (d) { setForm(null); load(); }
  }

  async function toggle(u) {
    const lock = u.status === 'ACTIVE';
    if (!window.confirm(lock ? `Khóa tài khoản của ${u.fullName}? Họ sẽ bị đăng xuất ngay.` : `Mở khóa tài khoản của ${u.fullName}?`)) return;
    if (await go(() => patch(`/api/staff/${u.id}/status`, { status: lock ? 'LOCKED' : 'ACTIVE' }))) load();
  }

  return (
    <>
      {/* Tabs chuyển đổi giữa Quản lý Nhân sự và Ma trận Phân quyền */}
      <div className="tabs" style={{ maxWidth: '440px', marginBottom: '16px' }}>
        <button type="button" className={tab === 'staff' ? 'active' : ''} onClick={() => setTab('staff')}>
          👥 Tài khoản nhân sự
        </button>
        <button type="button" className={tab === 'roles' ? 'active' : ''} onClick={() => setTab('roles')}>
          🔐 Ma trận phân quyền (RBAC)
        </button>
      </div>

      {tab === 'staff' ? (
        <div className="card">
          <div className="card-head">
            <h2>Tài khoản nhân viên cửa hàng</h2>
            <button className="btn btn-primary" onClick={() => { setForm({ ...NEW }); setFormErr(''); }}>+ Thêm nhân viên</button>
          </div>
          <div className="toolbar">
            <Field label="Tìm kiếm"><input type="search" value={filters.search} onChange={setF('search')} placeholder="Tên, email hoặc số điện thoại" /></Field>
            <Field label="Vai trò">
              <select value={filters.roleCode} onChange={setF('roleCode')}>
                <option value="">Tất cả</option>
                {STAFF_ROLES.map((r) => <option key={r} value={r}>{ROLE_LABELS[r]}</option>)}
              </select>
            </Field>
            <Field label="Trạng thái">
              <select value={filters.status} onChange={setF('status')}>
                <option value="">Tất cả</option>
                <option value="ACTIVE">Đang hoạt động</option>
                <option value="LOCKED">Đã khóa</option>
              </select>
            </Field>
          </div>

          {loadErr && <div className="error">{loadErr}</div>}
          {!data && !loadErr && <Spinner />}
          {data && data.items.length === 0 && <Empty icon="🧑‍💼" title="Không có nhân viên phù hợp" text="Đổi bộ lọc hoặc thêm nhân viên mới." />}
          {data && data.items.length > 0 && (
            <>
              <div className="table-wrap">
                <table className="table">
                  <thead><tr><th>Nhân viên</th><th>Liên hệ</th><th>Vai trò</th><th>Trạng thái</th><th>Đăng nhập gần nhất</th><th /></tr></thead>
                  <tbody>
                    {data.items.map((u) => (
                      <tr key={u.id}>
                        <td><div className="cell-main">{u.fullName}</div><div className="cell-sub">#{u.id}</div></td>
                        <td>{u.email}<div className="cell-sub">{u.phoneNumber || '—'}</div></td>
                        <td><span className="badge b-blue">{ROLE_LABELS[u.roleCode] || u.roleName}</span></td>
                        <td><span className={`badge ${u.status === 'ACTIVE' ? 'b-green' : 'b-red'}`}>{u.status === 'ACTIVE' ? 'Hoạt động' : 'Đã khóa'}</span></td>
                        <td>{formatDateTime(u.lastLoginAt)}</td>
                        <td>
                          <div className="actions">
                            <button className="btn btn-ghost btn-sm" onClick={() => { setForm({ ...u, phoneNumber: u.phoneNumber || '' }); setFormErr(''); }}>Sửa</button>
                            <button className={`btn btn-sm ${u.status === 'ACTIVE' ? 'btn-danger' : 'btn-outline'}`} disabled={busy} onClick={() => toggle(u)}>
                              {u.status === 'ACTIVE' ? 'Khóa' : 'Mở khóa'}
                            </button>
                          </div>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
              <Pager page={data.page} pageSize={data.pageSize} total={data.total} onChange={setPage} />
            </>
          )}
        </div>
      ) : (
        <div className="card">
          <div className="card-head">
            <h2>Hệ thống phân quyền chi tiết (RBAC)</h2>
            <small>Định nghĩa các nhóm quyền từ Khách vãng lai đến Quản lý hệ thống</small>
          </div>
          <div className="table-wrap" style={{ marginTop: '12px' }}>
            <table className="table">
              <thead>
                <tr>
                  <th>Mã Role</th>
                  <th>Tên Vai Trò</th>
                  <th>Mô tả chức năng nghiệp vụ</th>
                </tr>
              </thead>
              <tbody>
                <tr>
                  <td><code>GUEST</code></td>
                  <td><b>Khách vãng lai</b></td>
                  <td>Xem danh mục, tìm kiếm, xem sản phẩm, Build PC, gửi liên hệ và đặt hàng không cần tài khoản.</td>
                </tr>
                <tr>
                  <td><span className="badge b-blue">KHACH_HANG</span></td>
                  <td><b>Khách hàng</b></td>
                  <td>Quản lý tài khoản, sổ địa chỉ, giỏ hàng, tra cứu đơn hàng, lịch sử mua hàng, bảo hành & đánh giá.</td>
                </tr>
                <tr>
                  <td><span className="badge b-blue">LE_TAN</span></td>
                  <td><b>Lễ tân</b></td>
                  <td>Tiếp nhận và tư vấn khách, tạo đơn quầy, tiếp nhận máy sửa/bảo hành, xác nhận đơn online, xếp lịch.</td>
                </tr>
                <tr>
                  <td><span className="badge b-amber">THO</span></td>
                  <td><b>Thợ kỹ thuật</b></td>
                  <td>Xem danh mục công việc, cập nhật trạng thái lắp ráp/sửa chữa, ghi nhận linh kiện và báo giá phát sinh.</td>
                </tr>
                <tr>
                  <td><span className="badge b-green">KE_TOAN</span></td>
                  <td><b>Kế toán</b></td>
                  <td>Quản lý hóa đơn thuế, xuất hóa đơn điện tử, đối soát thanh toán, công nợ và báo cáo tài chính.</td>
                </tr>
                <tr>
                  <td><span className="badge b-green">THU_KHO</span></td>
                  <td><b>Thủ kho</b></td>
                  <td>Nhập/xuất kho, kiểm kê, quản lý nhà cung cấp, xử lý cảnh báo tồn kho thấp.</td>
                </tr>
                <tr>
                  <td><span className="badge b-red">QUAN_LY</span></td>
                  <td><b>Quản lý (Admin)</b></td>
                  <td>Toàn quyền hệ thống, quản lý nhân sự, duyệt giảm giá, chương trình khuyến mãi và xem toàn bộ báo cáo.</td>
                </tr>
              </tbody>
            </table>
          </div>
        </div>
      )}

      {form && (
        <Modal
          title={form.id ? 'Sửa thông tin nhân viên' : 'Thêm nhân viên mới'}
          onClose={() => setForm(null)}
          footer={
            <>
              <button type="button" className="btn btn-ghost" onClick={() => setForm(null)}>Hủy</button>
              <button type="submit" form="staff-form" className="btn btn-primary" disabled={busy}>{form.id ? 'Lưu thay đổi' : 'Tạo tài khoản'}</button>
            </>
          }
        >
          <form id="staff-form" onSubmit={save}>
            {formErr && <div className="error">{formErr}</div>}
            <Field label="Họ và tên"><input value={form.fullName} onChange={set('fullName')} minLength={2} required /></Field>
            <Field label="Email" hint={form.id ? 'Không thể đổi email.' : 'Dùng để đăng nhập.'}>
              <input type="email" value={form.email} onChange={set('email')} disabled={!!form.id} required />
            </Field>
            <div className="grid-2">
              <Field label="Số điện thoại"><input value={form.phoneNumber} onChange={set('phoneNumber')} inputMode="tel" /></Field>
              <Field label="Vai trò">
                <select value={form.roleCode} onChange={set('roleCode')}>
                  {STAFF_ROLES.map((r) => <option key={r} value={r}>{ROLE_LABELS[r]}</option>)}
                </select>
              </Field>
            </div>
            {!form.id && (
              <Field label="Mật khẩu ban đầu" hint="Ít nhất 8 ký tự, gồm cả chữ và số. Nhân viên nên đổi mật khẩu sau khi đăng nhập.">
                <input type="password" value={form.password} onChange={set('password')} autoComplete="new-password" required />
              </Field>
            )}
          </form>
        </Modal>
      )}
    </>
  );
}