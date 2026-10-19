import axios from 'axios'
import { authorization } from '@/utils/auth'
import { handleApiError, handleApiResponse } from '@/utils/request'

export function upload(api, file) {
  const data = new FormData()
  data.append('file', file)
  return axios.post(api, data, { headers: { Authorization: authorization() }}).then(async response => {
    // 与普通请求共用响应处理，但保留完整 Axios 响应，供上传调用方读取状态与响应头。
    response.data = await handleApiResponse(response)
    return response
  }, handleApiError)
}
