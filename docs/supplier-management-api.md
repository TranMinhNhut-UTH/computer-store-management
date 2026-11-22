# Supplier Management V1

PAR-001 implements the five operations below. The current runtime denies these HTTP routes while Supplier grants await approval and deployment. The isolated HTTP candidate exercises the same filter chain with only the Supplier boundary enabled. The signed-in frontend route is `/suppliers`; it uses the real API and shows the server's access-denied result while the boundary is closed.

| Method and path | Required permission | Success |
|---|---|---|
| GET `/api/v1/suppliers` | `SUPPLIER_READ` | 200 paged list |
| GET `/api/v1/suppliers/{id}` | `SUPPLIER_READ` | 200 detail |
| POST `/api/v1/suppliers` | `SUPPLIER_CREATE` | 201 with Location |
| PUT `/api/v1/suppliers/{id}` | `SUPPLIER_UPDATE` | 200 updated information |
| POST `/api/v1/suppliers/{id}/deactivate` | `SUPPLIER_DEACTIVATE` | 200 inactive record |

IDs are decimal strings. List accepts optional `q` (code/name, literal wildcard characters), `active`, zero-based `page`, and `size` (1–100, default 20). Order is name then ID. The response contains `items`, `page`, `size`, `totalElements`, `totalPages` and `allowedActions`. Action hints are calculated for the signed-in actor from database grants; each mutation authorizes independently and requires CSRF.

Create and PUT accept `code`, `name`, `phone`, `email`, `address`, and `notes`. Code is normalized to uppercase ASCII (50 characters) and unique. Name is required (200 characters). Phone accepts common separators and at least one digit (30 characters). Email is optional, preserves case, and is nonunique (254 characters). Address allows 500 characters and notes fit the MySQL TEXT byte limit. Blank optional values become null. Responses add `id`, `active`, `createdAt`, and `updatedAt` (UTC). PUT leaves active and creation time intact. Validation returns 400, anonymous access 401, insufficient permission 403, missing records 404, and duplicate code or an open purchase draft 409.

## Purchasing boundary

Deactivation takes a write lock on the Supplier row before checking for open DRAFT purchases in a current locking read. A draft causes 409 and the supplier remains active. Existing CONFIRMED and CANCELLED purchases remain linked. Repeated deactivation is a no-op. V1 provides no delete or reactivation endpoint.

Purchasing owns its transactions. Draft creation, supplier reassignment, and confirmation must lock and recheck the relevant Supplier rows inside the write transaction. Lock suppliers in ascending ID order when more than one is involved, before locking Purchase and Inventory rows. If Purchasing reads a draft before acquiring those locks to learn the supplier ID, re-read the locked draft and confirm its supplier and state before writing. Stop or restart the transaction if either changed. Never treat the UI selection or an earlier nonlocking read as evidence that the supplier is still active.

The disposable MySQL test exercised both orderings using separate JDBC transactions. A Purchasing transaction waiting behind deactivation saw the supplier become inactive. Deactivation waiting behind draft creation saw the newly committed draft. TV2 still needs to implement and verify these checks in the Purchasing use cases.

## Permission rollout readiness

The matrix proposes MANAGER for `SUPPLIER_READ`, `SUPPLIER_CREATE`, `SUPPLIER_UPDATE`, and `SUPPLIER_DEACTIVATE`. WAREHOUSE_STAFF gets `SUPPLIER_READ` only. ADMIN and SALES_STAFF get zero Supplier grants. This remains a proposal.

| Version | Flyway SQL checksum | Required outcome |
|---|---:|---|
| V6 | `-1416705504` | Four Supplier permission rows with `name=code` and null descriptions, zero Supplier grants |
| V7 | `982398228` | Exactly the five pairs above, including zero ADMIN grants |

The SQL is under `backend/src/main/resources/operations/supplier/v1`, outside normal Flyway discovery. V6 requires exactly five successful V1–V5 history entries with the expected versions and checksums, and no existing Supplier catalog rows. V7 requires the successful V6 checksum, required roles, exact compatible Supplier metadata, and zero existing Supplier grants. Both stages use a single insert statement and fail on conflicting or duplicate state. A second normal Flyway invocation skips the successful migrations.

The opt-in integration test ran V1 and the actual V6/V7 SQL through Flyway on MySQL 8.0.46 in a disposable Windows instance with TCP disabled. It used test-only history markers for the released V2–V5 versions and checksums. Run from `backend` with:

~~~powershell
.\mvnw.cmd -o '-Dtest=SupplierPermissionRolloutIT' '-Dcsm.mysql.home=C:\Program Files\MySQL\MySQL Server 8.0' test
~~~

Before live rollout, obtain grant and execution approval, verify target and backup, and validate the actual V1–V5 history using an explicit Flyway runner that registers the original Category and Product Java migrations. The Supplier feature branch builds independently, but the live migration sequence depends on the released V1–V5 history. Target V6 first and read back its checksum, four rows, and zero grants. Then target V7 and read back its checksum and the exact five pairs. Stop on any mismatch.

After live V7 read-back, release the five Supplier routes by changing the final argument of `buildSupplierBoundary` to `true` in the application `SecurityConfig.apiSecurityFilterChain` bean. The default remains `false`. Keep the method/path allowlist, existing authentication and CSRF settings, and service-level permission checks. Verify the branch's isolated security tests and run live smoke checks before advancing PAR-001.
