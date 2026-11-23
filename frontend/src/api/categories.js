import { createAuthClient } from './auth.js'
import { ApiError, createApiTransport, validateListPage } from './client.js'

const BASE = '/api/v1/categories'
function category(value) {
  if (!value || typeof value.id !== 'string' || !/^[1-9][0-9]*$/.test(value.id)
      || typeof value.code !== 'string' || typeof value.name !== 'string'
      || !(value.description === null || typeof value.description === 'string')
      || typeof value.active !== 'boolean'
      || ![value.createdAt, value.updatedAt].every(v => typeof v === 'string' && Number.isFinite(Date.parse(v)))) throw new ApiError(502)
  const { id, code, name, description, active, createdAt, updatedAt } = value
  return { id, code, name, description, active, createdAt, updatedAt }
}
function path(id) {
  if (typeof id !== 'string' || !/^[1-9][0-9]*$/.test(id)) throw new ApiError(400)
  return `${BASE}/${encodeURIComponent(id)}`
}

export function createCategoryClient(fetchRequest = globalThis.fetch.bind(globalThis)) {
  const request = createApiTransport(fetchRequest)
  const auth = createAuthClient(fetchRequest)
  async function mutate(url, method, values) {
    const csrf = await auth.refreshCsrf()
    // Explicit DTO projection; never send client state, ID, actor or timestamps.
    const body = values === undefined ? undefined : JSON.stringify({
      code: values.code, name: values.name, description: values.description ?? null,
    })
    return category(await request(url, {
      method, headers: { [csrf.headerName]: csrf.token, ...(body ? { 'Content-Type': 'application/json' } : {}) },
      ...(body ? { body } : {}),
    }))
  }
  return {
    async list({ q = '', active = '', page = 0, size = 20 } = {}, options = {}) {
      const params = new URLSearchParams({ page: String(page), size: String(size) })
      if (q) params.set('q', q)
      if (active !== '') params.set('active', String(active))
      const data = await request(`${BASE}?${params}`, options)
      validateListPage(data, page, size)
      return { ...data, items: data.items.map(category) }
    },
    async detail(id, options = {}) { return category(await request(path(id), options)) },
    create(values) { return mutate(BASE, 'POST', values) },
    update(id, values) { return mutate(path(id), 'PUT', values) },
    deactivate(id) { return mutate(`${path(id)}/deactivate`, 'POST') },
  }
}
export const categoryClient = createCategoryClient()
