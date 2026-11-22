# Product Management V1 — CAT-002

The controller and service contract below is covered by isolated tests. The HTTP security configuration still denies all Product routes in the running application pending approval and deployment of the Product grants. The signed-in shell exposes `/products`, reports real 401/403 responses, and disables write actions. The create form is a preview and never submits while DEP-001 is open.

| Method and path | Server permission | Behavior when released |
|---|---|---|
| GET `/api/v1/products` | `PRODUCT_READ` | 200. Optional `q`, `active`, `categoryId`. Case-normalized SKU and collation-based name, brand, model search. Literal search punctuation. Zero-based `page`, default `size=20`, maximum 100, ordered by name then ID. Returns `items/page/size/totalElements/totalPages`. |
| GET `/api/v1/products/{id}` | `PRODUCT_READ` | 200 or 404. |
| POST `/api/v1/products` | `PRODUCT_CREATE` and `PRODUCT_PRICE_UPDATE` | Accepts `{information, sellingPrice}`. Validates input, the active Category and SKU uniqueness. Currently stops with 409 before inserting either Product or Inventory. The intended success contract after DEP-001 is 201 with Location. |
| PUT `/api/v1/products/{id}` | `PRODUCT_UPDATE` | 200. Replaces information only. Leaves price, active state and creation timestamp unchanged. |
| PUT `/api/v1/products/{id}/price` | `PRODUCT_PRICE_UPDATE` | 200. Accepts only `{sellingPrice}`. Changes the current price and update timestamp. |
| POST `/api/v1/products/{id}/deactivate` | `PRODUCT_DEACTIVATE` | 200. Marks the Product inactive and preserves all references. Repeated deactivation is a no-op. |

`information` consists of `sku`, `name`, `categoryId`, optional `brandName`, `modelCode`, `description`, and `minStockLevel`. Use decimal strings for IDs, whole VND prices (0 to 19 digits of precision), and the nonnegative signed-BIGINT stock threshold. Pagination metadata uses JSON numbers. Product responses contain these fields plus `id`, `categoryName`, `sellingPrice`, `active`, `createdAt`, and `updatedAt` in UTC. The UI formats timestamps in Vietnam time. No Supplier reference is present on Product V1. No delete, reactivate, stock balance editing, purchase prices, or history rewrites are exposed.

Invalid payloads or unsupported write fields return 400. Missing Product or Category returns 404. Duplicate SKU and inactive target Category return 409. Writes are transactional. Information changes lock the target Category before the Product to coordinate with Category deactivation. CSRF and session authentication remain in force. Physical locking under concurrent Product and Category writes has not been verified.

**Proposed grants only**, consistent with the Product slice of the permission matrix. The five codes are `ELIGIBLE_PROPOSED` and no Product grants are seeded. MANAGER would receive all five; SALES_STAFF and WAREHOUSE_STAFF would receive `PRODUCT_READ` only. ADMIN would receive none by default. The initial-price guard follows the conservative D02/D09 proposal and requires both permissions. Confirm the Product read projection and any equivalent price role before rollout.

**DEP-001 handoff to TV2.** The existing Inventory repository has no approved initialization use case. Agree on the owner and API for creating exactly one zero-balance Inventory row in the Product create transaction. Confirm lock order, failure propagation, and the missing/duplicate-row contract. Verify success and rollback in a disposable database before replacing the current creation block. No stock or ledger changes are part of this checkpoint.
