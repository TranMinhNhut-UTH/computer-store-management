import assert from 'node:assert/strict'
import test from 'node:test'
import { createCategoryClient } from './categories.js'
import { ApiError } from './auth.js'
import { categoryValues, validateCategory } from '../features/categories/validation.js'

const record = { id: '9007199254740993', code: 'CPU', name: 'Processors', description: null, active: true, createdAt: '2026-11-21T01:00:00Z', updatedAt: '2026-11-21T01:00:00Z' }
const page = { items: [record], page: 0, size: 20, totalElements: 1, totalPages: 1 }
const csrf = { headerName: 'X-SERVER-CSRF', token: 'test-only-token' }

test('list uses exact page contract, string IDs, encoded search and optional active filter', async () => {
  const calls = []
  const client = createCategoryClient(async (url, options) => { calls.push({ url, options }); return Response.json(page) })
  assert.deepEqual(await client.list(), page)
  await client.list({ q: 'CPU% &', active: false, page: 2, size: 10 })
  const params = new URL(calls[1].url, 'http://local').searchParams
  assert.equal(params.get('q'), 'CPU% &'); assert.equal(params.get('active'), 'false')
  assert.equal(params.get('page'), '2'); assert.equal(params.get('size'), '10')
  assert.equal(new URL(calls[0].url, 'http://local').searchParams.has('active'), false)
  for (const call of calls) { assert.equal(call.options.credentials, 'same-origin'); assert.equal(call.options.cache, 'no-store') }
})
test('detail projects DTO and retains ID precision without numeric conversion', async () => {
  const client = createCategoryClient(async url => {
    assert.equal(url, '/api/v1/categories/9007199254740993')
    return Response.json({ ...record, internal: 'not-returned' })
  })
  assert.deepEqual(await client.detail(record.id), record)
})
for (const action of ['create', 'update', 'deactivate']) test(`${action} uses fresh server CSRF, exact method/path and payload allowlist`, async () => {
  const calls = []
  const client = createCategoryClient(async (url, options) => {
    calls.push({ url, options })
    return Response.json(url.endsWith('/csrf') ? csrf : record)
  })
  const values = { code: 'CPU', name: 'Processors', description: null, active: false, id: '42', createdAt: 'forbidden' }
  if (action === 'create') await client.create(values)
  if (action === 'update') await client.update(record.id, values)
  if (action === 'deactivate') await client.deactivate(record.id)
  assert.equal(calls[0].url, '/api/v1/auth/csrf')
  assert.equal(calls[1].options.headers[csrf.headerName], csrf.token)
  assert.equal(calls[1].options.method, action === 'update' ? 'PUT' : 'POST')
  assert.equal(calls[1].url, `/api/v1/categories${action === 'create' ? '' : '/' + record.id}${action === 'deactivate' ? '/deactivate' : ''}`)
  if (action !== 'deactivate') assert.deepEqual(JSON.parse(calls[1].options.body), { code: 'CPU', name: 'Processors', description: null })
  else assert.equal(calls[1].options.body, undefined)
  assert.ok(calls.every(call => call.options.credentials === 'same-origin'))
})
for (const status of [400, 401, 403, 404, 409, 500]) test(`HTTP ${status} is preserved, sanitized and never retried`, async () => {
  let writes = 0
  const client = createCategoryClient(async url => {
    if (url.endsWith('/csrf')) return Response.json(csrf)
    writes++
    return Response.json({ code: 'BUSINESS_CONFLICT', detail: 'PRIVATE_SENTINEL', fieldErrors: [{ field: 'name', message: 'PRIVATE_SENTINEL' }, { field: 'secret' }] }, { status })
  })
  await assert.rejects(client.create(record), error => error instanceof ApiError && error.status === status && error.fields.join() === 'name' && !JSON.stringify(error).includes('PRIVATE_SENTINEL'))
  assert.equal(writes, 1)
})
test('invalid CSRF prevents mutations; network, abort and malformed responses stay errors', async () => {
  let calls = 0
  const invalid = createCategoryClient(async () => { calls++; return Response.json({}) })
  await assert.rejects(invalid.create(record), { status: 502 }); assert.equal(calls, 1)
  await assert.rejects(createCategoryClient(async () => { throw new Error('private') }).list(), { status: 0 })
  await assert.rejects(createCategoryClient(async () => { throw new DOMException('stop', 'AbortError') }).list(), { name: 'AbortError' })
  for (const body of [{ content: [] }, { ...page, items: [{ ...record, id: 42 }] }, { ...page, size: 0 }]) {
    await assert.rejects(createCategoryClient(async () => Response.json(body)).list(), { status: 502 })
  }
})
test('validation matches canonical code/required fields and Unicode character limits', () => {
  assert.deepEqual(categoryValues({ code: ' cpu ', name: ' Processors ', description: ' ' }), { code: 'CPU', name: 'Processors', description: null })
  assert.deepEqual(validateCategory({ code: 'X'.repeat(50), name: '🚀'.repeat(120), description: '🚀'.repeat(500) }), {})
  assert.deepEqual(Object.keys(validateCategory({ code: 'X'.repeat(51), name: '🚀'.repeat(121), description: '🚀'.repeat(501) })), ['code', 'name', 'description'])
  assert.deepEqual(Object.keys(validateCategory({ code: ' ', name: ' ', description: '' })), ['code', 'name'])
  assert.ok(validateCategory({ code: 'bad code', name: 'Name', description: '' }).code)
})
