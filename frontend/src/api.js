// Thin fetch wrapper around the ProcureFlow REST API.
// The JWT is kept in localStorage for simplicity. A production deployment would normally prefer an
// HttpOnly cookie plus CSRF protection, which removes the token from reach of injected scripts.

const BASE = import.meta.env.VITE_API_BASE_URL || '';
const TOKEN_KEY = 'pf_token';

let token = localStorage.getItem(TOKEN_KEY);

export function setToken(value) {
  token = value;
  if (value) localStorage.setItem(TOKEN_KEY, value);
  else localStorage.removeItem(TOKEN_KEY);
}

export function hasToken() {
  return Boolean(token);
}

export class ApiError extends Error {
  constructor(status, body) {
    super((body && body.message) || `Request failed (${status})`);
    this.status = status;
    this.fieldErrors = (body && body.fieldErrors) || null;
  }
}

function queryString(params) {
  if (!params) return '';
  const search = new URLSearchParams();
  Object.entries(params).forEach(([key, value]) => {
    if (value !== undefined && value !== null && value !== '') search.append(key, value);
  });
  const text = search.toString();
  return text ? `?${text}` : '';
}

async function request(method, path, { params, body } = {}) {
  const headers = { Accept: 'application/json' };
  if (body !== undefined) headers['Content-Type'] = 'application/json';
  if (token) headers.Authorization = `Bearer ${token}`;

  let response;
  try {
    response = await fetch(`${BASE}/api${path}${queryString(params)}`, {
      method,
      headers,
      body: body !== undefined ? JSON.stringify(body) : undefined,
    });
  } catch {
    throw new ApiError(0, { message: 'Cannot reach the server. Is the backend running?' });
  }

  if (response.status === 204) return null;

  let data = null;
  const text = await response.text();
  if (text) {
    try {
      data = JSON.parse(text);
    } catch {
      data = null;
    }
  }

  if (!response.ok) {
    // An expired or revoked session: tell the app to sign out (login attempts report their own 401).
    if (response.status === 401 && token && !path.startsWith('/auth/')) {
      window.dispatchEvent(new Event('pf:unauthorized'));
    }
    throw new ApiError(response.status, data);
  }
  return data;
}

export const api = {
  get: (path, params) => request('GET', path, { params }),
  post: (path, body) => request('POST', path, { body: body === undefined ? {} : body }),
  put: (path, body) => request('PUT', path, { body }),
  patch: (path, body) => request('PATCH', path, { body }),
};
