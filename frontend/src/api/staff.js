import { createAuthClient } from './auth.js'
import { ApiError, createApiTransport, validateListPage } from './client.js'

const base = '/api/v1/employees'
export const staffRoles = ['ADMIN', 'MANAGER', 'SALES_STAFF', 'WAREHOUSE_STAFF']
const hints = ['EMPLOYEE_CREATE', 'EMPLOYEE_UPDATE', 'EMPLOYEE_ROLE_ASSIGN', 'EMPLOYEE_LOCK', 'EMPLOYEE_UNLOCK']
const validId = value => typeof value === 'string' && /^[1-9][0-9]*$/.test(value)

function entry(id) {
  if (!validId(id)) throw new ApiError(400)
  return base + '/' + id
}

function staff(data) {
  if (!validId(data?.id) || typeof data.username !== 'string' || typeof data.fullName !== 'string'
      || typeof data.active !== 'boolean' || !Array.isArray(data.roles) || !data.roles.every(role => staffRoles.includes(role))
      || ![data.email, data.phone].every(value => value === null || typeof value === 'string')
      || typeof data.updatedAt !== 'string' || !Number.isFinite(Date.parse(data.updatedAt))) throw new ApiError(502)
  const { id, username, fullName, email, phone, active, roles, updatedAt } = data
  return { id, username, fullName, email, phone, active, roles, updatedAt }
}

const information = ({ fullName, email, phone }) => ({ fullName, email: email || null, phone: phone || null })

export function createStaffClient(fetchRequest = globalThis.fetch.bind(globalThis)) {
  const request = createApiTransport(fetchRequest, ['username', 'fullName', 'email', 'phone', 'password', 'roles'])
  const auth = createAuthClient(fetchRequest)
  async function mutate(url, method, payload) {
    const csrf = await auth.refreshCsrf()
    return staff(await request(url, {
      method, headers: { [csrf.headerName]: csrf.token, ...(payload ? { 'Content-Type': 'application/json' } : {}) },
      ...(payload ? { body: JSON.stringify(payload) } : {}),
    }))
  }

  return {
    async list({ q = '', active = '', page = 0 } = {}, options) {
      const size = 20
      const params = new URLSearchParams({ page: String(page), size: String(size) })
      if (q) params.set('q', q)
      if (active !== '') params.set('active', active)
      const data = await request(base + '?' + params, options)
      validateListPage(data, page, size)
      const { page: current, totalElements, totalPages } = data
      return { items: data.items.map(staff), page: current, size, totalElements, totalPages,
        allowedActions: hints.filter(code => Array.isArray(data.allowedActions) && data.allowedActions.includes(code)) }
    },
    async detail(id, options) { return staff(await request(entry(id), options)) },
    create(values) {
      return mutate(base, 'POST', { ...information(values), username: values.username, password: values.password, roles: values.roles })
    },
    update(id, values) { return mutate(entry(id), 'PUT', information(values)) },
    assignRoles(id, roles) { return mutate(entry(id) + '/roles', 'PUT', { roles }) },
    setActive(id, active) { return mutate(entry(id) + (active ? '/unlock' : '/lock'), 'POST') },
  }
}

export const staffClient = createStaffClient()
