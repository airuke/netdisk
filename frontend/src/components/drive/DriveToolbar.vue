<script setup>
import { ref, watch, onBeforeUnmount } from 'vue'
import AppIcon from '@/components/AppIcon.vue'
const props = defineProps({
  keyword: String,
  sort: String,
  direction: String,
  layout: String,
  selected: Number,
  trash: Boolean,
  canCreate: Boolean,
  busy: Boolean,
})
const emit = defineEmits(['search', 'sort', 'layout', 'upload', 'create', 'action', 'refresh'])
const search = ref(props.keyword)
let timer
watch(
  () => props.keyword,
  (value) => {
    search.value = value
  },
)
function submitSearch() {
  clearTimeout(timer)
  emit('search', search.value.trim())
}
function inputSearch() {
  clearTimeout(timer)
  timer = setTimeout(submitSearch, 400)
}
onBeforeUnmount(() => clearTimeout(timer))
</script>
<template>
  <div class="drive-toolbar">
    <div class="toolbar-primary">
      <template v-if="!trash && canCreate">
        <el-button type="primary" :disabled="!canCreate || busy" @click="emit('upload')"
          ><AppIcon name="upload" :size="18" />上传文件</el-button
        >
        <el-button :disabled="!canCreate || busy" @click="emit('create')"
          ><AppIcon name="plus" :size="18" />新建文件夹</el-button
        >
      </template>
      <p v-else-if="trash" class="trash-toolbar-note"><AppIcon name="info" :size="17" />彻底删除后无法恢复</p>
      <form class="file-search" role="search" @submit.prevent="submitSearch">
        <AppIcon name="search" :size="18" /><input
          v-model="search"
          type="search"
          aria-label="搜索文件名称"
          :placeholder="trash ? '搜索回收站' : '搜索文件名称'"
          maxlength="100"
          @input="inputSearch"
        />
      </form>
    </div>
    <div class="toolbar-secondary">
      <div class="selection-actions" v-if="selected">
        <span>已选 {{ selected }} 项</span>
        <template v-if="trash"
          ><button :disabled="busy" @click="emit('action', 'restore')">还原</button
          ><button class="danger-text" :disabled="busy" @click="emit('action', 'purge')">
            彻底删除
          </button></template
        >
        <template v-else
          ><button :disabled="busy" @click="emit('action', 'download')">下载</button
          ><button :disabled="busy || selected !== 1" @click="emit('action', 'share')">分享</button>
          <button :disabled="busy" @click="emit('action', 'move')">移动</button
          ><button class="danger-text" :disabled="busy" @click="emit('action', 'trash')">
            删除
          </button></template
        >
      </div>
      <span v-else class="toolbar-hint">{{
        canCreate
          ? '双击文件夹进入，双击文件预览'
          : trash
            ? '选择内容后，可还原或彻底删除'
            : '此处汇总各目录文件；上传和新建请前往“我的文件”'
      }}</span>
      <div class="view-controls">
        <select
          aria-label="文件排序"
          :value="`${sort}:${direction}`"
          @change="emit('sort', $event.target.value)"
        >
          <option value="updatedAt:desc">{{ trash ? '删除时间 ↓' : '修改时间 ↓' }}</option>
          <option value="updatedAt:asc">时间 ↑</option>
          <option value="name:asc">名称 A–Z</option>
          <option value="name:desc">名称 Z–A</option>
          <option value="size:desc">大小 ↓</option>
        </select>
        <div class="layout-switch" aria-label="显示方式">
          <button
            class="icon-button"
            :class="{ active: layout === 'list' }"
            aria-label="列表视图"
            :aria-pressed="layout === 'list'"
            @click="emit('layout', 'list')"
          >
            <AppIcon name="list" :size="18" />
          </button>
          <button
            class="icon-button"
            :class="{ active: layout === 'grid' }"
            aria-label="网格视图"
            :aria-pressed="layout === 'grid'"
            @click="emit('layout', 'grid')"
          >
            <AppIcon name="grid" :size="18" />
          </button>
        </div>
        <button class="icon-button" aria-label="刷新文件列表" @click="emit('refresh')">
          <AppIcon name="refresh" :size="18" />
        </button>
      </div>
    </div>
  </div>
</template>
