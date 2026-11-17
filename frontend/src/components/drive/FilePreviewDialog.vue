<script setup>
import { ref, shallowRef, defineAsyncComponent, watch, onBeforeUnmount } from 'vue'
import AppIcon from '@/components/AppIcon.vue'
import { previewFile, downloadFile } from '@/api/files'
import { previewKind, formatSize, typeLabel } from '@/utils/files'
import { ElMessage } from 'element-plus'
const props = defineProps({ modelValue: Boolean, file: Object, loadPreview: Function, downloadAction: Function })
const emit = defineEmits(['update:modelValue'])
const PdfDocument = defineAsyncComponent(() => import('./PdfDocument.vue'))
const pdfBlob = shallowRef(null)
const url = ref(''),
  text = ref(''),
  loading = ref(false),
  error = ref(''),
  kind = ref('none')
let controller,
  serial = 0
function cleanup() {
  controller?.abort()
  serial++
  if (url.value) URL.revokeObjectURL(url.value)
  url.value = ''
  text.value = ''
  pdfBlob.value = null
}
async function load() {
  cleanup()
  const current = serial
  error.value = ''
  kind.value = previewKind(props.file)
  if (kind.value === 'none') {
    error.value = '此格式暂不支持在线预览，下载后可使用本机应用打开。'
    return
  }
  const limit = kind.value === 'text' ? 1024 * 1024 : 20 * 1024 * 1024
  if (props.file.size > limit) {
    error.value = '文件较大，请下载后查看。'
    return
  }
  loading.value = true
  controller = new AbortController()
  try {
    const blob = await (props.loadPreview || previewFile)(props.file.id, controller.signal)
    if (current !== serial) return
    if (kind.value === 'text') {
      const content = await blob.text()
      if (current === serial) text.value = content
    } else if (kind.value === 'pdf') pdfBlob.value = blob
    else url.value = URL.createObjectURL(blob)
  } catch (e) {
    if (current === serial && e.code !== 'ERR_CANCELED') error.value = e.message
  } finally {
    if (current === serial) loading.value = false
  }
}
async function download() {
  try {
    await (props.downloadAction || downloadFile)(props.file.id)
  } catch (e) {
    ElMessage.error(e.message)
  }
}
watch(
  () => [props.modelValue, props.file?.id],
  ([open]) => {
    if (open && props.file) load()
    else cleanup()
  },
)
onBeforeUnmount(cleanup)
</script>
<template>
  <el-dialog
    :model-value="modelValue"
    :title="file?.name || '文件预览'"
    width="1000px"
    class="drive-dialog preview-dialog"
    @update:model-value="emit('update:modelValue', $event)"
  >
    <div class="preview-meta">
      <span>{{ file ? `${typeLabel(file)} · ${formatSize(file.size)}` : '' }}</span
      ><el-button size="small" @click="download"
        ><AppIcon name="download" :size="16" />下载文件</el-button
      >
    </div>
    <div class="preview-stage" v-loading="loading">
      <div v-if="error" class="preview-error" role="alert">
        <AppIcon name="file" :size="44" />
        <p>{{ error }}</p>
        <el-button v-if="kind !== 'none'" @click="load">重新加载</el-button>
      </div>
      <img v-else-if="kind === 'image' && url" :src="url" :alt="file.name" />
      <PdfDocument v-else-if="kind === 'pdf' && pdfBlob" :blob="pdfBlob" :file-name="file.name" />
      <pre v-else-if="kind === 'text' && !loading">{{ text }}</pre>
    </div>
    <p v-if="kind === 'text' && !error" class="dialog-help">
      以纯文本显示，建议文件使用 UTF-8 编码。
    </p>
  </el-dialog>
</template>
