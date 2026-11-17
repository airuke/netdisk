<script setup>
import { nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import AppIcon from '@/components/AppIcon.vue'
import workerUrl from 'pdfjs-dist/build/pdf.worker.min.mjs?url'
const props = defineProps({ blob: Blob, fileName: String })
const host = ref(null),
  canvas = ref(null),
  pageNumber = ref(1),
  total = ref(0)
const loading = ref(true),
  error = ref(''),
  pageText = ref('')
let documentTask,
  pdf,
  renderTask,
  observer,
  loadEpoch = 0,
  renderEpoch = 0,
  resizeTimer
async function renderPage() {
  if (!pdf || !canvas.value) return
  const epoch = ++renderEpoch
  const previous = renderTask
  previous?.cancel()
  if (previous) await previous.promise.catch(() => {})
  loading.value = true
  try {
    const page = await pdf.getPage(pageNumber.value)
    if (epoch !== renderEpoch) return
    const original = page.getViewport({ scale: 1 })
    const width = Math.min(900, Math.max(180, host.value.clientWidth - 32))
    const viewport = page.getViewport({ scale: width / original.width })
    const ratio = Math.min(2, window.devicePixelRatio || 1)
    canvas.value.width = Math.ceil(viewport.width * ratio)
    canvas.value.height = Math.ceil(viewport.height * ratio)
    canvas.value.style.width = `${Math.ceil(viewport.width)}px`
    canvas.value.style.height = `${Math.ceil(viewport.height)}px`
    renderTask = page.render({
      canvas: canvas.value,
      canvasContext: canvas.value.getContext('2d'),
      viewport,
      transform: ratio === 1 ? undefined : [ratio, 0, 0, ratio, 0, 0],
    })
    await renderTask.promise
    const text = await page.getTextContent()
    if (epoch === renderEpoch) pageText.value = text.items.map((item) => item.str || '').join(' ')
  } catch (e) {
    if (epoch === renderEpoch && e.name !== 'RenderingCancelledException')
      error.value = '此 PDF 页面无法显示，请下载后查看。'
  } finally {
    if (epoch === renderEpoch) loading.value = false
  }
}
async function load() {
  const epoch = ++loadEpoch
  renderEpoch++
  renderTask?.cancel()
  documentTask?.destroy().catch(() => {})
  pdf = null
  loading.value = true
  error.value = ''
  total.value = 0
  pageNumber.value = 1
  try {
    const lib = await import('pdfjs-dist')
    const bytes = new Uint8Array(await props.blob.arrayBuffer())
    if (epoch !== loadEpoch) return
    lib.GlobalWorkerOptions.workerSrc = workerUrl
    const base = `${import.meta.env.BASE_URL}pdfjs/`
    documentTask = lib.getDocument({
      data: bytes,
      cMapUrl: `${base}cmaps/`,
      cMapPacked: true,
      standardFontDataUrl: `${base}standard_fonts/`,
      wasmUrl: `${base}wasm/`,
      isEvalSupported: false,
    })
    documentTask.onPassword = () => {
      error.value = '加密 PDF 请下载后使用本机应用打开。'
      documentTask.destroy().catch(() => {})
    }
    const document = await documentTask.promise
    if (epoch !== loadEpoch) {
      await document.destroy()
      return
    }
    pdf = document
    total.value = pdf.numPages
    await nextTick()
    await renderPage()
  } catch {
    if (epoch === loadEpoch) error.value ||= '无法读取此 PDF，请下载后查看。'
  } finally {
    if (epoch === loadEpoch) loading.value = false
  }
}
watch(pageNumber, renderPage)
watch(() => props.blob, load)
onMounted(() => {
  load()
  observer = new ResizeObserver(() => {
    clearTimeout(resizeTimer)
    resizeTimer = setTimeout(() => {
      if (pdf) renderPage()
    }, 150)
  })
  observer.observe(host.value)
})
onBeforeUnmount(() => {
  loadEpoch++
  renderEpoch++
  clearTimeout(resizeTimer)
  observer?.disconnect()
  renderTask?.cancel()
  documentTask?.destroy().catch(() => {})
})
</script>
<template>
  <div class="pdf-document" ref="host">
    <nav class="pdf-controls" aria-label="PDF 翻页">
      <button
        class="icon-button"
        aria-label="PDF 上一页"
        :disabled="loading || pageNumber <= 1"
        @click="pageNumber--"
      >
        <AppIcon name="back" :size="17" />
      </button>
      <span>第 {{ pageNumber }} 页 / 共 {{ total || '…' }} 页</span>
      <button
        class="icon-button"
        aria-label="PDF 下一页"
        :disabled="loading || pageNumber >= total"
        @click="pageNumber++"
      >
        <AppIcon name="arrow" :size="17" />
      </button>
    </nav>
    <div class="pdf-page" v-loading="loading">
      <p v-if="error" class="inline-error" role="alert">{{ error }}</p>
      <template v-else
        ><canvas ref="canvas" role="img" :aria-label="`${fileName}第${pageNumber}页`" />
        <p class="sr-only">{{ pageText }}</p></template
      >
    </div>
  </div>
</template>
