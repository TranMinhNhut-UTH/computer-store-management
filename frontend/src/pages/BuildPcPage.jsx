import React, { useMemo, useState } from 'react';
import { Modal } from '../components/ui';
import { BUILD_SLOTS, CATEGORIES, PRESETS, PRODUCTS, byId, catIcon, catLabel, checkCompat } from '../data/products';
import { formatVND } from '../utils';

export default function BuildPcPage({ flash, onAddMany, onGoToCart, onGoToAddresses }) {
  const [sel, setSel] = useState(() => {
    // FR-16: Hỗ trợ khôi phục cấu hình nếu người dùng mở qua link chia sẻ dạng ?build=c1,m1,r1...
    try {
      const params = new URLSearchParams(window.location.search);
      const buildParam = params.get('build');
      if (buildParam) {
        const ids = buildParam.split(',');
        const loaded = {};
        ids.forEach((id) => {
          const p = byId(id);
          if (p) loaded[p.cat] = p;
        });
        return loaded;
      }
    } catch {}
    return {};
  });

  const [picking, setPicking] = useState(null);
  const [budgetFilter, setBudgetFilter] = useState('all'); // FR-13: Lọc theo ngân sách gợi ý

  const chosen = BUILD_SLOTS.map((k) => sel[k]).filter(Boolean);
  const total = chosen.reduce((s, p) => s + p.price, 0);
  const issues = useMemo(() => checkCompat(sel), [sel]);
  const coreReady = ['cpu', 'mainboard', 'ram', 'psu', 'case'].every((k) => sel[k]);

  const applyPreset = (preset) => {
    const next = {};
    preset.parts.forEach((id) => { const p = byId(id); if (p) next[p.cat] = p; });
    setSel(next);
    flash(`Đã áp dụng cấu hình ${preset.label.replace(/^\S+\s/, '')}. Bạn vẫn có thể đổi từng linh kiện.`);
  };

  const choose = (p) => { setSel((s) => ({ ...s, [p.cat]: p })); setPicking(null); };
  const clearSlot = (k) => setSel((s) => { const n = { ...s }; delete n[k]; return n; });

  const addAll = () => {
    if (chosen.length === 0) return flash('Hãy chọn ít nhất một linh kiện.', true);
    onAddMany(chosen);
    onGoToCart();
  };

  const copySummary = async () => {
    const text = ['Cấu hình PC – CS Computer Store', ...chosen.map((p) => `- ${catLabel(p.cat)}: ${p.name} (${formatVND(p.price)})`), `Tổng: ${formatVND(total)}`].join('\n');
    try {
      await navigator.clipboard.writeText(text);
      flash('Đã sao chép cấu hình, bạn có thể gửi cho bạn bè hoặc nhân viên tư vấn.');
    } catch {
      flash('Trình duyệt không cho phép sao chép tự động.', true);
    }
  };

  // FR-16: Chia sẻ cấu hình qua Link trực tiếp
  const shareLink = async () => {
    const ids = BUILD_SLOTS.map((k) => sel[k]?.id).filter(Boolean).join(',');
    const url = `${window.location.origin}${window.location.pathname}?build=${ids}`;
    try {
      await navigator.clipboard.writeText(url);
      flash('Đã sao chép liên kết cấu hình! Gửi link này cho bạn bè để xem đúng bộ máy.');
    } catch {
      flash('Không thể tạo liên kết chia sẻ.', true);
    }
  };

  // FR-13: Gợi ý cấu hình tự động theo khoảng ngân sách
  const applyBudgetPreset = (maxPrice) => {
    // Thuật toán chọn linh kiện tối ưu trong tầm giá
    const cpuList = PRODUCTS.filter((p) => p.cat === 'cpu' && p.price <= maxPrice * 0.35).sort((a, b) => b.price - a.price);
    const mbList = PRODUCTS.filter((p) => p.cat === 'mainboard' && p.price <= maxPrice * 0.25).sort((a, b) => b.price - a.price);
    const ramList = PRODUCTS.filter((p) => p.cat === 'ram' && p.price <= maxPrice * 0.12).sort((a, b) => b.price - a.price);
    const ssdList = PRODUCTS.filter((p) => p.cat === 'ssd' && p.price <= maxPrice * 0.12).sort((a, b) => b.price - a.price);
    const psuList = PRODUCTS.filter((p) => p.cat === 'psu' && p.price <= maxPrice * 0.12).sort((a, b) => b.price - a.price);
    const caseList = PRODUCTS.filter((p) => p.cat === 'case' && p.price <= maxPrice * 0.12).sort((a, b) => b.price - a.price);

    if (cpuList.length && mbList.length && ramList.length) {
      setSel({
        cpu: cpuList[0],
        mainboard: mbList[0],
        ram: ramList[0],
        ssd: ssdList[0],
        psu: psuList[0],
        case: caseList[0],
      });
      flash(`Đã gợi ý cấu hình tối ưu trong tầm giá ${formatVND(maxPrice)}!`);
    } else {
      flash('Ngân sách quá thấp để build cấu hình tối thiểu.', true);
    }
  };

  const warnFor = (p) => checkCompat({ ...sel, [p.cat]: p }).filter((i) => i.cats.includes(p.cat))[0]?.text;

  return (
    <div className="build-layout">
      <div>
        <div className="card">
          <div className="card-head">
            <div>
              <h2>Chọn linh kiện & Gợi ý theo nhu cầu (FR-13, 14)</h2>
              <small>Hệ thống tự động kiểm tra socket, RAM, nguồn và case thời gian thực.</small>
            </div>
          </div>

          {/* FR-13: Chọn mẫu sẵn & Ngân sách */}
          <div className="presets" style={{ flexDirection: 'column', alignItems: 'flex-start', gap: '10px' }}>
            <div style={{ display: 'flex', gap: '8px', flexWrap: 'wrap', alignItems: 'center' }}>
              <span className="muted">Cấu hình mẫu:</span>
              {PRESETS.map((p) => (
                <button type="button" key={p.key} className="chip" onClick={() => applyPreset(p)}>{p.label}</button>
              ))}
              {chosen.length > 0 && <button type="button" className="chip" onClick={() => setSel({})}>Xóa hết</button>}
            </div>

            <div style={{ display: 'flex', gap: '8px', flexWrap: 'wrap', alignItems: 'center' }}>
              <span className="muted">Gợi ý theo ngân sách:</span>
              <button type="button" className={`chip ${budgetFilter === 15e6 ? 'on' : ''}`} onClick={() => { setBudgetFilter(15e6); applyBudgetPreset(15e6); }}>Dưới 15 Triệu</button>
              <button type="button" className={`chip ${budgetFilter === 25e6 ? 'on' : ''}`} onClick={() => { setBudgetFilter(25e6); applyBudgetPreset(25e6); }}>Khoảng 25 Triệu</button>
              <button type="button" className={`chip ${budgetFilter === 45e6 ? 'on' : ''}`} onClick={() => { setBudgetFilter(45e6); applyBudgetPreset(45e6); }}>Cao cấp 45+ Triệu</button>
            </div>
          </div>

          {BUILD_SLOTS.map((k) => {
            const cat = CATEGORIES.find((c) => c.key === k) || { label: k };
            const p = sel[k];
            return (
              <div key={k} className={`slot ${p ? 'filled' : ''}`}>
                <div className="slot-ico">{catIcon(k)}</div>
                <div>
                  <div className="slot-label">{cat.label}</div>
                  {p ? (
                    <>
                      <div className="slot-name">{p.name}</div>
                      <div className="slot-price">{formatVND(p.price)}</div>
                    </>
                  ) : (
                    <div className="muted">Chưa chọn{k === 'vga' ? ' (không bắt buộc nếu CPU có iGPU)' : ''}</div>
                  )}
                </div>
                <div className="slot-actions">
                  <button type="button" className={`btn btn-sm ${p ? 'btn-ghost' : 'btn-primary'}`} onClick={() => setPicking(k)}>
                    {p ? 'Đổi' : '+ Chọn'}
                  </button>
                  {p && <button type="button" className="btn btn-sm btn-danger" onClick={() => clearSlot(k)} aria-label={`Bỏ ${cat.label}`}>✕</button>}
                </div>
              </div>
            );
          })}
        </div>
      </div>

      <aside className="card summary">
        <h3>Cấu hình của bạn</h3>
        {issues.length > 0 && issues.map((i) => <div key={i.text} className="issue warn"><span>⚠️</span><span>{i.text}</span></div>)}
        {issues.length === 0 && coreReady && <div className="issue ok"><span>✅</span><span>Các linh kiện đã chọn tương thích hoàn hảo.</span></div>}
        {issues.length === 0 && !coreReady && <div className="issue info"><span>ℹ️</span><span>Chọn đủ CPU, mainboard, RAM, nguồn và case để hoàn tất kiểm tra.</span></div>}

        <div className="total-row">
          <span>Tạm tính ({chosen.length} linh kiện)</span>
          <strong>{formatVND(total)}</strong>
        </div>

        <button className="btn btn-primary btn-block btn-lg" onClick={addAll} disabled={chosen.length === 0}>Thêm cả cấu hình vào giỏ</button>

        {/* FR-16: Nút chia sẻ Link cấu hình & Sao chép tóm tắt */}
        <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '8px', marginTop: '8px' }}>
          <button className="btn btn-outline btn-sm" onClick={shareLink} disabled={chosen.length === 0}>🔗 Chia sẻ Link</button>
          <button className="btn btn-ghost btn-sm" onClick={copySummary} disabled={chosen.length === 0}>📋 Sao chép text</button>
        </div>

        <p className="muted" style={{ fontSize: 13, marginTop: 12 }}>
          Muốn nhận máy lắp sẵn? Thêm địa chỉ nhận hàng ở <button type="button" className="link-btn" onClick={onGoToAddresses}>Sổ địa chỉ</button>.
        </p>
      </aside>

      {picking && (
        <Modal title={`Chọn ${catLabel(picking)}`} onClose={() => setPicking(null)}>
          <div className="pick-list">
            {PRODUCTS.filter((p) => p.cat === picking).map((p) => {
              const warn = warnFor(p);
              const isOut = p.stock <= 0;
              return (
                <button
                  type="button"
                  key={p.id}
                  className={`pick-item ${sel[picking]?.id === p.id ? 'sel' : ''}`}
                  style={isOut ? { opacity: 0.5 } : undefined}
                  onClick={() => !isOut && choose(p)}
                >
                  <span className="ico">{p.icon}</span>
                  <span>
                    <b>{p.name} {isOut && <span className="badge" style={{ background: '#6b7280', color: '#fff' }}>Hết hàng</span>}</b>
                    <small>{p.specs.join(' · ')}</small>
                    {warn && <small className="warn-text">⚠️ {warn}</small>}
                  </span>
                  <span className="slot-price">{formatVND(p.price)}</span>
                </button>
              );
            })}
          </div>
        </Modal>
      )}
    </div>
  );
}