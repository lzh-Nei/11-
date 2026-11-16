import { defineStore } from 'pinia'
import { authApi, type LoginResult, type MeInfo } from '@/api/auth'

const AT_KEY = 'aies_at'
const RT_KEY = 'aies_rt'

/** 登录闭环的客户端状态：双token + /me 装载的用户/角色/权限 + 30010 冷却 */
export const useAuthStore = defineStore('auth', {
  state: () => ({
    accessToken: localStorage.getItem(AT_KEY) || '',
    refreshToken: localStorage.getItem(RT_KEY) || '',
    me: null as MeInfo | null,
    firstLogin: false,
    cooldownUntil: 0
  }),
  getters: {
    role: (s) => s.me?.roles?.[0] ?? '',
    cooldownSeconds: (s) => Math.max(0, Math.ceil((s.cooldownUntil - Date.now()) / 1000))
  },
  actions: {
    setTokens(result: LoginResult) {
      this.accessToken = result.accessToken
      this.refreshToken = result.refreshToken
      this.firstLogin = result.firstLogin
      localStorage.setItem(AT_KEY, result.accessToken)
      localStorage.setItem(RT_KEY, result.refreshToken)
    },
    async loadMe() {
      this.me = await authApi.me()
    },
    startCooldown(seconds: number) {
      this.cooldownUntil = Date.now() + seconds * 1000
    },
    async logout() {
      try {
        await authApi.logout()
      } finally {
        this.clear()
      }
    },
    clear() {
      this.accessToken = ''
      this.refreshToken = ''
      this.me = null
      this.firstLogin = false
      localStorage.removeItem(AT_KEY)
      localStorage.removeItem(RT_KEY)
    }
  }
})
