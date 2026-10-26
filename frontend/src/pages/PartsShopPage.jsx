import React, { useEffect, useMemo, useState } from 'react';
import { Empty, Modal } from '../components/ui';
import { CATEGORIES, PRODUCTS, catLabel, discountPercent } from '../data/products';
import { formatVND } from '../utils';

const SLIDES = [
  { cls: 's-red', art: '🖥️', title: 'Build PC Gaming – giảm đến 15%', text: 'Chọn linh kiện, hệ thống tự kiểm tra tương thích và tính giá trọn bộ.', cta: 'Build PC ngay', go: 'build' },
  { cls: 's-ink', art: '💾', title: 'RAM DDR5 xả kho, từ 1.490.000đ', text: 'Kingston, Corsair, G.Skill chính hãng, bảo hành theo serial.', cta: 'Xem RAM', go: 'ram' },
  { cls: 's-blue', art: '♻️', title: 'Thu cũ đổi mới, trợ giá đến 2 triệu', text: 'Mang VGA, CPU cũ đến cửa hàng để được định giá nâng cấp.', cta: 'Xem VGA', go: 'vga' },
];

// FR-20: Sắp xếp theo giá, độ phổ biến, mới nhất, đánh giá
const SORTS = {
  popular: ['Bán chạy nhất', (a, b) => b.sold - a.sold],
  rating: ['Đánh giá cao nhất', (a, b) => Number(b.rating) - Number(a.rating)],
  newest: ['Mới nhất (Mã sản phẩm)', (a, b) => b.id.localeCompare(a.id)],
  priceAsc: ['Giá thấp đến cao', (a, b) => a.price - b.price],
  priceDesc: ['Giá cao đến thấp', (a, b) => b.price - a.price],
  discount: ['Giảm giá nhiều nhất', (a, b) => discountPercent(b) - discountPercent(a)],
};

const PRICE_RANGES = [
  ['all', 'Mọi mức giá', 0, Infinity],
  ['u2', 'Dưới 2 triệu', 0, 2e6],
  ['2-5', '2 – 5 triệu', 2e6, 5e6],
  ['5-10', '5 – 10 triệu', 5e6, 10e6],
  ['o10', 'Trên 10 triệu', 10e6, Infinity],
];

const ATTR_LABELS = {
  socket: 'Chuẩn Socket',
  ramType: 'Chuẩn RAM',
  form: 'Kích thước mạch (Form)',
  tdp: 'Công suất tỏa nhiệt / tiêu thụ (TDP)',
  igpu: 'Đồ họa tích hợp (iGPU)',
  m2: 'Khe SSD M.2 NVMe',
  nvme: 'Giao tiếp NVMe',
  watt: 'Công suất định danh',
  length: 'Chiều dài Card (mm)',
  height: 'Chiều cao tản (mm)',
  maxGpu: 'Hỗ trợ VGA tối đa (mm)',
  maxCooler: 'Hỗ trợ tản CPU tối đa (mm)',
  forms: 'Mainboard hỗ trợ',
  sockets: 'Socket hỗ trợ',
  warrantyMonth: 'Thời hạn bảo hành (tháng)',
  preInstalledOS: 'Hệ điều hành cài sẵn',
};

const ROOT_GROUPS = [
  { id: 'root-pc', name: 'PC Nguyên Bộ', keys: ['prebuildpc'] },
  { id: 'root-parts', name: 'Linh Kiện Máy Tính', keys: ['cpu', 'mainboard', 'ram', 'vga', 'ssd', 'psu', 'case', 'cooler'] },
  { id: 'root-gear', name: 'Màn Hình & Ngoại Vi', keys: ['monitor'] },
];

const DEMO_SOLD_OUT = new Set(['c4', 'g2', 'r2', 'm3']);
const stockOf = (p) => (DEMO_SOLD_OUT.has(p.id) ? 0 : p.stock);
const isSoldOut = (p) => stockOf(p) <= 0;
const COMPAT_HINT = {
  cpu: 'cùng chuẩn socket',
  mainboard: 'cùng socket, chuẩn RAM và có khe M.2',
  ram: 'cùng chuẩn RAM (DDR4/DDR5)',
  vga: 'kích thước và công suất card đồ họa',
  ssd: 'cùng chuẩn giao tiếp (NVMe/SATA)',
  psu: 'công suất không thấp hơn sản phẩm gốc',
  case: 'hỗ trợ đủ các cỡ mainboard của sản phẩm gốc',
  cooler: 'hỗ trợ đủ các socket của sản phẩm gốc',
};
const sameAttr = (a, b) => a === undefined || b === undefined || a === b;
const hasAll = (have, need) => !Array.isArray(need) || !Array.isArray(have) || need.every((x) => have.includes(x));
function checkReplacement(orig, cand) {
  const a = orig.attrs || {};
  const c = cand.attrs || {};
  const notes = [];
  let ok = true;

  switch (orig.cat) {
    case 'cpu':
      ok = sameAttr(a.socket, c.socket);
      if (ok && c.tdp > a.tdp) notes.push(`TDP cao hơn ${c.tdp - a.tdp}W, nên kiểm tra nguồn và tản nhiệt`);
      if (ok && a.igpu === true && c.igpu === false) notes.push('Không có đồ họa tích hợp, cần thêm card đồ họa rời');
      break;
    case 'mainboard':
      ok = sameAttr(a.socket, c.socket) && sameAttr(a.ramType, c.ramType) && !(a.m2 === true && c.m2 === false);
      if (ok && !sameAttr(a.form, c.form)) notes.push(`Kích thước ${c.form} (gốc ${a.form}), kiểm tra case có hỗ trợ`);
      break;
    case 'ram':
      ok = sameAttr(a.ramType, c.ramType);
      break;
    case 'vga':
      if (c.tdp > a.tdp) notes.push(`Tiêu thụ điện cao hơn ${c.tdp - a.tdp}W, kiểm tra công suất nguồn`);
      if (c.length > a.length) notes.push(`Dài hơn ${c.length - a.length}mm, kiểm tra case có đủ chỗ`);
      break;
    case 'ssd':
      ok = !!c.nvme === !!a.nvme;
      break;
    case 'psu':
      ok = c.watt >= a.watt;
      break;
    case 'case':
      ok = hasAll(c.forms, a.forms);
      if (ok && c.maxGpu < a.maxGpu) notes.push(`Chỉ nhận card đồ họa dài tối đa ${c.maxGpu}mm`);
      if (ok && c.maxCooler < a.maxCooler) notes.push(`Chỉ nhận tản CPU cao tối đa ${c.maxCooler}mm`);
      break;
    case 'cooler':
      ok = hasAll(c.sockets, a.sockets);
      if (ok && c.height > a.height) notes.push(`Cao hơn ${c.height - a.height}mm, kiểm tra case có đủ chỗ`);
      break;
    default:
      break;
  }
  return { ok, notes };
}

function getReplacements(product, limit = 3) {
  return PRODUCTS.filter((x) => x.cat === product.cat && x.id !== product.id && !isSoldOut(x))
    .map((x) => ({ product: x, ...checkReplacement(product, x) }))
    .filter((x) => x.ok)
    .sort(
      (x, y) =>
        x.notes.length - y.notes.length ||
        Math.abs(x.product.price - product.price) - Math.abs(y.product.price - product.price) ||
        y.product.sold - x.product.sold
    )
    .slice(0, limit);
}

const priceDiffText = (diff) =>
  diff === 0 ? 'Cùng mức giá' : diff < 0 ? `Rẻ hơn ${formatVND(-diff)}` : `Đắt hơn ${formatVND(diff)}`;
function ProductCard({ p, onOpen, onAdd, onBuy, isFav, onToggleFav, isCompared, onToggleCompare }) {
  const off = discountPercent(p);
  const soldOut = isSoldOut(p);
  return (
    <article className="product-card" style={{ position: 'relative' }}>
      <button
        type="button"
        onClick={() => onToggleFav(p.id)}
        style={{
          position: 'absolute', top: 12, right: 12, zIndex: 5,
          background: '#fff', border: '1px solid var(--line)', borderRadius: '50%',
          width: 32, height: 32, display: 'grid', placeItems: 'center', fontSize: 16,
          color: isFav ? '#ef4444' : '#9ca3af', cursor: 'pointer', boxShadow: '0 2px 6px rgba(0,0,0,0.08)'
        }}
        title={isFav ? 'Bỏ thích' : 'Thêm vào yêu thích'}
      >
        {isFav ? '❤️' : '🤍'}
      </button>

      {soldOut
        ? <span className="pc-badge" style={{ background: '#6b7280' }}>Hết hàng</span>
        : off > 0 && <span className="pc-badge">Giảm {off}%</span>}

      <button
        type="button"
        className="pc-thumb"
        style={soldOut ? { opacity: 0.5, filter: 'grayscale(1)' } : undefined}
        onClick={() => onOpen(p)}
        aria-label={`Xem ${p.name}`}
      >
        {p.icon}
      </button>
      <span className="pc-brand">{p.brand}</span>
      <button type="button" className="pc-name" onClick={() => onOpen(p)}>{p.name}</button>
      <div className="pc-specs">{p.specs.slice(0, 3).map((s) => <span key={s}>{s}</span>)}</div>

      <div style={{ margin: '6px 0 10px', fontSize: '13px', display: 'flex', alignItems: 'center', gap: '6px' }}>
        <input
          type="checkbox"
          id={`cmp-${p.id}`}
          checked={isCompared}
          onChange={() => onToggleCompare(p)}
          style={{ cursor: 'pointer' }}
        />
        <label htmlFor={`cmp-${p.id}`} style={{ cursor: 'pointer', color: 'var(--muted)' }}>
          So sánh sản phẩm
        </label>
      </div>

      <div className="pc-price">
        {formatVND(p.price)}
        {p.oldPrice > p.price && <span className="pc-old">{formatVND(p.oldPrice)}</span>}
      </div>
      <div className="pc-meta">
        <span><span className="star">★</span> {p.rating}</span>
        <span>Đã bán {p.sold}</span>
      </div>
      <div className="pc-actions">
        {soldOut ? (
          <button
            type="button"
            className="btn btn-outline btn-sm"
            style={{ gridColumn: '1 / -1' }}
            onClick={() => {
              const replacements = getReplacements(p);
              if (replacements.length > 0) {
                onOpen(replacements[0].product);
              } else {
                alert('Rất tiếc, hiện không tìm thấy sản phẩm thay thế tương thích. Vui lòng thử lại sau.');
              }
            }}
          >
            Xem sản phẩm thay thế
          </button>
        ) : (
          <>
            <button type="button" className="btn btn-primary btn-sm" onClick={() => onBuy(p)}>Mua ngay</button>
            <button type="button" className="btn btn-outline btn-sm" onClick={() => onAdd(p)} aria-label="Thêm vào giỏ" title="Thêm vào giỏ">🛒</button>
          </>
        )}
      </div>
    </article>
  );
}

// ---------------------------------------------------------------------------
// MODAL SO SÁNH SẢN PHẨM (FR-21: Tối đa 3-4 sản phẩm)
// ---------------------------------------------------------------------------
function CompareModal({ comparedList, onClose, onRemoveCompare, onAdd, onBuy }) {
  return (
    <Modal title="⚖️ Bảng So Sánh Sản Phẩm Chi Tiết" onClose={onClose} wide>
      {comparedList.length === 0 ? (
        <div className="empty">
          <strong>Chưa có sản phẩm nào trong bảng so sánh</strong>
          <p>Hãy tích chọn "So sánh sản phẩm" ở các thẻ sản phẩm bên ngoài (tối đa 4 sản phẩm).</p>
        </div>
      ) : (
        <div className="table-wrap">
          <table className="table" style={{ textAlign: 'center' }}>
            <thead>
              <tr>
                <th style={{ textAlign: 'left', width: '180px' }}>Tiêu chí</th>
                {comparedList.map((p) => (
                  <th key={p.id} style={{ minWidth: '220px', position: 'relative' }}>
                    <button
                      type="button"
                      className="icon-btn"
                      style={{ position: 'absolute', top: 4, right: 4, color: '#dc2626' }}
                      onClick={() => onRemoveCompare(p.id)}
                      title="Xóa khỏi bảng so sánh"
                    >
                      ✕
                    </button>
                    <div style={{ fontSize: '32px', marginBottom: '4px' }}>{p.icon}</div>
                    <div style={{ fontSize: '14px', fontWeight: 700, color: 'var(--ink)' }}>{p.name}</div>
                  </th>
                ))}
              </tr>
            </thead>
            <tbody>
              <tr>
                <td style={{ textAlign: 'left', fontWeight: 600 }}>Giá bán</td>
                {comparedList.map((p) => (
                  <td key={p.id}>
                    <b style={{ color: 'var(--red)', fontSize: '16px' }}>{formatVND(p.price)}</b>
                  </td>
                ))}
              </tr>
              <tr>
                <td style={{ textAlign: 'left', fontWeight: 600 }}>Thương hiệu</td>
                {comparedList.map((p) => (<td key={p.id}>{p.brand}</td>))}
              </tr>
              <tr>
                <td style={{ textAlign: 'left', fontWeight: 600 }}>Danh mục</td>
                {comparedList.map((p) => (<td key={p.id}>{catLabel(p.cat)}</td>))}
              </tr>
              <tr>
                <td style={{ textAlign: 'left', fontWeight: 600 }}>Đánh giá</td>
                {comparedList.map((p) => (<td key={p.id}>⭐ {p.rating} ({p.sold} đã bán)</td>))}
              </tr>
              <tr>
                <td style={{ textAlign: 'left', fontWeight: 600 }}>Thông số kỹ thuật</td>
                {comparedList.map((p) => (
                  <td key={p.id} style={{ textAlign: 'left' }}>
                    <ul style={{ paddingLeft: '16px', margin: 0, fontSize: '13px' }}>
                      {p.specs.map((s) => <li key={s}>{s}</li>)}
                      {Object.entries(p.attrs || {}).filter(([k]) => k !== 'isPrebuilt').map(([k, v]) => (
                        <li key={k}><b>{ATTR_LABELS[k] || k}:</b> {String(v)}</li>
                      ))}
                    </ul>
                  </td>
                ))}
              </tr>
              <tr>
                  <td style={{ textAlign: 'left', fontWeight: 600 }}>Thao tác nhanh</td>
                    {comparedList.map((p) => {
                    const soldOut = isSoldOut(p);
                        return (
                            <td key={p.id}>
                                <div style={{ display: 'flex', gap: 6, justifyContent: 'center' }}>
                                    {soldOut ? (
                                        <span className="badge" style={{ background: '#fef2f2', color: '#dc2626', border: '1px solid #fecaca' }}>
                                          🚫 Tạm hết hàng
                                        </span>
                                      ) : (
                                          <>
                                          <button type="button" className="btn btn-primary btn-sm" onClick={() => { onBuy(p); onClose(); }}>Mua ngay</button>
                                          <button type="button" className="btn btn-outline btn-sm" onClick={() => onAdd(p)}>🛒 Giỏ</button>
                                        </>
                                      )}
                                    </div>
                                  </td>
                                );
                              })}
                            </tr>
            </tbody>
          </table>
        </div>
      )}
    </Modal>
  );
}
function DetailModal({ product, onClose, onOpen, onAdd, onBuy }) {
  const off = discountPercent(product);
  const soldOut = isSoldOut(product);
  const replacements = soldOut ? getReplacements(product) : [];

  const handleReplacementClick = () => {
    if (replacements.length > 0) {
      onOpen(replacements[0].product);
    } else {
      alert('Rất tiếc, hiện không tìm thấy sản phẩm thay thế tương thích. Vui lòng thử lại sau.');
    }
  };

  return (
    <Modal title={product.name} onClose={onClose} wide>
      <div className="detail">
        <div className="detail-art">{product.icon}</div>
        <div>
          <span className="badge b-gray">{catLabel(product.cat)}</span>
          <h2>{product.name}</h2>
          <p className="muted">Thương hiệu {product.brand} · <span style={{ color: '#f59e0b' }}>★</span> {product.rating} · Đã bán {product.sold}</p>
          {soldOut && (
            <div role="alert" style={{ margin: '10px 0', padding: '10px 12px', borderRadius: 8, background: '#fef2f2', color: '#b91c1c', border: '1px solid #fecaca' }}>
              <strong>⚠️ Sản phẩm hiện đã hết hàng.</strong>
              <div style={{ fontSize: 13, marginTop: 3 }}>Bạn không thể mua ngay hoặc thêm sản phẩm này vào giỏ hàng.</div>
            </div>
          )}
          <div style={{ margin: '12px 0' }}>
            <span className="price">{formatVND(product.price)}</span>
            {off > 0 && (
              <>
                <span className="pc-old" style={{ fontSize: 16 }}>{formatVND(product.oldPrice)}</span>
                <span className="badge b-red" style={{ marginLeft: 8 }}>-{off}%</span>
              </>
            )}
          </div>
          <table className="spec-table">
            <tbody>
              {product.specs.map((s, i) => (<tr key={s}><td>Đặc điểm {i + 1}</td><td>{s}</td></tr>))}
              <tr><td>Bảo hành</td><td>36 tháng chính hãng</td></tr>
            </tbody>
          </table>
          <div style={{ display: 'flex', gap: 10, flexWrap: 'wrap', marginTop: 12 }}>
            {soldOut ? (
              <button type="button" className="btn btn-outline btn-lg" onClick={handleReplacementClick}>
                🔎 Gợi ý sản phẩm thay thế
              </button>
            ) : (
              <>
                <button type="button" className="btn btn-primary btn-lg" onClick={() => { if (!isSoldOut(product)) { onBuy(product); onClose(); } }}>
                  Mua ngay
                </button>
                <button type="button" className="btn btn-outline btn-lg" onClick={() => { if (!isSoldOut(product)) { onAdd(product); onClose(); } }}>
                  Thêm vào giỏ
                </button>
              </>
            )}
          </div>
          {soldOut && replacements.length > 0 && (
            <div style={{ marginTop: 14, padding: 12, border: '1px solid var(--line)', borderRadius: 10 }}>
              <strong>✨ Sản phẩm thay thế tương thích</strong>
              <div style={{ display: 'grid', gap: 8, marginTop: 8 }}>
                {replacements.map(({ product: replacement, notes }) => (
                  <button
                    type="button"
                    key={replacement.id}
                    className="btn btn-ghost"
                    style={{ justifyContent: 'space-between', textAlign: 'left', gap: 12 }}
                    onClick={() => onOpen(replacement)}
                  >
                    <span>{replacement.icon} {replacement.name}{notes.length > 0 ? ` · ${notes.join('; ')}` : ''}</span>
                    <strong style={{ whiteSpace: 'nowrap' }}>{formatVND(replacement.price)}</strong>
                  </button>
                ))}
              </div>
            </div>
          )}
        </div>
      </div>
    </Modal>
  );
}
export default function PartsShopPage({ onAddToCart, onBuyNow, onGoToBuildPc }) {
  const [cat, setCat] = useState('all');
  const [brand, setBrand] = useState('all');
  const [range, setRange] = useState('all');
  const [sort, setSort] = useState('popular');
  const [q, setQ] = useState('');
  const [slide, setSlide] = useState(0);
  const [detail, setDetail] = useState(null);
  const [attrFilter, setAttrFilter] = useState('');

  // FR-18: State gợi ý từ khóa khi gõ tìm kiếm
  const [showSuggestions, setShowSuggestions] = useState(false);

  // FR-21: Danh sách yêu thích (Lưu vào localStorage)
  const [favorites, setFavorites] = useState(() => {
    try {
      return JSON.parse(localStorage.getItem('cs_favorites') || '[]');
    } catch {
      return [];
    }
  });
  const [showOnlyFav, setShowOnlyFav] = useState(false);

  // FR-21: Danh sách so sánh (Tối đa 4 sản phẩm)
  const [comparedIds, setComparedIds] = useState([]);
  const [showCompareModal, setShowCompareModal] = useState(false);

  useEffect(() => {
    localStorage.setItem('cs_favorites', JSON.stringify(favorites));
  }, [favorites]);

  const toggleFavorite = (productId) => {
    setFavorites((prev) =>
      prev.includes(productId) ? prev.filter((id) => id !== productId) : [...prev, productId]
    );
  };

  const toggleCompare = (product) => {
    setComparedIds((prev) => {
      if (prev.includes(product.id)) {
        return prev.filter((id) => id !== product.id);
      }
      if (prev.length >= 4) {
        alert('Bạn chỉ có thể so sánh tối đa 4 sản phẩm cùng lúc!');
        return prev;
      }
      return [...prev, product.id];
    });
  };

  // FR-18: Gợi ý từ khóa tự động khi gõ
  const suggestions = useMemo(() => {
    const kw = q.trim().toLowerCase();
    if (!kw || kw.length < 2) return [];
    return PRODUCTS.filter(
      (p) =>
        p.name.toLowerCase().includes(kw) ||
        p.brand.toLowerCase().includes(kw) ||
        p.id.toLowerCase().includes(kw) ||
        p.specs.some((s) => s.toLowerCase().includes(kw))
    ).slice(0, 6);
  }, [q]);

  const primaryAttrKey =
    cat === 'cpu' || cat === 'mainboard'
      ? 'socket'
      : cat === 'ram'
      ? 'ramType'
      : cat === 'psu'
      ? 'watt'
      : cat === 'case'
      ? 'form'
      : null;

  const primaryAttrOptions = useMemo(() => {
    if (!primaryAttrKey) return [];
    const rawVals = PRODUCTS.filter((p) => p.cat === cat).flatMap((p) => {
      if (cat === 'case') return p.attrs?.forms || [];
      return p.attrs?.[primaryAttrKey] ? [String(p.attrs[primaryAttrKey])] : [];
    });
    return [...new Set(rawVals)];
  }, [cat, primaryAttrKey]);

  useEffect(() => {
    const t = setInterval(() => setSlide((s) => (s + 1) % SLIDES.length), 5500);
    return () => clearInterval(t);
  }, []);

  const brands = useMemo(
    () => ['all', ...new Set(PRODUCTS.filter((p) => cat === 'all' || p.cat === cat).map((p) => p.brand))],
    [cat]
  );

  // FR-18, FR-19, FR-20: Lọc và sắp xếp toàn diện
  const list = useMemo(() => {
    const [, , min, max] = PRICE_RANGES.find((r) => r[0] === range);
    const kw = q.trim().toLowerCase();
    return PRODUCTS.filter((p) => {
      const matchCat = cat === 'all' || p.cat === cat;
      const matchBrand = brand === 'all' || p.brand === brand;
      const matchPrice = p.price >= min && p.price < max;

      // FR-18: Tìm kiếm theo tên, mã (id), hoặc thương hiệu
      const matchKw =
        !kw ||
        p.name.toLowerCase().includes(kw) ||
        p.brand.toLowerCase().includes(kw) ||
        p.id.toLowerCase().includes(kw);

      // FR-21: Lọc danh sách yêu thích
      const matchFav = !showOnlyFav || favorites.includes(p.id);

      let matchAttr = true;
      if (attrFilter && primaryAttrKey) {
        if (cat === 'case') {
          matchAttr = Array.isArray(p.attrs?.forms) && p.attrs.forms.includes(attrFilter);
        } else {
          matchAttr = String(p.attrs?.[primaryAttrKey]) === String(attrFilter);
        }
      }

      return matchCat && matchBrand && matchPrice && matchKw && matchFav && matchAttr;
    })
      .sort(SORTS[sort][1])
      .sort((a, b) => Number(isSoldOut(a)) - Number(isSoldOut(b)));
  }, [cat, brand, range, sort, q, attrFilter, primaryAttrKey, showOnlyFav, favorites]);

  const pickCat = (key) => {
    setCat(key);
    setBrand('all');
    setAttrFilter('');
  };

  const slideAction = (s) => (s.go === 'build' ? onGoToBuildPc() : pickCat(s.go));
  const currentRootGroup = ROOT_GROUPS.find((g) => g.keys.includes(cat));
  const comparedList = PRODUCTS.filter((p) => comparedIds.includes(p.id));

  return (
    <>
      <section className="hero-grid">
        <nav className="cat-menu" aria-label="Danh mục sản phẩm">
          <button type="button" className={cat === 'all' ? 'active' : ''} onClick={() => pickCat('all')}>
            <span className="ico">🏷️</span> Tất cả sản phẩm <span className="count">{PRODUCTS.length}</span>
          </button>

          {ROOT_GROUPS.map((group) => (
            <div key={group.id} style={{ marginTop: '6px' }}>
              <div style={{ fontSize: '11px', fontWeight: 700, textTransform: 'uppercase', color: 'var(--muted)', padding: '4px 10px' }}>
                {group.name}
              </div>
              {CATEGORIES.filter((c) => group.keys.includes(c.key)).map((c) => (
                <button type="button" key={c.key} className={cat === c.key ? 'active' : ''} onClick={() => pickCat(c.key)} style={{ width: '100%' }}>
                  <span className="ico">{c.icon}</span> {c.label}
                  <span className="count">{PRODUCTS.filter((p) => p.cat === c.key).length}</span>
                </button>
              ))}
            </div>
          ))}
        </nav>

        <div className="slider" aria-roledescription="carousel">
          {SLIDES.map((s, i) => (
            <div key={s.title} className={`slide ${s.cls} ${i === slide ? 'on' : ''}`} aria-hidden={i !== slide}>
              <h2>{s.title}</h2>
              <p>{s.text}</p>
              <button type="button" className="btn" tabIndex={i === slide ? 0 : -1} onClick={() => slideAction(s)}>{s.cta}</button>
              <span className="art" aria-hidden="true">{s.art}</span>
            </div>
          ))}
          <div className="dots">
            {SLIDES.map((s, i) => (
              <button type="button" key={s.title} className={i === slide ? 'on' : ''} onClick={() => setSlide(i)} aria-label={`Slide ${i + 1}`} />
            ))}
          </div>
        </div>

        <div className="promo-stack">
          <button type="button" className="promo p1" onClick={onGoToBuildPc}>
            <strong>Tự build PC</strong>
            <span>Kiểm tra tương thích tự động</span>
          </button>
          <button type="button" className="promo p2" onClick={() => pickCat('monitor')}>
            <strong>Màn hình 180Hz</strong>
            <span>Từ 2.690.000đ</span>
          </button>
        </div>
      </section>

      <section className="perk-bar">
        <div><span className="ico">🚚</span><span><b>Giao nhanh 2 giờ</b>nội thành TP.HCM</span></div>
        <div><span className="ico">🛡️</span><span><b>Bảo hành chính hãng</b>tra cứu theo serial</span></div>
        <div><span className="ico">🔧</span><span><b>Lắp máy miễn phí</b>khi mua trọn bộ</span></div>
        <div><span className="ico">♻️</span><span><b>Thu cũ đổi mới</b>trợ giá nâng cấp</span></div>
      </section>

      {/* Thanh công cụ nhanh Yêu thích & So sánh (FR-21) */}
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '14px', background: '#fff', padding: '10px 16px', borderRadius: '10px', border: '1px solid var(--line)' }}>
        <div style={{ display: 'flex', gap: '10px', alignItems: 'center' }}>
          <button
            type="button"
            className={`btn btn-sm ${showOnlyFav ? 'btn-primary' : 'btn-outline'}`}
            onClick={() => setShowOnlyFav(!showOnlyFav)}
          >
            ❤️ Sản phẩm yêu thích ({favorites.length})
          </button>
        </div>

        {comparedIds.length > 0 && (
          <div>
            <button
              type="button"
              className="btn btn-primary btn-sm"
              onClick={() => setShowCompareModal(true)}
            >
              ⚖️ So sánh sản phẩm ({comparedIds.length}/4)
            </button>
          </div>
        )}
      </div>

      {cat !== 'all' && (
        <div style={{ marginBottom: '10px', fontSize: '14px', display: 'flex', alignItems: 'center', gap: '8px', flexWrap: 'wrap' }}>
          <span className="muted" style={{ cursor: 'pointer' }} onClick={() => pickCat('all')}>🏠 Trang chủ</span>
          {currentRootGroup && (
            <>
              <span>›</span>
              <span className="muted">{currentRootGroup.name}</span>
            </>
          )}
          <span>›</span>
          <b style={{ color: brand === 'all' ? 'var(--red)' : 'inherit', cursor: 'pointer' }} onClick={() => setBrand('all')}>
            {catLabel(cat)}
          </b>
          {brand !== 'all' && (
            <>
              <span>›</span>
              <span className="badge b-red">{brand}</span>
            </>
          )}
        </div>
      )}

      <div className="section-head">
        <h2>{cat === 'all' ? 'Sản phẩm nổi bật' : catLabel(cat)} <span className="muted" style={{ fontSize: 15, fontWeight: 500 }}>({list.length})</span></h2>
      </div>

      {/* FR-18, FR-19, FR-20: Thanh Tìm kiếm thông minh có Gợi ý, Lọc thông số & Sắp xếp */}
      <div className="filters" style={{ position: 'relative' }}>
        <div style={{ flex: 1, position: 'relative', minWidth: '300px' }}>
          <input
            type="search"
            placeholder="🔍 Tìm kiếm sản phẩm"
            value={q}
            onChange={(e) => { setQ(e.target.value); setShowSuggestions(true); }}
            onFocus={() => setShowSuggestions(true)}
            onBlur={() => setTimeout(() => setShowSuggestions(false), 200)}
            aria-label="Tìm kiếm sản phẩm"
          />
          {/* Hộp gợi ý từ khóa khi gõ (FR-18) */}
          {showSuggestions && suggestions.length > 0 && (
            <div style={{ position: 'absolute', top: '105%', left: 0, right: 0, background: '#fff', border: '1px solid var(--line)', borderRadius: '10px', boxShadow: '0 10px 25px rgba(0,0,0,0.1)', zIndex: 50, overflow: 'hidden' }}>
              <div style={{ padding: '6px 12px', fontSize: '12px', background: '#f7f8fa', color: 'var(--muted)', fontWeight: 600 }}>
                💡 Gợi ý tìm kiếm nhanh
              </div>
              {suggestions.map((item) => (
                <div
                  key={item.id}
                  style={{ padding: '10px 12px', display: 'flex', justifyContent: 'space-between', alignItems: 'center', cursor: 'pointer', borderTop: '1px solid #f1f2f4' }}
                  onMouseDown={() => { setQ(item.name); setDetail(item); setShowSuggestions(false); }}
                >
                  <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                    <span>{item.icon}</span>
                    <div>
                      <div style={{ fontSize: '13.5px', fontWeight: 600 }}>{item.name}</div>
                      <small className="muted">Mã: {item.id} · {catLabel(item.cat)}</small>
                    </div>
                  </div>
                  <b style={{ color: 'var(--red)', fontSize: '13.5px' }}>{formatVND(item.price)}</b>
                </div>
              ))}
            </div>
          )}
        </div>

        {/* FR-19: Lọc theo thông số kỹ thuật đặc thù */}
        {primaryAttrKey && primaryAttrOptions.length > 0 && (
          <select value={attrFilter} onChange={(e) => setAttrFilter(e.target.value)} aria-label="Lọc thông số kỹ thuật">
            <option value="">Tất cả {ATTR_LABELS[primaryAttrKey] || primaryAttrKey}</option>
            {primaryAttrOptions.map((opt) => (
              <option key={opt} value={opt}>
                {ATTR_LABELS[primaryAttrKey] || 'Chuẩn'}: {opt}{primaryAttrKey === 'watt' ? 'W' : ''}
              </option>
            ))}
          </select>
        )}

        {/* FR-19: Lọc theo khoảng giá */}
        <select value={range} onChange={(e) => setRange(e.target.value)} aria-label="Khoảng giá">
          {PRICE_RANGES.map(([k, label]) => <option key={k} value={k}>{label}</option>)}
        </select>

        {/* FR-20: Sắp xếp sản phẩm */}
        <select value={sort} onChange={(e) => setSort(e.target.value)} aria-label="Sắp xếp">
          {Object.entries(SORTS).map(([k, [label]]) => <option key={k} value={k}>{label}</option>)}
        </select>
      </div>

      {/* FR-19: Lọc theo thương hiệu */}
      <div className="filters">
        {brands.map((b) => (
          <button type="button" key={b} className={`chip ${brand === b ? 'on' : ''}`} onClick={() => setBrand(b)}>
            {b === 'all' ? 'Tất cả hãng' : b}
          </button>
        ))}
      </div>

      {list.length === 0 ? (
        <div className="card">
          <Empty icon="🔍" title="Không tìm thấy sản phẩm phù hợp" text="Thử đổi từ khóa tìm kiếm, thông số kỹ thuật, khoảng giá hoặc hãng khác.">
            <button className="btn btn-outline" onClick={() => { setQ(''); setRange('all'); pickCat('all'); setShowOnlyFav(false); }}>Xóa bộ lọc</button>
          </Empty>
        </div>
      ) : (
        <div className="product-grid">
          {list.map((p) => (
            <ProductCard
              key={p.id}
              p={p}
              onOpen={setDetail}
              onAdd={onAddToCart}
              onBuy={onBuyNow}
              isFav={favorites.includes(p.id)}
              onToggleFav={toggleFavorite}
              isCompared={comparedIds.includes(p.id)}
              onToggleCompare={toggleCompare}
            />
          ))}
        </div>
      )}

      {detail && <DetailModal product={detail} onClose={() => setDetail(null)} onOpen={setDetail} onAdd={onAddToCart} onBuy={onBuyNow} />}

      {/* Modal So sánh sản phẩm (FR-21) */}
      {showCompareModal && (
        <CompareModal
          comparedList={comparedList}
          onClose={() => setShowCompareModal(false)}
          onRemoveCompare={(id) => setComparedIds((prev) => prev.filter((x) => x !== id))}
          onAdd={onAddToCart}
          onBuy={onBuyNow}
        />
      )}
    </>
  );
}