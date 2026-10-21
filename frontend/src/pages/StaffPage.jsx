import React, { useEffect, useState } from 'react';
import { api, post } from '../api';
import { Field, Form, Submit, Table } from '../components/UI';

const STAFF_LABELS = {
  id: 'Mã NV',
  fullName: 'Họ và tên',
  email: 'Email công việc',
  phoneNumber: 'Số điện thoại',
  roleCode: 'Chức vụ / Vai trò',
  status: 'Trạng thái',
};

export default function StaffPage({ go, busy }) {
  const [staff, setStaff] = useState([]);
  const [roles, setRoles] = useState([]);

  async function loadStaff() {
    const d = await api('/api/staff?' + new URLSearchParams({ page: '1', size: '50' }));
    const list = d?.items || d?.content || d?.data || [];
    setStaff(list);
    return d;
  }

  async function loadRoles() {
    const d = await api('/api/staff/roles');
    setRoles(Array.isArray(d) ? d : []);
    return d;
  }

  async function loadAll() {
    await loadRoles();
    return await loadStaff();
  }

  useEffect(() => {
    go(loadAll);
  }, []);

  const handleCreateStaff = async (e) => {
    e.preventDefault();
    const form = e.currentTarget;
    const f = Object.fromEntries(new FormData(form));
    await go(async () => {
      const d = await post('/api/staff', f);
      await loadStaff();
      form.reset();
      return d;
    });
  };

  return (
    <div className="layout">
      <div className="card">
        <Form
          title="Cấp tài khoản nhân viên mới"
          subtitle="Dành cho Quản lý cửa hàng (QUAN_LY) thêm kỹ thuật viên, thu ngân hoặc CSKH."
          onSubmit={handleCreateStaff}
          busy={busy}
        >
          <Field name="fullName" label="Họ và tên nhân viên" required />
          <Field name="email" label="Email nội bộ" type="email" required />
          <Field name="phoneNumber" label="Số điện thoại" />
          <Field name="password" label="Mật khẩu khởi tạo" type="password" minLength="8" required />
          <label className="field-group">
            <span>Vai trò hệ thống<em> *</em></span>
            <select name="roleCode" required defaultValue="">
              <option value="" disabled>
                -- Chọn vai trò --
              </option>
              {roles.map((r) => (
                <option key={r} value={r}>
                  {r}
                </option>
              ))}
            </select>
          </label>
          <Submit disabled={busy}>+ Tạo tài khoản nhân sự</Submit>
        </Form>
      </div>

      <div className="card">
        <div className="bar">
          <h2>Danh sách nhân sự ({staff.length})</h2>
          <button type="button" onClick={() => go(loadAll)}>
            Làm mới
          </button>
        </div>

        <Table
          rows={staff}
          cols={['id', 'fullName', 'email', 'phoneNumber', 'roleCode', 'status']}
          labels={STAFF_LABELS}
        />
      </div>
    </div>
  );
}