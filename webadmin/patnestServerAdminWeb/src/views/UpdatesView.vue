<template>
  <div class="page-container">
    <div class="page-h">
      <div>
        <h2>系统更新</h2>
        <p>上传更新包，或扫描服务器目录后一键发布</p>
      </div>
      <div class="ml-auto">
        <el-button :icon="Refresh" :loading="loading" @click="load">刷新</el-button>
      </div>
    </div>

    <el-alert
      type="warning"
      :closable="false"
      show-icon
      title="更新会重建镜像并切换流量，请只应用自己构建并确认过的 zip 包。"
      description="后端使用蓝绿发布；管理页面会重建 Nginx。数据库迁移随后端发布自动执行，旧镜像请保留以便回滚。"
      style="margin-bottom:16px"
    />

    <div class="up-summary">
      <div class="summary-item">
        <div class="summary-label">当前后端版本</div>
        <div class="summary-value">v{{ system.api?.version || '-' }}</div>
        <div class="summary-sub">{{ system.api?.slot || '-' }} 槽位</div>
      </div>
      <div class="summary-item">
        <div class="summary-label">更新代理</div>
        <div class="summary-value">
          <span class="pn-tag" :class="info.agent_online ? 'pn-tag-ok' : 'pn-tag-danger'">
            {{ info.agent_online ? '在线' : '离线' }}
          </span>
        </div>
        <div class="summary-sub">{{ info.agent_pid ? `进程号 ${info.agent_pid}` : '未启动' }}</div>
      </div>
      <div class="summary-item">
        <div class="summary-label">可用更新包</div>
        <div class="summary-value">{{ packages.length }}</div>
        <div class="summary-sub">上传目录 + 服务器目录</div>
      </div>
      <div class="summary-item">
        <div class="summary-label">最近任务</div>
        <div class="summary-value">{{ jobs.length }}</div>
        <div class="summary-sub">{{ activeJob ? '有任务执行中' : '当前空闲' }}</div>
      </div>
    </div>

    <div class="update-grid">
      <section class="panel">
        <div class="panel-h">
          <div>
            <h3>方式一：上传更新包</h3>
            <p>上传到 {{ info.tmp_hint || 'data/updates/tmp' }}，再由宿主机代理发布</p>
          </div>
        </div>
        <div class="panel-b">
          <el-upload
            drag
            accept=".zip"
            :show-file-list="false"
            :disabled="uploading"
            :http-request="uploadPackage"
          >
            <el-icon class="upload-icon"><UploadFilled /></el-icon>
            <div class="el-upload__text">把 zip 拖到这里，或 <em>点击选择</em></div>
            <template #tip>
              <div class="el-upload__tip">只接受构建脚本生成的 patnest 更新包，最大 {{ maxMB }} MB</div>
            </template>
          </el-upload>
          <el-progress v-if="uploading" :percentage="progress" :stroke-width="8" style="margin-top:16px" />
          <div v-if="uploaded" class="upload-result">
            <el-icon><CircleCheck /></el-icon>
            <div>
              <strong>v{{ uploaded.version }}</strong>
              <span>{{ uploaded.name }}</span>
            </div>
            <el-button size="small" type="primary" @click="apply(uploaded)">应用更新</el-button>
          </div>
        </div>
      </section>

      <section class="panel">
        <div class="panel-h">
          <div>
            <h3>方式二：服务器目录</h3>
            <p>把 zip 放到 {{ info.manual_hint || 'data/updates/manual' }}</p>
          </div>
          <el-button class="ml-auto" size="small" :icon="Refresh" :loading="scanning" @click="scan">检查更新</el-button>
        </div>
        <div class="panel-b">
          <div class="dir-hint">
            <el-icon><FolderOpened /></el-icon>
            <span>{{ info.manual_hint || 'data/updates/manual' }}</span>
          </div>
          <div v-if="!packages.length" class="empty-box">目录里还没有可用的更新包</div>
          <div v-else class="pkg-list">
            <div v-for="pkg in packages" :key="pkg.name" class="pkg-row">
              <div class="pkg-main">
                <div class="pkg-title">
                  <span class="pn-tag" :class="pkg.valid ? 'pn-tag-brand' : 'pn-tag-danger'">
                    v{{ pkg.version || '未知' }}
                  </span>
                  <span class="pkg-name">{{ pkg.name }}</span>
                </div>
                <div class="pkg-meta">
                  <span>{{ pkg.source === 'manual' ? '服务器目录' : '页面上传' }}</span>
                  <span>{{ targetsLabel(pkg.targets) }}</span>
                  <span>{{ formatSize(pkg.size) }}</span>
                  <span>{{ formatTime(pkg.modified) }}</span>
                </div>
                <div v-if="!pkg.valid" class="pkg-error">{{ pkg.error }}</div>
              </div>
              <el-button
                type="primary"
                size="small"
                :disabled="!pkg.valid || Boolean(activeJob)"
                @click="apply(pkg)"
              >
                应用
              </el-button>
            </div>
          </div>
        </div>
      </section>
    </div>

    <section class="panel">
      <div class="panel-h">
        <div>
          <h3>更新任务</h3>
          <p>由宿主机更新代理执行，页面每 3 秒刷新</p>
        </div>
        <span class="ml-auto pn-tag" :class="info.agent_online ? 'pn-tag-ok' : 'pn-tag-danger'">
          {{ info.agent_online ? '代理在线' : '代理离线' }}
        </span>
      </div>
      <el-table :data="jobs" size="small" empty-text="还没有更新任务">
        <el-table-column prop="version" label="版本" width="150">
          <template #default="{ row }">v{{ row.version }}</template>
        </el-table-column>
        <el-table-column label="内容" width="190">
          <template #default="{ row }">{{ targetsLabel(row.targets) }}</template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <span class="pn-tag" :class="jobTagClass(row.state)">{{ jobStateLabel(row.state) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="阶段" width="120">
          <template #default="{ row }">{{ stepLabel(row.step) }}</template>
        </el-table-column>
        <el-table-column label="说明" min-width="220" show-overflow-tooltip>
          <template #default="{ row }">{{ messageLabel(row.message) }}</template>
        </el-table-column>
        <el-table-column prop="created_at" label="创建时间" width="170">
          <template #default="{ row }">{{ formatTime(row.created_at) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="90" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="showLog(row)">日志</el-button>
          </template>
        </el-table-column>
      </el-table>
    </section>

    <!-- HTTPS 证书：后端 9200 端口自己终结 TLS，443 已关闭 -->
    <section class="tls-section">
      <div class="tls-head">
        <div>
          <h3>HTTPS 证书</h3>
          <p>上传证书链与私钥，保存后立即生效；证书用于后端自己的 HTTPS 监听</p>
        </div>
        <el-button :loading="tlsLoading" @click="loadTLS">刷新状态</el-button>
      </div>

      <el-descriptions :column="3" border size="small" class="tls-desc">
        <el-descriptions-item label="当前状态">
          <span class="pn-tag" :class="tls.configured ? 'pn-tag-ok' : 'pn-tag-soft'">
            {{ tls.configured ? '已配置' : '未配置（仅 HTTP）' }}
          </span>
        </el-descriptions-item>
        <el-descriptions-item label="域名">{{ (tls.domains || []).join(', ') || '-' }}</el-descriptions-item>
        <el-descriptions-item label="有效期">
          <span v-if="tls.not_after">{{ tls.not_after }}（剩 {{ tls.days_left }} 天）</span>
          <span v-else>-</span>
        </el-descriptions-item>
        <el-descriptions-item label="签发者">{{ tls.issuer || '-' }}</el-descriptions-item>
        <el-descriptions-item label="证书目录" :span="2">{{ tls.dir || '-' }}</el-descriptions-item>
      </el-descriptions>

      <!-- Let's Encrypt：镜像内置 acme.sh，走 Cloudflare DNS-01 验证，续期由后端 worker 负责 -->
      <div class="tls-acme">
        <div class="tls-acme-head">
          <div>
            <div class="tls-acme-title">
              Let's Encrypt 自动证书
              <span class="pn-tag" :class="acme.managed ? 'pn-tag-ok' : 'pn-tag-soft'">
                {{ acme.managed ? '已接管' : '未启用' }}
              </span>
              <span v-if="acme.version" class="pn-tag pn-tag-soft">acme.sh {{ acme.version }}</span>
            </div>
            <p class="tls-acme-desc">
              服务器镜像里已经带了 acme.sh，不用另外装 certbot。验证走 Cloudflare 的 DNS 记录，
              不需要开放 80 端口；签完和手工上传落在同一个证书目录，nginx 会自己重载。
            </p>
          </div>
          <el-button :loading="acmeChecking" @click="checkACME">验证 Token</el-button>
        </div>

        <el-form label-width="88px" class="tls-acme-form">
          <el-form-item label="域名">
            <el-input v-model="acmeForm.domain" placeholder="www.example.com" style="width:300px" />
            <span class="tls-hint">多个域名用英文逗号分隔，第一个作为主名</span>
          </el-form-item>
          <el-form-item label="CF Token">
            <el-input
              v-model="acmeForm.token"
              type="password"
              show-password
              :placeholder="acme.token_set ? '已保存，留空表示不修改' : 'Cloudflare API Token'"
              style="width:300px"
            />
            <span class="tls-hint">权限要有 Zone:Zone:Read 与 Zone:DNS:Edit</span>
          </el-form-item>
          <el-form-item label="邮箱">
            <el-input v-model="acmeForm.email" placeholder="选填，用于接收到期提醒" style="width:300px" />
          </el-form-item>
          <el-form-item label="自动续期">
            <el-switch v-model="acmeForm.auto_renew" :loading="acmeSaving" @change="saveACMESettings" />
            <span class="tls-hint">剩余 {{ acmeForm.renew_days }} 天时自动续，每 12 小时检查一次</span>
          </el-form-item>
          <el-form-item label="测试环境">
            <el-switch v-model="acmeForm.staging" />
            <span class="tls-hint">先签测试证书验证流程（浏览器不信任），避免撞上每周 5 张的频率限制</span>
          </el-form-item>
        </el-form>

        <div class="tls-actions">
          <el-button type="primary" :loading="acmeIssuing" :disabled="!acme.available" @click="issueACME">
            申请 / 更新证书
          </el-button>
          <el-button :loading="acmeRenewing" :disabled="!acme.managed" @click="renewACME">立即续期</el-button>
          <el-button v-if="acme.last_run" link @click="showACMELog">上次执行日志</el-button>
          <span v-if="!acme.available" class="tls-hint tls-backend-warn">
            当前镜像里没有 acme.sh，请重建 api 镜像（Dockerfile.api 会从 vendor/acme.sh 带进去）
          </span>
        </div>
      </div>

      <!-- 后端自己终结 TLS：开关一拨就生效，不用重启、不用改 .env -->
      <div class="tls-backend">
        <div class="tls-backend-text">
          <div class="tls-backend-title">
            后端同时接受 HTTPS
            <span class="pn-tag" :class="backendTLSOn ? 'pn-tag-ok' : 'pn-tag-soft'">
              {{ backendTLSOn ? '已开启' : '未开启' }}
            </span>
          </div>
          <p class="tls-backend-desc">
            开启后后端自己的端口（9200）同时接受 http 与 https，客户端可以直接用
            <code>https://{{ (tls.domains || [])[0] || '你的域名' }}:9200</code> 访问。
            站点只监听 9200，不再开放 443。切换即时生效，不需要重启服务。
          </p>
          <p v-if="!backendTLSAvailable" class="tls-backend-desc tls-backend-warn">
            还没有可用证书，请先在上面上传证书链与私钥。
          </p>
        </div>
        <el-switch
          v-model="backendTLSOn"
          :loading="backendTLSSaving"
          :disabled="!backendTLSAvailable"
          active-text="开启"
          inactive-text="关闭"
          @change="toggleBackendTLS"
        />
      </div>

      <el-alert
        v-if="tls.configured && tls.days_left !== undefined && tls.days_left < 15"
        type="warning"
        :closable="false"
        show-icon
        :title="`证书将在 ${tls.days_left} 天后过期，请尽快续期后重新上传`"
        style="margin-bottom:12px"
      />

      <el-form label-width="96px">
        <el-form-item label="证书链">
          <el-input
            v-model="tlsForm.cert"
            type="textarea"
            :rows="5"
            placeholder="-----BEGIN CERTIFICATE-----&#10;站点证书在前、中间证书在后，按顺序拼接（缺中间证书会报链不完整）"
          />
        </el-form-item>
        <el-form-item label="私钥">
          <el-input
            v-model="tlsForm.key"
            type="textarea"
            :rows="4"
            placeholder="-----BEGIN PRIVATE KEY-----"
          />
        </el-form-item>
        <el-form-item label="域名">
          <el-input
            v-model="tlsForm.server_name"
            placeholder="留空则取证书里的第一个域名"
            style="width:320px"
          />
        </el-form-item>
      </el-form>

      <div class="tls-actions">
        <el-button type="primary" :loading="tlsSaving" @click="saveTLS">保存并生效</el-button>
        <el-button
          v-if="tls.configured"
          type="danger"
          plain
          :loading="tlsSaving"
          @click="removeTLS"
        >移除证书</el-button>
        <span class="tls-hint">
          保存后立即生效：证书是握手时现读的，新连接立刻用新证书，不需要重启。
        </span>
      </div>
    </section>

    <el-dialog v-model="logDialog" title="更新日志" width="760px">
      <pre class="log-box">{{ jobLog || '暂无日志' }}</pre>
    </el-dialog>

    <!-- acme.sh 的原始输出：签发失败时这里会告诉到底是 DNS、80 端口还是速率限制的问题 -->
    <el-dialog v-model="acmeLogDialog" title="acme.sh 执行记录" width="760px">
      <pre class="log-box">{{ acmeLogText || '暂无日志' }}</pre>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, onUnmounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Refresh, UploadFilled, CircleCheck, FolderOpened } from '@element-plus/icons-vue'
import { adminApi } from '@/api'

const maxMB = 512
const loading = ref(false)
const scanning = ref(false)
const uploading = ref(false)
const progress = ref(0)
const uploaded = ref(null)
const packages = ref([])
const jobs = ref([])
const info = reactive({
  manual_hint: 'data/updates/manual',
  tmp_hint: 'data/updates/tmp',
  agent_online: false,
  agent_pid: '',
  agent_heartbeat: '',
})
const system = reactive({ api: {} })
const logDialog = ref(false)
const jobLog = ref('')
let pollTimer = null

const activeJob = computed(() => jobs.value.find(j => j.state === 'queued' || j.state === 'running'))

function errorText(e) {
  return e?.response?.data?.message || e?.message || '操作失败'
}

async function load() {
  loading.value = true
  try {
    const [updateRes, systemRes] = await Promise.all([
      adminApi.getUpdates(),
      adminApi.getSystemInfo(),
    ])
    const data = updateRes.data || {}
    Object.assign(info, data.info || {})
    packages.value = data.packages || []
    jobs.value = data.jobs || []
    Object.assign(system, systemRes.data || {})
    if (activeJob.value) startPolling()
  } catch (e) {
    ElMessage.error(errorText(e))
  } finally {
    loading.value = false
  }
}

async function scan() {
  scanning.value = true
  try {
    const res = await adminApi.scanUpdates()
    const data = res.data || {}
    Object.assign(info, data.info || {})
    packages.value = data.packages || []
    ElMessage.success(`扫描完成，发现 ${data.scanned || 0} 个更新包`)
  } catch (e) {
    ElMessage.error(errorText(e))
  } finally {
    scanning.value = false
  }
}

async function uploadPackage(options) {
  uploading.value = true
  progress.value = 0
  uploaded.value = null
  try {
    const res = await adminApi.uploadUpdate(options.file, (event) => {
      if (event.total) progress.value = Math.min(99, Math.round((event.loaded * 100) / event.total))
    })
    progress.value = 100
    uploaded.value = res.data
    ElMessage.success(`更新包 v${res.data.version} 上传成功`)
    options.onSuccess?.(res)
    await load()
  } catch (e) {
    ElMessage.error(errorText(e))
    options.onError?.(e)
  } finally {
    uploading.value = false
  }
}

async function apply(pkg) {
  if (!pkg?.valid) return ElMessage.error('更新包无效')
  if (activeJob.value) return ElMessage.warning('已有更新任务正在执行')
  try {
    await ElMessageBox.confirm(
      `将应用 v${pkg.version}（${targetsLabel(pkg.targets)}）。后端会蓝绿切换，管理页面会短暂重建，确定继续？`,
      '确认更新',
      { type: 'warning', confirmButtonText: '开始更新', cancelButtonText: '取消' }
    )
  } catch {
    return
  }
  try {
    const res = await adminApi.applyUpdate({ file: pkg.name, targets: pkg.targets })
    ElMessage.success('更新任务已排队')
    jobs.value.unshift(res.data)
    startPolling()
  } catch (e) {
    ElMessage.error(errorText(e))
  }
}

function startPolling() {
  if (pollTimer) return
  pollTimer = setInterval(refreshJobs, 3000)
}

function stopPolling() {
  if (pollTimer) clearInterval(pollTimer)
  pollTimer = null
}

async function refreshJobs() {
  try {
    const res = await adminApi.getUpdates()
    const data = res.data || {}
    Object.assign(info, data.info || {})
    packages.value = data.packages || []
    jobs.value = data.jobs || []
    if (!activeJob.value) stopPolling()
  } catch {
    // 发布切流量瞬间可能失败，继续保持轮询。
  }
}

async function showLog(job) {
  logDialog.value = true
  jobLog.value = '加载中...'
  try {
    const res = await adminApi.getUpdateJob(job.id)
    jobLog.value = res.data?.log || '暂无日志'
  } catch (e) {
    jobLog.value = errorText(e)
  }
}

function targetsLabel(targets) {
  const map = { api: '后端', web: '管理页面', db: '数据库迁移' }
  return (targets || []).map(t => map[t] || t).join(' + ') || '-'
}

function jobStateLabel(state) {
  return { queued: '排队中', running: '执行中', success: '成功', failed: '失败' }[state] || state
}

function jobTagClass(state) {
  return {
    queued: 'pn-tag-soft',
    running: 'pn-tag-warn',
    success: 'pn-tag-ok',
    failed: 'pn-tag-danger',
  }[state] || 'pn-tag-soft'
}

const STEP_LABELS = {
  queued: '排队',
  backup: '备份',
  copy: '复制文件',
  'release-api': '发布后端',
  'release-web': '发布页面',
  done: '完成',
  failed: '失败',
  interrupted: '中断',
}

const MESSAGE_LABELS = {
  'Backup current release': '备份当前版本',
  'Copy update files': '复制更新文件',
  'Build backend and release (blue-green)': '构建后端并执行蓝绿发布',
  'Rebuild admin panel': '重建管理页面',
  'Update finished': '更新完成',
  'Update agent restarted; task interrupted and previous files restored': '更新代理重启，任务已中断并恢复旧文件',
  'Invalid version': '版本号无效',
  'Staging path must be relative': '暂存目录必须是相对路径',
  'Staging path escapes update root': '暂存目录越界',
  'Staging directory not found': '暂存目录不存在',
  'Waiting for host update agent': '等待服务器更新代理',
}

function stepLabel(step) {
  return STEP_LABELS[step] || step || '-'
}

function messageLabel(message) {
  if (!message) return '-'
  if (MESSAGE_LABELS[message]) return MESSAGE_LABELS[message]
  // 更新代理回传的镜像构建输出，统一转成中文说明
  const building = /^Image (\S+) Building$/.exec(message)
  if (building) return `正在构建镜像 ${building[1]}`
  return message
}

function formatSize(size) {
  if (!size) return '-'
  if (size > 1024 * 1024) return `${(size / 1024 / 1024).toFixed(1)} MB`
  return `${Math.max(1, Math.round(size / 1024))} KB`
}

function formatTime(value) {
  if (!value) return '-'
  const d = new Date(value)
  if (Number.isNaN(d.getTime())) return value
  return d.toLocaleString('zh-CN', { hour12: false })
}

/* ---------- HTTPS 证书 ---------- */
const tls = ref({})
const tlsLoading = ref(false)
const tlsSaving = ref(false)
const tlsForm = reactive({ cert: '', key: '', server_name: '' })

// 后端自身是否接收 HTTPS：开关在页面上拨，服务端即时生效
const backendTLSOn = ref(false)
const backendTLSAvailable = ref(false)
const backendTLSSaving = ref(false)

async function loadTLS() {
  tlsLoading.value = true
  try {
    const res = await adminApi.getTLS()
    tls.value = res.data || {}
    const bt = tls.value.backend_tls || {}
    backendTLSOn.value = !!bt.enabled
    backendTLSAvailable.value = !!bt.available
  } catch (e) {
    // 证书未配置时接口也返回 200；这里出错多半是权限或网络，不额外打扰
  } finally {
    tlsLoading.value = false
  }
}

// 切换后端自己的 HTTPS 开关。
// 接口失败时把开关拨回原值，不然页面显示的状态和实际不一致。
async function toggleBackendTLS(on) {
  backendTLSSaving.value = true
  try {
    const res = await adminApi.setBackendTLS({ enabled: on })
    ElMessage.success((res.data && res.data.message) || (on ? '已开启' : '已关闭'))
    loadTLS()
  } catch (e) {
    backendTLSOn.value = !on
    ElMessage.error(e.message || '切换失败')
  } finally {
    backendTLSSaving.value = false
  }
}

async function saveTLS() {
  if (!tlsForm.cert.trim() || !tlsForm.key.trim()) {
    return ElMessage.warning('请填写证书链和私钥')
  }
  tlsSaving.value = true
  try {
    await adminApi.uploadTLS({
      cert: tlsForm.cert.trim(),
      key: tlsForm.key.trim(),
      server_name: tlsForm.server_name.trim(),
    })
    ElMessage.success('证书已保存，立即生效')
    tlsForm.cert = ''
    tlsForm.key = ''
    loadTLS()
  } catch (e) {
    ElMessage.error(e.message || '保存失败')
  } finally {
    tlsSaving.value = false
  }
}

async function removeTLS() {
  try {
    await ElMessageBox.confirm(
      '移除后后端不再接受 HTTPS 连接，只能继续用 HTTP 访问（同时会关闭 HTTPS 开关）。确定移除吗？',
      '移除证书',
      { type: 'warning' }
    )
  } catch {
    return
  }
  tlsSaving.value = true
  try {
    await adminApi.removeTLS()
    ElMessage.success('已移除')
    loadTLS()
  } catch (e) {
    ElMessage.error(e.message || '移除失败')
  } finally {
    tlsSaving.value = false
  }
}

/* ---------- Let's Encrypt（镜像内置 acme.sh，Cloudflare DNS-01 验证） ---------- */
const acme = ref({})
const acmeForm = reactive({
  domain: '',
  email: '',
  // Token 只写不读：后端只回 token_set，页面上永远拿不到明文，
  // 留空提交表示沿用已保存的那份。
  token: '',
  auto_renew: true,
  renew_days: 30,
  staging: false,
})
const acmeIssuing = ref(false)
const acmeRenewing = ref(false)
const acmeSaving = ref(false)
const acmeChecking = ref(false)
const acmeLogDialog = ref(false)
const acmeLogText = ref('')

async function loadACME() {
  try {
    const res = await adminApi.getACME()
    acme.value = res.data || {}
    acmeForm.domain = acme.value.domain || (acme.value.cert && acme.value.cert.domains ? acme.value.cert.domains[0] : '') || ''
    acmeForm.email = acme.value.email || ''
    acmeForm.auto_renew = acme.value.auto_renew !== false
    acmeForm.renew_days = acme.value.renew_days || 30
  } catch {
    // 老版本后端没有这组接口时静默失败，手工上传证书那一块照常可用
  }
}

// 失败响应里带着 acme.sh 的原始输出（见后端 FailWithDetail），
// 比只弹一句「签发失败」有用得多。
function acmeErrorPayload(err) {
  return (err && err.response && err.response.data && err.response.data.data) || {}
}

function openACMELog(text) {
  acmeLogText.value = text
  acmeLogDialog.value = true
}

function showACMELog() {
  const r = acme.value.last_run
  if (!r) return openACMELog('暂无日志')
  openACMELog(`${formatTime(r.at)}  ${r.domain || ''}  ${r.ok ? '成功' : '失败'}\n\n${r.output || ''}`)
}

async function issueACME() {
  const domain = acmeForm.domain.trim()
  if (!domain) return ElMessage.warning('请填写域名')
  if (acme.value.cert) {
    const cur = (acme.value.cert.domains || []).join(', ') || '未知域名'
    try {
      await ElMessageBox.confirm(
        `新证书会覆盖当前正在使用的证书（${cur}），确定继续吗？`,
        '申请证书',
        { type: 'warning' }
      )
    } catch {
      return
    }
  }
  acmeIssuing.value = true
  try {
    const res = await adminApi.issueACME({
      domain,
      email: acmeForm.email.trim(),
      token: acmeForm.token.trim(),
      staging: acmeForm.staging,
    })
    const d = res.data || {}
    ElMessage.success(d.message || '证书已签发')
    acmeForm.token = ''
    await Promise.all([loadACME(), loadTLS()])
  } catch (e) {
    const p = acmeErrorPayload(e)
    openACMELog([p.hint || e.message || '签发失败', '', p.output ? `—— acme.sh 输出 ——\n${p.output}` : ''].join('\n').trim())
    loadACME()
    loadTLS()
  } finally {
    acmeIssuing.value = false
  }
}

async function renewACME() {
  acmeRenewing.value = true
  try {
    const res = await adminApi.renewACME({ domain: acmeForm.domain.trim() })
    ElMessage.success((res.data && res.data.message) || '续期完成')
    await Promise.all([loadACME(), loadTLS()])
  } catch (e) {
    const p = acmeErrorPayload(e)
    openACMELog([p.hint || e.message || '续期失败', '', p.output || ''].join('\n').trim())
  } finally {
    acmeRenewing.value = false
  }
}

async function saveACMESettings() {
  acmeSaving.value = true
  try {
    await adminApi.updateACMESettings({
      auto_renew: acmeForm.auto_renew,
      email: acmeForm.email.trim(),
      renew_days: acmeForm.renew_days,
      token: acmeForm.token.trim(),
    })
    ElMessage.success('已保存')
    acmeForm.token = ''
    loadACME()
  } catch (e) {
    ElMessage.error(e.message || '保存失败')
    loadACME()
  } finally {
    acmeSaving.value = false
  }
}

// Cloudflare Token 自检：后端会真的调一次 CF API，
// 顺带告出这个 Token 能管哪些 zone、目标域名是否落在里面。
async function checkACME() {
  const domain = acmeForm.domain.trim()
  if (!domain) return ElMessage.warning('请先填写域名')
  if (!acmeForm.token.trim() && !acme.value.token_set) {
    return ElMessage.warning('请先填写 Cloudflare API Token')
  }
  acmeChecking.value = true
  try {
    const res = await adminApi.checkACME({ domain, token: acmeForm.token.trim() })
    const d = res.data || {}
    if (d.ok) {
      ElMessage.success(d.hint || 'Token 有效，可以开始申请证书')
    } else {
      openACMELog([d.detail || '', d.hint || '', (d.zones || []).length ? `\nToken 可管理的域名：\n${d.zones.join('\n')}` : ''].join('\n').trim())
    }
  } catch (e) {
    ElMessage.error(e.message || '检测失败')
  } finally {
    acmeChecking.value = false
  }
}

onMounted(() => {
  load()
  loadTLS()
  loadACME()
})
onUnmounted(stopPolling)
</script>

<style scoped>
.up-summary {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 14px;
  margin-bottom: 16px;
}
.summary-item {
  background: var(--panel);
  border: 1px solid var(--line);
  border-radius: 8px;
  padding: 16px 18px;
}
.summary-label { font-size: 12.5px; color: var(--ink3); }
.summary-value { font-size: 22px; font-weight: 600; margin: 7px 0 3px; color: var(--ink); }
.summary-sub { font-size: 12px; color: var(--ink3); }

.update-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 16px;
}
.update-grid .panel { margin-bottom: 16px; }
.upload-icon { font-size: 42px; color: var(--brand); margin: 8px 0 4px; }
.upload-result {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-top: 14px;
  padding: 10px 12px;
  border: 1px solid var(--line);
  border-radius: 6px;
  background: var(--line2);
  color: var(--ink2);
}
.upload-result > div { display: flex; flex-direction: column; min-width: 0; }
.upload-result strong { color: var(--ink); font-size: 13px; }
.upload-result span { font-size: 12px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.upload-result .el-button { margin-left: auto; flex-shrink: 0; }
.dir-hint {
  display: flex;
  align-items: center;
  gap: 7px;
  padding: 9px 11px;
  margin-bottom: 10px;
  background: var(--line2);
  border-radius: 6px;
  color: var(--ink2);
  font-size: 12.5px;
  word-break: break-all;
}
.empty-box { padding: 28px; text-align: center; color: var(--ink3); font-size: 13px; }
/* 更新包列表：限制高度，包多时在面板内上下滚动，避免把页面撑得过长 */
.pkg-list {
  max-height: 340px;
  overflow-y: auto;
  padding-right: 6px;
  overscroll-behavior: contain;
}
.pkg-list::-webkit-scrollbar { width: 8px; }
.pkg-list::-webkit-scrollbar-track { background: transparent; }
.pkg-list::-webkit-scrollbar-thumb { background: var(--line); border-radius: 4px; }
.pkg-list::-webkit-scrollbar-thumb:hover { background: var(--ink3); }
.pkg-row {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 11px 0;
  border-bottom: 1px solid var(--line2);
}
.pkg-row:last-child { border-bottom: none; }
.pkg-main { min-width: 0; flex: 1; }
.pkg-title { display: flex; align-items: center; gap: 8px; min-width: 0; }
.pkg-name { font-size: 13px; color: var(--ink); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.pkg-meta { display: flex; flex-wrap: wrap; gap: 10px; margin-top: 5px; font-size: 11.5px; color: var(--ink3); }
.pkg-error { margin-top: 5px; font-size: 12px; color: var(--danger); }
.log-box {
  margin: 0;
  padding: 14px;
  max-height: 480px;
  overflow: auto;
  background: #1B1E23;
  color: #E7E9EC;
  border-radius: 6px;
  font-family: "Cascadia Mono", Consolas, monospace;
  font-size: 12px;
  line-height: 1.6;
  white-space: pre-wrap;
  word-break: break-word;
}

@media (max-width: 1100px) {
  .up-summary { grid-template-columns: repeat(2, minmax(0, 1fr)); }
  .update-grid { grid-template-columns: 1fr; }
}
@media (max-width: 640px) {
  .up-summary { grid-template-columns: 1fr; }
}

/* ---------- HTTPS 证书 ---------- */
.tls-section {
  margin-top: 24px;
  padding: 20px;
  background: #fff;
  border: 1px solid var(--line, #EDE4E7);
  border-radius: 12px;
}
.tls-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 16px;
}
.tls-head h3 {
  margin: 0 0 4px;
  font-size: 16px;
  color: var(--ink, #3A2A2F);
}
.tls-head p {
  margin: 0;
  font-size: 12.5px;
  color: var(--ink3, #9AA3AF);
}
.tls-desc { margin-bottom: 12px; }
.tls-backend {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  padding: 14px 16px;
  margin-bottom: 16px;
  background: #FAF7F8;
  border: 1px solid var(--line, #EDE4E7);
  border-radius: 10px;
}
.tls-backend-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13.5px;
  font-weight: 600;
  color: var(--ink, #3A2A2F);
  margin-bottom: 4px;
}
.tls-backend-desc {
  margin: 0;
  font-size: 12.5px;
  line-height: 1.7;
  color: var(--ink3, #9AA3AF);
}
.tls-backend-desc code {
  padding: 1px 5px;
  background: #fff;
  border: 1px solid var(--line, #EDE4E7);
  border-radius: 4px;
  font-size: 12px;
  color: var(--ink, #3A2A2F);
}
.tls-backend-warn { margin-top: 6px; color: #E6A23C; }

/* Let's Encrypt 自动签发卡片：和手工上传区分开，但读同一份证书状态 */
.tls-acme {
  padding: 14px 16px;
  margin-bottom: 16px;
  background: #FAF7F8;
  border: 1px solid var(--line, #EDE4E7);
  border-radius: 10px;
}
.tls-acme-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 10px;
}
.tls-acme-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13.5px;
  font-weight: 600;
  color: var(--ink, #3A2A2F);
}
.tls-acme-desc {
  margin: 6px 0 0;
  font-size: 12.5px;
  line-height: 1.7;
  color: var(--ink3, #9AA3AF);
}
.tls-acme-form { margin-top: 4px; }
.tls-acme-form .el-form-item { margin-bottom: 10px; }
.tls-acme-form .tls-hint { margin-left: 10px; }
.tls-actions {
  display: flex;
  align-items: center;
  gap: 12px;
}
.tls-hint {
  font-size: 12px;
  color: var(--ink3, #9AA3AF);
}
</style>
