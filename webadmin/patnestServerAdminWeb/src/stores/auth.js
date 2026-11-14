import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { authApi } from '@/api'

function readCachedUser() {
  try {
    const raw = localStorage.getItem('pn_user')
    return raw ? JSON.parse(raw) : null
  } catch {
    localStorage.removeItem('pn_user')
    return null
  }
}

export const useAuthStore = defineStore('auth', () => {
  const token = ref(localStorage.getItem('pn_token') || '')
  // 刷新令牌：访问令牌过期后用它换一张新的，管理员不必每两小时重登一次
  const refreshToken = ref(localStorage.getItem('pn_refresh') || '')
  const user = ref(readCachedUser())
  // 每次登录/登出递增，用于识别属于旧会话的在途请求。
  const sessionSeq = ref(0)

  const isLoggedIn = computed(() => !!token.value && !!user.value)
  const username = computed(() => user.value?.username || '')
  const role = computed(() => user.value?.role || '')

  async function login(credentials) {
    const res = await authApi.login(credentials)
    if (res.data?.totp_required) {
      // 两步验证：这时还拿不到令牌，调用方要拿着票据去第二步
      return res
    }
    applySession(res)
    return res
  }

  // 两步验证第二步：用登录时下发的票据 + 6 位口令（或恢复码）换令牌。
  async function loginTotp(payload) {
    const res = await authApi.loginTotp(payload)
    applySession(res)
    return res
  }

  function applySession(res) {
    token.value = res.data.access_token
    refreshToken.value = res.data.refresh_token || ''
    user.value = res.data.user
    sessionSeq.value += 1
    localStorage.setItem('pn_token', token.value)
    if (refreshToken.value) localStorage.setItem('pn_refresh', refreshToken.value)
    else localStorage.removeItem('pn_refresh')
    localStorage.setItem('pn_user', JSON.stringify(user.value))
  }

  // 静默续期：换访问令牌，顺带收下服务端轮换出来的新刷新令牌。
  // sessionSeq 不动，这样在途请求不会被误判成旧会话。
  function applyAccessToken(accessToken, refreshedRefreshToken) {
    token.value = accessToken || ''
    if (token.value) localStorage.setItem('pn_token', token.value)
    else localStorage.removeItem('pn_token')
    // 后端每次刷新都会轮换刷新令牌；若返回的是旧版本（没带这个字段），继续沿用手里这张
    if (refreshedRefreshToken) {
      refreshToken.value = refreshedRefreshToken
      localStorage.setItem('pn_refresh', refreshedRefreshToken)
    }
  }

  // 只清理本地登录态，不请求后端；用于令牌已被服务端判定失效的场景。
  function clearSession() {
    token.value = ''
    refreshToken.value = ''
    user.value = null
    sessionSeq.value += 1
    localStorage.removeItem('pn_token')
    localStorage.removeItem('pn_refresh')
    localStorage.removeItem('pn_user')
  }

  function logout() {
    const current = token.value
    if (current) {
      // 显式带上令牌：本地状态会立即清空，不能依赖请求拦截器稍后再读 token。
      authApi.logout({ headers: { Authorization: `Bearer ${current}` } }).catch(() => {})
    }
    clearSession()
  }

  return {
    token,
    refreshToken,
    user,
    sessionSeq,
    isLoggedIn,
    username,
    role,
    login,
    loginTotp,
    logout,
    clearSession,
    applyAccessToken,
  }
})
