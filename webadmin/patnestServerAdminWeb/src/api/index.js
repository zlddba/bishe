import axios from 'axios'
import { useAuthStore } from '@/stores/auth'
import router from '@/router'

// 默认同源 /api/v1：生产由 Nginx 转发，开发由 vite proxy 转发，构建产物里不会写死主机名。
const LOCAL_HOSTS = ['localhost', '127.0.0.1', '::1']

function resolveBaseURL(raw) {
  if (!raw) return '/api/v1'
  try {
    const url = new URL(raw, window.location.origin)
    // 旧配置可能写成 http://localhost:9200/api/v1，换台机器访问就会打到访问者自己的电脑。
    if (LOCAL_HOSTS.includes(url.hostname) && !LOCAL_HOSTS.includes(window.location.hostname)) {
      return '/api/v1'
    }
  } catch {
    return raw
  }
  return raw.replace(/\/$/, '')
}

const request = axios.create({
  baseURL: resolveBaseURL(import.meta.env.VITE_API_BASE),
  timeout: 15000,
})

request.interceptors.request.use((config) => {
  const auth = useAuthStore()
  // 记录本次请求使用的是哪个会话，401 回来时用来分辨“旧会话的迟到响应”。
  config._pnToken = auth.token
  config._pnSession = auth.sessionSeq
  if (auth.token) {
    config.headers.Authorization = `Bearer ${auth.token}`
  }
  return config
})

// 登录/登出/刷新接口自身的 401 不做全局处理，避免死循环和误伤新会话。
function isAuthEndpoint(url = '') {
  return url.includes('/auth/login') || url.includes('/auth/logout') || url.includes('/auth/refresh')
}

// 同一时刻只发一次刷新请求，其它撞上 401 的请求共用这个 Promise。
let refreshPromise = null

function refreshSession() {
  const auth = useAuthStore()
  if (!auth.refreshToken) return Promise.resolve(false)
  if (!refreshPromise) {
    refreshPromise = request
      .post('/auth/refresh', { refresh_token: auth.refreshToken })
      .then((res) => {
        auth.applyAccessToken(res.data?.access_token || '', res.data?.refresh_token || '')
        return !!auth.token
      })
      .catch(() => false)
      .finally(() => {
        refreshPromise = null
      })
  }
  return refreshPromise
}

// 只有"发起该请求时的会话"仍是当前会话，才说明登录态真的失效了。
// 否则是上一次会话的请求迟到返回，不能清掉刚登录成功的新令牌。
function expireSession(config) {
  const auth = useAuthStore()
  if (!auth.token || config._pnSession !== auth.sessionSeq) return
  auth.clearSession()
  if (router.currentRoute.value.path !== '/login') {
    router.replace('/login')
  }
}

request.interceptors.response.use(
  (res) => {
    // CSV 等二进制响应没有统一 JSON 包装
    if (res.config.responseType === 'blob') return res
    const data = res.data
    if (data.code !== 0) {
      return Promise.reject(new Error(data.message || 'Request failed'))
    }
    return data
  },
  (err) => {
    if (err.response?.status === 401 && !isAuthEndpoint(err.config?.url)) {
      // 访问令牌过期是常态：先静默换一张，原请求重放一次，管理员不会因此退回登录页。
      const config = err.config || {}
      if (!config._pnRetried && config._pnToken) {
        config._pnRetried = true
        return refreshSession().then((ok) => {
          if (ok) return request(config)
          expireSession(config)
          return Promise.reject(err)
        })
      }
      expireSession(config)
    }
    return Promise.reject(err)
  }
)

export default request

export const authApi = {
  login: (data) => request.post('/auth/login', data),
  logout: (config) => request.post('/auth/logout', null, config),
  getProfile: () => request.get('/auth/me'),
}

export const systemApi = {
  // 后端运行版本（发布/热更新后与前端构建版本可能不同）
  getVersion: () => request.get('/version'),
}

export const adminApi = {
  getUsers: (params) => request.get('/admin/users', { params }),
  createUser: (data) => request.post('/admin/users', data),
  updateUser: (id, data) => request.put(`/admin/users/${id}`, data),
  deleteUser: (id) => request.delete(`/admin/users/${id}`),
  purgeUser: (id) => request.delete(`/admin/users/${id}/purge`),
  resetPassword: (id, data) => request.put(`/admin/users/${id}/password`, data),

  getSitters: (params) => request.get('/admin/sitters', { params }),
  getSitterDetail: (id) => request.get(`/admin/sitters/${id}`),
  createSitter: (data) => request.post('/admin/sitters', data),
  updateSitter: (id, data) => request.put(`/admin/sitters/${id}`, data),
  updateSitterStatus: (id, data) => request.put(`/admin/sitters/${id}/status`, data),
  deleteSitter: (id) => request.delete(`/admin/sitters/${id}`),

  getOrders: (params) => request.get('/admin/orders', { params }),
  getOrder: (id) => request.get(`/admin/orders/${id}`),
  createOrder: (data) => request.post('/admin/orders', data),
  updateOrder: (id, data) => request.put(`/admin/orders/${id}`, data),
  deleteOrder: (id) => request.delete(`/admin/orders/${id}`),
  refundOrder: (id, data) => request.post(`/admin/orders/${id}/refund`, data),

  getPets: (params) => request.get('/admin/pets', { params }),
  createPet: (data) => request.post('/admin/pets', data),
  updatePet: (id, data) => request.put(`/admin/pets/${id}`, data),
  deletePet: (id) => request.delete(`/admin/pets/${id}`),

  getDynamics: (params) => request.get('/admin/dynamics', { params }),
  createDynamic: (data) => request.post('/admin/dynamics', data),
  updateDynamic: (id, data) => request.put(`/admin/dynamics/${id}`, data),
  deleteDynamic: (id) => request.delete(`/admin/dynamics/${id}`),

  getReviews: (params) => request.get('/admin/reviews', { params }),
  createReview: (data) => request.post('/admin/reviews', data),
  updateReview: (id, data) => request.put(`/admin/reviews/${id}`, data),
  deleteReview: (id) => request.delete(`/admin/reviews/${id}`),

  getAuditLogs: (params) => request.get('/admin/audit', { params }),
  getStats: () => request.get('/admin/stats'),
  getSystemInfo: () => request.get('/admin/system/info'),
  getTLS: () => request.get('/admin/system/tls'),
  uploadTLS: (data) => request.post('/admin/system/tls', data),
  removeTLS: () => request.delete('/admin/system/tls'),
  // 后端自己的端口是否同时接受 HTTPS（页面开关，切换即时生效）
  setBackendTLS: (data) => request.post('/admin/system/tls/backend', data),
  getUpdates: () => request.get('/admin/updates'),
  scanUpdates: () => request.post('/admin/updates/scan'),
  uploadUpdate: (file, onUploadProgress) => {
    const form = new FormData()
    form.append('file', file)
    return request.post('/admin/updates/upload', form, {
      timeout: 10 * 60 * 1000,
      onUploadProgress,
    })
  },
  applyUpdate: (data) => request.post('/admin/updates/apply', data),
  getUpdateJob: (id) => request.get(`/admin/updates/jobs/${id}`),

  getNotifications: (params) => request.get('/admin/notifications', { params }),
  broadcastNotification: (data) => request.post('/admin/notifications/broadcast', data),
  deleteNotification: (id) => request.delete(`/admin/notifications/${id}`),

  getPushTasks: (params) => request.get('/admin/push-tasks', { params }),
  createPushTask: (data) => request.post('/admin/push-tasks', data),
  sendPushTask: (id) => request.post(`/admin/push-tasks/${id}/send`),
  cancelPushTask: (id) => request.post(`/admin/push-tasks/${id}/cancel`),

  getCoupons: (params) => request.get('/admin/coupons', { params }),
  createCoupon: (data) => request.post('/admin/coupons', data),
  updateCoupon: (id, data) => request.put(`/admin/coupons/${id}`, data),
  deleteCoupon: (id) => request.delete(`/admin/coupons/${id}`),

  getTemplates: () => request.get('/admin/notification-templates'),
  createTemplate: (data) => request.post('/admin/notification-templates', data),
  updateTemplate: (id, data) => request.put(`/admin/notification-templates/${id}`, data),
  deleteTemplate: (id) => request.delete(`/admin/notification-templates/${id}`),

  getPermissions: () => request.get('/admin/permissions'),
  updatePermission: (data) => request.put('/admin/permissions', data),

  exportCSV: (kind) => request.get(`/admin/export/${kind}`, { responseType: 'blob', timeout: 120000 }),

  getRefundRequests: (params) => request.get('/admin/refunds', { params }),
  handleRefundRequest: (id, data) => request.put(`/admin/refunds/${id}`, data),

  getTickets: (params) => request.get('/admin/tickets', { params }),
  getTicket: (id) => request.get(`/admin/tickets/${id}`),
  replyTicket: (id, data) => request.post(`/admin/tickets/${id}/reply`, data),
  updateTicket: (id, data) => request.put(`/admin/tickets/${id}`, data),

  getSettlements: (params) => request.get('/admin/settlements', { params }),
  paySettlement: (id, data) => request.put(`/admin/settlements/${id}`, data),

  // 平台参数：全局佣金比例（单机构覆盖走 updateSitter 的 commission_rate）
  getCommissionSetting: () => request.get('/admin/settings/commission'),
  updateCommissionSetting: (data) => request.put('/admin/settings/commission', data),

  // 钱包管理
  getWallets: (params) => request.get('/admin/wallets', { params }),
  getWalletDetail: (userId) => request.get(`/admin/wallets/${userId}`),
  adjustWallet: (userId, data) => request.post(`/admin/wallets/${userId}/adjust`, data),
  getRecharges: (params) => request.get('/admin/wallet/recharges', { params }),
  confirmRecharge: (id) => request.post(`/admin/wallet/recharges/${id}/confirm`),
  getWalletWithdrawals: (params) => request.get('/admin/wallet/withdrawals', { params }),
  payWalletWithdrawal: (id, data) => request.post(`/admin/wallet/withdrawals/${id}/pay`, data),
  rejectWalletWithdrawal: (id, data) => request.post(`/admin/wallet/withdrawals/${id}/reject`, data),
  getCancelFailures: (params) => request.get('/admin/account-cancels/failed', { params }),
  retryCancel: (userId) => request.post(`/admin/account-cancels/${userId}/retry`),

  verifyPetVaccine: (id, data) => request.put(`/admin/pets/${id}/vaccine`, data),

  uploadImage: (file) => {
    const form = new FormData()
    form.append('file', file)
    return request.post('/upload/image', form, { headers: { 'Content-Type': 'multipart/form-data' } })
  },
}
