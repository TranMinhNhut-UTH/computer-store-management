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

Deactivation locks the Supplier row, checks for open DRAFT purchases, and marks the supplier inactive if none exist. Existing CONFIRMED and CANCELLED purchases remain linked. Repeated deactivation is a no-op. V1 provides no delete or reactivation endpoint. TV2 retains ownership of Purchasing. For draft creation, supplier reassignment and confirmation, Purchasing must lock the relevant Supplier row and verify it remains active inside its write transaction. Coordinate supplier/purchase lock ordering with TV2 before shared rollout. Reassign or cancel a linked draft through Purchasing before deactivation.

## Pending permission rollout

The matrix proposes MANAGER for all four SUPPLIER codes and WAREHOUSE_STAFF for READ only. ADMIN and SALES_STAFF receive no Supplier grants in this proposal. The immutable SQL is under `backend/src/main/resources/operations/supplier/v1`, outside normal Flyway discovery. V6 registers exactly four codes using `name=code` and null descriptions. V7 adds exactly five pairs. Both stages insert only; duplicate/conflicting baseline data requires review before running them.

V6/V7 are proposed versions to avoid the Product branch’s V4/V5. Supplier source has no Product dependency. Before making the scripts executable in a rollout, approve the matrix, establish the shared migration sequence and history, verify the target, backup and zero existing Supplier permission/grant rows, and confirm statements against disposable MySQL with strict mode and InnoDB. Register the migrations explicitly outside normal application startup, verify four permissions before applying grants, then verify five pairs before releasing the HTTP boundary. No database migration is run by this checkpoint.
