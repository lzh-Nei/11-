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
  createTime: string
  metrics: TaskMetric[]
}

/** MP Page 形状 */
export interface PageResult<T> {
  records: T[]
  total: number
  size: number
  current: number
}

export const taskApi = {
  async list(page = 1, size = 10, status?: string): Promise<PageResult<TaskInfo>> {
    const params: Record<string, unknown> = { page, size }
    if (status) params.status = status
    return (await request.get('/tasks', { params })) as unknown as PageResult<TaskInfo>
  },
  async detail(id: number): Promise<TaskInfo> {
    return (await request.get(`/tasks/${id}`)) as unknown as TaskInfo
  }
}
