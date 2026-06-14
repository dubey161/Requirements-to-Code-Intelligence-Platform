/**
 * Central API client — automatically attaches the JWT Bearer token to every request.
 * On 401, clears the stored auth and redirects to /login.
 */

const STORAGE_KEY = 'ai_platform_auth'
const BASE_URL = (import.meta.env.VITE_API_BASE_URL ?? '').replace(/\/$/, '')

function getToken() {
  try {
    const raw = localStorage.getItem(STORAGE_KEY)
    return raw ? JSON.parse(raw).accessToken : null
  } catch {
    return null
  }
}

export async function apiFetch(path, options = {}) {
  const token = getToken()
  const headers = {
    'Content-Type': 'application/json',
    ...(token ? { Authorization: `Bearer ${token}` } : {}),
    ...(options.headers ?? {}),
  }

  const res = await fetch(BASE_URL + path, { ...options, headers })

  if (res.status === 401) {
    localStorage.removeItem(STORAGE_KEY)
    window.location.href = '/login'
    throw new Error('Session expired — please log in again')
  }

  return res
}

/** POST to a public auth endpoint (no token). */
export async function authPost(path, body) {
  const res = await fetch(BASE_URL + path, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
  })
  return res
}
