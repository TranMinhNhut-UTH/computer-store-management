import React, { useEffect, useState } from 'react';
import { post } from './api';
import LoginPage from './pages/LoginPage';
import VerifyRecoveryPage from './pages/VerifyRecoveryPage';
import PartsShopPage from './pages/PartsShopPage';
import BuildPcPage from './pages/BuildPcPage';
import CartPage from './pages/CartPage';
import ProfilePage from './pages/ProfilePage';
import AddressesPage from './pages/AddressesPage';
import StaffPage from './pages/StaffPage';
import LogsPage from './pages/LogsPage';

const NAV_ITEMS = [
  ['parts', '🛍️ Mua Linh Kiện Rời'],
  ['build-pc', '🛠️ Lắp Ráp PC '],
  ['cart', '🛒 Giỏ Hàng'],
  ['profile', '👤 Hồ Sơ'],
  ['addresses', '📦 Sổ Địa Chỉ'],
  ['staff', '🛡️ Nhân Viên'],
  ['logs', '📋 Nhật Ký'],
];

export default function App() {
  // Trang đầu tiên xuất hiện luôn là 'login'
  const [page, setPage] = useState('login');
  const [recoveryConfig, setRecoveryConfig] = useState({ mode: 'verify', identifier: '' });

  const [msg, setMsg] = useState('');
  const [err, setErr] = useState('');
  const [busy, setBusy] = useState(false);
  const [token, setToken] = useState(localStorage.getItem('accessToken') || '');
  const [refresh, setRefresh] = useState(localStorage.getItem('refreshToken') || '');

  // State Giỏ hàng lưu trong localStorage
  const [cart, setCart] = useState(() => {
    try {
      const saved = localStorage.getItem('cs_cart');
      return saved ? JSON.parse(saved) : [];
    } catch {
      return [];
    }
  });

  useEffect(() => {
    localStorage.setItem('cs_cart', JSON.stringify(cart));
  }, [cart]);

  const cartCount = cart.reduce((sum, i) => sum + i.qty, 0);

  function flash(message, isError = false) {
    setMsg(isError ? '' : message);
    setErr(isError ? message : '');
  }

  function changePage(nextPage) {
    flash('');
    setPage(nextPage);
  }

  function openVerifyRecovery(mode = 'verify', identifier = '') {
    flash('');
    setRecoveryConfig({ mode, identifier });
    setPage('verify-recovery');
  }

  // Các hàm thao tác Giỏ hàng
  const handleAddToCart = (product) => {
    setCart((prev) => {
      const found = prev.find((i) => i.id === product.id);
      if (found) {
        return prev.map((i) => (i.id === product.id ? { ...i, qty: i.qty + 1 } : i));
      }
      return [...prev, { ...product, qty: 1 }];
    });
    flash(`Đã thêm "${product.name}" vào giỏ hàng!`);
  };

  const handleBuyNow = (product) => {
    handleAddToCart(product);
    setPage('cart');
  };

  const handleUpdateCartQty = (id, nextQty) => {
    if (nextQty <= 0) {
      setCart((prev) => prev.filter((i) => i.id !== id));
    } else {
      setCart((prev) => prev.map((i) => (i.id === id ? { ...i, qty: nextQty } : i)));
    }
  };

  const handleRemoveCartItem = (id) => {
    setCart((prev) => prev.filter((i) => i.id !== id));
  };

  const handleClearCart = () => {
    setCart([]);
  };

  async function go(fn) {
    setBusy(true);
    flash('');
    try {
      const d = await fn();
      if (d?.accessToken) {
        localStorage.setItem('accessToken', d.accessToken);
        setToken(d.accessToken);
      }
      if (d?.refreshToken) {
        localStorage.setItem('refreshToken', d.refreshToken);
        setRefresh(d.refreshToken);
      }
      flash('Thao tác thành công');
      return d;
    } catch (e) {
      flash(e.message, true);
      return null;
    } finally {
      setBusy(false);
    }
  }

  const handleLogout = async () => {
    await go(async () => {
      if (refresh) {
        await post('/api/auth/logout', { refreshToken: refresh });
      }
      return { message: 'Đã đăng xuất' };
    });
    localStorage.removeItem('accessToken');
    localStorage.removeItem('refreshToken');
    setToken('');
    setRefresh('');
    changePage('login');
  };

  // 1. Trang Đăng nhập & Đăng ký (Xuất hiện đầu tiên)
  if (page === 'login') {
    return (
      <LoginPage
        go={go}
        busy={busy}
        msg={msg}
        err={err}
        onLoginSuccess={() => changePage('parts')}
        onGoToVerifyRecovery={openVerifyRecovery}
      />
    );
  }

  // 2. Trang Xác thực OTP & Khôi phục mật khẩu
  if (page === 'verify-recovery') {
    return (
      <VerifyRecoveryPage
        go={go}
        busy={busy}
        msg={msg}
        err={err}
        initialMode={recoveryConfig.mode}
        initialIdentifier={recoveryConfig.identifier}
        onBackToLogin={() => changePage('login')}
      />
    );
  }

  // 3. Giao diện Website chính với Top Header trải ngang
  return (
    <div className="shell">
      <header className="top-navbar">
        <div className="nav-inner">
          <div className="logo" onClick={() => changePage('parts')} style={{ cursor: 'pointer' }}>
            CS <span>COMPUTER STORE</span>
          </div>

          <nav className="nav-links">
            {NAV_ITEMS.map(([id, label]) => (
              <button
                key={id}
                type="button"
                className={page === id ? 'active' : ''}
                onClick={() => changePage(id)}
              >
                {label}
                {id === 'cart' && cartCount > 0 && (
                  <span className="cart-badge-count">{cartCount}</span>
                )}
              </button>
            ))}
          </nav>

          <div className="nav-actions">
            <button
              type="button"
              className="cart-quick-btn"
              onClick={() => changePage('cart')}
            >
              🛒 Giỏ hàng ({cartCount})
            </button>
            <button type="button" className="logout-btn" onClick={handleLogout}>
              Đăng xuất
            </button>
          </div>
        </div>
      </header>

      <main className="main">
        <div className="page-subbar">
          <div>
            <small>CS HARDWARE · HIGH PERFORMANCE PC</small>
            <h1>{NAV_ITEMS.find((n) => n[0] === page)?.[1]}</h1>
          </div>
          <span className="support-pill">🎧 Hotline Kỹ thuật: 1900 6868</span>
        </div>

        {msg && <div className="notice">{msg}</div>}
        {err && <div className="error">{err}</div>}

        {page === 'parts' && (
          <PartsShopPage
            onAddToCart={handleAddToCart}
            onBuyNow={handleBuyNow}
            onGoToBuildPc={() => changePage('build-pc')}
          />
        )}

        {page === 'build-pc' && (
          <BuildPcPage
            flash={flash}
            onGoToAddresses={() => changePage('addresses')}
          />
        )}

        {page === 'cart' && (
          <CartPage
            cart={cart}
            onUpdateQty={handleUpdateCartQty}
            onRemoveItem={handleRemoveCartItem}
            onClearCart={handleClearCart}
            onGoToShop={() => changePage('parts')}
            onGoToAddresses={() => changePage('addresses')}
            flash={flash}
          />
        )}

        {page === 'profile' && <ProfilePage go={go} busy={busy} />}
        {page === 'addresses' && <AddressesPage go={go} busy={busy} />}
        {page === 'staff' && <StaffPage go={go} busy={busy} />}
        {page === 'logs' && <LogsPage go={go} />}

        <footer>
          © 2026 CS Computer Store · Chuyên Linh Kiện Máy Tính, Hi-End PC & Lắp Ráp Theo Yêu Cầu
        </footer>
      </main>
    </div>
  );
}