import { handoffUrl } from '@/utils/authSession'

/**
 * 下钻页统一新窗口打开（保持同一左右布局）。
 * 登录态通过一次性的 handoff nonce 在 BroadcastChannel 中交接，不进入 URL/localStorage；
 * 直接新开 URL 的标签页没有会话，会被路由守卫送回登录页。
 */
export function openWindow(path: string): void {
  window.open(handoffUrl(path), '_blank', 'noopener')
}
