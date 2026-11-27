import assert from 'node:assert/strict'
import { after, afterEach, before, beforeEach, test } from 'node:test'
import { JSDOM } from 'jsdom'
import { createServer } from 'vite'
import { act, createElement } from 'react'

let dom, server, createRoot, root, container, InventoryPage, calls, client

before(async () => {
  dom = new JSDOM('<!doctype html><html><body></body></html>', { url: 'http://localhost:5173/inventory' })
  for (const name of ['window', 'document', 'HTMLElement']) {
    Object.defineProperty(globalThis, name, { configurable: true, value: dom.window[name] })
  }
  globalThis.IS_REACT_ACT_ENVIRONMENT = true
  ;({ createRoot } = await import('react-dom/client'))
  globalThis.fetch = async () => { throw new Error('Real network is forbidden') }
  server = await createServer({ server: { middlewareMode: true, hmr: false }, appType: 'custom' })
  InventoryPage = (await server.ssrLoadModule('/src/features/inventory/InventoryPage.jsx')).InventoryPage
})

beforeEach(() => {
  calls = []
  client = {
    async getBalances(query) {
      calls.push(['balances', query])
      return {
        items: [{ productId: '42', sku: 'CPU-42', productName: 'CPU', quantityOnHand: '9007199254740993',
          minStockLevel: '2', stockStatus: 'NORMAL' }],
        page: query.page, size: 20, totalElements: 1, totalPages: 1,
      }
    },
    async getMovements(query) {
      calls.push(['movements', query])
      return {
        items: [{ id: '90', productId: '42', sku: 'CPU-42', productName: 'CPU', transactionType: 'PURCHASE_IN',
          quantityDelta: '10', quantityAfter: '15', occurredAt: '2026-11-27T01:00:00Z', recordedBy: 'warehouse',
          sourceType: 'PURCHASE_ITEM', sourceId: '17', reversalOfTransactionId: null, reversalReason: null,
          note: null, createdAt: '2026-11-27T01:00:00Z' }],
        page: query.page, size: 20, totalElements: 1, totalPages: 1,
      }
    },
  }
  container = document.body.appendChild(document.createElement('div'))
  root = createRoot(container)
})

afterEach(async () => { await act(async () => root.unmount()); container.remove() })
after(async () => { await server.close(); dom.window.close() })

async function render(permissions = ['INVENTORY_READ', 'INVENTORY_LEDGER_READ']) {
  await act(async () => root.render(createElement(InventoryPage, { permissions, client })))
}

async function click(element) {
  assert.ok(element)
  await act(async () => { element.click(); await new Promise(resolve => setTimeout(resolve, 0)) })
}

async function change(selector, value) {
  const element = container.querySelector(selector)
  assert.ok(element)
  await act(async () => {
    const prototype = element.tagName === 'SELECT' ? window.HTMLSelectElement.prototype : window.HTMLInputElement.prototype
    Object.getOwnPropertyDescriptor(prototype, 'value').set.call(element, value)
    element.dispatchEvent(new window.Event(element.tagName === 'SELECT' ? 'change' : 'input', { bubbles: true }))
  })
}

test('balance tab formats exact quantities and filters low/out stock', async () => {
  await render()
  assert.match(container.querySelector('tbody').textContent, /9\.007\.199\.254\.740\.993/)
  assert.deepEqual(calls[0], ['balances', { page: 0, filter: 'ALL' }])

  await change('#inventory-balance-filter', 'LOW')
  assert.deepEqual(calls.at(-1), ['balances', { page: 0, filter: 'LOW' }])
})

test('movement filters apply together and display source with local timestamp', async () => {
  await render()
  await click(container.querySelector('#inventory-tab-movements'))
  assert.match(container.querySelector('tbody').textContent, /27\/11\/2026/)
  assert.match(container.querySelector('tbody').textContent, /Phiếu nhập #17/)

  await change('#movement-product', '42')
  await change('#movement-type', 'PURCHASE_IN')
  await change('#movement-from', '2026-11-01T00:00')
  await change('#movement-to', '2026-12-01T00:00')
  await click([...container.querySelectorAll('button')].find(button => button.textContent === 'Lọc lịch sử'))

  assert.deepEqual(calls.at(-1), ['movements', {
    page: 0, productId: '42', type: 'PURCHASE_IN', from: '2026-11-01T00:00', to: '2026-12-01T00:00',
  }])
})

test('movement filters reject IDs beyond the backend range before requesting', async () => {
  await render()
  await click(container.querySelector('#inventory-tab-movements'))
  const movementCalls = calls.filter(([name]) => name === 'movements').length
  await change('#movement-product', '9223372036854775808')
  await click([...container.querySelectorAll('button')].find(button => button.textContent === 'Lọc lịch sử'))

  assert.match(container.querySelector('[role=alert]').textContent, /vượt quá giới hạn/)
  assert.equal(container.querySelector('#movement-product').getAttribute('aria-invalid'), 'true')
  assert.equal(calls.filter(([name]) => name === 'movements').length, movementCalls)
})

test('movement rows show reversal details in readable language', async () => {
  const originalClient = client
  client = {
    ...originalClient,
    async getMovements(query) {
      const data = await originalClient.getMovements(query)
      return {
        ...data,
        items: [{
          ...data.items[0],
          sourceType: 'REVERSAL',
          sourceId: '89',
          reversalOfTransactionId: '89',
          reversalReason: 'Đơn bị hủy',
          note: 'Đã kiểm tra',
        }],
      }
    },
  }
  await render()
  await click(container.querySelector('#inventory-tab-movements'))

  const row = container.querySelector('tbody')
  assert.match(row.textContent, /Đảo movement #89/)
  assert.match(row.textContent, /Lý do: Đơn bị hủy/)
  assert.match(row.textContent, /Ghi chú: Đã kiểm tra/)
})

test('only tabs permitted by server capabilities are shown', async () => {
  await render(['INVENTORY_READ'])
  assert.equal(container.querySelectorAll('[role=tab]').length, 1)
  assert.equal(container.querySelector('#inventory-tab-movements'), null)
  assert.deepEqual(calls.map(([name]) => name), ['balances'])
})

test('inventory tabs support arrow-key navigation', async () => {
  await render()
  const balancesTab = container.querySelector('#inventory-tab-balances')
  await act(async () => balancesTab.dispatchEvent(new window.KeyboardEvent('keydown', {
    key: 'ArrowRight', bubbles: true, cancelable: true,
  })))

  assert.equal(container.querySelector('#inventory-tab-movements').getAttribute('aria-selected'), 'true')
})
