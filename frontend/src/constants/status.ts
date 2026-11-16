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
  PENDING: { label: '待批阅', tone: 'warn' },
  AI_DONE: { label: '已出分', tone: 'teal' },
  REVIEWED: { label: '已复核', tone: 'ok' },
  FAILED: { label: '失败', tone: 'err' },
  /* 解析状态机（V3：PENDING/QUEUED/SUCCESS/FAILED） */
  QUEUED: { label: '排队中', tone: 'muted' },
  GENERATING: { label: '解析中', tone: 'warn' },
  SUCCESS: { label: '成功', tone: 'ok' },
  /* 补写（V6：PENDING/DONE） */
  DONE: { label: '完成', tone: 'ok' }
}

/** 未知枚举兜底：原值展示 + muted 灰（不臆造中文翻译） */
export function statusMeta(raw: string | null | undefined): StatusMeta {
  if (!raw) return { label: '—', tone: 'muted' }
  return STATUS_MAP[String(raw)] ?? { label: String(raw), tone: 'muted' }
}
