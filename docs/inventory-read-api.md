# Inventory Read API V1

`INV-001` and `INV-002` expose read-only inventory balances and immutable movement history.

| Method and path | Success |
|---|---|
| GET `/api/v1/inventory/balances?page=0&size=20&filter=ALL` | Paginated active-Product balances |
| GET `/api/v1/inventory/movements?page=0&size=20&productId=42&type=PURCHASE_IN&from=2026-11-01T00:00:00&to=2026-12-01T00:00:00` | Paginated movement history |

`filter` accepts `ALL`, `LOW` and `OUT`. Low stock is `quantity_on_hand <= min_stock_level`, including zero. Out means exactly zero. Inactive Products are excluded from balance queries.

Movement filters are optional: Product ID, transaction type and half-open time range `[from, to)`. The response is decimal-string safe for quantities and includes recorded actor, source reference, reversal reference and audit timestamps.

TV1 confirmed the role mapping from `authorization-permission-matrix.md` §4.5: `INVENTORY_READ` for ADMIN, MANAGER, SALES_STAFF and WAREHOUSE_STAFF; `INVENTORY_LEDGER_READ` for MANAGER and WAREHOUSE_STAFF. Both routes require their respective server-side permission. This records the approved access policy, not proof that the role grants have been seeded in a runtime database. No endpoint creates missing inventory rows or modifies balances/history.

## Frontend

The local `/inventory` page provides a balance tab and a movement-history tab. It uses server-reported capabilities to show the navigation entry and each tab; client-side visibility is not an authorization boundary. Balance quantities remain decimal strings and are formatted without converting through JavaScript `Number`. Movement date inputs are UTC `LocalDateTime` filters; returned instants are displayed in `Asia/Ho_Chi_Minh`.

The backend UI capability endpoint now projects these permissions only when present in the current server-resolved grants, and the HTTP security allowlist admits authenticated GET requests to both routes. The service permission guard remains authoritative. Runtime role-grant rollout and MySQL/live verification are still separate. Until those grants exist in the target database, requests can still receive `403`.
