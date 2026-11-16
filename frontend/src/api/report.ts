import request from './request'

/* 报表域接口（W11 ReportController 真身；DTO 对齐 ReportOverviewDTO 契约锁定版） */

export interface TaskStat {
  taskId: number
  title: string
  submitted: number
  scored: number
  avgScore: number | null
  rate: number
}

export interface RangeCount {
  range: string
  count: number
}

export interface MetricScore {
  metricName: string
  avgScore: number | null
}

export interface ReportOverview {
  tasks: TaskStat[]
  scoreDist: RangeCount[]
  metricRadar: MetricScore[]
  scoreGapDist: RangeCount[]
}

export const reportApi = {
  /** 教师聚合报表（report:view:teacher） */
  async overview(): Promise<ReportOverview> {
    return (await request.get('/reports/overview')) as unknown as ReportOverview
  },
  /** 学生个人报表（report:view） */
  async overviewMe(): Promise<ReportOverview> {
    return (await request.get('/reports/overview/me')) as unknown as ReportOverview
  }
}
