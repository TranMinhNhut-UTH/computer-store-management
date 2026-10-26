import React, { useEffect, useState } from 'react';
import { post } from '../api';
import { Field } from '../components/ui';
import { passwordProblem } from '../utils';
import { AuthHero } from './LoginPage';

const COOLDOWN = 60;

/**
 * 3 chế độ:
 *  - verify: nhập OTP xác thực tài khoản vừa đăng ký
 *  - forgot: nhập email/SĐT để nhận mã đặt lại mật khẩu
 *  - reset : nhập mã + mật khẩu mới
 */
export default function VerifyRecoveryPage({
  go, busy, msg, err, initialMode = 'verify', initialIdentifier = '', onBackToLogin, onLoginSuccess,
}) {
  const [mode, setMode] = useState(initialMode);
  const [identifier, setIdentifier] = useState(initialIdentifier);
  const [code, setCode] = useState('');
  const [pw, setPw] = useState({ next: '', confirm: '' });
  const [localErr, setLocalErr] = useState('');
  const [cooldown, setCooldown] = useState(0);
  const [done, setDone] = useState(false);

  useEffect(() => {
    if (cooldown <= 0) return undefined;
    const t = setTimeout(() => setCooldown((c) => c - 1), 1000);
    return () => clearTimeout(t);
  }, [cooldown]);

  const clean = (v) => v.replace(/\D/g, '').slice(0, 6);

  async function verify(e) {
    e.preventDefault();
    setLocalErr('');
    const d = await go(() => post('/api/auth/verify-otp', { identifier: identifier.trim(), code }));
    if (d?.accessToken) onLoginSuccess(d);
  }

  async function resend() {
    setLocalErr('');
    const path = mode === 'verify' ? '/api/auth/resend-otp' : '/api/auth/forgot-password';
    const d = await go(() => post(path, { identifier: identifier.trim() }));
    if (d) setCooldown(COOLDOWN);
  }

  async function sendForgot(e) {
    e.preventDefault();
    setLocalErr('');
    const d = await go(() => post('/api/auth/forgot-password', { identifier: identifier.trim() }));
    if (d) {
      setMode('reset');
      setCooldown(COOLDOWN);
    }
  }

  async function reset(e) {
    e.preventDefault();
    setLocalErr('');
    const pwErr = passwordProblem(pw.next);
    if (pwErr) return setLocalErr(pwErr);
    if (pw.next !== pw.confirm) return setLocalErr('Mật khẩu nhập lại không khớp.');
    const d = await go(() => post('/api/auth/reset-password', { identifier: identifier.trim(), code, newPassword: pw.next }));
    if (d) setDone(true);
  }

  const title = { verify: 'Xác thực tài khoản', forgot: 'Quên mật khẩu', reset: 'Đặt lại mật khẩu' }[mode];
  const sub = {
    verify: 'Nhập mã gồm 6 chữ số chúng tôi đã gửi tới email hoặc số điện thoại của bạn.',
    forgot: 'Nhập email hoặc số điện thoại đã đăng ký, chúng tôi sẽ gửi mã đặt lại mật khẩu.',
    reset: 'Nhập mã 6 số vừa nhận và đặt mật khẩu mới.',
  }[mode];

  const resendBtn = (
    <button type="button" className="link-btn" disabled={busy || cooldown > 0 || !identifier.trim()} onClick={resend}>
      {cooldown > 0 ? `Gửi lại mã sau ${cooldown}s` : 'Gửi lại mã'}
    </button>
  );

  return (
    <div className="auth-shell">
      <AuthHero />
      <section className="auth-panel">
        <div className="auth-card">
          {done ? (
            <div className="success-box">
              <div className="big">🎉</div>
              <h2>Đã đổi mật khẩu</h2>
              <p className="sub">Bạn có thể đăng nhập bằng mật khẩu mới ngay bây giờ.</p>
              <button className="btn btn-primary btn-block btn-lg" onClick={onBackToLogin}>Về trang đăng nhập</button>
            </div>
          ) : (
            <>
              <h2>{title}</h2>
              <p className="sub">{sub}</p>

              {mode !== 'reset' && (
                <div className="tabs">
                  <button type="button" className={mode === 'verify' ? 'active' : ''} onClick={() => { setMode('verify'); setLocalErr(''); }}>
                    Xác thực OTP
                  </button>
                  <button type="button" className={mode === 'forgot' ? 'active' : ''} onClick={() => { setMode('forgot'); setLocalErr(''); }}>
                    Quên mật khẩu
                  </button>
                </div>
              )}

              {msg && <div className="notice">{msg}</div>}
              {(localErr || err) && <div className="error">{localErr || err}</div>}

              {mode === 'verify' && (
                <form onSubmit={verify}>
                  <Field label="Email hoặc số điện thoại">
                    <input value={identifier} onChange={(e) => setIdentifier(e.target.value)} required />
                  </Field>
                  <Field label="Mã OTP">
                    <input className="otp-input" value={code} onChange={(e) => setCode(clean(e.target.value))} inputMode="numeric" placeholder="······" autoFocus required />
                  </Field>
                  <div className="auth-links">{resendBtn}<span className="muted">Mã có hiệu lực 5 phút</span></div>
                  <button className="btn btn-primary btn-block btn-lg" disabled={busy || code.length !== 6}>
                    {busy ? 'Đang xác thực…' : 'Xác thực'}
                  </button>
                </form>
              )}

              {mode === 'forgot' && (
                <form onSubmit={sendForgot}>
                  <Field label="Email hoặc số điện thoại">
                    <input value={identifier} onChange={(e) => setIdentifier(e.target.value)} autoFocus required />
                  </Field>
                  <button className="btn btn-primary btn-block btn-lg" disabled={busy}>
                    {busy ? 'Đang gửi…' : 'Gửi mã đặt lại'}
                  </button>
                </form>
              )}

              {mode === 'reset' && (
                <form onSubmit={reset}>
                  <Field label="Email hoặc số điện thoại">
                    <input value={identifier} onChange={(e) => setIdentifier(e.target.value)} required />
                  </Field>
                  <Field label="Mã OTP">
                    <input className="otp-input" value={code} onChange={(e) => setCode(clean(e.target.value))} inputMode="numeric" placeholder="······" autoFocus required />
                  </Field>
                  <Field label="Mật khẩu mới" hint="Ít nhất 8 ký tự, gồm cả chữ và số.">
                    <input type="password" value={pw.next} onChange={(e) => setPw({ ...pw, next: e.target.value })} autoComplete="new-password" required />
                  </Field>
                  <Field label="Nhập lại mật khẩu mới">
                    <input type="password" value={pw.confirm} onChange={(e) => setPw({ ...pw, confirm: e.target.value })} autoComplete="new-password" required />
                  </Field>
                  <div className="auth-links">{resendBtn}<span /></div>
                  <button className="btn btn-primary btn-block btn-lg" disabled={busy || code.length !== 6}>
                    {busy ? 'Đang lưu…' : 'Đặt lại mật khẩu'}
                  </button>
                </form>
              )}

              <p className="auth-foot">
                <button type="button" className="link-btn" onClick={onBackToLogin}>← Quay lại đăng nhập</button>
              </p>
            </>
          )}
        </div>
      </section>
    </div>
  );
}
