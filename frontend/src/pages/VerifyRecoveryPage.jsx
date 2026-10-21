import React, { useState } from 'react';
import { post } from '../api';
import { Field, Form, Submit } from '../components/UI';

const RECOVERY_TABS = [
  ['verify', 'Xác thực OTP'],
  ['resend', 'Gửi lại OTP'],
  ['forgot', 'Quên mật khẩu'],
  ['reset', 'Đặt lại mật khẩu'],
];

export default function VerifyRecoveryPage({
  go,
  busy,
  msg,
  err,
  initialMode = 'verify',
  initialIdentifier = '',
  onBackToLogin,
}) {
  const [mode, setMode] = useState(initialMode);
  const [identifier, setIdentifier] = useState(initialIdentifier);

  const handleVerify = async (e) => {
    e.preventDefault();
    const f = Object.fromEntries(new FormData(e.currentTarget));
    const res = await go(() =>
      post('/api/auth/verify-otp', {
        identifier: f.identifier,
        code: f.code,
      })
    );
    if (res) onBackToLogin();
  };

  const handleResend = async (e) => {
    e.preventDefault();
    const f = Object.fromEntries(new FormData(e.currentTarget));
    setIdentifier(f.identifier);
    const res = await go(() =>
      post('/api/auth/resend-otp', {
        identifier: f.identifier,
      })
    );
    if (res) setMode('verify');
  };

  const handleForgot = async (e) => {
    e.preventDefault();
    const f = Object.fromEntries(new FormData(e.currentTarget));
    setIdentifier(f.identifier);
    const res = await go(() =>
      post('/api/auth/forgot-password', {
        identifier: f.identifier,
      })
    );
    if (res) setMode('reset');
  };

  const handleReset = async (e) => {
    e.preventDefault();
    const f = Object.fromEntries(new FormData(e.currentTarget));
    const res = await go(() =>
      post('/api/auth/reset-password', {
        identifier: f.identifier,
        code: f.code,
        newPassword: f.newPassword,
      })
    );
    if (res) onBackToLogin();
  };

  return (
    <div className="auth-screen">
      <div className="auth-container">
        <div className="auth-brand">
          <div className="logo">
            CS <span>COMPUTER STORE</span>
          </div>
          <small>TRUNG TÂM BẢO MẬT & XÁC THỰC TÀI KHOẢN</small>
        </div>

        <section className="card auth-card">
          <div style={{ marginBottom: '16px' }}>
            <button type="button" className="back-link-btn" onClick={onBackToLogin}>
              ← Quay lại trang Đăng nhập
            </button>
          </div>

          <div className="tabs">
            {RECOVERY_TABS.map(([id, label]) => (
              <button
                type="button"
                className={mode === id ? 'sel' : ''}
                onClick={() => setMode(id)}
                key={id}
              >
                {label}
              </button>
            ))}
          </div>

          {msg && <div className="notice">{msg}</div>}
          {err && <div className="error">{err}</div>}

          {mode === 'verify' && (
            <Form
              key="verify-form"
              title="Kích hoạt bằng mã OTP"
              subtitle="Nhập mã 6 chữ số đã được gửi tới Email hoặc SMS của bạn."
              onSubmit={handleVerify}
              busy={busy}
            >
              <Field
                name="identifier"
                label="Email hoặc Số điện thoại"
                defaultValue={identifier}
                required
              />
              <Field
                name="code"
                label="Mã xác thực OTP (6 số)"
                placeholder="123456"
                minLength="6"
                maxLength="6"
                required
              />
              <Submit disabled={busy}>Xác nhận kích hoạt</Submit>
              <div className="auth-links">
                <button type="button" className="link-btn" onClick={() => setMode('resend')}>
                  Chưa nhận được mã? Gửi lại OTP
                </button>
              </div>
            </Form>
          )}

          {mode === 'resend' && (
            <Form
              key="resend-form"
              title="Yêu cầu gửi lại mã OTP"
              subtitle="Hệ thống sẽ cấp lại mã OTP mới cho tài khoản của bạn."
              onSubmit={handleResend}
              busy={busy}
            >
              <Field
                name="identifier"
                label="Email hoặc Số điện thoại"
                defaultValue={identifier}
                required
              />
              <Submit disabled={busy}>Gửi mã OTP mới</Submit>
            </Form>
          )}

          {mode === 'forgot' && (
            <Form
              key="forgot-form"
              title="Khôi phục mật khẩu"
              subtitle="Nhập Email hoặc SĐT đã đăng ký để nhận mã khôi phục mật khẩu."
              onSubmit={handleForgot}
              busy={busy}
            >
              <Field
                name="identifier"
                label="Email hoặc Số điện thoại"
                defaultValue={identifier}
                required
              />
              <Submit disabled={busy}>Gửi mã khôi phục</Submit>
              <div className="auth-links">
                <button type="button" className="link-btn" onClick={() => setMode('reset')}>
                  Đã có mã OTP? Đặt lại mật khẩu ngay →
                </button>
              </div>
            </Form>
          )}

          {mode === 'reset' && (
            <Form
              key="reset-form"
              title="Thiết lập mật khẩu mới"
              subtitle="Sử dụng mã OTP vừa nhận được để tạo mật khẩu mới."
              onSubmit={handleReset}
              busy={busy}
            >
              <Field
                name="identifier"
                label="Email hoặc Số điện thoại"
                defaultValue={identifier}
                required
              />
              <Field
                name="code"
                label="Mã OTP khôi phục (6 chữ số)"
                minLength="6"
                maxLength="6"
                required
              />
              <Field
                name="newPassword"
                label="Mật khẩu mới (tối thiểu 8 ký tự)"
                type="password"
                minLength="8"
                required
              />
              <Submit disabled={busy}>Hoàn tất đổi mật khẩu</Submit>
            </Form>
          )}
        </section>
      </div>
    </div>
  );
}