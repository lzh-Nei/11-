import request from './request'
import type { EvaluationVO } from './evaluation'
import type { PageResult } from './task'

/** 教师评价工作流行：提交 + 当前评价 + 最近评价方 + 申诉改派。 */
export interface TeacherWorkflowRow {
  submissionId: number
  taskId: number
  taskTitle: string | null
  classId: number | null
  className: string | null
  studentId: number
  studentName: string | null
  studentUsername: string | null
  fileName: string
  versionNo: number
  parseStatus: string
  submitTime: string
  evaluationId: number | null
  evaluationStatus: string | null
  totalScore: number | null
  currentVersionNo: number | null
  aiModel: string | null
  lastEvaluator: string | null
  evaluationUpdateTime: string | null
  canTriggerAi: boolean
  appealId: number | null
  appealStatus: string | null
  assignedTeacherId: number | null
  assignedTeacherName: string | null
  appealReason: string | null
}

export interface TeacherWorkflowQuery {
  page: number
  size: number
  classId?: number
  keyword?: string
  stage?: 'all' | 'ai' | 'review' | 'history'
}

export const teacherWorkflowApi = {
  async list(query: TeacherWorkflowQuery): Promise<PageResult<TeacherWorkflowRow>> {
    const params: Record<string, unknown> = {
      page: query.page,
      size: query.size,
      stage: query.stage || 'all'
    }
    if (query.classId) params.classId = query.classId
    if (query.keyword) params.keyword = query.keyword
    return (await request.get('/teacher/workflow/submissions', { params })) as unknown as PageResult<TeacherWorkflowRow>
  },
  async triggerAi(submissionId: number): Promise<void> {
    await request.post(`/teacher/workflow/submissions/${submissionId}/ai-evaluate`)
  },
  async approve(evaluationId: number): Promise<EvaluationVO> {
    return (await request.post(`/teacher/workflow/evaluations/${evaluationId}/approve`)) as unknown as EvaluationVO
  }
}
