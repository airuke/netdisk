import request from '@/utils/request'
export const listFiles = (params, signal) => request.get('/files', { params, signal })
export const fileSummary = () => request.get('/files/summary')
export const createFolder = (parentId, name) => request.post('/folders', { parentId, name })
export const renameFile = (id, name) => request.patch(`/files/${id}/name`, { name })
export const moveFile = (id, parentId) => request.patch(`/files/${id}/parent`, { parentId })
export const trashFile = (id) => request.delete(`/files/${id}`)
export const restoreFile = (id) => request.post(`/recycle-bin/${id}/restore`)
export const purgeFile = (id) => request.delete(`/recycle-bin/${id}`)
export const previewFile = (id, signal) =>
  request.get(`/files/${id}/preview`, {
    responseType: 'blob',
    signal,
    timeout: 120000,
  })
export async function downloadFile(id) {
  const data = await request.post(`/files/${id}/download-ticket`)
  // 使用短时文件票据触发浏览器流式下载；不把 JWT 放进 URL。
  if (!/^\/api\/downloads\/[A-Za-z0-9_-]+$/.test(data.url)) throw new Error('下载地址无效')
  const link = document.createElement('a')
  link.href = data.url
  link.rel = 'noreferrer'
  document.body.appendChild(link)
  link.click()
  link.remove()
}
export function uploadFile(file, parentId, signal, onUploadProgress, sha256) {
  const body = new FormData()
  body.append('file', file)
  body.append('parentId', parentId)
  if (sha256) body.append('sha256', sha256)
  return request.post('/files/upload', body, {
    signal,
    onUploadProgress,
    timeout: 15 * 60 * 1000,
  })
}
