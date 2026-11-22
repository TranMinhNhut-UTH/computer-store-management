# Customer Management V1

`PAR-002` implements employee lookup, creation and information updates for customer records (FR-CUS-01–05). The signed-in screen is `/customers`. The four routes below are ready for isolated checks. Production denies them until the proposed grants and D12 contact projection are approved, the grants are applied, and the route boundary is released.

| Method and path | Required permission | Success |
|---|---|---|
| GET `/api/v1/customers` | `CUSTOMER_READ` | 200 paginated list |
| GET `/api/v1/customers/{id}` | `CUSTOMER_READ` | 200 detail |
| POST `/api/v1/customers` | `CUSTOMER_CREATE` | 201 with `Location` |
| PUT `/api/v1/customers/{id}` | `CUSTOMER_UPDATE` | 200 full information replacement |

List accepts `q` (code or name, up to 200 characters, literal wildcard characters), `active` (optional status filter), `page` (zero-based), and `size` (1–100, default 20). Sort order is `fullName`, then ID. The response contains `items`, `page`, `size`, `totalElements`, `totalPages`, and server-derived `allowedActions` for create and update. IDs are decimal strings. List and detail include `id`, `code`, `fullName`, `active`, and UTC timestamps. `contact` is null for list rows and for read-only detail. The proposed edit projection supplies `contact.phone`, `email`, `address`, and `notes` only to actors with update permission. Mutation responses can echo the submitted contact fields.

Create and PUT accept `code`, `fullName`, `phone`, `email`, `address`, and `notes`. Code is unique and normalized to uppercase ASCII (50 characters). Full name is required (150 characters). Phone supports digits and common separators (30 characters). Email is optional, preserves case and is nonunique, as is phone. Address allows 500 characters, and notes fit the MySQL TEXT byte limit. Blank optional fields become null. PUT preserves ID, active status, and creation time. V1 has no delete, deactivate, reactivate, customer account, or customer role.

Errors use the shared ProblemDetail contract. Expect 400 for invalid input, 401 for anonymous access with a valid security context for the request method, 403 for missing permission or CSRF, 404 for a missing record, and 409 for a duplicate code. The database unique constraint backs up the application duplicate check. Each write is transactional and independently authorized.

## Sales handoff for TV2

`SAL-001` can select an existing active customer by ID using the list's `active=true` filter where the actor has `CUSTOMER_READ`. A walk-in sale may leave `sales_order.customer_id` null. No placeholder customer row or account is required.

After checking Sales authorization, call `CustomerService.requireActiveForSale(long customerId)` inside the same Sales write transaction that attaches or reassigns a customer. The method requires an existing transaction, locks the customer row, and checks current activity. It returns 404 for missing and 409 for inactive. Perform the check before changing the order and retain the lock until commit. When the selection came from an earlier read, revalidate the order state and selected ID inside the transaction. TV2 owns the Sales workflow and cross-module lock order.

Existing orders continue to use the nullable FK with `ON DELETE RESTRICT`. An inactive customer's existing references remain valid. Customer edits preserve the ID and do not revise existing orders, quantities, or totals. Order history is outside the Customer DTO and remains subject to Sales permissions.

## Proposed permission rollout

| Role | Proposed grants |
|---|---|
| MANAGER | `CUSTOMER_READ` |
| SALES_STAFF | `CUSTOMER_READ`, `CUSTOMER_CREATE`, `CUSTOMER_UPDATE` |
| ADMIN, WAREHOUSE_STAFF | None |

`CUSTOMER_DEACTIVATE` remains `PROVISIONAL_DENY`. The dormant SQL under `backend/src/main/resources/operations/customer/v1` is outside normal Flyway discovery. V8 (`151122022`) requires the expected successful V1–V7 history and registers exactly three codes. V9 (`985142827`) requires V8, matching metadata and zero existing Customer grants, and adds exactly the four pairs above. Each stage rejects incompatible state. Before live use, approve grants and contact scope, verify the target and backup, and register the migrations in an explicit rollout with the original V1–V7 sources. Verify V8 and V9 separately before releasing the routes. Existing Product and Supplier grants remain independent.

`CustomerRolloutIT` applies V8/V9 on disposable MySQL 8 with fixture history for V2–V7. It also verifies duplicate-code rejection, nullable walk-in references, history preservation after an edit, and the delete restriction. Run it from `backend` with `.\mvnw.cmd -o '-Dtest=CustomerRolloutIT' '-Dcsm.mysql.home=C:\Program Files\MySQL\MySQL Server 8.0' test`. Browser smoke used mocked API data at desktop and 375px. Live Customer smoke remains pending.
