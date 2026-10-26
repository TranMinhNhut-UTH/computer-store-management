import React, { useEffect, useState } from 'react';
import { post } from './api';
import { initials, ROLE_LABELS } from './utils';
import logoImg from './assets/logo.png';
import LoginPage from './pages/LoginPage';
import VerifyRecoveryPage from './pages/VerifyRecoveryPage';
import PartsShopPage from './pages/PartsShopPage';
import BuildPcPage from './pages/BuildPcPage';
import CartPage from './pages/CartPage';
import ProfilePage from './pages/ProfilePage';
import AddressesPage from './pages/AddressesPage';
import StaffPage from './pages/StaffPage';
import ManagerPage from './pages/ManagerPage';
import ReceptionistPage from './pages/ReceptionistPage';
import TechnicianPage from './pages/TechnicianPage';

const NAV_ITEMS = [
  ['parts', '🛍️ Linh kiện rời', ['GUEST', 'KHACH_HANG']],
  ['build-pc', '🛠️ Lắp Ráp PC', ['GUEST', 'KHACH_HANG']],
  ['cart', '🛒 Giỏ Hàng', ['GUEST', 'KHACH_HANG']],
  ['addresses', '📦 Sổ Địa Chỉ', ['KHACH_HANG']],
  ['technician', '🛠️ Việc Kỹ Thuật', ['THO', 'QUAN_LY']],
  ['reception', '🛎️ Quầy Lễ Tân', ['LE_TAN', 'QUAN_LY']],
  ['manager', '📊 Quản Trị', ['QUAN_LY']],
  ['staff', '🛡️ Nhân Sự & Phân Quyền', ['QUAN_LY']],

  ['profile', '👤 Hồ Sơ', ['KHACH_HANG', 'LE_TAN', 'QUAN_LY', 'THO', 'KE_TOAN', 'THU_KHO']],
];
const getDefaultPage = (u) => {
  if (!u) return 'parts';
  switch (u.roleCode) {
    case 'QUAN_LY':
      return 'manager';
    case 'LE_TAN':
      return 'reception';
    case 'THO':
      return 'technician';
    default:
      return 'parts';
  }
};

const readUser = () => {
  try {
    return JSON.parse(localStorage.getItem('cs_user') || 'null');
  } catch {
    return null;
  }
};

export default function App() {
  const [token, setToken] = useState(localStorage.getItem('accessToken') || '');
  const [refresh, setRefresh] = useState(localStorage.getItem('refreshToken') || '');
  const [user, setUser] = useState(readUser);
  const [page, setPage] = useState(token ? getDefaultPage(readUser()) : 'login');
  const [recoveryConfig, setRecoveryConfig] = useState({ mode: 'verify', identifier: '' });
  const [msg, setMsg] = useState('');
  const [err, setErr] = useState('');
  const [busy, setBusy] = useState(false);
  const getCartKey = (u) => {
      const identifier = u?.id || u?.email || 'guest';
      return `cs_cart_${identifier}`;
    };
  const [cart, setCart] = useState(() => {
      try {
        const currentUser = readUser();
        const saved = localStorage.getItem(getCartKey(currentUser));
        return saved ? JSON.parse(saved) : [];
      } catch {
        return [];
      }
    });


  useEffect(() => {
      try {
        const saved = localStorage.getItem(getCartKey(user));
        setCart(saved ? JSON.parse(saved) : []);
      } catch {
        setCart([]);
      }
    }, [user]);

  useEffect(() => {
      localStorage.setItem(getCartKey(user), JSON.stringify(cart));
    }, [cart, user]);

  const cartCount = cart.reduce((sum, i) => sum + i.qty, 0);
  const role = user?.roleCode;
  const currentRole = user?.roleCode || 'GUEST';
  const navItems = NAV_ITEMS.filter(([, , allowedRoles]) => {
      if (!allowedRoles) return true;
      return allowedRoles.includes(currentRole);
    });
  function flash(message, isError = false) {
    setMsg(isError ? '' : message);
    setErr(isError ? message : '');
  }

  function changePage(nextPage) {
    flash('');
    setPage(nextPage);
    window.scrollTo({ top: 0 });
  }

  function openVerifyRecovery(mode = 'verify', identifier = '') {
    flash('');
    setRecoveryConfig({ mode, identifier });
    setPage('verify-recovery');
  }

  function saveUser(u) {
    setUser(u);
    localStorage.setItem('cs_user', JSON.stringify(u));
  }

  function clearSession() {
    ['accessToken', 'refreshToken', 'cs_user'].forEach((k) => localStorage.removeItem(k));
    setToken('');
    setRefresh('');
    setUser(null);
  }

  useEffect(() => {
    const onExpired = () => {
      clearSession();
      setPage('login');
      setErr('Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại.');
    };
    window.addEventListener('auth:expired', onExpired);
    return () => window.removeEventListener('auth:expired', onExpired);
  }, []);

  const handleAuthSuccess = (d) => {
    if (d?.user) {
      saveUser(d.user);
      changePage(getDefaultPage(d.user));
    } else {
      changePage('parts');
    }
  };
  const addItems = (products) =>
    setCart((prev) => {
      const next = [...prev];
      products.forEach((product) => {
        const i = next.findIndex((x) => x.id === product.id);
        if (i >= 0) next[i] = { ...next[i], qty: next[i].qty + 1 };
        else next.push({ ...product, qty: 1 });
      });
      return next;
    });

  const handleAddToCart = (product) => {
    addItems([product]);
    flash(`Đã thêm "${product.name}" vào giỏ hàng!`);
  };

  const handleAddManyToCart = (products) => {
    addItems(products);
    flash(`Đã thêm ${products.length} linh kiện vào giỏ hàng!`);
  };

  const handleBuyNow = (product) => {
    addItems([product]);
    changePage('cart');
  };

  const handleUpdateCartQty = (id, nextQty) => {
    if (nextQty <= 0) setCart((prev) => prev.filter((i) => i.id !== id));
    else setCart((prev) => prev.map((i) => (i.id === id ? { ...i, qty: nextQty } : i)));
  };

  const handleRemoveCartItem = (id) => setCart((prev) => prev.filter((i) => i.id !== id));
  const handleClearCart = () => setCart([]);

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
      flash(d?.message || 'Thao tác thành công');
      return d;
    } catch (e) {
      flash(e.message, true);
      return null;
    } finally {
      setBusy(false);
    }
  }

  const handleLogout = async () => {
    try {
      if (refresh) await post('/api/auth/logout', { refreshToken: refresh });
    } catch {}
    clearSession();
    changePage('login');
  };

  if (page === 'login') {
    return (
      <LoginPage
        go={go}
        busy={busy}
        msg={msg}
        err={err}
        onLoginSuccess={handleAuthSuccess}
        onGoToVerifyRecovery={openVerifyRecovery}
      />
    );
  }

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
        onLoginSuccess={handleAuthSuccess}
      />
    );
  }

  return (
    <div className="shell">
      <header className="top-navbar">
        <div className="nav-inner">
          <div
            className="logo"
            onClick={() => changePage(getDefaultPage(user))}
            style={{
              cursor: 'pointer',
              display: 'flex',
              alignItems: 'center',
              gap: '10px',
              position: 'relative',
              height: '70px'
            }}
          >
            <img
              src={logoImg}
              alt="CS Logo"
              style={{
                position: 'absolute',
                left: 0,
                top: '50%',
                transform: 'translateY(-50%)',
                height: '110px',
                width: '200px',
                zIndex: 10
              }}
            />
            {/* Chừa một khoảng trống tương ứng chiều rộng logo để chữ không bị đè */}
            <span style={{ marginLeft: '180px' }}></span>
          </div>

          <nav className="nav-links">
            {navItems
              .filter(([id]) => id !== 'cart')
              .map(([id, label]) => (
                <button key={id} type="button" className={page === id ? 'active' : ''} onClick={() => changePage(id)}>
                  {label}
                </button>
              ))}
          </nav>

          <div className="nav-actions">
            {(!user || user.roleCode === 'KHACH_HANG') && (
              <button type="button" className="cart-quick-btn" onClick={() => changePage('cart')}>
                🛒 Giỏ hàng ({cartCount})
              </button>
            )}

            {user && (
              <div className="user-chip" title={user.email || user.phoneNumber}>
                <span className="avatar-sm">{initials(user.fullName)}</span>
                <div>
                  {user.fullName}
                  <small>{ROLE_LABELS[user.roleCode] || user.roleName}</small>
                </div>
              </div>
            )}

            <button type="button" className="logout-btn" onClick={handleLogout}>
              Đăng xuất
            </button>
          </div>
        </div>
      </header>

      <main className="main">
        <div className="page-subbar">
          <div>
            <h1>{NAV_ITEMS.find((n) => n[0] === page)?.[1]}</h1>
          </div>
        </div>

        {msg && <div className="notice">{msg}</div>}
        {err && <div className="error">{err}</div>}

        {page === 'parts' && (role === 'KHACH_HANG' || role === 'GUEST')&&(
          <PartsShopPage
            onAddToCart={handleAddToCart}
            onBuyNow={handleBuyNow}
            onGoToBuildPc={() => changePage('build-pc')}
          />
        )}

        {page === 'build-pc' &&(role === 'KHACH_HANG' || role === 'GUEST') &&(
          <BuildPcPage
            flash={flash}
            onAddMany={handleAddManyToCart}
            onGoToCart={() => changePage('cart')}
            onGoToAddresses={() => changePage('addresses')}
          />
        )}

        {page === 'cart' && (role === 'KHACH_HANG' || role === 'GUEST')&&(
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

        {page === 'profile' && <ProfilePage go={go} busy={busy} onProfileUpdated={saveUser} onLogout={handleLogout} />}
        {page === 'addresses' && <AddressesPage go={go} busy={busy} />}
        {page === 'staff' && role === 'QUAN_LY' && <StaffPage go={go} busy={busy} />}
        {page === 'reception' && (role === 'LE_TAN' || role === 'QUAN_LY') && <ReceptionistPage flash={flash} />}
        {page === 'manager' && role === 'QUAN_LY' && <ManagerPage go={go} busy={busy} flash={flash} />}
        {page === 'technician' && ['THO', 'QUAN_LY'].includes(role) && <TechnicianPage flash={flash} />}
        <footer>© 2026 PCP-Shop · Chuyên linh kiện máy tính, PC Hi-End và lắp ráp theo yêu cầu
                 <span className="support-pill">🎧 Hotline kỹ thuật: 1900 6868</span></footer>
      </main>
    </div>
  );
}