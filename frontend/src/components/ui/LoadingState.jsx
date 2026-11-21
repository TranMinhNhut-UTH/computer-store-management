export function LoadingState({ label = 'Đang tải…' }) {
  return <div className="csm-loading" role="status"><span className="spinner" aria-hidden="true" />{label}</div>
}
