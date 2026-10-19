import request from '@/utils/request'

export function login(username, password) {
  return request({ url: 'api/auth/login', method: 'post', data: { username, password, clientType: 'WEB' }})
}

export function getInfo() {
  return request({ url: 'api/auth/me', method: 'get' })
}

export function logout() {
  return request({ url: 'api/auth/logout', method: 'post' })
}

export function changePassword(data) {
  return request({ url: 'api/auth/password', method: 'post', data })
}
