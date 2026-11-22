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

## Product permission rollout readiness

All five `PRODUCT_*` codes above match the source catalog and are `ELIGIBLE_PROPOSED`. Registration and grants are still unapproved. The immutable, review-only source preserves the seven pairs supported by the current permission matrix. It rejects unknown or duplicate role codes, incomplete registration, unknown Product permissions, conflicting metadata and duplicate or unexpected Product mappings. It only returns missing rows to review and never writes them.

| Role | Proposed in the existing matrix | Additional request held for decision |
|---|---|---|
| MANAGER | READ, CREATE, UPDATE, PRICE_UPDATE, DEACTIVATE | None |
| SALES_STAFF | READ | None |
| WAREHOUSE_STAFF | READ | CREATE and UPDATE differ from the matrix and are excluded from the prepared payload. |
| ADMIN | None | None |

Confirm the Product read projection and the two disputed warehouse grants explicitly. Initial pricing still requires both `PRODUCT_CREATE` and `PRODUCT_PRICE_UPDATE`. Approval of warehouse CREATE alone would leave this create command denied. Even an authorized MANAGER cannot create a Product before DEP-001 is integrated.

**Stage A draft (V4):** Exactly the five Product permissions, with `name=code` and `description=NULL`. The manifest SHA-256 is
`f7f1eb15a362171740a64ac2ff1fe9b7732c0f13b6d8d2f132d109b05873b6af`.
This is a content fingerprint for review. Flyway versions and checksums will be set when the executable artifacts are approved.

**Stage B draft (V5):** Insert only the exact approved Product role/code pairs after successful registration. V4/V5 are the next candidates based on the inspected Product, main and Inventory branch sources. Recheck live Flyway history, V1–V3 success/checksums and version collisions before implementation and deployment. Category V2/V3 stay frozen. A future manual runner must account for their applied history during Flyway validation and require them to be applied already. Once Product rows or later versions exist, the current Category runner's strict Category-only preflight will reject them, so do not rerun it as part of Product rollout.

After explicit approval, package future migrations outside default Spring Boot discovery. Use the Category runner's opt-in pattern and freeze each version independently of the live permission catalog. In each stage, verify the target and InnoDB tables, lock and compare the relevant rows in one transaction, preserve compatible rows, and rely on the UNIQUE/FK constraints as the final guard. Unrelated permissions and mappings stay outside the Product write set. A failed precondition or insert aborts the entire stage. No executable V4/V5 or Product runner ships in this checkpoint.

**HTTP activation:** The exact six method/path combinations are exercised with the real filter chain and service guards in an isolated candidate configuration. The application builds that same filter chain with Product activation disabled. A reviewed release change is required after grants are approved and applied. Default denial of other APIs and CSRF behavior remain unchanged.

**DEP-001 handoff to TV2.** The existing Inventory repository has no approved initialization use case. Agree on the owner and API for creating exactly one zero-balance Inventory row in the Product create transaction. Confirm lock order, failure propagation, and the missing/duplicate-row contract. Verify success and rollback in a disposable database before replacing the current creation block. No stock or ledger changes are part of this checkpoint.
