import { createAuthClient } from './auth.js'
import { ApiError, createApiTransport, validateListPage } from './client.js'

const base = '/api/v1/products'
const idPattern = /^[1-9][0-9]{0,18}$/
const integerPattern = /^(0|[1-9][0-9]{0,18})$/
const infoFields = ['sku', 'name', 'categoryId', 'brandName', 'modelCode', 'description', 'minStockLevel']
const writePermissions = ['PRODUCT_UPDATE', 'PRODUCT_PRICE_UPDATE', 'PRODUCT_DEACTIVATE']
function information(values) {
  return Object.fromEntries(infoFields.map(key => [key, values[key]]))
}
function path(id) {
  if (typeof id !== 'string' || !idPattern.test(id)) throw new ApiError(400)
  return `${base}/${id}`
}
function record(value) {
  if (!value || !['id', 'categoryId'].every(key => typeof value[key] === 'string' && idPattern.test(value[key]))
      || !['sellingPrice', 'minStockLevel'].every(key => typeof value[key] === 'string' && integerPattern.test(value[key]))
      || !['sku', 'name', 'categoryName'].every(key => typeof value[key] === 'string')
      || !['brandName', 'modelCode', 'description'].every(key => value[key] === null || typeof value[key] === 'string')
      || typeof value.active !== 'boolean'
      || !['createdAt', 'updatedAt'].every(key => typeof value[key] === 'string' && Number.isFinite(Date.parse(value[key])))) {
    throw new ApiError(502)
  }
  return value
}

export function createProductClient(fetchRequest = globalThis.fetch.bind(globalThis)) {
  const request = createApiTransport(fetchRequest, [...infoFields, 'sellingPrice'])
  const auth = createAuthClient(fetchRequest)
  async function mutate(url, method, payload) {
    const csrf = await auth.refreshCsrf()
    return record(await request(url, {
      method,
      headers: { [csrf.headerName]: csrf.token, ...(payload ? { 'Content-Type': 'application/json' } : {}) },
      ...(payload ? { body: JSON.stringify(payload) } : {}),
    }))
  }
  return {
    async list({ q = '', active = '', categoryId = '', page = 0, size = 20 } = {}, { signal } = {}) {
      const params = new URLSearchParams({ page: String(page), size: String(size) })
      if (q) params.set('q', q)
      if (active !== '') params.set('active', String(active))
      if (categoryId) params.set('categoryId', categoryId)
      const data = await request(`${base}?${params}`, { signal })
      validateListPage(data, page, size)
      const allowedActions = Array.isArray(data.allowedActions)
        ? writePermissions.filter(code => data.allowedActions.includes(code))
        : []
      return { ...data, items: data.items.map(record), allowedActions }
    },
    async detail(id, { signal } = {}) { return record(await request(path(id), { signal })) },
    create(values) { return mutate(base, 'POST', { information: information(values), sellingPrice: values.sellingPrice }) },
    update(id, values) { return mutate(path(id), 'PUT', information(values)) },
    updatePrice(id, sellingPrice) { return mutate(`${path(id)}/price`, 'PUT', { sellingPrice }) },
    deactivate(id) { return mutate(`${path(id)}/deactivate`, 'POST') },
  }
}
export const productClient = createProductClient()
