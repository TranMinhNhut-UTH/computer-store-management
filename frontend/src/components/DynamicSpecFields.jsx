import React from 'react';
import { CATEGORY_ATTR_SCHEMA, formatProductAttrs } from '../data/products'; // Đổi lại đường dẫn file chứa PRODUCTS của bạn nếu khác

/**
 * 1. Form nhập thuộc tính kỹ thuật tự động biến đổi theo loại linh kiện (Dùng cho Quản lý thêm/sửa SP)
 */
export function DynamicSpecForm({ category, attrs = {}, onChange }) {
  const schema = CATEGORY_ATTR_SCHEMA[category] || [];

  if (schema.length === 0) {
    return <p className="muted">Danh mục này chưa yêu cầu thuộc tính kỹ thuật đặc thù.</p>;
  }

  const handleFieldChange = (key, value) => {
    onChange({ ...attrs, [key]: value });
  };

  const handleMultiSelectToggle = (key, option) => {
    const current = Array.isArray(attrs[key]) ? attrs[key] : [];
    const next = current.includes(option)
      ? current.filter((item) => item !== option)
      : [...current, option];
    handleFieldChange(key, next);
  };

  return (
    <div style={{ background: '#f8fafc', border: '1px solid var(--line)', borderRadius: '10px', padding: '14px', marginTop: '10px' }}>
      <div style={{ fontWeight: 700, fontSize: '14px', marginBottom: '12px', color: 'var(--red)' }}>
        ⚙️ Thông số kỹ thuật đặc thù ({category.toUpperCase()}) – Dùng để kiểm tra tương thích Build PC
      </div>

      <div className="grid-2">
        {schema.map((field) => {
          const val = attrs[field.key] ?? field.defaultValue ?? '';

          // Kiểu Đúng / Sai (Checkbox)
          if (field.type === 'boolean') {
            return (
              <label key={field.key} className="check-row" style={{ alignSelf: 'center', marginTop: '12px' }}>
                <input
                  type="checkbox"
                  checked={Boolean(attrs[field.key])}
                  onChange={(e) => handleFieldChange(field.key, e.target.checked)}
                />
                <span>{field.label}</span>
              </label>
            );
          }

          // Kiểu Chọn nhiều giá trị (VD: Case hỗ trợ cả mATX và ATX)
          if (field.type === 'multiselect') {
            const selectedList = Array.isArray(attrs[field.key]) ? attrs[field.key] : [];
            return (
              <div key={field.key} className="field">
                <span>{field.label}</span>
                <div style={{ display: 'flex', gap: '6px', flexWrap: 'wrap', marginTop: '4px' }}>
                  {field.options.map((opt) => {
                    const active = selectedList.includes(opt);
                    return (
                      <button
                        key={opt}
                        type="button"
                        className={`chip ${active ? 'on' : ''}`}
                        onClick={() => handleMultiSelectToggle(field.key, opt)}
                      >
                        {active ? '✓ ' : '+ '}{opt}
                      </button>
                    );
                  })}
                </div>
              </div>
            );
          }

          // Kiểu Dropdown chọn 1 (Select)
          if (field.type === 'select') {
            return (
              <label key={field.key} className="field">
                <span>{field.label}</span>
                <select
                  value={val}
                  onChange={(e) => handleFieldChange(field.key, e.target.value)}
                >
                  <option value="">-- Chọn {field.label} --</option>
                  {field.options.map((opt) => (
                    <option key={opt} value={opt}>{opt}</option>
                  ))}
                </select>
              </label>
            );
          }

          // Kiểu Số hoặc Text (Kèm đơn vị đo như W, mm, MHz)
          return (
            <label key={field.key} className="field">
              <span>
                {field.label} {field.unit ? `(${field.unit})` : ''}
              </span>
              <input
                type={field.type === 'number' ? 'number' : 'text'}
                placeholder={field.placeholder || ''}
                value={val}
                onChange={(e) =>
                  handleFieldChange(
                    field.key,
                    field.type === 'number' ? Number(e.target.value) : e.target.value
                  )
                }
              />
            </label>
          );
        })}
      </div>
    </div>
  );
}

/**
 * 2. Bảng hiển thị chi tiết thuộc tính kỹ thuật (Dùng ở Modal Chi tiết sản phẩm tại PartsShopPage)
 */
export function DynamicSpecTable({ product }) {
  if (!product) return null;
  const rows = formatProductAttrs(product.cat, product.attrs);

  return (
    <table className="spec-table">
      <tbody>
        <tr>
          <td>Thương hiệu</td>
          <td><b>{product.brand}</b></td>
        </tr>
        {product.specs?.length > 0 && (
          <tr>
            <td>Tóm tắt hiệu năng</td>
            <td>{product.specs.join(' · ')}</td>
          </tr>
        )}
        {rows.map((r) => (
          <tr key={r.key}>
            <td>{r.label}</td>
            <td><b style={{ color: 'var(--ink)' }}>{r.value}</b></td>
          </tr>
        ))}
      </tbody>
    </table>
  );
}