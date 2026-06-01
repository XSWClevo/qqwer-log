type JwtHeader = {
  alg?: string
}

type JwtPayload = {
  exp?: number
}

const base64UrlToBase64 = (value: string) => {
  const normalized = value.replace(/-/g, '+').replace(/_/g, '/')
  return normalized.padEnd(Math.ceil(normalized.length / 4) * 4, '=')
}

const decodeBase64Url = (value: string) => {
  return globalThis.atob(base64UrlToBase64(value))
}

const parseJwtSection = <T>(value: string): T | null => {
  try {
    return JSON.parse(decodeBase64Url(value)) as T
  } catch {
    return null
  }
}

/**
 * 这里只做“像不像 JWT”的格式校验，不做签名验证。
 * 目的只有两个：
 * 1. 避免把 undefined/null/普通字符串 继续发给后端
 * 2. 避免前端仅凭 localStorage 里有值就误判自己已登录
 */
export function isLikelyJwtToken(value?: string | null): value is string {
  if (!value) {
    return false
  }

  const trimmed = value.trim()
  if (!trimmed || trimmed === 'undefined' || trimmed === 'null') {
    return false
  }

  return trimmed.split('.').length === 3
}

export function isUsableJwtToken(value?: string | null): value is string {
  if (!isLikelyJwtToken(value)) {
    return false
  }

  const parts = value.split('.')
  const encodedHeader = parts[0]
  const encodedPayload = parts[1]
  if (!encodedHeader || !encodedPayload) {
    return false
  }
  const header = parseJwtSection<JwtHeader>(encodedHeader)
  const payload = parseJwtSection<JwtPayload>(encodedPayload)

  if (!header || !payload) {
    return false
  }

  if (String(header.alg || '').toLowerCase() === 'none') {
    return false
  }

  if (typeof payload.exp === 'number' && payload.exp <= Math.floor(Date.now() / 1000)) {
    return false
  }

  return true
}

export function readStoredJwtToken(key: string) {
  const token = localStorage.getItem(key)
  return isUsableJwtToken(token) ? token : ''
}

export function clearStoredAuthTokens() {
  localStorage.removeItem('accessToken')
  localStorage.removeItem('refreshToken')
}
