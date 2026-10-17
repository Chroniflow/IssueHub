export function formatLocalDateTime(value: string | null | undefined, fallback = '时间未知') {
  if (!value) return fallback
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return fallback
  return new Intl.DateTimeFormat('zh-CN', { dateStyle: 'medium', timeStyle: 'short' }).format(date)
}

export function isValidEmail(value: string) {
  return /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(value.trim())
}

export function hasTextWithin(value: string, min: number, max: number) {
  const length = value.trim().length
  return length >= min && length <= max
}
