<template>
  <!-- 在线聊天（V20）：学生 ↔ 任课教师，历史走 REST，新消息走 /ws chat.message -->
  <div class="chat-page">
    <aside class="contacts">
      <header class="contacts-head">
        <div class="head-row">
          <h2>消息</h2>
          <button type="button" class="refresh" :disabled="loadingContacts" @click="refreshContacts">
            {{ loadingContacts ? '刷新中…' : '刷新' }}
          </button>
        </div>
        <span class="hint">与任课教师 / 班级学生沟通</span>
      </header>
      <div v-if="loadingContacts" class="empty">加载中…</div>
      <div v-else-if="!contacts.length" class="empty">暂无可联系人</div>
      <button
        v-for="item in contacts"
        :key="item.userId"
        class="contact"
        :class="{ active: item.userId === activeId }"
        @click="open(item.userId)"
      >
        <span class="avatar">{{ initial(item.realName) }}</span>
        <span class="contact-meta">
          <span class="contact-name">
            {{ item.realName }}
            <i>{{ item.role === 'TEACHER' ? '教师' : '学生' }}</i>
          </span>
          <span class="contact-preview">{{ item.lastContent || '暂无消息' }}</span>
        </span>
        <span class="contact-right">
          <time>{{ shortTime(item.lastTime) }}</time>
          <b v-if="item.unread > 0" class="unread">{{ item.unread > 99 ? '99+' : item.unread }}</b>
        </span>
        <span class="online-dot" :class="{ on: item.online }" :title="item.online ? '在线' : '离线'" />
      </button>
    </aside>

    <section class="panel">
      <template v-if="activeContact">
        <header class="panel-head">
          <b>{{ activeContact.realName }}</b>
          <span class="status" :class="{ on: activeContact.online }">
            {{ activeContact.online ? '在线' : '离线' }}
          </span>
        </header>

        <div ref="scrollRef" class="message-list">
          <div v-if="messagesError && !messages.length" class="empty">
            {{ messagesError }}
            <button type="button" class="retry" @click="loadMessages">重新加载</button>
          </div>
          <button
            v-else-if="hasMore"
            class="load-more"
            :disabled="loadingMessages"
            @click="loadMore"
          >
            加载更早消息
          </button>
          <p v-if="!messages.length && !loadingMessages && !messagesError" class="empty">
            还没有消息，打个招呼吧
          </p>
          <div
            v-for="message in messages"
            :key="message.id"
            class="message-row"
            :class="{ mine: message.senderId === myId }"
          >
            <div class="bubble">{{ message.content }}</div>
            <time>{{ shortTime(message.createTime) }}</time>
          </div>
        </div>

        <form class="composer" @submit.prevent="send">
          <textarea
            v-model="draft"
            maxlength="1000"
            rows="2"
            placeholder="输入消息，Enter 发送，Shift + Enter 换行"
            @keydown.enter.exact.prevent="send"
          />
          <div class="composer-side">
            <small v-if="sendError" class="send-error">{{ sendError }}</small>
            <small v-else>{{ draft.length }}/1000</small>
            <button type="submit" :disabled="!draft.trim() || sending">
              {{ sending ? '发送中…' : '发送' }}
            </button>
          </div>
        </form>
      </template>
      <div v-else class="empty big">选择左侧联系人开始聊天</div>
    </section>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { chatApi, type ChatContact, type ChatMessage } from '@/api/chat'
import { onRealtimeEvent, realtimeConnected, type RealtimeEvent } from '@/utils/realtime'
import { useAuthStore } from '@/stores/auth'
import { useChatStore } from '@/stores/chat'

const PAGE_SIZE = 30
/** 在线状态无服务端事件，靠定时拉取会话列表刷新（60s）+ 窗口聚焦/WS 重连即时刷新 */
const CONTACTS_REFRESH_MS = 60000

const auth = useAuthStore()
const chatStore = useChatStore()
const contacts = ref<ChatContact[]>([])
const messages = ref<ChatMessage[]>([])
const activeId = ref<number | null>(null)
const draft = ref('')
const loadingContacts = ref(false)
const loadingMessages = ref(false)
const sending = ref(false)
const hasMore = ref(false)
const messagesError = ref<string | null>(null)
const sendError = ref<string | null>(null)
const scrollRef = ref<HTMLElement | null>(null)

const myId = computed(() => auth.me?.id ?? 0)
const activeContact = computed(
  () => contacts.value.find((item) => item.userId === activeId.value) ?? null
)

let unsubscribe: (() => void) | null = null
let contactsTimer: number | undefined
let disposed = false
/** 会话切换序号：异步响应回来时校验，防止旧会话数据覆盖新会话 */
let loadSeq = 0

async function loadContacts(silent = false): Promise<void> {
  if (!silent) loadingContacts.value = true
  try {
    const rows = await chatApi.contacts()
    if (disposed) return
    contacts.value = rows
    chatStore.syncContacts(rows)
  } catch {
    // 全局拦截器已提示；静默刷新失败时保留上次列表
  } finally {
    if (!silent) loadingContacts.value = false
  }
}

function refreshContacts(): void {
  void loadContacts()
}

async function open(peerId: number): Promise<void> {
  if (activeId.value === peerId) return
  activeId.value = peerId
  chatStore.setActivePeer(peerId)
  messages.value = []
  hasMore.value = false
  messagesError.value = null
  sendError.value = null
  draft.value = ''
  loadSeq += 1
  await loadMessages()
  await markRead(peerId)
}

async function loadMessages(): Promise<void> {
  const peerId = activeId.value
  if (peerId === null) return
  const seq = ++loadSeq
  loadingMessages.value = true
  messagesError.value = null
  try {
    const rows = await chatApi.messages(peerId, undefined, PAGE_SIZE)
    // 响应前校验会话未切换，避免旧响应覆盖新会话
    if (disposed || seq !== loadSeq || activeId.value !== peerId) return
    messages.value = rows
    hasMore.value = rows.length === PAGE_SIZE
    await scrollToBottom()
  } catch (e) {
    if (seq !== loadSeq || activeId.value !== peerId) return
    messagesError.value = (e as { message?: string })?.message || '消息加载失败，请稍后重试'
  } finally {
    if (seq === loadSeq) loadingMessages.value = false
  }
}

async function loadMore(): Promise<void> {
  const peerId = activeId.value
  if (peerId === null || !messages.value.length || loadingMessages.value) return
  loadingMessages.value = true
  const container = scrollRef.value
  const oldHeight = container?.scrollHeight ?? 0
  try {
    const rows = await chatApi.messages(peerId, messages.value[0].id, PAGE_SIZE)
    if (disposed || activeId.value !== peerId) return
    messages.value = [...rows, ...messages.value]
    hasMore.value = rows.length === PAGE_SIZE
    await nextTick()
    if (container) {
      container.scrollTop = container.scrollHeight - oldHeight
    }
  } catch {
    // 全局拦截器已提示；保留当前已加载消息
  } finally {
    if (activeId.value === peerId) loadingMessages.value = false
  }
}

async function markRead(peerId: number): Promise<void> {
  try {
    await chatApi.markRead(peerId)
  } catch {
    // 已读失败不阻断会话打开：全局提示后由下次刷新纠正
    return
  }
  const contact = contacts.value.find((item) => item.userId === peerId)
  if (contact) contact.unread = 0
  chatStore.clearPeer(peerId)
}

async function send(): Promise<void> {
  const peerId = activeId.value
  const text = draft.value.trim()
  if (!text || peerId === null || sending.value) return
  sending.value = true
  sendError.value = null
  try {
    const message = await chatApi.send(peerId, text)
    updateContactPreview(message)
    // 发送期间可能已切走会话：只更新预览，不污染新会话消息列表/输入框
    if (activeId.value === peerId) {
      append(message)
      draft.value = ''
      await scrollToBottom()
    }
  } catch {
    // 全局拦截器已提示；保留草稿便于直接重试
    sendError.value = '发送失败，请重试'
  } finally {
    sending.value = false
  }
}

function append(message: ChatMessage): void {
  if (messages.value.some((item) => item.id === message.id)) return
  messages.value.push(message)
}

function updateContactPreview(message: ChatMessage): void {
  const peerId = message.senderId === myId.value ? message.receiverId : message.senderId
  const contact = contacts.value.find((item) => item.userId === peerId)
  if (!contact) return
  contact.lastContent = message.content
  contact.lastTime = message.createTime
}

function onRealtime(event: RealtimeEvent): void {
  if (event.type !== 'chat.message') return
  const message = event.data as ChatMessage
  if (!message?.id) return
  const peerId = message.senderId === myId.value ? message.receiverId : message.senderId
  const contact = contacts.value.find((item) => item.userId === peerId)
  if (contact) {
    contact.lastContent = message.content
    contact.lastTime = message.createTime
    if (message.receiverId === myId.value && peerId !== activeId.value) {
      contact.unread += 1
    }
  } else {
    void loadContacts(true)
  }
  if (peerId !== activeId.value) return
  append(message)
  if (message.receiverId === myId.value) {
    if (contact) contact.unread = 0
    void markRead(peerId)
  }
  void scrollToBottom()
}

async function scrollToBottom(): Promise<void> {
  await nextTick()
  const container = scrollRef.value
  if (container) container.scrollTop = container.scrollHeight
}

function initial(name: string): string {
  return name ? name.slice(0, 1) : '?'
}

function shortTime(value: string | null): string {
  if (!value) return ''
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return ''
  const now = new Date()
  const sameDay = date.toDateString() === now.toDateString()
  const hh = String(date.getHours()).padStart(2, '0')
  const mm = String(date.getMinutes()).padStart(2, '0')
  if (sameDay) return `${hh}:${mm}`
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')
  return `${month}-${day} ${hh}:${mm}`
}

/** 在线状态无服务端推送：定时 + 窗口聚焦 + WS 重连三路刷新 */
function onWindowFocus(): void {
  void loadContacts(true)
}

watch(realtimeConnected, (connected) => {
  if (connected) void loadContacts(true)
})

onMounted(async () => {
  unsubscribe = onRealtimeEvent(onRealtime)
  window.addEventListener('focus', onWindowFocus)
  contactsTimer = window.setInterval(() => void loadContacts(true), CONTACTS_REFRESH_MS)
  await loadContacts()
  if (contacts.value.length > 0) {
    await open(contacts.value[0].userId)
  }
})

onBeforeUnmount(() => {
  disposed = true
  unsubscribe?.()
  window.removeEventListener('focus', onWindowFocus)
  if (contactsTimer !== undefined) window.clearInterval(contactsTimer)
  chatStore.setActivePeer(0)
})
</script>

<style scoped>
.chat-page {
  display: flex;
  height: calc(100vh - 48px);
  min-height: 420px;
  background: var(--aies-surface);
}
.contacts {
  width: 288px;
  flex: none;
  border-right: 1px solid var(--aies-border);
  overflow-y: auto;
  background: var(--aies-surface);
}
.contacts-head {
  padding: 18px 16px 10px;
  border-bottom: 1px solid var(--aies-border);
}
.contacts-head h2 {
  margin: 0;
  font-size: 17px;
  color: var(--aies-ink);
}
.head-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}
.refresh,
.retry {
  border: 1px solid var(--aies-border);
  background: transparent;
  color: var(--aies-ink2);
  border-radius: 6px;
  font-size: 11.5px;
  padding: 4px 9px;
  font-family: inherit;
  cursor: pointer;
}
.refresh:hover,
.retry:hover {
  border-color: var(--aies-brand);
  color: var(--aies-brand);
}
.refresh:disabled {
  opacity: 0.55;
  cursor: default;
}
.retry {
  margin-left: 8px;
}
.hint {
  display: block;
  margin-top: 4px;
  font-size: 11.5px;
  color: var(--aies-ink3);
}
.contact {
  position: relative;
  width: 100%;
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 11px 14px;
  border: none;
  border-bottom: 1px solid var(--aies-border);
  background: transparent;
  font-family: inherit;
  text-align: left;
  cursor: pointer;
}
.contact:hover {
  background: var(--aies-paper);
}
.contact.active {
  background: var(--aies-brand-soft, #f7ede6);
}
.avatar {
  width: 36px;
  height: 36px;
  flex: none;
  display: grid;
  place-items: center;
  border-radius: 50%;
  background: var(--aies-brand);
  color: #fff;
  font-size: 14px;
  font-weight: 600;
}
.contact-meta {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 3px;
}
.contact-name {
  font-size: 13.5px;
  color: var(--aies-ink);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.contact-name i {
  margin-left: 4px;
  font-style: normal;
  font-size: 10.5px;
  color: var(--aies-ink3);
}
.contact-preview {
  font-size: 12px;
  color: var(--aies-ink3);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.contact-right {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 4px;
  flex: none;
}
.contact-right time {
  font-size: 10.5px;
  color: var(--aies-ink3);
}
.unread {
  min-width: 17px;
  height: 17px;
  padding: 0 5px;
  border-radius: 9px;
  background: var(--aies-brand);
  color: #fff;
  font-size: 10.5px;
  line-height: 17px;
  text-align: center;
}
.online-dot {
  position: absolute;
  left: 41px;
  top: 37px;
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: #c9c9c9;
  border: 2px solid var(--aies-surface);
}
.online-dot.on {
  background: #2f9e6e;
}
.panel {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
}
.panel-head {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 14px 20px;
  border-bottom: 1px solid var(--aies-border);
}
.panel-head b {
  font-size: 15px;
  color: var(--aies-ink);
}
.status {
  font-size: 11.5px;
  color: var(--aies-ink3);
}
.status.on {
  color: #2f9e6e;
}
.message-list {
  flex: 1;
  overflow-y: auto;
  padding: 18px 20px;
  display: flex;
  flex-direction: column;
  gap: 10px;
  background: var(--aies-paper);
}
.load-more {
  align-self: center;
  padding: 5px 14px;
  border: 1px solid var(--aies-border);
  border-radius: 14px;
  background: var(--aies-surface);
  color: var(--aies-ink2);
  font-size: 12px;
  cursor: pointer;
}
.message-row {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 3px;
  max-width: 72%;
}
.message-row.mine {
  align-self: flex-end;
  align-items: flex-end;
}
.bubble {
  padding: 8px 12px;
  border-radius: 10px;
  background: var(--aies-surface);
  border: 1px solid var(--aies-border);
  color: var(--aies-ink);
  font-size: 13.5px;
  line-height: 1.6;
  white-space: pre-wrap;
  word-break: break-word;
}
.message-row.mine .bubble {
  background: var(--aies-brand);
  border-color: var(--aies-brand);
  color: #fff;
}
.message-row time {
  font-size: 10.5px;
  color: var(--aies-ink3);
}
.composer {
  border-top: 1px solid var(--aies-border);
  padding: 10px 16px;
  display: flex;
  gap: 12px;
  align-items: flex-end;
  background: var(--aies-surface);
}
.composer textarea {
  flex: 1;
  resize: none;
  border: 1px solid var(--aies-border);
  border-radius: 8px;
  padding: 8px 10px;
  font: inherit;
  font-size: 13.5px;
  color: var(--aies-ink);
  background: var(--aies-paper);
  outline: none;
}
.composer textarea:focus {
  border-color: var(--aies-brand);
}
.composer-side {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 6px;
}
.composer-side small {
  font-size: 10.5px;
  color: var(--aies-ink3);
}
.composer-side .send-error {
  color: #b91c1c;
}
.composer-side button {
  padding: 7px 18px;
  border: none;
  border-radius: 8px;
  background: var(--aies-brand);
  color: #fff;
  font-size: 13px;
  cursor: pointer;
}
.composer-side button:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}
.empty {
  padding: 20px 16px;
  color: var(--aies-ink3);
  font-size: 12.5px;
  text-align: center;
}
.empty.big {
  margin: auto;
  font-size: 13.5px;
}

@media (max-width: 860px) {
  .contacts {
    width: 210px;
  }
  .online-dot {
    left: 38px;
  }
}
</style>
