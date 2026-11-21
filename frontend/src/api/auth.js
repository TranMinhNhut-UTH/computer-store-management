const BASE_URL = '/api/v1/auth'

export class ApiError extends Error {
  constructor(status, code = '') {
    super('Auth request failed')
    this.name = 'ApiError'
    this.status = status
    this.code = code
  }
}

// Cookies stay browser-managed; CSRF tokens exist only in request scope, never browser storage.
export function createAuthClient(fetchRequest = globalThis.fetch.bind(globalThis)) {
  async function request(path, options = {}) {
    let response
    try {
      response = await fetchRequest(`${BASE_URL}${path}`, {
        ...options,
        credentials: 'same-origin',
        cache: 'no-store',
        headers: { Accept: 'application/json', ...options.headers },
      })
    } catch (error) {
      if (error.name === 'AbortError') throw error
      throw new ApiError(0)
    }
    if (!response.ok) {
      const problem = await response.json().catch(() => null)
      // Do not expose raw backend detail, validation values or internal exception messages.
      throw new ApiError(response.status, typeof problem?.code === 'string' ? problem.code : '')
    }
    if (response.status === 204) return null
    try { return await response.json() } catch { throw new ApiError(502) }
  }

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

  async function post(path, body) {
    const csrf = await refreshCsrf()
    return request(path, {
      method: 'POST',
      headers: { [csrf.headerName]: csrf.token, ...(body ? { 'Content-Type': 'application/json' } : {}) },
      ...(body ? { body: JSON.stringify(body) } : {}),
    })
  }

  return {
    refreshCsrf,
    getCurrentUser,
    async login({ username, password }) { return currentUser(await post('/login', { username, password })) },
    async logout() { await post('/logout') },
  }
}

export const authClient = createAuthClient()
