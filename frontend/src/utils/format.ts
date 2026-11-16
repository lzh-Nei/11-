/* ══════════ 全站格式化工具（WP8-B0 格式化死刑条款配套）
   铁律：任何视图禁止直接渲染 ISO 串 / 枚举原文 / 裸字节数 / 裸 uuid。
   全部显示层一律走本文件（状态枚举另见 constants/status.ts） */

/** '2026-11-16T12:03:44Z' → '2026-11-16 12:03'（非法输入原样返回，不臆造） */
export function formatDateTime(iso: string | null | undefined): string {
  if (!iso) return '—'
  const d = new Date(iso)
  if (isNaN(d.getTime())) return String(iso)
  const p = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())} ${p(d.getHours())}:${p(d.getMinutes())}`
}

/** 相对时间（列表态用）：3 小时前 / 2 天前；>7 天回落绝对日期 */
export function formatRelative(iso: string | null | undefined): string {
  if (!iso) return '—'
  const t = new Date(iso).getTime()
  if (isNaN(t)) return String(iso)
  const diff = Date.now() - t
  const m = Math.floor(diff / 60000)
  if (m < 1) return '刚刚'
  if (m < 60) return `${m} 分钟前`
  const h = Math.floor(m / 60)
  if (h < 24) return `${h} 小时前`
  const day = Math.floor(h / 24)
  if (day < 7) return `${day} 天前`
  return formatDateTime(iso)
}

/** 字节数 → '1.0 MB' / '832 KB' / '12 B' */
export function formatBytes(bytes: number | null | undefined): string {
  if (bytes == null || isNaN(bytes)) return '—'
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`
  if (bytes < 1024 * 1024 * 1024) return `${(bytes / 1024 / 1024).toFixed(1)} MB`
  return `${(bytes / 1024 / 1024 / 1024).toFixed(2)} GB`
}

/** uuid / trace_id 默认短形态（详情折叠 + 一键复制由调用方组件处理） */
export function maskId(id: string | null | undefined, head = 6, tail = 4): string {
  if (!id) return '—'
  const s = String(id)
  if (s.length <= head + tail + 1) return s
  return `${s.slice(0, head)}…${s.slice(-tail)}`
}

/** 分数等级：≥85 优 / ≥70 良 / ≥60 及格 / <60 待提升（色彩等级供 ScoreDisplay） */
export type ScoreLevel = 'excellent' | 'good' | 'pass' | 'low'
export function scoreLevel(score: number | null | undefined): ScoreLevel | null {
  if (score == null || isNaN(Number(score))) return null
  const s = Number(score)
  if (s >= 85) return 'excellent'
  if (s >= 70) return 'good'
  if (s >= 60) return 'pass'
  return 'low'
}

export const SCORE_LEVEL_LABEL: Record<ScoreLevel, string> = {
  excellent: '优',
  good: '良',
  pass: '及格',
  low: '待提升'
}
