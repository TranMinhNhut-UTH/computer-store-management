import assert from 'node:assert/strict'
import { before, beforeEach, afterEach, after, test } from 'node:test'
import { JSDOM } from 'jsdom'
import { createServer } from 'vite'
import { createElement, act } from 'react'

let dom, server, SupplierPage, root, container, client, calls, createRoot
const record = { id: '9007199254740993', code: 'AC-01', name: 'Đại lý', phone: '+84 123', email: null,
  address: 'Hà Nội', notes: null, active: true, createdAt: '2026-11-21T08:00:00Z', updatedAt: '2026-11-22T08:00:00Z' }
const actions = ['SUPPLIER_CREATE', 'SUPPLIER_UPDATE', 'SUPPLIER_DEACTIVATE']
const page = { items: [record], page: 0, size: 20, totalElements: 21, totalPages: 2, allowedActions: actions }

before(async () => {
  dom = new JSDOM('<!doctype html><html><body></body></html>', { url: 'http://localhost:5173/suppliers' })
  for (const name of ['window', 'document', 'HTMLElement']) {
    Object.defineProperty(globalThis, name, { configurable: true, value: dom.window[name] })
  }
  globalThis.IS_REACT_ACT_ENVIRONMENT = true
  ;({ createRoot } = await import('react-dom/client'))
  globalThis.fetch = async () => { throw new Error('Live requests are forbidden in Supplier UI tests') }
  dom.window.HTMLDialogElement.prototype.showModal = function () { this.setAttribute('open', '') }
  dom.window.HTMLDialogElement.prototype.close = function () { this.removeAttribute('open') }
  server = await createServer({ server: { middlewareMode: true, hmr: false }, appType: 'custom' })
  SupplierPage = (await server.ssrLoadModule('/src/features/suppliers/SupplierPage.jsx')).SupplierPage
})
beforeEach(() => {
  calls = []
  client = {
    async list(query) { calls.push(['list', query]); return { ...page, page: query.page } },
    async detail(id) { calls.push(['detail', id]); return record },
    async create(values) { calls.push(['create', values]) },
    async update(id, values) { calls.push(['update', id, values]) },
    async deactivate(id) { calls.push(['deactivate', id]) },
  }
  container = document.body.appendChild(document.createElement('div'))
  root = createRoot(container)
})
afterEach(async () => { await act(async () => root.unmount()); container.remove() })
after(async () => { await server.close(); dom.window.close() })

const button = text => [...container.querySelectorAll('button')].find(element => element.textContent === text)
const label = text => container.querySelector(`[aria-label="${text}"]`)
async function render() { await act(async () => root.render(createElement(SupplierPage, { client }))) }
async function click(element) { assert.ok(element); await act(async () => element.click()) }
async function submit() {
  await act(async () => container.querySelector('dialog form').dispatchEvent(new window.Event('submit', { bubbles: true, cancelable: true })))
}
async function change(element, value) {
  assert.ok(element)
  await act(async () => {
    Object.getOwnPropertyDescriptor(Object.getPrototypeOf(element), 'value').set.call(element, value)
    element.dispatchEvent(new window.Event(element.tagName === 'SELECT' ? 'change' : 'input', { bubbles: true }))
  })
}

test('Read-only Supplier users can list and view details without mutation controls', async () => {
  client.list = async () => ({ ...page, allowedActions: [] })
  await render()
  assert.match(container.querySelector('tbody').textContent, /AC-01.*Đại lý.*22\/11\/2026/s)
  assert.equal(button('Thêm nhà cung cấp'), undefined)
  assert.equal(label('Sửa AC-01'), null)
  assert.equal(label('Ngừng sử dụng AC-01'), null)
  await click(label('Xem AC-01'))
  assert.match(container.querySelector('dialog').textContent, /Hà Nội/)
})

test('Supplier create and edit submit, refresh the list, and use the server ID', async () => {
  await render()
  await click(button('Thêm nhà cung cấp'))
  await change(container.querySelector('dialog [name=code]'), 'NEW')
  await change(container.querySelector('dialog [name=name]'), 'Nhà phân phối')
  await submit()
  assert.deepEqual(calls.slice(-2), [['create', { code: 'NEW', name: 'Nhà phân phối' }], ['list', { q: '', active: '', page: 0 }]])
  await click(label('Sửa AC-01'))
  assert.equal(container.querySelector('dialog [name=phone]').value, '+84 123')
  await change(container.querySelector('dialog [name=phone]'), '12345')
  await submit()
  const edit = calls.find(([type]) => type === 'update')
  assert.equal(edit[1], record.id)
  assert.equal(edit[2].phone, '12345')
  assert.equal(container.querySelector('dialog'), null)
})

test('Deactivation confirms separately and keeps the draft conflict visible', async () => {
  client.deactivate = async id => { calls.push(['deactivate', id]); throw { status: 409 } }
  await render()
  await click(label('Ngừng sử dụng AC-01'))
  assert.match(container.querySelector('dialog').textContent, /lịch sử phiếu nhập/)
  await click(button('Hủy'))
  assert.equal(calls.some(([type]) => type === 'deactivate'), false)
  await click(label('Ngừng sử dụng AC-01'))
  await submit()
  assert.equal(calls.at(-1)[1], record.id)
  assert.match(container.querySelector('dialog [role=alert]').textContent, /phiếu nhập nháp/)
})

test('Search debounces and status changes clear the current page', async () => {
  await render()
  await click(button('Trang sau'))
  assert.equal(calls.at(-1)[1].page, 1)
  await change(container.querySelector('input'), 'Đại')
  assert.equal(calls.length, 2)
  await act(async () => { await new Promise(resolve => setTimeout(resolve, 375)) })
  assert.deepEqual(calls.at(-1)[1], { q: 'Đại', active: '', page: 0 })
  await click(button('Trang sau'))
  await change(container.querySelector('#supplier-active'), 'false')
  assert.deepEqual(calls.at(-1)[1], { q: 'Đại', active: 'false', page: 0 })
})

test('A later authorization error leaves no list rows or write actions', async () => {
  await render()
  client.list = async () => { throw { status: 403, detail: 'private' } }
  await change(container.querySelector('#supplier-active'), 'true')
  assert.match(container.querySelector('[role=alert]').textContent, /không có quyền/)
  assert.equal(container.querySelector('tbody'), null)
  assert.equal(button('Thêm nhà cung cấp'), undefined)
  assert.doesNotMatch(container.textContent, /private/)
})

test('Applying a Supplier filter hides stale rows and write controls while waiting', async () => {
  let finish
  const original = client.list
  client.list = (query, options) => query.active === 'false'
    ? new Promise(resolve => { finish = resolve })
    : original(query, options)
  await render()
  await change(container.querySelector('#supplier-active'), 'false')
  assert.equal(container.querySelector('table'), null)
  assert.equal(button('Thêm nhà cung cấp'), undefined)
  await act(async () => finish({ ...page, items: [], totalElements: 0, totalPages: 0 }))
  assert.match(container.textContent, /Không có nhà cung cấp/)
  assert.equal(container.querySelector('[aria-label="Phân trang nhà cung cấp"]'), null)
})
