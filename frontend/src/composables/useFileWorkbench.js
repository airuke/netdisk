import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { listFiles, fileSummary } from '@/api/files'
import { driveViews } from '@/utils/files'
import { useUploadsStore } from '@/stores/uploads'
export function useFileWorkbench() {
  const route = useRoute(),
    router = useRouter(),
    uploads = useUploadsStore()
  const view = computed(() =>
    driveViews.some((v) => v.id === route.query.view) ? route.query.view : 'files',
  )
  const parentId = computed(() =>
    /^\d+$/.test(route.query.folder || '') ? route.query.folder : '0',
  )
  const keyword = computed(() => String(route.query.q || ''))
  const page = computed(() => Math.max(1, parseInt(route.query.page, 10) || 1))
  const sort = computed(() =>
    ['updatedAt', 'name', 'size'].includes(route.query.sort) ? route.query.sort : 'updatedAt',
  )
  const direction = computed(() => (route.query.order === 'asc' ? 'asc' : 'desc'))
  const currentView = computed(() => driveViews.find((v) => v.id === view.value))
  const items = ref([]),
    total = ref(0),
    breadcrumbs = ref([]),
    selected = ref([])
  const summary = ref({ fileCount: 0, folderCount: 0, usedBytes: 0, trashCount: 0 })
  const loading = ref(true),
    error = ref(''),
    summaryError = ref('')
  const selectedFiles = computed(() => items.value.filter((n) => selected.value.includes(n.id)))
  const folderName = computed(() => breadcrumbs.value.at(-1)?.name || '全部文件')
  const canCreate = computed(() => view.value === 'files' && !keyword.value && !error.value)
  let serial = 0,
    controller
  function navigate(query) {
    selected.value = []
    return router.push({ name: 'drive', query })
  }
  function openFolder(id) {
    return navigate({ folder: id })
  }
  function changeView(id) {
    return navigate(id === 'files' ? {} : { view: id })
  }
  function changeQuery(patch) {
    return navigate({ ...route.query, ...patch, page: undefined })
  }
  async function load() {
    controller?.abort()
    controller = new AbortController()
    const current = ++serial
    loading.value = true
    error.value = ''
    selected.value = []
    try {
      const data = await listFiles(
        {
          parentId: parentId.value,
          view: view.value,
          keyword: keyword.value,
          sort: sort.value,
          direction: direction.value,
          page: page.value,
          pageSize: 30,
        },
        controller.signal,
      )
      if (current !== serial) return
      if (data.items.length === 0 && page.value > 1 && data.total > 0) {
        await router.replace({
          name: 'drive',
          query: { ...route.query, page: Math.ceil(data.total / 30) },
        })
        return
      }
      items.value = data.items
      total.value = data.total
      breadcrumbs.value = data.breadcrumbs
    } catch (e) {
      if (current === serial && e.code !== 'ERR_CANCELED') {
        items.value = []
        error.value = e.message
      }
    } finally {
      if (current === serial) loading.value = false
    }
  }
  async function refreshSummary() {
    try {
      summary.value = await fileSummary()
      summaryError.value = ''
    } catch (e) {
      summaryError.value = e.message
    }
  }
  async function refresh() {
    await Promise.all([load(), refreshSummary()])
  }
  function toggle(id) {
    selected.value = selected.value.includes(id)
      ? selected.value.filter((v) => v !== id)
      : [...selected.value, id]
  }
  async function batch(operation, files, label) {
    let succeeded = 0
    const failed = []
    for (const file of files) {
      try {
        await operation(file.id)
        succeeded++
      } catch (e) {
        failed.push(`${file.name}：${e.message}`)
      }
    }
    await refresh()
    if (failed.length)
      ElMessage({
        type: 'warning',
        message: `${label}完成 ${succeeded}/${files.length} 项。${failed[0]}`,
        duration: 6500,
      })
    else ElMessage.success(`${label}成功`)
  }
  watch(() => route.query, load, { immediate: true })
  watch(() => uploads.revision, refresh)
  refreshSummary()
  onBeforeUnmount(() => {
    serial++
    controller?.abort()
  })
  return {
    view,
    parentId,
    keyword,
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
    navigate,
    openFolder,
    changeView,
    changeQuery,
    refresh,
    toggle,
    batch,
  }
}
