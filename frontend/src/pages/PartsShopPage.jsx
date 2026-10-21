import React, { useState } from 'react';
import { CATEGORIES, HARDWARE_ITEMS, formatVND } from '../data/products';

export default function PartsShopPage({ onAddToCart, onBuyNow, onGoToBuildPc }) {
  const [cat, setCat] = useState('ALL');
  const [keyword, setKeyword] = useState('');

  const filtered = HARDWARE_ITEMS.filter((item) => {
    const matchCat = cat === 'ALL' || item.category === cat;
    const matchKw =
      !keyword.trim() ||
      item.name.toLowerCase().includes(keyword.toLowerCase()) ||
      item.specs.toLowerCase().includes(keyword.toLowerCase());
    return matchCat && matchKw;
  });

  return (
    <div>
      {/* Banner mua lẻ linh kiện */}
      <div className="hero-banner">
        <div>
          <span className="tech-pill">CHÍNH HÃNG 100% · BẢO HÀNH 36 THÁNG</span>
          <h2>Siêu Thị Linh Kiện Máy Tính Rời</h2>
          <p>
            Mua lẻ từng linh kiện nâng cấp máy tính với giá tốt nhất.
            Nếu bạn muốn tiệm lắp ráp trọn bộ nguyên thùng, hãy chuyển sang trang <b>Lắp Ráp PC </b>.
          </p>
        </div>
        <div className="hero-stats">
          <button type="button" className="primary" onClick={onGoToBuildPc}>
            🛠️ Chuyển sang Lắp Ráp PC (Trả trước 1/4) →
          </button>
        </div>
      </div>

      {/* Thanh tìm kiếm & Bộ lọc danh mục */}
      <div className="shop-toolbar">
        <input
          type="search"
          placeholder="🔍 Tìm tên linh kiện (VD: RTX 5080, Ryzen 7, DDR5, Samsung...)"
          value={keyword}
          onChange={(e) => setKeyword(e.target.value)}
        />
      </div>

      <div className="tabs" style={{ marginBottom: '22px' }}>
        {CATEGORIES.map(([id, label]) => (
          <button
            key={id}
            type="button"
            className={cat === id ? 'sel' : ''}
            onClick={() => setCat(id)}
          >
            {label}
          </button>
        ))}
      </div>

      {/* Lưới sản phẩm */}
      <div className="product-grid-full">
        {filtered.map((item) => (
          <article key={item.id} className="product-card">
            <div>
              <div className="product-top">
                <span className="cat-tag">{item.category}</span>
                <span className="badge">{item.badge}</span>
              </div>
              <h3>{item.name}</h3>
              <p className="specs-text">{item.specs}</p>
            </div>

            <div className="product-bottom-stack">
              <div className="bar" style={{ marginBottom: '10px' }}>
                <div className="price">{formatVND(item.price)}</div>
                <small className="stock-ok">● {item.stock}</small>
              </div>
              <div className="grid-2">
                <button
                  type="button"
                  className="add-pc-btn"
                  onClick={() => onAddToCart(item)}
                >
                  🛒 Thêm vào giỏ
                </button>
                <button
                  type="button"
                  className="primary"
                  onClick={() => onBuyNow(item)}
                >
                  Mua ngay
                </button>
              </div>
            </div>
          </article>
        ))}
      </div>
    </div>
  );
}