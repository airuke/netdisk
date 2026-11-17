<script setup>
import { ref, watch, onBeforeUnmount } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import DriveSidebar from '@/components/drive/DriveSidebar.vue'
import DriveTopbar from '@/components/drive/DriveTopbar.vue'
import UploadTaskPanel from '@/components/drive/UploadTaskPanel.vue'
import ShareList from '@/components/shares/ShareList.vue'
import AppIcon from '@/components/AppIcon.vue'
import { listShares, revokeShare } from '@/api/shares'
import { fileSummary } from '@/api/files'
import { shareUrl, copyText } from '@/utils/shares'
import '@/assets/drive.css'
import '@/assets/share.css'
const route = useRoute(), router = useRouter(), items = ref([]), total = ref(0), loading = ref(false), error = ref('')
const mobileOpen = ref(false), page = ref(1), busyId = ref(''), copyFallback = ref('')
const summary = ref({ usedBytes: 0, fileCount: 0, folderCount: 0, trashCount: 0 }), summaryError = ref('正在读取统计')
let controller, serial = 0
async function load() {
  controller?.abort(); controller = new AbortController(); const current = ++serial
  loading.value = true; error.value = ''
  try {
    const data = await listShares({ page: page.value, pageSize: 20 }, controller.signal)
    if (current === serial) { items.value = data.items; total.value = data.total }
  } catch (e) { if (current === serial && e.code !== 'ERR_CANCELED') error.value = e.message }
  finally { if (current === serial) loading.value = false }
}
watch(() => route.query.page, value => {
  page.value = Math.min(100000, Math.max(1, Number.parseInt(value, 10) || 1)); load()
}, { immediate: true })
fileSummary().then(data => { summary.value = data; summaryError.value = '' }).catch(e => { summaryError.value = e.message })
function navigate(view) {
  mobileOpen.value = false
  if (view === 'shares') return
  router.push({ name: 'drive', query: view === 'files' ? {} : { view } })
}
async function copy(share) {
  const value = shareUrl(share.path)
  try { await copyText(value); ElMessage.success(share.hasExtractionCode ? '链接已复制，请同时发送你保存的提取码' : '链接已复制') }
  catch { copyFallback.value = value }
}
async function revoke(share) {
  try { await ElMessageBox.confirm('取消后，现有链接和访问凭证都将失效。已被对方下载的文件副本无法收回。', '取消分享',
    { confirmButtonText: '取消分享', cancelButtonText: '保留分享', type: 'warning' }) }
  catch { return }
  busyId.value = share.id
  try { await revokeShare(share.id); ElMessage.success('分享已取消'); await load() }
  catch (e) { ElMessage.error(e.message) }
  finally { busyId.value = '' }
}
onBeforeUnmount(() => { serial++; controller?.abort() })
</script>
<template>
  <div class="drive-app"><DriveSidebar view="shares" :summary="summary" :summary-error="summaryError" :mobile-open="mobileOpen" @navigate="navigate" @close="mobileOpen = false" />
    <div class="drive-main"><DriveTopbar @menu="mobileOpen = true" /><main class="workbench">
      <div class="page-heading"><div><p class="page-kicker">SHARED BY ME</p><h1>我的分享<span v-if="!loading && !error">{{ total }} 项</span></h1><p>把需要的资料送出去，让访问范围留在掌握中。</p></div>
        <el-button type="primary" @click="navigate('files')"><AppIcon name="plus" :size="16" />去文件中创建分享</el-button></div>
      <section class="shares-surface" aria-label="我的分享列表"><header class="shares-toolbar"><span><AppIcon name="share" :size="18" />分享记录</span><button class="icon-button" aria-label="刷新分享列表" @click="load"><AppIcon name="refresh" /></button></header>
        <div class="share-management-note"><AppIcon name="info" :size="16" /><span>接收方免登录访问。提取码在创建时显示，取消分享后访问立即失效。</span></div>
        <div v-if="loading" class="share-skeleton" role="status" aria-label="正在读取分享记录"><div v-for="n in 4" :key="n" /></div>
        <div v-else-if="error" class="share-empty" role="alert"><AppIcon name="alert" :size="40" /><h2>暂时无法读取分享</h2><p>{{ error }}</p><el-button @click="load">重新加载</el-button></div>
        <div v-else-if="!items.length" class="share-empty"><AppIcon name="share" :size="44" /><h2>把一份资料分享出去</h2><p>回到“我的文件”，选择一个文件或文件夹，点击分享即可生成链接。</p><el-button type="primary" @click="navigate('files')">前往我的文件</el-button></div>
        <ShareList v-else :items="items" :busy-id="busyId" @copy="copy" @revoke="revoke" />
        <footer class="file-list-footer" v-if="!loading && !error"><span>共 {{ total }} 条分享记录</span><el-pagination v-if="total > 20" small background layout="prev, pager, next" :pager-count="5" :current-page="page" :page-size="20" :total="total" @current-change="p => router.push({ query: { page: p } })" /></footer>
      </section>
    </main></div><UploadTaskPanel />
    <el-dialog :model-value="!!copyFallback" title="复制分享链接" width="520px" class="drive-dialog" @update:model-value="value => { if (!value) copyFallback = '' }"><p>选中下方链接后手动复制。有提取码的分享，请同时发送你保存的提取码。</p><input :value="copyFallback" readonly aria-label="待复制的分享链接" class="share-copy-text" @focus="$event.target.select()" /></el-dialog>
  </div>
</template>
