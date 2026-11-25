import { ApiError, createApiTransport } from './client.js'

const request = createApiTransport()

export async function getUiCapabilities(options) {
  const response = await request('/api/v1/auth/ui-capabilities', options)
  if (!Array.isArray(response?.permissions) || !response.permissions.every(code => typeof code === 'string')) {
    throw new ApiError(502)
  }
  return response.permissions
}
