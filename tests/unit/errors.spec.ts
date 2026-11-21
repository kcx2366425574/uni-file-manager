import { describe, expect, it } from 'vitest'
import { describeError } from '../../src/utils/errors'

describe('describeError', () => {
  it('maps known native codes to Chinese messages', () => {
    expect(describeError({ code: 'E_PERM' })).toBe('缺少「所有文件访问」权限，请到系统设置中开启')
    expect(describeError({ code: 'E_NO_SPACE' })).toBe('目标存储空间不足')
    expect(describeError({ code: 'E_EXISTS' })).toBe('目标位置已存在同名文件')
    expect(describeError({ code: 'E_NOT_EMPTY' })).toBe('目录内还有文件，请先移出后再删除')
  })
  it('falls back to the original message when code is unknown', () => {
    expect(describeError({ code: 'E_IO', message: 'disk gone' })).toBe('磁盘错误：disk gone')
    expect(describeError({ message: 'boom' })).toBe('boom')
  })
  it('never renders undefined for a malformed error', () => {
    expect(describeError(null)).toBe('操作失败，请重试')
    expect(describeError({})).toBe('操作失败，请重试')
  })
})
