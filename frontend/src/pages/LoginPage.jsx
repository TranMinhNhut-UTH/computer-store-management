import React, { useState } from 'react';
import { post } from '../api';
import { Field, Form, Submit } from '../components/UI';

const LOGIN_TABS = [
  ['login', 'Đăng nhập'],
  ['register', 'Đăng ký thành viên'],
];

export default function LoginPage({
  go,
  busy,
  msg,
  err,
  onLoginSuccess,
  onGoToVerifyRecovery,
}) {
  const [mode, setMode] = useState('login');

  const handleLogin = async (e) => {
    e.preventDefault();
    const f = Object.fromEntries(new FormData(e.currentTarget));
    const res = await go(() =>
      post('/api/auth/login', {
        identifier: f.identifier,
        password: f.password,
      })
    );
    if (res?.accessToken && onLoginSuccess) {
      onLoginSuccess();
    }
  };

  const handleRegister = async (e) => {
    e.preventDefault();
    const f = Object.fromEntries(new FormData(e.currentTarget));
    const payload = {
      fullName: f.fullName,
      email: f.email || null,
      phoneNumber: f.phoneNumber || null,
      password: f.password,
    };
    const res = await go(() => post('/api/auth/register', payload));
    if (res) {
      const identifier = f.email || f.phoneNumber || '';
      onGoToVerifyRecovery('verify', identifier);
    }
  };

  return (
    <div className="auth-screen">
      <div className="auth-grid">
        {/* Cột giới thiệu thương hiệu Linh kiện PC */}
        <div className="auth-showcase">
          <div className="tech-badge">⚡ NEXT-GEN PC HARDWARE STORE</div>
          <h1>
            Nâng cấp cấu hình, <br />
            <span>Bứt phá hiệu năng.</span>
          </h1>
          <p>
            Hệ thống phân phối chính hãng CPU, VGA, Mainboard, RAM, SSD & Hi-End Custom PC dành cho Game thủ và Creator chuyên nghiệp.
          </p>
          <div className="hardware-specs">
            <div className="spec-item">
              <strong>RTX 50-Series</strong>
              <span>Kiến trúc GPU thế hệ mới</span>
            </div>
            <div className="spec-item">
              <strong>DDR5 & PCIe 5.0</strong>
              <span>Tốc độ băng thông cực đại</span>
            </div>
            <div className="spec-item">
              <strong>Bảo hành 36T</strong>
              <span>Lỗi 1 đổi 1 tận nơi</span>
            </div>
          </div>
        </div>

        {/* Cột Form Đăng nhập / Đăng ký */}
        <div className="auth-container">
          <div className="auth-brand">
            <div className="logo">
              CS <span>COMPUTER STORE</span>
            </div>
            <small>CỔNG TÀI KHOẢN KHÁCH HÀNG & QUẢN TRỊ</small>
          </div>

          <section className="card auth-card">
            <div className="tabs">
              {LOGIN_TABS.map(([id, label]) => (
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

            {mode === 'login' && (
              <Form
                title="Đăng nhập hệ thống"
                subtitle="Nhập thông tin tài khoản để mua sắm linh kiện và theo dõi bảo hành."
                onSubmit={handleLogin}
                busy={busy}
              >
                <Field
                  name="identifier"
                  label="Email hoặc Số điện thoại"
                  placeholder="VD: gamer@computerstore.vn"
                  required
                />
                <Field
                  name="password"
                  label="Mật khẩu"
                  type="password"
                  placeholder="••••••••"
                  required
                />
                <Submit disabled={busy}>Đăng nhập ngay</Submit>

                <div className="auth-links">
                  <button
                    type="button"
                    className="link-btn"
                    onClick={() => onGoToVerifyRecovery('forgot')}
                  >
                    Quên mật khẩu?
                  </button>
                  <button
                    type="button"
                    className="link-btn"
                    onClick={() => onGoToVerifyRecovery('verify')}
                  >
                    Kích hoạt mã OTP →
                  </button>
                </div>
              </Form>
            )}

            {mode === 'register' && (
              <Form
                title="Đăng ký tài khoản mới"
                subtitle="Trở thành thành viên để nhận ưu đãi Build PC và tích điểm đổi quà."
                onSubmit={handleRegister}
                busy={busy}
              >
                <Field name="fullName" label="Họ và tên" placeholder="Nguyễn Văn A" required />
                <Field name="email" label="Địa chỉ Email" type="email" placeholder="you@example.com" />
                <Field name="phoneNumber" label="Số điện thoại" placeholder="09xxxxxxxx" />
                <Field
                  name="password"
                  label="Mật khẩu bảo mật (tối thiểu 8 ký tự)"
                  type="password"
                  minLength="8"
                  placeholder="Tối thiểu 8 ký tự"
                  required
                />
                <Submit disabled={busy}>Đăng ký & Nhận mã OTP</Submit>

                <div className="auth-links">
                  <button
                    type="button"
                    className="link-btn"
                    onClick={() => onGoToVerifyRecovery('verify')}
                  >
                    Đã nhận được mã OTP? Nhập mã ngay →
                  </button>
                </div>
              </Form>
            )}
          </section>
        </div>
      </div>
    </div>
  );
}