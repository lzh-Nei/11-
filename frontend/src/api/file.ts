import request from './request'

/* 文件清单（FileController 真身：前端下载中心用） */

/** 文件中心列表项（含上传者姓名与班级） */
export interface FileMeta {
  id: string
  name: string
  size: number
  uploadTime: string
  uploaderId: number | null
  uploaderName: string | null
  className: string | null
}

export const fileApi = {
  /** 已入库文件列表（教师/管理员） */
  async list(): Promise<FileMeta[]> {
    return (await request.get('/files')) as unknown as FileMeta[]
  },
  /** 管理员删除文件 */
  async remove(id: string): Promise<void> {
    await request.delete(`/files/${id}`)
  }
}
