<script setup>
import { computed, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import AppIcon from '@/components/AppIcon.vue'
import DriveTopbar from '@/components/drive/DriveTopbar.vue'
import CreateShareDialog from '@/components/shares/CreateShareDialog.vue'
import DriveSidebar from '@/components/drive/DriveSidebar.vue'
import DriveToolbar from '@/components/drive/DriveToolbar.vue'
import FileCollection from '@/components/drive/FileCollection.vue'
import FileEmptyState from '@/components/drive/FileEmptyState.vue'
import FileInspector from '@/components/drive/FileInspector.vue'
import FileNameDialog from '@/components/drive/FileNameDialog.vue'
import MoveFileDialog from '@/components/drive/MoveFileDialog.vue'
import FilePreviewDialog from '@/components/drive/FilePreviewDialog.vue'
import UploadTaskPanel from '@/components/drive/UploadTaskPanel.vue'
import { useFileWorkbench } from '@/composables/useFileWorkbench'
import { useFileActions } from '@/composables/useFileActions'
import { useAuthStore } from '@/stores/auth'
import { useUploadsStore } from '@/stores/uploads'
import '@/assets/drive.css'
import '@/assets/share.css'
const router = useRouter(),
  auth = useAuthStore(),
  uploads = useUploadsStore()
const work = useFileWorkbench(),
  actions = useFileActions(work)
const {
  view,
  keyword,
  parentId,
  page,
  sort,
  direction,
  currentView,
  items,
  total,
  breadcrumbs,
  selected,
  selectedFiles,
  summary,
  summaryError,
  loading,
  error,
  folderName,
  canCreate,
} = work
const {
  busy,
  nameOpen,
  nameMode,
  nameFile,
  moveOpen,
  movingFiles,
  dialogError,
  previewOpen,
  previewItem,
  shareOpen,
  shareFile,
} = actions
const mobileOpen = ref(false),
  fileInput = ref(null),
  dragging = ref(false)
let uploadDestination = null
const layout = ref(localStorage.getItem('netdisk_file_layout') === 'grid' ? 'grid' : 'list')
const title = computed(() =>
  keyword.value
    ? '搜索结果'
    : view.value === 'files' && parentId.value !== '0'
      ? folderName.value
      : currentView.value.title,
)
function setLayout(value) {
  layout.value = value
  localStorage.setItem('netdisk_file_layout', value)
}
function navigate(viewId) {
  mobileOpen.value = false
  if (viewId === 'shares') router.push({ name: 'shares' })
  else work.changeView(viewId)
}
function pickFiles() {
  if (!canCreate.value) return
  if (!auth.user || !uploads.ready) { ElMessage.info('正在读取账号和上传设置，请稍后再试'); return }
  uploadDestination = { parentId: parentId.value, name: folderName.value }
  fileInput.value?.click()
}
function queueFiles(files) {
  if (!canCreate.value || !files.length) return
  try { uploads.add(Array.from(files), parentId.value, folderName.value) } catch (e) { ElMessage.error(e.message) }
}
function onFiles(event) {
  if (uploadDestination && event.target.files.length) {
    try { uploads.add(Array.from(event.target.files), uploadDestination.parentId, uploadDestination.name) } catch (e) { ElMessage.error(e.message) }
  }
  uploadDestination = null
  event.target.value = ''
}
function drop(event) {
  dragging.value = false
  queueFiles(event.dataTransfer.files)
}
</script>
<template>
  <div class="drive-app">
    <DriveSidebar
      :view="view"
      :summary="summary"
      :summary-error="summaryError"
      :mobile-open="mobileOpen"
      @navigate="navigate"
      @close="mobileOpen = false"
    />
    <div class="drive-main">
      <DriveTopbar @menu="mobileOpen = true" />
      <main class="workbench">
        <div class="page-heading">
          <div>
            <p class="page-kicker">{{ view === 'trash' ? 'RECYCLE BIN' : 'MY WORKSPACE' }}</p>
            <h1>
              {{ title }}<span v-if="!loading && !error">{{ total }} 项</span>
            </h1>
            <p>{{ keyword ? `“${keyword}” 的匹配结果` : currentView.subtitle }}</p>
          </div>
          <div v-if="view === 'files' && !keyword" class="heading-decoration" aria-hidden="true">
            <AppIcon name="folder" :size="38" /><span>有序存放<br />随时找到</span>
          </div>
        </div>
        <nav class="file-breadcrumbs" aria-label="文件路径">
          <template v-for="(crumb, i) in breadcrumbs" :key="crumb.id"
            ><AppIcon v-if="i" name="chevron" :size="13" /><button
              :aria-current="
                i === breadcrumbs.length - 1 && view === 'files' && !keyword
                  ? 'location'
                  : undefined
              "
              @click="work.openFolder(crumb.id)"
            >
              {{ crumb.name }}
            </button></template
          ><template v-if="view !== 'files' || keyword"
            ><AppIcon name="chevron" :size="13" /><span>{{
              keyword ? '搜索结果' : currentView.title
            }}</span></template
          >
        </nav>
        <section class="workbench-surface" aria-label="文件工作台">
          <DriveToolbar
            :keyword="keyword"
            :sort="sort"
            :direction="direction"
            :layout="layout"
            :selected="selected.length"
            :trash="view === 'trash'"
            :can-create="canCreate"
            :busy="busy"
            @search="(q) => work.changeQuery({ q: q || undefined })"
            @sort="
              (value) => work.changeQuery({ sort: value.split(':')[0], order: value.split(':')[1] })
            "
            @layout="setLayout"
            @upload="pickFiles"
            @create="actions.create"
            @action="(type) => actions.action(type)"
            @refresh="work.refresh"
          />
          <div class="workspace-columns">
            <section
              class="file-content"
              :class="{ 'is-dragging': dragging }"
              aria-label="文件内容"
              @dragover.prevent="dragging = canCreate"
              @dragleave.self="dragging = false"
              @drop.prevent="drop"
            >
              <div v-if="dragging" class="drop-overlay">
                <AppIcon name="upload" :size="36" />
                <p>松开文件，上传到“{{ folderName }}”</p>
              </div>
              <div v-if="loading" class="file-skeleton" role="status" aria-label="正在加载文件">
                <div v-for="n in 7" :key="n"><i /><span /><b /></div>
              </div>
              <FileEmptyState
                v-else-if="error || !items.length"
                :error="error"
                :trash="view === 'trash'"
                :search="!!keyword"
                :can-create="canCreate"
                @retry="work.refresh"
                @home="work.changeView('files')"
                @upload="pickFiles"
                @create="actions.create"
              />
              <FileCollection
                v-else
                :items="items"
                :selected="selected"
                :layout="layout"
                :trash="view === 'trash'"
                :busy="busy"
                @toggle="work.toggle"
                @select-all="(checked) => (selected = checked ? items.map((f) => f.id) : [])"
                @open="actions.open"
                @action="(type, file) => actions.action(type, file)"
              />
              <div class="file-list-footer" v-if="!loading && !error">
                <span>{{
                  total
                    ? `共 ${total} 项 · 本页 ${items.length} 项`
                    : '整理好，每一次寻找都更轻松。'
                }}</span
                ><el-pagination
                  v-if="total > 30"
                  small
                  background
                  layout="prev, pager, next"
                  :pager-count="5"
                  :current-page="page"
                  :page-size="30"
                  :total="total"
                  @current-change="
                    (p) => work.navigate({ ...router.currentRoute.value.query, page: p })
                  "
                />
              </div>
            </section>
            <FileInspector
              :files="selectedFiles"
              :trash="view === 'trash'"
              @preview="actions.open"
              @download="(file) => actions.action('download', file)"
            />
          </div>
        </section>
        <p class="workspace-footnote">
          <AppIcon name="lock" :size="13" />你的文件由账号管理，分享内容按链接权限访问。
        </p>
      </main>
    </div>
    <input
      ref="fileInput"
      type="file"
      multiple
      class="sr-only"
      tabindex="-1"
      aria-label="选择上传文件"
      @change="onFiles"
    />
    <FileNameDialog
      v-model="nameOpen"
      :mode="nameMode"
      :file="nameFile"
      :busy="busy"
      :error="dialogError"
      @submit="actions.saveName"
    />
    <MoveFileDialog
      v-model="moveOpen"
      :files="movingFiles"
      :busy="busy"
      :error="dialogError"
      @submit="actions.saveMove"
    />
    <FilePreviewDialog v-model="previewOpen" :file="previewItem" />
    <CreateShareDialog v-model="shareOpen" :file="shareFile" />
    <UploadTaskPanel />
  </div>
</template>
