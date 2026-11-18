# Computer Store Management — frontend

React + Vite, JavaScript/JSX. Use Node.js `^20.19.0 || >=22.12.0` and npm.

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

The initial page makes no API calls and runs without the backend. Login UI is not implemented.
The development proxy forwards relative `/api` requests to `http://127.0.0.1:8080` while
preserving the original Host header. Future API clients must follow the backend's session/CSRF
contract; the proxy does not disable CSRF. No wildcard CORS is enabled.

The proxy applies only to the development server. Production API routing/deployment remains separate.
