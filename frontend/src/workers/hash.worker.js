import { createSHA256 } from 'hash-wasm'

self.onmessage = async ({ data: { blob } }) => {
  try {
    const hash = await createSHA256()
    hash.init()
    const block = 4 * 1024 * 1024
    for (let offset = 0; offset < blob.size; offset += block) {
      const buffer = await blob.slice(offset, offset + block).arrayBuffer()
      hash.update(new Uint8Array(buffer))
      self.postMessage({ progress: Math.min(100, Math.round((offset + buffer.byteLength) / blob.size * 100)) })
    }
    self.postMessage({ hash: hash.digest('hex'), progress: 100 })
  } catch (error) { self.postMessage({ error: error.message || '无法计算文件指纹' }) }
}
