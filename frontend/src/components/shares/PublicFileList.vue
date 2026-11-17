<script setup>
import AppIcon from '@/components/AppIcon.vue'
import { fileIcon, formatSize, typeLabel } from '@/utils/files'
defineProps({ items: Array, downloadingId: String })
defineEmits(['open', 'download'])
</script>
<template>
  <ul class="public-file-list"><li v-for="file in items" :key="file.id">
    <span class="file-glyph" :class="file.category.toLowerCase()"><AppIcon :name="fileIcon(file)" :size="25" /></span>
    <button class="public-file-name" @click="$emit('open', file)"><strong>{{ file.name }}</strong><span>{{ typeLabel(file) }}{{ file.type === 'FILE' ? ` · ${formatSize(file.size)}` : '' }}</span></button>
    <button v-if="file.type === 'FILE'" class="public-download" :disabled="downloadingId === file.id" :aria-label="`下载${file.name}`" @click="$emit('download', file.id)"><AppIcon name="download" :size="17" /><span>{{ downloadingId === file.id ? '准备下载…' : '下载' }}</span></button>
    <button v-else class="icon-button" :aria-label="`进入${file.name}`" @click="$emit('open', file)"><AppIcon name="chevron" /></button>
  </li></ul>
</template>
