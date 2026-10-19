/* eslint-env jest */
jest.mock('@/api/login', () => ({login: jest.fn(), getInfo: jest.fn(), logout: jest.fn()}))
jest.mock('@/utils/auth', () => ({getToken: jest.fn(), setToken: jest.fn(), removeToken: jest.fn()}))
jest.mock('@/utils/session', () => ({initializeSession: jest.fn()}))
import user from '@/store/modules/user'
import {getInfo, login, logout} from '@/api/login'
import {getToken, removeToken, setToken} from '@/utils/auth'
import {initializeSession} from '@/utils/session'

describe('authentication store contract', () => {
  beforeEach(() => {
    jest.clearAllMocks();
    getToken.mockReturnValue('current-token')
  })
  it('rejects an unknown login status before persisting credentials', async () => {
    login.mockResolvedValue({status: 'FUTURE_STATUS', accessToken: 'token'})
    await expect(user.actions.Login({commit: jest.fn()}, {username: 'user', password: 'password'})).rejects.toThrow()
    expect(setToken).not.toHaveBeenCalled()
    expect(initializeSession).not.toHaveBeenCalled()
  })
  it('persists only a completed login and initializes the shared lifecycle', async () => {
    const result = {status: 'SUCCESS', accessToken: 'opaque', absoluteExpiresAt: 1000, mustChangePassword: true}
    login.mockResolvedValue(result)
    const commit = jest.fn()
    expect(await user.actions.Login({commit}, {username: 'user', password: 'password'})).toBe(result)
    expect(setToken).toHaveBeenCalledWith('opaque')
    expect(initializeSession).toHaveBeenCalledWith(1000)
    expect(commit).toHaveBeenCalledWith('SET_PASSWORD_CHANGE', true)
  })
  it('clears local authentication even when server logout fails', async () => {
    logout.mockRejectedValue(new Error('offline'))
    const commit = jest.fn()
    await expect(user.actions.LogOut({commit})).rejects.toThrow('offline')
    expect(removeToken).toHaveBeenCalled()
    expect(commit).toHaveBeenCalledWith('SET_TOKEN', '')
    expect(commit).toHaveBeenCalledWith('SET_ROLES', [])
  })
  it('does not let a completed logout clear a newer login', async () => {
    let finish
    logout.mockImplementation(() => new Promise(resolve => {
      finish = resolve
    }))
    const commit = jest.fn()
    const pending = user.actions.LogOut({commit})
    getToken.mockReturnValue('new-token')
    finish()
    await pending
    expect(removeToken).not.toHaveBeenCalled()
    expect(commit).not.toHaveBeenCalled()
  })
  it('discards user information returned after the session changes', async () => {
    let finish
    getInfo.mockImplementation(() => new Promise(resolve => {
      finish = resolve
    }))
    const commit = jest.fn()
    const pending = user.actions.GetInfo({commit})
    getToken.mockReturnValue('new-token')
    finish({user: {id: 1}, roles: ['user:list']})
    await expect(pending).rejects.toThrow('Session changed')
    expect(commit).not.toHaveBeenCalled()
  })
  it('loads the current session information', async () => {
    const info = {user: {id: 1}, roles: ['user:list'], mustChangePassword: false}
    getInfo.mockResolvedValue(info)
    const commit = jest.fn()
    expect(await user.actions.GetInfo({commit})).toBe(info)
    expect(commit).toHaveBeenCalledWith('SET_USER', info.user)
    expect(commit).toHaveBeenCalledWith('SET_LOAD_MENUS', true)
  })
})
