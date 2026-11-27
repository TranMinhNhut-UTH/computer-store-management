import assert from 'node:assert/strict'
import { after, before, beforeEach, afterEach, test } from 'node:test'
import { JSDOM } from 'jsdom'
import { createServer } from 'vite'
import { createElement, act } from 'react'

// Test-only sessions/fetch and dialog adapter. No real account or HTTP request is used.
let server, dom, App, ThemeProvider, root, container, authenticated, requests, reportAccess, reportResponse, capabilities, categoryStatus, loginAccepted
let createRoot, passwordStatus, inventoryResponse, movementResponse
let productResponse, productStockResponse
let modules
let viewportWidth = 1440
const mediaQueries = new Map()
before(async () => {
  dom = new JSDOM('<!doctype html><html><body></body></html>', { url: 'http://localhost:5173' })
  for (const name of ['window', 'document', 'HTMLElement', 'localStorage', 'FormData']) {
    Object.defineProperty(globalThis, name, { configurable: true, value: dom.window[name] })
  }
  ;({ createRoot } = await import('react-dom/client'))
  globalThis.IS_REACT_ACT_ENVIRONMENT = true
  globalThis.requestAnimationFrame = callback => setTimeout(callback, 0)
  window.matchMedia = query => {
    if (!mediaQueries.has(query)) {
      const listeners = new Set()
      mediaQueries.set(query, {
        media: query,
        get matches() { return viewportWidth >= Number(query.match(/min-width:\s*(\d+)px/)[1]) },
        addEventListener(type, listener) { if (type === 'change') listeners.add(listener) },
        removeEventListener(type, listener) { if (type === 'change') listeners.delete(listener) },
        listeners,
      })
    }
    return mediaQueries.get(query)
  }
  dom.window.HTMLDialogElement.prototype.showModal = function () { this.setAttribute('open', '') }
  dom.window.HTMLDialogElement.prototype.close = function () { this.removeAttribute('open') }
  globalThis.fetch = async (url, options = {}) => {
    requests.push({ url, options })
    if (url.endsWith('/csrf')) return Response.json({ headerName: 'X-CSRF-TOKEN', token: 'test-token' })
    if (url.endsWith('/me')) return Response.json(authenticated ? { userId: '42', username: 'test-employee' } : { code: 'UNAUTHENTICATED' }, { status: authenticated ? 200 : 401 })
    if (url.endsWith('/login')) {
      if (!loginAccepted) return Response.json({ code: 'UNAUTHENTICATED' }, { status: 401 })
      authenticated = true
      return Response.json({ userId: '42', username: 'test-employee' })
    }
    if (url.endsWith('/logout')) { authenticated = false; return new Response(null, { status: 204 }) }
    if (url.endsWith('/change-password')) {
      if (passwordStatus !== 204) return Response.json({ code: 'VALIDATION_ERROR' }, { status: passwordStatus })
      authenticated = false
      return new Response(null, { status: 204 })
    }
    if (url.endsWith('/auth/ui-capabilities')) return Response.json({ permissions: capabilities })
    if (url.endsWith('/reports/access')) return reportAccess ? new Response(null, { status: 204 }) : Response.json({ code: 'ACCESS_DENIED' }, { status: 403 })
    if (url.endsWith('/reports/overview')) return reportResponse()
    if (url.startsWith('/api/v1/reports/inventory?')) return inventoryResponse(new URL(url, 'http://localhost').searchParams)
    if (url.startsWith('/api/v1/reports/inventory/products/') && productStockResponse) return productStockResponse(url)
    if (url.startsWith('/api/v1/reports/inventory/movements?')) {
      return movementResponse(new URL(url, 'http://localhost').searchParams)
    }
    if (url.startsWith('/api/v1/employees')) {
      return capabilities.includes('EMPLOYEE_READ')
        ? Response.json({ items: [], page: 0, size: 20, totalPages: 0, totalElements: 0, allowedActions: [] })
        : Response.json({ code: 'ACCESS_DENIED' }, { status: 403 })
    }
    if (url.startsWith('/api/v1/categories')) return Response.json({ code: categoryStatus === 401 ? 'UNAUTHENTICATED' : 'ACCESS_DENIED' }, { status: categoryStatus })
    if (url.startsWith('/api/v1/products') && productResponse) return productResponse(url)
    if (url.startsWith('/api/v1/products') || url.startsWith('/api/v1/suppliers') || url.startsWith('/api/v1/customers')) return Response.json({ code: 'ACCESS_DENIED' }, { status: 403 })
    throw new Error('Unexpected request')
  }
  server = await createServer({ server: { middlewareMode: true }, appType: 'custom' })
  App = (await server.ssrLoadModule('/src/App.jsx')).default
  ThemeProvider = (await server.ssrLoadModule('/src/theme/ThemeProvider.jsx')).ThemeProvider
  modules = {}
  for (const name of ['Button', 'TextInput', 'LoadingState']) modules[name] = (await server.ssrLoadModule(`/src/components/ui/${name}.jsx`))[name]
})
beforeEach(() => {
  requests = []
  window.history.replaceState(null, '', '/')
  authenticated = false
  categoryStatus = 403
  loginAccepted = true
  passwordStatus = 204
  reportAccess = false
  productResponse = null
  productStockResponse = null
  capabilities = ['CATEGORY_READ', 'PRODUCT_READ', 'SUPPLIER_READ', 'CUSTOMER_READ']
  reportResponse = () => Response.json({ code: 'ACCESS_DENIED' }, { status: 403 })
  inventoryResponse = params => Response.json({
    summary: { activeSkus: 0, totalQuantity: '0', outOfStockSkus: 0, lowStockSkus: 0 },
    items: [], page: Number(params.get('page')), size: 20, totalElements: 0, totalPages: 0,
  })
  movementResponse = params => Response.json({
    items: [], page: Number(params.get('page')), size: 20, totalElements: 0, totalPages: 0,
  })
  viewportWidth = 1440
  mediaQueries.clear()
  localStorage.clear()
  container = document.createElement('div')
  document.body.append(container)
  root = createRoot(container)
})
afterEach(async () => { await act(async () => root.unmount()); container.remove() })
after(async () => { await server.close(); dom.window.close() })
async function render(element = createElement(App)) {
  await act(async () => { root.render(createElement(ThemeProvider, null, element)); await new Promise(resolve => setTimeout(resolve, 20)) })
}
async function click(element) { assert.ok(element); await act(async () => { element.click(); await new Promise(resolve => setTimeout(resolve, 20)) }) }
async function resizeViewport(width) {
  await act(async () => {
    const previous = new Map([...mediaQueries].map(([query, media]) => [query, media.matches]))
    viewportWidth = width
    for (const [query, media] of mediaQueries) {
      if (previous.get(query) !== media.matches) media.listeners.forEach(listener => listener({ matches: media.matches, media: query }))
    }
    await new Promise(resolve => setTimeout(resolve, 20))
  })
}

async function fillPassword(name, value) {
  const input = container.querySelector(`input[name="${name}"]`)
  assert.ok(input)
  await act(async () => {
    Object.getOwnPropertyDescriptor(window.HTMLInputElement.prototype, 'value').set.call(input, value)
    input.dispatchEvent(new window.Event('input', { bubbles: true }))
  })
}

async function submitPassword() {
  await act(async () => container.querySelector('main form').dispatchEvent(
    new window.Event('submit', { bubbles: true, cancelable: true })))
}

test('Account Security works without Staff grants and signs out only after a successful change', async () => {
  authenticated = true
  capabilities = []
  await render()
  assert.equal(container.querySelector('[data-nav-id="employees"]'), null)
  await click(container.querySelector('[data-nav-id="account-security"]'))
  assert.equal(window.location.pathname, '/account-security')
  assert.equal(container.querySelector('#page-title').textContent, 'Bảo mật tài khoản')
  const current = ' fixture password '
  const next = ' ' + 'é'.repeat(35) + ' '
  await fillPassword('currentPassword', current)
  await fillPassword('newPassword', next + 'x')
  await fillPassword('confirmation', 'different')
  await submitPassword()
  assert.equal(container.querySelector('[name="newPassword"]').getAttribute('aria-invalid'), 'true')
  assert.equal(container.querySelector('[name="confirmation"]').getAttribute('aria-invalid'), 'true')
  assert.equal(requests.filter(request => request.url.endsWith('/change-password')).length, 0)

  passwordStatus = 400
  await fillPassword('newPassword', next)
  await fillPassword('confirmation', next)
  await submitPassword()
  assert.match(container.querySelector('main [role="alert"]').textContent, /Kiểm tra mật khẩu hiện tại/)
  assert.ok(container.querySelector('[data-app-shell]'))
  assert.equal(container.querySelector('[name="currentPassword"]').value, '')

  passwordStatus = 204
  await fillPassword('currentPassword', current)
  await fillPassword('newPassword', next)
  await fillPassword('confirmation', next)
  await submitPassword()
  const writes = requests.filter(request => request.url.endsWith('/change-password'))
  assert.equal(writes.length, 2) // Each write corresponds to an explicit submit.
  assert.deepEqual(JSON.parse(writes[1].options.body), { currentPassword: current, newPassword: next })
  assert.equal(writes[1].options.headers['X-CSRF-TOKEN'], 'test-token')
  assert.equal(writes[1].options.credentials, 'same-origin')
  assert.equal(container.querySelector('[data-app-shell]'), null)
  assert.match(container.querySelector('[role="status"]').textContent, /Đã đổi mật khẩu/)
  assert.equal(document.activeElement.id, 'username')
  assert.equal(window.location.pathname, '/account-security')
  assert.equal(requests.some(request => /\/auth\/(login|logout)$/.test(request.url)), false)
  assert.equal(Object.values(localStorage).some(value => value.includes(next) || value.includes(current)), false)
})

test('Account Security on a mobile route uses standard session recovery on a rejected change', async () => {
  authenticated = true
  viewportWidth = 375
  passwordStatus = 401
  window.history.replaceState(null, '', '/account-security')
  await render()
  assert.equal(container.querySelector('[data-app-shell]').dataset.viewport, 'mobile')
  await fillPassword('currentPassword', 'fixture current')
  await fillPassword('newPassword', 'fixture new')
  await fillPassword('confirmation', 'fixture new')
  await submitPassword()
  assert.match(container.querySelector('#auth-error').textContent, /Phiên đăng nhập đã kết thúc/)
  assert.equal(container.querySelector('[data-app-shell]'), null)
  assert.equal(requests.filter(request => request.url.endsWith('/change-password')).length, 1)
})

test('anonymous direct Category URL renders Login without requesting categories', async () => {
  window.history.replaceState(null, '', '/categories')
  await render()
  assert.ok(container.querySelector('#username'))
  assert.equal(container.querySelector('[data-app-shell]'), null)
  assert.equal(requests.some(request => !/\/auth\/(me|csrf)$/.test(request.url)), false)
  assert.equal(window.location.pathname, '/categories')
})

test('direct Category URL and a fresh mount both restore Category after the session check', async () => {
  authenticated = true
  window.history.replaceState(null, '', '/categories')
  await render()
  await act(async () => { root.unmount() })
  root = createRoot(container)
  await render()
  assert.equal(window.location.pathname, '/categories')
  assert.equal(container.querySelector('#page-title').textContent, 'Quản lý danh mục')
  assert.equal(container.querySelector('[aria-current="page"]').dataset.navId, 'categories')
  assert.equal(requests.filter(request => request.url.endsWith('/me')).length, 2)
})

test('Category links update the URL and browser Back and Forward restore the selected screen', async () => {
  authenticated = true
  window.history.replaceState(null, '', '/dashboard')
  await render()
  const link = container.querySelector('[data-nav-id="categories"]')
  assert.equal(link.getAttribute('href'), '/categories')
  await click(link)
  assert.equal(window.location.pathname, '/categories')
  async function travel(direction) {
    await act(async () => {
      const changed = new Promise(resolve => window.addEventListener('popstate', resolve, { once: true }))
      window.history[direction]()
      await changed
    })
  }
  await travel('back')
  assert.equal(container.querySelector('[aria-current="page"]').dataset.navId, 'overview')
  assert.equal(container.querySelector('.category-page'), null)
  await travel('forward')
  assert.equal(container.querySelector('[aria-current="page"]').dataset.navId, 'categories')
  assert.ok(container.querySelector('.category-page'))
})

test('direct Product navigation keeps the session gate and renders the denied state without sample data', async () => {
  window.history.replaceState(null, '', '/products')
  await render()
  assert.ok(container.querySelector('#username'))
  assert.equal(requests.some(request => request.url.startsWith('/api/v1/products')), false)
  authenticated = true
  await act(async () => root.unmount())
  root = createRoot(container)
  await render()
  assert.equal(container.querySelector('#page-title').textContent, 'Quản lý sản phẩm')
  assert.match(container.querySelector('.product-page [role=alert]').textContent, /Truy cập bị từ chối/)
  assert.equal(container.querySelector('.product-page table'), null)
})

test('the authenticated shell navigates between Category, Product, Supplier and Customer', async () => {
  authenticated = true
  window.history.replaceState(null, '', '/products')
  await render()
  assert.equal(container.querySelector('#page-title').textContent, 'Quản lý sản phẩm')
  await click(container.querySelector('aside [data-nav-id="suppliers"]'))
  assert.equal(window.location.pathname, '/suppliers')
  assert.equal(container.querySelector('#page-title').textContent, 'Quản lý nhà cung cấp')
  assert.match(container.querySelector('#supplier-page [role=alert]').textContent, /không có quyền/)
  await click(container.querySelector('aside [data-nav-id="customers"]'))
  assert.equal(window.location.pathname, '/customers')
  assert.equal(container.querySelector('#page-title').textContent, 'Quản lý khách hàng')
  assert.match(container.querySelector('#customer-page [role=alert]').textContent, /không có quyền/)
  await click(container.querySelector('aside [data-nav-id="categories"]'))
  assert.equal(container.querySelector('#page-title').textContent, 'Quản lý danh mục')
  for (const module of ['products', 'suppliers', 'customers', 'categories']) {
    assert.equal(requests.filter(request => request.url.startsWith('/api/v1/' + module)).length, 1)
  }
  assert.equal(requests.filter(request => request.url.endsWith('/me')).length, 1)
})

test('Product stock requests require the authenticated shell Reports capability and an open detail', async () => {
  authenticated = true
  window.history.replaceState(null, '', '/products')
  const record = { id: '9007199254740993', sku: 'CPU', name: 'Bộ xử lý', categoryId: '7', categoryName: 'Linh kiện',
    brandName: null, modelCode: null, description: null, sellingPrice: '100', minStockLevel: '2', active: true,
    createdAt: '2026-11-21T17:00:00Z', updatedAt: '2026-11-22T06:45:00Z' }
  productResponse = url => url.includes('?')
    ? Response.json({ items: [record], page: 0, size: 20, totalElements: 1, totalPages: 1 })
    : Response.json(record)
  const stockPath = '/api/v1/reports/inventory/products/' + record.id
  productStockResponse = () => Response.json({ productId: record.id, sku: record.sku, productName: record.name,
    quantityOnHand: '0', minStockLevel: '2', stockStatus: 'OUT_OF_STOCK' })

  for (const allowed of [false, true]) {
    reportAccess = allowed
    await render()
    assert.equal(requests.filter(request => request.url === stockPath).length, 0)
    await click(container.querySelector('[aria-label="Xem sản phẩm CPU"]'))
    assert.equal(requests.filter(request => request.url === stockPath).length, allowed ? 1 : 0)
    if (allowed) assert.match(container.querySelector('.product-stock').textContent, /Hết hàng/)
    else assert.equal(container.querySelector('.product-stock'), null)
    await act(async () => root.unmount())
    root = createRoot(container)
  }
})

test('sidebar uses server read grants and unfinished modules are absent from navigation', async () => {
  authenticated = true
  capabilities = ['CATEGORY_READ', 'PRODUCT_READ', 'CATEGORY_UPDATE']
  window.history.replaceState(null, '', '/inventory')
  await render()
  assert.ok(container.querySelector('.csm-username').textContent.includes('test-employee'))
  assert.deepEqual([...container.querySelectorAll('[data-nav-id]')].map(node => node.dataset.navId), ['overview', 'categories', 'products', 'account-security'])
  assert.equal(container.querySelector('#page-title').textContent, 'Kho hàng')
  assert.match(container.querySelector('.csm-empty').textContent, /Chưa|chưa/)
  assert.ok(requests.every(request => request.url.includes('/auth/') || request.url.endsWith('/reports/access')))
  assert.equal(container.querySelector('#password'), null)
  assert.equal(container.textContent.includes('test-token'), false)
})

test('signed-in account without read grants sees only overview even if an action is granted', async () => {
  authenticated = true
  capabilities = ['CATEGORY_UPDATE']
  await render()
  assert.deepEqual([...container.querySelectorAll('[data-nav-id]')].map(node => node.dataset.navId), ['overview', 'account-security'])
  assert.match(container.querySelector('main').textContent, /chưa có quyền/)
  assert.equal(requests.filter(request => request.url.endsWith('/ui-capabilities')).length, 1)
})

test('theme toggles persist across shell navigation', async () => {
  authenticated = true
  await render()
  await click(container.querySelector('.theme-toggle'))
  assert.equal(document.documentElement.dataset.theme, 'dark')
  assert.equal(localStorage.getItem('csm-theme'), 'dark')
  await click(container.querySelector('aside [data-nav-id="categories"]'))
  assert.equal(document.documentElement.dataset.theme, 'dark')
  await click(container.querySelector('.theme-toggle'))
  assert.equal(document.documentElement.dataset.theme, 'light')
})

test('Category navigation is behind me, handles 403 without fake data and preserves theme/logout', async () => {
  authenticated = true
  await render()
  await click(container.querySelector('aside [data-nav-id="categories"]'))
  assert.equal(container.querySelector('#page-title').textContent, 'Quản lý danh mục')
  assert.match(container.querySelector('.category-page [role=alert]').textContent, /Truy cập bị từ chối/)
  assert.doesNotMatch(container.querySelector('.category-page').textContent, /runtime grants|API hiện chưa được mở/)
  assert.equal(container.querySelector('table'), null)
  assert.equal(requests.filter(request => request.url.startsWith('/api/v1/categories')).length, 1)
  await click(container.querySelector('.theme-toggle'))
  assert.equal(document.documentElement.dataset.theme, 'dark')
  assert.equal(container.querySelectorAll('nav#application-navigation').length, 1)
  await click([...container.querySelectorAll('button')].find(button => button.textContent === 'Đăng xuất'))
  assert.ok(container.querySelector('#username'))
  assert.equal(container.querySelector('.category-page'), null)
})

test('drawer opens, focuses close, Escape closes and restores focus', async () => {
  await resizeViewport(390)
  authenticated = true
  await render()
  const opener = container.querySelector('.csm-menu-toggle')
  opener.focus()
  await click(opener)
  const dialog = container.querySelector('dialog')
  assert.equal(dialog.open, true)
  assert.equal(container.querySelector('aside'), null)
  assert.equal(container.querySelectorAll('nav').length, 1)
  assert.equal(document.body.style.overflow, 'hidden')
  assert.equal(document.activeElement, container.querySelector('.csm-close-menu'))
  await act(async () => dialog.dispatchEvent(new window.KeyboardEvent('keydown', { key: 'Escape', bubbles: true })))
  assert.equal(dialog.open, false)
  assert.equal(document.activeElement, opener)
  assert.equal(document.body.style.overflow, '')
  assert.equal(container.querySelectorAll('nav').length, 0)
})

test('drawer close button, backdrop and navigation close the drawer', async () => {
  await resizeViewport(390)
  authenticated = true
  await render()
  const opener = container.querySelector('.csm-menu-toggle')
  const dialog = container.querySelector('dialog')
  await click(opener)
  await click(container.querySelector('.csm-close-menu'))
  assert.equal(dialog.open, false)
  await click(opener)
  await click(dialog)
  assert.equal(dialog.open, false)
  await click(opener)
  await click(dialog.querySelector('[data-nav-id="suppliers"]'))
  assert.equal(dialog.open, false)
  assert.equal(container.querySelector('#page-title').textContent, 'Quản lý nhà cung cấp')
  assert.equal(document.activeElement, container.querySelector('main'))
  await click(opener)
  assert.equal(dialog.querySelector('[aria-current="page"]').dataset.navId, 'suppliers')
})

test('desktop hamburger collapses and expands one sidebar, preserves selection and never opens a drawer', async () => {
  authenticated = true
  reportAccess = true
  await render()
  const shell = container.querySelector('[data-app-shell]')
  const toggle = container.querySelector('.csm-menu-toggle')
  assert.equal(toggle.getAttribute('aria-expanded'), 'true')
  assert.equal(toggle.getAttribute('aria-controls'), 'application-navigation')
  assert.equal(toggle.hasAttribute('aria-haspopup'), false)
  assert.equal(container.querySelector('dialog'), null)
  await click(container.querySelector('[data-nav-id="products"]'))
  await click(toggle)
  assert.equal(shell.classList.contains('csm-shell--collapsed'), true)
  assert.equal(toggle.getAttribute('aria-label'), 'Mở rộng điều hướng')
  assert.equal(toggle.getAttribute('aria-expanded'), 'false')
  assert.equal(container.querySelectorAll('nav').length, 1)
  assert.equal(container.querySelectorAll('.csm-nav-label').length, 0)
  assert.equal(container.querySelector('[aria-current="page"]').dataset.navId, 'products')
  for (const button of container.querySelectorAll('[data-nav-id]')) {
    assert.ok(button.getAttribute('aria-label'))
    assert.equal(button.title, button.getAttribute('aria-label'))
    assert.ok(button.querySelector('svg[aria-hidden="true"] path').getAttribute('d'))
  }
  await click(toggle)
  assert.equal(shell.classList.contains('csm-shell--collapsed'), false)
  assert.equal(container.querySelectorAll('[data-nav-id]').length, 7)
  assert.equal(container.querySelectorAll('.csm-nav-label').length, 7)
  assert.equal(container.querySelector('nav').textContent.includes('Chưa triển khai'), false)
  assert.equal(container.querySelector('dialog'), null)
  assert.equal(document.body.style.overflow, '')
})

test('tablet defaults to compact sidebar and hamburger only toggles that sidebar', async () => {
  await resizeViewport(900)
  authenticated = true
  await render()
  const shell = container.querySelector('[data-app-shell]')
  const toggle = container.querySelector('.csm-menu-toggle')
  assert.equal(shell.dataset.viewport, 'tablet')
  assert.equal(shell.classList.contains('csm-shell--collapsed'), true)
  assert.equal(container.querySelector('dialog'), null)
  await click(toggle)
  assert.equal(shell.classList.contains('csm-shell--collapsed'), false)
  assert.equal(container.querySelectorAll('nav').length, 1)
  await click(toggle)
  assert.equal(shell.classList.contains('csm-shell--collapsed'), true)
  assert.equal(container.querySelector('dialog'), null)
})

test('breakpoints at 768 and 1024 switch navigation without duplicate landmarks', async () => {
  authenticated = true
  await render()
  for (const [width, viewport] of [[1024, 'desktop'], [1023, 'tablet'], [768, 'tablet'], [767, 'mobile']]) {
    await resizeViewport(width)
    assert.equal(container.querySelector('[data-app-shell]').dataset.viewport, viewport)
    assert.equal(container.querySelectorAll('nav').length, viewport === 'mobile' ? 0 : 1)
    assert.equal(container.querySelectorAll('aside').length, viewport === 'mobile' ? 0 : 1)
    assert.equal(container.querySelectorAll('dialog').length, viewport === 'mobile' ? 1 : 0)
  }
})

test('resizing an open mobile drawer restores scrolling and focus without reopening it on return', async () => {
  await resizeViewport(390)
  authenticated = true
  await render()
  document.body.style.overflow = 'auto'
  await click(container.querySelector('.csm-menu-toggle'))
  await click(container.querySelector('dialog [data-nav-id="products"]'))
  await click(container.querySelector('.csm-menu-toggle'))
  await resizeViewport(768)
  assert.equal(container.querySelector('dialog'), null)
  assert.equal(container.querySelectorAll('nav').length, 1)
  assert.equal(container.querySelector('[aria-current="page"]').dataset.navId, 'products')
  assert.equal(document.body.style.overflow, 'auto')
  assert.equal(document.activeElement, container.querySelector('main'))
  await resizeViewport(390)
  assert.equal(container.querySelector('dialog').open, false)
  assert.equal(container.querySelectorAll('nav').length, 0)
  document.body.style.overflow = ''
})

test('theme and selection survive sidebar toggles and mobile navigation without reloading grants or refetching on resize', async () => {
  authenticated = true
  await render()
  const startupRequests = requests.length
  await click(container.querySelector('.theme-toggle'))
  await click(container.querySelector('.csm-menu-toggle'))
  await click(container.querySelector('[data-nav-id="customers"]'))
  await resizeViewport(900)
  assert.equal(document.documentElement.dataset.theme, 'dark')
  assert.equal(container.querySelector('[aria-current="page"]').dataset.navId, 'customers')
  await resizeViewport(390)
  await click(container.querySelector('.csm-menu-toggle'))
  assert.equal(container.querySelector('dialog [aria-current="page"]').dataset.navId, 'customers')
  await click(container.querySelector('dialog [data-nav-id="overview"]'))
  assert.equal(container.querySelector('#page-title').textContent, 'Tổng quan')
  assert.equal(document.documentElement.dataset.theme, 'dark')
  assert.equal(localStorage.length, 1)
  assert.equal(localStorage.getItem('csm-theme'), 'dark')
  assert.equal(requests.length, startupRequests + 1)
  assert.equal(requests.filter(request => request.url.endsWith('/ui-capabilities')).length, 1)
})

test('logout keeps CSRF headers then returns to Login and refreshes token', async () => {
  authenticated = true
  await render()
  await click([...container.querySelectorAll('.csm-topbar button')].find(button => button.textContent.includes('Đăng xuất')))
  assert.equal(container.querySelector('[data-app-shell]'), null)
  assert.ok(container.querySelector('#username'))
  const index = requests.findIndex(request => request.url.endsWith('/logout'))
  assert.ok(index > 0)
  assert.ok(requests[index - 1].url.endsWith('/csrf'))
  assert.equal(requests[index].options.headers['X-CSRF-TOKEN'], 'test-token')
  assert.equal(requests[index].options.credentials, 'same-origin')
  assert.ok(requests[index + 1].url.endsWith('/csrf'))
})

test('shared input connects label/error; loading button is disabled and cannot trigger handler', async () => {
  let clicks = 0
  await render(createElement('div', null,
    createElement(modules.TextInput, { label: 'Test field', error: 'Required' }),
    createElement(modules.Button, { loading: true, onClick: () => clicks++ }, 'Save'),
    createElement(modules.LoadingState)))
  const input = container.querySelector('input')
  assert.equal(container.querySelector('label').htmlFor, input.id)
  assert.equal(input.getAttribute('aria-invalid'), 'true')
  assert.ok(document.getElementById(input.getAttribute('aria-describedby')))
  const button = container.querySelector('.csm-button')
  assert.equal(button.disabled, true)
  assert.equal(button.getAttribute('aria-busy'), 'true')
  await click(button)
  assert.equal(clicks, 0)
  assert.ok(container.querySelector('[role="status"]'))
})

test('reports remain hidden when the server denies access, even when opened directly', async () => {
  authenticated = true
  window.history.replaceState(null, '', '/reports')
  await render()
  assert.equal(container.querySelector('[data-nav-id="reports"]'), null)
  assert.match(container.querySelector('.reports-page [role=alert]').textContent, /không có quyền/)
  assert.equal(container.querySelector('.reports-kpi'), null)
})

test('authorized reports show loading then SQL response totals and remain reachable at 375px', async () => {
  authenticated = true
  reportAccess = true
  let complete
  reportResponse = () => new Promise(resolve => { complete = resolve })
  await render()
  await click(container.querySelector('[data-nav-id="reports"]'))
  assert.match(container.querySelector('.reports-page [role=status]').textContent, /Đang tải/)
  const none = { total: 0, active: 0, inactive: 0 }
  const products = { total: 3, active: 2, inactive: 1 }
  await act(async () => complete(Response.json({
    products, categories: { total: 1, active: 1, inactive: 0 }, suppliers: none, customers: none,
    productsByCategory: [{ code: 'A', name: 'Fixture Category', products }],
  })))
  assert.deepEqual([...container.querySelectorAll('.reports-kpi strong')].map(node => node.textContent), ['3', '1', '0', '0'])
  assert.match(container.querySelector('table').textContent, /Fixture Category/)
  assert.match(container.querySelector('.reports-page').textContent, /đang chờ tích hợp/)
  const request = requests.find(entry => entry.url.endsWith('/reports/overview'))
  assert.equal(request.options.credentials, 'same-origin')
  assert.equal(request.options.method, undefined)
  await resizeViewport(375)
  await click(container.querySelector('.csm-menu-toggle'))
  assert.equal(container.querySelector('dialog [data-nav-id="reports"]').getAttribute('aria-current'), 'page')
  assert.equal(container.querySelectorAll('.reports-kpi').length, 4)
})

test('reports recover from a network error and distinguish an empty database', async () => {
  authenticated = true
  reportAccess = true
  window.history.replaceState(null, '', '/reports')
  reportResponse = () => { throw new Error('unavailable') }
  await render()
  assert.match(container.querySelector('.reports-page [role=alert]').textContent, /Không thể tải/)
  const zero = { total: 0, active: 0, inactive: 0 }
  reportResponse = () => Response.json({
    products: zero, categories: zero, suppliers: zero, customers: zero, productsByCategory: [],
  })
  await click(container.querySelector('.reports-page button'))
  assert.match(container.querySelector('.reports-page .csm-empty').textContent, /Chưa có danh mục/)
  assert.match(container.querySelector('.reports-page').textContent, /Không có sản phẩm tồn thấp/)
  assert.match(container.querySelector('.reports-page').textContent, /Không có biến động kho được ghi nhận/)
  assert.equal(container.querySelectorAll('.reports-kpi').length, 4)
  assert.equal(container.querySelector('.reports-page table'), null)
})

test('inventory reports switch filters from a later page, preserve exact stock and isolate integrity errors', async () => {
  authenticated = true
  reportAccess = true
  window.history.replaceState(null, '', '/reports')
  const zero = { total: 0, active: 0, inactive: 0 }
  reportResponse = () => Response.json({
    products: zero, categories: zero, suppliers: zero, customers: zero, productsByCategory: [],
  })
  let conflict = false
  inventoryResponse = params => {
    const page = Number(params.get('page'))
    const filter = params.get('filter') ?? 'LOW'
    if (conflict) return Response.json({ code: 'BUSINESS_CONFLICT' }, { status: 409 })
    const low = index => ({
      sku: 'SKU-' + index, productName: 'Mặt hàng thử', categoryName: 'Danh mục thử',
      quantityOnHand: '9007199254740993', minStockLevel: '9007199254740993', stockStatus: 'LOW_STOCK',
    })
    const out = { ...low(0), sku: 'Z-EMPTY', quantityOnHand: '0', stockStatus: 'OUT_OF_STOCK' }
    const normal = {
      ...low(0), sku: 'N-NORMAL', quantityOnHand: String(9223372036854775810n - 20n * 9007199254740993n),
      minStockLevel: '0', stockStatus: 'NORMAL',
    }
    const lowItems = Array.from({ length: 20 }, (_, index) => low(index))
    const items = { ALL: [normal, ...lowItems, out], LOW: [...lowItems, out], OUT: [out] }[filter]
    return Response.json({
      summary: { activeSkus: 22, totalQuantity: '9223372036854775810', outOfStockSkus: 1, lowStockSkus: 21 },
      items: items.slice(page * 20, (page + 1) * 20),
      page, size: 20, totalElements: items.length, totalPages: Math.ceil(items.length / 20),
    })
  }
  await render()
  const region = container.querySelector('[aria-labelledby="reports-inventory"]')
  const select = region.querySelector('select')
  const filterBy = value => act(async () => {
    select.value = value
    select.dispatchEvent(new window.Event('change', { bubbles: true }))
  })
  assert.equal(select.value, 'LOW')
  assert.match(region.querySelector('.reports-inventory-metrics').textContent, /9\.223\.372\.036\.854\.775\.810/)
  assert.match(region.querySelector('tbody').textContent, /9\.007\.199\.254\.740\.993.*Tồn thấp/)
  assert.match(region.querySelector('tbody').textContent, /SKU-0.*Danh mục thử/)
  const button = label => [...region.querySelectorAll('button')].find(node => node.textContent === label)
  await click(button('Trang sau'))
  assert.match(region.querySelector('tbody').textContent, /Z-EMPTY.*Hết hàng/)
  assert.ok(requests.some(entry => entry.url.endsWith('/inventory?page=1&size=20')))
  await filterBy('ALL')
  assert.match(region.querySelector('tbody').textContent, /N-NORMAL.*Bình thường/)
  assert.match(region.querySelector('nav').textContent, /22 sản phẩm · Trang 1\/2/)
  assert.ok(requests.at(-1).url.endsWith('/inventory?page=0&size=20&filter=ALL'))
  await filterBy('OUT')
  assert.equal(region.querySelectorAll('tbody tr').length, 1)
  assert.match(region.querySelector('tbody').textContent, /Z-EMPTY.*Hết hàng/)
  assert.match(region.querySelector('nav').textContent, /1 sản phẩm hết hàng · Trang 1\/1/)
  assert.ok(requests.at(-1).url.endsWith('/inventory?page=0&size=20&filter=OUT'))
  conflict = true
  await filterBy('LOW')
  assert.match(region.querySelector('[role=alert]').textContent, /chưa có số dư tồn kho/)
  assert.equal(region.querySelector('.reports-inventory-metrics'), null)
  assert.equal(container.querySelectorAll('.reports-kpi').length, 4)
})

test('movement reports display signed quantities and apply Vietnam date filters from page one', async () => {
  authenticated = true
  reportAccess = true
  window.history.replaceState(null, '', '/reports')
  const zero = { total: 0, active: 0, inactive: 0 }
  reportResponse = () => Response.json({
    products: zero, categories: zero, suppliers: zero, customers: zero, productsByCategory: [],
  })
  movementResponse = params => {
    const page = Number(params.get('page'))
    const filtered = params.has('q')
    return Response.json({
      items: Array.from({ length: filtered ? 0 : page ? 1 : 20 }, () => ({
        sku: 'P_1', productName: 'Linh kiện', movementType: page ? 'PURCHASE_IN' : 'SALE_OUT',
        quantityDelta: page ? '9007199254740993' : '-9007199254740993', occurredAt: '2026-11-26T17:00:00Z',
      })),
      page, size: 20, totalElements: filtered ? 0 : 21, totalPages: filtered ? 0 : 2,
    })
  }
  await render()
  const region = container.querySelector('[aria-labelledby="reports-movements"]')
  assert.match(region.querySelector('tbody').textContent, /00:00 27\/11\/2026.*Xuất bán-9\.007\.199\.254\.740\.993/)
  const button = text => [...region.querySelectorAll('button')].find(node => node.textContent === text)
  await click(button('Trang sau'))
  assert.match(region.querySelector('tbody').textContent, /Nhập hàng\+9\.007\.199\.254\.740\.993/)
  const form = region.querySelector('form')
  form.elements.q.value = 'P_1'
  form.elements.type.value = 'SALE_OUT'
  form.elements.fromDate.value = '2026-11-28'
  form.elements.toDate.value = '2026-11-27'
  const before = requests.length
  await click(button('Áp dụng bộ lọc'))
  assert.equal(requests.length, before)
  assert.match(region.textContent, /Ngày kết thúc phải từ ngày bắt đầu/)
  form.elements.fromDate.value = '2026-11-27'
  await click(button('Áp dụng bộ lọc'))
  const params = new URL(requests.at(-1).url, 'http://localhost').searchParams
  assert.deepEqual(Object.fromEntries(params), {
    page: '0', size: '20', q: 'P_1', type: 'SALE_OUT', fromDate: '2026-11-27', toDate: '2026-11-27',
  })
  assert.match(region.textContent, /Không có biến động kho được ghi nhận/)
})

test('staff navigation and deep links respect the server capability and API denial', async () => {
  authenticated = true
  capabilities = ['EMPLOYEE_READ']
  await render()
  await click(container.querySelector('[data-nav-id=employees]'))
  assert.equal(window.location.pathname, '/employees')
  assert.match(container.querySelector('main').textContent, /Không có tài khoản phù hợp/)
  await act(async () => root.unmount())
  root = createRoot(container)
  capabilities = []
  await render()
  assert.equal(container.querySelector('[data-nav-id=employees]'), null)
  assert.match(container.querySelector('.staff-page [role=alert]').textContent, /không có quyền/)
})

test('a protected 401 returns to sign-in and a fresh login resumes the same route without retrying the request', async () => {
  authenticated = true
  categoryStatus = 401
  window.history.replaceState(null, '', '/categories')
  await render()
  assert.equal(container.querySelector('[data-app-shell]'), null)
  assert.equal(window.location.pathname, '/categories')
  assert.match(container.querySelector('#auth-error').textContent, /Phiên đăng nhập đã kết thúc/)
  assert.equal(document.activeElement, container.querySelector('#username'))
  assert.equal(requests.filter(entry => entry.url.startsWith('/api/v1/categories')).length, 1)
  assert.equal(requests.filter(entry => entry.url.endsWith('/logout')).length, 0)

  loginAccepted = false
  const submit = () => act(async () => container.querySelector('form').dispatchEvent(
    new window.Event('submit', { bubbles: true, cancelable: true })))
  await submit()
  assert.match(container.querySelector('#auth-error').textContent, /Không thể đăng nhập/)

  loginAccepted = true
  categoryStatus = 403
  await submit()
  assert.equal(window.location.pathname, '/categories')
  assert.ok(container.querySelector('[data-app-shell]'))
  assert.match(container.querySelector('.category-page [role=alert]').textContent, /Truy cập bị từ chối/)
  assert.equal(requests.filter(entry => entry.url.startsWith('/api/v1/categories')).length, 2)
})
