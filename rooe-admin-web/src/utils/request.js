import axios from 'axios'
import router from '@/router/routers'
import { Notification } from 'element-ui'
import store from '../store'
import { getToken } from '@/utils/auth'
import Config from '@/settings'
import Cookies from 'js-cookie'

const service = axios.create({
  baseURL: process.env.NODE_ENV === 'production' ? process.env.VUE_APP_BASE_API : '/',
  timeout: Config.timeout
})

service.interceptors.request.use(
  config => {
    if (getToken()) {
      config.headers['Authorization'] = getToken()
    }
    config.headers['Content-Type'] = 'application/json'
    return config
  },
  error => Promise.reject(error)
)

export function isApiResult(result) {
  return result && typeof result.code === 'string' && typeof result.message === 'string' &&
    Object.prototype.hasOwnProperty.call(result, 'data')
}

function readBlob(blob) {
  return new Promise((resolve, reject) => {
    const reader = new FileReader()
    reader.onload = () => resolve(reader.result)
    reader.onerror = () => reject(reader.error)
    reader.readAsText(blob, 'utf-8')
  })
}

let redirectingToLogin = false

// Shared by normal requests, direct Axios uploads, and failed Blob downloads.
export async function handleApiError(error) {
  const response = error.response
  const status = response ? response.status : 0
  let result = response && response.data
  if (typeof Blob !== 'undefined' && result instanceof Blob) {
    try {
      result = JSON.parse(await readBlob(result))
      response.data = result
    } catch (e) {
      result = null
    }
  }
  const code = result && typeof result.code === 'string' ? result.code : undefined
  const message = result && typeof result.message === 'string' && result.message
    ? result.message
    : (/timeout/i.test(error.message || '') ? '网络请求超时' : '接口请求失败')
  if (code) {
    error.code = code
    error.businessCode = code
  }
  error.message = message
  error.httpStatus = status

  Notification.error({ title: message, duration: 5000 })

  const sessionExpired = code === 'AUTH_1005'
  const loginRequest = /(?:^|\/)auth\/login(?:\?|$)/.test((error.config || (response && response.config) || {}).url || '')
  const legacyUnauthorized = status === 401 && (!code || code === '401') && !loginRequest
  if ((sessionExpired || legacyUnauthorized) && getToken() && !redirectingToLogin) {
    redirectingToLogin = true
    try {
      await store.dispatch('LogOut')
      Cookies.set('point', 401)
      location.reload()
    } catch (e) {
      router.push({ path: '/login' })
    } finally {
      redirectingToLogin = false
    }
  } else if (status === 403 && (!code || code === '403' || code === 'PERMISSION_1001')) {
    router.push({ path: '/401' })
  }
  throw error
}

service.interceptors.response.use(
  response => {
    const result = response.data
    if (isApiResult(result)) {
      if (result.code === '0') {
        return result.data
      }
      const error = new Error(result.message)
      error.response = response
      return handleApiError(error)
    }
    return result
  },
  handleApiError
)

export default service
