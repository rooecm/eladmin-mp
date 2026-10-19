import { getToken, isCurrentSessionRequest, TokenKey } from '@/utils/auth'

const prefix = 'rooe.auth.'
export const defaults = { touchInterval: 5 * 60 * 1000, idleTimeout: 20 * 60 * 1000, warningBefore: 2 * 60 * 1000 }
const read = name => Number(localStorage.getItem(prefix + name) || 0)
const write = (name, value) => localStorage.setItem(prefix + name, String(value))

export function initializeSession(absoluteExpiresAt, now = Date.now()) {
  // 登录接口的绝对到期时间单位为秒，浏览器统一以毫秒保存并跨标签页共享。
  write('lastActiveAt', now)
  write('lastRenewableServerContactAt', now)
  write('absoluteExpiresAt', absoluteExpiresAt * 1000)
  write('lastTouchAt', 0)
}

export function recordRenewal(response, now = Date.now()) {
  const ttl = Number((response.headers || {})['x-session-expires-in'])
  // 只有当前会话的 2xx 响应携带续期头才算有效联系，失败或旧会话响应不推进时间。
  if (response.status >= 200 && response.status < 300 && ttl > 0 && isCurrentSessionRequest(response.config)) {
    write('lastRenewableServerContactAt', now)
  }
}

// 监听真实用户活动与跨标签页变化；返回清理函数，供组件卸载时解除所有监听。
export function startSessionActivity({ touch, logout, onLogout, onWarning }, options = {}) {
  const settings = { ...defaults, ...options }
  let stopping = false
  let warned = false
  let touching = false
  let currentToken = getToken()
  const notifyLogout = async changed => {
    try {
      await onLogout(changed)
    } catch (e) { /* 事件回调异常在此收口，避免未处理的 Promise 拒绝。 */
    }
  }
  const stop = async() => {
    const token = getToken()
    if (stopping || !token) return
    stopping = true
    try {
      await logout()
    } catch (e) { /* 后端注销失败时仍清理当前本地会话。 */
    } finally {
      // 等待注销期间可能已重新登录，不能清理新的 Token。
      if (getToken() === token) await notifyLogout()
      stopping = false
    }
  }
  const keepAlive = async() => {
    touching = true
    try {
      await touch()
    } catch (e) { /* 失败后仅在后续真实活动中重试，仍受触发间隔约束。 */
    } finally {
      touching = false
    }
  }
  const check = () => {
    const token = getToken()
    if (token !== currentToken) {
      currentToken = token
      warned = false
    }
    if (!token) return false
    const now = Date.now()
    // 本地空闲截止与服务端绝对截止取较早者，续期不能越过绝对生命周期。
    const remaining = Math.min(read('lastActiveAt') + settings.idleTimeout, read('absoluteExpiresAt')) - now
    if (remaining <= 0) {
      stop()
      return false
    }
    if (remaining > settings.warningBefore) warned = false
    else if (!warned) {
      warned = true
      onWarning()
    }
    return true
  }
  const interact = event => {
    // 忽略脚本合成事件，后台定时器或轮询不能冒充用户活动维持登录。
    if (!event.isTrusted || !check()) return
    const now = Date.now()
    if (now - read('lastActiveAt') >= 1000) write('lastActiveAt', now)
    if (!touching && now - read('lastRenewableServerContactAt') >= settings.touchInterval && now - read('lastTouchAt') >= 10000) {
      // 普通成功请求已续期时无需 touch；提前记录触发时间，失败也保留重试间隔。
      write('lastTouchAt', now)
      keepAlive()
    }
  }
  const sync = event => {
    if (event.key === TokenKey) notifyLogout(!!event.newValue)
  }
  const events = ['keydown', 'pointerdown', 'pointermove', 'touchstart', 'wheel']
  events.forEach(type => window.addEventListener(type, interact, { passive: true }))
  window.addEventListener('storage', sync)
  window.addEventListener('online', check)
  document.addEventListener('visibilitychange', check)
  // 定时器仅检查本地过期状态，touch 只能由真实用户活动触发。
  const timer = window.setInterval(check, 1000)
  return () => {
    clearInterval(timer)
    events.forEach(type => window.removeEventListener(type, interact))
    window.removeEventListener('storage', sync)
    window.removeEventListener('online', check)
    document.removeEventListener('visibilitychange', check)
  }
}
