import assert from 'node:assert/strict'
import test from 'node:test'
import { createProductClient } from './products.js'

const fixture = { id: '9007199254740993', sku: 'IT', name: 'Product', categoryId: '7', categoryName: 'Category',
  brandName: null, modelCode: null, description: null, minStockLevel: '0', sellingPrice: '9999999999999999999',
  active: true, createdAt: '2026-11-22T05:00:00Z', updatedAt: '2026-11-22T06:00:00Z' }

test('Product lists forward filters, pagination and abort signal without converting identifiers or price', async () => {
  const signal = new AbortController().signal
  const client = createProductClient(async (url, options) => {
    assert.equal(url, '/api/v1/products?page=1&size=20&q=CPU+%26+RAM&active=false&categoryId=7')
    assert.equal(options.signal, signal)
    assert.equal(options.credentials, 'same-origin')
    return Response.json({ items: [fixture], page: 1, size: 20, totalElements: 21, totalPages: 2 })
  })
  const result = await client.list({ q: 'CPU & RAM', active: false, categoryId: '7', page: 1 }, { signal })
  assert.equal(result.items[0].id, fixture.id)
  assert.equal(result.items[0].sellingPrice, fixture.sellingPrice)
})

test('Product mutations use fresh CSRF and separate information, price and state contracts', async () => {
  const calls = []
  const client = createProductClient(async (url, options) => {
    calls.push([url, options])
    if (url.endsWith('/csrf')) return Response.json({ headerName: 'X-CSRF-TOKEN', token: 'mock' })
    return Response.json(fixture)
  })
  await client.update(fixture.id, { ...fixture, actorId: '1' })
  await client.updatePrice(fixture.id, fixture.sellingPrice)
  await client.deactivate(fixture.id)
  await client.create(fixture)
  const info = { sku: 'IT', name: 'Product', categoryId: '7', brandName: null, modelCode: null, description: null, minStockLevel: '0' }
  const expected = [
    ['/9007199254740993', 'PUT', info],
    ['/9007199254740993/price', 'PUT', { sellingPrice: fixture.sellingPrice }],
    ['/9007199254740993/deactivate', 'POST', undefined],
    ['', 'POST', { information: info, sellingPrice: fixture.sellingPrice }],
  ]
  assert.equal(calls.length, 8)
  expected.forEach(([suffix, method, body], index) => {
    assert.equal(calls[index * 2][0], '/api/v1/auth/csrf')
    const [url, options] = calls[index * 2 + 1]
    assert.equal(url, '/api/v1/products' + suffix)
    assert.equal(options.method, method)
    assert.equal(options.headers['X-CSRF-TOKEN'], 'mock')
    assert.equal(options.credentials, 'same-origin')
    assert.deepEqual(options.body ? JSON.parse(options.body) : undefined, body)
  })
})

test('Product client rejects malformed numeric responses and preserves only allowlisted errors', async () => {
  const malformed = createProductClient(async () => Response.json({ ...fixture, sellingPrice: 10 }))
  await assert.rejects(malformed.detail(fixture.id), { status: 502 })
  const denied = createProductClient(async () => Response.json({ code: 'ACCESS_DENIED', detail: 'private' }, { status: 403 }))
  await assert.rejects(denied.detail(fixture.id), { status: 403, code: 'ACCESS_DENIED' })
  const invalid = createProductClient(async () => Response.json({
    code: 'VALIDATION_ERROR', fieldErrors: [{ field: 'minStockLevel' }, { field: 'actorId' }],
  }, { status: 400 }))
  await assert.rejects(invalid.detail(fixture.id), { status: 400, fields: ['minStockLevel'] })
})
