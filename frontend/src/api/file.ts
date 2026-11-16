import request from './request'

/* 文件清单（FileController 真身：前端下载中心用） */

/** FileMeta 契约（后端 record：id/name/size/md5/uploadTime） */
export interface FileMeta {
  id: string
  name: string
  size: number
  md5: string
  uploadTime: string
}

export const fileApi = {
  /** 已入库文件列表（下载中心） */
  async list(): Promise<FileMeta[]> {
    return (await request.get('/files')) as unknown as FileMeta[]
  }
}
