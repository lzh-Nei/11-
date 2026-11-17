import request from './request'

/** 评价当前值（E-01 / UC-21：traceId 仅教师视角返回，学生视角字段缺省） */
export interface EvaluationVO {
  id: number
  submissionId: number
  taskId: number | null
  status: 'PENDING' | 'AI_DONE' | 'REVIEWED' | 'FAILED'
  currentVersionNo: number
  totalScore: number | null
  metricScores: Record<string, number> | null
  aiComment: string | null
  aiModel?: string | null
  reviewerName?: string | null
  appealStatus?: 'PENDING' | 'RESOLVED' | null
  errorCode: number | null
  updateTime: string
  traceId?: string | null
}

/** E-04 历史版本行（UC-25：倒序游标分页） */
/** E-04 历史版本行 / E-05 版本快照（EvaluationHistoryVO 真身：
    list 场景只填 versionNo/source/totalScore/reviewerName/createTime，
    E-05 额外填 metricScores/aiComment/teacherComment/traceId） */
export interface EvaluationHistoryItem {
  versionNo: number
  source: string
  totalScore: number | null
  reviewerName: string | null
  aiModel?: string | null
  createTime: string
  metricScores?: Record<string, number> | null
  aiComment?: string | null
  teacherComment?: string | null
  traceId?: string | null
}

/** E-04 游标分页（EvaluationHistoryPageVO 真身：list/nextCursor/hasMore） */
export interface EvaluationHistoryPage {
  list: EvaluationHistoryItem[]
  nextCursor: string | null
  hasMore: boolean
}

/** E-02/E-06 复核/手动评分载荷（ReviewEvaluationRequest 真身：totalScore 必填） */
export interface ReviewPayload {
  totalScore: number
  metricScores?: Record<string, number>
  reviewComment: string
}

export const evaluationApi = {
  /** E-01 评价当前值查询（学生限本人，IDOR 40400同形） */
  async detail(id: number): Promise<EvaluationVO> {
    return (await request.get(`/evaluations/${id}`)) as unknown as EvaluationVO
  },
  /** E-02 教师复核改分（UC-22：AI_DONE→REVIEWED，理由≥10字，乐观锁） */
  async review(id: number, payload: ReviewPayload): Promise<EvaluationVO> {
    return (await request.put(`/evaluations/${id}/review`, payload)) as unknown as EvaluationVO
  },
  /** E-03 触发重评（UC-23：REVIEWED/FAILED→PENDING→MQ重走管道） */
  async reEvaluate(id: number): Promise<void> {
    await request.post(`/evaluations/${id}/re-evaluate`)
  },
  /** E-04 历史版本分页（UC-25） */
  async history(id: number, cursor?: string, limit?: number): Promise<EvaluationHistoryPage> {
    const params: Record<string, unknown> = {}
    if (cursor) params.cursor = cursor
    if (limit) params.limit = limit
    return (await request.get(`/evaluations/${id}/history`, { params })) as unknown as EvaluationHistoryPage
  },
  /** E-05 单版本完整快照（UC-25） */
  async historyVersion(id: number, versionNo: number): Promise<EvaluationHistoryItem> {
    return (await request.get(`/evaluations/${id}/history/${versionNo}`)) as unknown as EvaluationHistoryItem
  },
  /** E-06 手动评分旁路（UC-24：FAILED 直接打分，复用复核链） */
  async manualScore(id: number, payload: ReviewPayload): Promise<EvaluationVO> {
    return (await request.put(`/evaluations/${id}/manual`, payload)) as unknown as EvaluationVO
  },
  /** 学生对已复核评价发起申诉，随机改派其他教师 */
  async appeal(
    id: number,
    reason: string
  ): Promise<{ appealId: number; assignedTeacherId: number; assignedTeacherName: string | null; status: string }> {
    return (await request.post(`/evaluations/${id}/appeal`, { reason })) as unknown as {
      appealId: number
      assignedTeacherId: number
      assignedTeacherName: string | null
      status: string
    }
  }
}
