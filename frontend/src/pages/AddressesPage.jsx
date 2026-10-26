import React, { useCallback, useEffect, useState } from 'react';
import { del, get, post, put } from '../api';
import { Empty, Field, Modal, Spinner } from '../components/ui';

const EMPTY = { recipientName: '', phoneNumber: '', province: '', district: '', ward: '', street: '', defaultAddress: false };

export default function AddressesPage({ go, busy }) {
  const [list, setList] = useState(null);
  const [loadErr, setLoadErr] = useState('');
  const [editing, setEditing] = useState(null);   // null | {id?, ...form}

  const load = useCallback(() => {
    get('/api/me/addresses').then(setList).catch((e) => setLoadErr(e.message));
  }, []);
  useEffect(load, [load]);

  const set = (k) => (e) => setEditing({ ...editing, [k]: e.target.type === 'checkbox' ? e.target.checked : e.target.value });

  async function save(e) {
    e.preventDefault();
    const { id, ...body } = editing;
    const d = await go(() => (id ? put(`/api/me/addresses/${id}`, body) : post('/api/me/addresses', body)));
    if (d) { setEditing(null); load(); }
  }

  async function makeDefault(id) {
    if (await go(() => put(`/api/me/addresses/${id}/default`))) load();
  }

  async function remove(a) {
    if (!window.confirm(`Xóa địa chỉ của "${a.recipientName}"?`)) return;
    if (await go(() => del(`/api/me/addresses/${a.id}`))) load();
  }

  if (loadErr) return <div className="error">{loadErr}</div>;
  if (!list) return <div className="card"><Spinner /></div>;

  return (
    <>
      <div className="section-head">
        <h2>Địa chỉ nhận hàng ({list.length})</h2>
        <button className="btn btn-primary" onClick={() => setEditing({ ...EMPTY })}>+ Thêm địa chỉ</button>
      </div>

      {list.length === 0 ? (
        <div className="card">
          <Empty icon="📦" title="Bạn chưa lưu địa chỉ nào" text="Thêm địa chỉ để đặt hàng và hẹn lắp máy tại nhà nhanh hơn.">
            <button className="btn btn-primary" onClick={() => setEditing({ ...EMPTY })}>Thêm địa chỉ đầu tiên</button>
          </Empty>
        </div>
      ) : (
        <div className="addr-grid">
          {list.map((a) => (
            <div key={a.id} className={`addr-card ${a.defaultAddress ? 'default' : ''}`}>
              <div className="row">
                <strong>{a.recipientName}</strong>
                {a.defaultAddress && <span className="badge b-red">Mặc định</span>}
              </div>
              <span>{a.phoneNumber}</span>
              <span className="muted">{a.street}, {a.ward}, {a.district}, {a.province}</span>
              <div className="acts">
                <button className="btn btn-ghost btn-sm" onClick={() => setEditing({ ...a })}>Sửa</button>
                {!a.defaultAddress && <button className="btn btn-outline btn-sm" disabled={busy} onClick={() => makeDefault(a.id)}>Đặt làm mặc định</button>}
                <button className="btn btn-danger btn-sm" disabled={busy} onClick={() => remove(a)}>Xóa</button>
              </div>
            </div>
          ))}
        </div>
      )}

      {editing && (
        <Modal
          title={editing.id ? 'Sửa địa chỉ' : 'Thêm địa chỉ mới'}
          onClose={() => setEditing(null)}
          footer={
            <>
              <button type="button" className="btn btn-ghost" onClick={() => setEditing(null)}>Hủy</button>
              <button type="submit" form="addr-form" className="btn btn-primary" disabled={busy}>Lưu địa chỉ</button>
            </>
          }
        >
          <form id="addr-form" onSubmit={save}>
            <div className="grid-2">
              <Field label="Người nhận"><input value={editing.recipientName} onChange={set('recipientName')} maxLength={150} required /></Field>
              <Field label="Số điện thoại"><input value={editing.phoneNumber} onChange={set('phoneNumber')} inputMode="tel" required /></Field>
            </div>
            <div className="grid-2">
              <Field label="Tỉnh / Thành phố"><input value={editing.province} onChange={set('province')} required /></Field>
              <Field label="Quận / Huyện"><input value={editing.district} onChange={set('district')} required /></Field>
            </div>
            <div className="grid-2">
              <Field label="Phường / Xã"><input value={editing.ward} onChange={set('ward')} required /></Field>
              <Field label="Số nhà, tên đường"><input value={editing.street} onChange={set('street')} required /></Field>
            </div>
            <label className="check-row">
              <input type="checkbox" checked={!!editing.defaultAddress} onChange={set('defaultAddress')} /> Đặt làm địa chỉ mặc định
            </label>
          </form>
        </Modal>
      )}
    </>
  );
}
