import assert from 'node:assert/strict'
import test from 'node:test'
import { createCategoryClient } from './categories.js'
import { createCustomerClient } from './customers.js'
import { createProductClient } from './products.js'
import { createStaffClient } from './staff.js'
import { createSupplierClient } from './suppliers.js'

const emptyPage = { items: [], page: 2, size: 20, totalElements: 0, totalPages: 0 }

for (const [name, createClient] of Object.entries({
  category: createCategoryClient,
  customer: createCustomerClient,
  product: createProductClient,
  staff: createStaffClient,
  supplier: createSupplierClient,
})) {
  test(`${name} accepts empty out-of-range pages and rejects invalid pagination responses`, async () => {
    const empty = await createClient(async () => Response.json(emptyPage)).list({ page: 2 })
    assert.deepEqual(empty.items, [])
    assert.equal(empty.page, 2)
    assert.equal(empty.totalPages, 0)

    for (const changes of [
      { page: 1 }, { size: 10 }, { size: 101 }, { page: '2' }, { totalElements: -1 },
      { totalPages: Number.MAX_SAFE_INTEGER + 1 }, { items: Array(21).fill(null) },
    ]) {
      const client = createClient(async () => Response.json({ ...emptyPage, ...changes }))
      await assert.rejects(client.list({ page: 2 }), { status: 502 })
    }
  })
}
