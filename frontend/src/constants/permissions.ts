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
