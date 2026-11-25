# Product Management V1 — CAT-002

The six routes below require an authenticated session and the listed server permissions. The signed-in shell exposes `/products` and reports real 401/403 responses. The list response includes `allowedActions` for the current user, drawn from the UPDATE, PRICE_UPDATE, and DEACTIVATE grants. The UI uses these hints to enable each control. Every mutation independently checks its permission and CSRF. The create form remains a preview while DEP-001 is open.

| Method and path | Server permission | Behavior when released |
|---|---|---|
| GET `/api/v1/products` | `PRODUCT_READ` | 200. Optional `q`, `active`, `categoryId`. Case-normalized SKU and collation-based name, brand, model search. Literal search punctuation. Zero-based `page`, default `size=20`, maximum 100, ordered by name then ID. Returns `items/page/size/totalElements/totalPages/allowedActions`. |
| GET `/api/v1/products/{id}` | `PRODUCT_READ` | 200 or 404. |
| POST `/api/v1/products` | `PRODUCT_CREATE` and `PRODUCT_PRICE_UPDATE` | Accepts `{information, sellingPrice}`. Validates input, the active Category and SKU uniqueness. Currently stops with 409 before inserting either Product or Inventory. The intended success contract after DEP-001 is 201 with Location. |
| PUT `/api/v1/products/{id}` | `PRODUCT_UPDATE` | 200. Replaces information only. Leaves price, active state and creation timestamp unchanged. |
| PUT `/api/v1/products/{id}/price` | `PRODUCT_PRICE_UPDATE` | 200. Accepts only `{sellingPrice}`. Changes the current price and update timestamp. |
| POST `/api/v1/products/{id}/deactivate` | `PRODUCT_DEACTIVATE` | 200. Marks the Product inactive and preserves all references. Repeated deactivation is a no-op. |

`information` consists of `sku`, `name`, `categoryId`, optional `brandName`, `modelCode`, `description`, and `minStockLevel`. Use decimal strings for IDs, whole VND prices (0 to 19 digits of precision), and the nonnegative signed-BIGINT stock threshold. Pagination metadata uses JSON numbers. Product responses contain these fields plus `id`, `categoryName`, `sellingPrice`, `active`, `createdAt`, and `updatedAt` in UTC. The UI formats timestamps in Vietnam time. No Supplier reference is present on Product V1. No delete, reactivate, stock balance editing, purchase prices, or history rewrites are exposed.

Invalid payloads or unsupported write fields return 400. Missing Product or Category returns 404. Duplicate SKU and inactive target Category return 409. Writes are transactional. Information changes lock the target Category before the Product to coordinate with Category deactivation. CSRF and session authentication remain in force. Physical locking under concurrent Product and Category writes has not been verified.

Product writes refresh the locked row before changing it. Information updates cannot overwrite a price or active status committed since an earlier read in the same transaction. Reassignment also refreshes the locked target Category before checking activity.

On create and price update, `sellingPrice` must be a JSON string in canonical whole VND, from `"0"` through `"9999999999999999999"` (`DECIMAL(19,0)`). Numeric tokens, fractional or exponent notation, and out-of-range values return a `sellingPrice` validation error. The server does not round or coerce prices.

## Approved Product permission rollout V1

Approval applies to this five-code subset and these seven pairs. It does not change the meaning of the global catalog status or approve other slices.

| Role | Approved Product permissions |
|---|---|
| MANAGER | `PRODUCT_READ`, `PRODUCT_CREATE`, `PRODUCT_UPDATE`, `PRODUCT_PRICE_UPDATE`, `PRODUCT_DEACTIVATE` |
| SALES_STAFF | `PRODUCT_READ` |
| WAREHOUSE_STAFF | `PRODUCT_READ` |
| ADMIN | None |

Warehouse CREATE and UPDATE have been withdrawn. Initial pricing requires both `PRODUCT_CREATE` and `PRODUCT_PRICE_UPDATE`. Even an authorized MANAGER cannot create a Product before DEP-001 is integrated.

V4 registers the five codes with `name=code` and `description=NULL`. V5 inserts the seven approved pairs. They are compiled as opt-in Java migrations outside normal discovery. The local operator confirmed both versions and the five-permission, seven-grant read-back before releasing the routes. See the [Product rollout runbook](authorization-runtime-permission-rollout.md#15-product-permission-rollout-v1-v4v5) for commands, history requirements, and postchecks.

The isolated HTTP tests exercise the six Product method/path combinations through the application filter chain and service guards. Other API boundaries and CSRF rules continue to apply.

**DEP-001 handoff to TV2.** The existing Inventory repository has no approved initialization use case. Agree on the owner and API for creating exactly one zero-balance Inventory row in the Product create transaction. Confirm lock order, failure propagation, and the missing/duplicate-row contract. Verify success and rollback in a disposable database before replacing the current creation block. No stock or ledger changes are part of this checkpoint.
