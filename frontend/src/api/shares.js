import request from '@/utils/request'
export const createShare = (body) => request.post('/shares', body)
export const listShares = (params, signal) => request.get('/shares', { params, signal })
export const revokeShare = (id) => request.delete(`/shares/${id}`)
const publicConfig = (accessToken, signal) => ({ skipAuth: true, signal,
  headers: accessToken ? { 'X-Share-Access': accessToken } : {} })
export const shareInfo = (code, signal) => request.get(`/public/shares/${code}`, publicConfig('', signal))
export const unlockShare = (code, extractionCode, signal) => request.post(`/public/shares/${code}/access`,
  { extractionCode }, publicConfig('', signal))
export const sharedFiles = (code, accessToken, params, signal) => request.get(`/public/shares/${code}/files`,
  { ...publicConfig(accessToken, signal), params })
export const sharedPreview = (code, accessToken, id, signal) => request.get(`/public/shares/${code}/files/${id}/preview`,
  { ...publicConfig(accessToken, signal), responseType: 'blob', timeout: 120000 })
export async function sharedDownload(code, accessToken, id) {
  const result = await request.post(`/public/shares/${code}/files/${id}/download-ticket`, null,
    publicConfig(accessToken))
  if (!/^\/api\/public\/share-downloads\/[A-Za-z0-9_-]+$/.test(result.url)) throw new Error('下载地址无效')
  const link = document.createElement('a')
  link.href = result.url; link.rel = 'noreferrer'; document.body.appendChild(link); link.click(); link.remove()
}
