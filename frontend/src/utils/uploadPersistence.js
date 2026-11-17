const prefix = 'netdisk_upload_tasks_v2_'
const fields = ['id', 'clientId', 'sessionId', 'name', 'size', 'parentId', 'folderName', 'hash',
  'status', 'progress', 'hashProgress', 'partCount', 'chunkCount', 'expiresAt', 'instant', 'createdAt',
  'error', 'restartRequired', 'retryable']
export function readTasks(userId) {
  try {
    const data = JSON.parse(localStorage.getItem(prefix + userId) || '[]')
    if (!Array.isArray(data)) return []
    return data.filter(t => typeof t.id === 'string' && typeof t.clientId === 'string'
      && typeof t.name === 'string' && Number.isSafeInteger(t.size) && t.size >= 0).slice(-100)
      .map(t => ({ ...Object.fromEntries(fields.map(key => [key, t[key]])),
        file: null, verifiedFile: false, speed: 0, stopping: false, cancelPending: false,
        status: ['done', 'cancelled'].includes(t.status) ? t.status : 'needs-file' }))
  } catch { return [] }
}
export function saveTasks(userId, tasks) {
  if (!userId) return ''
  try {
    localStorage.setItem(prefix + userId, JSON.stringify(tasks.slice(-100).map(t =>
      Object.fromEntries(fields.map(key => [key, t[key]])))))
    return ''
  } catch { return '浏览器未能保存本地任务记录；服务器已建立的分片任务仍可同步恢复。' }
}
