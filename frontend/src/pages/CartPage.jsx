import React, { useEffect, useState } from 'react';
import { api } from '../api';
import { formatVND } from '../data/products';

export default function CartPage({
  cart,
  onUpdateQty,
  onRemoveItem,
  onClearCart,
  onGoToShop,
  onGoToAddresses,
  flash,
}) {
  const [addresses, setAddresses] = useState([]);
  const [selectedAddressId, setSelectedAddressId] = useState('');
  const [paymentMethod, setPaymentMethod] = useState('COD');
  const [checkoutDone, setCheckoutDone] = useState(null);

  useEffect(() => {
    api('/api/me/addresses')
      .then((d) => {
        const list = Array.isArray(d) ? d : [];
        setAddresses(list);
        const def = list.find((a) => a.defaultAddress) || list[0];
        if (def) setSelectedAddressId(String(def.id));
      })
      .catch(() => {});
  }, []);

  const totalItems = cart.reduce((sum, item) => sum + item.qty, 0);
  const totalPrice = cart.reduce((sum, item) => sum + item.price * item.qty, 0);

  const handleCheckout = (e) => {
    e.preventDefault();
    if (!cart.length) return;
    const code = 'ORD-' + Math.floor(100000 + Math.random() * 900000);
    setCheckoutDone({
      code,
      totalItems,
      totalPrice,
      paymentMethod,
    });
    onClearCart();
    flash(`Đặt mua linh kiện thành công! Mã đơn hàng: ${code}`);
  };

  return (
    <div className="layout">
      <div className="card">
        <div className="bar">
          <h2>Giỏ hàng linh kiện rời ({totalItems} sản phẩm)</h2>
          {cart.length > 0 && (
            <button type="button" className="danger-outline" onClick={onClearCart}>
              Xóa toàn bộ giỏ
            </button>
          )}
        </div>

        {checkoutDone && (
          <div className="notice" style={{ display: 'block' }}>
            <b>✅ Đặt hàng thành công — Mã đơn: {checkoutDone.code}</b>
            <div>Tổng thanh toán: <b>{formatVND(checkoutDone.totalPrice)}</b> ({checkoutDone.paymentMethod})</div>
          </div>
        )}

        {cart.length === 0 ? (
          <div className="empty-state">
            <p className="muted">Giỏ hàng của bạn đang trống.</p>
            <button type="button" className="primary" style={{ maxWidth: '320px', marginTop: '12px' }} onClick={onGoToShop}>
              ← Tiếp tục chọn mua linh kiện rời
            </button>
          </div>
        ) : (
          <div className="items">
            {cart.map((item) => (
              <article key={item.id} className="cart-item-row">
                <div>
                  <span className="cat-tag">{item.category}</span>{' '}
                  <b>{item.name}</b>
                  <p className="muted" style={{ margin: '4px 0' }}>{item.specs}</p>
                  <div className="price-sm">{formatVND(item.price)} / cái</div>
                </div>

                <div className="cart-qty-controls">
                  <div className="qty-box">
                    <button type="button" onClick={() => onUpdateQty(item.id, item.qty - 1)}>
                      −
                    </button>
                    <span>{item.qty}</span>
                    <button type="button" onClick={() => onUpdateQty(item.id, item.qty + 1)}>
                      +
                    </button>
                  </div>
                  <strong className="price">{formatVND(item.price * item.qty)}</strong>
                  <button
                    type="button"
                    className="danger-outline"
                    onClick={() => onRemoveItem(item.id)}
                  >
                    Xóa
                  </button>
                </div>
              </article>
            ))}
          </div>
        )}
      </div>

      {/* Cột phải: Thanh toán đơn linh kiện rời */}
      <div className="card sticky-panel">
        <h2>Thanh toán đơn linh kiện</h2>
        <p className="muted">Áp dụng cho khách mua lẻ linh kiện tự lắp hoặc nâng cấp máy.</p>

        <div className="deposit-summary-box">
          <div className="summary-row">
            <span>Tổng số lượng:</span>
            <strong>{totalItems} linh kiện</strong>
          </div>
          <div className="summary-row">
            <span>Phí vận chuyển:</span>
            <strong style={{ color: '#059669' }}>Miễn phí toàn quốc</strong>
          </div>
          <div className="divider" style={{ margin: '12px 0' }} />
          <div className="summary-row">
            <span>Tổng tiền thanh toán:</span>
            <strong className="total-price">{formatVND(totalPrice)}</strong>
          </div>
        </div>

        <form onSubmit={handleCheckout} className="form">
          <label className="field-group">
            <span>Địa chỉ giao hàng</span>
            {addresses.length > 0 ? (
              <select
                value={selectedAddressId}
                onChange={(e) => setSelectedAddressId(e.target.value)}
              >
                {addresses.map((a) => (
                  <option key={a.id} value={a.id}>
                    {a.recipientName} ({a.phoneNumber}) - {[a.street, a.ward, a.district, a.province].filter(Boolean).join(', ')}
                  </option>
                ))}
              </select>
            ) : (
              <div style={{ marginTop: '6px' }}>
                <button type="button" onClick={onGoToAddresses}>
                  + Thêm địa chỉ nhận hàng
                </button>
              </div>
            )}
          </label>

          <label className="field-group">
            <span>Phương thức thanh toán</span>
            <select value={paymentMethod} onChange={(e) => setPaymentMethod(e.target.value)}>
              <option value="COD">Thanh toán khi nhận hàng (COD)</option>
              <option value="BANK">Chuyển khoản Ngân hàng (QR Code)</option>
              <option value="VNPAY">Cổng thanh toán VNPay / ZaloPay</option>
            </select>
          </label>

          <button type="submit" className="primary" disabled={cart.length === 0}>
            Xác nhận đặt mua linh kiện ({formatVND(totalPrice)})
          </button>
        </form>
      </div>
    </div>
  );
}