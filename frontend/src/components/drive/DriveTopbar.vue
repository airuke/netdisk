<script setup>
import { onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import AppIcon from '@/components/AppIcon.vue'
import { useAuthStore } from '@/stores/auth'
import { useUploadsStore } from '@/stores/uploads'
defineEmits(['menu'])
const auth = useAuthStore(), uploads = useUploadsStore(), router = useRouter()
onMounted(async () => {
  try {
    if (!auth.user) await auth.loadCurrentUser()
    if (auth.user) await uploads.initialize(auth.user.id)
  } catch (e) { ElMessage.error(e.message) }
})
async function logout() {
  if (uploads.activeCount) {
    try {
      await ElMessageBox.confirm('退出会停止当前上传请求，已保存的分片可在重新登录后恢复。服务端已开始的合并可能继续完成。',
        '退出登录', { confirmButtonText: '退出', cancelButtonText: '继续上传' })
    } catch { return }
  }
  uploads.reset(); auth.clearSession(); await router.replace({ name: 'login' })
}
</script>
<template>
  <header class="drive-topbar">
    <div class="topbar-context">
      <button class="icon-button menu-button" aria-label="打开导航" @click="$emit('menu')"><AppIcon name="menu" /></button>
      <span>个人文件空间</span><small>让重要的内容，随时在手边</small>
    </div>
    <div class="topbar-account">
      <button class="transfer-button" aria-label="上传任务" @click="uploads.open = !uploads.open">
        <AppIcon name="upload" :size="18" /><span>上传任务</span><b v-if="uploads.activeCount">{{ uploads.activeCount }}</b>
      </button>
      <span class="user-avatar">{{ (auth.user?.nickname || auth.user?.username || '我').slice(0, 1) }}</span>
      <span class="account-name">{{ auth.user?.nickname || auth.user?.username || '我的账号' }}</span>
      <button class="icon-button" aria-label="退出登录" title="退出登录" @click="logout"><AppIcon name="logout" :size="18" /></button>
    </div>
  </header>
</template>
