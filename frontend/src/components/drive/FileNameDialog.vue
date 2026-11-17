<script setup>
import { ref, watch, nextTick } from 'vue'
import AppIcon from '@/components/AppIcon.vue'
const props = defineProps({
  modelValue: Boolean,
  mode: String,
  file: Object,
  busy: Boolean,
  error: String,
})
const emit = defineEmits(['update:modelValue', 'submit'])
const name = ref(''),
  input = ref(null)
watch(
  () => props.modelValue,
  async (open) => {
    if (!open) return
    name.value = props.mode === 'rename' ? props.file.name : ''
    await nextTick()
    input.value?.focus()
  },
)
function submit() {
  if (name.value.trim() && !props.busy) emit('submit', name.value.trim())
}
</script>
<template>
  <el-dialog
    :model-value="modelValue"
    :title="mode === 'rename' ? '重命名' : '新建文件夹'"
    width="440px"
    class="drive-dialog"
    :close-on-click-modal="!busy"
    :show-close="!busy"
    :close-on-press-escape="!busy"
    @update:model-value="emit('update:modelValue', $event)"
    @opened="input?.focus()"
  >
    <form @submit.prevent="submit">
      <div class="dialog-context">
        <AppIcon
          :name="mode === 'rename' && file?.type === 'FILE' ? 'file' : 'folder'"
          :size="26"
        />
        <p>
          {{ mode === 'rename' ? '给它一个更容易找到的名字。' : '把相关内容收进同一个文件夹。' }}
        </p>
      </div>
      <label for="file-name-input">{{ mode === 'rename' ? '名称' : '文件夹名称' }}</label
      ><el-input
        id="file-name-input"
        ref="input"
        v-model="name"
        maxlength="200"
        placeholder="请输入名称"
        :disabled="busy"
      />
      <p v-if="mode === 'rename' && file.type === 'FILE'" class="dialog-help">
        请保留原文件扩展名。
      </p>
      <p v-if="error" class="inline-error" role="alert">{{ error }}</p>
      <button type="submit" class="sr-only" tabindex="-1">保存</button>
    </form>
    <template #footer
      ><el-button :disabled="busy" @click="emit('update:modelValue', false)">取消</el-button
      ><el-button type="primary" :loading="busy" :disabled="!name.trim()" @click="submit">{{
        mode === 'rename' ? '保存名称' : '创建文件夹'
      }}</el-button></template
    >
  </el-dialog>
</template>
