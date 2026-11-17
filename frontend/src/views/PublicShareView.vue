<script setup>
import { ref, watch, nextTick, onBeforeUnmount } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import BrandMark from '@/components/BrandMark.vue'
import AppIcon from '@/components/AppIcon.vue'
import PublicFileList from '@/components/shares/PublicFileList.vue'
import FilePreviewDialog from '@/components/drive/FilePreviewDialog.vue'
import { shareInfo, unlockShare, sharedFiles, sharedPreview, sharedDownload } from '@/api/shares'
import { formatSize, formatDate } from '@/utils/files'
import '@/assets/drive.css'
import '@/assets/share.css'
const route = useRoute(), info = ref(null), root = ref(null), token = ref(''), codeInput = ref(''), input = ref(null)
const loading = ref(false), unlocking = ref(false), contentLoading = ref(false), error = ref(''), unlockError = ref(''), contentError = ref('')
const items = ref([]), crumbs = ref([]), folderId = ref('0'), page = ref(1), total = ref(0), downloadingId = ref('')
const previewOpen = ref(false), previewFile = ref(null)
let infoController, contentController, serial = 0, contentSerial = 0
function code() { return String(route.params.code || '') }
async function loadInfo() {
  infoController?.abort(); contentController?.abort(); infoController = new AbortController()
  const current = ++serial; contentSerial++
  token.value = ''; info.value = null; root.value = null; previewOpen.value = false
  loading.value = true; unlocking.value = false; error.value = ''; unlockError.value = ''; codeInput.value = ''
  try {
    const data = await shareInfo(code(), infoController.signal)
    if (current !== serial) return
    info.value = data
    if (!data.requiresCode) await unlock('')

  } catch (e) { if (current === serial && e.code !== 'ERR_CANCELED') error.value = e.message }
  finally { if (current === serial) { loading.value = false; await nextTick(); input.value?.focus() } }
}
async function unlock(value = codeInput.value) {
  if (unlocking.value) return
  unlocking.value = true; unlockError.value = ''; const current = serial
  try {
    const grant = await unlockShare(code(), value.toUpperCase(), infoController.signal)
    if (current !== serial) return
    token.value = grant.accessToken; root.value = grant.root; folderId.value = '0'; page.value = 1
    await loadFiles()
  } catch (e) { if (current === serial && e.code !== 'ERR_CANCELED') unlockError.value = e.message }
  finally { if (current === serial) unlocking.value = false }
}
function expired(e) {
  if ([403, 410].includes(e.status)) { token.value = ''; previewOpen.value = false; unlockError.value = e.message }
}
async function loadFiles() {
  contentController?.abort(); contentController = new AbortController(); const current = ++contentSerial
  contentLoading.value = true; contentError.value = ''
  try {
    const data = await sharedFiles(code(), token.value, { folderId: folderId.value, page: page.value, pageSize: 30 }, contentController.signal)
    if (current !== contentSerial) return
    items.value = data.items; total.value = data.total; crumbs.value = data.breadcrumbs
  } catch (e) { if (current === contentSerial && e.code !== 'ERR_CANCELED') { contentError.value = e.message; expired(e) } }
  finally { if (current === contentSerial) contentLoading.value = false }
}
function openFile(file) {
  if (file.type === 'FOLDER') { folderId.value = file.id; page.value = 1; loadFiles() }
  else { previewFile.value = file; previewOpen.value = true }
}
async function download(id) {
  downloadingId.value = id
  const current = serial
  try { await sharedDownload(code(), token.value, id) }
  catch (e) { if (current === serial) { expired(e); ElMessage.error(e.message) } }
  finally { downloadingId.value = '' }
}
async function preview(id, signal) {
  const current = serial
  try { return await sharedPreview(code(), token.value, id, signal) }
  catch (e) { if (current === serial) expired(e); throw e }
}
watch(() => route.params.code, loadInfo, { immediate: true })
onBeforeUnmount(() => { serial++; contentSerial++; infoController?.abort(); contentController?.abort() })
</script>
<template>
  <div class="drive-app share-public"><header class="share-public-header"><BrandMark /><router-link :to="{ name: 'drive' }">我的网盘<AppIcon name="arrow" :size="15" /></router-link></header>
    <main class="share-public-main">
      <div v-if="loading" class="public-loading" role="status" aria-label="正在打开分享"><div class="share-skeleton"><div /><div /><div /></div><p>正在打开分享…</p></div>
      <section v-else-if="error" class="public-access-card" role="alert"><span class="public-access-symbol"><AppIcon name="alert" :size="32" /></span><p class="page-kicker">SHARED FILES</p><h1>这个分享暂时无法打开</h1><p>{{ error }}</p><el-button @click="loadInfo">重新加载</el-button></section>
      <section v-else-if="info && !token" class="public-access-card">
        <span class="public-access-symbol"><AppIcon :name="info.requiresCode ? 'lock' : 'share'" :size="32" /></span><p class="page-kicker">SHARED WITH YOU</p>
        <h1>{{ info.requiresCode ? '一份资料，等待你打开' : info.name }}</h1><p>{{ info.owner }} 分享的内容{{ info.expiresAt ? ` · 有效至 ${formatDate(info.expiresAt)}` : '' }}</p>
        <form v-if="info.requiresCode" class="public-code-form" @submit.prevent="unlock()"><label for="public-code">输入 4 位提取码</label><input id="public-code" ref="input" v-model="codeInput" maxlength="4" autocomplete="off" :disabled="unlocking" placeholder="例如 A8K2" required minlength="4" />
          <p v-if="unlockError" class="inline-error" role="alert">{{ unlockError }}</p><el-button type="primary" native-type="submit" :loading="unlocking">查看分享内容<AppIcon name="arrow" :size="16" /></el-button></form>
        <template v-else><p v-if="unlockError" class="inline-error" role="alert">{{ unlockError }}</p><el-button type="primary" :loading="unlocking" @click="loadInfo">重新打开分享</el-button></template>
        <p class="public-access-note"><AppIcon name="user" :size="14" />无需注册或登录网盘账号</p>
      </section>
      <section v-else-if="root && token" class="public-content">
        <div class="public-content-heading"><span class="public-access-symbol"><AppIcon :name="root.type === 'FOLDER' ? 'folder' : 'file'" :size="28" /></span><div><p class="page-kicker">SHARED WITH YOU</p><h1>{{ root.name }}</h1><p>{{ info.owner }} 分享 · {{ root.type === 'FOLDER' ? '文件夹' : formatSize(root.size) }}{{ info.expiresAt ? ` · 有效至 ${formatDate(info.expiresAt)}` : '' }}</p></div></div>
        <div class="public-content-surface"><header class="public-content-toolbar"><nav aria-label="分享文件路径"><template v-for="(crumb, i) in crumbs" :key="crumb.id"><AppIcon v-if="i" name="chevron" :size="13" /><button @click="folderId = crumb.id; page = 1; loadFiles()">{{ crumb.name }}</button></template></nav><button class="icon-button" aria-label="刷新分享内容" @click="loadFiles"><AppIcon name="refresh" /></button></header>
          <div v-if="contentLoading" class="share-skeleton" role="status" aria-label="正在加载分享文件"><div v-for="n in 4" :key="n" /></div>
          <div v-else-if="contentError" class="share-empty" role="alert"><AppIcon name="alert" :size="36" /><h2>暂时无法读取文件</h2><p>{{ contentError }}</p><el-button @click="loadFiles">重新加载</el-button></div>
          <div v-else-if="!items.length" class="share-empty"><AppIcon name="folder" :size="40" /><h2>这个文件夹里还没有内容</h2><p>你可以返回上一级，查看其他分享文件。</p></div>
          <PublicFileList v-else :items="items" :downloading-id="downloadingId" @open="openFile" @download="download" />
          <footer class="file-list-footer" v-if="!contentLoading && !contentError"><span>共 {{ total }} 项 · 免登录访问</span><el-pagination v-if="total > 30" small layout="prev, pager, next" :pager-count="5" :page-size="30" :total="total" :current-page="page" @current-change="p => { page = p; loadFiles() }" /></footer>
        </div><p class="public-content-note"><AppIcon name="info" :size="15" />点击文件名称可预览；文件夹请进入后选择文件下载。</p>
      </section>
    </main><footer class="share-public-footer">AIRUIKE DRIVE · 让资料的传递更简单</footer>
    <FilePreviewDialog v-model="previewOpen" :file="previewFile" :load-preview="preview" :download-action="download" />
  </div>
</template>
