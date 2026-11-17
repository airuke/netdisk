<script setup>
import AppIcon from '@/components/AppIcon.vue'
defineProps({ trash: Boolean, search: Boolean, canCreate: Boolean, error: String })
defineEmits(['upload', 'create', 'retry', 'home'])
</script>
<template>
  <div class="file-empty" :role="error ? 'alert' : 'status'">
    <div class="empty-art">
      <span class="empty-sheet sheet-back" /><span class="empty-sheet sheet-front"
        ><AppIcon
          :name="error ? 'alert' : trash ? 'trash' : search ? 'search' : 'folder'"
          :size="44" /></span
      ><i v-if="!error"><AppIcon name="check" :size="14" /></i>
    </div>
    <h2>
      {{
        error
          ? '文件暂时没有加载出来'
          : search
            ? '没有找到匹配的文件'
            : trash
              ? '回收站很干净'
              : '给文件一个新家'
      }}
    </h2>
    <p>
      {{
        error ||
        (search
          ? '试试更短的名称，或检查搜索条件。'
          : trash
            ? '删除的文件和文件夹会显示在这里。'
            : canCreate
              ? '上传第一份文件，或先建一个文件夹。'
              : '此分类还没有内容，上传对应类型的文件后就会出现。')
      }}
    </p>
    <div class="empty-actions" v-if="error">
      <el-button type="primary" @click="$emit('retry')">重新加载</el-button
      ><el-button @click="$emit('home')">返回全部文件</el-button>
    </div>
    <div class="empty-actions" v-else-if="canCreate">
      <el-button type="primary" @click="$emit('upload')"
        ><AppIcon name="upload" :size="17" />上传文件</el-button
      ><el-button @click="$emit('create')">新建文件夹</el-button>
    </div>
    <el-button v-else-if="search || (!trash && !canCreate)" @click="$emit('home')"
      >返回全部文件</el-button
    >
  </div>
</template>
