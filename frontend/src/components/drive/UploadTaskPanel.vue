<script setup>
import { ref } from 'vue'
import AppIcon from '@/components/AppIcon.vue'
import UploadTaskItem from './UploadTaskItem.vue'
import { useUploadsStore } from '@/stores/uploads'
const uploads = useUploadsStore(), input = ref(null)
let selectedTask = null
function select(task) { selectedTask = task; input.value?.click() }
function attach(event) {
  const file = event.target.files?.[0]
  if (file && selectedTask) uploads.attachFile(selectedTask, file)
  selectedTask = null; event.target.value = ''
}
</script>
<template>
  <section v-if="uploads.open" class="upload-panel" aria-label="上传任务">
    <header><div><AppIcon name="upload" :size="18" /><h2>上传任务</h2><span v-if="uploads.activeCount">{{ uploads.activeCount }} 项进行中</span></div>
      <button class="icon-button" aria-label="收起上传任务" @click="uploads.open = false"><AppIcon name="close" :size="18" /></button></header>
    <div v-if="uploads.recoveryError || uploads.persistenceWarning" class="upload-notice" role="alert">
      <p>{{ uploads.recoveryError || uploads.persistenceWarning }}</p>
      <button :disabled="uploads.syncing" @click="uploads.sync">重新同步</button>
    </div>
    <div v-if="!uploads.tasks.length" class="upload-empty"><AppIcon name="cloud" :size="36" /><p>还没有上传任务</p><span>上传文件，或同步服务器上未完成的任务。</span></div>
    <ul v-else class="upload-task-list"><UploadTaskItem v-for="task in uploads.tasks" :key="task.id" :task="task"
      @pause="uploads.pause" @resume="uploads.resume" @select="select" @cancel="uploads.cancel" @sync="uploads.sync" /></ul>
    <footer><button :disabled="uploads.syncing" @click="uploads.sync">{{ uploads.syncing ? '正在同步…' : '恢复/同步任务' }}</button><button @click="uploads.clearCompleted">清除已完成</button></footer>
    <p class="upload-footer-note">刷新后重新选择原文件即可续传。暂存任务按服务器有效期保留。</p>
    <input ref="input" type="file" class="sr-only" tabindex="-1" aria-label="重新选择待续传的原文件" @change="attach" />
  </section>
</template>
