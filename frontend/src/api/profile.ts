import request from './request'
import type { MeInfo } from './auth'

/* 个人资料自助更新（ProfileController 真身；仅姓名/手机号可改） */

export interface UpdateProfilePayload {
  realName: string
  phone: string
}

export const profileApi = {
  /** PUT /api/profile：更新姓名/手机号，返回最新 UserVO */
  async update(payload: UpdateProfilePayload): Promise<MeInfo> {
    return (await request.put('/profile', payload)) as unknown as MeInfo
  }
}
