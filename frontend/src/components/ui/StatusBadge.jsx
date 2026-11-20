export function StatusBadge({ variant = 'neutral', children }) {
  return <span className={`csm-badge csm-badge--${variant}`}>{children}</span>
}
