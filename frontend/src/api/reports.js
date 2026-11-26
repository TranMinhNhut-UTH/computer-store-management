import { ApiError, createApiTransport, validateListPage } from './client.js'

const base = '/api/v1/reports'

export const overviewKeys = ['products', 'categories', 'suppliers', 'customers']

const isCount = value => Number.isSafeInteger(value) && value >= 0
const isBreakdown = value => [value?.total, value?.active, value?.inactive].every(isCount)
  && value.total === value.active + value.inactive
const isQuantity = value => typeof value === 'string' && /^(0|[1-9][0-9]*)$/.test(value)

function isLowStockProduct(row) {
  return ['sku', 'productName', 'categoryName'].every(key => typeof row?.[key] === 'string')
    && isQuantity(row.quantityOnHand) && isQuantity(row.minStockLevel)
    && BigInt(row.quantityOnHand) <= BigInt(row.minStockLevel)
    && row.stockStatus === (row.quantityOnHand === '0' ? 'OUT_OF_STOCK' : 'LOW_STOCK')
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
    async getInventory({ page = 0, size = 20 } = {}, options) {
      const params = new URLSearchParams({ page: String(page), size: String(size) })
      const data = await request(base + '/inventory?' + params, options)
      validateListPage(data, page, size)
      const { summary } = data
      if (![summary?.activeSkus, summary?.outOfStockSkus, summary?.lowStockSkus].every(isCount)
          || !isQuantity(summary.totalQuantity) || summary.outOfStockSkus > summary.lowStockSkus
          || summary.lowStockSkus > summary.activeSkus || summary.lowStockSkus !== data.totalElements
          || !data.items.every(isLowStockProduct)) throw new ApiError(502)
      return data
    },
  }
}

export const reportsClient = createReportsClient()
