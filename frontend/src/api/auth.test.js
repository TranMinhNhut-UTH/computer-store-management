import { createApiTransport, observeSessionRejection } from './client.js'
import assert from 'node:assert/strict'
import test from 'node:test'
import { ApiError, createAuthClient } from './auth.js'
import { createStaffClient } from './staff.js'

// These tests never contact a server: every fetch is an in-memory stub.
const json = (body, status = 200) => new Response(JSON.stringify(body), { status })
const csrf = { headerName: 'X-CSRF-TOKEN', token: 'test-masked-token' }

test('a rejected Staff mutation signals sign-in but sends the write only once', async () => {
  const writes = []
  const staff = createStaffClient(async (url, options) => {
    if (url.endsWith('/csrf')) return json(csrf)
    writes.push({ url, method: options.method })
    return json({ code: 'UNAUTHENTICATED' }, 401)
  })
  let rejected = 0
  const stop = observeSessionRejection(() => { rejected++ })
  try {
    await assert.rejects(staff.setActive('42', false), { status: 401 })
    assert.equal(rejected, 1)
    assert.deepEqual(writes, [{ url: '/api/v1/employees/42/lock', method: 'POST' }])
  } finally {
    stop()
  }
})

test('login sends exact password and server-named CSRF header with same-origin cookies', async () => {
  const requests = []
  const client = createAuthClient(async (url, options) => {
    requests.push({ url, options })
    if (url.endsWith('/csrf')) return json(csrf)
    return json({ userId: '9007199254740993', username: 'unit-user', passwordHash: 'must-not-propagate' })
  })
  const result = await client.login({ username: ' UNIT-USER ', password: ' untrimmed unit password ' })
  assert.deepEqual(result, { userId: '9007199254740993', username: 'unit-user' })
  assert.deepEqual(requests.map(item => item.url), ['/api/v1/auth/csrf', '/api/v1/auth/login'])
  const request = requests[1].options
  assert.equal(request.method, 'POST')
  assert.equal(request.headers['X-CSRF-TOKEN'], csrf.token)
  assert.deepEqual(JSON.parse(request.body), { username: ' UNIT-USER ', password: ' untrimmed unit password ' })
  for (const item of requests) assert.equal(item.options.credentials, 'same-origin')
})

test('anonymous me is null, but server failure is not interpreted as anonymous', async () => {
  const anonymous = createAuthClient(async () => json({ code: 'UNAUTHENTICATED' }, 401))
  assert.equal(await anonymous.getCurrentUser(), null)
  const failed = createAuthClient(async () => json({ detail: 'internal-secret' }, 500))
  await assert.rejects(failed.getCurrentUser(), error => error.status === 500 && !error.message.includes('internal-secret'))
})

test('logout fetches fresh CSRF and accepts 204 without parsing a JSON body', async () => {
  const urls = []
  const client = createAuthClient(async (url, options) => {
    urls.push(url)
    if (url.endsWith('/csrf')) return json(csrf)
    assert.equal(options.method, 'POST')
    assert.equal(options.headers[csrf.headerName], csrf.token)
    assert.equal(options.body, undefined)
    return new Response(null, { status: 204 })
  })
  await client.logout()
  assert.deepEqual(urls, ['/api/v1/auth/csrf', '/api/v1/auth/logout'])
})

test('a failed POST is not retried and raw problem detail is discarded', async () => {
  let posts = 0
  const client = createAuthClient(async (url) => {
    if (url.endsWith('/csrf')) return json(csrf)
    posts++
    return json({ code: 'ACCESS_DENIED', detail: 'internal-secret' }, 403)
  })
  await assert.rejects(client.logout(), error => error instanceof ApiError && error.status === 403
    && error.code === 'ACCESS_DENIED' && !error.message.includes('internal-secret'))
  assert.equal(posts, 1)
})

test('invalid CSRF response prevents sending any POST', async () => {
  let requests = 0
  const client = createAuthClient(async () => { requests++; return json({}) })
  await assert.rejects(client.logout(), error => error.status === 502)
  assert.equal(requests, 1)
})

test('network errors are sanitized and aborted requests remain abortable', async () => {
  const failed = createAuthClient(async () => { throw new Error('sensitive network details') })
  await assert.rejects(failed.refreshCsrf(), error => error.status === 0 && !error.message.includes('sensitive'))
  const aborted = createAuthClient(async () => { throw new DOMException('Cancelled', 'AbortError') })
  await assert.rejects(aborted.refreshCsrf(), error => error.name === 'AbortError')
})

test('a delayed or aborted 401 cannot reject a newer session', async () => {
  const requests = []
  const request = createApiTransport(() => new Promise(resolve => requests.push(resolve)))
  const denied = () => Response.json({ code: 'UNAUTHENTICATED' }, { status: 401 })
  let rejected = 0
  const old = observeSessionRejection(() => { throw new Error('Old session must not be notified') })
  const stale = request('/api/v1/categories')
  const stop = observeSessionRejection(() => { rejected++ })
  try {
    old()
    requests.shift()(denied())
    await assert.rejects(stale, { status: 401 })
    assert.equal(rejected, 0)

    const abort = new AbortController()
    const abandoned = request('/api/v1/categories', { signal: abort.signal })
    abort.abort()
    requests.shift()(denied())
    await assert.rejects(abandoned, { status: 401 })
    assert.equal(rejected, 0)

    const current = request('/api/v1/categories')
    requests.shift()(denied())
    await assert.rejects(current, { status: 401 })
    assert.equal(rejected, 1)
  } finally { stop() }
})
