<script setup>
import { computed } from 'vue'
import AppIcon from '@/components/AppIcon.vue'
import FileActions from './FileActions.vue'
import { fileIcon, typeLabel, formatSize, formatDate } from '@/utils/files'
const props = defineProps({
  items: Array,
  selected: Array,
  layout: String,
  trash: Boolean,
  busy: Boolean,
})
const emit = defineEmits(['toggle', 'select-all', 'open', 'action'])
const allSelected = computed(
  () => props.items.length > 0 && props.items.every((n) => props.selected.includes(n.id)),
)
function size(file) {
  return file.type === 'FOLDER' ? '—' : formatSize(file.size)
}
</script>
<template>
  <div v-if="layout === 'list'" class="file-table-wrap">
    <table class="file-table">
      <thead>
        <tr>
          <th class="check-column">
            <input
              :disabled="busy"
              type="checkbox"
              aria-label="选择本页全部文件"
              :checked="allSelected"
              :indeterminate="selected.length > 0 && !allSelected"
              @change="emit('select-all', $event.target.checked)"
            />
          </th>
          <th>名称</th>
          <th class="type-column">类型</th>
          <th class="size-column">大小</th>
          <th class="time-column">{{ trash ? '删除时间' : '修改时间' }}</th>
          <th class="action-column"><span class="sr-only">操作</span></th>
        </tr>
      </thead>
      <tbody>
        <tr
          v-for="file in items"
          :key="file.id"
          :class="{ selected: selected.includes(file.id) }"
          @dblclick="emit('open', file)"
        >
          <td>
            <input
              :disabled="busy"
              type="checkbox"
              :aria-label="`选择${file.name}`"
              :checked="selected.includes(file.id)"
              @change="emit('toggle', file.id)"
            />
          </td>
          <td>
            <div class="file-name-cell">
              <span class="file-glyph" :class="file.category.toLowerCase()"
                ><AppIcon :name="fileIcon(file)" :size="23" /></span
              ><button class="file-name-button" :title="file.name" @click="emit('open', file)">
                {{ file.name }}<small v-if="file.originalPath">{{ file.originalPath }}</small>
              </button>
            </div>
          </td>
          <td class="type-column">{{ typeLabel(file) }}</td>
          <td class="size-column">{{ size(file) }}</td>
          <td class="time-column">{{ formatDate(trash ? file.deletedAt : file.updatedAt) }}</td>
          <td class="action-column">
            <FileActions
              :file="file"
              :trash="trash"
              :disabled="busy"
              @action="(action, item) => emit('action', action, item)"
            />
          </td>
        </tr>
      </tbody>
    </table>
  </div>
  <div v-else>
    <label class="grid-select-all"
      ><input
        :disabled="busy"
        type="checkbox"
        :checked="allSelected"
        @change="emit('select-all', $event.target.checked)"
      />选择本页全部</label
    >
    <ul class="file-grid">
      <li
        v-for="file in items"
        :key="file.id"
        class="file-card"
        :class="{ selected: selected.includes(file.id) }"
        @dblclick="emit('open', file)"
      >
        <div class="file-card-top">
          <input
            :disabled="busy"
            type="checkbox"
            :aria-label="`选择${file.name}`"
            :checked="selected.includes(file.id)"
            @change="emit('toggle', file.id)"
          /><FileActions
            :file="file"
            :trash="trash"
            :disabled="busy"
            @action="(action, item) => emit('action', action, item)"
          />
        </div>
        <button class="file-card-open" :title="file.name" @click="emit('open', file)">
          <span class="file-glyph" :class="file.category.toLowerCase()"
            ><AppIcon :name="fileIcon(file)" :size="38" /></span
          ><strong>{{ file.name }}</strong>
        </button>
        <p>
          {{ file.type === 'FOLDER' ? '文件夹' : formatSize(file.size)
          }}<span>{{ formatDate(trash ? file.deletedAt : file.updatedAt) }}</span>
        </p>
        <small v-if="file.originalPath" class="grid-path" :title="file.originalPath">{{
          file.originalPath
        }}</small>
      </li>
    </ul>
  </div>
</template>
