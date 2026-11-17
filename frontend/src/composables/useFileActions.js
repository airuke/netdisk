import { ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  createFolder,
  renameFile,
  moveFile,
  trashFile,
  restoreFile,
  purgeFile,
  downloadFile,
} from '@/api/files'
export function useFileActions(workbench) {
  const busy = ref(false),
    nameOpen = ref(false),
    nameMode = ref('create'),
    nameFile = ref(null)
  const moveOpen = ref(false),
    movingFiles = ref([]),
    dialogError = ref('')
  const previewOpen = ref(false),
    previewItem = ref(null)
  const shareOpen = ref(false), shareFile = ref(null)
  function create() {
    dialogError.value = ''
    nameMode.value = 'create'
    nameFile.value = null
    nameOpen.value = true
  }
  function open(file) {
    if (workbench.view.value === 'trash') return
    if (file.type === 'FOLDER') workbench.openFolder(file.id)
    else {
      previewItem.value = file
      previewOpen.value = true
    }
  }
  async function saveName(value) {
    busy.value = true
    dialogError.value = ''
    try {
      if (nameMode.value === 'create') await createFolder(workbench.parentId.value, value)
      else await renameFile(nameFile.value.id, value)
      nameOpen.value = false
      ElMessage.success(nameMode.value === 'create' ? '文件夹已创建' : '名称已更新')
      await workbench.refresh()
    } catch (e) {
      dialogError.value = e.message
    } finally {
      busy.value = false
    }
  }
  async function saveMove(parentId) {
    busy.value = true
    dialogError.value = ''
    const snapshot = [...movingFiles.value]
    let succeeded = 0
    try {
      for (const file of snapshot) {
        await moveFile(file.id, parentId)
        succeeded++
      }
      moveOpen.value = false
      ElMessage.success('移动成功')
    } catch (e) {
      movingFiles.value = snapshot.slice(succeeded)
      dialogError.value = `${succeeded ? `已移动 ${succeeded} 项。` : ''}${e.message}`
    } finally {
      await workbench.refresh()
      busy.value = false
    }
  }
  async function action(type, file) {
    if (busy.value) return
    const files = file ? [file] : [...workbench.selectedFiles.value]
    if (!files.length) return
    if (type === 'open' || type === 'preview') {
      open(files[0])
      return
    }
    if (type === 'share') {
      if (files.length !== 1) { ElMessage.info('一次分享一个文件或文件夹；多个文件可整理到同一文件夹后分享'); return }
      shareFile.value = files[0]; shareOpen.value = true; return
    }
    if (type === 'rename') {
      nameMode.value = 'rename'
      nameFile.value = files[0]
      dialogError.value = ''
      nameOpen.value = true
      return
    }
    if (type === 'move') {
      movingFiles.value = files
      dialogError.value = ''
      moveOpen.value = true
      return
    }
    if (type === 'download') {
      const downloadable = files.filter((f) => f.type === 'FILE')
      if (!downloadable.length) {
        ElMessage.info('文件夹不能直接下载，请进入后选择文件')
        return
      }
      if (files.length !== downloadable.length) ElMessage.info('已跳过文件夹')
      busy.value = true
      try {
        for (const item of downloadable) await downloadFile(item.id)
      } catch (e) {
        ElMessage.error(e.message)
      } finally {
        busy.value = false
      }
      return
    }
    const labels = { trash: '移到回收站', purge: '彻底删除', restore: '还原' }
    if (type !== 'restore') {
      try {
        await ElMessageBox.confirm(
          type === 'purge'
            ? `彻底删除选中的 ${files.length} 项？包含文件夹内全部内容，删除后无法恢复。`
            : `将选中的 ${files.length} 项移到回收站？文件夹内的内容会一起移入。`,
          labels[type],
          {
            confirmButtonText: type === 'purge' ? '确认彻底删除' : '移到回收站',
            cancelButtonText: '取消',
            type: 'warning',
          },
        )
      } catch {
        return
      }
    }
    busy.value = true
    try {
      await workbench.batch(
        { trash: trashFile, purge: purgeFile, restore: restoreFile }[type],
        files,
        labels[type],
      )
    } finally {
      busy.value = false
    }
  }
  return {
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
    create,
    open,
    saveName,
    saveMove,
    action,
  }
}
