const BASE = import.meta.env.VITE_API_BASE || ''; // để trống = dùng proxy của Vite (/api -> backend)

let refreshing = null;

async function parse(res) {
  if (res.status === 204) return {};
  const text = await res.text();
  let data = null;
  try {
    data = text ? JSON.parse(text) : null;
  } catch {
    data = null;
  }
  if (!res.ok) {
    const fallback = res.status === 429 ? 'Bạn thao tác quá nhanh. Vui lòng thử lại sau.' : `Lỗi ${res.status}`;
    const err = new Error(data?.detail || data?.title || fallback);
    err.status = res.status;
    throw err;
  }
  return data ?? {};
}

function tryRefresh() {
  const rt = localStorage.getItem('refreshToken');
  if (!rt) return Promise.resolve(false);
  if (!refreshing) {
    refreshing = fetch(`${BASE}/api/auth/refresh`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ refreshToken: rt }),
    })
      .then(async (res) => {
        if (!res.ok) return false;
        const d = await res.json();
        localStorage.setItem('accessToken', d.accessToken);
        localStorage.setItem('refreshToken', d.refreshToken);
        return true;
      })
      .catch(() => false)
      .finally(() => {
        refreshing = null;
      });
  }
  return refreshing;
}

async function request(method, path, body, retried = false) {
  const headers = {};
  if (body !== undefined) headers['Content-Type'] = 'application/json';
  const token = localStorage.getItem('accessToken');
  if (token) headers.Authorization = `Bearer ${token}`;

  let res;
  try {
    res = await fetch(`${BASE}${path}`, {
      method,
      headers,
      body: body !== undefined ? JSON.stringify(body) : undefined,
    });
  } catch {
    throw new Error('Không kết nối được máy chủ. Hãy kiểm tra backend đã chạy ở cổng 5080 chưa.');
  }

  if (res.status === 401 && !retried && !path.startsWith('/api/auth/')) {
    if (await tryRefresh()) return request(method, path, body, true);
    localStorage.removeItem('accessToken');
    localStorage.removeItem('refreshToken');
    window.dispatchEvent(new Event('auth:expired'));
  }
  return parse(res);
}

export const get = (path) => request('GET', path);
export const post = (path, body) => request('POST', path, body ?? {});
export const put = (path, body) => request('PUT', path, body ?? {});
export const patch = (path, body) => request('PATCH', path, body ?? {});
export const del = (path) => request('DELETE', path);
export const api = (path, opts = {}) => request(opts.method || 'GET', path, opts.body);