/* eslint-env jest */
import {initializeSession, recordRenewal, startSessionActivity} from '@/utils/session'
import {getToken, setToken, TokenKey} from '@/utils/auth'

describe('opaque session browser lifecycle', () => {
  let now
  let stop
  let callbacks
  let handlers
  beforeEach(() => {
    const values = new Map()
    Object.defineProperty(window, 'localStorage', {
      configurable: true, value: {
        getItem: key => values.has(key) ? values.get(key) : null,
        setItem: (key, value) => values.set(key, String(value)),
        removeItem: key => values.delete(key)
      }
    })
    now = 1000000
    jest.spyOn(Date, 'now').mockImplementation(() => now)
    jest.useFakeTimers()
    setToken('opaque-token')
    initializeSession(now / 1000 + 3600)
    callbacks = {
      logout: jest.fn(() => Promise.reject(new Error('offline'))),
      onLogout: jest.fn(),
      onWarning: jest.fn()
    }
    handlers = {}
    jest.spyOn(window, 'addEventListener').mockImplementation((type, listener) => {
      handlers[type] = listener
    })
  })
  afterEach(() => {
    if (stop) stop()
    stop = null
    jest.restoreAllMocks()
    jest.useRealTimers()
  })
  const active = () => Number(localStorage.getItem('rooe.auth.lastActiveAt'))
  const response = (status, config = {headers: {Authorization: `Bearer ${getToken()}`}}) => ({status, config})

  it('only successful current-session responses advance the shared activity time', () => {
    const initial = active()
    now += 1000
    recordRenewal(response(500))
    expect(active()).toBe(initial)
    recordRenewal(response(401))
    expect(active()).toBe(initial)
    recordRenewal(response(200))
    expect(active()).toBe(now)
    const oldResponse = response(200)
    setToken('new-login')
    now += 1000
    recordRenewal(oldResponse)
    expect(active()).toBe(now - 1000)
  })

  it('interaction only extends activity and never issues a keep-alive request', () => {
    stop = startSessionActivity(callbacks)
    now += 5 * 60 * 1000
    jest.advanceTimersByTime(1000)
    expect(callbacks.logout).not.toHaveBeenCalled()
    handlers.keydown({isTrusted: false})
    expect(active()).not.toBe(now)
    handlers.keydown({isTrusted: true})
    expect(active()).toBe(now)
  })

  it('visibility and network recovery only check idle expiry and still clear state on logout failure', async () => {
    stop = startSessionActivity(callbacks)
    now += 21 * 60 * 1000
    handlers.online()
    await Promise.resolve()
    await Promise.resolve()
    expect(callbacks.logout).toHaveBeenCalledTimes(1)
    expect(callbacks.onLogout).toHaveBeenCalled()
  })

  it('warns before expiry and a storage logout synchronizes other tabs', () => {
    stop = startSessionActivity(callbacks)
    now += 19 * 60 * 1000
    jest.advanceTimersByTime(1000)
    expect(callbacks.onWarning).toHaveBeenCalledTimes(1)
    handlers.storage({key: 'rooe.auth.other', newValue: null})
    expect(callbacks.onLogout).not.toHaveBeenCalled()
    handlers.storage({key: TokenKey, newValue: null})
    expect(callbacks.onLogout).toHaveBeenCalledWith(false)
    handlers.storage({key: TokenKey, newValue: 'new-token'})
    expect(callbacks.onLogout).toHaveBeenCalledWith(true)
  })

  it('warns only once near absolute expiry despite repeated trusted interaction', () => {
    initializeSession(now / 1000 + 90)
    stop = startSessionActivity(callbacks)
    handlers.keydown({isTrusted: true})
    now += 1000
    handlers.pointermove({isTrusted: true})
    jest.advanceTimersByTime(1000)
    expect(callbacks.onWarning).toHaveBeenCalledTimes(1)
  })

  it('allows a new idle warning after interaction extends the idle deadline', () => {
    stop = startSessionActivity(callbacks)
    now += 19 * 60 * 1000
    handlers.keydown({isTrusted: true})
    jest.advanceTimersByTime(1000)
    now += 19 * 60 * 1000
    jest.advanceTimersByTime(1000)
    expect(callbacks.onWarning).toHaveBeenCalledTimes(2)
  })

  it('does not clear a new login when a previous expiry logout finishes', async () => {
    let finish
    callbacks.logout.mockImplementation(() => new Promise(resolve => {
      finish = resolve
    }))
    stop = startSessionActivity(callbacks)
    now += 21 * 60 * 1000
    handlers.online()
    setToken('new-token')
    initializeSession(now / 1000 + 3600)
    finish()
    await Promise.resolve()
    expect(callbacks.onLogout).not.toHaveBeenCalled()
  })

  it('recovers the stopping flag even when the local logout callback fails', async () => {
    callbacks.onLogout.mockRejectedValue(new Error('navigation failed'))
    stop = startSessionActivity(callbacks)
    now += 21 * 60 * 1000
    handlers.online()
    for (let i = 0; i < 5; i++) await Promise.resolve()
    handlers.online()
    expect(callbacks.logout).toHaveBeenCalledTimes(2)
    for (let i = 0; i < 5; i++) await Promise.resolve()
  })

  it('merges partial activity settings with defaults', () => {
    stop = startSessionActivity(callbacks, {idleTimeout: 60 * 1000})
    now += 61 * 1000
    handlers.online()
    expect(callbacks.logout).toHaveBeenCalledTimes(1)
  })
})
