/** 触发浏览器保存 Blob（临时对象 URL + a[download]）。
    回收必须延后：部分浏览器在 click 同步返回后仍需读取 URL，立即 revoke 会取消下载。 */
export function saveBlob(blob: Blob, fileName: string): void {
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = fileName
  document.body.appendChild(a)
  a.click()
  a.remove()
  window.setTimeout(() => URL.revokeObjectURL(url), 1000)
}
