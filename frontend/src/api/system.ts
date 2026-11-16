import request from './request'

/* 系统探活（HealthController：匿名放行，部署探针/登录页存活检测用） */

export const systemApi = {
  /** GET /api/health：返回 "ok" */
  async health(): Promise<string> {
    return (await request.get('/health')) as unknown as string
  }
}
