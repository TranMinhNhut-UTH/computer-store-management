# Basic Reports V1

`RPT-001` delivers the catalog and partner overview at `/reports`. It consolidates the same records and active-status filters already available through the approved list APIs. The overview does not query transactions, stock balances, customer records beyond aggregate counts, or contact fields.

| Method and path | Success |
|---|---|
| GET `/api/v1/reports/access` | `204`. Checks current permissions without running aggregate queries. The signed-in sidebar uses this response. |
| GET `/api/v1/reports/overview` | `200`. Returns the catalog and partner aggregates described below. |

Both operations require a current authenticated session, a server-resolved `MANAGER` role and **all four** existing read permissions. The server checks each request independently. Anonymous or expired sessions receive `401`. Authenticated sessions that lack the role or any required grant receive `403`. The API has no write operations.

| Role | Overview |
|---|---|
| MANAGER | Allowed with `CATEGORY_READ`, `PRODUCT_READ`, `SUPPLIER_READ` and `CUSTOMER_READ` |
| ADMIN | Denied without a qualifying Manager assignment and the required grants |
| SALES_STAFF | Denied without a qualifying Manager assignment and the required grants |
| WAREHOUSE_STAFF | Denied without a qualifying Manager assignment and the required grants |

The overview depends on the existing grants deployed by V1–V9 and introduces no permission or migration. The four proposed `REPORT_*` permissions remain inactive. Evaluate their Manager-only rollout separately when the corresponding report contracts and data sources are ready.

## Response and counts

- `products`, `categories`, `suppliers` and `customers` each contain numeric `total`, `active` and `inactive` counts. `total` includes both statuses and equals the sum of their counts.
- `productsByCategory` contains `{code, name, products}` rows with the same breakdown for products in each category. Rows include inactive categories and categories with no products. They are sorted by category code. Product status is independent of category status.
- Counts use fixed SQL aggregate queries in a read-only transaction. An empty database returns zero for each available count and an empty category list. Unavailable transaction metrics are omitted from the API and explicitly labeled unavailable on the page. The endpoint accepts no date or status filters and returns no per-customer rows.

## TV2 handoff and deferred metrics

| Pending reports | Readiness prerequisite |
|---|---|
| Net revenue, units sold and units returned | `DEP-008`, `SAL-001/002`, `PAY-001/002` and `RET-001`. Reconcile fixtures covering completed and canceled orders, confirmed returns and refunds. Apply the approved formula of completed-order revenue minus confirmed-return value, without subtracting cash refunds again. Establish the report period and timezone boundary under P07 before releasing a date filter. |
| Current stock, low-stock alerting and movement history | `INV-001/002` and `DEP-001`. Verify the Product-to-Inventory balance lifecycle and reconciled ledger data. The approved low-stock threshold is quantity on hand at or below `min_stock_level`. A missing balance cannot be read as zero. |

TV2 owns the source workflows and data reconciliation. TV1 owns the report projection, authorization and UI. Profit, bulk export and transaction drill-down are outside this delivery.

## Verification

`ReportsOverviewIT` uses disposable MySQL 8 with networking disabled. It validates empty counts, mixed statuses, deterministic grouping, empty groups and unchanged source rows. `ReportsSecurityTest` uses the real HTTP filter chain to verify anonymous denial, missing grants, non-Manager denial, successful access and read-only routes. The frontend tests cover real API response handling through controlled fixtures, pending and empty states, retry, authorization and the mobile drawer.

The browser smoke ran against Spring Boot, Vite and disposable MySQL fixture data. At 1440px and 375px, Manager saw Product, Category, Supplier and Customer totals of 3, 3, 2 and 1 and categories in A, B, Z order. The mobile drawer and dark theme worked, and neither viewport had horizontal page overflow. Anonymous overview received HTTP 401. Admin received HTTP 403 and had no Reports navigation entry.
