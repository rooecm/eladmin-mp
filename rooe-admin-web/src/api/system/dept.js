import request from '@/utils/request'

export function getDepts(params) {
  return request({
    url: 'api/dept',
    method: 'get',
    params
  })
}

export function getDeptSuperior(ids, exclude) {
  exclude = exclude !== undefined ? exclude : false
  return request({
    url: 'api/dept/superior',
    method: 'post',
    data: {
      ids: Array.isArray(ids) ? ids : [ids],
      exclude
    }
  })
}

export function add(data) {
  return request({
    url: 'api/dept',
    method: 'post',
    data
  })
}

export function del(ids) {
  return request({
    url: 'api/dept',
    method: 'delete',
    data: {
      ids: Array.isArray(ids) ? ids : [ids]
    }
  })
}

export function edit(data) {
  return request({
    url: 'api/dept',
    method: 'put',
    data
  })
}

export default { add, edit, del, getDepts, getDeptSuperior }
