import assert from 'node:assert/strict'
import { test } from 'node:test'
import { ApiError } from './client.js'
import { createInventoryClient } from './inventory.js'

test('balance client requests a filter and validates decimal-string rows', async () => {
  let url
  const client = createInventoryClient(async requestUrl => {
    url = requestUrl
    return Response.json({
      items: [{ productId: '42', sku: 'CPU-42', productName: 'CPU', quantityOnHand: '9007199254740993',
        minStockLevel: '2', stockStatus: 'NORMAL' }],
      page: 0, size: 20, totalElements: 1, totalPages: 1,
    })
  })

  const result = await client.getBalances({ filter: 'LOW' })

  assert.equal(new URL(url, 'http://localhost').searchParams.get('filter'), 'LOW')
  assert.equal(result.items[0].quantityOnHand, '9007199254740993')
})

test('movement client sends filters and rejects invalid time windows', async () => {
  let url
  const client = createInventoryClient(async requestUrl => {
    url = requestUrl
    return Response.json({ items: [], page: 0, size: 20, totalElements: 0, totalPages: 0 })
  })
  await client.getMovements({ productId: '42', type: 'PURCHASE_IN', from: '2026-11-01T00:00', to: '2026-12-01T00:00' })
  const params = new URL(url, 'http://localhost').searchParams
  assert.equal(params.get('productId'), '42')
  assert.equal(params.get('type'), 'PURCHASE_IN')
  assert.equal(params.get('from'), '2026-11-01T00:00')
  assert.equal(params.get('to'), '2026-12-01T00:00')
  await assert.rejects(() => client.getMovements({ productId: '0' }), error => error instanceof ApiError && error.status === 400)
  await assert.rejects(() => client.getMovements({ productId: '9223372036854775808' }),
    error => error instanceof ApiError && error.status === 400)
  await assert.rejects(() => client.getMovements({ from: '2026-12-01T00:00', to: '2026-11-01T00:00' }),
    error => error instanceof ApiError && error.status === 400)
})

test('malformed API list response is rejected', async () => {
  const client = createInventoryClient(async () => Response.json({
    items: [{ productId: '42', sku: 'CPU-42', productName: 'CPU', quantityOnHand: 1,
      minStockLevel: '2', stockStatus: 'NORMAL' }],
    page: 0, size: 20, totalElements: 1, totalPages: 1,
  }))
  await assert.rejects(() => client.getBalances(), error => error instanceof ApiError && error.status === 502)
})
