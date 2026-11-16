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

export const evaluationApi = {
  /** E-01 评价当前值查询（学生限本人，IDOR 40400同形） */
  async detail(id: number): Promise<EvaluationVO> {
    return (await request.get(`/evaluations/${id}`)) as unknown as EvaluationVO
  }
}
