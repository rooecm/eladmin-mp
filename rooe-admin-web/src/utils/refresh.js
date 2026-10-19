import axios from 'axios'
import Config from '@/settings'
import {getRefreshToken, setCredentials} from '@/utils/auth'

// 续期必须使用独立实例，避免走业务拦截器导致 401 递归与重复通知。
// 该实例同时导出，便于测试直接驱动真实的续期调用路径。
export const refreshClient = axios.create({
  baseURL: process.env.NODE_ENV === 'production' ? process.env.VUE_APP_BASE_API : '/',
  timeout: Config.timeout
})

// 单飞：并发 401 共用同一次续期，否则 Refresh 轮换会互相作废。
let inFlight = null

export function refreshCredentials() {
  if (inFlight) return inFlight
  const token = getRefreshToken()
  if (!token) return Promise.reject(new Error('Missing refresh token'))
  const pending = refreshClient.post('api/auth/refresh', {refreshToken: token})
    .then(response => {
      const body = response.data || {}
      // 只接受同时具备两把凭证的响应，避免半套状态写入本地。
      if (body.code !== '0' || !body.data || !body.data.accessToken || !body.data.refreshToken) {
        throw new Error('Refresh was rejected')
      }
      setCredentials(body.data.accessToken, body.data.refreshToken)
      return body.data
    })
  // 单飞标记必须在本次续期结束后释放，失败时也允许后续请求重新尝试。
  inFlight = pending.finally(() => {
    inFlight = null
  })
  return inFlight
}

// 仅用于测试隔离：丢弃进行中的续期标记，使下一个用例从干净状态开始。
export function resetRefreshState() {
  inFlight = null
}

export function isRefreshRequest(config) {
  return /(?:^|\/)auth\/refresh(?:\?|$)/.test((config && config.url) || '')
}
