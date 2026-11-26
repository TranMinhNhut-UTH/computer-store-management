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
const allWrites = ['PRODUCT_UPDATE', 'PRODUCT_PRICE_UPDATE', 'PRODUCT_DEACTIVATE']

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
async function render(allowedActions = [], permissions = [], categoryApi = categories) {
  const list = client.list
  client.list = async (...args) => ({ ...await list(...args), allowedActions })
  await act(async () => root.render(createElement(ProductPage, { client, categories: categoryApi, permissions })))
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

test('Product renders list and detail for read-only staff with write actions hidden', async () => {
  await render()
  assert.match(container.querySelector('tbody').textContent, /9\.999\.999\.999\.999\.999\.999/)
  assert.match(container.querySelector('tbody').textContent, /13:45 22\/11\/2026/)
  for (const name of ['Sửa sản phẩm CPU', 'Đổi giá bán CPU', 'Ngừng kinh doanh CPU']) assert.equal(byLabel(name), null)
  assert.equal(button('Xem biểu mẫu tạo'), undefined)
  await click(byLabel('Xem sản phẩm CPU'))
  assert.match(container.querySelector('dialog').textContent, /Linh kiện.*Brand.*M1/s)
  assert.deepEqual(calls.at(-1), ['detail', product.id])
})

test('Product enables only the specific write action reported by the server', async () => {
  await render(['PRODUCT_UPDATE', 'PRODUCT_CREATE'])
  assert.equal(byLabel('Sửa sản phẩm CPU').disabled, false)
  assert.equal(byLabel('Đổi giá bán CPU'), null)
  assert.equal(byLabel('Ngừng kinh doanh CPU'), null)
  assert.equal(button('Tạo sản phẩm'), undefined)
  assert.equal(container.querySelector('dialog'), null)
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
  await render(allWrites, ['PRODUCT_CREATE', 'PRODUCT_PRICE_UPDATE'])
  await click(button('Xem biểu mẫu tạo'))
  assert.ok(container.querySelector('dialog [name=sku]'))
  assert.ok(container.querySelector('dialog [name=sellingPrice]'))
  assert.ok(container.querySelector('dialog select option[value="7"]'))
  assert.match(container.querySelector('dialog').textContent, /DEP-001/)
  assert.ok(button('Lưu sản phẩm').disabled)
  await submit()
  assert.equal(calls.some(([action]) => action === 'create'), false)
})

test('Product Create needs both server actions, validates, and refreshes after saving', async () => {
  let finish
  client.create = values => {
    calls.push(['create', values])
    return new Promise(resolve => { finish = resolve })
  }
  await render(['PRODUCT_CREATE', 'PRODUCT_PRICE_UPDATE'])
  await click(button('Tạo sản phẩm'))
  await submit()
  assert.equal(document.activeElement.name, 'sellingPrice')
  assert.equal(calls.some(([action]) => action === 'create'), false)

  await change('[name=sku]', 'new-sku')
  await change('[name=name]', 'Sản phẩm mới')
  await change('[name=categoryId]', '7')
  await change('[name=sellingPrice]', '9999999999999999999')
  await submit()
  assert.ok(button('Lưu sản phẩm').disabled)
  assert.equal(calls.at(-1)[1].sellingPrice, '9999999999999999999')
  await submit()
  assert.equal(calls.filter(([action]) => action === 'create').length, 1)
  await act(async () => finish(product))
  assert.equal(container.querySelector('dialog'), null)
  assert.match(container.textContent, /Đã tạo sản phẩm với số dư tồn kho ban đầu bằng 0/)
  assert.equal(calls.at(-1)[0], 'list')
})

test('Product Create keeps form input when a server field error or conflict is returned', async () => {
  let error = { status: 400, fields: ['sku'] }
  client.create = async () => { throw error }
  await render(['PRODUCT_CREATE', 'PRODUCT_PRICE_UPDATE'])
  await click(button('Tạo sản phẩm'))
  await change('[name=sku]', 'duplicate')
  await change('[name=name]', 'Sản phẩm mới')
  await change('[name=categoryId]', '7')
  await change('[name=sellingPrice]', '0')
  await submit()
  const input = container.querySelector('[name=sku]')
  assert.equal(input.getAttribute('aria-invalid'), 'true')
  error = { status: 409 }
  await submit()
  assert.match(container.querySelector('[role=alert]').textContent, /SKU đã tồn tại.*danh mục đã ngừng sử dụng/)
  assert.equal(input.value, 'duplicate')
  assert.equal(button('Lưu sản phẩm').disabled, false)
})

test('Product category lookup returns to an available page when the result shrinks', async () => {
  const pages = []
  const categoryApi = { async list({ page }) {
    pages.push(page)
    if (page === 1) return { items: [], totalPages: 1 }
    return { items: [{ id: '8', code: 'NEW', name: 'Danh mục mới' }], totalPages: pages.length === 1 ? 2 : 1 }
  } }
  await render(['PRODUCT_UPDATE'], [], categoryApi)
  await click(byLabel('Sửa sản phẩm CPU'))
  await click(button('Danh mục sau'))
  assert.deepEqual(pages, [0, 1, 0])
  const picker = container.querySelector('#product-category')
  assert.equal(picker.value, product.categoryId)
  assert.ok(picker.querySelector('option[value="8"]'))
  assert.equal(button('Danh mục sau'), undefined)
})

test('Product category lookup retries a transient failure without retrying a permission denial', async () => {
  let status = 0
  let attempts = 0
  const categoryApi = { async list() {
    attempts++
    if (attempts === 1 || status === 403) throw { status }
    return categories.list()
  } }
  await render(['PRODUCT_UPDATE'], [], categoryApi)
  await click(byLabel('Sửa sản phẩm CPU'))
  assert.match(container.querySelector('dialog [role=alert]').textContent, /Không thể kết nối/)
  await click(button('Thử lại'))
  assert.equal(attempts, 2)
  assert.ok(container.querySelector('#product-category option[value="7"]'))
  status = 403
  await change('dialog input[maxlength="120"]', 'restricted')
  assert.match(container.querySelector('dialog [role=alert]').textContent, /Truy cập bị từ chối/)
  assert.equal(button('Thử lại'), undefined)
  assert.equal(attempts, 3)
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
  await render(allWrites)
  await click(byLabel('Sửa sản phẩm CPU'))
  assert.equal(container.querySelector('dialog [name=sellingPrice]'), null)
  await change('dialog [name=name]', 'Sản phẩm mới')
  await submit()
  assert.equal(calls.find(([action]) => action === 'update')[2].name, 'Sản phẩm mới')
  assert.equal(container.querySelector('dialog'), null)
  await click(byLabel('Đổi giá bán CPU'))
  assert.equal(container.querySelector('dialog [name=sku]'), null)
  const priceField = container.querySelector('dialog [name=sellingPrice]')
  assert.equal(priceField.hasAttribute('maxlength'), false)
  for (const invalid of ['1.25', '1e6', '0001', '99999999999999999999']) {
    await change('dialog [name=sellingPrice]', invalid)
    await submit()
    assert.equal(priceField.value, invalid)
    assert.equal(priceField.getAttribute('aria-invalid'), 'true')
    assert.equal(calls.some(([action]) => action === 'price'), false)
  }
  await change('dialog [name=sellingPrice]', '9223372036854775808')
  await submit()
  assert.deepEqual(calls.find(([action]) => action === 'price'), ['price', product.id, '9223372036854775808'])
})

test('Deactivation requires confirmation and 409 remains in the dialog', async () => {
  let attempts = 0
  client.deactivate = async () => { attempts++; throw { status: 409 } }
  await render(allWrites)
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
  await render(allWrites)
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
