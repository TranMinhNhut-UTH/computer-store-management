import React, { useEffect, useState } from 'react';
import { api, del, post, put } from '../api';
import { Field, Form, Submit } from '../components/UI';

export default function AddressesPage({ go, busy }) {
  const [addresses, setAddresses] = useState([]);

  async function loadAddresses() {
    const d = await api('/api/me/addresses');
    setAddresses(Array.isArray(d) ? d : []);
    return d;
  }

  useEffect(() => {
    go(loadAddresses);
  }, []);

  const handleAddAddress = async (e) => {
    e.preventDefault();
    const form = e.currentTarget;
    const f = Object.fromEntries(new FormData(form));
    const payload = {
      recipientName: f.recipientName,
      phoneNumber: f.phoneNumber,
      province: f.province,
      district: f.district,
      ward: f.ward,
      street: f.street,
      defaultAddress: f.defaultAddress === 'on',
      latitude: null,
      longitude: null,
    };

    await go(async () => {
      const d = await post('/api/me/addresses', payload);
      await loadAddresses();
      form.reset();
      return d;
    });
  };

  const handleSetDefault = (id) => {
    go(async () => {
      await put(`/api/me/addresses/${id}/default`, {});
      return await loadAddresses();
    });
  };

  const handleDelete = (id) => {
    if (window.confirm('Bạn có chắc muốn xóa địa chỉ nhận hàng này?')) {
      go(async () => {
        await del(`/api/me/addresses/${id}`);
        return await loadAddresses();
      });
    }
  };

  return (
    <div className="layout">
      <div className="card">
        <Form
          title="Thêm địa chỉ giao linh kiện"
          subtitle="Nhập chính xác địa chỉ để đội ngũ kỹ thuật giao và lắp đặt PC tận nơi."
          onSubmit={handleAddAddress}
          busy={busy}
        >
          <div className="grid-2">
            <Field name="recipientName" label="Họ tên người nhận" placeholder="Nguyễn Văn A" required />
            <Field name="phoneNumber" label="Số điện thoại" placeholder="09xxxxxxxx" required />
          </div>
          <div className="grid-3">
            <Field name="province" label="Tỉnh / Thành phố" placeholder="TP. Hồ Chí Minh" required />
            <Field name="district" label="Quận / Huyện" placeholder="Quận 1" required />
            <Field name="ward" label="Phường / Xã" placeholder="Phường Bến Nghé" required />
          </div>
          <Field
            name="street"
            label="Số nhà, tên đường, tòa nhà"
            placeholder="Số 123 Đường Lê Lợi..."
            required
          />
          <label className="checkbox-label">
            <input type="checkbox" name="defaultAddress" /> Đặt làm địa chỉ nhận hàng mặc định
          </label>
          <Submit disabled={busy}>+ Lưu địa chỉ nhận hàng</Submit>
        </Form>
      </div>

      <div className="card">
        <div className="bar">
          <h2>Sổ địa chỉ của bạn ({addresses.length})</h2>
          <button type="button" onClick={() => go(loadAddresses)}>
            Làm mới
          </button>
        </div>

        <div className="items">
          {addresses.map((a) => (
            <article key={a.id} className={a.defaultAddress ? 'address-default' : ''}>
              <div className="bar" style={{ marginBottom: '6px' }}>
                <b>
                  📦 {a.recipientName}
                  {a.defaultAddress && <span className="badge">Mặc định</span>}
                </b>
                <span className="phone-tag">{a.phoneNumber}</span>
              </div>
              <p>
                {[a.street, a.ward, a.district, a.province].filter(Boolean).join(', ')}
              </p>
              <div className="address-actions">
                {!a.defaultAddress && (
                  <button type="button" onClick={() => handleSetDefault(a.id)}>
                    Thiết lập mặc định
                  </button>
                )}
                <button type="button" className="danger-outline" onClick={() => handleDelete(a.id)}>
                  Xóa địa chỉ
                </button>
              </div>
            </article>
          ))}
          {!addresses.length && (
            <p className="muted">Bạn chưa lưu địa chỉ giao hàng nào. Hãy thêm địa chỉ ở khung bên trái.</p>
          )}
        </div>
      </div>
    </div>
  );
}