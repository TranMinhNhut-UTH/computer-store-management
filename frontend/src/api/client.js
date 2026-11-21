export class ApiError extends Error {
  constructor(status, code = '', fields = []) {
    super('API request failed')
    this.name = 'ApiError'
    this.status = status
    this.code = code
    this.fields = fields
  }
}

// Shared transport: browser-managed cookies, no secrets in storage or raw server details in errors.
export function createApiTransport(fetchRequest = globalThis.fetch.bind(globalThis)) {
  return async function request(path, options = {}) {
    let response
    try {
      response = await fetchRequest(path, {
        ...options, credentials: 'same-origin', cache: 'no-store',
        headers: { Accept: 'application/json', ...options.headers },
      })
    } catch (error) {
      if (error.name === 'AbortError') throw error
      throw new ApiError(0)
    }
    if (!response.ok) {
      const problem = await response.json().catch(() => null)
      const fields = Array.isArray(problem?.fieldErrors)
        ? problem.fieldErrors.map(item => item?.field).filter(field => ['code', 'name', 'description'].includes(field)) : []
      throw new ApiError(response.status, typeof problem?.code === 'string' ? problem.code : '', fields)
    }
    if (response.status === 204) return null
    try { return await response.json() } catch { throw new ApiError(502) }
  }
}
