# Customer Management V1

`PAR-002` implements employee lookup, creation and information updates for customer records (FR-CUS-01–05). The signed-in screen is `/customers`. The release source enables the four authenticated routes below. Deployment requires verifying V8/V9 and the approved grants on the target. Each operation checks its current server-side permission, and writes still require CSRF.

| Method and path | Required permission | Success |
|---|---|---|
| GET `/api/v1/customers` | `CUSTOMER_READ` | 200 paginated list |
| GET `/api/v1/customers/{id}` | `CUSTOMER_READ` | 200 detail |
| POST `/api/v1/customers` | `CUSTOMER_CREATE` | 201 with `Location` |
| PUT `/api/v1/customers/{id}` | `CUSTOMER_UPDATE` | 200 core field update |

List accepts `q` (code or name, up to 200 characters, literal wildcard characters), `active` (optional status filter), `page` (zero-based), and `size` (1–100, default 20). Sort order is `fullName`, then ID. The response contains `items`, `page`, `size`, `totalElements`, `totalPages`, and server-derived `allowedActions` for create and update. IDs are decimal strings. List and detail include `id`, `code`, `fullName`, `active`, and UTC timestamps.

Create and PUT accept only `code` and `fullName`. Code is unique and normalized to uppercase ASCII (maximum 50 characters). Full name is required (maximum 150 characters). PUT preserves ID, status, creation time and all stored contact values. V1 has no delete, deactivate, reactivate, customer account, or customer role.

### D12 contact decision pending

The schema allows nullable phone (30 characters), email (254), address (500) and notes (TEXT). Only customer code is unique. The core release omits contact data from responses, limits search to code and name, and rejects contact keys on create and update, including explicit nulls.

Decide which roles may view or change each contact field and in which workflows (Customer lookup, Sales lookup and profile editing). Decide whether to mask any fields, allow them in search or apply a format if they become writable. The baseline supplies storage limits and optionality without deciding those behaviors.

Errors use the shared ProblemDetail contract. Expect 400 for invalid input, 401 for anonymous access with a valid security context for the request method, 403 for missing permission or CSRF, 404 for a missing record, and 409 for a duplicate code. The database unique constraint backs up the application duplicate check. Each write is transactional and independently authorized.

## Sales handoff for TV2

`SAL-001` can select an existing active customer by ID using the list's `active=true` filter where the actor has `CUSTOMER_READ`. A walk-in sale may leave `sales_order.customer_id` null. No placeholder customer row or account is required.

After checking Sales authorization, call `CustomerService.requireActiveForSale(long customerId)` inside the same Sales write transaction that attaches or reassigns a customer. The method requires an existing transaction, locks the customer row, and checks current activity. It returns 404 for missing and 409 for inactive. Perform the check before changing the order and retain the lock until commit. When the selection came from an earlier read, revalidate the order state and selected ID inside the transaction. TV2 owns the Sales workflow and cross-module lock order.

Existing orders continue to use the nullable FK with `ON DELETE RESTRICT`. An inactive customer's existing references remain valid. Customer edits preserve the ID and do not revise existing orders, quantities, or totals. Order history is outside the Customer DTO and remains subject to Sales permissions.

## Approved V1 permission rollout

| Role | Approved grants |
|---|---|
| MANAGER | `CUSTOMER_READ` |
| SALES_STAFF | `CUSTOMER_READ`, `CUSTOMER_CREATE`, `CUSTOMER_UPDATE` |
| ADMIN, WAREHOUSE_STAFF | None |

`CUSTOMER_DEACTIVATE` remains `PROVISIONAL_DENY`. V8 (checksum `151122022`) registers three codes. V9 (checksum `985142827`) adds exactly the four approved grants. Both scripts are outside normal Flyway discovery. See the [Windows rollout runbook](customer-runtime-activation.md) for real V1–V7 history checks, backup, separate stages and the post-V9 route patch.

`CustomerRolloutIT` exercises the runner on disposable MySQL 8. It applies V1, checks fixture V2–V7 history against the original artifacts, then applies and verifies V8 and V9 separately. It also verifies duplicate-code rejection, nullable walk-in references, history preservation after an edit and the delete restriction.

## Local Customer smoke evidence

| Session | Live browser result | Isolated security coverage |
|---|---|---|
| Anonymous | PASS. The Login screen rendered at `/customers`, and Customer list GET through Vite returned `401`. | `anonymousRequestsReturn401` |
| MANAGER | PASS for list and read-only UI. The manager.csm session received HTTP 200 with an empty list and no allowed actions or create/edit controls. A missing Customer returned HTTP 404. Detail HTTP 200 remains PENDING until an existing record is available. | `managerReadIsAllowedWithRedactedContactAndWritesAreDenied` verifies list/detail `200`, no allowed actions and mutation `403`. |
| ADMIN | PASS. The admin.csm session received HTTP 403 for list and detail, showed the access-denied message and offered no create/edit controls. | `adminAndWarehouseWithoutGrantsAreDenied` verifies list and mutation `403`. |
| SALES_STAFF | PENDING. There is no local Sales Staff test account. | `salesCanUseAllFourRoutesAndReceivesOnlyGrantedActions` verifies list/detail `200`, create `201`, and update `200` with CSRF. |
| WAREHOUSE_STAFF | PENDING. There is no local Warehouse Staff test account. | `adminAndWarehouseWithoutGrantsAreDenied` verifies list and mutation `403`. |

The saved `CustomerSecurityTest` run passed all seven cases against the release configuration with isolated fixtures. These browser checks made no data changes. The route patch is applied. The Manager response verifies that the local runtime has an effective CUSTOMER_READ grant. The available smoke record does not include the operator’s V8/V9 history, checksums or full grant read-back. Retain that read-back before declaring the target rollout verified. Positive detail, Sales Staff and Warehouse Staff live checks remain pending.
