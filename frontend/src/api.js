export const BASE = '/ERP'

export async function authenticatedFetch(path, options) {
  const response = await fetch(`${BASE}${path}`, options)
  if (response.status === 401 || (response.redirected && new URL(response.url).pathname === `${BASE}/login/login.do`)) {
    requireLogin()
  }
  if (response.status === 403) {
    // CSRF rejects an expired session before Spring can redirect to login.
    const session = await fetch(`${BASE}/api/session`, { headers: { Accept: 'application/json' } })
    if (session.ok && (await session.json()).authenticated === false) requireLogin()
  }
  if (response.ok && options?.method && !['GET', 'HEAD'].includes(options.method.toUpperCase()) && /^\/(approval|notice|api\/(approvals|notices))(\/|$)/.test(path)) notifyDashboardChange()
  return response
}

export function notifyDashboardChange() {
  window.dispatchEvent(new Event('erp:dashboard-changed'))
}

function requireLogin() {
  window.location.replace(`${BASE}/login/login.do`)
  throw new Error('로그인이 필요합니다.')
}

export async function getJson(path) {
  const response = await authenticatedFetch(path, { headers: { Accept: 'application/json' } })
  if (!response.ok) throw new Error(await errorMessage(response, '데이터를 불러오지 못했습니다.'))
  if (!response.headers.get('content-type')?.includes('application/json')) throw new Error('데이터를 불러오지 못했습니다.')
  return response.json()
}

export async function sendJson(path, method, body, csrf) {
  const response = await authenticatedFetch(path, {
    method,
    headers: {
      'Content-Type': 'application/json',
      Accept: 'application/json',
      [csrf.headerName]: csrf.token
    },
    body: JSON.stringify(body)
  })
  if (!response.ok) throw new Error(await errorMessage(response, '요청을 처리하지 못했습니다.'))
  return response.status === 204 ? null : response.json()
}

export const postJson = (path, body, csrf) => sendJson(path, 'POST', body, csrf)

async function errorMessage(response, fallback) {
  const body = await response.json().catch(() => null)
  return body?.message || fallback
}

export function currentRoute() {
  return { path: window.location.pathname, search: window.location.search }
}
