<script setup>
import AppIcon from '@/components/AppIcon.vue'
import { formatDate, formatSize } from '@/utils/files'
import { shareStatus, shareUrl } from '@/utils/shares'
defineProps({ items: Array, busyId: String })
defineEmits(['copy', 'revoke'])
</script>
<template>
  <ul class="share-list">
    <li v-for="share in items" :key="share.id" class="share-record">
      <span class="file-glyph" :class="share.type === 'FOLDER' ? 'folder' : 'document'"><AppIcon :name="share.type === 'FOLDER' ? 'folder' : 'file'" :size="25" /></span>
      <div class="share-record-name"><strong :title="share.name">{{ share.name }}</strong><span>{{ share.type === 'FOLDER' ? '文件夹' : formatSize(share.size) }} · 创建于 {{ formatDate(share.createdAt) }}</span>
        <a :href="shareUrl(share.path)" target="_blank" rel="noopener noreferrer">{{ shareUrl(share.path) }}</a></div>
      <div class="share-record-access"><span><AppIcon :name="share.hasExtractionCode ? 'lock' : 'share'" :size="14" />{{ share.hasExtractionCode ? '提取码访问' : '免提取码访问' }}</span>
        <small>{{ share.expiresAt ? `有效至 ${formatDate(share.expiresAt)}` : '长期有效' }}</small></div>
      <span class="share-status-badge" :class="share.status.toLowerCase()">{{ shareStatus[share.status] || '不可用' }}</span>
      <div class="share-record-actions"><button :disabled="share.status !== 'ACTIVE'" @click="$emit('copy', share)"><AppIcon name="copy" :size="14" />复制链接</button>
        <button v-if="share.status !== 'REVOKED'" class="danger-text" :disabled="busyId === share.id" @click="$emit('revoke', share)">{{ busyId === share.id ? '正在取消…' : '取消分享' }}</button></div>
    </li>
  </ul>
</template>
