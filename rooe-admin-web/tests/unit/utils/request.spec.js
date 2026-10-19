/* eslint-env jest */
import request from '@/utils/request'
import axios from 'axios'
import {download, initData} from '@/api/data'
import {add, del, edit, getDepts, getDeptSuperior} from '@/api/system/dept'
import {upload} from '@/utils/upload'
import {Notification} from 'element-ui'
import store from '@/store'
import router from '@/router/routers'
import {removeToken, setToken} from '@/utils/auth'

// Jest 23 使用 Axios 的 CommonJS 入口，仍通过真实拦截器验证响应
jest.mock('axios', () => jest.requireActual('axios/dist/browser/axios.cjs'))
jest.mock('@/router/routers', () => ({push: jest.fn(), replace: jest.fn()}))
jest.mock('@/store', () => ({dispatch: jest.fn(() => Promise.resolve()), commit: jest.fn()}))
jest.mock('element-ui', () => ({ Notification: { error: jest.fn() }}))

const page = { content: [{ id: 1, name: '部门', hasChildren: true }], totalElements: 1 }

function respondWith(data) {
  request.defaults.adapter = config => Promise.resolve({
    data,
    status: 200,
    statusText: 'OK',
    headers: {},
    config
  })
}

describe('ApiResult 响应适配', () => {
  const originalAdapter = request.defaults.adapter
  const originalAxiosAdapter = axios.defaults.adapter

  beforeAll(() => {
    jest.spyOn(window.location, 'reload').mockImplementation(() => {
    })
  })

  afterEach(() => {
    request.defaults.adapter = originalAdapter
    axios.defaults.adapter = originalAxiosAdapter
    jest.clearAllMocks()
    removeToken()
  })

  it('部门列表的通用 CRUD 查询和部门树查询都能读取分页数据', async() => {
    respondWith({ code: '0', message: 'success', data: page })

    expect(await initData('api/dept', { page: 0 })).toEqual(page)
    expect(await getDepts({ pid: 1 })).toEqual(page)
    expect(Notification.error).not.toHaveBeenCalled()
  })

  it('部门、用户、角色页面的上级部门查询能读取树数据', async() => {
    respondWith({ code: '0', message: 'success', data: page })

    expect(await getDeptSuperior([1], true)).toEqual(page)
  })

  it('新增、修改、删除成功时允许 data 为 null', async() => {
    respondWith({ code: '0', message: 'success', data: null })

    expect(await add({ name: '部门' })).toBeNull()
    expect(await edit({ id: 1, name: '部门' })).toBeNull()
    expect(await del([1])).toBeNull()
    expect(Notification.error).not.toHaveBeenCalled()
  })

  it('保留尚未使用 ApiResult 的接口响应', async() => {
    respondWith(page)
    expect(await initData('api/users', {})).toEqual(page)

    const login = { token: 'token', user: { id: 1 }}
    respondWith(login)
    expect(await request({ url: 'auth/login', method: 'post' })).toEqual(login)
  })

  it('保留文件下载的 Blob 响应', async() => {
    const file = new Blob(['file'], { type: 'application/octet-stream' })
    respondWith(file)

    expect(await download('api/dept/download', {})).toBe(file)
  })

  it.each([true, false])('直接上传保留完整 Axios 响应（统一包装：%s）', async wrapped => {
    const file = { id: 1, type: 'image', realName: 'test.png' }
    const response = {
      data: wrapped ? {code: '0', message: 'success', data: file} : file,
      status: 201, statusText: 'Created', headers: {'x-upload-id': 'upload-1'}
    }
    axios.defaults.adapter = config => Promise.resolve({...response, config})

    const result = await upload('/api/localStorage/pictures', new Blob(['file']))
    expect(result.data).toEqual(file)
    expect(result.status).toBe(201)
    expect(result.headers['x-upload-id']).toBe('upload-1')
    expect(result.config.url).toBe('/api/localStorage/pictures')
  })

  it('业务失败时显示消息并拒绝 Promise，避免触发成功处理', async() => {
    const failure = { code: 'DEPT_IN_USE', message: '部门已被关联', data: null }
    respondWith(failure)

    await expect(del([1])).rejects.toMatchObject({
      message: failure.message,
      code: failure.code,
      response: { data: failure }
    })
    expect(Notification.error).toHaveBeenCalledWith({ title: failure.message, duration: 5000 })
  })

  it('HTTP 错误仍然使用服务端的错误消息', async() => {
    const data = { code: '400', message: '部门名称不能为空', data: null }
    const error = Object.assign(new Error('Request failed'), { response: { status: 400, data }})
    request.defaults.adapter = () => Promise.reject(error)

    await expect(add({})).rejects.toBe(error)
    expect(Notification.error).toHaveBeenCalledWith({ title: data.message, duration: 5000 })
  })

  function rejectWith(status, data, url = 'api/test') {
    const error = Object.assign(new Error('Request failed'), {response: {status, data}})
    request.defaults.adapter = config => {
      error.config = {...config, url}
      error.response.config = error.config
      return Promise.reject(error)
    }
    return error
  }

  it('HTTP 错误将业务码和提示信息交给调用方', async () => {
    rejectWith(400, {code: 'COMMON_1010', message: '部门存在用户关联', data: null})
    await expect(del([1])).rejects.toMatchObject({code: 'COMMON_1010', message: '部门存在用户关联', httpStatus: 400})
    expect(Notification.error).toHaveBeenCalledTimes(1)
  })

  it('用户名密码错误时展示消息并保持登录流程', async () => {
    setToken('existing-token')
    rejectWith(401, {code: 'AUTH_1001', message: '用户名或密码错误', data: null}, 'auth/login')
    await expect(request({url: 'auth/login', method: 'post'})).rejects.toMatchObject({code: 'AUTH_1001'})
    expect(store.dispatch).not.toHaveBeenCalled()
    expect(Notification.error).toHaveBeenCalledWith({title: '用户名或密码错误', duration: 5000})
  })

  it('会话失效时执行重新登录流程', async () => {
    setToken('existing-token')
    rejectWith(401, {code: 'AUTH_1005', message: '登录状态已失效，请重新登录', data: null})
    await expect(getDepts({})).rejects.toMatchObject({code: 'AUTH_1005'})
    expect(store.dispatch).toHaveBeenCalledWith('ClearAuth')
  })

  it('无权限时保留业务码并跳转权限提示页', async () => {
    rejectWith(403, {code: 'PERMISSION_1001', message: '没有权限执行此操作', data: null})
    await expect(getDepts({})).rejects.toMatchObject({code: 'PERMISSION_1001'})
    expect(store.dispatch).not.toHaveBeenCalled()
  })

  it('业务权限拒绝使用统一权限码并保留当前页面', async () => {
    rejectWith(400, {code: 'PERMISSION_1001', message: '不能授予超出自身的数据权限', data: null})
    await expect(getDepts({})).rejects.toMatchObject({code: 'PERMISSION_1001', httpStatus: 400})
    expect(router.push).not.toHaveBeenCalled()
    expect(store.dispatch).not.toHaveBeenCalled()
    expect(Notification.error).toHaveBeenCalledWith({title: '不能授予超出自身的数据权限', duration: 5000})
  })

  it('验证码错误和过期使用统一验证码码并保持登录流程', async () => {
    setToken('existing-token')
    rejectWith(400, {code: 'AUTH_1007', message: '验证码不存在或已过期', data: null}, 'auth/login')
    await expect(request({url: 'auth/login', method: 'post'})).rejects.toMatchObject({code: 'AUTH_1007'})
    expect(store.dispatch).not.toHaveBeenCalled()
  })

  it('网络失败和超时不会因 response 缺失而抛出二次异常', async () => {
    const networkError = new Error('Network Error')
    request.defaults.adapter = () => Promise.reject(networkError)
    await expect(getDepts({})).rejects.toBe(networkError)
    expect(Notification.error).toHaveBeenLastCalledWith({title: '接口请求失败', duration: 5000})
    const timeout = new Error('timeout of 5000ms exceeded')
    request.defaults.adapter = () => Promise.reject(timeout)
    await expect(getDepts({})).rejects.toBe(timeout)
    expect(Notification.error).toHaveBeenLastCalledWith({title: '网络请求超时', duration: 5000})
  })

  it.each([401, 403])('旧会话的 %s 响应不能清理或修改新会话', async status => {
    setToken('old-token')
    let rejectRequest
    const started = new Promise(resolve => {
      request.defaults.adapter = config => new Promise((resolveResponse, reject) => {
        rejectRequest = () => reject(Object.assign(new Error('Request failed'), {
          config,
          response: {
            status,
            config,
            data: {code: status === 401 ? 'AUTH_SESSION_INVALID' : 'AUTH_PASSWORD_CHANGE_REQUIRED', message: 'expired'}
          }
        }))
        resolve()
      })
    })
    const pending = getDepts({})
    await started
    setToken('new-token')
    rejectRequest()
    await expect(pending).rejects.toMatchObject({httpStatus: status})
    expect(store.dispatch).not.toHaveBeenCalled()
    expect(store.commit).not.toHaveBeenCalled()
    expect(router.replace).not.toHaveBeenCalled()
  })

  it('当前会话的强制改密响应仍跳转到改密页', async () => {
    setToken('current-token')
    rejectWith(403, {code: 'AUTH_PASSWORD_CHANGE_REQUIRED', message: '请修改密码'})
    await expect(getDepts({})).rejects.toMatchObject({httpStatus: 403})
    expect(store.commit).toHaveBeenCalledWith('SET_PASSWORD_CHANGE', true)
    expect(router.replace).toHaveBeenCalledWith({path: '/change-password'})
  })

  it('下载失败会等待 Blob JSON 解析后再返回业务错误', async () => {
    const data = {code: 'COMMON_1002', message: '文件不存在或已被删除', data: null}
    rejectWith(404, new Blob([JSON.stringify(data)], {type: 'application/json'}))
    await expect(download('api/storage/download', {})).rejects.toMatchObject({code: data.code, message: data.message})
    expect(Notification.error).toHaveBeenCalledTimes(1)
  })

  it('下载错误返回非 JSON 时仍保留原始错误', async () => {
    const error = rejectWith(502, new Blob(['bad gateway'], {type: 'text/html'}))
    await expect(download('api/storage/download', {})).rejects.toBe(error)
  })

  it('直接上传出现业务失败或 HTTP 失败时拒绝 Promise', async () => {
    const data = {code: 'STORAGE_1003', message: '文件上传失败，请稍后重试', data: null}
    axios.defaults.adapter = config => Promise.resolve({data, status: 200, headers: {}, config})
    await expect(upload('/api/localStorage/pictures', new Blob(['file']))).rejects.toMatchObject({code: data.code})
    axios.defaults.adapter = () => Promise.reject(Object.assign(new Error('Request failed'), {
      response: {
        status: 503,
        data
      }
    }))
    await expect(upload('/api/localStorage/pictures', new Blob(['file']))).rejects.toMatchObject({code: data.code})
    expect(Notification.error).toHaveBeenCalledTimes(2)
  })
})
