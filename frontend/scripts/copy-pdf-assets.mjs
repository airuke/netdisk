import { cpSync, mkdirSync } from 'node:fs'
import { createRequire } from 'node:module'
import { dirname, join } from 'node:path'
import { fileURLToPath } from 'node:url'
const require = createRequire(import.meta.url)
const source = dirname(require.resolve('pdfjs-dist/package.json'))
const target = fileURLToPath(new URL('../public/pdfjs/', import.meta.url))
mkdirSync(target, { recursive: true })
for (const folder of ['cmaps', 'standard_fonts', 'wasm']) {
  cpSync(join(source, folder), join(target, folder), { recursive: true })
}
cpSync(join(source, 'LICENSE'), join(target, 'LICENSE'))
