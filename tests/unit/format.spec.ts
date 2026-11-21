import { describe, expect, it } from 'vitest'
import { formatSize, formatTime } from '../../src/utils/format'

describe('formatSize', () => {
  it('renders bytes under 1KB verbatim', () => {
    expect(formatSize(0)).toBe('0 B')
    expect(formatSize(999)).toBe('999 B')
  })
  it('scales to KB/MB/GB with one decimal', () => {
    expect(formatSize(1536)).toBe('1.5 KB')
    expect(formatSize(1572864)).toBe('1.5 MB')
    expect(formatSize(1610612736)).toBe('1.5 GB')
  })
})

describe('formatTime', () => {
  it('renders local time as yyyy-MM-dd HH:mm', () => {
    const ms = new Date(2026, 10, 21, 15, 9).getTime()
    expect(formatTime(ms)).toBe('2026-11-21 15:09')
  })
})
