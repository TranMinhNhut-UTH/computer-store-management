import { ApiError, createApiTransport, validateListPage } from './client.js'

const base = '/api/v1/inventory'
const transactionTypes = new Set([
  'PURCHASE_IN', 'SALE_OUT', 'RETURN_IN', 'ADJUSTMENT_IN', 'ADJUSTMENT_OUT', 'SALE_CANCEL_IN',
])
const maxProductId = '9223372036854775807'
const isUnsignedInteger = value => typeof value === 'string' && /^(0|[1-9][0-9]*)$/.test(value)
const isInstant = value => typeof value === 'string' && Number.isFinite(Date.parse(value))

function validateBalance(row) {
  return typeof row?.productId === 'string'
    && typeof row.sku === 'string'
    && typeof row.productName === 'string'
    && isUnsignedInteger(row.quantityOnHand)
    && isUnsignedInteger(row.minStockLevel)
    && ['OUT_OF_STOCK', 'LOW_STOCK', 'NORMAL'].includes(row.stockStatus)
}

function validateMovement(row) {
  return typeof row?.id === 'string'
    && typeof row.productId === 'string'
    && typeof row.sku === 'string'
    && typeof row.productName === 'string'
    && transactionTypes.has(row.transactionType)
    && typeof row.quantityDelta === 'string'
    && /^-?(0|[1-9][0-9]*)$/.test(row.quantityDelta)
    && isUnsignedInteger(row.quantityAfter)
    && isInstant(row.occurredAt)
    && typeof row.recordedBy === 'string'
    && (row.sourceType === null || typeof row.sourceType === 'string')
    && (row.sourceId === null || typeof row.sourceId === 'string')
    && (row.reversalOfTransactionId === null || typeof row.reversalOfTransactionId === 'string')
    && (row.reversalReason === null || typeof row.reversalReason === 'string')
    && (row.note === null || typeof row.note === 'string')
    && isInstant(row.createdAt)
}

function productId(value) {
  if (value === '' || value == null) return ''
  if (!/^[1-9][0-9]{0,18}$/.test(value)
      || value.length === maxProductId.length && value > maxProductId) throw new ApiError(400)
  return value
}

export function createInventoryClient(fetchRequest) {
  const request = createApiTransport(fetchRequest)
  return {
    async getBalances({ page = 0, size = 20, filter = 'ALL' } = {}, options) {
      if (!['ALL', 'LOW', 'OUT'].includes(filter)) throw new ApiError(400)
      const params = new URLSearchParams({ page: String(page), size: String(size), filter })
      const data = await request(base + '/balances?' + params, options)
      validateListPage(data, page, size)
      if (!data.items.every(validateBalance)) throw new ApiError(502)
      return data
    },
    async getMovements({ page = 0, size = 20, productId: selectedProduct = '', type = '', from = '', to = '' } = {}, options) {
      const id = productId(selectedProduct)
      if (type && !transactionTypes.has(type)) throw new ApiError(400)
      if (from && !Number.isFinite(Date.parse(from)) || to && !Number.isFinite(Date.parse(to))
          || from && to && from >= to) throw new ApiError(400)

      const params = new URLSearchParams({ page: String(page), size: String(size) })
      if (id) params.set('productId', id)
      if (type) params.set('type', type)
      if (from) params.set('from', from)
      if (to) params.set('to', to)
      const data = await request(base + '/movements?' + params, options)
      validateListPage(data, page, size)
      if (!data.items.every(validateMovement)) throw new ApiError(502)
      return data
    },
  }
}

export const inventoryClient = createInventoryClient()
export { transactionTypes }
