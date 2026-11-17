export function abortError() { return new DOMException('操作已暂停', 'AbortError') }
export function hashFile(blob, signal, onProgress = () => {}) {
  return new Promise((resolve, reject) => {
    if (signal?.aborted) { reject(abortError()); return }
    const worker = new Worker(new URL('../workers/hash.worker.js', import.meta.url), { type: 'module' })
    function close() { signal?.removeEventListener('abort', abort); worker.terminate() }
    function abort() { close(); reject(abortError()) }
    signal?.addEventListener('abort', abort, { once: true })
    worker.onmessage = ({ data }) => {
      if (data.error) { close(); reject(new Error(data.error)) }
      else if (data.hash) { close(); onProgress(100); resolve(data.hash) }
      else onProgress(data.progress)
    }
    worker.onerror = () => { close(); reject(new Error('文件指纹计算失败，请重新选择文件')) }
    worker.postMessage({ blob })
  })
}
export function delay(ms, signal) {
  return new Promise((resolve, reject) => {
    if (signal?.aborted) { reject(abortError()); return }
    const timer = setTimeout(() => { signal?.removeEventListener('abort', abort); resolve() }, ms)
    function abort() { clearTimeout(timer); reject(abortError()) }
    signal?.addEventListener('abort', abort, { once: true })
  })
}
