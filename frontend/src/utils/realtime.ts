import { ref } from 'vue'
import { getSessionTokens } from '@/utils/authSession'

/** WebSocket 实时事件（当前仅评价状态；保留 type 供后续任务/公告扩展） */
export interface RealtimeEvent {
  type: string
  data?: unknown
  ts?: number
}

export const realtimeConnected = ref(false)

type Listener = (event: RealtimeEvent) => void
const listeners = new Set<Listener>()

let socket: WebSocket | null = null
let heartbeatTimer: number | undefined
let reconnectTimer: number | undefined
let reconnectAttempts = 0
let lastPongAt = 0
let stopped = true

/** 订阅实时事件，返回取消订阅函数 */
export function onRealtimeEvent(handler: Listener): () => void {
  listeners.add(handler)
  return () => listeners.delete(handler)
}

export function startRealtime(): void {
  stopRealtime()
  const { accessToken } = getSessionTokens()
  if (!accessToken) {
    return
  }
  stopped = false
  reconnectAttempts = 0
  connect()
}

export function stopRealtime(): void {
  stopped = true
  realtimeConnected.value = false
  stopHeartbeat()
  if (reconnectTimer !== undefined) {
    window.clearTimeout(reconnectTimer)
    reconnectTimer = undefined
  }
  if (socket) {
    socket.onclose = null
    socket.close()
    socket = null
  }
}

function connect(): void {
  if (stopped) return
  const { accessToken } = getSessionTokens()
  if (!accessToken) {
    return
  }
  const protocol = window.location.protocol === 'https:' ? 'wss' : 'ws'
  const url = `${protocol}://${window.location.host}/ws?token=${encodeURIComponent(accessToken)}`
  const ws = new WebSocket(url)
  socket = ws

  ws.onopen = () => {
    if (socket !== ws) return
    realtimeConnected.value = true
    reconnectAttempts = 0
    lastPongAt = Date.now()
    startHeartbeat()
  }
  ws.onmessage = (message) => {
    if (socket !== ws) return
    try {
      const event = JSON.parse(message.data) as RealtimeEvent
      if (event.type === 'pong') {
        lastPongAt = Date.now()
        return
      }
      if (event.type === 'connected') return
      listeners.forEach((listener) => {
        try {
          listener(event)
        } catch {
          // 单个订阅者异常不影响其他页面
        }
      })
    } catch {
      // 非 JSON 服务端消息忽略
    }
  }
  ws.onerror = () => {
    if (socket === ws) ws.close()
  }
  ws.onclose = () => {
    if (socket !== ws) return
    socket = null
    realtimeConnected.value = false
    stopHeartbeat()
    scheduleReconnect()
  }
}

function scheduleReconnect(): void {
  if (stopped) return
  const delay = Math.min(30000, 1000 * 2 ** Math.min(reconnectAttempts, 5))
  reconnectAttempts += 1
  reconnectTimer = window.setTimeout(connect, delay)
}

function startHeartbeat(): void {
  stopHeartbeat()
  heartbeatTimer = window.setInterval(() => {
    if (socket?.readyState !== WebSocket.OPEN) return
    if (Date.now() - lastPongAt > 45000) {
      socket.close()
      return
    }
    socket.send(JSON.stringify({ type: 'ping' }))
  }, 30000)
}

function stopHeartbeat(): void {
  if (heartbeatTimer !== undefined) {
    window.clearInterval(heartbeatTimer)
    heartbeatTimer = undefined
  }
}
