<template>
  <div class="page-container">
    <div class="page-h">
      <div>
        <h2>账号安全</h2>
        <p>给管理员账号加一道动态口令，密码泄露也进不来</p>
      </div>
    </div>

    <div class="panel">
      <div class="panel-h">
        <h3>两步验证（TOTP）</h3>
        <span class="pn-tag" :class="status.enabled ? 'pn-tag-ok' : 'pn-tag-soft'">
          {{ status.enabled ? '已启用' : '未启用' }}
        </span>
        <span class="ml-auto sec-account">{{ status.account || auth.username }}</span>
      </div>

      <div class="panel-b">
        <!-- 未启用：绑定引导 -->
        <template v-if="!status.enabled">
          <p class="sec-desc">
            启用后，登录时除了账号密码，还需要输入手机验证器 App 上实时生成的 6 位数字。
            支持 Google Authenticator、Microsoft Authenticator、1Password、Authy 等任意标准 TOTP 应用。
          </p>

          <div v-if="!draft.uri" class="sec-start">
            <el-button type="primary" :loading="setupLoading" @click="startSetup">开始绑定</el-button>
            <span v-if="status.pending" class="sec-hint">上次生成过密钥但没确认，重新开始会换一把新的</span>
          </div>

          <div v-else class="sec-bind">
            <div class="sec-qr">
              <img v-if="qrDataUrl" :src="qrDataUrl" alt="两步验证二维码" width="200" height="200" />
              <div v-else class="sec-qr-loading">二维码生成中…</div>
            </div>
            <div class="sec-bind-body">
              <div class="sec-step"><b>1</b> 用验证器 App 扫描左侧二维码</div>
              <div class="sec-step">
                <b>2</b> 扫不了就手动输入密钥
                <div class="sec-secret">
                  <code>{{ draft.secret }}</code>
                  <el-button link @click="copyText(draft.secret)">复制</el-button>
                </div>
              </div>
              <div class="sec-step">
                <b>3</b> 输入 App 里显示的 6 位验证码完成绑定
                <div class="sec-enable">
                  <el-input
                    v-model="draft.code"
                    placeholder="6 位验证码"
                    maxlength="6"
                    style="width:160px"
                    @keyup.enter="confirmEnable"
                  />
                  <el-button type="primary" :loading="enableLoading" @click="confirmEnable">启用</el-button>
                  <el-button link @click="resetDraft">取消</el-button>
                </div>
              </div>
            </div>
          </div>
        </template>

        <!-- 已启用：状态与操作 -->
        <template v-else>
          <div class="sec-grid">
            <div class="sec-item">
              <div class="lbl">启用时间</div>
              <div class="val">{{ formatTime(status.confirmed_at) }}</div>
            </div>
            <div class="sec-item">
              <div class="lbl">恢复码剩余</div>
              <div class="val">
                {{ status.recovery_left }} / {{ status.recovery_total }}
                <span v-if="status.recovery_left === 0" class="pn-tag pn-tag-danger">已用完</span>
              </div>
            </div>
            <div class="sec-item">
              <div class="lbl">验证周期</div>
              <div class="val">{{ status.period || 30 }} 秒 / 次</div>
            </div>
          </div>

          <p class="sec-desc">
            恢复码是验证器丢失时的唯一出路，每枚只能使用一次。
            建议打印或存在密码管理器里，不要和登录密码放在同一处。
          </p>

          <div class="sec-actions">
            <el-button @click="openRecovery">重新生成恢复码</el-button>
            <el-button type="danger" plain @click="openDisable">关闭两步验证</el-button>
          </div>
        </template>
      </div>
    </div>

    <div class="panel">
      <div class="panel-h"><h3>登录保护说明</h3></div>
      <div class="panel-b">
        <ul class="sec-notes">
          <li>管理员账号是单会话：每次登录都会踢掉该账号在其他设备上的登录状态。</li>
          <li>动态验证码 30 秒失效，同一个验证码不能重复使用。</li>
          <li>连续输错验证码会触发账号临时锁定，10 分钟后自动解除。</li>
          <li>换手机或删除了验证器时，请用恢复码登录；恢复码也用完了，只能由内置超级管理员
            （<code>admin</code>）在「用户管理」里重置你的两步验证。</li>
        </ul>
      </div>
    </div>

    <!-- 恢复码展示：只在生成那一刻出现一次 -->
    <el-dialog v-model="codesDialog" title="请立即保存恢复码" width="520px" :close-on-click-modal="false">
      <p class="sec-desc" style="margin-top:0">
        以下 {{ newCodes.length }} 枚恢复码只显示这一次，关闭后无法再次查看。每枚可使用一次，用过的会自动失效。
      </p>
      <div class="sec-codes">
        <code v-for="c in newCodes" :key="c">{{ c }}</code>
      </div>
      <template #footer>
        <el-button @click="copyText(newCodes.join('\n'))">复制全部</el-button>
        <el-button type="primary" @click="codesDialog = false">我已妥善保存</el-button>
      </template>
    </el-dialog>

    <!-- 重新生成恢复码 -->
    <el-dialog v-model="recoveryDialog" title="重新生成恢复码" width="420px">
      <p class="sec-desc" style="margin-top:0">需要先输入当前验证码。生成后旧恢复码立即全部作废。</p>
      <el-input v-model="recoveryCode" placeholder="6 位验证码或一枚恢复码" @keyup.enter="submitRecovery" />
      <template #footer>
        <el-button @click="recoveryDialog = false">取消</el-button>
        <el-button type="primary" :loading="recoveryLoading" @click="submitRecovery">生成</el-button>
      </template>
    </el-dialog>

    <!-- 关闭两步验证 -->
    <el-dialog v-model="disableDialog" title="关闭两步验证" width="420px">
      <p class="sec-desc" style="margin-top:0">
        关闭后只凭密码就能登录后台。需要验证登录密码 + 当前验证码（或用一枚恢复码）。
      </p>
      <el-form label-width="74px">
        <el-form-item label="登录密码">
          <el-input v-model="disableForm.password" type="password" show-password placeholder="当前账号的登录密码" />
        </el-form-item>
        <el-form-item label="验证码">
          <el-input v-model="disableForm.code" placeholder="6 位验证码或一枚恢复码" @keyup.enter="submitDisable" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="disableDialog = false">取消</el-button>
        <el-button type="danger" :loading="disableLoading" @click="submitDisable">确认关闭</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import QRCode from 'qrcode'
import { adminApi } from '@/api'
import { useAuthStore } from '@/stores/auth'

const auth = useAuthStore()

const status = reactive({
  enabled: false,
  pending: false,
  confirmed_at: '',
  recovery_total: 0,
  recovery_left: 0,
  account: '',
  period: 30,
})

const draft = reactive({ uri: '', secret: '', code: '' })
const qrDataUrl = ref('')
const setupLoading = ref(false)
const enableLoading = ref(false)

const codesDialog = ref(false)
const newCodes = ref([])

const recoveryDialog = ref(false)
const recoveryCode = ref('')
const recoveryLoading = ref(false)

const disableDialog = ref(false)
const disableForm = reactive({ password: '', code: '' })
const disableLoading = ref(false)

function formatTime(t) {
  if (!t) return '—'
  const d = new Date(t)
  if (Number.isNaN(d.getTime())) return String(t)
  const p = (n) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())} ${p(d.getHours())}:${p(d.getMinutes())}`
}

async function loadStatus() {
  try {
    const res = await adminApi.getTotp()
    Object.assign(status, res.data || {})
  } catch (e) {
    ElMessage.error(e.message || '读取两步验证状态失败')
  }
}

function resetDraft() {
  draft.uri = ''
  draft.secret = ''
  draft.code = ''
  qrDataUrl.value = ''
}

// 生成密钥并渲染二维码。密钥此时还没生效——必须输入一次正确验证码才算绑定完成。
async function startSetup() {
  setupLoading.value = true
  try {
    const res = await adminApi.setupTotp()
    const data = res.data || {}
    draft.uri = data.otpauth_uri || ''
    draft.secret = data.secret || ''
    draft.code = ''
    if (draft.uri) {
      qrDataUrl.value = await QRCode.toDataURL(draft.uri, {
        margin: 1,
        width: 200,
        color: { dark: '#1F2230', light: '#FFFFFF' },
      })
    }
    await loadStatus()
  } catch (e) {
    ElMessage.error(e.response?.data?.message || e.message || '生成密钥失败')
  } finally {
    setupLoading.value = false
  }
}

async function confirmEnable() {
  const code = draft.code.trim()
  if (code.length !== 6) {
    ElMessage.warning('请输入 6 位验证码')
    return
  }
  enableLoading.value = true
  try {
    const res = await adminApi.enableTotp({ code })
    newCodes.value = res.data?.recovery_codes || []
    codesDialog.value = Boolean(newCodes.value.length)
    resetDraft()
    await loadStatus()
    ElMessage.success('两步验证已启用')
  } catch (e) {
    ElMessage.error(e.response?.data?.message || e.message || '启用失败')
  } finally {
    enableLoading.value = false
  }
}

function openRecovery() {
  recoveryCode.value = ''
  recoveryDialog.value = true
}

async function submitRecovery() {
  const code = recoveryCode.value.trim()
  if (!code) return ElMessage.warning('请输入验证码')
  recoveryLoading.value = true
  try {
    const res = await adminApi.regenerateRecoveryCodes({ code })
    newCodes.value = res.data?.recovery_codes || []
    codesDialog.value = Boolean(newCodes.value.length)
    recoveryDialog.value = false
    await loadStatus()
    ElMessage.success('恢复码已重新生成')
  } catch (e) {
    ElMessage.error(e.response?.data?.message || e.message || '生成失败')
  } finally {
    recoveryLoading.value = false
  }
}

function openDisable() {
  disableForm.password = ''
  disableForm.code = ''
  disableDialog.value = true
}

async function submitDisable() {
  if (!disableForm.password || !disableForm.code) {
    ElMessage.warning('请填写登录密码与验证码')
    return
  }
  disableLoading.value = true
  try {
    await adminApi.disableTotp({ password: disableForm.password, code: disableForm.code.trim() })
    disableDialog.value = false
    await loadStatus()
    ElMessage.success('两步验证已关闭')
  } catch (e) {
    ElMessage.error(e.response?.data?.message || e.message || '关闭失败')
  } finally {
    disableLoading.value = false
  }
}

async function copyText(text) {
  if (!text) return
  try {
    await navigator.clipboard.writeText(text)
    ElMessage.success('已复制')
  } catch {
    // 非 https 或浏览器拒绝剪贴板权限时的兜底
    const ta = document.createElement('textarea')
    ta.value = text
    ta.style.position = 'fixed'
    ta.style.opacity = '0'
    document.body.appendChild(ta)
    ta.select()
    document.execCommand('copy')
    document.body.removeChild(ta)
    ElMessage.success('已复制')
  }
}

onMounted(loadStatus)
</script>

<style scoped>
.sec-account {
  font-size: 13px;
  color: var(--ink2);
  font-family: monospace;
}
.sec-desc {
  font-size: 13px;
  color: var(--ink2);
  line-height: 1.8;
  margin-bottom: 16px;
}
.sec-hint {
  font-size: 12.5px;
  color: var(--ink3);
  margin-left: 10px;
}
.sec-bind {
  display: flex;
  gap: 28px;
  align-items: flex-start;
  flex-wrap: wrap;
}
.sec-qr {
  width: 216px;
  height: 216px;
  border: 1px solid var(--line);
  border-radius: 10px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #fff;
  padding: 8px;
}
.sec-qr img {
  display: block;
}
.sec-qr-loading {
  font-size: 12.5px;
  color: var(--ink3);
}
.sec-bind-body {
  flex: 1;
  min-width: 320px;
}
.sec-step {
  font-size: 13.5px;
  color: var(--ink);
  line-height: 1.8;
  margin-bottom: 14px;
}
.sec-step b {
  display: inline-flex;
  width: 20px;
  height: 20px;
  border-radius: 50%;
  background: var(--brand-soft);
  color: var(--brand-ink);
  align-items: center;
  justify-content: center;
  font-size: 12px;
  margin-right: 8px;
}
.sec-secret {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-top: 6px;
}
.sec-secret code {
  font-family: monospace;
  font-size: 13px;
  letter-spacing: 0.06em;
  background: var(--line2);
  padding: 6px 10px;
  border-radius: 6px;
}
.sec-enable {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-top: 8px;
}
.sec-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));
  gap: 14px;
  margin-bottom: 16px;
}
.sec-item {
  background: var(--bg);
  border: 1px solid var(--line);
  border-radius: 9px;
  padding: 14px 16px;
}
.sec-item .lbl {
  font-size: 12.5px;
  color: var(--ink3);
}
.sec-item .val {
  font-size: 16px;
  font-weight: 500;
  color: var(--ink);
  margin-top: 6px;
  display: flex;
  align-items: center;
  gap: 8px;
}
.sec-actions {
  display: flex;
  gap: 12px;
}
.sec-notes {
  margin: 0;
  padding-left: 18px;
  font-size: 13px;
  color: var(--ink2);
  line-height: 2;
}
.sec-notes code {
  font-family: monospace;
  background: var(--line2);
  padding: 1px 5px;
  border-radius: 4px;
}
.sec-codes {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 10px;
  padding: 14px;
  background: var(--bg);
  border: 1px dashed var(--line);
  border-radius: 9px;
}
.sec-codes code {
  font-family: monospace;
  font-size: 15px;
  letter-spacing: 0.08em;
  color: var(--ink);
  text-align: center;
  padding: 6px 0;
}
</style>
