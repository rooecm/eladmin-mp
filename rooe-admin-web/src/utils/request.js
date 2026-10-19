import axios from 'axios'
import router from '@/router/routers'
import { Notification } from 'element-ui'
import store from '../store'
import { authorization, getToken, isCurrentSessionRequest } from '@/utils/auth'
import { recordRenewal } from '@/utils/session'
import Config from '@/settings'
import Cookies from 'js-cookie'

const service = axios.create({
  baseURL: process.env.NODE_ENV === 'production' ? process.env.VUE_APP_BASE_API : '/',
  timeout: Config.timeout
})

service.interceptors.request.use(
  config => {
    if (getToken()) {
      config.headers['Authorization'] = authorization()
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

// 普通请求、直接上传和 Blob 下载共用错误处理，统一业务错误码与会话跳转行为。
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

  const config = error.config || (response && response.config) || {}
  const loginRequest = /(?:^|\/)auth\/login(?:\?|$)/.test(config.url || '')
  const currentSession = isCurrentSessionRequest(config)
  // 仅当前会话的 401 清理登录态；强制改密的 403 跳转改密页，其他 403/503 保留会话。
  if (status === 401 && !loginRequest && currentSession && !redirectingToLogin) {
    redirectingToLogin = true
    try {
      await store.dispatch('ClearAuth')
      Cookies.set('point', 401)
      await router.replace({ path: '/login' })
    } finally {
      redirectingToLogin = false
    }
  } else if (status === 403 && code === 'AUTH_PASSWORD_CHANGE_REQUIRED' && currentSession) {
    store.commit('SET_PASSWORD_CHANGE', true)
    await router.replace({ path: '/change-password' })
  }
  throw error
}

// 统一记录服务端续期结果，解包 ApiResult，同时兼容仍直接返回业务数据的接口。
export function handleApiResponse(response) {
  recordRenewal(response)
  const result = response.data
  if (!isApiResult(result)) return result
  if (result.code === '0') return result.data
  const error = new Error(result.message)
  error.response = response
  return handleApiError(error)
}

service.interceptors.response.use(handleApiResponse, handleApiError)

export default service
