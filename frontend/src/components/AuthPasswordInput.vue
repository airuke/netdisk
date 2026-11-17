<script setup>
import { ref, watch } from 'vue'
import AppIcon from './AppIcon.vue'
const props = defineProps({
  modelValue: { type: String, default: '' },
  placeholder: { type: String, default: '请输入密码' },
  autocomplete: { type: String, default: 'current-password' },
  name: { type: String, default: 'password' },
  label: { type: String, default: '密码' },
  disabled: Boolean,
})
const emit = defineEmits(['update:modelValue'])
const visible = ref(false)
watch(
  () => props.modelValue,
  (value) => {
    if (!value) visible.value = false
  },
)
</script>
<template>
  <el-input
    :model-value="modelValue"
    :type="visible ? 'text' : 'password'"
    :name="name"
    :autocomplete="autocomplete"
    :placeholder="placeholder"
    :maxlength="64"
    size="large"
    :disabled="disabled"
    @update:model-value="emit('update:modelValue', $event)"
  >
    <template #prefix><AppIcon name="lock" :size="18" /></template>
    <template #suffix>
      <button
        type="button"
        class="password-toggle"
        :aria-label="`${visible ? '隐藏' : '显示'}${label}`"
        :aria-pressed="visible"
        :disabled="disabled"
        @click="visible = !visible"
      >
        <AppIcon :name="visible ? 'eye-off' : 'eye'" :size="18" />
      </button>
    </template>
  </el-input>
</template>
