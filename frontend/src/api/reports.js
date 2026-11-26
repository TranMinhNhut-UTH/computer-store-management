import { ApiError, createApiTransport, validateListPage } from './client.js'

const base = '/api/v1/reports'

export const overviewKeys = ['products', 'categories', 'suppliers', 'customers']

const isCount = value => Number.isSafeInteger(value) && value >= 0
const isBreakdown = value => [value?.total, value?.active, value?.inactive].every(isCount)
  && value.total === value.active + value.inactive
const isQuantity = value => typeof value === 'string' && /^(0|[1-9][0-9]*)$/.test(value)

function isStockProduct(row, filter) {
  if (!['sku', 'productName', 'categoryName'].every(key => typeof row?.[key] === 'string')
      || !isQuantity(row.quantityOnHand) || !isQuantity(row.minStockLevel)) return false
  const quantity = BigInt(row.quantityOnHand)
  const low = quantity <= BigInt(row.minStockLevel)
  const status = quantity === 0n ? 'OUT_OF_STOCK' : low ? 'LOW_STOCK' : 'NORMAL'
  return row.stockStatus === status
    && (filter === 'ALL' || (filter === 'LOW' && low) || (filter === 'OUT' && quantity === 0n))
}

export function createReportsClient(fetchRequest) {
  const request = createApiTransport(fetchRequest)
  return {
    async canViewOverview(options) {
      return await request(base + '/access', options) === null
    },
    async getOverview(options) {
      const data = await request(base + '/overview', options)
      if (!overviewKeys.every(key => isBreakdown(data?.[key])) || !Array.isArray(data?.productsByCategory)
          || !data.productsByCategory.every(row => typeof row?.code === 'string' && typeof row.name === 'string'
            && isBreakdown(row.products))) throw new ApiError(502)
      return data
    },
    async getInventory({ page = 0, size = 20, filter = 'LOW' } = {}, options) {
      const params = new URLSearchParams({ page: String(page), size: String(size) })
      if (filter !== 'LOW') params.set('filter', filter)
      const data = await request(base + '/inventory?' + params, options)
      validateListPage(data, page, size)
      const { summary } = data
      const total = { ALL: summary?.activeSkus, LOW: summary?.lowStockSkus, OUT: summary?.outOfStockSkus }[filter]
      if (![summary?.activeSkus, summary?.outOfStockSkus, summary?.lowStockSkus].every(isCount)
          || !isQuantity(summary.totalQuantity) || summary.outOfStockSkus > summary.lowStockSkus
          || summary.lowStockSkus > summary.activeSkus || total !== data.totalElements
          || !data.items.every(row => isStockProduct(row, filter))) throw new ApiError(502)
      return data
    },
  }
}

export const reportsClient = createReportsClient()
