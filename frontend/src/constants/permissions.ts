/* ══════════ 权限点中文映射（WP8-B1 格式化铁律：禁裸英文权限点）
   真身来源：V1__rbac.sql sys_permission 全量 22 项（perm_name 直取）
   + V2/V3/V14 增补项（task:mine/submission:mine/report:view:teacher/report:refresh）。
   未知权限点兜底原值灰标签，不臆造中文。 */

export const PERM_MAP: Record<string, string> = {
  'auth:logout': '退出登录',
  'user:me': '查看本人信息',
  'user:manage': '用户管理',
  'task:create': '创建任务',
  'task:publish': '发布任务',
  'task:edit': '编辑任务',
  'task:view': '查看任务',
  'task:mine': '我的任务',
  'submission:upload': '上传提交',
  'submission:view': '查看提交',
  'submission:mine': '我的提交',
  'evaluation:view': '查看评价',
  'evaluation:review': '复核评价',
  'evaluation:re-evaluate': '触发重评',
  'evaluation:history:view': '查看评价历史',
  'report:view': '查看报表',
  'report:view:student': '查看本人报表',
  'report:export': '导出报表',
  'report:refresh': '刷新报表缓存',
  'report:view:teacher': '教师聚合报表',
  'role:manage': '角色管理',
  'system:config': '系统参数配置',
  'class:manage': '班级管理',
  'audit:view': '审计日志查询',
  'llm:config': 'LLM 参数配置',
  'prompt:manage': 'Prompt 模板管理',
  'golden:manage': '黄金数据集管理'
}

/** 命名空间中文名（权限卡分组用） */
export const NS_MAP: Record<string, string> = {
  auth: '认证',
  user: '用户',
  task: '任务',
  submission: '提交',
  evaluation: '评价',
  report: '报表',
  class: '班级',
  system: '系统'
}

export function permLabel(code: string): string {
  return PERM_MAP[code] ?? code
}

export function nsLabel(ns: string): string {
  return NS_MAP[ns] ?? ns
}

/* ══════════ 权限点 → 功能入口映射（WP8-B4 权限即导航）
   kind: route=直达路由 / action=执行动作 / reserved=预留权限（端点未开放，如实告知） */
export type PermEntry =
  | { kind: 'route'; to: string; hint: string }
  | { kind: 'action'; act: 'logout' | 'profile' | 'dashboard'; hint: string }
  | { kind: 'reserved'; hint: string }

export const PERM_ROUTE: Record<string, PermEntry> = {
  'auth:logout': { kind: 'action', act: 'logout', hint: '退出登录' },
  'user:me': { kind: 'action', act: 'profile', hint: '定位到个人信息卡' },
  'task:create': { kind: 'route', to: '/tasks/manage', hint: '进入任务管理模块创建' },
  'task:publish': { kind: 'route', to: '/tasks/manage', hint: '进入任务管理模块发布草稿' },
  'task:edit': { kind: 'reserved', hint: '编辑端点为 V1 预留（T-02 未开放），M3 管理端规划' },
  'task:view': { kind: 'route', to: '/tasks', hint: '进入任务模块（学生任务中心 / 教师任务管理）' },
  'task:mine': { kind: 'route', to: '/tasks', hint: '进入任务中心' },
  'submission:upload': { kind: 'route', to: '/tasks', hint: '在任务中心选择任务提交作业' },
  'submission:view': {
    kind: 'route',
    to: '/submissions',
    hint: '进入提交模块（学生提交档案 / 教师评价复核）'
  },
  'submission:mine': { kind: 'route', to: '/submissions', hint: '进入提交档案模块' },
  'evaluation:view': { kind: 'route', to: '/my-evaluations', hint: '进入我的评价模块（报告新窗口打开）' },
  'evaluation:review': { kind: 'route', to: '/review', hint: '进入评价复核模块' },
  'evaluation:re-evaluate': { kind: 'route', to: '/review', hint: '在评价复核工作台内操作重评' },
  'evaluation:history:view': {
    kind: 'route',
    to: '/review',
    hint: '版本链在评价复核工作台内查看'
  },
  'report:view': { kind: 'action', act: 'dashboard', hint: '打开报表驾驶舱（新窗口）' },
  'report:view:student': {
    kind: 'route',
    to: '/',
    hint: '工作台统计卡展示本人报表数据（提交/出分/待批阅）'
  },
  'report:view:teacher': { kind: 'action', act: 'dashboard', hint: '打开教师聚合驾驶舱（新窗口）' },
  'report:refresh': { kind: 'action', act: 'dashboard', hint: '工作台页头「刷新报表缓存」按钮' },
  'report:export': { kind: 'reserved', hint: '导出端点预留，M3 报表导出规划' },
  'user:manage': { kind: 'reserved', hint: '用户管理端点为 M3 管理端规划' },
  'class:manage': { kind: 'reserved', hint: '班级管理端点预留，M3 规划' },
  'role:manage': { kind: 'reserved', hint: '管理端预留，M3 规划' },
  'system:config': { kind: 'reserved', hint: '管理端预留，M3 规划' },
  'audit:view': { kind: 'reserved', hint: '审计查询预留，M3 规划' },
  'llm:config': { kind: 'reserved', hint: 'LLM 配置预留，M3 规划' },
  'prompt:manage': { kind: 'reserved', hint: 'Prompt 管理预留，M3 规划' },
  'golden:manage': { kind: 'reserved', hint: '黄金集管理预留，M3 规划' }
}

export function permEntry(code: string): PermEntry | null {
  return PERM_ROUTE[code] ?? null
}
