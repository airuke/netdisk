<script setup>
import { ref, watch, computed } from 'vue'
import { ElMessage } from 'element-plus'
import AppIcon from '@/components/AppIcon.vue'
import { createShare } from '@/api/shares'
import { shareUrl, extractionCode, copyText } from '@/utils/shares'
import { formatSize, formatDate } from '@/utils/files'
const props = defineProps({ modelValue: Boolean, file: Object })
const emit = defineEmits(['update:modelValue', 'created'])
const days = ref(7), protectedShare = ref(true), code = ref(''), busy = ref(false), error = ref(''), created = ref(null)
const url = computed(() => created.value ? shareUrl(created.value.share.path) : '')
const text = computed(() => created.value ? `分享：${props.file?.name}\n链接：${url.value}${created.value.extractionCode ? `\n提取码：${created.value.extractionCode}` : ''}` : '')
watch(() => props.modelValue, open => {
  if (open) { days.value = 7; protectedShare.value = true; code.value = extractionCode(); error.value = ''; created.value = null }
})
async function submit() {
  error.value = ''
  if (protectedShare.value && !/^[a-zA-Z0-9]{4}$/.test(code.value)) { error.value = '提取码需要 4 位字母或数字'; return }
  busy.value = true
  try {
    created.value = await createShare({ nodeId: props.file.id, expiresDays: days.value,
      extractionCode: protectedShare.value ? code.value.toUpperCase() : '' })
    emit('created', created.value.share)
  } catch (e) { error.value = e.message }
  finally { busy.value = false }
}
async function copy() {
  try { await copyText(text.value); ElMessage.success('分享信息已复制') }
  catch (e) { error.value = e.message }
}
</script>
<template>
  <el-dialog :model-value="modelValue" title="分享文件" width="560px" class="drive-dialog share-dialog"
    :close-on-click-modal="!busy" :close-on-press-escape="!busy" :show-close="!busy" @update:model-value="emit('update:modelValue', $event)">
    <div class="share-target"><span class="file-glyph" :class="file?.type === 'FOLDER' ? 'folder' : 'document'"><AppIcon :name="file?.type === 'FOLDER' ? 'folder' : 'file'" :size="26" /></span>
      <div><strong>{{ file?.name }}</strong><span>{{ file?.type === 'FOLDER' ? '文件夹及其中的内容' : formatSize(file?.size) }}</span></div></div>
    <template v-if="!created">
      <form class="share-form" @submit.prevent="submit">
        <label for="share-days">有效期</label><select id="share-days" v-model.number="days" :disabled="busy">
          <option :value="1">1 天</option><option :value="7">7 天</option><option :value="30">30 天</option><option :value="0">长期有效，直到取消分享</option></select>
        <div class="share-code-heading"><label for="share-protected">使用提取码</label><el-switch id="share-protected" v-model="protectedShare" :disabled="busy" aria-label="启用提取码" /></div>
        <div v-if="protectedShare" class="share-code-row"><input v-model="code" maxlength="4" aria-label="四位提取码" autocomplete="off" :disabled="busy" />
          <button type="button" :disabled="busy" @click="code = extractionCode()"><AppIcon name="refresh" :size="15" />换一个</button></div>
        <p class="share-muted">接收方不需要注册或登录网盘。{{ protectedShare ? '输入提取码后即可查看和下载。' : '持有链接的人即可查看和下载。' }}</p>
        <p v-if="error" class="inline-error" role="alert">{{ error }}</p>
        <div class="share-form-actions"><el-button :disabled="busy" @click="emit('update:modelValue', false)">取消</el-button><el-button type="primary" native-type="submit" :loading="busy">创建分享链接</el-button></div>
      </form>
    </template>
    <template v-else>
      <div class="share-created-label"><AppIcon name="check" :size="18" />分享已创建</div>
      <textarea :value="text" readonly rows="4" aria-label="分享链接与提取码" class="share-copy-text" @focus="$event.target.select()" />
      <p class="share-muted">{{ created.share.expiresAt ? `有效至 ${formatDate(created.share.expiresAt)}` : '长期有效，可在“我的分享”中取消。' }}</p>
      <p v-if="created.extractionCode" class="share-muted">请保存提取码。管理列表只提供链接；忘记提取码时需取消后重新创建分享。</p>
      <p v-if="error" class="inline-error" role="alert">{{ error }}</p>
      <div class="share-form-actions"><el-button @click="emit('update:modelValue', false)">完成</el-button><el-button type="primary" @click="copy"><AppIcon name="copy" :size="16" />复制分享信息</el-button></div>
    </template>
  </el-dialog>
</template>
