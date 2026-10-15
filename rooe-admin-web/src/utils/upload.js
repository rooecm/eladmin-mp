import axios from 'axios'
import { getToken } from '@/utils/auth'

export function upload(api, file) {
  var data = new FormData()
  data.append('file', file)
  const config = {
    headers: { 'Authorization': getToken() }
  }
  return axios.post(api, data, config).then(response => {
    const result = response.data
    if (result && result.code === '0' && Object.prototype.hasOwnProperty.call(result, 'data')) {
      response.data = result.data
    }
    return response
  })
}
