import React from 'react';

export function Field({ name, label, type = 'text', required, defaultValue, minLength, maxLength, placeholder }) {
  return (
    <label className="field-group">
      <span>
        {label}
        {required && <em> *</em>}
      </span>
      <input
        name={name}
        type={type}
        required={required}
        defaultValue={defaultValue}
        minLength={minLength}
        maxLength={maxLength}
        placeholder={placeholder}
      />
    </label>
  );
}

export function Submit({ children, disabled }) {
  return (
    <button className="primary" type="submit" disabled={disabled}>
      {children}
    </button>
  );
}

export function Form({ title, subtitle, children, onSubmit, busy }) {
  return (
    <form onSubmit={onSubmit}>
      {title && <h2>{title}</h2>}
      {subtitle && <p className="muted" style={{ marginTop: '-2px', marginBottom: '14px' }}>{subtitle}</p>}
      <div className="form">{children}</div>
      {busy && <small className="busy-indicator">⚡ Hệ thống đang xử lý...</small>}
    </form>
  );
}

export function Table({ rows, cols, labels = {} }) {
  return (
    <div className="tablewrap">
      <table>
        <thead>
          <tr>
            {cols.map((c) => (
              <th key={c}>{labels[c] || c}</th>
            ))}
          </tr>
        </thead>
        <tbody>
          {rows.map((r, i) => (
            <tr key={r.id || i}>
              {cols.map((c) => (
                <td key={c}>{String(r[c] ?? '—')}</td>
              ))}
            </tr>
          ))}
        </tbody>
      </table>
      {!rows.length && (
        <div className="empty-state">
          <p className="muted">Chưa có dữ liệu hiển thị hoặc tài khoản của bạn không có quyền Quản lý.</p>
        </div>
      )}
    </div>
  );
}