import request from './request'
import type { PageResult } from './task'

/* M3 管理端接口（AdminController 真身；权限点复用 V1 system 段） */

export interface AdminUser {
  id: number
  username: string
  realName: string
  phone: string | null
  email: string | null
  classId: number | null
  className: string | null
  status: number
  firstLogin: number
  roleCodes: string[]
  createTime: string
}

export interface AdminTeacher {
  id: number
  username: string
  realName: string
}

export interface AdminRole {
  id: number
  roleCode: string
  roleName: string
  builtIn: number
  userCount: number
  permCodes: string[]
}

export interface AdminPermission {
  id: number
  permCode: string
  permName: string
  module: string
}

export interface AdminClass {
  id: number
  className: string
  classCode: string
  teacherId: number
  teacherName: string | null
  studentCount: number
  taskCount: number
  createTime: string
}

export interface AdminAuditLog {
  id: number
  operatorId: number
  operatorName: string | null
  module: string
  action: string
  target: string | null
  detail: string | null
  ip: string | null
  createTime: string
}

export interface AdminSystemConfig {
  counts: {
    users: number
    classes: number
    tasks: number
    submissions: number
    evaluations: number
  }
  smtpConfigured: boolean
  smtpHost: string | null
  captchaExpireSeconds: number
  captchaCodeCount: number
  emailExpireSeconds: number
  emailCooldownSeconds: number
  llmModel: string | null
  activePromptVersion: number | null
  goldenActiveCount: number
}

export interface AdminLlmConfig {
  id: number
  modelName: string
  temperature: number
  timeoutSeconds: number
  maxRetry: number
  updateTime: string
}

export interface AdminPrompt {
  id: number
  versionNo: number
  enabled: number
  contentLength: number
  contentPreview: string
  createTime: string
}

export interface AdminPromptDetail {
  id: number
  versionNo: number
  enabled: number
  content: string
  createTime: string
}

export interface AdminGoldenSample {
  id: number
  sampleFileKey: string
  teacherTotal: number
  teacherMetrics: Record<string, number> | null
  active: number
  lastConsistency: number | null
}

export interface AdminClassPayload {
  className: string
  classCode: string
  teacherId: number
}

export const adminApi = {
  /* 用户管理 */
  async users(params: {
    page: number
    size: number
    keyword?: string
    status?: number
    roleCode?: string
  }): Promise<PageResult<AdminUser>> {
    return (await request.get('/admin/users', { params })) as unknown as PageResult<AdminUser>
  },
  async updateUserStatus(id: number, status: number): Promise<void> {
    await request.put(`/admin/users/${id}/status`, { status })
  },
  async updateUserRoles(id: number, roleCodes: string[]): Promise<void> {
    await request.put(`/admin/users/${id}/roles`, { roleCodes })
  },
  async teachers(): Promise<AdminTeacher[]> {
    return (await request.get('/admin/teachers')) as unknown as AdminTeacher[]
  },

  /* 角色权限 */
  async roles(): Promise<AdminRole[]> {
    return (await request.get('/admin/roles')) as unknown as AdminRole[]
  },
  async permissions(): Promise<AdminPermission[]> {
    return (await request.get('/admin/permissions')) as unknown as AdminPermission[]
  },
  async updateRolePermissions(id: number, permCodes: string[]): Promise<void> {
    await request.put(`/admin/roles/${id}/permissions`, { permCodes })
  },

  /* 班级管理 */
  async classes(page: number, size: number): Promise<PageResult<AdminClass>> {
    return (await request.get('/admin/classes', {
      params: { page, size }
    })) as unknown as PageResult<AdminClass>
  },
  async createClass(payload: AdminClassPayload): Promise<AdminClass> {
    return (await request.post('/admin/classes', payload)) as unknown as AdminClass
  },
  async updateClass(id: number, payload: { className: string; teacherId: number }): Promise<AdminClass> {
    return (await request.put(`/admin/classes/${id}`, payload)) as unknown as AdminClass
  },
  async deleteClass(id: number): Promise<void> {
    await request.delete(`/admin/classes/${id}`)
  },

  /* 审计日志 */
  async auditLogs(params: {
    page: number
    size: number
    module?: string
    action?: string
  }): Promise<PageResult<AdminAuditLog>> {
    return (await request.get('/admin/audit-logs', { params })) as unknown as PageResult<AdminAuditLog>
  },

  /* 系统配置（只读快照） */
  async systemConfig(): Promise<AdminSystemConfig> {
    return (await request.get('/admin/system/config')) as unknown as AdminSystemConfig
  },

  /* LLM 参数 */
  async llmConfigs(): Promise<AdminLlmConfig[]> {
    return (await request.get('/admin/llm-configs')) as unknown as AdminLlmConfig[]
  },
  async updateLlmConfig(
    id: number,
    payload: { modelName: string; timeoutSeconds: number; maxRetry: number }
  ): Promise<void> {
    await request.put(`/admin/llm-configs/${id}`, payload)
  },

  /* Prompt 模板 */
  async prompts(): Promise<AdminPrompt[]> {
    return (await request.get('/admin/prompts')) as unknown as AdminPrompt[]
  },
  async promptDetail(id: number): Promise<AdminPromptDetail> {
    return (await request.get(`/admin/prompts/${id}`)) as unknown as AdminPromptDetail
  },
  async createPrompt(content: string): Promise<AdminPromptDetail> {
    return (await request.post('/admin/prompts', { content })) as unknown as AdminPromptDetail
  },
  async enablePrompt(id: number): Promise<void> {
    await request.put(`/admin/prompts/${id}/enable`)
  },

  /* 黄金数据集 */
  async goldenSamples(page: number, size: number): Promise<PageResult<AdminGoldenSample>> {
    return (await request.get('/admin/golden-samples', {
      params: { page, size }
    })) as unknown as PageResult<AdminGoldenSample>
  },
  async setGoldenActive(id: number, active: number): Promise<void> {
    await request.put(`/admin/golden-samples/${id}/active`, { active })
  }
}
