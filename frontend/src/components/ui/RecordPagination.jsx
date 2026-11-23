import { Button } from './Button.jsx'

export function RecordPagination({ result, noun, onPage }) {
  const { page, totalPages, totalElements } = result
  if (totalElements === 0) return null
  return <nav className="category-pagination" aria-label={'Phân trang ' + noun}>
    <p role="status">{totalElements} {noun} · Trang {page + 1}/{totalPages}</p>
    <div className="category-row-actions">
      <Button variant="secondary" disabled={page === 0} onClick={() => onPage(page - 1)}>Trang trước</Button>
      <Button variant="secondary" disabled={page + 1 >= totalPages} onClick={() => onPage(page + 1)}>Trang sau</Button>
    </div>
  </nav>
}
