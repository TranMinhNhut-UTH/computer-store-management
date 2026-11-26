# Inventory Read API V1

`INV-001` and `INV-002` expose read-only inventory balances and immutable movement history.

| Method and path | Success |
|---|---|
| GET `/api/v1/inventory/balances?page=0&size=20&filter=ALL` | Paginated active-Product balances |
| GET `/api/v1/inventory/movements?page=0&size=20&productId=42&type=PURCHASE_IN&from=2026-11-01T00:00:00&to=2026-12-01T00:00:00` | Paginated movement history |

`filter` accepts `ALL`, `LOW` and `OUT`. Low stock is `quantity_on_hand <= min_stock_level`, including zero. Out means exactly zero. Inactive Products are excluded from balance queries.

Movement filters are optional: Product ID, transaction type and half-open time range `[from, to)`. The response is decimal-string safe for quantities and includes recorded actor, source reference, reversal reference and audit timestamps.

These routes use the proposed `INVENTORY_READ` and `INVENTORY_LEDGER_READ` permissions. They remain dependent on DEP-002 approval and rollout; the proposed permission codes do not activate or grant themselves. No endpoint creates missing inventory rows or modifies balances/history.
