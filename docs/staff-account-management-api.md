# Staff account management V1

`IAM-005` implements FR-USR-01–06. The signed-in screen is `/employees`. This is the staff workflow for provisioning individual accounts, including subsequent administrators. The first local Admin bootstrap remains separate.

| Method and path | Required permission | Result |
|---|---|---|
| GET `/api/v1/employees` | `EMPLOYEE_READ` | `200` with page and server-granted action hints |
| GET `/api/v1/employees/{id}` | `EMPLOYEE_READ` | `200` with an individual account |
| POST `/api/v1/employees` | `EMPLOYEE_CREATE` and `EMPLOYEE_ROLE_ASSIGN` | `201` with `Location` |
| PUT `/api/v1/employees/{id}` | `EMPLOYEE_UPDATE` | `200` |
| PUT `/api/v1/employees/{id}/roles` | `EMPLOYEE_ROLE_ASSIGN` | `200` |
| POST `/api/v1/employees/{id}/lock` | `EMPLOYEE_LOCK` | `200` |
| POST `/api/v1/employees/{id}/unlock` | `EMPLOYEE_UNLOCK` | `200` |

Every route requires a current authenticated employee, the server-resolved `ADMIN` role and the listed permissions. The UI uses `EMPLOYEE_READ` from `/api/v1/auth/ui-capabilities` and the list's `allowedActions`. All mutations require CSRF. The routes and the navigation capability are disabled by default. The local release patch supplies an external configuration file after V11 read-back. Only a backend launch that explicitly loads that file enables the routes. [Local rollout guide](staff-runtime-activation.md)

The candidates for V10/V11 are exactly six `EMPLOYEE_*` codes and six `ADMIN` assignments, limited to this API. Manager, Sales Staff and Warehouse Staff have no staff-management grants. ADMIN receives no wildcard, and role management cannot edit the permission catalog.

## Data and validation

- List accepts optional `q` (username/full name, literal wildcard characters), `active`, zero-based `page` and `size` from 1 to 100. Sort is full name then ID.
- A staff response contains a decimal-string ID, username, full name, optional email and phone, active status, assigned role codes and the UTC update time. No password, hash, session data or entity graph is returned.
- Create accepts `username`, `fullName`, optional `email` and `phone`, `password` and a unique, nonempty list of existing role codes. The username and email are normalized and unique. Passwords are preserved as entered and encoded with the existing BCrypt encoder. The upper bound is 72 UTF-8 bytes.
- Information update accepts only `fullName`, `email` and `phone`. Username and credentials stay fixed. Role assignment replaces the requested set and preserves unchanged assignment rows. Lifecycle operations change active status and preserve the user ID, all history, and role assignments. There is no delete, public registration, password reset or self-service profile endpoint.

## Account safety and evidence

The administration write transactions acquire the existing ADMIN role row lock before resolving the actor and target. READ COMMITTED lets a waiting writer see the preceding transaction's actor, role and status changes. The lock serializes bootstrap and administration writes that could affect the active-ADMIN invariant. An administrator cannot self-lock or remove their own ADMIN assignment. A mutation that would leave no active ADMIN returns `409`. The transaction rolls back if account creation or role assignment fails.

Invalid input returns `400`. Missing or inactive identity returns `401`. Missing ADMIN, operation permissions, or CSRF return `403` when applicable. Missing records return `404`. Duplicate account identifiers and conflicting lifecycle changes return `409`. Locked accounts cannot log in.

### P01 session and role changes

- A successful lock revokes every prior session when the transaction commits. Protected requests return `401 UNAUTHENTICATED`. Unlock leaves those sessions revoked and requires fresh sign-in.
- An actual role-set change revokes all prior sessions the same way. Identical role assignments, ordinary account edits and failed Staff writes leave current sessions usable. New sessions use current server grants and still return `403` when a permission is missing.
- The SPA keeps the current route and prompts for sign-in without replaying rejected writes. Existing logout and CSRF protections remain. Requests already past authorization can finish. The V12 prerequisite and timeout limits are described in the [P01 rollout runbook](session-revocation-rollout.md).

`StaffManagementIT` covers the isolated MySQL migration stages and real HTTP sessions, including the four roles, CSRF, create and assignment rollback, lock and unlock, and competing administrators. The isolated fixture's predecessor history is compared against the original migration artifacts. Other deployments still require their own approved rollout and V10/V11 read-back.

Browser smoke on Spring Boot, Vite and disposable MySQL verified create, detail and edit at 1440px in light mode. At 375px in dark mode it verified navigation, role assignment, lock and unlock, and sign-in as the newly created account. Anonymous list returned HTTP 401. The non-Admin account received HTTP 403 and had no Staff navigation entry.

## Local rollout and smoke evidence

The operator confirmed the following on `computer_store_dev`. The disposable MySQL and browser results above remain separate evidence.

| Check | Local result |
|---|---|
| Flyway V10 read-back | PASS. Six `EMPLOYEE_*` permissions. |
| Flyway V11 read-back | PASS. Six explicit `ADMIN` grants. |
| Backend health | PASS. |
| Anonymous Staff API | PASS. HTTP `401`. |
| ADMIN | PASS. Opened `/employees` and saw management actions. |
| MANAGER | PASS. Staff Management access was denied. |
| Staff create, edit, role assignment, lock and unlock | PENDING. No live mutation smoke was recorded on `computer_store_dev`. |

The local launch uses the ignored `backend/config/staff-local.properties` activation file. It is excluded from Git, and the default application configuration keeps Staff disabled. The P01 release requires verified V12 before starting this backend.
