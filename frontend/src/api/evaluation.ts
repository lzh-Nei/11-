import request from './request'

/** 评价当前值（E-01 / UC-21：traceId 仅教师视角返回，学生视角字段缺省） */
export interface EvaluationVO {
  id: number
  submissionId: number
  status: 'PENDING' | 'AI_DONE' | 'REVIEWED' | 'FAILED'
  currentVersionNo: number
  totalScore: number | null
  metricScores: Record<string, number> | null
  aiComment: string | null
  errorCode: number | null
  updateTime: string
  traceId?: string | null
}

/** E-04 历史版本行（UC-25：倒序游标分页） */
export interface EvaluationHistoryItem {
  versionNo: number
  source: string
  totalScore: number | null
  changeReason: string | null
  operatorName: string | null
  createdAt: string
}

export interface EvaluationHistoryPage {
  items: EvaluationHistoryItem[]
  nextCursor: string | null
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
  async historyVersion(id: number, versionNo: number): Promise<EvaluationVO> {
    return (await request.get(`/evaluations/${id}/history/${versionNo}`)) as unknown as EvaluationVO
  },
  /** E-06 手动评分旁路（UC-24：FAILED 直接打分，复用复核链） */
  async manualScore(id: number, payload: ReviewPayload): Promise<EvaluationVO> {
    return (await request.put(`/evaluations/${id}/manual`, payload)) as unknown as EvaluationVO
  }
}
