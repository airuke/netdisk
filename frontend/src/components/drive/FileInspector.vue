<script setup>
import AppIcon from '@/components/AppIcon.vue'
import { fileIcon, typeLabel, formatSize, formatDate } from '@/utils/files'
defineProps({ files: Array, trash: Boolean })
defineEmits(['preview', 'download'])
</script>
<template>
  <aside class="file-inspector" aria-label="文件信息">
    <h2>文件信息</h2>
    <template v-if="files.length === 1">
      <div class="inspector-art">
        <span class="file-glyph" :class="files[0].category.toLowerCase()"
          ><AppIcon :name="fileIcon(files[0])" :size="54"
        /></span>
      </div>
      <h3>{{ files[0].name }}</h3>
      <p class="inspector-type">{{ typeLabel(files[0]) }}</p>
      <dl>
        <dt>文件大小</dt>
        <dd>{{ files[0].type === 'FOLDER' ? '—' : formatSize(files[0].size) }}</dd>
        <dt>创建时间</dt>
        <dd>{{ formatDate(files[0].createdAt) }}</dd>
        <dt>{{ trash ? '删除时间' : '修改时间' }}</dt>
        <dd>{{ formatDate(trash ? files[0].deletedAt : files[0].updatedAt) }}</dd>
        <template v-if="files[0].originalPath"
          ><dt>所在位置</dt>
          <dd>{{ files[0].originalPath }}</dd></template
        >
      </dl>
      <template v-if="!trash && files[0].type === 'FILE'"
        ><el-button type="primary" @click="$emit('preview', files[0])">预览文件</el-button
        ><el-button @click="$emit('download', files[0])">下载</el-button></template
      >
    </template>
    <template v-else-if="files.length > 1"
      ><div class="inspector-art"><AppIcon name="check" :size="44" /></div>
      <h3>已选择 {{ files.length }} 项</h3>
      <p class="inspector-type">文件合计 {{ formatSize(files.reduce((s, f) => s + f.size, 0)) }}</p>
      <p class="inspector-help">在工具栏中批量整理选中的内容。</p></template
    >
    <template v-else
      ><div class="inspector-art"><AppIcon name="folder" :size="52" /></div>
      <h3>让整理更轻松</h3>
      <p class="inspector-help">勾选文件后，可以在这里查看信息。</p>
      <div class="inspector-tip">
        <AppIcon name="move" :size="18" />
        <div>
          <b>文件位置随你安排</b>
          <p>用“移动到”将资料放进合适的文件夹。</p>
        </div>
      </div>
      <div class="inspector-tip">
        <AppIcon name="restore" :size="18" />
        <div>
          <b>误删也可以找回</b>
          <p>到回收站中还原文件，或确认后彻底删除。</p>
        </div>
      </div></template
    >
  </aside>
</template>
