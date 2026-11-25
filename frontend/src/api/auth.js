import { ApiError, createApiTransport } from './client.js'
export { ApiError } from './client.js'
const BASE_URL = '/api/v1/auth'

// Cookies stay browser-managed; CSRF tokens exist only in request scope, never browser storage.
export function createAuthClient(fetchRequest = globalThis.fetch.bind(globalThis)) {
  const transport = createApiTransport(fetchRequest, undefined, { recoverSession: false })
  const protectedTransport = createApiTransport(fetchRequest)
  const request = (path, options) => transport(`${BASE_URL}${path}`, options)

  function currentUser(payload) {
    if (typeof payload?.userId !== 'string' || typeof payload?.username !== 'string') throw new ApiError(502)
    return { userId: payload.userId, username: payload.username }
  }

  async function refreshCsrf(options = {}) {
    const payload = await request('/csrf', options)
    if (typeof payload?.headerName !== 'string' || !payload.headerName
        || typeof payload?.token !== 'string' || !payload.token) throw new ApiError(502)
    return { headerName: payload.headerName, token: payload.token }
  }

  async function getCurrentUser(options = {}) {
    try { return currentUser(await request('/me', options)) } catch (error) {
      if (error instanceof ApiError && error.status === 401) return null
      throw error
    }
  }

  async function post(path, body, send = transport) {
    const csrf = await refreshCsrf()
    return send(`${BASE_URL}${path}`, {
      method: 'POST',
      headers: { [csrf.headerName]: csrf.token, ...(body ? { 'Content-Type': 'application/json' } : {}) },
      ...(body ? { body: JSON.stringify(body) } : {}),
    })
  }

  return {
    refreshCsrf,
    getCurrentUser,
    async login({ username, password }) { return currentUser(await post('/login', { username, password })) },
    async changePassword({ currentPassword, newPassword }) {
      await post('/change-password', { currentPassword, newPassword }, protectedTransport)
    },
    async logout() { await post('/logout') },
  }
}

export const authClient = createAuthClient()
