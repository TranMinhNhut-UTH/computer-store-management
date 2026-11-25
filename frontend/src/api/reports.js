import { ApiError, createApiTransport } from './client.js'

const base = '/api/v1/reports'

export const overviewKeys = ['products', 'categories', 'suppliers', 'customers']

const isCount = value => Number.isSafeInteger(value) && value >= 0
const isBreakdown = value => [value?.total, value?.active, value?.inactive].every(isCount)
  && value.total === value.active + value.inactive

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
  }
}

export const reportsClient = createReportsClient()
