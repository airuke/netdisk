<script setup>
import { computed } from 'vue'
import AppIcon from '@/components/AppIcon.vue'
import { formatSize, formatDate } from '@/utils/files'
const props = defineProps({ task: Object })
defineEmits(['pause', 'resume', 'select', 'cancel', 'sync'])
const labels = { queued: '等待上传', hashing: '计算文件指纹', checking: '检查秒传与任务状态', creating: '建立上传任务',
  uploading: '上传分片', verifying: '校验文件', merging: '服务器合并与完整校验', done: '上传完成',
  paused: '已暂停', 'needs-file': '等待重新选择原文件', failed: '上传未完成', cancelled: '已取消' }
const progress = computed(() => props.task.status === 'hashing' ? props.task.hashProgress || 0 : props.task.progress || 0)
const pausable = computed(() => ['queued', 'hashing', 'checking', 'creating', 'uploading', 'verifying', 'merging'].includes(props.task.status)
  && Boolean(props.task.file))
</script>
<template>
  <li>
    <div class="task-heading">
      <AppIcon :name="task.status === 'done' ? 'check' : task.status === 'failed' ? 'alert' : 'file'" :size="18" />
      <strong :title="task.name">{{ task.name }}</strong>
      <span v-if="task.instant && task.status === 'done'" class="instant-badge"><AppIcon name="bolt" :size="12" />秒传</span>
    </div>
    <div class="task-progress" :class="task.status" role="progressbar" :aria-label="`${task.name}${task.status === 'hashing' ? '指纹计算' : '上传'}进度`"
      :aria-valuenow="progress" aria-valuemin="0" aria-valuemax="100"><span :style="{ width: `${progress}%` }" /></div>
    <p class="task-status"><span>{{ task.instant && task.status === 'done' ? '秒传完成' : labels[task.status] }}
      <template v-if="['hashing', 'uploading'].includes(task.status)"> · {{ progress }}%</template>
      <template v-if="task.status === 'uploading'"> · {{ formatSize(task.speed) }}/s</template></span><span>{{ formatSize(task.size) }}</span></p>
    <div class="task-details"><span>{{ task.folderName }}</span>
      <span v-if="task.chunkCount && !task.instant">已保存 {{ task.partCount || 0 }}/{{ task.chunkCount }} 片</span></div>
    <p v-if="task.hash" class="task-fingerprint" :title="task.hash">SHA-256 · {{ task.hash.slice(0, 16) }}…</p>
    <p v-if="task.status === 'needs-file'" class="task-help">重新选择原文件后会校验完整指纹，继续缺少的分片。</p>
    <p v-if="task.status === 'merging'" class="task-help">服务器正在保存最终文件；停止等待不会中断服务端合并，可稍后同步结果。</p>
    <p v-if="task.expiresAt && !['done', 'cancelled'].includes(task.status)" class="task-help">暂存有效至 {{ formatDate(task.expiresAt) }}</p>
    <p v-if="task.error" class="inline-error" role="alert">{{ task.error }}</p>
    <div v-if="!['done', 'cancelled'].includes(task.status) || task.cancelPending" class="task-controls">
      <button v-if="pausable" :disabled="task.stopping || task.cancelPending" @click="$emit('pause', task)">
        <AppIcon name="pause" :size="13" />{{ task.status === 'merging' ? '停止等待' : '暂停' }}</button>
      <template v-else-if="!task.cancelPending">
        <button v-if="task.file && ['paused', 'failed'].includes(task.status)" :disabled="task.stopping || task.retryable === false" @click="$emit('resume', task)">
          <AppIcon name="play" :size="13" />{{ task.restartRequired ? '重新上传' : task.status === 'failed' ? '重试' : '继续' }}</button>
        <button v-else-if="['paused', 'needs-file', 'failed'].includes(task.status)" :disabled="task.stopping || task.retryable === false" @click="$emit('select', task)">
          <AppIcon name="folder" :size="13" />重新选择原文件</button>
        <button v-if="task.status === 'merging'" @click="$emit('sync')"><AppIcon name="refresh" :size="13" />同步状态</button>
      </template>
      <button class="task-cancel" :disabled="task.cancelPending" @click="$emit('cancel', task)">{{ task.cancelPending ? '正在取消…' : '取消任务' }}</button>
      <span v-if="task.stopping">正在停止请求…</span>
    </div>
  </li>
</template>
