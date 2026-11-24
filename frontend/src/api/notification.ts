import request from './request'

/** 站内通知（V2 通知中心） */
export interface AppNotification {
  id: number
  type: string
  title: string
  content: string
  link: string | null
  read: boolean
  createTime: string
}

export const notificationApi = {
  async list(size = 30): Promise<AppNotification[]> {
    return (await request.get('/notifications', { params: { size } })) as unknown as AppNotification[]
  },
  async unreadCount(): Promise<number> {
    const data = (await request.get('/notifications/unread-count')) as unknown as {
      unread?: number
    }
    return Number(data?.unread ?? 0)
  },
  async markRead(id: number): Promise<void> {
    await request.post(`/notifications/${id}/read`)
  },
  async markAllRead(): Promise<number> {
    return (await request.post('/notifications/read-all')) as unknown as number
  }
}
