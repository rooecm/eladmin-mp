/* eslint-env jest */
jest.mock('@/router/routers', () => ({beforeEach: jest.fn(), afterEach: jest.fn(), addRoutes: jest.fn()}))
jest.mock('@/store', () => ({dispatch: jest.fn(), commit: jest.fn()}))
jest.mock('@/api/system/menu', () => ({buildMenus: jest.fn()}))
jest.mock('@/store/modules/permission', () => ({filterAsyncRouter: routes => routes}))
jest.mock('nprogress', () => ({configure: jest.fn(), done: jest.fn()}))
jest.mock('nprogress/nprogress.css', () => ({}))
import {loadMenus} from '@/router/index'
import router from '@/router/routers'
import store from '@/store'
import {buildMenus} from '@/api/system/menu'
import {removeToken, setToken} from '@/utils/auth'
import NProgress from 'nprogress'

describe('authenticated menu loading', () => {
  const to = {path: '/users'}
  let next
  beforeEach(() => {
    jest.clearAllMocks()
    setToken('current-token')
    next = jest.fn()
    buildMenus.mockResolvedValue([{path: '/users'}])
    store.dispatch.mockImplementation(() => Promise.resolve())
  })
  afterEach(() => removeToken())

  it('awaits both route and sidebar generation before continuing navigation', async () => {
    let finish
    let generated
    const started = new Promise(resolve => {
      generated = resolve
    })
    store.dispatch.mockImplementation(action => action === 'SetSidebarRouters'
      ? new Promise(resolve => {
        finish = resolve;
        generated()
      }) : Promise.resolve())
    const pending = loadMenus(next, to)
    await started
    expect(router.addRoutes).not.toHaveBeenCalled()
    finish()
    await pending
    expect(router.addRoutes).toHaveBeenCalledTimes(1)
    expect(next).toHaveBeenCalledWith({...to, replace: true})
  })

  it.each(['GenerateRoutes', 'SetSidebarRouters'])('handles %s failure and allows a later retry', async action => {
    store.dispatch.mockImplementation(name => name === action ? Promise.reject(new Error('failed')) : Promise.resolve())
    await loadMenus(next, to)
    expect(store.commit).toHaveBeenCalledWith('SET_LOAD_MENUS', true)
    expect(router.addRoutes).not.toHaveBeenCalled()
    expect(next).toHaveBeenCalledWith(false)
    expect(NProgress.done).toHaveBeenCalled()
  })

  it('does not install menus from a previous session', async () => {
    let finish
    buildMenus.mockImplementation(() => new Promise(resolve => {
      finish = resolve
    }))
    const pending = loadMenus(next, to)
    setToken('new-token')
    finish([{path: '/old-user'}])
    await pending
    expect(store.dispatch).not.toHaveBeenCalled()
    expect(router.addRoutes).not.toHaveBeenCalled()
    expect(next).toHaveBeenCalledWith(false)
    expect(NProgress.done).toHaveBeenCalled()
  })

  it('does not reset a new session menu flag when an old request fails', async () => {
    let fail
    buildMenus.mockImplementation(() => new Promise((resolve, reject) => {
      fail = reject
    }))
    const pending = loadMenus(next, to)
    setToken('new-token')
    fail(new Error('old request failed'))
    await pending
    expect(store.commit).not.toHaveBeenCalled()
    expect(next).toHaveBeenCalledWith(false)
  })

  it('stops navigation if logout occurs while routes are being generated', async () => {
    let finish
    let generated
    const started = new Promise(resolve => {
      generated = resolve
    })
    store.dispatch.mockImplementation(action => action === 'GenerateRoutes'
      ? new Promise(resolve => {
        finish = resolve;
        generated()
      }) : Promise.resolve())
    const pending = loadMenus(next, to)
    await started
    removeToken()
    finish()
    await pending
    expect(router.addRoutes).not.toHaveBeenCalled()
    expect(next).toHaveBeenCalledWith(false)
  })
})
