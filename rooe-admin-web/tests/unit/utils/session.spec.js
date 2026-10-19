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
      touch: jest.fn(() => Promise.resolve()),
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
  const contact = () => Number(localStorage.getItem('rooe.auth.lastRenewableServerContactAt'))
  const response = (status, headers = {'x-session-expires-in': '1800'}) => ({
    status,
    headers,
    config: {headers: {Authorization: `Bearer ${getToken()}`}}
  })

  it('only 2xx with the renewal header update the shared server contact time', () => {
    const initial = contact()
    now += 1000
    recordRenewal(response(200, {}));
    expect(contact()).toBe(initial)
    recordRenewal(response(403));
    expect(contact()).toBe(initial)
    recordRenewal(response(503));
    expect(contact()).toBe(initial)
    recordRenewal(response(401));
    expect(contact()).toBe(initial)
    recordRenewal(response(204));
    expect(contact()).toBe(now)
    const oldResponse = response(200)
    setToken('new-login')
    now += 1000
    recordRenewal(oldResponse);
    expect(contact()).toBe(now - 1000)
  })
  it('real interaction after five minutes touches, timers and synthetic events do not', async () => {
    stop = startSessionActivity(callbacks)
    now += 5 * 60 * 1000
    jest.advanceTimersByTime(1000)
    expect(callbacks.touch).not.toHaveBeenCalled()
    handlers.keydown({isTrusted: false})
    expect(callbacks.touch).not.toHaveBeenCalled()
    handlers.keydown({isTrusted: true})
    expect(callbacks.touch).toHaveBeenCalledTimes(1)
    handlers.pointerdown({isTrusted: true})
    expect(callbacks.touch).toHaveBeenCalledTimes(1)
    await Promise.resolve()
  })
  it('visibility and network recovery only check idle expiry and still clear state on logout failure', async () => {
    stop = startSessionActivity(callbacks)
    now += 21 * 60 * 1000
    handlers.online()
    await Promise.resolve()
    await Promise.resolve()
    expect(callbacks.logout).toHaveBeenCalledTimes(1)
    expect(callbacks.onLogout).toHaveBeenCalled()
    expect(callbacks.touch).not.toHaveBeenCalled()
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
  it('recovers from a synchronous touch failure and keeps the retry backoff', () => {
    callbacks.touch.mockImplementation(() => {
      throw new Error('offline')
    })
    stop = startSessionActivity(callbacks)
    now += 5 * 60 * 1000
    expect(() => handlers.keydown({isTrusted: true})).not.toThrow()
    now += 1000
    handlers.keydown({isTrusted: true})
    expect(callbacks.touch).toHaveBeenCalledTimes(1)
    now += 10000
    handlers.keydown({isTrusted: true})
    expect(callbacks.touch).toHaveBeenCalledTimes(2)
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
    stop = startSessionActivity(callbacks, {touchInterval: 1000})
    now += 2000
    handlers.keydown({isTrusted: true})
    expect(callbacks.touch).toHaveBeenCalledTimes(1)
    expect(callbacks.logout).not.toHaveBeenCalled()
  })
})
