<template>
  <!-- 顶部通知铃铛：未读数由 App.vue 的 realtime 事件统一维护 -->
  <router-link
    to="/notifications"
    class="bell"
    :class="{ active: route.path === '/notifications' }"
    :title="tip"
  >
    <NavIcon name="bell" />
    <span v-if="store.unread > 0" class="badge">
      {{ store.unread > 99 ? '99+' : store.unread }}
    </span>
  </router-link>
</template>

<script setup lang="ts">
import { computed, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { useNotificationStore } from '@/stores/notification'
import NavIcon from '@/components/ui/NavIcon.vue'

const route = useRoute()
const store = useNotificationStore()
const tip = computed(() => (store.unread > 0 ? `通知（${store.unread} 条未读）` : '通知'))

onMounted(() => {
  void store.refresh()
})
</script>

<style scoped>
.bell {
  position: relative;
  display: grid;
  place-items: center;
  width: 32px;
  height: 32px;
  border-radius: 8px;
  color: var(--aies-ink2);
  text-decoration: none;
  transition: background var(--aies-t), color var(--aies-t);
}
.bell:hover,
.bell.active {
  background: var(--aies-paper);
  color: var(--aies-brand);
}
.badge {
  position: absolute;
  top: 1px;
  right: 0;
  min-width: 16px;
  height: 16px;
  padding: 0 4px;
  border-radius: 8px;
  background: var(--aies-brand);
  color: #fff;
  font-size: 10px;
  line-height: 16px;
  text-align: center;
}
</style>
