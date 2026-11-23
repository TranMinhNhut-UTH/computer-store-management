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

- Locking does not invalidate existing HTTP sessions. A new request to Staff or another business use case that resolves the current actor returns `401` while the account is inactive. `GET /api/v1/auth/me` can still return `200` with the cached principal, so the shell may appear signed in. Session-only endpoints follow their existing rules.
- Each guarded business request reloads current roles and effective grants. Removing the last role that grants a permission takes effect on the next guarded request, typically `403` for an otherwise active account. If another assigned role still grants it, access remains. Grants restored by role assignment can take effect on the same session.
- Unlocking can restore guarded access on an unexpired pre-lock session. Requests already past authorization are not canceled. Immediate session revocation and invalidation across nodes remain open under P01. For suspected compromise, keep the account locked until existing sessions have expired or an approved procedure has invalidated them. Do not use unlock as a session reset.

`StaffManagementIT` covers the isolated MySQL migration stages and real HTTP sessions, including the four roles, CSRF, create and assignment rollback, lock and unlock, and competing administrators. The isolated fixture's predecessor history is compared against the original migration artifacts. Production activation still requires approval of the candidate assignments and live V10/V11 read-back.

Browser smoke on Spring Boot, Vite and disposable MySQL verified create, detail and edit at 1440px in light mode. At 375px in dark mode it verified navigation, role assignment, lock and unlock, and sign-in as the newly created account. Anonymous list returned HTTP 401. The non-Admin account received HTTP 403 and had no Staff navigation entry.
