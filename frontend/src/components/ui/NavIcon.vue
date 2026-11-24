<template>
  <!-- 模块图标（内联 SVG，不引图标库）：24x24 线性，颜色随 currentColor -->
  <svg
    class="nav-icon"
    viewBox="0 0 24 24"
    fill="none"
    stroke="currentColor"
    stroke-width="1.8"
    stroke-linecap="round"
    stroke-linejoin="round"
    aria-hidden="true"
  >
    <template v-if="name === 'dashboard'">
      <rect x="3.5" y="3.5" width="7" height="7" rx="1.6" />
      <rect x="13.5" y="3.5" width="7" height="7" rx="1.6" />
      <rect x="3.5" y="13.5" width="7" height="7" rx="1.6" />
      <rect x="13.5" y="13.5" width="7" height="7" rx="1.6" />
    </template>
    <template v-else-if="name === 'tasks'">
      <path d="M9.5 6h10.5M9.5 12h10.5M9.5 18h10.5" />
      <path d="M4 6l1.2 1.2L7.4 5M4 12l1.2 1.2L7.4 11M4 18l1.2 1.2L7.4 17" />
    </template>
    <template v-else-if="name === 'archive'">
      <path d="M4 7.5h16V20H4z" />
      <path d="M4 7.5l2-3.5h12l2 3.5" />
      <path d="M9.5 11.5h5" />
    </template>
    <template v-else-if="name === 'evaluation'">
      <path d="M12 3.2l2.7 5.5 6 .9-4.3 4.3 1 6-5.4-2.8-5.4 2.8 1-6L3.3 9.6l6-.9z" />
    </template>
    <template v-else-if="name === 'manage'">
      <path d="M4 6.5h16M4 12h16M4 17.5h16" />
      <circle cx="9" cy="6.5" r="2.1" fill="var(--aies-surface)" />
      <circle cx="15" cy="12" r="2.1" fill="var(--aies-surface)" />
      <circle cx="7.5" cy="17.5" r="2.1" fill="var(--aies-surface)" />
    </template>
    <template v-else-if="name === 'review'">
      <path d="M12 3l7 2.8v5.7c0 4.4-2.9 7.4-7 9-4.1-1.6-7-4.6-7-9V5.8z" />
      <path d="M9 12l2 2 4-4.2" />
    </template>
    <template v-else-if="name === 'chart'">
      <path d="M4 20h16" />
      <rect x="6" y="10.5" width="3" height="6.5" rx="0.8" />
      <rect x="11" y="6" width="3" height="11" rx="0.8" />
      <rect x="16" y="13" width="3" height="4" rx="0.8" />
    </template>
    <template v-else-if="name === 'files'">
      <path d="M3.5 7h6l2 2H20.5v10H3.5z" />
    </template>
    <template v-else-if="name === 'chat'">
      <path d="M4 5.5h16v10.5H9.5L4 19.5z" />
      <path d="M8 9.5h8M8 12.5h5" />
    </template>
    <template v-else-if="name === 'user'">
      <circle cx="12" cy="8" r="3.5" />
      <path d="M5 20c1.5-4 4-6 7-6s5.5 2 7 6" />
    </template>
    <template v-else-if="name === 'bell'">
      <path d="M6 16.5V11a6 6 0 1112 0v5.5l1.8 2H4.2z" />
      <path d="M10 19.5a2 2 0 004 0" />
    </template>
    <template v-else-if="name === 'lock'">
      <rect x="5" y="10" width="14" height="10" rx="2" />
      <path d="M8 10V8a4 4 0 118 0v2" />
    </template>
    <template v-else>
      <circle cx="12" cy="12" r="3" />
      <path d="M12 3v2M12 19v2M3 12h2M19 12h2M5.6 5.6L7 7M17 17l1.4 1.4M18.4 5.6L17 7M7 17l-1.4 1.4" />
    </template>
  </svg>
</template>

<script setup lang="ts">
defineProps<{ name: string }>()
</script>

<style scoped>
.nav-icon {
  width: 18px;
  height: 18px;
  flex: none;
}
</style>
