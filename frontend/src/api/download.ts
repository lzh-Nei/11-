import request, { fetchBlob } from './request'

/* 文件下载（DownloadController 真身：签名 URL 两段式——先拿 info 再跳下载） */

export interface DownloadInfo {
  fileId: string
  fileName: string
  size: number
  url: string
  expireSeconds: number
}

export const downloadApi = {
  /** D-01 下载信息（签名 URL + 过期时间） */
  async info(fileId: string): Promise<DownloadInfo> {
    return (await request.get(`/download/info/${fileId}`)) as unknown as DownloadInfo
  },
  /** D-02 文件直下（支持 Range 断点续传）：二进制链路不经 axios 拦截器，返回文件 Blob */
  async direct(fileId: string): Promise<Blob> {
    return await fetchBlob(`/api/download/${fileId}`)
  }
}
