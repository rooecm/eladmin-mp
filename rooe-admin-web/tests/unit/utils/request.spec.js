/* eslint-env jest */
import request from '@/utils/request'
import axios from 'axios'
import { initData, download } from '@/api/data'
import { getDepts, getDeptSuperior, add, edit, del } from '@/api/system/dept'
import { upload } from '@/utils/upload'
import { Notification } from 'element-ui'

// Jest 23 使用 Axios 的 CommonJS 入口，仍通过真实拦截器验证响应
jest.mock('axios', () => jest.requireActual('axios/dist/browser/axios.cjs'))
jest.mock('@/router/routers', () => ({ push: jest.fn() }))
jest.mock('@/store', () => ({}))
jest.mock('@/utils/auth', () => ({ getToken: jest.fn() }))
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

  afterEach(() => {
    request.defaults.adapter = originalAdapter
    axios.defaults.adapter = originalAxiosAdapter
    jest.clearAllMocks()
  })

  it('部门列表的通用 CRUD 查询和部门树查询都能读取分页数据', async() => {
    respondWith({ status: 200, code: '0', message: 'success', data: page })

    expect(await initData('api/dept', { page: 0 })).toEqual(page)
    expect(await getDepts({ pid: 1 })).toEqual(page)
    expect(Notification.error).not.toHaveBeenCalled()
  })

  it('部门、用户、角色页面的上级部门查询能读取树数据', async() => {
    respondWith({ status: 200, code: '0', message: 'success', data: page })

    expect(await getDeptSuperior([1], true)).toEqual(page)
  })

  it('新增、修改、删除成功时允许 data 为 null', async() => {
    respondWith({ status: 200, code: '0', message: 'success', data: null })

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

  it('直接使用 Axios 的图片上传也能读取业务数据', async() => {
    const file = { id: 1, type: 'image', realName: 'test.png' }
    axios.defaults.adapter = config => Promise.resolve({
      data: { status: 200, code: '0', message: 'success', data: file },
      status: 200,
      statusText: 'OK',
      headers: {},
      config
    })

    expect((await upload('/api/localStorage/pictures', new Blob(['file']))).data).toEqual(file)
  })

  it('业务失败时显示消息并拒绝 Promise，避免触发成功处理', async() => {
    const failure = { status: 400, code: 'DEPT_IN_USE', message: '部门已被关联', data: null }
    respondWith(failure)

    await expect(del([1])).rejects.toMatchObject({
      message: failure.message,
      code: failure.code,
      response: { data: failure }
    })
    expect(Notification.error).toHaveBeenCalledWith({ title: failure.message, duration: 5000 })
  })

  it('HTTP 错误仍然使用服务端的错误消息', async() => {
    const data = { status: 400, code: '400', message: '部门名称不能为空', data: null }
    const error = Object.assign(new Error('Request failed'), { response: { status: 400, data }})
    request.defaults.adapter = () => Promise.reject(error)

    await expect(add({})).rejects.toBe(error)
    expect(Notification.error).toHaveBeenCalledWith({ title: data.message, duration: 5000 })
  })
})
