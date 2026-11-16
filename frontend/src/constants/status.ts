/* ══════════ 全站状态色统一映射表（WP8-B0）
   真身来源：Flyway 迁移注释字典（V2 任务三态 / V4 评价状态机四态 + 生成 trace 四态 /
   V3 解析四态 / V6 补写两态）——出现新枚举先在此登记再使用，禁裸枚举渲染。
   tone：ok=绿 / warn=琥珀 / err=红 / teal=青 / muted=灰（色彩经 StatusTag 统一出） */

export type StatusTone = 'ok' | 'warn' | 'err' | 'teal' | 'muted'

export interface StatusMeta {
  label: string
  tone: StatusTone
}

export const STATUS_MAP: Record<string, StatusMeta> = {
  /* 任务状态（V2：DRAFT/PUBLISHED/CLOSED） */
  DRAFT: { label: '草稿', tone: 'muted' },
  PUBLISHED: { label: '已发布', tone: 'teal' },
  CLOSED: { label: '已关闭', tone: 'muted' },
  /* 评价状态机（V4 §7.1：PENDING/AI_DONE/REVIEWED/FAILED） */
  PENDING: { label: '待处理', tone: 'warn' },
  AI_DONE: { label: '已出分', tone: 'teal' },
  REVIEWED: { label: '已复核', tone: 'ok' },
  FAILED: { label: '失败', tone: 'err' },
  /* 解析状态机（V3：PENDING/QUEUED/SUCCESS/FAILED） */
  QUEUED: { label: '排队中', tone: 'muted' },
  GENERATING: { label: '处理中', tone: 'warn' },
  SUCCESS: { label: '成功', tone: 'ok' },
  /* 补写（V6：PENDING/DONE） */
  DONE: { label: '完成', tone: 'ok' },
  /* 评价历史来源（EvaluationHistory.source：AI_INITIAL/TEACHER_REVIEW/RE_EVALUATE） */
  AI_INITIAL: { label: 'AI 初评', tone: 'teal' },
  TEACHER_REVIEW: { label: '教师复核', tone: 'ok' },
  RE_EVALUATE: { label: '重评', tone: 'warn' }
}

/** 未知状态兜底：不向使用人员暴露内部枚举值 */
export function statusMeta(raw: string | null | undefined): StatusMeta {
  if (!raw) return { label: '—', tone: 'muted' }
  return STATUS_MAP[String(raw)] ?? { label: '未知状态', tone: 'muted' }
}
