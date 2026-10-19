import request from '@/utils/request'

export function add(data) {
  return request({
    url: 'api/users',
    method: 'post',
    data
  })
}

export function del(ids) {
  return request({
    url: 'api/users',
    method: 'delete',
    data: {
      ids: Array.isArray(ids) ? ids : [ids]
    }
  })
}

export function resetPwd(ids) {
  return request({
    url: 'api/users/resetPwd',
    method: 'put',
    data: {
      ids: Array.isArray(ids) ? ids : [ids]
    }
  })
}

export function edit(data) {
  return request({
    url: 'api/users',
    method: 'put',
    data
  })
}

export function editUser(data) {
  return request({
    url: 'api/users/center',
    method: 'put',
    data
  })
}

export function updatePass(user) {
  const data = {
    oldPassword: user.oldPass,
    newPassword: user.newPass
  }
  return request({
    url: 'api/auth/password',
    method: 'post',
    data
  })
}

export default { add, edit, del, resetPwd }

