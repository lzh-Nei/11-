import axios, { type InternalAxiosRequestConfig } from 'axios'
import { ElMessage } from 'element-plus'
import { useAuthStore } from '@/stores/auth'
import type { LoginResult } from '@/api/auth'
import router from '@/router'

/** 统一响应体形状（02-02 §九：Result{code,message,data}） */
interface ApiBody<T = unknown> {
  code: number
  message: string
  data: T
}

/** 允许续期后重放一次的请求配置 */
interface RetriableConfig extends InternalAxiosRequestConfig {
  _retried?: boolean
}

const request = axios.create({ baseURL: '/api', timeout: 15000 })
const REFRESH_URL = '/auth/refresh'
/** 单飞刷新：并发 401 共用同一次 RT 轮换（RT 一次即废，并发刷新必失败） */
let refreshPromise: Promise<string> | null = null

function isRefreshRequest(config?: InternalAxiosRequestConfig): boolean {
  return (config?.url ?? '').includes(REFRESH_URL)
}

function refreshAccessToken(): Promise<string> {
  const auth = useAuthStore()
  if (!auth.refreshToken) {
    return Promise.reject(new Error('missing refresh token'))
  }
  if (!refreshPromise) {
    refreshPromise = request
      .post(REFRESH_URL, { refreshToken: auth.refreshToken })
      .then((data) => {
        const result = data as unknown as LoginResult
        auth.setTokens(result)
        return result.accessToken
      })
      .finally(() => {
        refreshPromise = null
      })
  }
  return refreshPromise
}

/** 续期失败/无 RT：清会话并回登录页（并发调用只提示一次） */
function forceLogout(message = '登录已过期，请重新登录'): void {
  const auth = useAuthStore()
  const hadSession = Boolean(auth.accessToken || auth.refreshToken)
  auth.clear()
  if (!hadSession) return
  if (router.currentRoute.value.path !== '/login') router.push('/login')
  ElMessage.error(message)
}

/**
 * 401/40101 统一处理：单飞刷新 AT 并重放原请求；刷新失败才登出。
 * 刷新请求自身失败直接抛给发起方，避免递归。
 */
async function handleUnauthorized(
  config: InternalAxiosRequestConfig | undefined,
  message?: string
): Promise<unknown> {
  const auth = useAuthStore()
  if (!config || isRefreshRequest(config) || (config as RetriableConfig)._retried || !auth.refreshToken) {
    forceLogout(message)
    return Promise.reject(new Error(message || '登录已过期，请重新登录'))
  }
  ;(config as RetriableConfig)._retried = true
  try {
    await refreshAccessToken()
  } catch {
    forceLogout(message)
    return Promise.reject(new Error(message || '登录已过期，请重新登录'))
  }
  // 重放原请求：请求拦截器会用新 AT 覆盖 Authorization
  return request(config)
}

// 请求拦截：装配 Bearer AT（S-10）
request.interceptors.request.use((config) => {
  const auth = useAuthStore()
  if (auth.accessToken) {
    config.headers.Authorization = `Bearer ${auth.accessToken}`
  }
  return config
})

// 响应拦截：code===0 解包 data；40101→清态回登录页；40301→权限提示；30010→冷却倒计时
request.interceptors.response.use(
  (resp) => {
    // HTTP 204（登出）等无响应体场景直接放行
    if (resp.status === 204 || resp.data === undefined || resp.data === null) {
      return resp
    }
    const body = resp.data as ApiBody
    // 非 Result 包络（FileController / DownloadController info 等裸 DTO）：原样放行
    if (!body || typeof body !== 'object' || typeof (body as { code?: unknown }).code !== 'number') {
      return body as never
    }
    if (body.code === 0) {
      return body.data as never
    }
    if (body.code === 40101) {
      return handleUnauthorized(resp.config, body.message) as never
    }
    if (body.code === 40301) {
      ElMessage.error(body.message || '无操作权限')
      return Promise.reject(body)
    }
    if (body.code === 30010) {
      // S-12 频控：进入 60s 冷却（后端 message 已含精确文案）
      const auth = useAuthStore()
      auth.startCooldown(60)
      ElMessage.warning(body.message || '操作过于频繁，请稍后再试')
      return Promise.reject(body)
    }
    ElMessage.error(body.message || '请求失败')
    return Promise.reject(body)
  },
  (error) => {
    // 主动取消（上传取消/页面切换）不按网络故障提示，由调用方决定文案
    if (axios.isCancel(error) || (error as { code?: string })?.code === 'ERR_CANCELED') {
      return Promise.reject(error)
    }
    const status = error.response?.status
    const body = error.response?.data as ApiBody | undefined
    if (status === 401) {
      return handleUnauthorized(error.config, body?.message || '未登录或登录已过期')
    } else if (status === 403) {
      ElMessage.error(body?.message || '无操作权限')
    } else {
      ElMessage.error(body?.message || '网络或服务暂时不可用，请稍后重试')
    }
    return Promise.reject(error)
  }
)

/**
 * 二进制直下（302→MinIO 等跨域重定向 axios 拦截器不适用）：
 * fetch 携带 AT 跟随重定向（跨域跳转时浏览器自动剥离 Authorization，签名 URL 本就不需鉴权），
 * 返回响应体 Blob；业务码 JSON（code≠0）与 HTTP 错误统一抛 Error(message)。
 */
export async function fetchBlob(url: string): Promise<Blob> {
  const auth = useAuthStore()
  const resp = await fetch(url, {
    headers: auth.accessToken ? { Authorization: `Bearer ${auth.accessToken}` } : undefined
  })
  if (resp.status === 401 || resp.status === 403) {
    throw new Error('下载失败，请重新登录后再试')
  }
  const contentType = resp.headers.get('content-type') ?? ''
  if (contentType.includes('application/json')) {
    const body = (await resp.json()) as { message?: string }
    throw new Error(body.message || '下载失败')
  }
  return await resp.blob()
}

export default request
