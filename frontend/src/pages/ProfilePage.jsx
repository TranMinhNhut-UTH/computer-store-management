import React, { useEffect, useState } from 'react';
import { api, post, put } from '../api';
import { Field, Form, Submit } from '../components/UI';

export default function ProfilePage({ go, busy }) {
  const [profile, setProfile] = useState(null);

  async function loadProfile() {
    const d = await api('/api/me');
    setProfile(d);
    return d;
  }

  useEffect(() => {
    go(loadProfile);
  }, []);

  const handleUpdateProfile = async (e) => {
    e.preventDefault();
    const f = Object.fromEntries(new FormData(e.currentTarget));
    await go(async () => {
      const d = await put('/api/me', {
        fullName: f.fullName,
        gender: f.gender || null,
        dateOfBirth: f.dateOfBirth || null,
        avatarUrl: f.avatarUrl || null,
      });
      setProfile(d);
      return d;
    });
  };

  const handleChangePassword = async (e) => {
    e.preventDefault();
    const form = e.currentTarget;
    const f = Object.fromEntries(new FormData(form));
    await go(() => post('/api/me/change-password', f));
    form.reset();
  };

  return (
    <div className="layout">
      <div className="card">
        <div className="profile-header">
          <div className="avatar-circle">
            {profile?.avatarUrl ? (
              <img src={profile.avatarUrl} alt="Avatar" />
            ) : (
              <span>{(profile?.fullName || 'CS').charAt(0).toUpperCase()}</span>
            )}
          </div>
          <div>
            <h2>{profile?.fullName || 'Thành viên Computer Store'}</h2>
            <p className="muted">
              {profile?.email || profile?.phoneNumber || 'Tài khoản mua sắm linh kiện & bảo hành điện tử'}
            </p>
          </div>
        </div>

        <div className="divider" />

        <Form
          key={profile?.id || 'profile-form'}
          title="Thông tin cá nhân"
          subtitle="Cập nhật thông tin để nhận thông báo bảo hành và ưu đãi thành viên."
          onSubmit={handleUpdateProfile}
          busy={busy}
        >
          <Field name="fullName" label="Họ và tên" defaultValue={profile?.fullName || ''} required />
          <label className="field-group">
            <span>Giới tính</span>
            <select name="gender" defaultValue={profile?.gender || ''}>
              <option value="">Chưa chọn</option>
              <option value="MALE">Nam</option>
              <option value="FEMALE">Nữ</option>
              <option value="OTHER">Khác</option>
            </select>
          </label>
          <Field name="dateOfBirth" label="Ngày sinh" type="date" defaultValue={profile?.dateOfBirth || ''} />
          <Field
            name="avatarUrl"
            label="Đường dẫn ảnh đại diện (Avatar URL)"
            placeholder="https://example.com/avatar.png"
            defaultValue={profile?.avatarUrl || ''}
          />
          <Submit disabled={busy}>Lưu thay đổi</Submit>
        </Form>
      </div>

      <div className="card">
        <Form
          title="Bảo mật & Đổi mật khẩu"
          subtitle="Nên sử dụng mật khẩu mạnh để bảo vệ lịch sử mua hàng của bạn."
          onSubmit={handleChangePassword}
          busy={busy}
        >
          <Field name="currentPassword" label="Mật khẩu hiện tại" type="password" required />
          <Field
            name="newPassword"
            label="Mật khẩu mới (tối thiểu 8 ký tự)"
            type="password"
            minLength="8"
            required
          />
          <Submit disabled={busy}>Cập nhật mật khẩu</Submit>
        </Form>
      </div>
    </div>
  );
}