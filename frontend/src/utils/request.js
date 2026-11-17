import axios from 'axios'
import { getToken, removeToken } from '@/utils/token'
let unauthorizedHandler = null
export function setUnauthorizedHandler(handler) {
  unauthorizedHandler = handler
}
function handleUnauthorized(config) {
  if (config?.skipAuth || (config?.requestToken || '') !== getToken()) return
  removeToken()
  unauthorizedHandler?.()
}
const request = axios.create({ baseURL: '/api', timeout: 10000 })
request.interceptors.request.use((config) => {
  const token = config.skipAuth ? '' : getToken()
  config.requestToken = token
  if (token) config.headers.set('Authorization', `Bearer ${token}`)
  else config.headers.delete('Authorization')
  return config
})
request.interceptors.response.use(
  (response) => {
    if (response.config.responseType === 'blob') return response.data
    const body = response.data
    if (body?.code === 0) return body.data
    const error = new Error(body?.message || '请求失败')
    error.status = response.status
    if (body?.code === 401) handleUnauthorized(response.config)
    return Promise.reject(error)
  },
  async (originalError) => {
    if (axios.isCancel(originalError)) return Promise.reject(originalError)
    const status = originalError.response?.status || 0
    let data = originalError.response?.data
    if (data instanceof Blob) {
      try {
        data = JSON.parse(await data.text())
      } catch {
        data = null
      }
    }
    let message = data?.message
    if (!message) {
      if (['ECONNABORTED', 'ETIMEDOUT'].includes(originalError.code)) message = '请求超时，请重试'
      else if (status >= 500) message = '服务暂时不可用，请稍后重试'
      else if (status === 0) message = '网络连接失败，请重试'
      else message = '请求失败'
    }
    const error = new Error(message)
    error.status = status
    if (status === 401) handleUnauthorized(originalError.config)
    return Promise.reject(error)
  },
)
export default request