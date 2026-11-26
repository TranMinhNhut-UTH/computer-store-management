# Basic Reports V1

`RPT-001` delivers the catalog, partner and current Inventory reports at `/reports`. It consolidates the same records and active-status filters already available through the approved list APIs. Totals and the Product-by-Category breakdown use a single database statement, so concurrent writes cannot put them on different snapshots. The catalog and partner overview retains its original response. It reads aggregate counts and excludes stock balances, transaction data, and customer contacts.

| Method and path | Success |
|---|---|
| GET `/api/v1/reports/access` | `204`. Checks current permissions without running aggregate queries. The signed-in sidebar uses this response. |
| GET `/api/v1/reports/overview` | `200`. Returns the catalog and partner aggregates described below. |
| GET `/api/v1/reports/inventory?page=0&size=20` | `200`. Returns the current stock summary and paginated low-stock Products. |

All report routes require a current authenticated session, a server-resolved `MANAGER` role and **all four** existing read permissions. The server checks each request independently. Anonymous or expired sessions receive `401`. Authenticated sessions that lack the role or any required grant receive `403`. The API has no write operations.

| Role | Overview |
|---|---|
| MANAGER | Allowed with `CATEGORY_READ`, `PRODUCT_READ`, `SUPPLIER_READ` and `CUSTOMER_READ` |
| ADMIN | Denied without a qualifying Manager assignment and the required grants |
| SALES_STAFF | Denied without a qualifying Manager assignment and the required grants |
| WAREHOUSE_STAFF | Denied without a qualifying Manager assignment and the required grants |

The reports use the existing grants deployed by V1–V9 and add no permission or migration. The catalog contains proposed `INVENTORY_READ`, `REPORT_INVENTORY_READ`, and `REPORT_LOW_STOCK_READ` codes. The Inventory report uses the existing MANAGER report policy and does not require or activate these codes.

## Response and counts

- `products`, `categories`, `suppliers` and `customers` each contain numeric `total`, `active` and `inactive` counts. `total` includes both statuses and equals the sum of their counts.
- `productsByCategory` contains `{code, name, products}` rows with the same breakdown for products in each category. Rows include inactive categories and categories with no products. They are sorted by category code. Product status is independent of category status.
- Counts use fixed SQL aggregate queries in a read-only transaction. An empty database returns zero for each available count and an empty category list. Unavailable transaction metrics are omitted from the API and explicitly labeled unavailable on the page. The endpoint accepts no date or status filters and returns no per-customer rows.

## Current Inventory and low-stock Products

`summary` contains `activeSkus`, `totalQuantity`, `outOfStockSkus` and `lowStockSkus`. All four metrics use active Products and `inventory.quantity_on_hand`. Low stock includes zero and equals the count at or below `min_stock_level`. The Product's active status determines eligibility independently of Category status. A missing balance for any active Product returns `409 BUSINESS_CONFLICT` for this route. The catalog and partner overview remains available.

The response also contains `items`, `page`, `size`, `totalElements` and `totalPages`. Each item contains `sku`, `productName`, `categoryName`, `quantityOnHand`, `minStockLevel`, and `stockStatus` (`OUT_OF_STOCK` or `LOW_STOCK`). Rows are sorted by SKU and Product ID. Paging is zero-based, with size 1–100 (default 20). Quantities and thresholds are decimal strings to preserve exact BIGINT values and larger totals. The summary, pagination totals and page share one MySQL statement snapshot.

## TV2 handoff and deferred metrics

| Pending reports | Readiness prerequisite |
|---|---|
| Net revenue, units sold and units returned | `DEP-008`, `SAL-001/002`, `PAY-001/002` and `RET-001`. Reconcile fixtures covering completed and canceled orders, confirmed returns and refunds. Apply the approved formula of completed-order revenue minus confirmed-return value, without subtracting cash refunds again. Establish the report period and timezone boundary under P07 before releasing a date filter. |
| Movement history | `INV-002` and reconciled movement/transaction data from TV2. Current balances and low-stock reports are implemented independently. |

TV2 owns the source workflows and data reconciliation. TV1 owns the report projection, authorization and UI. Profit, bulk export and transaction drill-down are outside this delivery.

## Verification

`ReportsOverviewIT` uses disposable MySQL 8 with networking disabled. It validates the catalog and partner aggregates plus active-only inventory, threshold boundaries, missing balances, exact large quantities, deterministic pagination and unchanged source rows. `ReportsSecurityTest` uses the real HTTP filter chain to verify anonymous denial, missing grants, non-Manager denial, successful access and read-only routes. The frontend tests cover real API response handling through controlled fixtures, pending and empty states, retry, authorization and the mobile drawer.

The prior catalog and partner browser smoke ran against Spring Boot, Vite and disposable MySQL fixture data. At 1440px and 375px, Manager saw Product, Category, Supplier and Customer totals of 3, 3, 2 and 1 and categories in A, B, Z order. The mobile drawer and dark theme worked, and neither viewport had horizontal page overflow. Anonymous overview received HTTP 401. Admin received HTTP 403 and had no Reports navigation entry.
