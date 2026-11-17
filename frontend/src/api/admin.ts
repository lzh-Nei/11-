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

/** 管理端评价列表行（管理员查看教师评价/复核历史入口） */
export interface AdminEvaluation {
  id: number
  submissionId: number
  taskId: number | null
  taskTitle: string | null
  studentName: string | null
  teacherName: string | null
  status: string
  totalScore: number | null
  currentVersionNo: number | null
  updateTime: string
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

/** 消息队列监控（管理端只读）：broker 水位 + 数据库侧流水 */
export interface AdminMqQueue {
  key: 'parse' | 'eval'
  label: string
  queueName: string
  exchange: string
  routingKey: string
  available: boolean
  readyMessages: number | null
  consumers: number | null
  error: string | null
}

export interface AdminMqStats {
  parseQueued: number
  parseSuccess: number
  parseFailed: number
  evaluationPending: number
  evaluationAiDone: number
  evaluationReviewed: number
  evaluationFailed: number
}

export interface AdminMqEvent {
  submissionId: number
  fileName: string | null
  studentName: string | null
  taskTitle: string | null
  parseStatus: string | null
  evaluationStatus: string | null
  totalScore: number | null
  aiModel: string | null
  updateTime: string | null
}

export interface AdminMqMonitor {
  rabbitConnected: boolean
  broker: string
  sampledAt: string
  queues: AdminMqQueue[]
  stats: AdminMqStats
  recentEvents: AdminMqEvent[]
}

export interface AdminLlmConfig {
  id: number
  provider: string
  apiBaseUrl: string | null
  modelName: string
  temperature: number
  timeoutSeconds: number
  maxRetry: number
  apiKeyConfigured: boolean
  apiKeyHint: string | null
  updateTime: string
}

export interface LlmTestResult {
  success: boolean
  message: string
  modelVersion: string | null
  elapsedMs: number
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

/** 管理员新建用户（roleCodes 仅 STUDENT/TEACHER；初始强制改密） */
export interface AdminUserCreatePayload {
  username: string
  realName: string
  password: string
  email: string
  phone: string
  classId: number | null
  roleCodes: string[]
}

/** 管理员编辑用户基础资料（用户名/角色/状态不走此载荷） */
export interface AdminUserUpdatePayload {
  realName: string
  email: string
  phone: string
  classId: number | null
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
  async createUser(payload: AdminUserCreatePayload): Promise<AdminUser> {
    return (await request.post('/admin/users', payload)) as unknown as AdminUser
  },
  async updateUser(id: number, payload: AdminUserUpdatePayload): Promise<AdminUser> {
    return (await request.put(`/admin/users/${id}`, payload)) as unknown as AdminUser
  },
  async deleteUser(id: number): Promise<void> {
    await request.delete(`/admin/users/${id}`)
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

  /* 消息队列监控（只读） */
  async mqStatus(): Promise<AdminMqMonitor> {
    return (await request.get('/admin/mq/status')) as unknown as AdminMqMonitor
  },

  /* 管理端评价列表（管理员查看教师评价/复核历史） */
  async evaluations(params: {
    page: number
    size: number
    status?: string
  }): Promise<PageResult<AdminEvaluation>> {
    return (await request.get('/admin/evaluations', { params })) as unknown as PageResult<AdminEvaluation>
  },

  /* LLM 参数 */
  async llmConfigs(): Promise<AdminLlmConfig[]> {
    return (await request.get('/admin/llm-configs')) as unknown as AdminLlmConfig[]
  },
  async updateLlmConfig(
    id: number,
    payload: {
      provider: string
      apiBaseUrl?: string
      apiKey?: string
      modelName: string
      timeoutSeconds: number
      maxRetry: number
    }
  ): Promise<void> {
    await request.put(`/admin/llm-configs/${id}`, payload)
  },
  async testLlmConfig(id: number): Promise<LlmTestResult> {
    return (await request.post(`/admin/llm-configs/${id}/test`)) as unknown as LlmTestResult
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
