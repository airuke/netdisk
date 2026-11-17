export function formatSize(value = 0) {
  if (value === 0) return '0 B'
  const units = ['B', 'KB', 'MB', 'GB', 'TB']
  const index = Math.min(Math.floor(Math.log(value) / Math.log(1024)), units.length - 1)
  return `${(value / 1024 ** index).toFixed(index ? 1 : 0)} ${units[index]}`
}
export function formatDate(value) {
  if (!value) return '—'
  const date = new Date(value)
  return Number.isNaN(date.getTime())
    ? '—'
    : new Intl.DateTimeFormat('zh-CN', {
        month: '2-digit',
        day: '2-digit',
        hour: '2-digit',
        minute: '2-digit',
        hour12: false,
      }).format(date)
}
export function fileIcon(file) {
  return (
    { FOLDER: 'folder', IMAGE: 'image', DOCUMENT: 'file', VIDEO: 'video', AUDIO: 'music' }[
      file.category
    ] || 'file'
  )
}
export function typeLabel(file) {
  if (file.type === 'FOLDER') return '文件夹'
  const extension = file.name.includes('.') ? file.name.split('.').pop().toUpperCase() : ''
  return extension ? `${extension} 文件` : '文件'
}
export function previewKind(file) {
  const extension = file.name.split('.').pop().toLowerCase()
  if (['png', 'jpg', 'jpeg', 'gif', 'webp', 'bmp'].includes(extension)) return 'image'
  if (extension === 'pdf') return 'pdf'
  if (['txt', 'md', 'log', 'csv', 'json'].includes(extension)) return 'text'
  return 'none'
}
export const driveViews = [
  { id: 'files', title: '我的文件', icon: 'folder', subtitle: '给每一份文件，一个顺手的位置。' },
  { id: 'recent', title: '最近文件', icon: 'clock', subtitle: '最近上传或整理的文件，都在这里。' },
  { id: 'image', title: '图片', icon: 'image', subtitle: '收好照片，也收好生活中的片刻。' },
  { id: 'document', title: '文档', icon: 'file', subtitle: '项目资料、笔记和文档，井井有条。' },
  { id: 'video', title: '视频', icon: 'video', subtitle: '保存影像，随时找到。' },
  { id: 'audio', title: '音频', icon: 'music', subtitle: '音乐、录音与声音资料。' },
  {
    id: 'trash',
    title: '回收站',
    icon: 'trash',
    subtitle: '删除的内容仍然保留，确认后再彻底删除。',
  },
]
