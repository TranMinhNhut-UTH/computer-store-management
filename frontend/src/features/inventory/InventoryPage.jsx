import { useEffect, useState } from 'react'
import { inventoryClient, transactionTypes } from '../../api/inventory.js'
import { Button } from '../../components/ui/Button.jsx'
import { Card } from '../../components/ui/Card.jsx'
import { EmptyState } from '../../components/ui/EmptyState.jsx'
import { ListError } from '../../components/ui/ListError.jsx'
import { LoadingState } from '../../components/ui/LoadingState.jsx'
import { PageHeader } from '../../components/ui/PageHeader.jsx'
import { RecordPagination } from '../../components/ui/RecordPagination.jsx'
import { StatusBadge } from '../../components/ui/StatusBadge.jsx'
import { formatVietnameseDate } from '../../format/vietnameseDate.js'
import './inventory.css'

const numberFormat = new Intl.NumberFormat('vi-VN')
const balanceFilters = {
  ALL: { label: 'Tất cả', empty: 'Chưa có tồn kho để hiển thị', noun: 'sản phẩm' },
  LOW: { label: 'Tồn thấp', empty: 'Không có sản phẩm tồn thấp', noun: 'sản phẩm tồn thấp' },
  OUT: { label: 'Hết hàng', empty: 'Không có sản phẩm hết hàng', noun: 'sản phẩm hết hàng' },
}
const stockStatuses = {
  OUT_OF_STOCK: { label: 'Hết hàng', variant: 'error' },
  LOW_STOCK: { label: 'Tồn thấp', variant: 'warning' },
  NORMAL: { label: 'Bình thường', variant: 'success' },
}
const movementLabels = {
  PURCHASE_IN: 'Nhập hàng',
  SALE_OUT: 'Xuất bán',
  RETURN_IN: 'Nhập trả hàng',
  ADJUSTMENT_IN: 'Điều chỉnh tăng',
  ADJUSTMENT_OUT: 'Điều chỉnh giảm',
  SALE_CANCEL_IN: 'Đảo xuất bán',
}

function errorMessage(status, fallback) {
  if (status === 401) return 'Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại.'
  if (status === 403) return 'Tài khoản hiện không có quyền xem dữ liệu kho này.'
  return fallback
}

function formatQuantity(value) {
  return numberFormat.format(BigInt(value))
}

function movementSource(row) {
  if (row.sourceType === 'REVERSAL') return `Đảo movement #${row.sourceId}`
  const labels = {
    PURCHASE_ITEM: 'Phiếu nhập',
    SALES_ORDER_ITEM: 'Đơn bán',
    SALES_RETURN_ITEM: 'Phiếu trả hàng',
    INVENTORY_ADJUSTMENT_ITEM: 'Phiếu điều chỉnh',
  }
  return row.sourceType ? `${labels[row.sourceType] ?? row.sourceType} #${row.sourceId}` : ''
}

function Balances({ client, permissions }) {
  const [query, setQuery] = useState({ page: 0, filter: 'ALL' })
  const [settled, setSettled] = useState(null)
  const current = settled?.query === query ? settled : null
  const allowed = permissions.includes('INVENTORY_READ')

  useEffect(() => {
    if (!allowed) return undefined
    const controller = new AbortController()
    client.getBalances(query, { signal: controller.signal }).then(data => {
      if (!controller.signal.aborted) setSettled({ query, data })
    }).catch(error => {
      if (!controller.signal.aborted) setSettled({ query, error })
    })
    return () => controller.abort()
  }, [allowed, client, query])

  if (!allowed) return <ListError status={403} message={errorMessage(403)} />
  const { data, error } = current ?? {}
  const selected = balanceFilters[query.filter]
  return <>
    <div className="inventory-filters">
      <label htmlFor="inventory-balance-filter">Lọc tồn kho
        <select id="inventory-balance-filter" value={query.filter}
          onChange={event => setQuery({ page: 0, filter: event.target.value })}>
          {Object.entries(balanceFilters).map(([value, filter]) =>
            <option key={value} value={value}>{filter.label}</option>)}
        </select>
      </label>
    </div>
    {!current && <LoadingState label="Đang tải số dư tồn kho…" />}
    {error && <ListError status={error.status} onRetry={() => setQuery({ ...query })}
      message={errorMessage(error.status, 'Không thể tải số dư tồn kho.')} />}
    {data && (data.items.length === 0
      ? <EmptyState title={selected.empty} />
      : <>
        <div className="inventory-table-region" role="region" aria-label="Danh sách số dư tồn kho" tabIndex={0}>
          <table className="inventory-table">
            <thead><tr>{['SKU', 'Sản phẩm', 'Tồn hiện tại', 'Ngưỡng tối thiểu', 'Trạng thái']
              .map(label => <th key={label} scope="col">{label}</th>)}</tr></thead>
            <tbody>{data.items.map(row => {
              const status = stockStatuses[row.stockStatus]
              return <tr key={row.productId}>
                <td>{row.sku}</td><th scope="row">{row.productName}</th>
                <td>{formatQuantity(row.quantityOnHand)}</td><td>{formatQuantity(row.minStockLevel)}</td>
                <td><StatusBadge variant={status.variant}>{status.label}</StatusBadge></td>
              </tr>
            })}</tbody>
          </table>
        </div>
        <RecordPagination result={data} noun={selected.noun}
          onPage={page => setQuery(previous => ({ ...previous, page }))} />
      </>)}
  </>
}

function Movements({ client, permissions }) {
  const [filters, setFilters] = useState({ productId: '', type: '', from: '', to: '' })
  const [query, setQuery] = useState({ page: 0, ...filters })
  const [filterError, setFilterError] = useState('')
  const [settled, setSettled] = useState(null)
  const current = settled?.query === query ? settled : null
  const allowed = permissions.includes('INVENTORY_LEDGER_READ')

  useEffect(() => {
    if (!allowed) return undefined
    const controller = new AbortController()
    client.getMovements(query, { signal: controller.signal }).then(data => {
      if (!controller.signal.aborted) setSettled({ query, data })
    }).catch(error => {
      if (!controller.signal.aborted) setSettled({ query, error })
    })
    return () => controller.abort()
  }, [allowed, client, query])

  function applyFilters(event) {
    event.preventDefault()
    if (filters.productId && !/^[1-9][0-9]{0,18}$/.test(filters.productId)) {
      setFilterError({ field: 'productId', message: 'Mã sản phẩm phải là số nguyên dương hợp lệ.' })
      return
    }
    if (filters.productId && BigInt(filters.productId) > 9223372036854775807n) {
      setFilterError({ field: 'productId', message: 'Mã sản phẩm vượt quá giới hạn cho phép.' })
      return
    }
    if (filters.from && filters.to && filters.from >= filters.to) {
      setFilterError({ field: 'to', message: 'Thời điểm bắt đầu phải sớm hơn thời điểm kết thúc.' })
      return
    }
    setFilterError('')
    setQuery({ ...filters, page: 0 })
  }

  if (!allowed) return <ListError status={403} message={errorMessage(403)} />
  const { data, error } = current ?? {}
  return <>
    <form className="inventory-filters inventory-movement-filters" onSubmit={applyFilters}>
      <label htmlFor="movement-product">Mã sản phẩm
        <input id="movement-product" inputMode="numeric" maxLength={19} value={filters.productId}
          aria-invalid={filterError?.field === 'productId' || undefined}
          aria-describedby={filterError?.field === 'productId' ? 'movement-filter-error' : undefined}
          onChange={event => {
            setFilterError('')
            setFilters(previous => ({ ...previous, productId: event.target.value }))
          }} />
      </label>
      <label htmlFor="movement-type">Loại biến động
        <select id="movement-type" value={filters.type}
          onChange={event => {
            setFilterError('')
            setFilters(previous => ({ ...previous, type: event.target.value }))
          }}>
          <option value="">Tất cả</option>
          {[...transactionTypes].map(type => <option key={type} value={type}>{movementLabels[type]}</option>)}
        </select>
      </label>
      <label htmlFor="movement-from">Từ (UTC)
        <input id="movement-from" type="datetime-local" value={filters.from}
          onChange={event => {
            setFilterError('')
            setFilters(previous => ({ ...previous, from: event.target.value }))
          }} />
      </label>
      <label htmlFor="movement-to">Đến (UTC, không gồm)
        <input id="movement-to" type="datetime-local" value={filters.to}
          aria-invalid={filterError?.field === 'to' || undefined}
          aria-describedby={filterError?.field === 'to' ? 'movement-filter-error' : undefined}
          onChange={event => {
            setFilterError('')
            setFilters(previous => ({ ...previous, to: event.target.value }))
          }} />
      </label>
      <Button type="submit">Lọc lịch sử</Button>
    </form>
    {filterError && <p id="movement-filter-error" className="inventory-filter-error" role="alert">{filterError.message}</p>}
    {!current && <LoadingState label="Đang tải lịch sử biến động…" />}
    {error && <ListError status={error.status} onRetry={() => setQuery(previous => ({ ...previous }))}
      message={errorMessage(error.status, 'Không thể tải lịch sử biến động.')} />}
    {data && (data.items.length === 0
      ? <EmptyState title="Không có biến động phù hợp" />
      : <>
        <div className="inventory-table-region" role="region" aria-label="Lịch sử biến động tồn kho" tabIndex={0}>
          <table className="inventory-table inventory-movement-table">
            <thead><tr>{['Thời gian (GMT+7)', 'SKU / sản phẩm', 'Loại', 'Thay đổi', 'Tồn sau', 'Nguồn', 'Người ghi']
              .map(label => <th key={label} scope="col">{label}</th>)}</tr></thead>
            <tbody>{data.items.map(row => <tr key={row.id}>
              <td><time dateTime={row.occurredAt}>{formatVietnameseDate(row.occurredAt, { withTime: true })}</time></td>
              <th scope="row"><span className="inventory-secondary">{row.sku}</span>{row.productName}</th>
              <td>{movementLabels[row.transactionType]}</td>
              <td>{formatQuantity(row.quantityDelta)}</td>
              <td>{formatQuantity(row.quantityAfter)}</td>
              <td>
                {movementSource(row) || `Đảo movement #${row.reversalOfTransactionId}`}
                {row.reversalReason && <span className="inventory-secondary">Lý do: {row.reversalReason}</span>}
                {row.note && <span className="inventory-secondary">Ghi chú: {row.note}</span>}
              </td>
              <td>{row.recordedBy}</td>
            </tr>)}</tbody>
          </table>
        </div>
        <RecordPagination result={data} noun="movement"
          onPage={page => setQuery(previous => ({ ...previous, page }))} />
      </>)}
  </>
}

export function InventoryPage({ permissions = [], client = inventoryClient }) {
  const canReadBalances = permissions.includes('INVENTORY_READ')
  const canReadMovements = permissions.includes('INVENTORY_LEDGER_READ')
  const [tab, setTab] = useState('balances')
  const activeTab = tab === 'balances' && canReadBalances ? 'balances' : canReadMovements ? 'movements' : 'balances'

  function handleTabKeyDown(event) {
    if (!['ArrowLeft', 'ArrowRight', 'Home', 'End'].includes(event.key)) return
    event.preventDefault()
    const tabs = [...event.currentTarget.querySelectorAll('[role="tab"]')]
    const currentIndex = tabs.indexOf(event.target)
    if (currentIndex < 0) return
    const nextIndex = event.key === 'Home' ? 0
      : event.key === 'End' ? tabs.length - 1
        : (currentIndex + (event.key === 'ArrowRight' ? 1 : -1) + tabs.length) % tabs.length
    const nextTab = tabs[nextIndex]
    nextTab.focus()
    setTab(nextTab.id === 'inventory-tab-balances' ? 'balances' : 'movements')
  }

  return <section className="inventory-page">
    <PageHeader title="Kho hàng" description="Tra cứu số dư hiện tại và lịch sử biến động tồn kho." />
    <Card>
      <div className="inventory-tabs" role="tablist" aria-label="Dữ liệu kho" onKeyDown={handleTabKeyDown}>
        {canReadBalances && <button type="button" role="tab" id="inventory-tab-balances"
          tabIndex={activeTab === 'balances' ? 0 : -1}
          aria-selected={activeTab === 'balances'} aria-controls="inventory-panel"
          onClick={() => setTab('balances')}>Tồn kho</button>}
        {canReadMovements && <button type="button" role="tab" id="inventory-tab-movements"
          tabIndex={activeTab === 'movements' ? 0 : -1}
          aria-selected={activeTab === 'movements'} aria-controls="inventory-panel"
          onClick={() => setTab('movements')}>Lịch sử biến động</button>}
      </div>
      <section id="inventory-panel" role="tabpanel"
        aria-labelledby={activeTab === 'balances' ? 'inventory-tab-balances' : 'inventory-tab-movements'}>
        {activeTab === 'balances'
          ? <Balances client={client} permissions={permissions} />
          : <Movements client={client} permissions={permissions} />}
      </section>
    </Card>
  </section>
}
