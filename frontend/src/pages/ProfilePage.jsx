import React, { useEffect, useState } from 'react';
import { get, post, put } from '../api';
import { Field, Spinner } from '../components/ui';
import { ROLE_LABELS, formatDate, formatDateTime, initials, passwordProblem } from '../utils';

export default function ProfilePage({ go, busy, onProfileUpdated, onLogout }) {
  const [me, setMe] = useState(null);
  const [loadErr, setLoadErr] = useState('');
  const [form, setForm] = useState({ fullName: '', gender: '', dateOfBirth: '', avatarUrl: '' });
  const [pw, setPw] = useState({ currentPassword: '', newPassword: '', confirm: '' });
  const [pwErr, setPwErr] = useState('');

  const fill = (u) => {
    setMe(u);
    setForm({ fullName: u.fullName || '', gender: u.gender || '', dateOfBirth: u.dateOfBirth || '', avatarUrl: u.avatarUrl || '' });
  };

  useEffect(() => {
    get('/api/me').then(fill).catch((e) => setLoadErr(e.message));
  }, []);

  async function save(e) {
    e.preventDefault();
    const d = await go(() =>
      put('/api/me', {
        fullName: form.fullName.trim(),
        gender: form.gender || null,
        dateOfBirth: form.dateOfBirth || null,
        avatarUrl: form.avatarUrl.trim() || null,
      })
    );
    if (d?.id) { fill(d); onProfileUpdated(d); }
  }

  async function changePassword(e) {
    e.preventDefault();
    setPwErr('');
    const problem = passwordProblem(pw.newPassword);
    if (problem) return setPwErr(problem);
    if (pw.newPassword !== pw.confirm) return setPwErr('Mật khẩu nhập lại không khớp.');
    const d = await go(() => post('/api/me/change-password', { currentPassword: pw.currentPassword, newPassword: pw.newPassword }));
    if (d) {
      window.alert('Đổi mật khẩu thành công. Vì lý do bảo mật, bạn cần đăng nhập lại.');
      onLogout();
    }
  }

  if (loadErr) return <div className="error">{loadErr}</div>;
  if (!me) return <div className="card"><Spinner /></div>;

  return (
    <div className="profile-layout">
      <aside className="card profile-side">
        <div className="avatar">
          {me.avatarUrl ? <img src={me.avatarUrl} alt="" onError={(e) => { e.currentTarget.style.display = 'none'; }} /> : initials(me.fullName)}
        </div>
        <h2>{me.fullName}</h2>
        <div style={{ marginTop: 8, display: 'flex', gap: 6, justifyContent: 'center', flexWrap: 'wrap' }}>
          <span className="badge b-red">{ROLE_LABELS[me.roleCode] || me.roleName}</span>
          <span className={`badge ${me.verified ? 'b-green' : 'b-amber'}`}>{me.verified ? 'Đã xác thực' : 'Chưa xác thực'}</span>
        </div>
        <div className="kv">
          <div><span>Email</span><b>{me.email || '—'}</b></div>
          <div><span>Điện thoại</span><b>{me.phoneNumber || '—'}</b></div>
          <div><span>Tham gia</span><b>{formatDate(me.createdAt)}</b></div>
          <div><span>Đăng nhập gần nhất</span><b>{formatDateTime(me.lastLoginAt)}</b></div>
        </div>
      </aside>

      <div>
        <form className="card" onSubmit={save}>
          <div className="card-head"><h2>Thông tin cá nhân</h2></div>
          <Field label="Họ và tên">
            <input value={form.fullName} onChange={(e) => setForm({ ...form, fullName: e.target.value })} minLength={2} maxLength={150} required />
          </Field>
          <div className="grid-2">
            <Field label="Giới tính">
              <select value={form.gender} onChange={(e) => setForm({ ...form, gender: e.target.value })}>
                <option value="">Không muốn nêu</option>
                <option value="MALE">Nam</option>
                <option value="FEMALE">Nữ</option>
                <option value="OTHER">Khác</option>
              </select>
            </Field>
            <Field label="Ngày sinh">
              <input type="date" value={form.dateOfBirth} max={new Date().toISOString().slice(0, 10)} onChange={(e) => setForm({ ...form, dateOfBirth: e.target.value })} />
            </Field>
          </div>
          <Field label="Ảnh đại diện (đường dẫn)" hint="Dán link ảnh, ví dụ https://…/anh.jpg">
            <input value={form.avatarUrl} onChange={(e) => setForm({ ...form, avatarUrl: e.target.value })} maxLength={255} />
          </Field>
          <div className="grid-2">
            <Field label="Email" hint="Liên hệ cửa hàng nếu cần đổi email.">
              <input value={me.email || ''} disabled />
            </Field>
            <Field label="Số điện thoại">
              <input value={me.phoneNumber || ''} disabled />
            </Field>
          </div>
          <button className="btn btn-primary" disabled={busy}>Lưu thay đổi</button>
        </form>

        <form className="card" onSubmit={changePassword}>
          <div className="card-head"><h2>Đổi mật khẩu</h2><small>Bạn sẽ được đăng xuất khỏi mọi thiết bị.</small></div>
          {pwErr && <div className="error">{pwErr}</div>}
          <Field label="Mật khẩu hiện tại">
            <input type="password" value={pw.currentPassword} onChange={(e) => setPw({ ...pw, currentPassword: e.target.value })} autoComplete="current-password" required />
          </Field>
          <div className="grid-2">
            <Field label="Mật khẩu mới" hint="Ít nhất 8 ký tự, gồm cả chữ và số.">
              <input type="password" value={pw.newPassword} onChange={(e) => setPw({ ...pw, newPassword: e.target.value })} autoComplete="new-password" required />
            </Field>
            <Field label="Nhập lại mật khẩu mới">
              <input type="password" value={pw.confirm} onChange={(e) => setPw({ ...pw, confirm: e.target.value })} autoComplete="new-password" required />
            </Field>
          </div>
          <button className="btn btn-outline" disabled={busy}>Đổi mật khẩu</button>
        </form>
      </div>
    </div>
  );
}
