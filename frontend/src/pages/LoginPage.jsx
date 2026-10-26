import React, { useState } from 'react';
import { post } from '../api';
import { Field } from '../components/ui';
import { passwordProblem } from '../utils';

export function AuthHero() {
  return (
    <aside className="auth-hero">
      <div className="logo">
        CS <span>Computer Store</span>
      </div>
      <div>
        <h1>Linh kiện chính hãng, build PC đúng nhu cầu.</h1>
        <p className="lead">Chọn linh kiện, kiểm tra tương thích tự động và nhận máy lắp sẵn tại cửa hàng hoặc tận nơi.</p>
        <ul className="perk-list">
          <li><span className="ico">✅</span> Kiểm tra tương thích CPU, mainboard, RAM, nguồn trước khi mua</li>
          <li><span className="ico">🛡️</span> Bảo hành theo số serial, tra cứu mọi lúc</li>
          <li><span className="ico">🔧</span> Lắp ráp, vệ sinh, sửa chữa tại cửa hàng hoặc tại nhà</li>
          <li><span className="ico">♻️</span> Thu cũ đổi mới, trợ giá khi nâng cấp</li>
        </ul>
      </div>
      <small>Hotline kỹ thuật 1900 6868 · 8:00 – 21:30 mỗi ngày</small>
    </aside>
  );
}

export default function LoginPage({ go, busy, msg, err, onLoginSuccess, onGoToVerifyRecovery }) {
  const [tab, setTab] = useState('login');
  const [login, setLogin] = useState({ identifier: '', password: '' });
  const [reg, setReg] = useState({ fullName: '', email: '', phoneNumber: '', password: '', confirm: '' });
  const [localErr, setLocalErr] = useState('');

  const setL = (k) => (e) => setLogin({ ...login, [k]: e.target.value });
  const setR = (k) => (e) => setReg({ ...reg, [k]: e.target.value });

  async function submitLogin(e) {
    e.preventDefault();
    setLocalErr('');
    const d = await go(() => post('/api/auth/login', { identifier: login.identifier.trim(), password: login.password }));
    if (d?.accessToken) onLoginSuccess(d);
  }

  async function submitRegister(e) {
    e.preventDefault();
    setLocalErr('');
    if (!reg.email.trim() && !reg.phoneNumber.trim()) return setLocalErr('Vui lòng nhập email hoặc số điện thoại.');
    const pwErr = passwordProblem(reg.password);
    if (pwErr) return setLocalErr(pwErr);
    if (reg.password !== reg.confirm) return setLocalErr('Mật khẩu nhập lại không khớp.');

    const d = await go(() =>
      post('/api/auth/register', {
        fullName: reg.fullName.trim(),
        email: reg.email.trim() || undefined,
        phoneNumber: reg.phoneNumber.trim() || undefined,
        password: reg.password,
      })
    );
    if (d) onGoToVerifyRecovery('verify', reg.email.trim() || reg.phoneNumber.trim());
  }

  const needVerify = err.includes('chưa được xác thực');

  return (
    <div className="auth-shell">
      <AuthHero />
      <section className="auth-panel">
        <div className="auth-card">
          <h2>{tab === 'login' ? 'Chào mừng trở lại' : 'Tạo tài khoản'}</h2>
          <p className="sub">
            {tab === 'login' ? 'Đăng nhập để xem giỏ hàng, đơn hàng và bảo hành.' : 'Chỉ mất một phút. Mã OTP sẽ được gửi để xác thực.'}
          </p>

          <div className="tabs" role="tablist">
            <button type="button" role="tab" className={tab === 'login' ? 'active' : ''} onClick={() => { setTab('login'); setLocalErr(''); }}>
              Đăng nhập
            </button>
            <button type="button" role="tab" className={tab === 'register' ? 'active' : ''} onClick={() => { setTab('register'); setLocalErr(''); }}>
              Đăng ký
            </button>
          </div>

          {msg && <div className="notice">{msg}</div>}
          {(localErr || err) && <div className="error">{localErr || err}</div>}

          {tab === 'login' ? (
            <form onSubmit={submitLogin}>
              <Field label="Email hoặc số điện thoại">
                <input value={login.identifier} onChange={setL('identifier')} placeholder="vd: 0912345678" autoComplete="username" required />
              </Field>
              <Field label="Mật khẩu">
                <input type="password" value={login.password} onChange={setL('password')} autoComplete="current-password" required />
              </Field>
              <div className="auth-links">
                {needVerify ? (
                  <button type="button" className="link-btn" onClick={() => onGoToVerifyRecovery('verify', login.identifier)}>
                    Nhập mã OTP để xác thực
                  </button>
                ) : <span />}
                <button type="button" className="link-btn" onClick={() => onGoToVerifyRecovery('forgot', login.identifier)}>
                  Quên mật khẩu?
                </button>
              </div>
              <button className="btn btn-primary btn-block btn-lg" disabled={busy}>
                {busy ? 'Đang đăng nhập…' : 'Đăng nhập'}
              </button>
              <p className="auth-foot">
                Chưa có tài khoản? <button type="button" className="link-btn" onClick={() => setTab('register')}>Đăng ký ngay</button>
              </p>
            </form>
          ) : (
            <form onSubmit={submitRegister}>
              <Field label="Họ và tên">
                <input value={reg.fullName} onChange={setR('fullName')} minLength={2} maxLength={150} autoComplete="name" required />
              </Field>
              <div className="grid-2">
                <Field label="Email">
                  <input type="email" value={reg.email} onChange={setR('email')} autoComplete="email" />
                </Field>
                <Field label="Số điện thoại">
                  <input value={reg.phoneNumber} onChange={setR('phoneNumber')} inputMode="tel" autoComplete="tel" />
                </Field>
              </div>
              <Field label="Mật khẩu" hint="Ít nhất 8 ký tự, gồm cả chữ và số.">
                <input type="password" value={reg.password} onChange={setR('password')} autoComplete="new-password" required />
              </Field>
              <Field label="Nhập lại mật khẩu">
                <input type="password" value={reg.confirm} onChange={setR('confirm')} autoComplete="new-password" required />
              </Field>
              <button className="btn btn-primary btn-block btn-lg" disabled={busy}>
                {busy ? 'Đang tạo tài khoản…' : 'Tạo tài khoản'}
              </button>
              <p className="auth-foot">
                Đã có tài khoản? <button type="button" className="link-btn" onClick={() => setTab('login')}>Đăng nhập</button>
              </p>
            </form>
          )}
        </div>
      </section>
    </div>
  );
}
