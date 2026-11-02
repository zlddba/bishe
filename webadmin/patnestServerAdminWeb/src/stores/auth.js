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
  const user = ref(readCachedUser())
  // 每次登录/登出递增，用于识别属于旧会话的在途请求。
  const sessionSeq = ref(0)

  const isLoggedIn = computed(() => !!token.value && !!user.value)
  const username = computed(() => user.value?.username || '')
  const role = computed(() => user.value?.role || '')

  async function login(credentials) {
    const res = await authApi.login(credentials)
    token.value = res.data.access_token
    user.value = res.data.user
    sessionSeq.value += 1
    localStorage.setItem('pn_token', token.value)
    localStorage.setItem('pn_user', JSON.stringify(user.value))
    return res
  }

  // 只清理本地登录态，不请求后端；用于令牌已被服务端判定失效的场景。
  function clearSession() {
    token.value = ''
    user.value = null
    sessionSeq.value += 1
    localStorage.removeItem('pn_token')
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

  return { token, user, sessionSeq, isLoggedIn, username, role, login, logout, clearSession }
})
