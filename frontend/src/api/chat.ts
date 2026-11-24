import request from './request'

/** 会话列表项（V20 chat:use） */
export interface ChatContact {
  userId: number
  realName: string
  role: string
  online: boolean
  lastContent: string | null
  lastTime: string | null
  unread: number
}

/** 聊天消息 */
export interface ChatMessage {
  id: number
  senderId: number
  receiverId: number
  content: string
  read: boolean
  createTime: string
}

export const chatApi = {
  async contacts(): Promise<ChatContact[]> {
    return (await request.get('/chat/contacts')) as unknown as ChatContact[]
  },
  /** 历史消息：beforeId 用于加载更早一页，返回时间正序 */
  async messages(peerId: number, beforeId?: number, size = 30): Promise<ChatMessage[]> {
    const params: Record<string, unknown> = { peerId, size }
    if (beforeId) params.beforeId = beforeId
    return (await request.get('/chat/messages', { params })) as unknown as ChatMessage[]
  },
  async send(receiverId: number, content: string): Promise<ChatMessage> {
    return (await request.post('/chat/messages', { receiverId, content })) as unknown as ChatMessage
  },
  async markRead(peerId: number): Promise<{ updated: number }> {
    return (await request.post(`/chat/conversations/${peerId}/read`)) as unknown as {
      updated: number
    }
  }
}
