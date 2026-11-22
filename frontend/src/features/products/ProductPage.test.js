import assert from 'node:assert/strict'
import { after, afterEach, before, beforeEach, test } from 'node:test'
import { JSDOM } from 'jsdom'
import { createServer } from 'vite'
import { act, createElement } from 'react'

let dom, server, createRoot, root, container, client, ProductPage, calls
const product = { id: '9007199254740993', sku: 'CPU', name: 'Bộ xử lý', categoryId: '7', categoryName: 'Linh kiện',
  brandName: 'Brand', modelCode: 'M1', description: 'Thông tin', sellingPrice: '9999999999999999999',
  minStockLevel: '2', active: true, createdAt: '2026-11-21T17:00:00Z', updatedAt: '2026-11-22T06:45:00Z' }
const categories = { async list() { return { items: [{ id: '7', code: 'PARTS', name: 'Linh kiện' }], totalPages: 1 } } }

before(async () => {
  dom = new JSDOM('<!doctype html><html><body></body></html>', { url: 'http://localhost:5173/products' })
  for (const name of ['window', 'document', 'HTMLElement']) Object.defineProperty(globalThis, name, { configurable: true, value: dom.window[name] })
  globalThis.IS_REACT_ACT_ENVIRONMENT = true
  ;({ createRoot } = await import('react-dom/client'))
  globalThis.fetch = async () => { throw new Error('Real network is forbidden') }
  dom.window.HTMLDialogElement.prototype.showModal = function () { this.setAttribute('open', '') }
  dom.window.HTMLDialogElement.prototype.close = function () { this.removeAttribute('open') }
  server = await createServer({ server: { middlewareMode: true, hmr: false }, appType: 'custom' })
  ProductPage = (await server.ssrLoadModule('/src/features/products/ProductPage.jsx')).ProductPage
})
beforeEach(() => {
  calls = []
  client = {
    async list(query) { calls.push(['list', query]); return { items: [product], page: query.page, totalElements: 30, totalPages: 2 } },
    async detail(id) { calls.push(['detail', id]); return product },
    async create() { calls.push(['create']); throw new Error('Create must stay blocked') },
    async update(id, values) { calls.push(['update', id, values]); return product },
    async updatePrice(id, value) { calls.push(['price', id, value]); return product },
    async deactivate(id) { calls.push(['deactivate', id]); return { ...product, active: false } },
  }
  container = document.body.appendChild(document.createElement('div'))
  root = createRoot(container)
})
afterEach(async () => { await act(async () => root.unmount()); container.remove() })
after(async () => { await server.close(); dom.window.close() })

const button = label => [...container.querySelectorAll('button')].find(item => item.textContent === label)
const byLabel = label => container.querySelector(`button[aria-label="${label}"]`)
async function render(writesReleased = false) {
  await act(async () => root.render(createElement(ProductPage, { client, categories, writesReleased })))
}
async function click(el) { assert.ok(el); await act(async () => el.click()) }
async function change(selector, value) {
  const el = container.querySelector(selector)
  assert.ok(el)
  await act(async () => {
    Object.getOwnPropertyDescriptor(Object.getPrototypeOf(el), 'value').set.call(el, value)
    el.dispatchEvent(new window.Event(el.tagName === 'SELECT' ? 'change' : 'input', { bubbles: true }))
  })
}
async function submit() {
  await act(async () => container.querySelector('dialog form').dispatchEvent(
    new window.Event('submit', { bubbles: true, cancelable: true })))
}

test('Product renders list and detail with exact money, Vietnamese date and disabled unreleased actions', async () => {
  await render()
  assert.match(container.querySelector('tbody').textContent, /9\.999\.999\.999\.999\.999\.999/)
  assert.match(container.querySelector('tbody').textContent, /13:45 22\/11\/2026/)
  for (const name of ['Sửa sản phẩm CPU', 'Đổi giá bán CPU', 'Ngừng kinh doanh CPU']) assert.ok(byLabel(name).disabled)
  await click(byLabel('Xem sản phẩm CPU'))
  assert.match(container.querySelector('dialog').textContent, /Linh kiện.*Brand.*M1/s)
  assert.deepEqual(calls.at(-1), ['detail', product.id])
})

test('Product search debounces and status filter applies immediately with pagination reset', async () => {
  await render()
  await click(button('Trang sau'))
  assert.equal(calls.at(-1)[1].page, 1)
  await change('[name=product-search]', 'C')
  await change('[name=product-search]', 'CPU')
  assert.equal(calls.length, 2)
  await act(async () => { await new Promise(resolve => setTimeout(resolve, 375)) })
  assert.deepEqual(calls.at(-1)[1], { q: 'CPU', active: '', page: 0, size: 20 })
  await click(button('Trang sau'))
  await change('#product-active', 'false')
  assert.deepEqual(calls.at(-1)[1], { q: 'CPU', active: 'false', page: 0, size: 20 })
  await click(button('Đặt lại'))
  assert.deepEqual(calls.at(-1)[1], { q: '', active: '', page: 0, size: 20 })
})

test('Product creation form is inspectable but cannot submit without DEP-001', async () => {
  await render(true)
  await click(button('Xem biểu mẫu tạo'))
  assert.ok(container.querySelector('dialog [name=sku]'))
  assert.ok(container.querySelector('dialog [name=sellingPrice]'))
  assert.ok(container.querySelector('dialog select option[value="7"]'))
  assert.match(container.querySelector('dialog').textContent, /DEP-001/)
  assert.ok(button('Lưu sản phẩm').disabled)
  await submit()
  assert.equal(calls.some(([action]) => action === 'create'), false)
})

test('A superseded Product search cannot overwrite a newer empty result', async () => {
  const requests = []
  client.list = (query, { signal }) => new Promise(resolve => requests.push({ resolve, signal }))
  await render()
  assert.match(container.textContent, /Đang tải sản phẩm/)
  await change('#product-active', 'false')
  assert.equal(requests[0].signal.aborted, true)
  await act(async () => requests[1].resolve({ items: [], page: 0, totalElements: 0, totalPages: 0 }))
  await act(async () => requests[0].resolve({ items: [product], page: 0, totalElements: 1, totalPages: 1 }))
  assert.match(container.textContent, /Không có sản phẩm/)
  assert.equal(container.querySelector('table'), null)
})

test('Product edit form leaves price in a separate command and preserves a 19-digit amount', async () => {
  await render(true)
  await click(byLabel('Sửa sản phẩm CPU'))
  assert.equal(container.querySelector('dialog [name=sellingPrice]'), null)
  await change('dialog [name=name]', 'Sản phẩm mới')
  await submit()
  assert.equal(calls.find(([action]) => action === 'update')[2].name, 'Sản phẩm mới')
  assert.equal(container.querySelector('dialog'), null)
  await click(byLabel('Đổi giá bán CPU'))
  assert.equal(container.querySelector('dialog [name=sku]'), null)
  await change('dialog [name=sellingPrice]', '1.25')
  await submit()
  assert.ok(container.querySelector('dialog [aria-invalid=true]'))
  assert.equal(calls.some(([action]) => action === 'price'), false)
  await change('dialog [name=sellingPrice]', '9223372036854775808')
  await submit()
  assert.deepEqual(calls.find(([action]) => action === 'price'), ['price', product.id, '9223372036854775808'])
})

test('Deactivation requires confirmation and 409 remains in the dialog', async () => {
  let attempts = 0
  client.deactivate = async () => { attempts++; throw { status: 409 } }
  await render(true)
  await click(byLabel('Ngừng kinh doanh CPU'))
  assert.match(container.querySelector('dialog').textContent, /vẫn được lưu/)
  await click(button('Hủy'))
  assert.equal(attempts, 0)
  await click(byLabel('Ngừng kinh doanh CPU'))
  await submit()
  assert.equal(attempts, 1)
  assert.match(container.querySelector('dialog [role=alert]').textContent, /xung đột/)
})

test('Successful deactivation refreshes the row and removes its deactivate action', async () => {
  let current = product
  client.list = async () => ({ items: [current], page: 0, totalElements: 1, totalPages: 1 })
  client.deactivate = async id => {
    assert.equal(id, product.id)
    current = { ...product, active: false }
    return current
  }
  await render(true)
  await click(byLabel('Ngừng kinh doanh CPU'))
  await submit()
  assert.equal(container.querySelector('dialog'), null)
  assert.match(container.querySelector('.csm-badge--error').textContent, /Ngừng kinh doanh/)
  assert.equal(byLabel('Ngừng kinh doanh CPU'), null)
})

for (const [status, text] of [[401, /Phiên đăng nhập/], [403, /Truy cập bị từ chối/]]) {
  test(`Product handles ${status} without leaking server detail or showing sample rows`, async () => {
    client.list = async () => { throw { status, detail: 'private' } }
    await render()
    assert.match(container.querySelector('[role=alert]').textContent, text)
    assert.doesNotMatch(container.textContent, /private/)
    assert.equal(container.querySelector('table'), null)
  })
}
