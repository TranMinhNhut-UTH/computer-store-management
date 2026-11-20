import assert from 'node:assert/strict'
import { after, before, beforeEach, afterEach, test } from 'node:test'
import { JSDOM } from 'jsdom'
import { createServer } from 'vite'
import { createElement, act } from 'react'
import { createRoot } from 'react-dom/client'

// Test-only sessions/fetch and dialog adapter. No real account or HTTP request is used.
let server, dom, App, ThemeProvider, root, container, authenticated, requests
let modules
before(async () => {
  dom = new JSDOM('<!doctype html><html><body></body></html>', { url: 'http://localhost:5173' })
  for (const name of ['window', 'document', 'HTMLElement', 'localStorage']) {
    Object.defineProperty(globalThis, name, { configurable: true, value: dom.window[name] })
  }
  globalThis.IS_REACT_ACT_ENVIRONMENT = true
  globalThis.requestAnimationFrame = callback => setTimeout(callback, 0)
  window.matchMedia = () => ({ matches: false, addEventListener() {}, removeEventListener() {} })
  dom.window.HTMLDialogElement.prototype.showModal = function () { this.setAttribute('open', '') }
  dom.window.HTMLDialogElement.prototype.close = function () { this.removeAttribute('open') }
  globalThis.fetch = async (url, options = {}) => {
    requests.push({ url, options })
    if (url.endsWith('/csrf')) return Response.json({ headerName: 'X-CSRF-TOKEN', token: 'test-token' })
    if (url.endsWith('/me')) return Response.json(authenticated ? { userId: '42', username: 'test-employee' } : { code: 'UNAUTHENTICATED' }, { status: authenticated ? 200 : 401 })
    if (url.endsWith('/logout')) { authenticated = false; return new Response(null, { status: 204 }) }
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

test('drawer opens, focuses close, Escape closes and restores focus', async () => {
  authenticated = true
  await render()
  const opener = container.querySelector('.csm-menu-toggle')
  opener.focus()
  await click(opener)
  const dialog = container.querySelector('dialog')
  assert.equal(dialog.open, true)
  assert.equal(document.activeElement, container.querySelector('.csm-close-menu'))
  await act(async () => dialog.dispatchEvent(new window.KeyboardEvent('keydown', { key: 'Escape', bubbles: true })))
  assert.equal(dialog.open, false)
  assert.equal(document.activeElement, opener)
  assert.equal(document.body.style.overflow, '')
})

test('drawer close button, backdrop and navigation close the drawer', async () => {
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
  assert.equal(container.querySelector('aside [aria-current="page"]').dataset.navId, 'sales')
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
