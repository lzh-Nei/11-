/* 系统探活（HealthController / nginx 探针双口径）：部署探针与登录页存活检测用。
   注意：nginx 将 /api/health 反代到 actuator（{"status":"UP"}），vite dev 直达后端时是
   Result{code:0,data:"ok"}——两条链路响应体不同，故这里不走 axios 拦截器，只判 HTTP 与关键字。 */

export const systemApi = {
  /** 探活：任何 2xx 且响应可解析视为正常（4s 超时，失败不抛错） */
  async health(): Promise<boolean> {
    const controller = new AbortController()
    const timer = window.setTimeout(() => controller.abort(), 4000)
    try {
      const resp = await fetch('/api/health', { cache: 'no-store', signal: controller.signal })
      if (!resp.ok) return false
      const type = resp.headers.get('content-type') ?? ''
      if (type.includes('application/json')) {
        const body = (await resp.json()) as { status?: string; code?: number }
        return body.status === 'UP' || body.code === 0
      }
      return true
    } catch {
      return false
    } finally {
      window.clearTimeout(timer)
    }
  }
}
