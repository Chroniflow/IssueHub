import { describe, expect, it } from 'vitest'
import { createIdempotencyKey, getApiErrorMessage, getApiErrorStatus } from './http'
import axios from 'axios'

describe('HTTP helpers', () => {
  it('extracts structured API errors including field messages', () => {
    const error = new axios.AxiosError('bad request', 'ERR_BAD_REQUEST', undefined, undefined, {
      status: 422,
      statusText: 'Unprocessable Entity',
      headers: {},
      config: { headers: new axios.AxiosHeaders() },
      data: { message: '校验失败', fields: { title: '标题不能为空', body: '正文不能为空' } },
    })
    expect(getApiErrorStatus(error)).toBe(422)
    expect(getApiErrorMessage(error, '失败')).toBe('校验失败：标题不能为空；正文不能为空')
  })

  it('distinguishes network errors and generates non-empty idempotency keys', () => {
    const networkError = new axios.AxiosError('offline', 'ERR_NETWORK')
    expect(getApiErrorMessage(networkError, '失败')).toContain('网络连接失败')
    expect(createIdempotencyKey()).toMatch(/^[0-9a-f-]{36}$/)
  })
})
