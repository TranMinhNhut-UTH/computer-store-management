import assert from 'node:assert/strict'
import test from 'node:test'
import { createCustomerClient } from './customers.js'

const customer = { id: '9007199254740993', code: 'AC-01', fullName: 'Khách thử nghiệm', contact: { phone: '+84 123', email: null, address: null, notes: null }, active: true, createdAt: '2026-11-20T08:00:00Z', updatedAt: '2026-11-22T08:00:00Z' }

test('Customer list sends filters and retains only known server action hints', async () => {
  const client = createCustomerClient(async (path, options) => {
    assert.equal(path, '/api/v1/customers?page=1&size=20&q=A+%26+B&active=false')
    assert.equal(options.credentials, 'same-origin')
    return Response.json({ items: [customer], page: 1, size: 20, totalPages: 2, totalElements: 21,
      allowedActions: ['CUSTOMER_UPDATE', 'ADMIN', 'CUSTOMER_UPDATE'] })
  })
  const page = await client.list({ q: 'A & B', page: 1, active: false })
  assert.equal(page.items[0].id, customer.id)
  assert.equal('contact' in page.items[0], false)
  assert.deepEqual(page.allowedActions, ['CUSTOMER_UPDATE'])
})

test('Customer mutations project the DTO and obtain a fresh CSRF token per request', async () => {
  const requests = []
  const client = createCustomerClient(async (path, options) => {
    requests.push([path, options])
    if (path.endsWith('/csrf')) return Response.json({ headerName: 'X-CSRF-TOKEN', token: 'test' })
    return Response.json(customer)
  })
  const input = { code: 'AC-01', fullName: 'Khách thử nghiệm' }
  await client.create({ ...customer, phone: '+84 123', notes: 'Gọi trước' })
  await client.update(customer.id, { ...input, email: null })
  assert.equal(requests.length, 4)
  for (const [index, suffix, method] of [[1, '', 'POST'], [3, '/' + customer.id, 'PUT']]) {
    assert.equal(requests[index - 1][0], '/api/v1/auth/csrf')
    const [path, options] = requests[index]
    assert.equal(path, '/api/v1/customers' + suffix)
    assert.equal(options.method, method)
    assert.equal(options.headers['X-CSRF-TOKEN'], 'test')
    assert.equal(options.credentials, 'same-origin')
  }
  assert.deepEqual(JSON.parse(requests[1][1].body), { ...input, phone: '+84 123', notes: 'Gọi trước' })
  assert.deepEqual(JSON.parse(requests[3][1].body), { ...input, email: null })
})

test('Customer detail exposes only the approved contact projection', async () => {
  const client = createCustomerClient(async () => Response.json({ ...customer,
    contact: { ...customer.contact, privateField: 'hidden' }, history: [{ id: 1 }] }))
  const detail = await client.detail(customer.id)
  assert.deepEqual(detail.contact, customer.contact)
  assert.equal(Object.hasOwn(detail, 'history'), false)
  const malformed = createCustomerClient(async () => Response.json({ ...customer, contact: { phone: 123 } }))
  await assert.rejects(malformed.detail(customer.id), { status: 502 })
})

test('Customer client preserves safe authorization failures and rejects malformed records', async () => {
  const denied = createCustomerClient(async () => Response.json({ code: 'ACCESS_DENIED', detail: 'private' }, { status: 403 }))
  await assert.rejects(denied.detail(customer.id), { status: 403, code: 'ACCESS_DENIED', message: 'API request failed' })
  const malformed = createCustomerClient(async () => Response.json({ ...customer, id: 1 }))
  await assert.rejects(malformed.detail(customer.id), { status: 502 })
})
