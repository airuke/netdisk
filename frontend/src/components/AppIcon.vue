<script setup>
defineProps({
  name: { type: String, default: 'file' },
  size: { type: [Number, String], default: 20 },
})
const paths = {
  pause: ['M8 5v14M16 5v14'],
  play: ['m8 4 12 8-12 8Z'],
  bolt: ['M13 2 4 14h7l-1 8 10-12h-7Z'],
  copy: ['M8 8h13v13H8ZM3 16V3h13'],
  download: ['M12 3v13m-5-5 5 5 5-5', 'M4 17v4h16v-4'],
  grid: ['M3 3h7v7H3ZM14 3h7v7h-7ZM3 14h7v7H3ZM14 14h7v7h-7Z'],
  list: ['M8 6h13M8 12h13M8 18h13M3 6h.01M3 12h.01M3 18h.01'],
  trash: ['M3 6h18M8 6V3h8v3M5 6l1 15h12l1-15M10 10v7M14 10v7'],
  clock: ['M12 8v4l3 2', 'M22 12a10 10 0 1 1-20 0 10 10 0 0 1 20 0Z'],
  video: ['M3 5h13v14H3ZM16 10l5-3v10l-5-3'],
  music: ['M9 18V5l12-2v13M9 8l12-2', 'M9 18a3 3 0 1 1-3-3h3M21 16a3 3 0 1 1-3-3h3'],
  plus: ['M12 5v14M5 12h14'],
  chevron: ['m9 5 7 7-7 7'],
  back: ['M19 12H5m5-5-5 5 5 5'],
  close: ['m6 6 12 12M6 18 18 6'],
  more: ['M5 12h.01M12 12h.01M19 12h.01'],
  refresh: ['M20 7V3l-3 3a8 8 0 1 0 3 6', 'M20 3h-5'],
  rename: ['m15 4 5 5M4 20l4-1L21 6l-3-3L5 16Z'],
  move: ['M12 3v18M3 12h18m-4-4 4 4-4 4M8 4l4-3 4 3'],
  info: ['M12 11v6M12 7h.01', 'M22 12a10 10 0 1 1-20 0 10 10 0 0 1 20 0Z'],
  logout: ['M9 3H4v18h5M10 12h11m-4-4 4 4-4 4'],
  menu: ['M3 6h18M3 12h18M3 18h18'],
  alert: ['M12 3 2 21h20ZM12 9v5M12 17h.01'],
  restore: ['M3 11a9 9 0 1 1 2 7M3 4v7h7M12 7v5l3 2'],

  cloud: ['M7 18a5 5 0 0 1-1-9.9A6 6 0 0 1 17.8 8a5 5 0 0 1 .2 10H7Z'],
  eye: ['M2 12s3-7 10-7 10 7 10 7-3 7-10 7S2 12 2 12Z', 'M15 12a3 3 0 1 1-6 0 3 3 0 0 1 6 0Z'],
  'eye-off': [
    'm3 3 18 18M9 5.5A12 12 0 0 1 12 5c7 0 10 7 10 7a17 17 0 0 1-3 4M6 6c-3 2-4 6-4 6s3 7 10 7c2 0 4-.5 5-1.5',
  ],
  folder: ['M3 7V5a1 1 0 0 1 1-1h5l2 3h9a1 1 0 0 1 1 1v11a1 1 0 0 1-1 1H4a1 1 0 0 1-1-1V7Z'],
  file: [
    'M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8Z',
    'M14 2v6h6',
    'M8 13h8M8 17h5',
  ],
  image: ['M3 3h18v18H3Z', 'M3 16l6-6 5 5 3-3 4 4', 'M16 7h.01'],
  share: [
    'M10 13a5 5 0 0 0 7 0l3-3a5 5 0 0 0-7-7l-2 2',
    'M14 11a5 5 0 0 0-7 0l-3 3a5 5 0 0 0 7 7l2-2',
  ],
  upload: ['M12 16V3M7 8l5-5 5 5', 'M4 16v5h16v-5'],
  search: ['M21 21l-5-5', 'M18 10a8 8 0 1 1-16 0 8 8 0 0 1 16 0Z'],
  check: ['m5 12 4 4L19 6'],
  arrow: ['M5 12h14m-5-5 5 5-5 5'],
  lock: ['M5 10h14v11H5Z', 'M8 10V6a4 4 0 0 1 8 0v4', 'M12 14v3'],
  user: ['M20 21v-2a7 7 0 0 0-14 0v2', 'M17 7a5 5 0 1 1-10 0 5 5 0 0 1 10 0Z'],
}
</script>
<template>
  <svg
    :width="size"
    :height="size"
    viewBox="0 0 24 24"
    fill="none"
    stroke="currentColor"
    stroke-width="1.7"
    stroke-linecap="round"
    stroke-linejoin="round"
    aria-hidden="true"
    class="app-icon"
  >
    <path v-for="(path, index) in paths[name] || paths.file" :key="index" :d="path" />
  </svg>
</template>
