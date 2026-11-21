import assert from 'node:assert/strict'
import { after, before, beforeEach, afterEach, test } from 'node:test'
import { JSDOM } from 'jsdom'
import { createServer } from 'vite'
import { createElement, act } from 'react'
import { createRoot } from 'react-dom/client'

// Test-only sessions/fetch and dialog adapter. No real account or HTTP request is used.
let server, dom, App, ThemeProvider, root, container, authenticated, requests
let modules
let viewportWidth = 1440
const mediaQueries = new Map()
before(async () => {
  dom = new JSDOM('<!doctype html><html><body></body></html>', { url: 'http://localhost:5173' })
  for (const name of ['window', 'document', 'HTMLElement', 'localStorage']) {
    Object.defineProperty(globalThis, name, { configurable: true, value: dom.window[name] })
  }
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
    if (url.endsWith('/logout')) { authenticated = false; return new Response(null, { status: 204 }) }
    if (url.startsWith('/api/v1/categories')) return Response.json({ code: 'ACCESS_DENIED' }, { status: 403 })
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
  authenticated = false
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

test('anonymous renders Login only; a URL fragment cannot bypass auth', async () => {
  window.location.hash = '#inventory'
  await render()
  assert.ok(container.querySelector('#username'))
  assert.equal(container.querySelector('[data-app-shell]'), null)
  assert.equal(requests.some(request => !/\/auth\/(me|csrf)$/.test(request.url)), false)
})

test('me-confirmed user sees shell and navigation changes only coming-soon content', async () => {
  authenticated = true
  await render()
  assert.ok(container.querySelector('[data-app-shell]'))
  assert.ok(container.querySelector('.csm-username').textContent.includes('test-employee'))
  assert.equal(container.querySelector('#password'), null)
  await click(container.querySelector('aside [data-nav-id="inventory"]'))
  assert.equal(container.querySelector('#page-title').textContent, '\u004bho h\u00e0ng')
  assert.equal(container.querySelector('aside [aria-current="page"]').dataset.navId, 'inventory')
  assert.match(container.querySelector('.csm-empty').textContent, /Chưa|chưa/)
  assert.ok(requests.every(request => request.url.includes('/auth/')))
  assert.equal(container.textContent.includes('Admin'), false)
  assert.equal(container.textContent.includes('test-token'), false)
})

test('theme toggles persist across shell navigation', async () => {
  authenticated = true
  await render()
  await click(container.querySelector('.theme-toggle'))
  assert.equal(document.documentElement.dataset.theme, 'dark')
  assert.equal(localStorage.getItem('csm-theme'), 'dark')
  await click(container.querySelector('aside [data-nav-id="reports"]'))
  assert.equal(document.documentElement.dataset.theme, 'dark')
  await click(container.querySelector('.theme-toggle'))
  assert.equal(document.documentElement.dataset.theme, 'light')
})

test('Category navigation is behind me, handles denyAll without fake data and preserves theme/logout', async () => {
  authenticated = true
  await render()
  await click(container.querySelector('aside [data-nav-id="categories"]'))
  assert.equal(container.querySelector('#page-title').textContent, 'Quản lý danh mục')
  assert.match(container.querySelector('.category-page [role=alert]').textContent, /Truy cập bị từ chối/)
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
  await click(dialog.querySelector('[data-nav-id="sales"]'))
  assert.equal(dialog.open, false)
  assert.equal(container.querySelector('#page-title').textContent, 'Bán hàng')
  assert.equal(document.activeElement, container.querySelector('main'))
  await click(opener)
  assert.equal(dialog.querySelector('[aria-current="page"]').dataset.navId, 'sales')
})

test('desktop hamburger collapses and expands one sidebar, preserves selection and never opens a drawer', async () => {
  authenticated = true
  await render()
  const shell = container.querySelector('[data-app-shell]')
  const toggle = container.querySelector('.csm-menu-toggle')
  assert.equal(toggle.getAttribute('aria-expanded'), 'true')
  assert.equal(toggle.getAttribute('aria-controls'), 'application-navigation')
  assert.equal(toggle.hasAttribute('aria-haspopup'), false)
  assert.equal(container.querySelector('dialog'), null)
  await click(container.querySelector('[data-nav-id="inventory"]'))
  await click(toggle)
  assert.equal(shell.classList.contains('csm-shell--collapsed'), true)
  assert.equal(toggle.getAttribute('aria-label'), 'Mở rộng điều hướng')
  assert.equal(toggle.getAttribute('aria-expanded'), 'false')
  assert.equal(container.querySelectorAll('nav').length, 1)
  assert.equal(container.querySelectorAll('.csm-nav-label').length, 0)
  assert.equal(container.querySelector('[aria-current="page"]').dataset.navId, 'inventory')
  for (const button of container.querySelectorAll('[data-nav-id]')) {
    assert.ok(button.getAttribute('aria-label'))
    assert.equal(button.title, button.getAttribute('aria-label'))
    assert.ok(button.querySelector('svg[aria-hidden="true"] path').getAttribute('d'))
  }
  await click(toggle)
  assert.equal(shell.classList.contains('csm-shell--collapsed'), false)
  assert.equal(container.querySelectorAll('[data-nav-id]').length, 15)
  assert.equal(container.querySelectorAll('.csm-nav-label').length, 15)
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

test('theme and selection survive sidebar toggles and mobile navigation without extra auth or business requests', async () => {
  authenticated = true
  await render()
  const startupRequests = requests.length
  await click(container.querySelector('.theme-toggle'))
  await click(container.querySelector('.csm-menu-toggle'))
  await click(container.querySelector('[data-nav-id="reports"]'))
  await resizeViewport(900)
  assert.equal(document.documentElement.dataset.theme, 'dark')
  assert.equal(container.querySelector('[aria-current="page"]').dataset.navId, 'reports')
  await resizeViewport(390)
  await click(container.querySelector('.csm-menu-toggle'))
  assert.equal(container.querySelector('dialog [aria-current="page"]').dataset.navId, 'reports')
  await click(container.querySelector('dialog [data-nav-id="customers"]'))
  assert.equal(container.querySelector('#page-title').textContent, 'Khách hàng')
  assert.equal(document.documentElement.dataset.theme, 'dark')
  assert.equal(localStorage.length, 1)
  assert.equal(localStorage.getItem('csm-theme'), 'dark')
  assert.equal(requests.length, startupRequests)
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
