import React, { useState } from 'react';
import { formatDateTime } from '../utils';

const STATUS_META = {
  CHO_COC: { label: 'Chờ thu cọc 1/4', badge: 'b-amber' },
  DANG_LAP: { label: 'Đang thực hiện / Lắp ráp', badge: 'b-blue' },
  SAN_SANG: { label: 'Đã hoàn tất (Chờ giao)', badge: 'b-green' },
  HOAN_TAT: { label: 'Đã bàn giao khách', badge: 'b-gray' },
};

const readUser = () => {
  try {
    return JSON.parse(localStorage.getItem('cs_user') || 'null');
  } catch {
    return null;
  }
};

export default function TechnicianPage({ flash, user }) {
  const currentUser = user || readUser();
  const isManager = currentUser?.roleCode === 'QUAN_LY';

  const [tickets, setTickets] = useState(() => {
    try {
      const saved = localStorage.getItem('cs_reception_tickets');
      return saved ? JSON.parse(saved) : [];
    } catch {
      return [];
    }
  });

  const [statusFilter, setStatusFilter] = useState('ALL');

  const saveTickets = (nextList) => {
    setTickets(nextList);
    localStorage.setItem('cs_reception_tickets', JSON.stringify(nextList));
  };

  const handleUpdateStatus = (id, nextStatus) => {
    const next = tickets.map((t) => (t.id === id ? { ...t, status: nextStatus } : t));
    saveTickets(next);
    flash && flash(`Đã cập nhật trạng thái công việc ${id} thành công!`);
  };

  // 1. Lọc theo phân quyền: Thợ chỉ thấy việc có tên mình, Quản lý thấy tất cả
  const assignedTickets = tickets.filter((t) => {
    if (isManager) return true;
    if (!currentUser?.fullName) return false;
    // Kiểm tra tên thợ phụ trách có chứa tên của user đang đăng nhập không
    return t.assignedTech?.includes(currentUser.fullName);
  });

  // 2. Lọc tiếp theo trạng thái trên giao diện
  const filtered = assignedTickets.filter((t) => {
    return statusFilter === 'ALL' || t.status === statusFilter;
  });

  const inProgressCount = assignedTickets.filter((t) => t.status === 'DANG_LAP').length;
  const readyCount = assignedTickets.filter((t) => t.status === 'SAN_SANG').length;

  return (
    <div>
      <div className="perk-bar" style={{ gridTemplateColumns: 'repeat(3, 1fr)' }}>
        <div>
          <span className="ico">📋</span>
          <div>
            <b>{assignedTickets.length} Công việc của tôi</b>
            <span className="muted">{isManager ? '(Quyền Quản lý: Xem toàn bộ)' : `Thợ: ${currentUser?.fullName || '—'}`}</span>
          </div>
        </div>
        <div>
          <span className="ico">🔧</span>
          <div>
            <b>{inProgressCount} Đang thực hiện</b>
            <span className="muted">Cần xử lý kỹ thuật</span>
          </div>
        </div>
        <div>
          <span className="ico">✅</span>
          <div>
            <b>{readyCount} Đã hoàn tất</b>
            <span className="muted">Chờ lễ tân giao khách</span>
          </div>
        </div>
      </div>

      <div className="card">
        <div className="card-head">
          <div>
            <h2>Danh Sách Công Việc Kỹ Thuật Cá Nhân</h2>
            <small>Các đơn hàng lắp ráp và bảo hành được phân công riêng cho tài khoản của bạn</small>
          </div>
        </div>

        <div className="filters">
          <button
            type="button"
            className={`chip ${statusFilter === 'ALL' ? 'on' : ''}`}
            onClick={() => setStatusFilter('ALL')}
          >
            Tất cả ({assignedTickets.length})
          </button>
          <button
            type="button"
            className={`chip ${statusFilter === 'DANG_LAP' ? 'on' : ''}`}
            onClick={() => setStatusFilter('DANG_LAP')}
          >
            Đang thực hiện ({inProgressCount})
          </button>
          <button
            type="button"
            className={`chip ${statusFilter === 'SAN_SANG' ? 'on' : ''}`}
            onClick={() => setStatusFilter('SAN_SANG')}
          >
            Đã xong ({readyCount})
          </button>
        </div>

        {filtered.length === 0 ? (
          <div className="empty">
            <div className="empty-icon">🛠️</div>
            <strong>Không có công việc nào được giao cho bạn</strong>
            <p>Khi lễ tân lập phiếu và phân công đúng tên của bạn, công việc sẽ tự động xuất hiện tại đây.</p>
          </div>
        ) : (
          <div className="table-wrap">
            <table className="table">
              <thead>
                <tr>
                  <th>Mã phiếu</th>
                  <th>Khách hàng & Liên hệ</th>
                  <th>Cấu hình linh kiện / Yêu cầu</th>
                  <th>Thợ phụ trách</th>
                  <th>Trạng thái</th>
                  <th style={{ textAlign: 'right' }}>Thao tác kỹ thuật</th>
                </tr>
              </thead>
              <tbody>
                {filtered.map((t) => {
                  const st = STATUS_META[t.status] || STATUS_META.CHO_COC;
                  return (
                    <tr key={t.id}>
                      <td>
                        <div className="cell-main">{t.id}</div>
                        <div className="cell-sub">{formatDateTime(t.createdAt)}</div>
                      </td>
                      <td>
                        <div className="cell-main">{t.customerName}</div>
                        <div className="cell-sub">📞 {t.phone}</div>
                      </td>
                      <td style={{ maxWidth: '300px' }}>
                        <div className="cell-main" style={{ fontSize: '13.5px' }}>{t.itemsSummary}</div>
                        {t.note && <div className="cell-sub" style={{ color: 'var(--red)' }}>Ghi chú: {t.note}</div>}
                      </td>
                      <td>
                        <span className="badge b-amber">{t.assignedTech}</span>
                      </td>
                      <td>
                        <span className={`badge ${st.badge}`}>{st.label}</span>
                      </td>
                      <td className="actions">
                        {t.status === 'CHO_COC' && (
                          <span className="cell-sub" style={{ fontStyle: 'italic' }}>Chờ khách cọc 1/4 tại quầy</span>
                        )}
                        {t.status === 'DANG_LAP' && (
                          <button
                            type="button"
                            className="btn btn-primary btn-sm"
                            onClick={() => handleUpdateStatus(t.id, 'SAN_SANG')}
                          >
                            ✓ Báo hoàn tất lắp ráp
                          </button>
                        )}
                        {t.status === 'SAN_SANG' && (
                          <button
                            type="button"
                            className="btn btn-outline btn-sm"
                            onClick={() => handleUpdateStatus(t.id, 'DANG_LAP')}
                          >
                            ⟲ Làm lại / Sửa tiếp
                          </button>
                        )}
                        {t.status === 'HOAN_TAT' && (
                          <span className="cell-sub">Đã bàn giao xong</span>
                        )}
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        )}
      </div>
    </div>
  );
}