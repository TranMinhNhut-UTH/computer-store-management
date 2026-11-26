import { createAuthClient } from './auth.js'
import { ApiError, createApiTransport, validateListPage } from './client.js'

const base = '/api/v1/customers'
const idPattern = /^[1-9][0-9]{0,18}$/
export const customerContactFields = ['phone', 'email', 'address', 'notes']
const fields = ['code', 'fullName', ...customerContactFields]
const actions = ['CUSTOMER_CREATE', 'CUSTOMER_UPDATE']

function customer(value, includeContact = false) {
  if (typeof value?.id !== 'string' || !idPattern.test(value.id)
      || !['code', 'fullName'].every(key => typeof value[key] === 'string')
      || typeof value.active !== 'boolean'
      || !['createdAt', 'updatedAt'].every(key => typeof value[key] === 'string' && Number.isFinite(Date.parse(value[key])))) {
    throw new ApiError(502)
  }
  const { id, code, fullName, active, createdAt, updatedAt } = value
  const core = { id, code, fullName, active, createdAt, updatedAt }
  if (!includeContact) return core
  const contact = value.contact ?? {}
  if (typeof contact !== 'object' || Array.isArray(contact)
      || customerContactFields.some(key => contact[key] != null && typeof contact[key] !== 'string')) {
    throw new ApiError(502)
  }
  return { ...core, contact: Object.fromEntries(customerContactFields.map(key => [key, contact[key] ?? null])) }
}

function path(id) {
  if (typeof id !== 'string' || !idPattern.test(id)) throw new ApiError(400)
  return `${base}/${id}`
}

export function createCustomerClient(fetchRequest = globalThis.fetch.bind(globalThis)) {
  const request = createApiTransport(fetchRequest, fields)
  const auth = createAuthClient(fetchRequest)
  async function mutate(url, method, values) {
    const csrf = await auth.refreshCsrf()
    const body = JSON.stringify(Object.fromEntries(fields.filter(field => values[field] !== undefined)
      .map(field => [field, values[field]])))
    return customer(await request(url, {
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
      validateListPage(data, page, size)
      const allowedActions = Array.isArray(data.allowedActions)
        ? actions.filter(code => data.allowedActions.includes(code))
        : []
      return { ...data, items: data.items.map(item => customer(item)), allowedActions }
    },
    async detail(id, options = {}) { return customer(await request(path(id), options), true) },
    create(values) { return mutate(base, 'POST', values) },
    update(id, values) { return mutate(path(id), 'PUT', values) },
  }
}

export const customerClient = createCustomerClient()
