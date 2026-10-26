import React, { useEffect, useState } from 'react';
import { get } from '../api';
import { Empty } from '../components/ui';
import { formatVND } from '../utils';

const PAYMENTS = [
  ['COD', '💵 Thanh toán khi nhận hàng (COD)'],
  ['QR', '🏦 Chuyển khoản QR ngân hàng'],
  ['VNPAY', '💳 VNPay'],
  ['MOMO', '📱 Ví MoMo'],
  ['ZALOPAY', '📲 ZaloPay'],
];

// Mã giảm giá minh họa; sau này lấy từ API khuyến mãi
const COUPONS = {
  CS10: { label: 'Giảm 10% (tối đa 500.000đ)', calc: (sub) => Math.min(Math.round(sub * 0.1), 500000), freeShip: false },
  FREESHIP: { label: 'Miễn phí vận chuyển', calc: () => 0, freeShip: true },
};

export default function CartPage({ cart, onUpdateQty, onRemoveItem, onClearCart, onGoToShop, onGoToAddresses, flash }) {
  const [addresses, setAddresses] = useState(null);
  const [addrId, setAddrId] = useState(null);
  const [pay, setPay] = useState('COD');
  const [code, setCode] = useState('');
  const [coupon, setCoupon] = useState(null);

  useEffect(() => {
    get('/api/me/addresses')
      .then((list) => {
        setAddresses(list);
        setAddrId((list.find((a) => a.defaultAddress) || list[0])?.id ?? null);
      })
      .catch(() => setAddresses([]));
  }, []);

  const subtotal = cart.reduce((s, i) => s + i.price * i.qty, 0);
  const discount = coupon ? coupon.calc(subtotal) : 0;
  const shipping = subtotal === 0 || subtotal >= 2000000 || coupon?.freeShip ? 0 : 30000;
  const total = subtotal - discount + shipping;

  const applyCoupon = () => {
    const c = COUPONS[code.trim().toUpperCase()];
    if (!c) return flash('Mã giảm giá không hợp lệ hoặc đã hết hạn.', true);
    setCoupon(c);
    flash(`Đã áp dụng mã: ${c.label}`);
  };

  const checkout = () => {
    if (!addrId) return flash('Vui lòng chọn địa chỉ nhận hàng.', true);
    flash('Giao diện đặt hàng đã sẵn sàng. Tính năng tạo đơn sẽ hoạt động khi module Đơn hàng ở backend hoàn thành.');
  };

  if (cart.length === 0) {
    return (
      <div className="card">
        <Empty icon="🛒" title="Giỏ hàng đang trống" text="Chọn vài linh kiện hoặc tự build một bộ PC để bắt đầu.">
          <button className="btn btn-primary" onClick={onGoToShop}>Tiếp tục mua sắm</button>
        </Empty>
      </div>
    );
  }

  return (
    <div className="cart-layout">
      <div>
        <div className="card">
          <div className="card-head">
            <h2>Sản phẩm ({cart.reduce((s, i) => s + i.qty, 0)})</h2>
            <button className="btn btn-danger btn-sm" onClick={onClearCart}>Xóa giỏ hàng</button>
          </div>
          {cart.map((i) => (
            <div key={i.id} className="cart-item">
              <div className="cart-thumb">{i.icon}</div>
              <div>
                <h4>{i.name}</h4>
                <span className="muted" style={{ fontSize: 13 }}>{i.brand}</span>
                <div>
                  <div className="qty">
                    <button type="button" onClick={() => onUpdateQty(i.id, i.qty - 1)} aria-label="Giảm">−</button>
                    <span>{i.qty}</span>
                    <button type="button" onClick={() => onUpdateQty(i.id, i.qty + 1)} aria-label="Tăng">+</button>
                  </div>
                </div>
              </div>
              <div style={{ textAlign: 'right' }}>
                <div className="cart-price">{formatVND(i.price * i.qty)}</div>
                <button type="button" className="link-btn" style={{ fontSize: 13 }} onClick={() => onRemoveItem(i.id)}>Xóa</button>
              </div>
            </div>
          ))}
        </div>

        <div className="card">
          <div className="card-head"><h2>Địa chỉ nhận hàng</h2>
            <button className="btn btn-ghost btn-sm" onClick={onGoToAddresses}>Quản lý địa chỉ</button>
          </div>
          {addresses === null && <p className="muted">Đang tải địa chỉ…</p>}
          {addresses && addresses.length === 0 && (
            <p className="muted">Bạn chưa có địa chỉ nào. <button type="button" className="link-btn" onClick={onGoToAddresses}>Thêm địa chỉ</button> để đặt hàng.</p>
          )}
          {addresses?.map((a) => (
            <label key={a.id} className={`addr-pick ${addrId === a.id ? 'on' : ''}`}>
              <input type="radio" name="addr" checked={addrId === a.id} onChange={() => setAddrId(a.id)} />
              <span>
                <b>{a.recipientName}</b> · {a.phoneNumber} {a.defaultAddress && <span className="badge b-red">Mặc định</span>}
                <br />{a.street}, {a.ward}, {a.district}, {a.province}
              </span>
            </label>
          ))}
        </div>
      </div>

      <aside className="card summary">
        <h3>Thanh toán</h3>
        <div className="coupon">
          <input value={code} onChange={(e) => setCode(e.target.value)} placeholder="Mã giảm giá (thử CS10)" aria-label="Mã giảm giá" />
          <button className="btn btn-outline" onClick={applyCoupon}>Áp dụng</button>
        </div>
        <div className="sum-row"><span>Tạm tính</span><span>{formatVND(subtotal)}</span></div>
        {discount > 0 && <div className="sum-row disc"><span>Giảm giá</span><span>−{formatVND(discount)}</span></div>}
        <div className="sum-row"><span>Phí vận chuyển</span><span>{shipping === 0 ? 'Miễn phí' : formatVND(shipping)}</span></div>
        <div className="sum-total"><span>Tổng cộng</span><strong>{formatVND(total)}</strong></div>
        <small className="muted">Đã bao gồm VAT 10%. Miễn phí vận chuyển cho đơn từ 2.000.000đ.</small>

        <div className="step-title">Phương thức thanh toán</div>
        {PAYMENTS.map(([k, label]) => (
          <label key={k} className={`pay-option ${pay === k ? 'on' : ''}`}>
            <input type="radio" name="pay" checked={pay === k} onChange={() => setPay(k)} /> {label}
          </label>
        ))}
        <button className="btn btn-primary btn-block btn-lg" style={{ marginTop: 8 }} onClick={checkout}>Đặt hàng</button>
      </aside>
    </div>
  );
}
