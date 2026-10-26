import React, { Fragment, useCallback, useEffect, useState } from 'react';
import { get } from '../api';
import { Empty, Field, Pager, Spinner } from '../components/ui';
import { formatDateTime, qs } from '../utils';

const ACTIONS = {
  LOGIN: 'Đăng nhập',
  LOGIN_FAILED: 'Đăng nhập sai',
  LOGOUT: 'Đăng xuất',
  USER_REGISTER: 'Đăng ký tài khoản',
  USER_VERIFIED: 'Xác thực tài khoản',
  PASSWORD_RESET_REQUESTED: 'Yêu cầu đặt lại mật khẩu',
  PASSWORD_RESET: 'Đặt lại mật khẩu',
  PASSWORD_CHANGE: 'Đổi mật khẩu',
  PROFILE_UPDATE: 'Cập nhật hồ sơ',
  STAFF_CREATE: 'Tạo nhân viên',
  STAFF_UPDATE: 'Sửa nhân viên',
  STAFF_LOCK: 'Khóa nhân viên',
  STAFF_UNLOCK: 'Mở khóa nhân viên',
};
const TONE = { LOGIN_FAILED: 'b-red', STAFF_LOCK: 'b-red', STAFF_CREATE: 'b-green', STAFF_UNLOCK: 'b-green', PASSWORD_CHANGE: 'b-amber', PASSWORD_RESET: 'b-amber' };
const SIZE = 15;

const pretty = (json) => {
  if (!json) return null;
  try { return JSON.stringify(JSON.parse(json), null, 2); } catch { return json; }
};
const toIso = (v) => (v ? new Date(v).toISOString() : '');

export default function LogsPage() {
  const [filters, setFilters] = useState({ action: '', userId: '', from: '', to: '' });
  const [page, setPage] = useState(1);
  const [data, setData] = useState(null);
  const [err, setErr] = useState('');
  const [open, setOpen] = useState(null);

  const load = useCallback(() => {
    setErr('');
    get(`/api/audit-logs${qs({ action: filters.action, userId: filters.userId, from: toIso(filters.from), to: toIso(filters.to), page, size: SIZE })}`)
      .then(setData)
      .catch((e) => setErr(e.message));
  }, [filters, page]);
  useEffect(load, [load]);

  const setF = (k) => (e) => { setFilters({ ...filters, [k]: e.target.value }); setPage(1); };

  return (
    <div className="card">
      <div className="card-head">
        <h2>Nhật ký thao tác</h2>
        <button className="btn btn-ghost btn-sm" onClick={load}>↻ Làm mới</button>
      </div>
      <div className="toolbar">
        <Field label="Hành động">
          <select value={filters.action} onChange={setF('action')}>
            <option value="">Tất cả</option>
            {Object.entries(ACTIONS).map(([k, v]) => <option key={k} value={k}>{v}</option>)}
          </select>
        </Field>
        <Field label="Mã người dùng"><input type="number" min="1" value={filters.userId} onChange={setF('userId')} placeholder="vd: 1" /></Field>
        <Field label="Từ lúc"><input type="datetime-local" value={filters.from} onChange={setF('from')} /></Field>
        <Field label="Đến lúc"><input type="datetime-local" value={filters.to} onChange={setF('to')} /></Field>
      </div>

      {err && <div className="error">{err}</div>}
      {!data && !err && <Spinner />}
      {data && data.items.length === 0 && <Empty icon="📋" title="Chưa có nhật ký phù hợp" text="Thử mở rộng khoảng thời gian hoặc bỏ bớt bộ lọc." />}
      {data && data.items.length > 0 && (
        <>
          <div className="table-wrap">
            <table className="table">
              <thead><tr><th>Thời gian</th><th>Người thực hiện</th><th>Hành động</th><th>Đối tượng</th><th>IP</th><th /></tr></thead>
              <tbody>
                {data.items.map((l) => {
                  const hasDetail = l.oldValues || l.newValues;
                  return (
                    <Fragment key={l.id}>
                      <tr>
                        <td>{formatDateTime(l.createdAt)}</td>
                        <td>{l.userName ? <><span className="cell-main">{l.userName}</span><div className="cell-sub">#{l.userId}</div></> : <span className="muted">Hệ thống / ẩn danh</span>}</td>
                        <td><span className={`badge ${TONE[l.action] || 'b-gray'}`}>{ACTIONS[l.action] || l.action}</span></td>
                        <td>{l.entityName ? `${l.entityName} #${l.entityId}` : '—'}</td>
                        <td>{l.ipAddress || '—'}</td>
                        <td>{hasDetail && <button className="btn btn-ghost btn-sm" onClick={() => setOpen(open === l.id ? null : l.id)}>{open === l.id ? 'Ẩn' : 'Chi tiết'}</button>}</td>
                      </tr>
                      {open === l.id && (
                        <tr>
                          <td colSpan={6}>
                            <div className="log-detail">
                              <div><h5>Trước khi thay đổi</h5><div className="json-box">{pretty(l.oldValues) || '—'}</div></div>
                              <div><h5>Sau khi thay đổi</h5><div className="json-box">{pretty(l.newValues) || '—'}</div></div>
                            </div>
                          </td>
                        </tr>
                      )}
                    </Fragment>
                  );
                })}
              </tbody>
            </table>
          </div>
          <Pager page={data.page} pageSize={data.pageSize} total={data.total} onChange={setPage} />
        </>
      )}
    </div>
  );
}
