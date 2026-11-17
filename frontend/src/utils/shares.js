export const shareStatus = { ACTIVE: '分享中', EXPIRED: '已过期', REVOKED: '已取消', UNAVAILABLE: '内容不可用' }
export function shareUrl(path) {
  const base = import.meta.env.BASE_URL.replace(/\/$/, '')
  const origin = import.meta.env.VITE_PUBLIC_ORIGIN || window.location.origin
  if (!/^\/s\/[A-Za-z0-9_-]{24}$/.test(path)) throw new Error('分享地址无效')
  return new URL(base + path, origin).href
}
export function extractionCode() {
  const bytes = new Uint8Array(4); crypto.getRandomValues(bytes)
  const alphabet = 'ABCDEFGHJKLMNPQRSTUVWXYZ23456789'
  return [...bytes].map(b => alphabet[b % alphabet.length]).join('')
}
export async function copyText(value) {
  if (!navigator.clipboard?.writeText) throw new Error('当前浏览器不支持自动复制，请选中文本手动复制')
  await navigator.clipboard.writeText(value)
}
