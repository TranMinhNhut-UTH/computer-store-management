export function Button({ variant = 'primary', loading = false, disabled = false, children, className = '', type = 'button', ...props }) {
  return <button {...props} type={type} className={`csm-button csm-button--${variant} ${className}`} disabled={disabled || loading} aria-busy={loading || undefined}>{loading && <span className="spinner" aria-hidden="true" />}{children}</button>
}
