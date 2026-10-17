import { describe, expect, it } from 'vitest'
import { formatLocalDateTime, hasTextWithin, isValidEmail } from './format'

describe('format utilities', () => {
  it('formats valid dates and uses a safe fallback for invalid input', () => {
    expect(formatLocalDateTime('not-a-date')).toBe('时间未知')
    expect(formatLocalDateTime(null, '暂无')).toBe('暂无')
    expect(formatLocalDateTime('2026-01-02T03:04:00Z')).not.toBe('时间未知')
  })

  it('validates email and trimmed text boundaries', () => {
    expect(isValidEmail(' player@example.com ')).toBe(true)
    expect(isValidEmail('player@')).toBe(false)
    expect(hasTextWithin('  abc  ', 3, 3)).toBe(true)
    expect(hasTextWithin('  ab  ', 3, 10)).toBe(false)
  })
})
