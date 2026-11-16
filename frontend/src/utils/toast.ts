/* toast 统一口径（写操作铁律）：成功 toast / 失败可重试文案。
   全站写操作收口走这里，禁散装 ElMessage 随手写文案 */
import { ElMessage } from 'element-plus'

export const toast = {
  success(message: string) {
    ElMessage({ type: 'success', message, duration: 2200 })
  },
  /** 失败必须带"可重试"语义（后端 message 透传优先） */
  error(message: string, action?: string) {
    ElMessage({ type: 'error', message: action ? `${message}（${action}）` : message, duration: 3000 })
  },
  warning(message: string) {
    ElMessage({ type: 'warning', message, duration: 2200 })
  }
}
