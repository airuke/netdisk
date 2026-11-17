import request from '@/utils/request'
export const uploadConfig = () => request.get('/uploads/config')
export const listUploadSessions = () => request.get('/uploads')
export const checkInstant = (body, signal) => request.post('/uploads/instant', body, { signal, timeout: 30000 })
export const createUpload = (body, signal) => request.post('/uploads', body, { signal })
export const getUpload = (id, signal) => request.get(`/uploads/${id}`, { signal })
export const cancelUpload = (clientId) => request.delete(`/uploads/by-client/${clientId}`)
export const completeUpload = (id, signal) => request.post(`/uploads/${id}/complete`, null, { signal, timeout: 45 * 60 * 1000 })
export function uploadPart(id, index, blob, sha256, signal, onUploadProgress) {
  const data = new FormData()
  data.append('file', blob, `part-${index}`)
  data.append('sha256', sha256)
  return request.post(`/uploads/${id}/parts/${index}`, data, { signal, onUploadProgress, timeout: 5 * 60 * 1000 })
}
