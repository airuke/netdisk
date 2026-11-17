<script setup>
import AppIcon from '@/components/AppIcon.vue'
defineProps({ file: Object, trash: Boolean, disabled: Boolean })
defineEmits(['action'])
</script>
<template>
  <el-dropdown trigger="click" @command="$emit('action', $event, file)" :disabled="disabled">
    <button
      class="icon-button"
      :aria-label="`${file.name}的更多操作`"
      :disabled="disabled"
      @click.stop
    >
      <AppIcon name="more" />
    </button>
    <template #dropdown>
      <el-dropdown-menu>
        <template v-if="trash"
          ><el-dropdown-item command="restore">还原</el-dropdown-item
          ><el-dropdown-item command="purge">彻底删除</el-dropdown-item></template
        >
        <template v-else>
          <el-dropdown-item :command="file.type === 'FOLDER' ? 'open' : 'preview'">{{
            file.type === 'FOLDER' ? '打开文件夹' : '预览'
          }}</el-dropdown-item>
          <el-dropdown-item v-if="file.type === 'FILE'" command="download">下载</el-dropdown-item>
          <el-dropdown-item command="share">分享</el-dropdown-item>
          <el-dropdown-item command="rename">重命名</el-dropdown-item
          ><el-dropdown-item command="move">移动到</el-dropdown-item>
          <el-dropdown-item command="trash" divided>移到回收站</el-dropdown-item>
        </template>
      </el-dropdown-menu>
    </template>
  </el-dropdown>
</template>
