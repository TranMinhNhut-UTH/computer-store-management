import assert from 'node:assert/strict'
import test from 'node:test'
import { createSupplierClient } from './suppliers.js'

const supplier = { id: '9007199254740993', code: 'AC-01', name: 'Nhà nhập khẩu', phone: '+84 123',
  email: null, address: null, notes: null, active: true, createdAt: '2026-11-20T08:00:00Z', updatedAt: '2026-11-22T08:00:00Z' }

test('Supplier list sends filters and retains only known server action hints', async () => {
  const client = createSupplierClient(async (path, options) => {
    assert.equal(path, '/api/v1/suppliers?page=1&size=20&q=A+%26+B&active=false')
    assert.equal(options.credentials, 'same-origin')
    return Response.json({ items: [supplier], page: 1, size: 20, totalPages: 2, totalElements: 21,
      allowedActions: ['SUPPLIER_UPDATE', 'ADMIN', 'SUPPLIER_UPDATE'] })
  })
  const page = await client.list({ q: 'A & B', page: 1, active: false })
  assert.equal(page.items[0].id, supplier.id)
  assert.deepEqual(page.allowedActions, ['SUPPLIER_UPDATE'])
})

test('Supplier mutations project the DTO and obtain a fresh CSRF token per request', async () => {
  const requests = []
  const client = createSupplierClient(async (path, options) => {
    requests.push([path, options])
    if (path.endsWith('/csrf')) return Response.json({ headerName: 'X-CSRF-TOKEN', token: 'test' })
    return Response.json(supplier)
  })
  await client.create(supplier)
  await client.update(supplier.id, supplier)
  await client.deactivate(supplier.id)
  const input = { code: 'AC-01', name: 'Nhà nhập khẩu', phone: '+84 123', email: null, address: null, notes: null }
  assert.equal(requests.length, 6)
  for (const [index, suffix, method] of [[1, '', 'POST'], [3, '/' + supplier.id, 'PUT'], [5, '/' + supplier.id + '/deactivate', 'POST']]) {
    assert.equal(requests[index - 1][0], '/api/v1/auth/csrf')
    const [path, options] = requests[index]
    assert.equal(path, '/api/v1/suppliers' + suffix)
    assert.equal(options.method, method)
    assert.equal(options.headers['X-CSRF-TOKEN'], 'test')
    assert.equal(options.credentials, 'same-origin')
    if (index === 5) assert.equal(options.body, undefined)
    else assert.deepEqual(JSON.parse(options.body), input)
  }
})

test('Supplier client preserves safe authorization failures and rejects malformed records', async () => {
  const denied = createSupplierClient(async () => Response.json({ code: 'ACCESS_DENIED', detail: 'private' }, { status: 403 }))
  await assert.rejects(denied.detail(supplier.id), { status: 403, code: 'ACCESS_DENIED', message: 'API request failed' })
  const malformed = createSupplierClient(async () => Response.json({ ...supplier, id: 1 }))
  await assert.rejects(malformed.detail(supplier.id), { status: 502 })
})
