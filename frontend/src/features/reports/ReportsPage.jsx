import { useEffect, useState } from 'react'
import { overviewKeys, reportsClient } from '../../api/reports.js'
import { ListError } from '../../components/ui/ListError.jsx'
import { Card } from '../../components/ui/Card.jsx'
import { EmptyState } from '../../components/ui/EmptyState.jsx'
import { LoadingState } from '../../components/ui/LoadingState.jsx'
import { PageHeader } from '../../components/ui/PageHeader.jsx'
import { RecordPagination } from '../../components/ui/RecordPagination.jsx'
import { StatusBadge } from '../../components/ui/StatusBadge.jsx'
import '../categories/categories.css'
import './reports.css'

const labels = { products: 'Sản phẩm', categories: 'Danh mục', suppliers: 'Nhà cung cấp', customers: 'Khách hàng' }
const numbers = new Intl.NumberFormat('vi-VN')

function reportError(status, fallback) {
  if (status === 403) return 'Tài khoản hiện không có quyền xem báo cáo.'
  if (status === 401) return 'Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại.'
  return fallback
}

function InventoryReport() {
  const [query, setQuery] = useState({ page: 0 })
  const [settled, setSettled] = useState(null)
  const current = settled?.query === query ? settled : null

  useEffect(() => {
    const controller = new AbortController()
    reportsClient.getInventory(query, { signal: controller.signal }).then(data => {
      if (controller.signal.aborted) return
      if (query.page > 0 && query.page >= data.totalPages) {
        setQuery({ page: Math.max(0, data.totalPages - 1) })
      } else setSettled({ query, data })
    }).catch(error => {
      if (!controller.signal.aborted) setSettled({ query, error })
    })
    return () => controller.abort()
  }, [query])

  const { data, error } = current ?? {}
  return <Card className="reports-breakdown" aria-labelledby="reports-inventory">
    <h2 id="reports-inventory">Tổng quan tồn kho</h2>
    {!current && <LoadingState label="Đang tải báo cáo tồn kho…" />}
    {error && <ListError status={error.status} onRetry={() => setQuery({ ...query })}
      message={reportError(error.status, error.status === 409
        ? 'Có sản phẩm đang kinh doanh chưa có số dư tồn kho.'
        : 'Không thể tải báo cáo tồn kho.')} />}
    {data && <>
      <dl className="reports-inventory-metrics">
        <div><dt>SKU đang kinh doanh</dt><dd>{numbers.format(data.summary.activeSkus)}</dd></div>
        <div><dt>Tổng số lượng tồn</dt><dd>{numbers.format(BigInt(data.summary.totalQuantity))}</dd></div>
        <div><dt>SKU hết hàng</dt><dd>{numbers.format(data.summary.outOfStockSkus)}</dd></div>
        <div><dt>SKU tồn thấp (gồm hết hàng)</dt><dd>{numbers.format(data.summary.lowStockSkus)}</dd></div>
      </dl>
      <h3 id="reports-low-stock">Sản phẩm tồn thấp</h3>
      {data.items.length === 0 ? <EmptyState title="Không có sản phẩm tồn thấp" /> : <>
        <div className="reports-table reports-stock-table" role="region" aria-labelledby="reports-low-stock" tabIndex={0}>
          <table aria-labelledby="reports-low-stock">
            <thead><tr>{['SKU / Sản phẩm', 'Danh mục', 'Tồn hiện tại', 'Ngưỡng tối thiểu', 'Trạng thái']
              .map(label => <th key={label} scope="col">{label}</th>)}</tr></thead>
            <tbody>{data.items.map(row => {
              const empty = row.stockStatus === 'OUT_OF_STOCK'
              return <tr key={row.sku}>
                <th scope="row"><span className="csm-muted">{row.sku}</span>{row.productName}</th>
                <td>{row.categoryName}</td>
                <td>{numbers.format(BigInt(row.quantityOnHand))}</td>
                <td>{numbers.format(BigInt(row.minStockLevel))}</td>
                <td><StatusBadge variant={empty ? 'error' : 'warning'}>{empty ? 'Hết hàng' : 'Tồn thấp'}</StatusBadge></td>
              </tr>
            })}</tbody>
          </table>
        </div>
        <RecordPagination result={data} noun="sản phẩm tồn thấp" onPage={page => setQuery({ page })} />
      </>}
    </>}
  </Card>
}

export function ReportsPage() {
  const [request, setRequest] = useState({ phase: 'loading' })
  const [attempt, setAttempt] = useState(0)

  useEffect(() => {
    const controller = new AbortController()
    reportsClient.getOverview({ signal: controller.signal }).then(
      data => { if (!controller.signal.aborted) setRequest({ phase: 'ready', data }) },
      error => { if (!controller.signal.aborted) setRequest({ phase: 'error', status: error.status }) },
    )
    return () => controller.abort()
  }, [attempt])

  const { phase, data, status } = request

  function retry() {
    setRequest({ phase: 'loading' })
    setAttempt(value => value + 1)
  }

  return <section className="reports-page">
    <PageHeader title="Báo cáo" description="Tổng hợp danh mục, sản phẩm và đối tác." />
    {phase === 'loading' && <Card><LoadingState label="Đang tải báo cáo…" /></Card>}
    {phase === 'error' && <Card><ListError status={status} onRetry={retry}
      message={reportError(status, 'Không thể tải báo cáo.')} /></Card>}
    {phase === 'ready' && <>
      <div className="reports-kpis" aria-label="Tổng số theo nhóm">
        {overviewKeys.map(key => <Card className="reports-kpi" key={key}>
          <h2>{labels[key]}</h2>
          <strong>{numbers.format(data[key].total)}</strong>
          <dl><div><dt>Đang hoạt động</dt><dd>{numbers.format(data[key].active)}</dd></div>
            <div><dt>Ngừng hoạt động</dt><dd>{numbers.format(data[key].inactive)}</dd></div></dl>
        </Card>)}
      </div>
      <Card className="reports-breakdown">
        <h2 id="reports-by-category">Sản phẩm theo danh mục</h2>
        {data.productsByCategory.length === 0 ? <EmptyState title="Chưa có danh mục" /> : <div className="reports-table">
          <table aria-labelledby="reports-by-category">
            <thead><tr><th scope="col">Danh mục</th><th scope="col">Tổng</th><th scope="col">Hoạt động</th><th scope="col">Ngừng</th></tr></thead>
            <tbody>{data.productsByCategory.map(row => <tr key={row.code}>
              <th scope="row">{row.name}<span className="csm-muted">{row.code}</span></th>
              <td>{numbers.format(row.products.total)}</td><td>{numbers.format(row.products.active)}</td>
              <td>{numbers.format(row.products.inactive)}</td>
            </tr>)}</tbody>
          </table>
        </div>}
      </Card>
      <InventoryReport />
      <Card className="reports-breakdown">
        <h2>Số liệu chưa khả dụng</h2>
        <p className="csm-muted">Doanh thu, số bán và trả hàng đang chờ tích hợp dữ liệu giao dịch.</p>
      </Card>
    </>}
  </section>
}
