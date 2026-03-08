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

export function readStoredJwtToken(key: string) {
  const token = localStorage.getItem(key)
  return isLikelyJwtToken(token) ? token : ''
}

export function clearStoredAuthTokens() {
  localStorage.removeItem('accessToken')
  localStorage.removeItem('refreshToken')
}
