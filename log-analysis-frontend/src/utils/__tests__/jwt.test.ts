import { describe, expect, it, vi } from 'vitest'
import { isUsableJwtToken } from '@/utils/jwt'

const encodeSegment = (value: Record<string, unknown>) => {
  const json = JSON.stringify(value)
  return btoa(json).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/g, '')
}

const createJwt = (
  header: Record<string, unknown>,
  payload: Record<string, unknown>
) => `${encodeSegment(header)}.${encodeSegment(payload)}.signature`

describe('isUsableJwtToken', () => {
  it('rejects unsecured jwt tokens that declare alg none', () => {
    const token = createJwt(
      { alg: 'none', typ: 'JWT' },
      { sub: 'user-1', exp: Math.floor(Date.now() / 1000) + 60 }
    )

    expect(isUsableJwtToken(token)).toBe(false)
  })

  it('rejects expired jwt tokens', () => {
    vi.useFakeTimers()
    vi.setSystemTime(new Date('2026-06-01T19:10:00+08:00'))

    const token = createJwt(
      { alg: 'HS256', typ: 'JWT' },
      { sub: 'user-1', exp: Math.floor(Date.now() / 1000) - 5 }
    )

    expect(isUsableJwtToken(token)).toBe(false)

    vi.useRealTimers()
  })

  it('accepts signed jwt tokens that are not expired', () => {
    const token = createJwt(
      { alg: 'HS256', typ: 'JWT' },
      { sub: 'user-1', exp: Math.floor(Date.now() / 1000) + 60 }
    )

    expect(isUsableJwtToken(token)).toBe(true)
  })
})
