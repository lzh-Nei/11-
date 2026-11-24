<template>
  <!-- 通知中心（V2）：评价结果 / 教师复核 / 申诉受理 / 聊天消息 -->
  <PageShell :width="880">
    <PageHeader title="通知中心" subtitle="评价结果、教师复核、申诉受理与聊天消息提醒">
      <el-button size="small" :loading="loading" @click="load">刷新</el-button>
      <el-button size="small" plain :disabled="!store.unread" @click="readAll">全部已读</el-button>
    </PageHeader>

    <ScrollArea max-height="calc(100vh - 220px)" padded>
      <AppSkeleton v-if="loading && !items.length" :rows="4" :row-height="64" />
      <ErrorState v-else-if="error" :message="error" @retry="load" />
      <EmptyState v-else-if="!items.length" description="暂无通知——有新的评价或消息时会出现在这里" />
      <div v-else class="list">
        <button
          v-for="item in items"
          :key="item.id"
          type="button"
          class="row"
          :class="{ unread: !item.read }"
          @click="open(item)"
        >
          <span class="dot" />
          <span class="body">
            <span class="title">
              {{ item.title }}
              <i>{{ typeLabel(item.type) }}</i>
            </span>
            <span class="content">{{ item.content }}</span>
          </span>
          <time>{{ shortTime(item.createTime) }}</time>
        </button>
      </div>
    </ScrollArea>
  </PageShell>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { notificationApi, type AppNotification } from '@/api/notification'
import { useNotificationStore } from '@/stores/notification'
import { formatDateTime } from '@/utils/format'
import PageShell from '@/components/ui/PageShell.vue'
import PageHeader from '@/components/ui/PageHeader.vue'
import AppSkeleton from '@/components/ui/AppSkeleton.vue'
import ErrorState from '@/components/ui/ErrorState.vue'
import EmptyState from '@/components/ui/EmptyState.vue'
import ScrollArea from '@/components/ui/ScrollArea.vue'

const router = useRouter()
const store = useNotificationStore()
const items = ref<AppNotification[]>([])
const loading = ref(false)
const error = ref<string | null>(null)

const TYPE_LABEL: Record<string, string> = {
  EVALUATION: '评价',
  REVIEW: '复核',
  APPEAL: '申诉',
  CHAT: '消息',
  SYSTEM: '系统'
}

function typeLabel(type: string): string {
  return TYPE_LABEL[type] ?? '通知'
}

function shortTime(value: string): string {
  if (!value) return ''
  return formatDateTime(value)
}

async function load(): Promise<void> {
  loading.value = true
  error.value = null
  try {
    items.value = await notificationApi.list(50)
    await store.refresh()
  } catch (e) {
    error.value = (e as { message?: string })?.message || '通知加载失败，请稍后重试'
  } finally {
    loading.value = false
  }
}

async function open(item: AppNotification): Promise<void> {
  if (!item.read) {
    try {
      await notificationApi.markRead(item.id)
      item.read = true
      store.setUnread(store.unread - 1)
    } catch {
      // 已读失败不阻断跳转
    }
  }
  if (item.link) router.push(item.link)
}

async function readAll(): Promise<void> {
  try {
    await notificationApi.markAllRead()
    items.value.forEach((item) => {
      item.read = true
    })
    store.setUnread(0)
  } catch {
    // 全局拦截器已提示
  }
}

onMounted(load)
</script>

<style scoped>
.list {
  display: flex;
  flex-direction: column;
}
.row {
  display: flex;
  align-items: flex-start;
  gap: 12px;
  width: 100%;
  padding: 13px 14px;
  border: none;
  border-bottom: 1px solid var(--aies-border);
  background: transparent;
  font-family: inherit;
  text-align: left;
  cursor: pointer;
  transition: background var(--aies-t);
}
.row:hover {
  background: var(--aies-paper);
}
.dot {
  width: 7px;
  height: 7px;
  margin-top: 7px;
  flex: none;
  border-radius: 50%;
  background: transparent;
}
.row.unread .dot {
  background: var(--aies-brand);
}
.body {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.title {
  font-size: 13.5px;
  color: var(--aies-ink2);
  display: flex;
  align-items: center;
  gap: 6px;
}
.row.unread .title {
  color: var(--aies-ink);
  font-weight: 600;
}
.title i {
  font-style: normal;
  font-size: 10px;
  color: var(--aies-ink3);
  border: 1px solid var(--aies-border);
  border-radius: 999px;
  padding: 0 6px;
}
.content {
  font-size: 12.5px;
  color: var(--aies-ink3);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.row time {
  flex: none;
  font-size: 11px;
  color: var(--aies-ink3);
}
</style>
