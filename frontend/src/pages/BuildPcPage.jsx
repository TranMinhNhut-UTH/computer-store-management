import React, { useEffect, useState } from 'react';
import { api } from '../api';
import { BUILD_SLOTS, HARDWARE_ITEMS, formatVND } from '../data/products';

export default function BuildPcPage({ flash, onGoToAddresses }) {
  const [selectedParts, setSelectedParts] = useState({});
  const [activeSlot, setActiveSlot] = useState('CPU');
  const [addresses, setAddresses] = useState([]);
  const [selectedAddressId, setSelectedAddressId] = useState('');
  const [note, setNote] = useState('');
  const [orderSuccess, setOrderSuccess] = useState(null);

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

  const handleSelectPart = (slotKey, part) => {
    setSelectedParts((prev) => ({ ...prev, [slotKey]: part }));
    setOrderSuccess(null);
  };

  const handleRemovePart = (slotKey) => {
    setSelectedParts((prev) => {
      const next = { ...prev };
      delete next[slotKey];
      return next;
    });
  };

  const chosenList = Object.entries(selectedParts);
  const totalAmount = chosenList.reduce((sum, [, item]) => sum + item.price, 0);
  // Tính 1/4 (25%) tiền đặt cọc để tiệm lắp ráp
  const depositAmount = Math.ceil(totalAmount / 4);
  const remainingAmount = totalAmount - depositAmount;

  const slotCandidates = HARDWARE_ITEMS.filter((i) => i.category === activeSlot);

  const handleConfirmBuildOrder = (e) => {
    e.preventDefault();
    if (chosenList.length < 2) {
      flash('Vui lòng chọn ít nhất 2 linh kiện để cửa hàng tiến hành lắp ráp!', true);
      return;
    }

    const orderCode = 'PC-' + Math.floor(100000 + Math.random() * 900000);
    setOrderSuccess({
      orderCode,
      totalAmount,
      depositAmount,
      remainingAmount,
      itemCount: chosenList.length,
    });
    flash(`Đã tạo phiếu yêu cầu lắp ráp ${orderCode}! Vui lòng thanh toán cọc 1/4 (${formatVND(depositAmount)}).`);
  };

  return (
    <div>
      {/* Banner chính sách cọc 1/4 lắp ráp */}
      <div className="hero-banner build-banner">
        <div>
          <span className="tech-pill">DỊCH VỤ LẮP RÁP PC CHUYÊN NGHIỆP · CỌC 25% (1/4)</span>
          <h2>Tự Chọn Cấu Hình — Tiệm Lắp Ráp Trọn Gói</h2>
          <p>
            Bạn chỉ cần chọn linh kiện và <b>thanh toán trước 1/4 (25%) tổng giá trị</b>. Kỹ thuật viên của tiệm sẽ khui seal, lắp ráp, đi dây gọn đẹp, cài đặt Windows & стресс-test ổn định trước khi giao máy và thu 3/4 phần tiền còn lại.
          </p>
        </div>
        <div className="hero-stats">
          <div>
            <strong>25% (1/4)</strong>
            <span>Đặt cọc để lắp máy</span>
          </div>
          <div>
            <strong>75% (3/4)</strong>
            <span>Thanh toán khi nhận PC</span>
          </div>
        </div>
      </div>

      <div className="layout">
        {/* Cột trái: Các khe linh kiện & danh sách chọn */}
        <div>
          <div className="card">
            <h2>Bước 1: Chọn từng bộ phận cho thùng máy</h2>
            <p className="muted">Nhấn vào từng mục linh kiện bên dưới để chọn hoặc thay đổi.</p>

            <div className="slot-grid">
              {BUILD_SLOTS.map((slot) => {
                const picked = selectedParts[slot.key];
                const isCurrent = activeSlot === slot.key;
                return (
                  <div
                    key={slot.key}
                    className={`slot-box ${isCurrent ? 'slot-active' : ''} ${picked ? 'slot-picked' : ''}`}
                    onClick={() => setActiveSlot(slot.key)}
                  >
                    <div className="bar" style={{ marginBottom: '4px' }}>
                      <b>{slot.label}</b>
                      {picked ? (
                        <span className="badge">Đã chọn</span>
                      ) : (
                        <small className="muted">{slot.required ? 'Bắt buộc' : 'Tùy chọn'}</small>
                      )}
                    </div>

                    {picked ? (
                      <div className="slot-picked-info">
                        <span>{picked.name}</span>
                        <div className="bar" style={{ marginBottom: 0, marginTop: '6px' }}>
                          <strong className="price-sm">{formatVND(picked.price)}</strong>
                          <button
                            type="button"
                            className="danger-outline"
                            onClick={(e) => {
                              e.stopPropagation();
                              handleRemovePart(slot.key);
                            }}
                          >
                            Bỏ chọn
                          </button>
                        </div>
                      </div>
                    ) : (
                      <p className="muted" style={{ margin: 0 }}>
                        + Nhấn để chọn {slot.key}
                      </p>
                    )}
                  </div>
                );
              })}
            </div>
          </div>

          {/* Danh sách linh kiện thuộc khe đang chọn */}
          <div className="card">
            <div className="bar">
              <h2>
                Chọn linh kiện cho mục: <span style={{ color: 'var(--primary)' }}>{activeSlot}</span>
              </h2>
            </div>

            <div className="product-grid">
              {slotCandidates.map((item) => {
                const isSelected = selectedParts[activeSlot]?.id === item.id;
                return (
                  <article key={item.id} className="product-card">
                    <div>
                      <div className="product-top">
                        <span className="cat-tag">{item.category}</span>
                        <span className="badge">{item.badge}</span>
                      </div>
                      <h3>{item.name}</h3>
                      <p className="specs-text">{item.specs}</p>
                    </div>
                    <div className="product-bottom">
                      <div className="price">{formatVND(item.price)}</div>
                      <button
                        type="button"
                        className={isSelected ? 'primary' : 'add-pc-btn'}
                        onClick={() => handleSelectPart(activeSlot, item)}
                      >
                        {isSelected ? '✓Đang lắp' : '+Chọn lắp'}
                      </button>
                    </div>
                  </article>
                );
              })}
            </div>
          </div>
        </div>

        {/* Cột phải: Bảng tính tiền cọc 1/4 & Xác nhận đặt lắp */}
        <div className="card sticky-panel">
          <div className="bar">
            <h2>Phiếu Lắp Ráp PC ({chosenList.length} món)</h2>
            {chosenList.length > 0 && (
              <button type="button" onClick={() => setSelectedParts({})}>
                Làm mới
              </button>
            )}
          </div>

          {chosenList.length === 0 ? (
            <p className="muted">
              Chưa có linh kiện nào trong cấu hình. Hãy chọn CPU, Mainboard, RAM, SSD ở khung bên trái để hệ thống tính mức cọc 1/4.
            </p>
          ) : (
            <form onSubmit={handleConfirmBuildOrder}>
              <div className="build-list">
                {chosenList.map(([slotKey, item]) => (
                  <div key={slotKey} className="build-item">
                    <div>
                      <span className="cat-tag">{slotKey}</span> <b>{item.name}</b>
                      <div className="price-sm">{formatVND(item.price)}</div>
                    </div>
                    <button type="button" onClick={() => handleRemovePart(slotKey)}>
                      ✕
                    </button>
                  </div>
                ))}
              </div>

              {/* Khung tính toán 1/4 tiền đặt cọc */}
              <div className="deposit-summary-box">
                <div className="summary-row">
                  <span>Tổng giá trị linh kiện:</span>
                  <strong>{formatVND(totalAmount)}</strong>
                </div>
                <div className="summary-row">
                  <span>Phí lắp ráp, đi dây & cài Win:</span>
                  <strong style={{ color: '#059669' }}>MIỄN PHÍ (0 ₫)</strong>
                </div>
                <div className="divider" style={{ margin: '12px 0' }} />
                <div className="summary-row highlight-deposit">
                  <span>⚡ Tiền cọc lắp ráp (1/4 = 25%):</span>
                  <strong className="deposit-price">{formatVND(depositAmount)}</strong>
                </div>
                <div className="summary-row">
                  <span>Thanh toán khi nhận PC (3/4 = 75%):</span>
                  <strong>{formatVND(remainingAmount)}</strong>
                </div>
              </div>

              <div className="form">
                <label className="field-group">
                  <span>Địa chỉ giao thùng máy sau khi lắp xong</span>
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
                        + Thêm địa chỉ nhận hàng trong Sổ địa chỉ
                      </button>
                    </div>
                  )}
                </label>

                <label className="field-group">
                  <span>Ghi chú cho Kỹ thuật viên lắp máy</span>
                  <input
                    placeholder="VD: Đi dây gọn, chỉnh đèn RGB màu xanh Cyan, cài sẵn Win 11 Pro..."
                    value={note}
                    onChange={(e) => setNote(e.target.value)}
                  />
                </label>

                <button type="submit" className="primary">
                  🛠️ Đặt Lắp Ráp & Đặt Cọc 1/4 ({formatVND(depositAmount)})
                </button>
              </div>
            </form>
          )}

          {orderSuccess && (
            <div className="notice" style={{ marginTop: '16px', display: 'block' }}>
              <div><b>🎉 Đã ghi nhận yêu cầu lắp ráp: {orderSuccess.orderCode}</b></div>
              <div>• Số tiền cọc 1/4 cần thanh toán: <b>{formatVND(orderSuccess.depositAmount)}</b></div>
              <div>• Số tiền còn lại khi nhận máy (3/4): <b>{formatVND(orderSuccess.remainingAmount)}</b></div>
            </div>
          )}
        </div>
      </div>
    </div>
  );
}