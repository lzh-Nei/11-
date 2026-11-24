import { defineStore } from 'pinia'

/** 聊天未读（全局）：侧栏角标与聊天页共用，切换会话时同步当前对端避免重复计数 */
export const useChatStore = defineStore('chat', {
  state: () => ({
    unreadByPeer: {} as Record<number, number>,
    activePeerId: 0
  }),
  getters: {
    totalUnread: (state) =>
      Object.values(state.unreadByPeer).reduce((sum, count) => sum + (Number(count) || 0), 0)
  },
  actions: {
    /** 以服务端会话列表为准整体对齐未读数 */
    syncContacts(list: { userId: number; unread: number }[]) {
      const next: Record<number, number> = {}
      list.forEach((item) => {
        if (item.unread > 0) next[item.userId] = item.unread
      })
      this.unreadByPeer = next
    },
    addUnread(peerId: number) {
      this.unreadByPeer[peerId] = (this.unreadByPeer[peerId] ?? 0) + 1
    },
    clearPeer(peerId: number) {
      delete this.unreadByPeer[peerId]
    },
    /** 当前正在查看的会话（全局提醒据此跳过已读会话） */
    setActivePeer(peerId: number) {
      this.activePeerId = peerId
    },
    reset() {
      this.unreadByPeer = {}
      this.activePeerId = 0
    }
  }
})
