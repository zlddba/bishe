import { computed, onMounted, onUnmounted, ref } from 'vue'
import { systemApi } from '@/api'

const CURRENT_VERSION = typeof __APP_VERSION__ !== 'undefined' ? __APP_VERSION__ : 'dev'
const CURRENT_COMMIT = typeof __APP_COMMIT__ !== 'undefined' ? __APP_COMMIT__ : 'unknown'
const CURRENT_BUILD = typeof __APP_BUILD_ID__ !== 'undefined' ? __APP_BUILD_ID__ : 'dev'

const version = ref(CURRENT_VERSION)
const commit = ref(CURRENT_COMMIT)
const buildId = ref(CURRENT_BUILD)
const hasUpdate = ref(false)
const latestBuild = ref('')
const checking = ref(false)
const updateError = ref('')

// 后端实际运行的版本：热更新只换了后端镜像时，页面构建版本会落后，这里以服务端为准。
const backendVersion = ref('')
const backendCommit = ref('')
const backendSlot = ref('')
const backendBuildTime = ref('')

const displayVersion = computed(() => backendVersion.value || version.value)
const displayCommit = computed(() => {
  const sha = backendCommit.value && backendCommit.value !== 'unknown' ? backendCommit.value : commit.value
  return backendSlot.value ? `${backendSlot.value} · ${sha}` : sha
})

let timer = null
let users = 0

async function loadBackendVersion() {
  try {
    const res = await systemApi.getVersion()
    const data = res.data || {}
    backendVersion.value = data.version || ''
    backendCommit.value = data.commit || ''
    backendSlot.value = data.slot || ''
    backendBuildTime.value = data.build_time || ''
  } catch {
    // 拿不到后端版本时保留页面构建版本，不影响其他功能
  }
}

async function checkVersion() {
  checking.value = true
  updateError.value = ''
  loadBackendVersion()
  try {
    const res = await fetch(`${import.meta.env.BASE_URL}version.json?t=${Date.now()}`, { cache: 'no-store' })
    if (!res.ok) throw new Error(`HTTP ${res.status}`)
    const data = await res.json()
    latestBuild.value = data.build_id || ''
    hasUpdate.value = Boolean(data.build_id) && data.build_id !== CURRENT_BUILD
  } catch (e) {
    updateError.value = e.message || '检测失败'
  } finally {
    checking.value = false
  }
}

function onWindowFocus() {
  loadBackendVersion()
}

function applyUpdate() {
  window.location.reload()
}

/**
 * 轮询站点根目录的 version.json：构建产物换了 build_id 就提示刷新，
 * 同时保证同一页面内只存在一个定时器。
 */
export function useAppVersion(interval = 60000) {
  onMounted(() => {
    users += 1
    if (users === 1) {
      checkVersion()
      timer = setInterval(checkVersion, interval)
      window.addEventListener('focus', onWindowFocus)
    }
  })

  onUnmounted(() => {
    users = Math.max(0, users - 1)
    if (users === 0 && timer) {
      clearInterval(timer)
      timer = null
      window.removeEventListener('focus', onWindowFocus)
    }
  })

  return {
    version,
    commit,
    buildId,
    backendVersion,
    backendCommit,
    backendSlot,
    backendBuildTime,
    displayVersion,
    displayCommit,
    hasUpdate,
    latestBuild,
    checking,
    updateError,
    checkVersion,
    applyUpdate,
  }
}
