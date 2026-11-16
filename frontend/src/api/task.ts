import request from './request'

/** 指标视图（UC-13 同形展示） */
export interface TaskMetric {
  id: number
  metricName: string
  weight: number
  sortOrder: number
}

/** 任务视图（03-03 T-04/T-05；学生VO无 submissionCount） */
export interface TaskInfo {
  id: number
  title: string
  description: string
  teacherId: number
  classId: number
  deadline: string
  status: string
  publishTime: string | null
  createTime: string
  className: string | null
  teacherName: string | null
  submissionCount?: number | null
  metrics: TaskMetric[]
}

/** MP Page 形状 */
export interface PageResult<T> {
  records: T[]
  total: number
  size: number
  current: number
}

/** T-01 创建任务载荷（TaskCreateRequest 真身：Σweight 必须=100） */
export interface TaskCreatePayload {
  title: string
  description: string
  classId: number
  deadline: string
  metrics: { metricName: string; weight: number; sortOrder: number }[]
}

export const taskApi = {
  async list(page = 1, size = 10, status?: string, stage?: string): Promise<PageResult<TaskInfo>> {
    const params: Record<string, unknown> = { page, size }
    if (status) params.status = status
    if (stage) params.stage = stage
    return (await request.get('/tasks', { params })) as unknown as PageResult<TaskInfo>
  },
  async detail(id: number): Promise<TaskInfo> {
    return (await request.get(`/tasks/${id}`)) as unknown as TaskInfo
  },
  /** T-01 创建任务（UC-10）：Σweight≠100→40001、deadline 非法→30006 */
  async create(payload: TaskCreatePayload): Promise<TaskInfo> {
    return (await request.post('/tasks', payload)) as unknown as TaskInfo
  },
  /** 编辑任务（仅草稿；发布后指标集锁定） */
  async update(id: number, payload: TaskCreatePayload): Promise<TaskInfo> {
    return (await request.put(`/tasks/${id}`, payload)) as unknown as TaskInfo
  },
  /** 删除任务（仅草稿且无学生提交） */
  async remove(id: number): Promise<void> {
    await request.delete(`/tasks/${id}`)
  },
  /** T-03 发布任务：DRAFT→PUBLISHED 状态机，发布后指标集锁定（不可逆） */
  async publish(id: number): Promise<TaskInfo> {
    return (await request.post(`/tasks/${id}/publish`)) as unknown as TaskInfo
  }
}
