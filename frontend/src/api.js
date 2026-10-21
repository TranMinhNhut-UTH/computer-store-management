export const DEFAULT_API_BASE = 'http://localhost:5080';

export function getApiBase() {
  return localStorage.getItem('apiBase') || DEFAULT_API_BASE;
}

export function setApiBase(url) {
  localStorage.setItem('apiBase', url);
}

export async function api(path, opts = {}) {
  const base = getApiBase();
  const token = localStorage.getItem('accessToken');

  const headers = {
    ...(opts.body ? { 'Content-Type': 'application/json' } : {}),
    ...(token ? { Authorization: 'Bearer ' + token } : {}),
  };

  const response = await fetch(base + path, { ...opts, headers });
  if (response.status === 204) return null;

  const text = await response.text();
  let data;
  try {
    data = text ? JSON.parse(text) : null;
  } catch {
    data = text;
  }

  if (!response.ok) {
    throw new Error(data?.detail || data?.message || data?.title || `HTTP ${response.status}`);
  }
  return data;
}

export const post = (path, body) => api(path, { method: 'POST', body: JSON.stringify(body) });
export const put = (path, body) => api(path, { method: 'PUT', body: JSON.stringify(body) });
export const del = (path) => api(path, { method: 'DELETE' });