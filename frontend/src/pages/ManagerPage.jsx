import React, { useEffect, useState } from 'react';
import { api } from '../api';
import { CATEGORIES, PRODUCTS } from '../data/products';
import { formatDateTime, qs } from '../utils';

// Cấu trúc phân cấp 3 tầng cho FR-07 & FR-10
const INITIAL_TREE = [
  {
    id: 'root-pc',
    name: 'PC Build Sẵn (Nguyên bộ)',
    children: [
      { key: 'prebuildpc', name: 'PC Build Sẵn', brands: ['CS Built'], techAttrs: ['cpuModel', 'vgaModel', 'warrantyMonth', 'preInstalledOS'] },
    ],
  },
  {
    id: 'root-parts',
    name: 'Linh kiện máy tính',
    children: [
      { key: 'cpu', name: 'CPU – Bộ vi xử lý', brands: ['Intel', 'AMD'], techAttrs: ['socket (LGA1700/AM5)', 'tdp (W)', 'igpu (Boolean)'] },
      { key: 'mainboard', name: 'Mainboard', brands: ['MSI', 'Gigabyte', 'ASUS'], techAttrs: ['socket', 'ramType (DDR4/DDR5)', 'form (mATX/ATX)', 'm2'] },
      { key: 'ram', name: 'RAM', brands: ['Kingston', 'Corsair', 'G.Skill'], techAttrs: ['ramType (DDR4/DDR5)', 'capacity (GB)', 'bus (MHz)'] },
      { key: 'vga', name: 'VGA – Card đồ họa', brands: ['MSI', 'Gigabyte', 'Sapphire'], techAttrs: ['tdp (W)', 'length (mm)', 'vram (GB)'] },
      { key: 'ssd', name: 'SSD – Ổ cứng', brands: ['Samsung', 'WD', 'Kingston'], techAttrs: ['nvme (Boolean)', 'capacity', 'interface'] },
      { key: 'psu', name: 'Nguồn máy tính', brands: ['Cooler Master', 'Corsair', 'MSI'], techAttrs: ['watt (W)', 'efficiency (80+ Bronze/Gold)'] },
      { key: 'case', name: 'Vỏ case', brands: ['Xigmatek', 'NZXT', 'Lian Li'], techAttrs: ['forms (mATX/ATX)', 'maxGpu (mm)', 'maxCooler (mm)'] },
      { key: 'cooler', name: 'Tản nhiệt', brands: ['Deepcool', 'Thermalright', 'Noctua'], techAttrs: ['height (mm)', 'sockets (LGA1700/AM5)'] },
    ],
  },
  {
    id: 'root-gear',
    name: 'Màn hình & Ngoại vi',
    children: [
      { key: 'monitor', name: 'Màn hình', brands: ['LG', 'Dell'], techAttrs: ['size (inch)', 'refreshRate (Hz)', 'panel (IPS/VA)'] },
    ],
  },
];

export default function ManagerPage({ go, busy, flash }) {
  const [activeTab, setActiveTab] = useState('logs'); // 'logs' | 'categories'
  const [logs, setLogs] = useState([]);
  const [logPage, setLogPage] = useState(1);
  const [selectedLog, setSelectedLog] = useState(null);
  const [loadErr, setLoadErr] = useState('');

  // States cho Quản lý danh mục đa cấp (FR-07 & FR-10)
  const [tree, setTree] = useState(() => {
    try {
      const saved = localStorage.getItem('cs_category_tree_v2');
      return saved ? JSON.parse(saved) : INITIAL_TREE;
    } catch {
      return INITIAL_TREE;
    }
  });
  const [selRoot, setSelRoot] = useState(tree[0]?.id || 'root-parts');
  const [selCat, setSelCat] = useState('cpu');
  const [newBrand, setNewBrand] = useState('');
  const [newAttr, setNewAttr] = useState('');

  async function loadLogs(targetPage = logPage) {
    setLoadErr('');
    try {
      const query = qs({ page: targetPage, size: 30 });
      const d = await api('/api/audit-logs' + query);
      const items = d?.items || d?.content || d?.data || (Array.isArray(d) ? d : []);
      setLogs(items);
      return d;
    } catch (e) {
      setLoadErr(e.message);
    }
  }

  useEffect(() => {
    if (go) {
      go(() => loadLogs(1));
    } else {
      loadLogs(1);
    }
  }, []);

  const saveTree = (next) => {
    setTree(next);
    localStorage.setItem('cs_category_tree_v2', JSON.stringify(next));
  };

  const currentRootObj = tree.find((r) => r.id === selRoot) || tree[0];
  const currentCatObj = currentRootObj?.children.find((c) => c.key === selCat) || currentRootObj?.children[0];

  const handleAddBrand = (e) => {
    e.preventDefault();
    if (!newBrand.trim() || !currentCatObj) return;
    const b = newBrand.trim();
    const next = tree.map((r) =>
      r.id !== currentRootObj.id
        ? r
        : {
            ...r,
            children: r.children.map((c) =>
              c.key === currentCatObj.key && !c.brands.includes(b)
                ? { ...c, brands: [...c.brands, b] }
                : c
            ),
          }
    );
    saveTree(next);
    setNewBrand('');
    flash && flash(`Đã thêm nhánh hãng "${b}" vào danh mục ${currentCatObj.name}!`);
  };

  const handleAddTechAttr = (e) => {
    e.preventDefault();
    if (!newAttr.trim() || !currentCatObj) return;
    const a = newAttr.trim();
    const next = tree.map((r) =>
      r.id !== currentRootObj.id
        ? r
        : {
            ...r,
            children: r.children.map((c) =>
              c.key === currentCatObj.key && !c.techAttrs.includes(a)
                ? { ...c, techAttrs: [...c.techAttrs, a] }
                : c
            ),
          }
    );
    saveTree(next);
    setNewAttr('');
    flash && flash(`Đã thêm thuộc tính kỹ thuật "${a}" cho ${currentCatObj.name}!`);
  };

  return (
    <div>
      <div className="perk-bar" style={{ gridTemplateColumns: 'repeat(3, 1fr)' }}>
        <div>
          <span className="ico">🛡️</span>
          <div>
            <b>Quyền Quản Trị Tối Cao</b>
            <span className="muted">Toàn quyền kiểm soát hệ thống</span>
          </div>
        </div>
        <div>
          <span className="ico">🗂️</span>
          <div>
            <b>{CATEGORIES.length} Nhóm danh mục · {PRODUCTS.length} SP</b>
            <span className="muted">Phân cấp 3 tầng & Thông số động</span>
          </div>
        </div>
        <div>
          <span className="ico">📋</span>
          <div>
            <b>{logs.length} Bản ghi nhật ký</b>
            <span className="muted">Kiểm toán bảo mật gần nhất</span>
          </div>
        </div>
      </div>

      {/* Thanh chuyển đổi Tab Quản trị */}
      <div className="tabs" style={{ maxWidth: '560px', marginBottom: '16px' }}>
        <button
          type="button"
          className={activeTab === 'logs' ? 'active' : ''}
          onClick={() => setActiveTab('logs')}
        >
          📜 Nhật ký kiểm toán (Audit Logs)
        </button>
        <button
          type="button"
          className={activeTab === 'categories' ? 'active' : ''}
          onClick={() => setActiveTab('categories')}
        >
          🗂️ Danh mục đa cấp & Thuộc tính
        </button>
      </div>

      {activeTab === 'logs' ? (
        <div className="card">
          <div className="card-head">
            <div>
              <h2>Nhật ký hoạt động & Kiểm toán bảo mật hệ thống</h2>
              <small>Theo dõi toàn bộ lịch sử thao tác của nhân viên và khách hàng</small>
            </div>
            <button
              type="button"
              className="btn btn-outline btn-sm"
              onClick={() => (go ? go(() => loadLogs(logPage)) : loadLogs(logPage))}
            >
              🔄 Tải lại nhật ký
            </button>
          </div>

          {loadErr && <div className="error">{loadErr}</div>}

          {logs.length === 0 ? (
            <div className="empty">
              <div className="empty-icon">📜</div>
              <strong>Chưa có bản ghi nhật ký nào</strong>
              <p>Hệ thống sẽ tự động ghi lại lịch sử thao tác khi có người dùng tương tác.</p>
            </div>
          ) : (
            <div className="table-wrap">
              <table className="table">
                <thead>
                  <tr>
                    <th>ID</th>
                    <th>Người thực hiện</th>
                    <th>Hành động (Action)</th>
                    <th>Thời gian</th>
                    <th style={{ textAlign: 'right' }}>Chi tiết</th>
                  </tr>
                </thead>
                <tbody>
                  {logs.map((log, i) => (
                    <tr key={log.id || i}>
                      <td>#{log.id || i + 1}</td>
                      <td className="cell-main">User ID: {log.userId ?? 'Hệ thống'}</td>
                      <td>
                        <span className="badge b-blue">{log.action || 'ACTION'}</span>
                      </td>
                      <td>{formatDateTime(log.createdAt)}</td>
                      <td className="actions">
                        <button
                          type="button"
                          className="btn btn-ghost btn-sm"
                          onClick={() => setSelectedLog(log)}
                        >
                          Xem JSON
                        </button>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}

          <div className="pager">
            <button
              type="button"
              className="btn btn-ghost btn-sm"
              disabled={logPage <= 1}
              onClick={() => {
                const next = logPage - 1;
                setLogPage(next);
                loadLogs(next);
              }}
            >
              ← Trang trước
            </button>
            <span>Trang {logPage}</span>
            <button
              type="button"
              className="btn btn-ghost btn-sm"
              onClick={() => {
                const next = logPage + 1;
                setLogPage(next);
                loadLogs(next);
              }}
            >
              Trang sau →
            </button>
          </div>
        </div>
      ) : (
        <div className="card">
          <div className="card-head">
            <div>
              <h2>Quản lý Cây Danh Mục Đa Cấp & Khuôn Mẫu Thuộc Tính Kỹ Thuật </h2>
              <small>Ví dụ phân cấp: {currentRootObj?.name} &gt; {currentCatObj?.name} &gt; {currentCatObj?.brands.join(', ')}</small>
            </div>
          </div>

          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(3, 1fr)', gap: '16px', marginTop: '12px' }}>
            {/* Cấp 1 */}
            <div style={{ border: '1px solid var(--line)', borderRadius: '10px', padding: '14px' }}>
              <h3 style={{ fontSize: '15px', marginBottom: '10px' }}>1️⃣ Cấp 1: Danh mục gốc</h3>
              <div style={{ display: 'grid', gap: '8px' }}>
                {tree.map((r) => (
                  <button
                    key={r.id}
                    type="button"
                    className={`btn ${currentRootObj?.id === r.id ? 'btn-primary' : 'btn-ghost'}`}
                    style={{ justifyContent: 'space-between' }}
                    onClick={() => {
                      setSelRoot(r.id);
                      setSelCat(r.children[0]?.key || '');
                    }}
                  >
                    <span>📁 {r.name}</span>
                    <span className="badge b-gray">{r.children.length} nhóm</span>
                  </button>
                ))}
              </div>
            </div>

            {/* Cấp 2 */}
            <div style={{ border: '1px solid var(--line)', borderRadius: '10px', padding: '14px' }}>
              <h3 style={{ fontSize: '15px', marginBottom: '10px' }}>2️⃣ Cấp 2: Loại sản phẩm</h3>
              <div style={{ display: 'grid', gap: '8px' }}>
                {currentRootObj?.children.map((c) => (
                  <button
                    key={c.key}
                    type="button"
                    className={`btn ${currentCatObj?.key === c.key ? 'btn-primary' : 'btn-ghost'}`}
                    style={{ justifyContent: 'space-between' }}
                    onClick={() => setSelCat(c.key)}
                  >
                    <span>{c.name}</span>
                    <span className="badge b-gray">{c.brands.length} hãng</span>
                  </button>
                ))}
              </div>
            </div>

            {/* Cấp 3 & FR-10 */}
            <div style={{ border: '1px solid var(--line)', borderRadius: '10px', padding: '14px' }}>
              <h3 style={{ fontSize: '15px', marginBottom: '8px' }}>3️⃣ Cấp 3: Hãng ({currentCatObj?.name})</h3>
              <div style={{ display: 'flex', flexWrap: 'wrap', gap: '6px', marginBottom: '12px' }}>
                {currentCatObj?.brands.map((b) => (
                  <span key={b} className="badge b-blue">{b}</span>
                ))}
              </div>
              <form onSubmit={handleAddBrand} style={{ display: 'flex', gap: '6px', marginBottom: '18px' }}>
                <input
                  placeholder="Thêm hãng cấp 3 (vd: Intel, ASUS)..."
                  value={newBrand}
                  onChange={(e) => setNewBrand(e.target.value)}
                />
                <button type="submit" className="btn btn-outline btn-sm">+ Thêm</button>
              </form>

              <h3 style={{ fontSize: '15px', marginBottom: '8px', borderTop: '1px solid var(--line)', paddingTop: '12px' }}>
                ⚙️ Thuộc tính kỹ thuật
              </h3>
              <div style={{ display: 'flex', flexWrap: 'wrap', gap: '6px', marginBottom: '12px' }}>
                {currentCatObj?.techAttrs.map((attr) => (
                  <span key={attr} className="badge b-amber">{attr}</span>
                ))}
              </div>
              <form onSubmit={handleAddTechAttr} style={{ display: 'flex', gap: '6px' }}>
                <input
                  placeholder="Thêm thông số (vd: Bus RAM, TDP)..."
                  value={newAttr}
                  onChange={(e) => setNewAttr(e.target.value)}
                />
                <button type="submit" className="btn btn-outline btn-sm">+ Thêm</button>
              </form>
            </div>
          </div>
        </div>
      )}

      {/* MODAL XEM CHI TIẾT LOG JSON */}
      {selectedLog && (
        <div className="modal-backdrop" onClick={() => setSelectedLog(null)}>
          <div className="modal modal-wide" onClick={(e) => e.stopPropagation()}>
            <div className="modal-head">
              <h3>Chi tiết nhật ký #{selectedLog.id}</h3>
              <button type="button" className="icon-btn" onClick={() => setSelectedLog(null)}>
                ✕
              </button>
            </div>
            <div className="modal-body">
              <div className="log-detail">
                <div>
                  <h5>THÔNG TIN CHUNG</h5>
                  <div className="json-box">
                    {JSON.stringify(
                      {
                        id: selectedLog.id,
                        userId: selectedLog.userId,
                        action: selectedLog.action,
                        createdAt: formatDateTime(selectedLog.createdAt),
                      },
                      null,
                      2
                    )}
                  </div>
                </div>
                <div>
                  <h5>DỮ LIỆU CHI TIẾT (DETAILS)</h5>
                  <div className="json-box">
                    {typeof selectedLog.details === 'object'
                      ? JSON.stringify(selectedLog.details, null, 2)
                      : String(selectedLog.details || 'Không có dữ liệu bổ sung')}
                  </div>
                </div>
              </div>
            </div>
            <div className="modal-foot">
              <button
                type="button"
                className="btn btn-primary"
                onClick={() => setSelectedLog(null)}
              >
                Đóng
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}