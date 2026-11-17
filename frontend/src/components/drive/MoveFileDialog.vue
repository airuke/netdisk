<script setup>
import { ref, watch, computed } from 'vue'
import AppIcon from '@/components/AppIcon.vue'
import { listFiles } from '@/api/files'
const props = defineProps({ modelValue: Boolean, files: Array, busy: Boolean, error: String })
const emit = defineEmits(['update:modelValue', 'submit'])
const parentId = ref('0'),
  folders = ref([]),
  crumbs = ref([]),
  loading = ref(false),
  loadError = ref(''),
  page = ref(1),
  total = ref(0)
let serial = 0
const forbidden = computed(() => props.files.map((f) => f.id))
async function load(id = parentId.value, p = 1) {
  const current = ++serial
  loading.value = true
  loadError.value = ''
  try {
    const data = await listFiles({
      parentId: id,
      page: p,
      pageSize: 100,
      sort: 'name',
      direction: 'asc',
    })
    if (current !== serial) return
    parentId.value = id
    page.value = p
    crumbs.value = data.breadcrumbs
    total.value = data.total
    folders.value = data.items.filter((f) => f.type === 'FOLDER')
  } catch (e) {
    if (current === serial) loadError.value = e.message
  } finally {
    if (current === serial) loading.value = false
  }
}
watch(
  () => props.modelValue,
  (open) => {
    if (open) load('0')
    else serial++
  },
)
</script>
<template>
  <el-dialog
    :model-value="modelValue"
    title="移动到"
    width="500px"
    class="drive-dialog"
    :close-on-click-modal="!busy"
    :show-close="!busy"
    :close-on-press-escape="!busy"
    @update:model-value="emit('update:modelValue', $event)"
  >
    <p class="dialog-help">将 {{ files.length }} 项内容放进选定的文件夹。</p>
    <nav class="move-breadcrumbs" aria-label="目标目录">
      <template v-for="(crumb, i) in crumbs" :key="crumb.id"
        ><AppIcon v-if="i" name="chevron" :size="13" /><button
          :disabled="busy || loading"
          @click="load(crumb.id)"
        >
          {{ crumb.name }}
        </button></template
      >
    </nav>
    <div class="folder-picker" v-loading="loading">
      <p v-if="loadError" class="inline-error" role="alert">
        {{ loadError }}<button @click="load()">重新加载</button>
      </p>
      <p v-else-if="!folders.length && !loading" class="picker-empty">
        {{
          total > 0
            ? '此页没有可进入的文件夹，可切换页码。'
            : '这里没有子文件夹，可直接移入当前位置。'
        }}
      </p>
      <button
        v-for="folder in folders"
        :key="folder.id"
        :disabled="forbidden.includes(folder.id) || busy"
        @click="load(folder.id)"
      >
        <AppIcon name="folder" :size="23" /><span>{{ folder.name }}</span
        ><small v-if="forbidden.includes(folder.id)">所选目录</small
        ><AppIcon v-else name="chevron" :size="16" />
      </button>
    </div>
    <el-pagination
      v-if="total > 100"
      small
      layout="prev, pager, next"
      :current-page="page"
      :page-size="100"
      :total="total"
      @current-change="(p) => load(parentId, p)"
    />
    <p v-if="error" class="inline-error" role="alert">{{ error }}</p>
    <template #footer
      ><el-button :disabled="busy" @click="emit('update:modelValue', false)">取消</el-button
      ><el-button
        type="primary"
        :loading="busy"
        :disabled="loading || !!loadError || forbidden.includes(parentId)"
        @click="emit('submit', parentId)"
        >移到这里</el-button
      ></template
    >
  </el-dialog>
</template>
