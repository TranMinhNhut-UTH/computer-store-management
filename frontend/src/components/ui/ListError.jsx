import { Button } from './Button.jsx'

export function ListError({ status, message, onRetry }) {
  return <div className="csm-list-error" role="alert">
    <p>{message}</p>
    {status !== 401 && status !== 403 && <Button variant="secondary" onClick={onRetry}>Thử lại</Button>}
  </div>
}
