import Config from '@/settings'

export const TokenKey = Config.TokenKey

export function getToken() {
  return localStorage.getItem(TokenKey)
}

export function authorization() {
  const token = getToken()
  return token ? `Bearer ${token}` : ''
}

// 将响应与发出请求时的 Token 关联，避免旧请求影响刚登录的新会话。
export function isCurrentSessionRequest(config) {
  const token = getToken()
  const headers = config && config.headers
  const sent = headers && (typeof headers.get === 'function' ? headers.get('Authorization') : headers.Authorization || headers.authorization)
  return !!token && sent === `Bearer ${token}`
}

export function setToken(token) {
  localStorage.setItem(TokenKey, token)
}
export function removeToken() {
  localStorage.removeItem(TokenKey)
  localStorage.removeItem('rooe.auth.lastActiveAt')
  localStorage.removeItem('rooe.auth.lastRenewableServerContactAt')
  localStorage.removeItem('rooe.auth.absoluteExpiresAt')
  localStorage.removeItem('rooe.auth.lastTouchAt')
}
