import { defineStore } from 'pinia'
import { authApi, type LoginResult, type MeInfo } from '@/api/auth'
import {
  clearLegacyTokens,
  clearSessionTokens,
  getSessionTokens,
  writeSessionTokens
} from '@/utils/authSession'

/** 登录闭环的客户端状态：双token + /me 装载的用户/角色/权限 + 30010 冷却
 *  token 存 sessionStorage：每个标签页一个独立会话，多用户同时登录互不覆盖。 */
export const useAuthStore = defineStore('auth', {
  state: () => {
    const { accessToken, refreshToken } = getSessionTokens()
    return {
      accessToken,
      refreshToken,
      me: null as MeInfo | null,
      firstLogin: false,
      cooldownUntil: 0
    }
  },
  getters: {
    role: (s) => s.me?.roles?.[0] ?? '',
    cooldownSeconds: (s) => Math.max(0, Math.ceil((s.cooldownUntil - Date.now()) / 1000))
  },
  actions: {
    setTokens(result: LoginResult) {
      this.accessToken = result.accessToken
      this.refreshToken = result.refreshToken
      this.firstLogin = result.firstLogin
      writeSessionTokens(result.accessToken, result.refreshToken)
    },
    async loadMe() {
      this.me = await authApi.me()
    },
    /** 自助资料更新后同步用户态（侧栏/首页姓名即时刷新） */
    setMe(me: MeInfo | null) {
      this.me = me
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
      clearSessionTokens()
      // 兼容旧版本遗留的全局 token
      clearLegacyTokens()
    }
  }
})
