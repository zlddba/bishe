<template>
  <div class="login-wrap">
    <div class="login-brand">
      <div class="brand-mark">
        <span class="brand-badge">PN</span>
        <span>宠栖后台</span>
      </div>
      <div>
        <div class="brand-title">宠物寄养服务平台<br>运营管理中枢</div>
        <div class="brand-sub">把机构、订单、宠物、寄养动态与口碑统一收进一个管理后台。清晰、克制、一目了然。</div>
      </div>
      <div class="brand-foot">宠栖 PetNest 后台管理系统</div>
    </div>
    <div class="login-form">
      <div class="login-card">
        <div class="login-title">{{ step === 'totp' ? '两步验证' : '登录后台' }}</div>
        <div class="login-hint">
          {{ step === 'totp' ? `已确认密码，请输入 ${accountLabel} 的验证码` : '使用管理员账号继续' }}
        </div>

        <!-- 第一步：账号密码 -->
        <el-form v-if="step === 'password'" ref="formRef" :model="form" :rules="rules" @submit.prevent="handleLogin">
          <el-form-item prop="username">
            <el-input v-model="form.username" placeholder="请输入账户名称" size="large" :disabled="loading" />
          </el-form-item>
          <el-form-item prop="password">
            <el-input v-model="form.password" type="password" placeholder="请输入密码" size="large" show-password :disabled="loading" @keyup.enter="handleLogin" />
          </el-form-item>
          <el-checkbox v-model="form.remember" :disabled="loading">记住登录状态</el-checkbox>
          <el-form-item>
            <el-button type="primary" size="large" :loading="loading" class="login-btn" @click="handleLogin">
              登 录
            </el-button>
          </el-form-item>
        </el-form>

        <!-- 第二步：动态口令 / 恢复码 -->
        <div v-else class="totp-step">
          <el-input
            ref="codeRef"
            v-model="totpCode"
            :placeholder="useRecovery ? 'XXXXX-XXXXX' : '6 位动态验证码'"
            size="large"
            :maxlength="useRecovery ? 14 : 6"
            :disabled="totpLoading"
            class="totp-input"
            @keyup.enter="handleTotp"
          />
          <div class="totp-tip">
            {{ useRecovery
              ? '输入启用两步验证时保存的恢复码，每枚只能用一次。'
              : '打开手机上的验证器 App，输入当前显示的 6 位数字。' }}
          </div>
          <el-button type="primary" size="large" :loading="totpLoading" class="login-btn" @click="handleTotp">
            验证并登录
          </el-button>
          <div class="totp-links">
            <el-button link @click="toggleRecovery">{{ useRecovery ? '改用动态验证码' : '验证器不在身边？用恢复码' }}</el-button>
            <el-button link @click="backToPassword">返回上一步</el-button>
          </div>
        </div>
        <div class="login-help">如需帮助，请联系系统管理员</div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, computed, nextTick } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { authApi } from '@/api'
import { ElMessage } from 'element-plus'

const router = useRouter()
const auth = useAuthStore()
const formRef = ref(null)
const codeRef = ref(null)
const loading = ref(false)

const form = reactive({ username: '', password: '', remember: true })
const rules = {
  username: [{ required: true, message: '请输入账号', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }],
}

// 两步验证（TOTP）：密码通过后服务端只下发一张 5 分钟的票据，
// 这里再用它换正式令牌。票据过期或被作废就退回密码步骤重新来。
const step = ref('password')
const ticket = ref('')
const totpCode = ref('')
const totpLoading = ref(false)
const useRecovery = ref(false)
const accountLabel = computed(() => form.username.trim() || '当前账号')

function toggleRecovery() {
  useRecovery.value = !useRecovery.value
  totpCode.value = ''
  nextTick(() => codeRef.value?.focus?.())
}

function backToPassword() {
  step.value = 'password'
  ticket.value = ''
  totpCode.value = ''
  useRecovery.value = false
  loading.value = false
  totpLoading.value = false
}

function loginErrorMessage(err) {
  if (!err.response) {
    return '连不上后端接口，请确认服务已启动、接口地址可访问'
  }
  const serverMessage = err.response.data?.message || ''
  if (err.response.status === 401) {
    return (err.config?.url || '').includes('/auth/login')
      ? '账号或密码不正确'
      : '登录状态校验失败，请重新登录'
  }
  if (err.response.status === 403) return serverMessage || '该账号已被禁用或暂时锁定'
  if (err.response.status === 429) return serverMessage || '登录尝试过于频繁，请稍后再试'
  return serverMessage || err.message || '登录失败'
}

// 发布新版本后，旧页面可能拿不到新的分包文件，表现为“登录成功却进不去”，整页刷新即可恢复。
function isChunkLoadError(err) {
  return /dynamically imported module|Importing a module script failed|Loading chunk/i.test(err?.message || '')
}

function reloadIntoApp() {
  const base = (import.meta.env.BASE_URL || '/').replace(/\/$/, '')
  window.location.replace(`${base}/dashboard`)
}

// 登录成功后的统一收尾：确认是管理员会话再进后台。
async function afterLogin() {
  // 立刻用新令牌访问受保护接口，确认会话已生效再进入后台。
  const profile = await authApi.getProfile()
  if (profile.data?.role !== 'admin') {
    ElMessage.error('仅管理员账号可登录此后台')
    auth.logout()
    return
  }

  ElMessage.success('登录成功')
  await router.replace('/dashboard')
  sessionStorage.removeItem('pn_chunk_retry')
}

async function handleLogin() {
  // 回车和点击可能同时触发提交；管理员是单会话，重复登录会把刚拿到的令牌顶掉。
  if (loading.value) return
  loading.value = true
  try {
    const valid = await formRef.value.validate().catch(() => false)
    if (!valid) return

    const res = await auth.login({ username: form.username.trim(), password: form.password })

    // 启用了两步验证：此时还没有令牌，进入第二步
    if (res.data?.totp_required) {
      ticket.value = res.data.totp_ticket
      step.value = 'totp'
      totpCode.value = ''
      useRecovery.value = false
      form.password = ''
      loading.value = false
      nextTick(() => codeRef.value?.focus?.())
      return
    }

    await afterLogin()
  } catch (err) {
    // 登录接口已返回但会话校验没通过时，不在本地留下半登录状态。
    if ((err.config?.url || '').includes('/auth/me')) {
      auth.clearSession()
    }
    if (isChunkLoadError(err) && !sessionStorage.getItem('pn_chunk_retry')) {
      sessionStorage.setItem('pn_chunk_retry', '1')
      reloadIntoApp()
      return
    }
    ElMessage.error(loginErrorMessage(err))
  } finally {
    loading.value = false
  }
}

async function handleTotp() {
  if (totpLoading.value) return
  const code = totpCode.value.trim()
  const expected = useRecovery.value ? 10 : 6
  if (code.replace(/[^0-9A-Za-z]/g, '').length !== expected) {
    ElMessage.warning(useRecovery.value ? '请输入 10 位恢复码' : '请输入 6 位动态验证码')
    return
  }
  totpLoading.value = true
  try {
    await auth.loginTotp({ ticket: ticket.value, code })
    await afterLogin()
  } catch (err) {
    if (isChunkLoadError(err) && !sessionStorage.getItem('pn_chunk_retry')) {
      sessionStorage.setItem('pn_chunk_retry', '1')
      reloadIntoApp()
      return
    }
    const msg = err.response?.data?.message || ''
    // 票据过期/作废/错误次数过多：退回密码步骤，让用户重新登录
    if (err.response?.status === 401 && /过期|重新登录|次数过多/.test(msg)) {
      ElMessage.error(msg)
      backToPassword()
      return
    }
    ElMessage.error(msg || '验证码不正确')
    totpCode.value = ''
    nextTick(() => codeRef.value?.focus?.())
  } finally {
    totpLoading.value = false
  }
}
</script>

<style scoped>
.login-wrap {
  width: 100%;
  min-height: 100vh;
  display: flex;
  align-items: stretch;
}
.login-brand {
  flex: 1.1;
  background: linear-gradient(160deg, #E0708A, #8E3B4E);
  color: #fff;
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  padding: 48px 56px;
}
.brand-mark {
  display: flex;
  align-items: center;
  gap: 10px;
  font-weight: 600;
  font-size: 16px;
}
.brand-badge {
  width: 30px;
  height: 30px;
  border-radius: 7px;
  background: #fff;
  color: var(--brand-ink);
  display: flex;
  align-items: center;
  justify-content: center;
  font-weight: 700;
  font-size: 12px;
}
.brand-title {
  font-size: 26px;
  font-weight: 600;
  line-height: 1.3;
}
.brand-sub {
  font-size: 15px;
  opacity: 0.9;
  margin-top: 10px;
  line-height: 1.7;
  max-width: 420px;
}
.brand-foot {
  font-size: 12.5px;
  opacity: 0.6;
}
.login-form {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--bg);
  padding: 32px;
}
.login-card {
  width: 340px;
}
.login-title {
  font-size: 22px;
  font-weight: 600;
  color: var(--ink);
}
.login-hint {
  font-size: 13.5px;
  color: var(--ink3);
  margin-top: 6px;
  margin-bottom: 24px;
}
.login-btn {
  width: 100%;
  justify-content: center;
  padding: 11px;
  margin-top: 12px;
}
.login-help {
  text-align: center;
  font-size: 12.5px;
  color: var(--ink3);
  margin-top: 18px;
}

/* 两步验证第二步 */
.totp-step {
  display: flex;
  flex-direction: column;
}
.totp-input :deep(.el-input__inner) {
  text-align: center;
  letter-spacing: 0.32em;
  font-size: 19px;
  font-variant-numeric: tabular-nums;
  padding-left: 0.32em;
}
.totp-tip {
  font-size: 12.5px;
  color: var(--ink3);
  line-height: 1.7;
  margin-top: 10px;
}
.totp-links {
  display: flex;
  justify-content: space-between;
  margin-top: 6px;
}
</style>
