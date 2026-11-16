import request, { fetchBlob } from './request'
import type { PageResult } from './task'

/** 提交记录行（S-02 / UC-17：含解析状态与评价状态；evaluationId=W7-C2加法细化，UC-21跳转锚点） */
export interface SubmissionMine {
  id: number
  taskId: number
  fileName: string
  fileSize: number
  fileType: string
  versionNo: number
  parseStatus: string
  evaluationStatus: string
  evaluationId: number | null
  createTime: string
}

/** 提交详情（S-03 / UC-18：签名URL + 提取文本预览） */
export interface SubmissionDetail extends SubmissionMine {
  downloadUrl: string
  textPreview: string | null
  textChars: number | null
}

/** S-01 提交响应（版本号回显） */
export interface SubmissionVO {
  id: number
  taskId: number
  versionNo: number
  fileName: string
  parseStatus: string
}

export const submissionApi = {
  /** S-01 提交作业（multipart，onUploadProgress 提供真实上传进度回调） */
  async submit(
    taskId: number,
    file: File,
    onUploadProgress?: (e: { loaded: number; total?: number }) => void
  ): Promise<SubmissionVO> {
    const form = new FormData()
    form.append('taskId', String(taskId))
    form.append('file', file)
    return (await request.post('/submissions', form, {
      headers: { 'Content-Type': 'multipart/form-data' },
      onUploadProgress,
      timeout: 120000
    })) as unknown as SubmissionVO
  },
  /** S-02 我的提交列表（UC-17） */
  async mine(page = 1, size = 20, taskId?: number): Promise<PageResult<SubmissionMine>> {
    const params: Record<string, unknown> = { page, size }
    if (taskId) params.taskId = taskId
    return (await request.get('/submissions/mine', { params })) as unknown as PageResult<SubmissionMine>
  },
  /** S-03 提交详情（签名URL+文本预览） */
  async detail(id: number): Promise<SubmissionDetail> {
    return (await request.get(`/submissions/${id}`)) as unknown as SubmissionDetail
  },
  /** S-04 下载原件（302→MinIO 预签名直连）：二进制链路不经 axios 拦截器，返回文件 Blob */
  async download(id: number): Promise<Blob> {
    return await fetchBlob(`/api/submissions/${id}/download`)
  }
}
