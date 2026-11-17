<script setup>
import { ref, watch, nextTick } from 'vue'
import BrandMark from '@/components/BrandMark.vue'
import AppIcon from '@/components/AppIcon.vue'
import { driveViews, formatSize } from '@/utils/files'
const props = defineProps({
  view: String,
  summary: Object,
  summaryError: String,
  mobileOpen: Boolean,
})
const emit = defineEmits(['navigate', 'close'])
const sidebar = ref(null)
let previousFocus
watch(
  () => props.mobileOpen,
  async (open) => {
    if (open) {
      previousFocus = document.activeElement
      await nextTick()
      sidebar.value?.querySelector('.mobile-close')?.focus()
    } else previousFocus?.focus()
  },
)
function keydown(event) {
  if (!props.mobileOpen) return
  if (event.key === 'Escape') {
    event.preventDefault()
    emit('close')
    return
  }
  if (event.key !== 'Tab') return
  const buttons = Array.from(sidebar.value.querySelectorAll('button:not(:disabled)'))
  const first = buttons[0],
    last = buttons.at(-1)
  if (event.shiftKey && document.activeElement === first) {
    event.preventDefault()
    last?.focus()
  } else if (!event.shiftKey && document.activeElement === last) {
    event.preventDefault()
    first?.focus()
  }
}
</script>
<template>
  <div v-if="mobileOpen" class="sidebar-backdrop" @click="$emit('close')" />
  <aside
    ref="sidebar"
    class="drive-sidebar"
    :class="{ 'is-open': mobileOpen }"
    :role="mobileOpen ? 'dialog' : undefined"
    :aria-modal="mobileOpen ? true : undefined"
    aria-label="云盘导航"
    @keydown="keydown"
  >
    <div class="sidebar-brand">
      <BrandMark /><button
        class="icon-button mobile-close"
        aria-label="关闭导航"
        @click="$emit('close')"
      >
        <AppIcon name="close" />
      </button>
    </div>
    <p class="nav-label">文件空间</p>
    <nav>
      <button
        v-for="item in driveViews.slice(0, 2)"
        :key="item.id"
        class="nav-item"
        :class="{ active: view === item.id }"
        :aria-current="view === item.id ? 'page' : undefined"
        @click="$emit('navigate', item.id)"
      >
        <AppIcon :name="item.icon" /><span>{{ item.title }}</span>
      </button>
      <p class="nav-label">按类型整理</p>
      <button
        v-for="item in driveViews.slice(2, 6)"
        :key="item.id"
        class="nav-item"
        :class="{ active: view === item.id }"
        @click="$emit('navigate', item.id)"
      >
        <AppIcon :name="item.icon" /><span>{{ item.title }}</span>
      </button>
      <div class="nav-divider" />
      <button class="nav-item" :class="{ active: view === 'shares' }" :aria-current="view === 'shares' ? 'page' : undefined" @click="$emit('navigate', 'shares')">
        <AppIcon name="share" /><span>我的分享</span>
      </button>
      <button
        class="nav-item"
        :class="{ active: view === 'trash' }"
        :aria-current="view === 'trash' ? 'page' : undefined"
        @click="$emit('navigate', 'trash')"
      >
        <AppIcon name="trash" /><span>回收站</span
        ><span v-if="summary.trashCount" class="nav-count">{{ summary.trashCount }}</span>
      </button>
    </nav>
    <div class="storage-note">
      <div class="storage-heading">
        <AppIcon name="cloud" :size="18" /><span>已保存的内容</span>
      </div>
      <strong>{{ summaryError ? '暂时无法统计' : formatSize(summary.usedBytes) }}</strong>
      <p>
        {{
          summaryError
            ? '刷新页面后重试'
            : `${summary.fileCount} 个文件 · ${summary.folderCount} 个文件夹`
        }}
      </p>
      <small>包含回收站中的文件</small>
    </div>
    <div class="sidebar-footer">AIRUIKE DRIVE <span>个人文件空间</span></div>
  </aside>
</template>
