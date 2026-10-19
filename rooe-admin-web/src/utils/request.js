import axios from 'axios'
import router from '@/router/routers'
import {Notification} from 'element-ui'
import store from '../store'
import {authorization, getToken, isCurrentSessionRequest} from '@/utils/auth'
import {isRefreshRequest, refreshCredentials} from '@/utils/refresh'
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

// 统一补齐业务码与提示信息，确保续期失败等提前返回路径也携带完整上下文。
function normalize(error, result, status, code) {
  const message = result && typeof result.message === 'string' && result.message
    ? result.message
    : (/timeout/i.test(error.message || '') ? '网络请求超时' : '接口请求失败')
  if (code) {
    error.code = code
    error.businessCode = code
  }
  error.message = message
  error.httpStatus = status
  return error
}

let redirectingToLogin = false

async function expireSession() {
  if (redirectingToLogin) return
  redirectingToLogin = true
  try {
    await store.dispatch('ClearAuth')
    Cookies.set('point', 401)
    await router.replace({path: '/login'})
  } finally {
    redirectingToLogin = false
  }
}

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
  const config = error.config || (response && response.config) || {}
  const loginRequest = /(?:^|\/)auth\/login(?:\?|$)/.test(config.url || '')
  // 仅当前会话的请求可以触发续期或清理登录态，旧会话响应不得影响新登录。
  const currentSession = isCurrentSessionRequest(config)

  // Access 过期时先静默续期再重放原请求；重放带 _retry 标记，避免续期失败后无限循环。
  if (status === 401 && !config._retry && !isRefreshRequest(config) && !loginRequest && currentSession) {
    try {
      await refreshCredentials()
      const replay = {...config, _retry: true}
      // 重放前重读当前 Token：续期已写入新凭证，旧请求头必须被覆盖。
      replay.headers = {...(config.headers || {}), Authorization: authorization()}
      return service(replay)
    } catch (refreshError) {
      // 续期失败说明 Refresh 也已失效，无法继续该请求，只能回到登录页。
      await expireSession()
      throw normalize(error, result, status, code)
    }
  }

  const normalized = normalize(error, result, status, code)
  Notification.error({title: normalized.message, duration: 5000})
  // 仅当前会话的 401 清理登录态；强制改密的 403 跳转改密页，其他 403/503 保留会话。
  if (status === 401 && !loginRequest && currentSession) {
    await expireSession()
  } else if (status === 403 && code === 'AUTH_PASSWORD_CHANGE_REQUIRED' && currentSession) {
    store.commit('SET_PASSWORD_CHANGE', true)
    await router.replace({ path: '/change-password' })
  }
  throw normalized
}

// 解包 ApiResult，同时兼容仍直接返回业务数据的接口。
export function handleApiResponse(response) {
  const result = response.data
  if (!isApiResult(result)) return result
  if (result.code === '0') return result.data
  const error = new Error(result.message)
  error.response = response
  return handleApiError(error)
}

service.interceptors.response.use(handleApiResponse, handleApiError)

export default service
