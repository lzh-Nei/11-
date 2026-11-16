/* 管理端展示字典（角色/权限模块中文名，禁裸英文枚举） */

export const ROLE_LABEL: Record<string, string> = {
  STUDENT: '学生',
  TEACHER: '教师',
  ADMIN: '管理员'
}

export const PERM_MODULE_LABEL: Record<string, string> = {
  auth: '认证',
  user: '用户',
  task: '任务',
  submission: '提交',
  evaluation: '评价',
  report: '报表',
  system: '系统'
}

export const AUDIT_MODULES = ['auth', 'user', 'task', 'submission', 'evaluation', 'report', 'system']

export function roleLabel(code: string | null | undefined): string {
  if (!code) return '—'
  return ROLE_LABEL[code] ?? code
}

export function permModuleLabel(code: string): string {
  return PERM_MODULE_LABEL[code] ?? code
}
