import assert from 'node:assert/strict'
import { before, beforeEach, afterEach, after, test } from 'node:test'
import { JSDOM } from 'jsdom'
import { createServer } from 'vite'
import { createElement, act } from 'react'

let dom, server, CustomerPage, root, container, client, calls, createRoot
const record = { id: '9007199254740993', code: 'AC-01', fullName: 'Đại lý', contact: { phone: '+84 123', email: null, address: 'Hà Nội', notes: null }, active: true, createdAt: '2026-11-21T08:00:00Z', updatedAt: '2026-11-22T08:00:00Z' }
const actions = ['CUSTOMER_CREATE', 'CUSTOMER_UPDATE']
const page = { items: [record], page: 0, size: 20, totalElements: 21, totalPages: 2, allowedActions: actions }

before(async () => {
  dom = new JSDOM('<!doctype html><html><body></body></html>', { url: 'http://localhost:5173/customers' })
  for (const name of ['window', 'document', 'HTMLElement']) {
    Object.defineProperty(globalThis, name, { configurable: true, value: dom.window[name] })
  }
  globalThis.IS_REACT_ACT_ENVIRONMENT = true
  ;({ createRoot } = await import('react-dom/client'))
  globalThis.fetch = async () => { throw new Error('Live requests are forbidden in Customer UI tests') }
  dom.window.HTMLDialogElement.prototype.showModal = function () { this.setAttribute('open', '') }
  dom.window.HTMLDialogElement.prototype.close = function () { this.removeAttribute('open') }
  server = await createServer({ server: { middlewareMode: true, hmr: false }, appType: 'custom' })
  CustomerPage = (await server.ssrLoadModule('/src/features/customers/CustomerPage.jsx')).CustomerPage
})
beforeEach(() => {
  calls = []
  client = {
    async list(query) { calls.push(['list', query]); return { ...page, page: query.page } },
    async detail(id) { calls.push(['detail', id]); return record },
    async create(values) { calls.push(['create', values]) },
    async update(id, values) { calls.push(['update', id, values]) },
  }
  container = document.body.appendChild(document.createElement('div'))
  root = createRoot(container)
})
afterEach(async () => { await act(async () => root.unmount()); container.remove() })
after(async () => { await server.close(); dom.window.close() })

const button = text => [...container.querySelectorAll('button')].find(element => element.textContent === text)
const label = text => container.querySelector(`[aria-label="${text}"]`)
async function render() { await act(async () => root.render(createElement(CustomerPage, { client }))) }
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

test('Read-only Customer users can list and view details without mutation controls', async () => {
  client.list = async () => ({ ...page, allowedActions: [] })
  client.detail = async () => ({ ...record, contact: null })
  await render()
  assert.match(container.querySelector('tbody').textContent, /AC-01.*Đại lý.*22\/11\/2026/s)
  assert.equal(button('Thêm khách hàng'), undefined)
  assert.equal(label('Sửa AC-01'), null)
  assert.equal(label('Ngừng sử dụng AC-01'), null)
  await click(label('Xem AC-01'))
  assert.match(container.querySelector('dialog').textContent, /Đại lý/)
  assert.doesNotMatch(container.querySelector('dialog').textContent, /Hà Nội|Điện thoại/)
})

test('Customer create and edit submit, refresh the list, and use the server ID', async () => {
  await render()
  await click(button('Thêm khách hàng'))
  await change(container.querySelector('dialog [name=code]'), 'NEW')
  await change(container.querySelector('dialog [name=fullName]'), 'Nhà phân phối')
  await submit()
  assert.deepEqual(calls.slice(-2), [['create', { code: 'NEW', fullName: 'Nhà phân phối' }], ['list', { q: '', active: '', page: 0 }]])
  await click(label('Sửa AC-01'))
  assert.equal(container.querySelector('dialog [name=phone]'), null)
  await change(container.querySelector('dialog [name=fullName]'), 'Tên đã sửa')
  await submit()
  const edit = calls.find(([type]) => type === 'update')
  assert.equal(edit[1], record.id)
  assert.deepEqual(edit[2], { code: record.code, fullName: 'Tên đã sửa' })
  assert.equal(container.querySelector('dialog'), null)
})

test('Denied detail blocks the edit form and missing rows show the empty state', async () => {
  client.detail = async () => { throw { status: 403 } }
  await render()
  await click(label('Sửa AC-01'))
  assert.match(container.querySelector('dialog [role=alert]').textContent, /không có quyền/)
  assert.equal(container.querySelector('dialog form'), null)
  await click(button('Đóng'))
  client.list = async () => ({ ...page, items: [], totalElements: 0, totalPages: 0 })
  await change(container.querySelector('#customer-active'), 'false')
  assert.match(container.textContent, /Không có khách hàng/)
})

test('Duplicate codes show a safe conflict and retain the form', async () => {
  client.create = async () => { throw { status: 409, detail: 'private' } }
  await render()
  await click(button('Thêm khách hàng'))
  await change(container.querySelector('dialog [name=code]'), 'DUPLICATE')
  await change(container.querySelector('dialog [name=fullName]'), 'Khách thử nghiệm')
  await submit()
  assert.match(container.querySelector('dialog [role=alert]').textContent, /đã được sử dụng/)
  assert.doesNotMatch(container.querySelector('dialog').textContent, /private/)
  assert.equal(container.querySelector('dialog [name=fullName]').value, 'Khách thử nghiệm')
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
  await change(container.querySelector('#customer-active'), 'false')
  assert.deepEqual(calls.at(-1)[1], { q: 'Đại', active: 'false', page: 0 })
})

test('A later authorization error leaves no list rows or write actions', async () => {
  await render()
  client.list = async () => { throw { status: 403, detail: 'private' } }
  await change(container.querySelector('#customer-active'), 'true')
  assert.match(container.querySelector('[role=alert]').textContent, /không có quyền/)
  assert.equal(container.querySelector('tbody'), null)
  assert.equal(button('Thêm khách hàng'), undefined)
  assert.doesNotMatch(container.textContent, /private/)
})

test('Customer validation links messages to fields and focuses the first invalid input', async () => {
  await render()
  await click(button('Thêm khách hàng'))
  const code = container.querySelector('dialog [name=code]')
  const name = container.querySelector('dialog [name=fullName]')
  await change(code, 'bad code')
  await submit()
  assert.equal(document.activeElement, code)
  for (const input of [code, name]) {
    assert.equal(input.getAttribute('aria-invalid'), 'true')
    const message = document.getElementById(input.getAttribute('aria-describedby'))
    assert.ok(message?.textContent)
  }
  assert.equal(calls.some(([kind]) => kind === 'create'), false)
  await change(code, 'GOOD')
  assert.equal(code.hasAttribute('aria-invalid'), false)
})
