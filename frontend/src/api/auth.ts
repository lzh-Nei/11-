import request from './request'

/** 登录/刷新响应（03-03 A-01 契约形状，D-18 严格契约） */
export interface LoginResult {
  accessToken: string
  refreshToken: string
  role: string
  firstLogin: boolean
}

/** 图形验证码（A-04：base64 已带前缀可直接作 img src） */
export interface Captcha {
  captchaId: string
  image: string
  expireSeconds: number
}

/** /me 用户信息（W6-C2 契约细化：含 permissions 清单） */
export interface MeInfo {
  id: number
  username: string
  realName: string
  phone: string
  email: string
  roles: string[]
  permissions: string[]
}

export interface LoginPayload {
  username: string
  password: string
  captchaId: string
  captchaCode: string
}

/** A-06 邮箱验证码发送响应（mockCode 仅dev模拟模式回显，真实SMTP通道恒为null） */
export interface EmailCodeSendResult {
  username: string
  expireSeconds: number
  mockCode: string | null
}

/** A-06 验证码场景（对应后端 EmailScene 枚举） */
export type EmailCodeScene = 'REGISTER' | 'LOGIN' | 'RESET_PASSWORD'

export interface EmailCodePayload {
  username: string
  email: string
  scene: EmailCodeScene
  captchaId: string
  captchaCode: string
}

/** A-08 注册请求（UC-43：role不入请求体，新用户固定绑定STUDENT） */
export interface RegisterPayload {
  username: string
  password: string
  realName: string
  email: string
  classCode: string
  emailCode: string
  captchaId: string
  captchaCode: string
}

/** A-09 忘记密码请求（UC-44：S-13防枚举，无论命中与否统一文案） */
export interface ForgotPasswordPayload {
  username: string
  email: string
  captchaId: string
  captchaCode: string
}

/** A-10 重置密码请求（UC-44：一次性邮箱码，成功即作废该用户全部RT） */
export interface ResetPasswordPayload {
  username: string
  emailCode: string
  newPassword: string
}

export const authApi = {
  async captcha(): Promise<Captcha> {
    return (await request.get('/auth/captcha')) as unknown as Captcha
  },
  async login(payload: LoginPayload): Promise<LoginResult> {
    return (await request.post('/auth/login', payload)) as unknown as LoginResult
  },
  /** A-07 邮箱验证码登录（UC-42）：签发 AT/RT 同 A-01 */
  async loginByEmail(payload: { username: string; emailCode: string }): Promise<LoginResult> {
    return (await request.post('/auth/login/email', payload)) as unknown as LoginResult
  },
  async me(): Promise<MeInfo> {
    return (await request.get('/auth/me')) as unknown as MeInfo
  },
  async logout(): Promise<void> {
    await request.post('/auth/logout')
  },
  /** A-06 发送邮箱验证码（UC-42/43/44共用；频控30010、SMTP不可用40004由全局拦截器提示） */
  async emailCode(payload: EmailCodePayload): Promise<EmailCodeSendResult> {
    return (await request.post('/auth/email-code', payload)) as unknown as EmailCodeSendResult
  },
  /** A-08 注册（UC-43）：返回UserVO（字段形状同MeInfo，刻意不含password） */
  async register(payload: RegisterPayload): Promise<MeInfo> {
    return (await request.post('/auth/register', payload)) as unknown as MeInfo
  },
  /** A-09 忘记密码（UC-44）：后端无论命中与否code=0，统一文案由页面侧提示 */
  async forgotPassword(payload: ForgotPasswordPayload): Promise<void> {
    await request.post('/auth/password/forgot', payload)
  },
  /** A-10 重置密码（UC-44）：成功即作废该用户全部RT */
  async resetPassword(payload: ResetPasswordPayload): Promise<void> {
    await request.post('/auth/password/reset', payload)
  }
}
