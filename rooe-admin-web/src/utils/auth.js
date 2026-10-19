import Config from '@/settings'

export const TokenKey = Config.TokenKey
export const RefreshTokenKey = Config.RefreshTokenKey

export function getToken() {
  return localStorage.getItem(TokenKey)
}

export function getRefreshToken() {
  return localStorage.getItem(RefreshTokenKey)
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

export function setRefreshToken(token) {
  if (token) localStorage.setItem(RefreshTokenKey, token)
}

// 轮换后同时写入两把凭证，任何一步失败都不应留下半套状态。
export function setCredentials(accessToken, refreshToken) {
  setToken(accessToken)
  setRefreshToken(refreshToken)
}

export function removeToken() {
  localStorage.removeItem(TokenKey)
  localStorage.removeItem(RefreshTokenKey)
  localStorage.removeItem('rooe.auth.lastActiveAt')
  localStorage.removeItem('rooe.auth.absoluteExpiresAt')
}
