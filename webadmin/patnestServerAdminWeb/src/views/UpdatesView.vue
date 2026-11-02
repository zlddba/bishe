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

    <el-dialog v-model="logDialog" title="更新日志" width="760px">
      <pre class="log-box">{{ jobLog || '暂无日志' }}</pre>
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

onMounted(load)
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
</style>
