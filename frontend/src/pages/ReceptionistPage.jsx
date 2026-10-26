import React, { useState , useEffect } from 'react';
import { get, post, put } from '../api';
import { formatDateTime, formatVND, ROLE_LABELS } from '../utils';

const INITIAL_TICKETS = [
  {
    id: 'PC-882401',
    customerName: 'Nguyễn Hoàng Nam',
    phone: '0908123456',
    serviceType: 'BUILD_PC',
    itemsSummary: 'Ryzen 7 9800X3D + RTX 5080 + X870E Aorus + RAM 32GB DDR5',
    totalAmount: 76520000,
    depositPaid: false,
    assignedTech: 'Lê Văn Hùng (Thợ chính)',
    status: 'CHO_COC', // CHO_COC | DANG_LAP | SAN_SANG | HOAN_TAT
    createdAt: new Date(Date.now() - 3600 * 1000 * 2).toISOString(),
    note: 'Đi dây gọn, đồng bộ LED màu xanh Cyan',
  },
  {
    id: 'PC-882402',
    customerName: 'Trần Quốc Tuấn',
    phone: '0912987654',
    serviceType: 'BUILD_PC',
    itemsSummary: 'Core i5-14600K + RTX 4070 Super + B760M TUF + SSD 1TB',
    totalAmount: 34960000,
    depositPaid: true,
    assignedTech: 'Phạm Quốc Bảo (Thợ lắp ráp)',
    status: 'DANG_LAP',
    createdAt: new Date(Date.now() - 3600 * 1000 * 5).toISOString(),
    note: 'Cài sẵn Windows 11 Pro + Office',
  },
  {
    id: 'BH-104290',
    customerName: 'Lê Thị Mỹ Linh',
    phone: '0987654321',
    serviceType: 'BAO_HANH',
    itemsSummary: 'Bảo hành VGA MSI RTX 4070 (Kiểm tra quạt tản nhiệt kêu)',
    totalAmount: 0,
    depositPaid: true,
    assignedTech: 'Lê Văn Hùng (Thợ chính)',
    status: 'SAN_SANG',
    createdAt: new Date(Date.now() - 3600 * 1000 * 24).toISOString(),
    note: 'Bảo hành chính hãng theo Serial',
  },
];

const SERVICE_TYPES = {
  BUILD_PC: { label: '🛠️ Lắp ráp PC (Cọc 1/4)', badge: 'b-red' },
  BAO_HANH: { label: '🛡️ Tiếp nhận Bảo hành', badge: 'b-blue' },
  SUA_CHUA: { label: '🔧 Vệ sinh / Sửa chữa PC', badge: 'b-amber' },
  MUA_LE: { label: '🛍️ Nhận linh kiện tại quầy', badge: 'b-green' },
};

const STATUS_META = {
  CHO_COC: { label: 'Chờ thu cọc 1/4', badge: 'b-amber' },
  DANG_LAP: { label: 'Thợ đang xử lý / lắp máy', badge: 'b-blue' },
  SAN_SANG: { label: 'Sẵn sàng bàn giao khách', badge: 'b-green' },
  HOAN_TAT: { label: 'Đã bàn giao & Thu đủ', badge: 'b-gray' },
};

export default function ReceptionistPage({ flash }) {
  const [tickets, setTickets] = useState(() => {
    try {
      const saved = localStorage.getItem('cs_reception_tickets');
      return saved ? JSON.parse(saved) : INITIAL_TICKETS;
    } catch {
      return INITIAL_TICKETS;
    }
  });
    const [techStaffList, setTechStaffList] = useState([
    'Lê Văn Hùng (Thợ chính)',
    'Phạm Quốc Bảo (Thợ lắp ráp)',
  ]);

  useEffect(() => {
    get('/api/staff?size=100')
      .then((res) => {
        const items = res?.items || res?.content || res?.data || (Array.isArray(res) ? res : []);
        const techs = items
          .filter((s) => s.roleCode === 'THO' && s.status === 'ACTIVE')
          .map((s) => `${s.fullName} (ID: ${s.id})`);

        if (techs.length > 0) {
          setTechStaffList(techs);
        }
      })
      .catch(() => {
      });
  }, []);

  const [statusFilter, setStatusFilter] = useState('ALL');
  const [serviceFilter, setServiceFilter] = useState('ALL');
  const [search, setSearch] = useState('');
  const [showModal, setShowModal] = useState(false);
  const [selectedTicket, setSelectedTicket] = useState(null);
  const [selectedTech, setSelectedTech] = useState('');
  const saveTickets = (nextList) => {
    setTickets(nextList);
    localStorage.setItem('cs_reception_tickets', JSON.stringify(nextList));
  };

  // Tạo phiếu tiếp nhận mới tại quầy Lễ tân
  const handleCreateTicket = (e) => {
    e.preventDefault();
    const f = Object.fromEntries(new FormData(e.currentTarget));
    const total = Number(f.totalAmount || 0);
    const isBuildPc = f.serviceType === 'BUILD_PC';
    const prefix = isBuildPc ? 'PC-' : f.serviceType === 'BAO_HANH' ? 'BH-' : 'DV-';

    const newTicket = {
      id: prefix + Math.floor(100000 + Math.random() * 900000),
      customerName: f.customerName.trim(),
      phone: f.phone.trim(),
      serviceType: f.serviceType,
      itemsSummary: f.itemsSummary.trim(),
      totalAmount: total,
      depositPaid: f.depositPaid === 'on' || total === 0,
      assignedTech: f.assignedTech,
      status:
        isBuildPc && f.depositPaid !== 'on'
          ? 'CHO_COC'
          : 'DANG_LAP',
      createdAt: new Date().toISOString(),
      note: f.note?.trim() || '',
    };

    const next = [newTicket, ...tickets];
    saveTickets(next);
    setShowModal(false);
    flash && flash(`Đã tạo phiếu tiếp nhận ${newTicket.id} cho khách hàng ${newTicket.customerName}!`);
  };

  // Xác nhận đã thu cọc 1/4 -> Chuyển cho Thợ lắp
  const handleConfirmDeposit = (id) => {
    const next = tickets.map((t) =>
      t.id === id ? { ...t, depositPaid: true, status: 'DANG_LAP' } : t
    );
    saveTickets(next);
    flash && flash(`Đã xác nhận thu cọc 1/4 cho phiếu ${id} và chuyển cho ${ROLE_LABELS.THO} lắp ráp!`);
  };

  // Chuyển trạng thái phiếu
  const handleUpdateStatus = (id, nextStatus) => {
    const next = tickets.map((t) => (t.id === id ? { ...t, status: nextStatus } : t));
    saveTickets(next);
    flash && flash(`Đã cập nhật trạng thái phiếu ${id} thành "${STATUS_META[nextStatus]?.label}"!`);
  };

  const filtered = tickets.filter((t) => {
    const matchStatus = statusFilter === 'ALL' || t.status === statusFilter;
    const matchService = serviceFilter === 'ALL' || t.serviceType === serviceFilter;
    const kw = search.trim().toLowerCase();
    const matchKw =
      !kw ||
      t.id.toLowerCase().includes(kw) ||
      t.customerName.toLowerCase().includes(kw) ||
      t.phone.toLowerCase().includes(kw) ||
      t.itemsSummary.toLowerCase().includes(kw);
    return matchStatus && matchService && matchKw;
  });

  // Thống kê nhanh tại quầy lễ tân
  const waitingDepositCount = tickets.filter((t) => t.status === 'CHO_COC').length;
  const inProgressCount = tickets.filter((t) => t.status === 'DANG_LAP').length;
  const readyCount = tickets.filter((t) => t.status === 'SAN_SANG').length;
  const totalDepositCollected = tickets
    .filter((t) => t.serviceType === 'BUILD_PC' && t.depositPaid)
    .reduce((sum, t) => sum + Math.ceil(t.totalAmount / 4), 0);

  return (
    <div>
      {/* Thống kê ca trực của Lễ tân */}
      <div className="perk-bar">
        <div>
          <span className="ico">⏳</span>
          <div>
            <b>{waitingDepositCount} Phiếu chờ cọc 1/4</b>
            <span className="muted">Khách đặt lắp PC chưa cọc</span>
          </div>
        </div>
        <div>
          <span className="ico">🔧</span>
          <div>
            <b>{inProgressCount} Đang lắp / Bảo hành</b>
            <span className="muted">Đang ở phòng {ROLE_LABELS.THO}</span>
          </div>
        </div>
        <div>
          <span className="ico">✅</span>
          <div>
            <b>{readyCount} Chờ khách lấy máy</b>
            <span className="muted">Sẵn sàng bàn giao tại quầy</span>
          </div>
        </div>
        <div>
          <span className="ico">💳</span>
          <div>
            <b>{formatVND(totalDepositCollected)}</b>
            <span className="muted">Tổng tiền cọc 1/4 đã thu</span>
          </div>
        </div>
      </div>

      <div className="card">
        <div className="card-head">
          <div>
            <h2>Quầy Tiếp Nhận Khách Hàng & Điều Phối Lắp Ráp ({ROLE_LABELS.LE_TAN})</h2>
            <small>
              Tiếp nhận khách tại quầy, thu cọc 1/4 (25%) đơn Build PC, bàn giao cho {ROLE_LABELS.THO} và trả máy
            </small>
          </div>
          <button
            type="button"
            className="btn btn-primary"
            onClick={() => setShowModal(true)}
          >
            + Tạo phiếu tiếp nhận khách
          </button>
        </div>

        {/* Bộ lọc nhanh bằng Chips */}
        <div className="filters">
          <input
            type="search"
            placeholder="🔍 Tìm mã phiếu, tên khách hoặc SĐT..."
            value={search}
            onChange={(e) => setSearch(e.target.value)}
          />

          <select value={serviceFilter} onChange={(e) => setServiceFilter(e.target.value)}>
            <option value="ALL">Tất cả loại dịch vụ</option>
            {Object.entries(SERVICE_TYPES).map(([k, v]) => (
              <option key={k} value={k}>
                {v.label}
              </option>
            ))}
          </select>

          <button
            type="button"
            className={`chip ${statusFilter === 'ALL' ? 'on' : ''}`}
            onClick={() => setStatusFilter('ALL')}
          >
            Tất cả ({tickets.length})
          </button>
          {Object.entries(STATUS_META).map(([k, v]) => (
            <button
              key={k}
              type="button"
              className={`chip ${statusFilter === k ? 'on' : ''}`}
              onClick={() => setStatusFilter(k)}
            >
              {v.label}
            </button>
          ))}
        </div>

        {/* Bảng danh sách phiếu tiếp nhận tại quầy */}
        {filtered.length === 0 ? (
          <div className="empty">
            <div className="empty-icon">🛎️</div>
            <strong>Không có phiếu tiếp nhận nào phù hợp</strong>
            <p>Hãy tạo phiếu mới khi có khách đến quầy mua linh kiện, đặt lắp PC hoặc bảo hành.</p>
          </div>
        ) : (
          <div className="table-wrap">
            <table className="table">
              <thead>
                <tr>
                  <th>Mã phiếu</th>
                  <th>Khách hàng</th>
                  <th>Loại dịch vụ & Nội dung</th>
                  <th>Chi phí & Tiền cọc (1/4)</th>
                  <th>Thợ phụ trách</th>
                  <th>Trạng thái</th>
                  <th style={{ textAlign: 'right' }}>Thao tác Lễ tân</th>
                </tr>
              </thead>
              <tbody>
                {filtered.map((t) => {
                  const deposit = Math.ceil((t.totalAmount || 0) / 4);
                  const remain = (t.totalAmount || 0) - deposit;
                  const srv = SERVICE_TYPES[t.serviceType] || SERVICE_TYPES.BUILD_PC;
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
                      <td style={{ maxWidth: '280px' }}>
                        <span className={`badge ${srv.badge}`} style={{ marginBottom: '4px' }}>
                          {srv.label}
                        </span>
                        <div className="cell-main" style={{ fontSize: '13.5px' }}>
                          {t.itemsSummary}
                        </div>
                        {t.note && <div className="cell-sub">Ghi chú: {t.note}</div>}
                      </td>
                      <td>
                        {t.totalAmount > 0 ? (
                          <div>
                            <div className="cell-main">Tổng: {formatVND(t.totalAmount)}</div>
                            {t.serviceType === 'BUILD_PC' && (
                              <>
                                <div className="cell-sub" style={{ color: 'var(--red)', fontWeight: 600 }}>
                                  Cọc 1/4: {formatVND(deposit)} {t.depositPaid ? '✓' : '(Chưa thu)'}
                                </div>
                                <div className="cell-sub">Còn lại 3/4: {formatVND(remain)}</div>
                              </>
                            )}
                          </div>
                        ) : (
                          <span className="badge b-green">Miễn phí / Bảo hành</span>
                        )}
                      </td>
                      <td>
                        <div className="cell-main">{t.assignedTech}</div>
                      </td>
                      <td>
                        <span className={`badge ${st.badge}`}>{st.label}</span>
                      </td>
                      <td className="actions">
                        {t.status === 'CHO_COC' && (
                          <button
                            type="button"
                            className="btn btn-primary btn-sm"
                            onClick={() => handleConfirmDeposit(t.id)}
                          >
                            💰 Thu cọc 1/4
                          </button>
                        )}
                        {t.status === 'DANG_LAP' && (
                          <button
                            type="button"
                            className="btn btn-outline btn-sm"
                            onClick={() => handleUpdateStatus(t.id, 'SAN_SANG')}
                          >
                            📦 Báo lắp xong
                          </button>
                        )}
                        {t.status === 'SAN_SANG' && (
                          <button
                            type="button"
                            className="btn btn-primary btn-sm"
                            onClick={() => handleUpdateStatus(t.id, 'HOAN_TAT')}
                          >
                            🤝 Giao máy & Thu 3/4
                          </button>
                        )}
                        <button
                          type="button"
                          className="btn btn-ghost btn-sm"
                          onClick={() => setSelectedTicket(t)}
                        >
                          In phiếu
                        </button>
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        )}
      </div>

      {/* MODAL TIẾP NHẬN KHÁCH MỚI TẠI QUẦY */}
      {showModal && (
        <div className="modal-backdrop" onClick={() => setShowModal(false)}>
          <div className="modal modal-wide" onClick={(e) => e.stopPropagation()}>
            <div className="modal-head">
              <h3>Lập Phiếu Tiếp Nhận Khách Hàng Tại Quầy</h3>
              <button type="button" className="icon-btn" onClick={() => setShowModal(false)}>
                ✕
              </button>
            </div>

            <form onSubmit={handleCreateTicket}>
              <div className="modal-body">
                <div className="grid-2">
                  <label className="field">
                    <span>Họ và tên khách hàng *</span>
                    <input name="customerName" placeholder="VD: Nguyễn Văn An" required />
                  </label>
                  <label className="field">
                    <span>Số điện thoại liên hệ *</span>
                    <input name="phone" placeholder="09xxxxxxxx" required />
                  </label>
                </div>

                <div className="grid-2">
                  <label className="field">
                    <span>Loại yêu cầu dịch vụ *</span>
                    <select name="serviceType" defaultValue="BUILD_PC">
                      {Object.entries(SERVICE_TYPES).map(([k, v]) => (
                        <option key={k} value={k}>
                          {v.label}
                        </option>
                      ))}
                    </select>
                  </label>

                  <label className="field">
                    <span>Phân công Thợ kỹ thuật phụ trách *</span>
                    <select
                      name="assignedTech"
                      value={selectedTech}
                      onChange={(e) => setSelectedTech(e.target.value)}
                      required
                    >
                      <option value="" disabled>-- Chọn thợ kỹ thuật --</option>
                      {techStaffList.map((tech) => (
                        <option key={tech} value={tech}>
                          {tech}
                        </option>
                      ))}
                    </select>
                  </label>
                  </div>

                <label className="field">
                  <span>Cấu hình linh kiện / Tình trạng máy tiếp nhận *</span>
                  <textarea
                    name="itemsSummary"
                    rows="3"
                    placeholder="VD: Lắp bộ PC Ryzen 7 9800X3D + RTX 5080 + RAM 32GB (hoặc Mô tả lỗi bảo hành, Số Serial...)"
                    required
                  />
                </label>

                <div className="grid-2">
                  <label className="field">
                    <span>Tổng giá trị đơn hàng / Dịch vụ (VNĐ)</span>
                    <input
                      name="totalAmount"
                      type="number"
                      min="0"
                      step="10000"
                      defaultValue="20000000"
                    />
                    <small>Hệ thống sẽ tự động tính 25% (1/4) tiền cọc lắp ráp.</small>
                  </label>

                  <label className="field">
                    <span>Ghi chú cho kỹ thuật viên</span>
                    <input
                      name="note"
                      placeholder="VD: Khách hẹn 17h chiều lấy máy, cài Win 11..."
                    />
                  </label>
                </div>

                <label className="check-row">
                  <input type="checkbox" name="depositPaid" defaultChecked />
                  <span>Khách đã thanh toán tiền đặt cọc 1/4 (25%) trực tiếp tại quầy Lễ tân</span>
                </label>
              </div>

              <div className="modal-foot">
                <button
                  type="button"
                  className="btn btn-ghost"
                  onClick={() => setShowModal(false)}
                >
                  Hủy
                </button>
                <button type="submit" className="btn btn-primary">
                  Lưu phiếu & Chuyển kỹ thuật
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* MODAL XEM / IN PHIẾU BIÊN NHẬN CHO KHÁCH */}
      {selectedTicket && (
        <div className="modal-backdrop" onClick={() => setSelectedTicket(null)}>
          <div className="modal" onClick={(e) => e.stopPropagation()}>
            <div className="modal-head">
              <h3>Phiếu Biên Nhận #{selectedTicket.id}</h3>
              <button type="button" className="icon-btn" onClick={() => setSelectedTicket(null)}>
                ✕
              </button>
            </div>
            <div className="modal-body">
              <div className="kv" style={{ marginTop: 0 }}>
                <div>
                  <span>Mã biên nhận:</span>
                  <b>{selectedTicket.id}</b>
                </div>
                <div>
                  <span>Khách hàng:</span>
                  <b>{selectedTicket.customerName} ({selectedTicket.phone})</b>
                </div>
                <div>
                  <span>Thời gian tiếp nhận:</span>
                  <b>{formatDateTime(selectedTicket.createdAt)}</b>
                </div>
                <div>
                  <span>Hạng mục:</span>
                  <b>{SERVICE_TYPES[selectedTicket.serviceType]?.label}</b>
                </div>
                <div>
                  <span>Chi tiết linh kiện:</span>
                  <b>{selectedTicket.itemsSummary}</b>
                </div>
                <div>
                  <span>Kỹ thuật viên:</span>
                  <b>{selectedTicket.assignedTech}</b>
                </div>
                <div>
                  <span>Tổng chi phí:</span>
                  <b style={{ color: 'var(--red)' }}>{formatVND(selectedTicket.totalAmount)}</b>
                </div>
                {selectedTicket.serviceType === 'BUILD_PC' && (
                  <>
                    <div>
                      <span>Tiền cọc lắp ráp (1/4):</span>
                      <b>
                        {formatVND(Math.ceil(selectedTicket.totalAmount / 4))}{' '}
                        ({selectedTicket.depositPaid ? 'Đã thu' : 'Chưa thu'})
                      </b>
                    </div>
                    <div>
                      <span>Còn phải thu khi giao máy (3/4):</span>
                      <b style={{ color: 'var(--red)' }}>
                        {formatVND(
                          selectedTicket.totalAmount - Math.ceil(selectedTicket.totalAmount / 4)
                        )}
                      </b>
                    </div>
                  </>
                )}
              </div>
            </div>
            <div className="modal-foot">
              <button
                type="button"
                className="btn btn-ghost"
                onClick={() => setSelectedTicket(null)}
              >
                Đóng
              </button>
              <button
                type="button"
                className="btn btn-primary"
                onClick={() => window.print()}
              >
                🖨️ In phiếu giao khách
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}