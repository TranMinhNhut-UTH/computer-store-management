# Computer Store Management — frontend

React + Vite, JavaScript/JSX. Use Node.js `^22.22.2 || ^24.15.0 || >=26.0.0` and npm
(the jsdom development test dependency requires these Node versions).

From `frontend/`:

```powershell
npm ci
npm run dev
```

Open <http://localhost:5173>. The server binds to localhost and fails if port 5173 is occupied.

```powershell
npm run build
```

Build output is written to `dist/`. Both `dist/` and `node_modules/` are ignored by Git.

The login page checks the current session and obtains a CSRF token when it opens. If the
backend is unavailable, it shows a connection error and a retry button.
The development proxy forwards relative `/api` requests to `http://127.0.0.1:8080` while
preserving the original Host header. Future API clients must follow the backend's session/CSRF
contract; the proxy does not disable CSRF. No wildcard CORS is enabled.

The proxy applies only to the development server. Production API routing/deployment remains separate.

## Authentication

The client uses relative `/api/v1/auth` URLs and `credentials: 'same-origin'`:

- GET `/me` restores a session; anonymous 401 shows the login form.
- GET `/csrf` obtains the server-provided `headerName` and masked `token`.
- POST `/login` sends the entered username/password unchanged with a fresh CSRF header.
- Successful login is confirmed by GET `/me`, then GET `/csrf` refreshes the rotated token.
- POST `/logout` uses a fresh CSRF header; on success, clear the displayed user and refresh CSRF.

Tokens exist only in memory during requests. No password, token, session ID or authentication
state is stored in localStorage/sessionStorage. The browser manages the session cookie.
Failed POST requests are not retried automatically. No demo accounts or fake runtime authentication
are included; successful login/logout require a real employee account.

Run the client tests (fetch is stubbed; no HTTP POST or database access):

```powershell
node --test src/api/auth.test.js
```

## Light/dark theme

Use the theme button on the login page to switch between light and dark without reloading.
Light is the default. The preference is stored as `light` or `dark` under `csm-theme` in
localStorage; invalid values fall back to light. If storage is blocked, switching still works
for the current page. No authentication data is stored there.

`ThemeProvider` wraps the application once. Components use `useTheme()` and the shared
`--csm-*` CSS variables. See [CSM Frontend Design System V1](../docs/frontend-design-system.md)
for tokens, accessibility requirements and component conventions. The authenticated App Shell
is available after the server confirms `/me`; navigation entries are coming-soon placeholders,
not working business modules or a client-side permission system.

Navigation adapts to the viewport: desktop (>=1024px) starts with a 256px sidebar;
the hamburger toggles a 72px icon rail. Tablet (768–1023px) starts with the icon rail
and can expand it. Only mobile (<768px) uses a modal drawer with backdrop, Escape,
close button, scroll lock and focus restoration. A single navigation is mounted at a
time; resize preserves the selected module and closes an open mobile drawer.
Icons have accessible names; compact items also have native title tooltips.
Expansion preferences stay in memory, independently for desktop/tablet.

Run all current frontend tests:

```powershell
node --test --test-concurrency=1 src/app/AppLayout.test.js src/theme/theme.test.js src/api/auth.test.js src/api/categories.test.js src/features/categories/CategoryPage.test.js
```

Unit tests and build checks do not replace browser QA. Responsive navigation has also
been checked in isolated headless Chrome with mocked auth responses and no backend
requests; see the design-system document for the checked viewports and interactions.
Real-account login, touch devices, screen readers, zoom and other browsers remain pending.

## Category Management — Frontend Part 2

Choose **Danh mục** in the authenticated shell to open **Quản lý danh mục**. Other
modules retain their existing placeholders. Navigation does not grant permissions.
The Category backend still has HTTP denyAll and no runtime grants: a real request
currently shows a clear access-denied state, with no sample-data fallback.

The page implements list/search by code or name, active/all/inactive filters, fixed
20-item API pagination, loading/empty/error states, create/edit dialogs and confirmed
deactivation. Edit/confirmation fetch fresh detail before enabling submission.
Only code/name/description are sent; IDs remain decimal strings. Update cannot change
active/createdAt. No delete/reactivate action is offered.

`api/client.js` shares sanitized errors and same-origin cookie transport with the existing
auth client. Category mutations fetch a fresh server-named CSRF header before each
POST/PUT; failures are never retried automatically. No auth data enters browser storage.
400/401/403/404/409 are handled explicitly. A deactivation 409 explains the conservative
Product-reference restriction without claiming every conflict has that cause.

Native modal dialogs provide focus containment, Escape/cancel, scroll lock and focus
restoration; dismiss is disabled while saving. CSS uses existing semantic tokens for
both themes. Tables scroll within their own region on narrow screens.

Validation: 60/60 frontend tests PASS (24 regression + 36 new), using only mocked APIs;
production build PASS. Browser render smoke checks use intercepted APIs only and do
not prove Category E2E. Real HTTP access, approved runtime grants, successful live CRUD,
keyboard/screen-reader/touch/zoom and cross-browser QA remain pending.
