/** 下钻页统一新窗口打开（保持同一左右布局；noopener 防被打开页反向操作本窗口） */
export function openWindow(path: string): void {
  window.open(path, '_blank', 'noopener')
}
