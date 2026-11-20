export function PageHeader({ title, description, actions }) {
  return <header className="csm-page-header"><div><h1 id="page-title">{title}</h1>{description && <p className="csm-muted">{description}</p>}</div>{actions && <div className="csm-page-actions">{actions}</div>}</header>
}
