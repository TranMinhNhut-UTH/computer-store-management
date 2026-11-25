import assert from 'node:assert/strict'
import { before, beforeEach, afterEach, after, test } from 'node:test'
import { JSDOM } from 'jsdom'
import { createServer } from 'vite'
import { createElement, act } from 'react'
import { mkdtemp, rm } from 'node:fs/promises'
import { tmpdir } from 'node:os'
import { join, dirname, basename, resolve } from 'node:path'

let dom, server, CategoryPage, root, container, client, calls, createRoot, cacheDir
const record = { id: '9007199254740993', code: 'CPU', name: 'Bộ xử lý', description: 'Mô tả CPU', active: true, createdAt: '2026-11-21T17:00:00Z', updatedAt: '2026-11-22T06:45:00Z' }
const page = { items: [record], page: 0, size: 20, totalElements: 1, totalPages: 1 }
const wait = () => new Promise(resolve => setTimeout(resolve, 15))
before(async () => {
  dom = new JSDOM('<!doctype html><html><body></body></html>', { url: 'http://localhost:5173' })
  for (const name of ['window', 'document', 'HTMLElement']) Object.defineProperty(globalThis, name, { configurable: true, value: dom.window[name] })
  globalThis.IS_REACT_ACT_ENVIRONMENT = true
  // React DOM detects input-event support at import time; establish the DOM first.
  ;({ createRoot } = await import('react-dom/client'))
  // All Category requests use the injected in-memory client. Any accidental real request fails.
  globalThis.fetch = async () => { throw new Error('Real network forbidden in Category UI tests') }
  dom.window.HTMLDialogElement.prototype.showModal = function () { this.setAttribute('open', '') }
  dom.window.HTMLDialogElement.prototype.close = function () { this.removeAttribute('open') }
  cacheDir = await mkdtemp(join(tmpdir(), 'csm-category-test-'))
  server = await createServer({ cacheDir, server: { middlewareMode: true, hmr: false }, appType: 'custom' })
  CategoryPage = (await server.ssrLoadModule('/src/features/categories/CategoryPage.jsx')).CategoryPage
})
beforeEach(() => {
  calls = []
  client = {
    async list(query) { calls.push(['list', query]); return page },
    async detail(id) { calls.push(['detail', id]); return record },
    async create(values) { calls.push(['create', values]); return record },
    async update(id, values) { calls.push(['update', id, values]); return record },
    async deactivate(id) { calls.push(['deactivate', id]); return { ...record, active: false } },
  }
  container = document.createElement('div'); document.body.append(container); root = createRoot(container)
})
afterEach(async () => { await act(async () => root.unmount()); container.remove(); document.body.style.overflow = '' })
after(async () => {
  await server.close(); dom.window.close()
  assert.equal(dirname(resolve(cacheDir)), resolve(tmpdir()))
  assert.ok(basename(cacheDir).startsWith('csm-category-test-'))
  await rm(cacheDir, { recursive: true, force: true })
})
async function render(permissions = ['CATEGORY_CREATE', 'CATEGORY_UPDATE', 'CATEGORY_DEACTIVATE']) {
  await act(async () => { root.render(createElement(CategoryPage, { client, permissions })); await wait() })
}
async function click(element) { assert.ok(element); await act(async () => { element.click(); await wait() }) }
async function submit() { await act(async () => { container.querySelector('dialog form').dispatchEvent(new window.Event('submit', { bubbles: true, cancelable: true })); await wait() }) }
async function change(element, value) {
  assert.ok(element)
  await act(async () => {
    const setter = Object.getOwnPropertyDescriptor(Object.getPrototypeOf(element), 'value').set
    setter.call(element, value)
    element.dispatchEvent(new window.Event(element.tagName === 'SELECT' ? 'change' : 'input', { bubbles: true }))
    await wait()
  })
}
const button = text => [...container.querySelectorAll('button')].find(element => element.textContent === text)
const searchBox = () => container.querySelector('[name=q]')
const statusFilter = () => container.querySelector('#category-active')
const listCalls = () => calls.filter(([method]) => method === 'list').map(([, query]) => query)
async function enterSearch() {
  await act(async () => {
    searchBox().dispatchEvent(new window.KeyboardEvent('keydown', { key: 'Enter', bubbles: true, cancelable: true }))
    await wait()
  })
}
async function settleDebounce() {
  await act(async () => { await new Promise(resolve => setTimeout(resolve, 385)) })
}
async function validForm() {
  await change(container.querySelector('[name=code]'), ' cpu ')
  await change(container.querySelector('[name=name]'), ' Bộ xử lý ')
  await change(container.querySelector('[name=description]'), ' Mô tả ')
}
test('renders real list DTO, statuses and dates without client role inference or fake totals', async () => {
  await render()
  assert.equal(container.querySelector('h1').textContent, 'Quản lý danh mục')
  assert.match(container.querySelector('tbody').textContent, /CPU.*Bộ xử lý.*Mô tả CPU.*Đang sử dụng/s)
  assert.deepEqual([...container.querySelectorAll('time')].map(time => [time.textContent, time.dateTime]), [
    ['13:45 22/11/2026', record.updatedAt], ['22/11/2026', record.createdAt],
  ])
  assert.equal(container.querySelector('.csm-badge--success').textContent, 'Đang sử dụng')
  assert.equal(container.querySelector('.category-access-note'), null)
  assert.doesNotMatch(container.textContent, /runtime grants/)
  assert.match(container.querySelector('nav').textContent, /1 danh mục/)
  assert.equal(button('Trang sau').disabled, true)
})
test('auto-search debounces typing, trims the query and resets pagination', async () => {
  client.list = async query => { calls.push(['list', query]); return { ...page, page: query.page, totalElements: 41, totalPages: 3 } }
  await render(); await click(button('Trang sau'))
  assert.equal(listCalls().at(-1).page, 1)
  await change(searchBox(), 'C')
  await change(searchBox(), ' CP')
  await change(searchBox(), ' CPU ')
  assert.equal(listCalls().length, 2)
  await settleDebounce()
  assert.equal(listCalls().length, 3)
  assert.deepEqual(listCalls().at(-1), { q: 'CPU', active: '', page: 0, size: 20 })
  assert.equal(button('Trang trước').disabled, true)
})
test('Enter immediately applies the query and cancels the pending duplicate request', async () => {
  await render()
  await change(searchBox(), 'CPU')
  assert.equal(listCalls().length, 1)
  await enterSearch()
  assert.equal(listCalls().at(-1).q, 'CPU')
  await enterSearch()
  await settleDebounce()
  assert.equal(listCalls().length, 2)
})
test('status applies immediately with pending search text, resets pagination and can be cleared', async () => {
  client.list = async query => { calls.push(['list', query]); return { ...page, page: query.page, totalElements: 41, totalPages: 3 } }
  await render()
  await click(button('Trang sau'))
  await change(searchBox(), 'CPU')
  await change(statusFilter(), 'false')
  assert.deepEqual(listCalls().at(-1), { q: 'CPU', active: 'false', page: 0, size: 20 })
  assert.equal(listCalls().length, 3)
  await settleDebounce()
  assert.equal(listCalls().length, 3)
  await change(statusFilter(), 'true')
  assert.deepEqual(listCalls().at(-1), { q: 'CPU', active: 'true', page: 0, size: 20 })
  await click(button('Đặt lại'))
  assert.deepEqual(listCalls().at(-1), { q: '', active: '', page: 0, size: 20 })
  assert.equal(searchBox().value, '')
  assert.equal(statusFilter().value, '')
})
test('superseded list requests cannot display stale results or errors even if the client ignores abort', async () => {
  const requests = []
  client.list = (query, { signal }) => new Promise((resolve, reject) => {
    requests.push({ query, signal, resolve, reject })
  })
  await render()
  await change(statusFilter(), 'false')
  await change(searchBox(), 'new')
  await enterSearch()
  assert.equal(requests.length, 3)
  assert.equal(requests[0].signal.aborted, true)
  assert.equal(requests[1].signal.aborted, true)
  await act(async () => {
    requests[0].resolve(page)
    requests[1].reject({ status: 403 })
  })
  assert.match(container.textContent, /Đang tải danh mục/)
  assert.equal(container.querySelector('table, [role=alert]'), null)
  await act(async () => {
    requests[2].resolve({ ...page, items: [{ ...record, name: 'Kết quả mới' }] })
  })
  assert.match(container.querySelector('tbody').textContent, /Kết quả mới/)
})
test('loading contains no data; empty response displays honest empty state', async () => {
  let finish
  client.list = () => new Promise(resolve => { finish = resolve })
  await render()
  assert.match(container.textContent, /Đang tải danh mục/)
  assert.equal(container.querySelector('table'), null)
  await act(async () => { finish({ ...page, items: [], totalElements: 0, totalPages: 0 }); await wait() })
  assert.match(container.textContent, /Không có danh mục/)
  assert.equal(container.querySelector('table'), null)
})
for (const [status, message] of [[400, 'Dữ liệu chưa hợp lệ'], [401, 'Phiên đăng nhập đã kết thúc'], [403, 'Truy cập bị từ chối'], [404, 'không còn tồn tại'], [409, 'xung đột'], [0, 'Không thể kết nối']]) test(`list ${status} shows safe error and no fake data`, async () => {
  client.list = async () => { throw { status, detail: 'PRIVATE_SENTINEL' } }
  await render()
  assert.ok(container.querySelector('[role=alert]').textContent.includes(message))
  assert.equal(container.textContent.includes('PRIVATE_SENTINEL'), false)
  assert.equal(container.querySelector('table'), null)
  assert.equal(button('Tạo danh mục'), undefined)
  assert.equal(Boolean(button('Thử lại')), status !== 401 && status !== 403)
})
test('create dialog has labels, initial focus, exact DTO and reloads only after successful save', async () => {
  await render(); const opener = button('Tạo danh mục'); opener.focus(); await click(opener)
  assert.equal(document.activeElement, container.querySelector('[name=code]'))
  assert.equal(document.body.style.overflow, 'hidden')
  assert.equal(container.querySelector('label[for="category-description"]').textContent, 'Mô tả')
  await validForm(); await submit()
  assert.deepEqual(calls.find(call => call[0] === 'create')[1], { code: 'CPU', name: 'Bộ xử lý', description: 'Mô tả' })
  assert.equal(container.querySelector('dialog'), null)
  assert.equal(calls.filter(call => call[0] === 'list').length, 2)
  assert.ok(document.activeElement === opener, `Expected focus on create opener; active=${document.activeElement.tagName}#${document.activeElement.id}; disabled=${opener.disabled}`)
  assert.equal(document.body.style.overflow, '')
})
test('invalid required/max-length input prevents writes and focuses the first invalid field', async () => {
  await render(); await click(button('Tạo danh mục')); await submit()
  assert.equal(container.querySelectorAll('[aria-invalid=true]').length, 2)
  assert.equal(document.activeElement, container.querySelector('[name=code]'))
  await change(container.querySelector('[name=code]'), 'X'.repeat(51))
  await change(container.querySelector('[name=name]'), 'N'.repeat(121))
  await change(container.querySelector('[name=description]'), 'D'.repeat(501))
  await submit()
  assert.equal(container.querySelectorAll('[aria-invalid=true]').length, 3)
  assert.equal(calls.some(call => call[0] === 'create'), false)
})
test('edit fetches fresh detail and PUT sends only information fields, including clear-description null', async () => {
  client.detail = async id => { calls.push(['detail', id]); return { ...record, name: 'Fresh', active: false } }
  await render(); await click(button('Sửa'))
  assert.equal(container.querySelector('[name=name]').value, 'Fresh')
  await change(container.querySelector('[name=name]'), 'Updated')
  await change(container.querySelector('[name=description]'), '')
  await submit()
  assert.deepEqual(calls.find(call => call[0] === 'update'), ['update', record.id, { code: 'CPU', name: 'Updated', description: null }])
})
test('detail 404 never allows stale list data to be submitted', async () => {
  client.detail = async () => { throw { status: 404 } }
  await render(); await click(button('Sửa'))
  assert.match(container.querySelector('dialog [role=alert]').textContent, /không còn tồn tại/)
  assert.equal(button('Lưu danh mục').disabled, true)
  assert.equal(container.querySelector('dialog fieldset'), null)
  await submit()
  assert.equal(calls.some(call => call[0] === 'update'), false)
})
test('deactivate requires explicit confirm; cancel writes nothing and restores focus', async () => {
  await render(); const opener = button('Ngừng sử dụng')
  assert.equal(opener.type, 'button')
  assert.ok(opener.classList.contains('category-deactivate'))
  assert.ok(opener.classList.contains('csm-button--secondary'))
  opener.focus(); await click(opener)
  assert.equal(document.activeElement, button('Hủy'))
  assert.match(container.querySelector('dialog').textContent, /không bị xóa/)
  assert.match(container.querySelector('dialog').textContent, /bộ lọc Ngừng sử dụng/)
  assert.equal(calls.some(call => call[0] === 'deactivate'), false)
  await click(button('Hủy'))
  assert.equal(document.activeElement, opener)
  await click(opener); await submit()
  assert.deepEqual(calls.find(call => call[0] === 'deactivate'), ['deactivate', record.id])
  assert.equal(container.querySelector('dialog'), null)
})
test('deactivate 409 explains the provisional Product reference restriction without a fake success', async () => {
  client.deactivate = async () => { throw { status: 409 } }
  await render(); await click(button('Ngừng sử dụng')); await submit()
  assert.match(container.querySelector('dialog [role=alert]').textContent, /sản phẩm tham chiếu/)
  assert.equal(container.querySelector('.message-success'), null)
  assert.equal(calls.filter(call => call[0] === 'list').length, 1)
})
for (const status of [400, 401, 403, 404, 409]) test(`save ${status} retains dialog, maps safe errors and does not retry`, async () => {
  let writes = 0
  client.create = async () => { writes++; throw { status, fields: ['name'], detail: 'PRIVATE_SENTINEL' } }
  await render(); await click(button('Tạo danh mục')); await validForm(); await submit()
  assert.ok(container.querySelector('dialog [role=alert]'))
  assert.equal(container.textContent.includes('PRIVATE_SENTINEL'), false)
  assert.equal(writes, 1)
  assert.equal(button('Lưu danh mục').disabled, [401, 403, 404].includes(status))
  if (status === 400) assert.equal(container.querySelector('[name=name]').getAttribute('aria-invalid'), 'true')
})
test('pending save prevents duplicate submits and dismiss; dialog Escape restores focus when idle', async () => {
  let finish, writes = 0
  client.create = () => { writes++; return new Promise(resolve => { finish = resolve }) }
  await render(); const opener = button('Tạo danh mục'); opener.focus(); await click(opener); await validForm(); await submit(); await submit()
  assert.equal(writes, 1); assert.equal(button('Hủy').disabled, true)
  await act(async () => { container.querySelector('dialog').dispatchEvent(new window.Event('cancel', { cancelable: true })); await wait() })
  assert.ok(container.querySelector('dialog'))
  await act(async () => { finish(record); await wait() })
  await click(opener)
  await act(async () => { container.querySelector('dialog').dispatchEvent(new window.Event('cancel', { cancelable: true })); await wait() })
  assert.equal(container.querySelector('dialog'), null); assert.equal(document.activeElement, opener)
})
test('inactive rows offer edit but never hard delete or reactivate', async () => {
  client.list = async () => ({ ...page, items: [{ ...record, active: false }] })
  await render()
  assert.ok(button('Sửa')); assert.equal(button('Ngừng sử dụng'), undefined)
  assert.equal(container.querySelector('.csm-badge--error').textContent, 'Ngừng sử dụng')
  assert.equal(/Xóa|Kích hoạt lại/.test(container.textContent), false)
})

test('successful save keeps the focused opener available throughout a pending list refresh', async () => {
  let finishRefresh, reads = 0
  client.list = async query => {
    calls.push(['list', query])
    if (++reads === 1) return page
    return new Promise(resolve => { finishRefresh = resolve })
  }
  await render()
  const opener = button('Tạo danh mục')
  opener.focus()
  await click(opener); await validForm(); await submit()
  try {
    assert.equal(container.querySelector('dialog'), null)
    assert.match(container.textContent, /Đang tải danh mục/)
    assert.equal(container.querySelector('table'), null, 'Refreshing must not display stale rows')
    assert.equal(opener.disabled, false, 'A refresh must not disable the dialog focus-return target')
    assert.ok(document.activeElement === opener, 'Focus must return to the opener while refresh is pending')
  } finally {
    await act(async () => { finishRefresh(page); await wait() })
  }
  assert.ok(document.activeElement === opener, 'Focus must remain on the opener after refresh')
})

test('Category read-only users open fresh details without write actions', async () => {
  client.detail = async () => ({ ...record, name: 'Chi tiết từ máy chủ' })
  await render([])
  assert.equal(button('Tạo danh mục'), undefined)
  assert.equal(container.querySelector('button[aria-label="Sửa danh mục CPU"]'), null)
  assert.equal(container.querySelector('button[aria-label="Ngừng sử dụng danh mục CPU"]'), null)
  const opener = container.querySelector('button[aria-label="Xem danh mục CPU"]')
  opener.focus()
  await click(opener)
  assert.match(container.querySelector('dialog').textContent, /Chi tiết từ máy chủ/)
  assert.equal(container.querySelector('dialog form'), null)
  await click(button('Đóng chi tiết'))
  assert.equal(document.activeElement, opener)
})
