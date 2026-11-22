import { createAuthClient } from './auth.js'
import { ApiError, createApiTransport } from './client.js'

const base = '/api/v1/suppliers'
const idPattern = /^[1-9][0-9]{0,18}$/
const fields = ['code', 'name', 'phone', 'email', 'address', 'notes']
const actions = ['SUPPLIER_CREATE', 'SUPPLIER_UPDATE', 'SUPPLIER_DEACTIVATE']

function supplier(value) {
  if (typeof value?.id !== 'string' || !idPattern.test(value.id)
      || !['code', 'name'].every(key => typeof value[key] === 'string')
      || !fields.slice(2).every(key => value[key] === null || typeof value[key] === 'string')
      || typeof value.active !== 'boolean'
      || !['createdAt', 'updatedAt'].every(key => typeof value[key] === 'string' && Number.isFinite(Date.parse(value[key])))) {
    throw new ApiError(502)
  }
  return value
}

function path(id) {
  if (typeof id !== 'string' || !idPattern.test(id)) throw new ApiError(400)
  return `${base}/${id}`
}

export function createSupplierClient(fetchRequest = globalThis.fetch.bind(globalThis)) {
  const request = createApiTransport(fetchRequest, fields)
  const auth = createAuthClient(fetchRequest)
  async function mutate(url, method, values) {
    const csrf = await auth.refreshCsrf()
    const body = values && JSON.stringify(Object.fromEntries(fields.map(field => [field, values[field] ?? null])))
    return supplier(await request(url, {
      method,
      headers: { [csrf.headerName]: csrf.token, ...(body ? { 'Content-Type': 'application/json' } : {}) },
      ...(body ? { body } : {}),
    }))
  }
  return {
    async list({ q = '', active = '', page = 0, size = 20 } = {}, options = {}) {
      const params = new URLSearchParams({ page: String(page), size: String(size) })
      if (q) params.set('q', q)
      if (active !== '') params.set('active', String(active))
      const data = await request(`${base}?${params}`, options)
      if (!Array.isArray(data?.items) || !['page', 'size', 'totalElements', 'totalPages']
        .every(key => Number.isSafeInteger(data[key]) && data[key] >= 0)
        || data.size < 1 || data.size > 100 || data.items.length > data.size) throw new ApiError(502)
      const allowedActions = Array.isArray(data.allowedActions)
        ? actions.filter(code => data.allowedActions.includes(code))
        : []
      return { ...data, items: data.items.map(supplier), allowedActions }
    },
    async detail(id, options = {}) { return supplier(await request(path(id), options)) },
    create(values) { return mutate(base, 'POST', values) },
    update(id, values) { return mutate(path(id), 'PUT', values) },
    deactivate(id) { return mutate(`${path(id)}/deactivate`, 'POST') },
  }
}

export const supplierClient = createSupplierClient()
