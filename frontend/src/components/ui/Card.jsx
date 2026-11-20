export function Card({ as: Element = 'section', className = '', children, ...props }) {
  return <Element {...props} className={`csm-card ${className}`}>{children}</Element>
}
