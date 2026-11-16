import request from './request'

/* 分片上传链（S-06 UploadController 真身；upload×5 全封装——
   init/chunk/complete 主链 + batch 小文件批量 + corrupt-chunk 演示端点） */

export interface InitResponse {
  uploadId: string
  fileName: string
  fileSize: number
  totalChunks: number
  uploadedChunks: number[]
}

export interface ChunkResult {
  ok: boolean
  chunkIndex: number
  md5: string | null
  message: string | null
}

export interface CompleteResponse {
  success: boolean
  fileId: string | null
  name: string | null
  size: number
  md5: string | null
  badChunks: number[]
  message: string | null
}

export interface BatchItemResult {
  fileName: string
  ok: boolean
  fileId: string | null
  message: string | null
}

const DEFAULT_CHUNK = 2 * 1024 * 1024 // 2MB 分片

export const uploadApi = {
  /** U-01 初始化：返回 uploadId 与服务端已有分片（断点续传依据） */
  async init(fileName: string, fileSize: number, lastModified: number, chunkSize = DEFAULT_CHUNK): Promise<InitResponse> {
    const totalChunks = Math.max(1, Math.ceil(fileSize / chunkSize))
    const form = new FormData()
    form.append('fileName', fileName)
    form.append('fileSize', String(fileSize))
    form.append('lastModified', String(lastModified))
    form.append('chunkSize', String(chunkSize))
    form.append('totalChunks', String(totalChunks))
    return (await request.post('/upload/init', form)) as unknown as InitResponse
  },
  /** U-02 上传单分片 */
  async chunk(uploadId: string, chunkIndex: number, blob: Blob): Promise<ChunkResult> {
    const form = new FormData()
    form.append('uploadId', uploadId)
    form.append('chunkIndex', String(chunkIndex))
    form.append('file', blob)
    return (await request.post('/upload/chunk', form, { timeout: 60000 })) as unknown as ChunkResult
  },
  /** U-03 请求合并：badChunks 非空 = 定向重传清单 */
  async complete(uploadId: string): Promise<CompleteResponse> {
    const form = new FormData()
    form.append('uploadId', uploadId)
    return (await request.post('/upload/complete', form, { timeout: 60000 })) as unknown as CompleteResponse
  },
  /** U-04 小文件批量上传 */
  async batch(files: File[]): Promise<BatchItemResult[]> {
    const form = new FormData()
    for (const f of files) form.append('files', f)
    return (await request.post('/upload/batch', form, { timeout: 120000 })) as unknown as BatchItemResult[]
  },
  /** U-05 演示：人为破坏一个分片（答辩展示"坏块检测→定向重传"闭环用） */
  async corruptChunk(uploadId: string): Promise<ChunkResult> {
    const form = new FormData()
    form.append('uploadId', uploadId)
    return (await request.post('/upload/corrupt-chunk', form)) as unknown as ChunkResult
  }
}
