export const BASE = '/ERP'

export async function getJson(path) {
  const response = await fetch(`${BASE}${path}`, { headers: { Accept: 'application/json' } })
  if (response.redirected || !response.headers.get('content-type')?.includes('application/json')) {
    window.location.replace(`${BASE}/login/login.do`)
    throw new Error('로그인이 필요합니다.')
  }
  if (!response.ok) throw new Error(await errorMessage(response, '데이터를 불러오지 못했습니다.'))
  return response.json()
}

export async function sendJson(path, method, body, csrf) {
  const response = await fetch(`${BASE}${path}`, {
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
