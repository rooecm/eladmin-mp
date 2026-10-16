import axios from 'axios'
import { getToken } from '@/utils/auth'
import { handleApiError, isApiResult } from '@/utils/request'

export function upload(api, file) {
  var data = new FormData()
  data.append('file', file)
  const config = {
    headers: { 'Authorization': getToken() }
  }
  return axios.post(api, data, config).then(response => {
    const result = response.data
    if (isApiResult(result)) {
      if (result.code !== '0') {
        const error = new Error(result.message)
        error.response = response
        return handleApiError(error)
      }
      response.data = result.data
    }
    return response
  }, handleApiError)
}
