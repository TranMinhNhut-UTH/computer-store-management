export function EmptyState({ title = 'Chưa triển khai', description, action }) {
  return <div className="csm-empty"><span className="csm-empty-mark" aria-hidden="true">—</span><h2>{title}</h2>{description && <p className="csm-muted">{description}</p>}{action}</div>
}
