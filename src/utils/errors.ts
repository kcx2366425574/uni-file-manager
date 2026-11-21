const MESSAGES: Record<string, string> = {
  E_PERM: '缺少「所有文件访问」权限，请到系统设置中开启',
  E_NOENT: '文件或目录不存在',
  E_EXISTS: '目标位置已存在同名文件',
  E_NO_SPACE: '目标存储空间不足',
  E_BAD_NAME: '名称不合法或与现有分组重名',
  E_NOT_EMPTY: '目录内还有文件，请先移出后再删除',
  E_RENAME_FAILED: '移动失败，原文件未改动',
  E_COPY_FAILED: '复制校验失败，原文件未改动',
  E_IO: '磁盘错误'
}

export function describeError(e: unknown): string {
  if (e && typeof e === 'object') {
    const { code, message } = e as { code?: string; message?: string }
    if (code && MESSAGES[code]) {
      return code === 'E_IO' && message ? `磁盘错误：${message}` : MESSAGES[code]
    }
    if (message && typeof message === 'string') return message
  }
  return '操作失败，请重试'
}
