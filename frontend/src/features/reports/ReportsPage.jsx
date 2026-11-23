import { useEffect, useState } from 'react'
import { overviewKeys, reportsClient } from '../../api/reports.js'
import { Button } from '../../components/ui/Button.jsx'
import { Card } from '../../components/ui/Card.jsx'
import { EmptyState } from '../../components/ui/EmptyState.jsx'
import { LoadingState } from '../../components/ui/LoadingState.jsx'
import { PageHeader } from '../../components/ui/PageHeader.jsx'
import './reports.css'

const labels = { products: 'Sản phẩm', categories: 'Danh mục', suppliers: 'Nhà cung cấp', customers: 'Khách hàng' }
const numbers = new Intl.NumberFormat('vi-VN')

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
    {phase === 'error' && <Card>
      <p className="message message-error" role="alert">
        {status === 403 ? 'Tài khoản hiện không có quyền xem báo cáo.'
          : status === 401 ? 'Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại.'
          : 'Không thể tải báo cáo.'}
      </p>
      {status !== 401 && status !== 403 && <Button variant="secondary" onClick={retry}>Thử lại</Button>}
    </Card>}
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
      <Card className="reports-breakdown">
        <h2>Chưa có số liệu</h2>
        <p className="csm-muted">Doanh thu, số bán và trả hàng, tồn kho và tồn thấp đang chờ tích hợp dữ liệu giao dịch.</p>
      </Card>
    </>}
  </section>
}
