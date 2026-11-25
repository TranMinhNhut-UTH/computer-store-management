import assert from 'node:assert/strict'
import { after, afterEach, before, beforeEach, test } from 'node:test'
import { JSDOM } from 'jsdom'
import { createServer } from 'vite'
import { act, createElement } from 'react'
import { createStaffClient } from '../../api/staff.js'

const employee = { id: '9007199254740993', username: 'staff.one', fullName: 'Nhân viên', email: null, phone: null,
  active: true, roles: ['SALES_STAFF'], updatedAt: '2026-11-23T00:00:00Z' }
const writes = ['EMPLOYEE_CREATE', 'EMPLOYEE_UPDATE', 'EMPLOYEE_ROLE_ASSIGN', 'EMPLOYEE_LOCK', 'EMPLOYEE_UNLOCK']
const page = { items: [employee], page: 0, size: 20, totalPages: 1, totalElements: 1, allowedActions: writes }
let dom, vite, StaffPage, createRoot, container, root, client, calls

before(async () => {
  dom = new JSDOM('<!doctype html><html><body></body></html>', { url: 'http://localhost:5173/employees' })
  for (const name of ['window', 'document', 'HTMLElement']) {
    Object.defineProperty(globalThis, name, { configurable: true, value: dom.window[name] })
  }
  globalThis.IS_REACT_ACT_ENVIRONMENT = true
  globalThis.fetch = async () => { throw new Error('Network disabled for staff UI tests') }
  ;({ createRoot } = await import('react-dom/client'))
  dom.window.HTMLDialogElement.prototype.showModal = function () { this.open = true }
  dom.window.HTMLDialogElement.prototype.close = function () { this.open = false }
  vite = await createServer({ server: { middlewareMode: true, hmr: false }, appType: 'custom' })
  StaffPage = (await vite.ssrLoadModule('/src/features/employees/StaffPage.jsx')).StaffPage
})
beforeEach(() => {
  calls = []
  client = {
    async list() { return page },
    async detail(id) { calls.push(['detail', id]); return employee },
    async create(values) { calls.push(['create', values]); return employee },
    async update(id, values) { calls.push(['update', id, values]); return employee },
    async assignRoles(id, roles) { calls.push(['roles', id, roles]); return employee },
    async setActive(id, active) { calls.push(['active', id, active]); return employee },
  }
  container = document.body.appendChild(document.createElement('div'))
  root = createRoot(container)
})
afterEach(async () => { await act(async () => root.unmount()); container.remove() })
after(async () => { await vite.close(); dom.window.close() })

const button = label => [...container.querySelectorAll('button')].find(node => node.textContent === label)
const labelled = label => [...container.querySelectorAll('button')].find(node => node.getAttribute('aria-label') === label)
async function render(currentUserId = '1') {
  await act(async () => root.render(createElement(StaffPage, { client, currentUserId })))
}
async function click(node) { assert.ok(node); await act(async () => node.click()) }
async function fill(name, value) {
  const node = container.querySelector('dialog [name="' + name + '"]')
  await act(async () => {
    Object.getOwnPropertyDescriptor(Object.getPrototypeOf(node), 'value').set.call(node, value)
    node.dispatchEvent(new window.Event('input', { bubbles: true }))
  })
}
async function submit() {
  await act(async () => container.querySelector('dialog form')
    .dispatchEvent(new window.Event('submit', { cancelable: true, bubbles: true })))
}

test('staff list honors permission hints and guards read-only details', async () => {
  client.list = async () => ({ ...page, allowedActions: ['EMPLOYEE_CREATE'] })
  await render()
  assert.equal(button('Tạo tài khoản'), undefined)
  assert.equal(labelled('Sửa staff.one'), undefined)
  assert.equal(labelled('Vai trò của staff.one'), undefined)
  assert.equal(labelled('Khóa staff.one'), undefined)
  await click(labelled('Xem staff.one'))
  assert.match(container.querySelector('dialog').textContent, /Nhân viên bán hàng/)
  assert.equal(container.querySelector('dialog form'), null)
})

test('staff creation validates field errors and clears the password after a server conflict', async () => {
  client.create = async values => { calls.push(['create', values]); throw { status: 409 } }
  await render()
  await click(button('Tạo tài khoản'))
  await submit()
  const username = container.querySelector('dialog [name=username]')
  assert.equal(document.activeElement, username)
  assert.equal(username.getAttribute('aria-invalid'), 'true')
  assert.equal(calls.length, 0)
  await fill('username', 'new.employee')
  await fill('fullName', 'New employee')
  await fill('password', 'fixture input')
  await click(container.querySelector('[value=MANAGER]'))
  await submit()
  assert.deepEqual(calls[0][1].roles, ['MANAGER'])
  assert.equal(container.querySelector('[name=password]').value, '')
  assert.match(container.querySelector('dialog [role=alert]').textContent, /Không thể lưu/)
})

test('editing, role assignment and lock confirmation are separate commands', async () => {
  await render()
  await click(labelled('Sửa staff.one'))
  assert.equal(container.querySelector('dialog [name=password]'), null)
  assert.equal(container.querySelector('dialog [name=roles]'), null)
  await fill('fullName', 'Đã sửa')
  await submit()
  assert.equal(calls.find(([kind]) => kind === 'update')[2].fullName, 'Đã sửa')
  await click(labelled('Vai trò của staff.one'))
  await click(container.querySelector('[value=MANAGER]'))
  await submit()
  assert.deepEqual(calls.find(([kind]) => kind === 'roles'), ['roles', employee.id, ['SALES_STAFF', 'MANAGER']])
  await click(labelled('Khóa staff.one'))
  assert.equal(calls.some(([kind]) => kind === 'active'), false)
  await click(button('Xác nhận khóa'))
  assert.deepEqual(calls.find(([kind]) => kind === 'active'), ['active', employee.id, false])
})

test('self lock is hidden and the administrator role cannot be unchecked for the current user', async () => {
  client.detail = async () => ({ ...employee, roles: ['ADMIN'] })
  await render(employee.id)
  assert.equal(labelled('Khóa staff.one'), undefined)
  await click(labelled('Vai trò của staff.one'))
  assert.equal(container.querySelector('[value=ADMIN]').disabled, true)
})

test('staff list reports loading, empty and access denial without stale controls', async () => {
  let finish
  client.list = () => new Promise(resolve => { finish = resolve })
  await render()
  assert.match(container.querySelector('[role=status]').textContent, /Đang tải/)
  await act(async () => finish({ ...page, items: [], totalElements: 0, totalPages: 0, allowedActions: [] }))
  assert.match(container.textContent, /Không có tài khoản phù hợp/)
  assert.equal(container.querySelector('table'), null)
  client = { ...client, async list() { throw { status: 403 } } }
  await render()
  assert.match(container.querySelector('[role=alert]').textContent, /không có quyền/)
  assert.equal(button('Thử lại'), undefined)
})

test('staff transport uses fresh CSRF and sends only the fields for each command', async () => {
  const requests = []
  const api = createStaffClient(async (url, options) => {
    requests.push([url, options])
    return url.endsWith('/csrf') ? Response.json({ headerName: 'X-CSRF-TOKEN', token: 'fixture-token' })
      : Response.json({ ...employee, passwordHash: 'never expose' })
  })
  const response = await api.update(employee.id, { fullName: 'Edit', active: false, roles: ['ADMIN'], password: 'ignore' })
  assert.equal('passwordHash' in response, false)
  await api.assignRoles(employee.id, ['MANAGER'])
  await api.setActive(employee.id, true)
  assert.equal(requests.filter(([url]) => url.endsWith('/csrf')).length, 3)
  const commands = requests.filter(([url]) => !url.endsWith('/csrf'))
  assert.deepEqual(JSON.parse(commands[0][1].body), { fullName: 'Edit', email: null, phone: null })
  assert.deepEqual(JSON.parse(commands[1][1].body), { roles: ['MANAGER'] })
  assert.ok(commands[2][0].endsWith('/unlock'))
  for (const [, options] of commands) {
    assert.equal(options.credentials, 'same-origin')
    assert.equal(options.headers['X-CSRF-TOKEN'], 'fixture-token')
  }
})
