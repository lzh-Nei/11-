import { defineStore } from 'pinia'
import { notificationApi } from '@/api/notification'

/** 通知未读数（铃铛角标）；写入方在 App.vue 的 realtime 监听内统一刷新 */
export const useNotificationStore = defineStore('notification', {
  state: () => ({ unread: 0 }),
  actions: {
    async refresh(): Promise<void> {
      try {
        this.unread = await notificationApi.unreadCount()
      } catch {
        // 角标刷新失败不打扰用户，下次事件/进入页面时重试
      }
    },
    setUnread(value: number) {
      this.unread = Math.max(0, Number(value) || 0)
    },
    reset() {
      this.unread = 0
    }
  }
})
