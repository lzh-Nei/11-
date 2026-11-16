import axios from 'axios'
import { ElMessage } from 'element-plus'
import { useAuthStore } from '@/stores/auth'
import router from '@/router'

/** 统一响应体形状（02-02 §九：Result{code,message,data}） */
interface ApiBody<T = unknown> {
  code: number
  message: string
  data: T
}

const request = axios.create({ baseURL: '/api', timeout: 15000 })

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
    if (body.code === 0) {
      return body.data as never
    }
    if (body.code === 40101) {
      const auth = useAuthStore()
      auth.clear()
      router.push('/login')
      ElMessage.error(body.message || '登录已过期，请重新登录')
      return Promise.reject(body)
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
    const status = error.response?.status
    const body = error.response?.data as ApiBody | undefined
    if (status === 401) {
      const auth = useAuthStore()
      auth.clear()
      router.push('/login')
      ElMessage.error(body?.message || '未登录或登录已过期')
    } else if (status === 403) {
      ElMessage.error(body?.message || '无操作权限')
    } else {
      ElMessage.error(body?.message || `请求异常（${status ?? '网络错误'}）`)
    }
    return Promise.reject(error)
  }
)

export default request
