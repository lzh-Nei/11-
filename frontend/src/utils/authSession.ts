const AT_KEY = 'aies_at'
const RT_KEY = 'aies_rt'
const HANDOFF_PARAM = 'handoff'
const CHANNEL_NAME = 'aies-auth-handoff'
const HANDOFF_TIMEOUT_MS = 1500

/**
 * 登录态存储：sessionStorage 是每个标签页独立的。
 * 同一浏览器同时登录多个用户时，各标签页互不覆盖；直接新开 URL 的标签页没有会话，
 * 会被路由守卫送回登录页。
 */
export interface SessionTokens {
  accessToken: string
  refreshToken: string
}

/** 当前标签页会话 token */
export function getSessionTokens(): SessionTokens {
  return {
    accessToken: sessionStorage.getItem(AT_KEY) || '',
    refreshToken: sessionStorage.getItem(RT_KEY) || ''
  }
}

export function writeSessionTokens(accessToken: string, refreshToken: string): void {
  sessionStorage.setItem(AT_KEY, accessToken)
  sessionStorage.setItem(RT_KEY, refreshToken)
}

export function clearSessionTokens(): void {
  sessionStorage.removeItem(AT_KEY)
  sessionStorage.removeItem(RT_KEY)
}

/** 清理旧版本 localStorage 中的全局 token，避免遗留登录态被新标签页复用 */
export function clearLegacyTokens(): void {
  try {
    localStorage.removeItem(AT_KEY)
    localStorage.removeItem(RT_KEY)
  } catch {
    // 隐私模式/禁用 localStorage 时忽略
  }
}

function newNonce(): string {
  if (typeof crypto !== 'undefined' && 'randomUUID' in crypto) {
    return crypto.randomUUID()
  }
  return Math.random().toString(36).slice(2) + Date.now().toString(36)
}

/* ==================== 应用内新窗口交接（BroadcastChannel） ====================
 * 新窗口只携带一次随机 nonce（URL query），不携带 token。
 * 原标签页收到带 nonce 的请求后，仅在 nonce 属于自己刚发起的交接时回传 token；
 * 其他用户/标签页没有该 nonce，不会响应。直接新开 URL 没有 nonce，无法获得会话。 */

const pendingHandoffs = new Set<string>()
let responderStarted = false

/** 原标签页启动一次响应器：只回应自己刚打开的新窗口 */
export function startAuthHandoffResponder(): void {
  if (responderStarted || typeof BroadcastChannel === 'undefined') {
    return
  }
  responderStarted = true
  const channel = new BroadcastChannel(CHANNEL_NAME)
  channel.onmessage = (event: MessageEvent) => {
    const data = event.data as { type?: string; nonce?: string } | null
    if (data?.type !== 'aies-auth-request' || !data.nonce || !pendingHandoffs.has(data.nonce)) {
      return
    }
    pendingHandoffs.delete(data.nonce)
    const { accessToken, refreshToken } = getSessionTokens()
    if (accessToken && refreshToken) {
      channel.postMessage({
        type: 'aies-auth-response',
        nonce: data.nonce,
        accessToken,
        refreshToken
      })
    }
  }
}

/** 生成带交接 nonce 的新窗口地址；当前标签页无登录态时不带 nonce */
export function handoffUrl(path: string): string {
  const { accessToken, refreshToken } = getSessionTokens()
  if (!accessToken || !refreshToken) {
    return path
  }
  const nonce = newNonce()
  pendingHandoffs.add(nonce)
  // 防止窗口被拦截时 nonce 永久残留
  window.setTimeout(() => pendingHandoffs.delete(nonce), HANDOFF_TIMEOUT_MS * 4)
  const url = new URL(path, window.location.origin)
  url.searchParams.set(HANDOFF_PARAM, nonce)
  return url.pathname + url.search + url.hash
}

/**
 * 新标签页启动时消费 URL 中的 nonce：向原标签页请求登录态并写入当前 sessionStorage。
 * 等待超时/原标签页已关闭则保持未登录，由路由守卫回登录页。
 */
export async function receiveAuthHandoff(): Promise<void> {
  if (typeof BroadcastChannel === 'undefined') {
    return
  }
  const url = new URL(window.location.href)
  const nonce = url.searchParams.get(HANDOFF_PARAM)
  if (!nonce) {
    return
  }
  url.searchParams.delete(HANDOFF_PARAM)
  window.history.replaceState(window.history.state, '', url.pathname + url.search + url.hash)

  await new Promise<void>((resolve) => {
    const channel = new BroadcastChannel(CHANNEL_NAME)
    let settled = false
    const finish = () => {
      if (settled) return
      settled = true
      window.clearTimeout(timer)
      channel.close()
      resolve()
    }
    const timer = window.setTimeout(finish, HANDOFF_TIMEOUT_MS)
    channel.onmessage = (event: MessageEvent) => {
      const data = event.data as {
        type?: string
        nonce?: string
        accessToken?: string
        refreshToken?: string
      } | null
      if (data?.type !== 'aies-auth-response' || data.nonce !== nonce) {
        return
      }
      if (data.accessToken && data.refreshToken) {
        writeSessionTokens(data.accessToken, data.refreshToken)
      }
      finish()
    }
    channel.postMessage({ type: 'aies-auth-request', nonce })
  })
}
