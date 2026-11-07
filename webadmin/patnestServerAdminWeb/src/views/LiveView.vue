<template>
  <div class="page-container">
    <div class="page-h">
      <div><h2>直播管理</h2><p>查看机构直播、强制下播、封禁直播权限与弹幕治理</p></div>
      <div class="ml-auto filter-bar">
        <el-select v-model="query.status" placeholder="全部状态" clearable style="width:130px" @change="reload">
          <el-option label="直播中" value="living" />
          <el-option label="已结束" value="ended" />
          <el-option label="已封禁" value="banned" />
        </el-select>
        <el-input
          v-model="query.keyword"
          placeholder="机构名 / 直播标题"
          clearable
          style="width:220px"
          @keyup.enter="reload"
          @clear="reload"
        />
        <el-button type="primary" @click="reload">查询</el-button>
      </div>
    </div>

    <div class="table-card">
      <el-table :data="rooms" v-loading="loading" stripe @row-click="openDetail" style="cursor:pointer">
        <el-table-column prop="id" label="ID" width="70" />
        <el-table-column prop="sitter_name" label="机构" min-width="140" />
        <el-table-column prop="title" label="直播标题" min-width="180" show-overflow-tooltip />
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="statusType(row.status)" size="small">{{ statusText(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="在线 / 峰值 / 累计" width="170">
          <template #default="{ row }">
            {{ row.viewer_count }} / {{ row.peak_viewers }} / {{ row.total_views }}
          </template>
        </el-table-column>
        <el-table-column prop="started_at" label="开播时间" min-width="170">
          <template #default="{ row }">{{ fmt(row.started_at) }}</template>
        </el-table-column>
        <el-table-column label="结束原因" min-width="130">
          <template #default="{ row }">{{ endReasonText(row.end_reason) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="200" fixed="right">
          <template #default="{ row }">
            <el-button size="small" @click.stop="openDetail(row)">详情</el-button>
            <el-button
              size="small"
              type="warning"
              :disabled="row.status !== 'living'"
              @click.stop="stopRoom(row)"
            >下播</el-button>
            <el-button size="small" type="danger" @click.stop="banRoom(row)">封禁</el-button>
          </template>
        </el-table-column>
      </el-table>
      <div class="pagination-wrap">
        <el-pagination
          v-model:current-page="query.page"
          v-model:page-size="query.per_page"
          :total="total"
          :page-sizes="[10,20,50]"
          layout="total, sizes, prev, pager, next"
          @change="reload"
        />
      </div>
    </div>

    <el-dialog v-model="detailVisible" title="直播详情" width="760px" destroy-on-close @closed="onDialogClosed">
      <div v-if="detail" class="live-detail">
        <div class="live-player">
          <video ref="videoEl" controls playsinline style="width:100%;max-height:380px;background:#000" />
          <div v-if="!detail.room.play_hls" class="player-tip">该场直播没有可播放地址</div>
          <div v-else-if="!hlsReady" class="player-tip">当前浏览器不支持 HLS，可点击下方地址用外部播放器打开</div>
        </div>

        <el-descriptions :column="2" border size="small" style="margin-top:14px">
          <el-descriptions-item label="机构">{{ detail.room.sitter_name }}</el-descriptions-item>
          <el-descriptions-item label="标题">{{ detail.room.title }}</el-descriptions-item>
          <el-descriptions-item label="状态">
            <el-tag :type="statusType(detail.room.status)" size="small">{{ statusText(detail.room.status) }}</el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="在线人数">{{ detail.room.viewer_count }}</el-descriptions-item>
          <el-descriptions-item label="开播">{{ fmt(detail.room.started_at) }}</el-descriptions-item>
          <el-descriptions-item label="结束">{{ detail.room.ended_at ? fmt(detail.room.ended_at) : '—' }}</el-descriptions-item>
          <el-descriptions-item label="累计观看">{{ detail.room.total_views }}</el-descriptions-item>
          <el-descriptions-item label="结束原因">{{ endReasonText(detail.room.end_reason) }}</el-descriptions-item>
          <el-descriptions-item label="播放地址 (HLS)" :span="2">
            <span class="mono">{{ detail.room.play_hls || '—' }}</span>
            <el-button v-if="detail.room.play_hls" link type="primary" @click="copy(detail.room.play_hls)">复制</el-button>
            <el-button v-if="detail.room.play_hls" link type="primary" @click="openExternal(detail.room.play_hls)">外部打开</el-button>
          </el-descriptions-item>
          <el-descriptions-item label="推流地址 (RTMP)" :span="2">
            <span class="mono">{{ detail.room.push_url || '直播已结束，推流地址已失效' }}</span>
            <el-button v-if="detail.room.push_url" link type="primary" @click="copy(detail.room.push_url)">复制</el-button>
          </el-descriptions-item>
          <el-descriptions-item v-if="detail.sitter_banned" label="直播封禁" :span="2">
            <el-tag type="danger" size="small">已封禁</el-tag>
            <span class="ban-reason">{{ detail.live_ban_reason }}</span>
            <el-button link type="primary" @click="unban(detail.room.sitter_id)">解除封禁</el-button>
          </el-descriptions-item>
        </el-descriptions>

        <h4 class="msg-title">弹幕（{{ detail.messages.length }}）</h4>
        <div class="msg-list">
          <div v-for="m in detail.messages" :key="m.id" class="msg-item" :class="{ hidden: m.status === 'hidden' }">
            <span class="msg-user">{{ m.nickname }}</span>
            <span class="msg-content">{{ m.content }}</span>
            <el-button
              link
              type="primary"
              size="small"
              @click="toggleMessage(m)"
            >{{ m.status === 'hidden' ? '恢复' : '隐藏' }}</el-button>
          </div>
          <div v-if="!detail.messages.length" class="msg-empty">暂无弹幕</div>
        </div>
      </div>

      <template #footer>
        <el-button @click="detailVisible = false">关闭</el-button>
        <el-button
          type="warning"
          :disabled="!detail || detail.room.status !== 'living'"
          @click="stopRoom(detail.room)"
        >强制下播</el-button>
        <el-button type="danger" @click="banRoom(detail.room)">封禁该机构直播</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { onBeforeUnmount, nextTick, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { adminApi } from '@/api'

const rooms = ref([])
const total = ref(0)
const loading = ref(false)
const query = ref({ page: 1, per_page: 20, status: '', keyword: '' })

const detailVisible = ref(false)
const detail = ref(null)
const videoEl = ref(null)
const hlsReady = ref(false)
let hls = null

function fmt(v) {
  if (!v) return '—'
  return String(v).replace('T', ' ').slice(0, 19)
}
function statusText(s) {
  return { living: '直播中', ended: '已结束', banned: '已封禁' }[s] || s || '—'
}
function statusType(s) {
  return { living: 'success', ended: 'info', banned: 'danger' }[s] || 'info'
}
function endReasonText(r) {
  return { host_stop: '机构下播', admin_stop: '平台下播', banned: '违规封禁', timeout: '超时结束' }[r] || '—'
}

async function reload() {
  loading.value = true
  try {
    const res = await adminApi.getLiveRooms(query.value)
    rooms.value = res.data.items || []
    total.value = res.data.total || 0
  } catch (e) {
    ElMessage.error(e.message || '加载失败')
  } finally {
    loading.value = false
  }
}

function destroyPlayer() {
  if (hls) {
    hls.destroy()
    hls = null
  }
  const video = videoEl.value
  if (video) {
    video.pause()
    video.removeAttribute('src')
    video.load()
  }
  hlsReady.value = false
}

/** 关弹窗就停流：否则上一场的 hls 实例还在后台拉流，下次打开先闪上一场的画面 */
function onDialogClosed() {
  destroyPlayer()
  detail.value = null
}

async function openDetail(row) {
  try {
    const res = await adminApi.getLiveDetail(row.id)
    detail.value = res.data
    detailVisible.value = true
    await nextTick()
    await setupPlayer(res.data.room.play_hls)
  } catch (e) {
    ElMessage.error(e.message || '加载详情失败')
  }
}

async function setupPlayer(url) {
  destroyPlayer()
  if (!url || !videoEl.value) return
  const video = videoEl.value
  if (video.canPlayType('application/vnd.apple.mpegurl')) {
    video.src = url // Safari 原生支持 HLS
    hlsReady.value = true
    return
  }
  try {
    const mod = await import('hls.js')
    const Hls = mod.default || mod
    if (!Hls.isSupported()) return
    hls = new Hls({
      lowLatencyMode: true,
      // 只留 2 个目标切片、最多落后 4 个；追帧时允许 1.5 倍速，延迟能压到 2-3s
      liveSyncDurationCount: 2,
      liveMaxLatencyDurationCount: 4,
      maxLiveSyncPlaybackRate: 1.5,
      backBufferLength: 4,
    })
    hls.loadSource(url)
    hls.attachMedia(video)
    hlsReady.value = true
  } catch {
    hlsReady.value = false
  }
}

async function stopRoom(row) {
  if (!row) return
  try {
    const { value } = await ElMessageBox.prompt('强制下播后直播立即结束，可填写原因（选填）', '强制下播', {
      confirmButtonText: '确认下播',
      cancelButtonText: '取消',
      inputPlaceholder: '例如：内容违规',
    })
    await adminApi.stopLive(row.id, { reason: value || '' })
    ElMessage.success('已下播')
    detailVisible.value = false
    reload()
  } catch (e) {
    if (e !== 'cancel') ElMessage.error(e.message || '操作失败')
  }
}

async function banRoom(row) {
  if (!row) return
  try {
    const { value } = await ElMessageBox.prompt('封禁后该机构无法再开播，当前直播会立即终止', '封禁直播', {
      confirmButtonText: '确认封禁',
      cancelButtonText: '取消',
      inputPlaceholder: '请填写封禁原因（必填）',
      inputValidator: (v) => (v && v.trim() ? true : '请填写封禁原因'),
    })
    await adminApi.banLive(row.id, { reason: value.trim() })
    ElMessage.success('已封禁该机构直播')
    detailVisible.value = false
    reload()
  } catch (e) {
    if (e !== 'cancel') ElMessage.error(e.message || '操作失败')
  }
}

async function unban(sitterId) {
  try {
    await adminApi.setSitterLiveBan(sitterId, { banned: false })
    ElMessage.success('已解除封禁')
    detailVisible.value = false
    reload()
  } catch (e) {
    ElMessage.error(e.message || '操作失败')
  }
}

async function toggleMessage(m) {
  try {
    await adminApi.hideLiveMessage(m.id, { hidden: m.status !== 'hidden' })
    m.status = m.status === 'hidden' ? 'visible' : 'hidden'
  } catch (e) {
    ElMessage.error(e.message || '操作失败')
  }
}

function copy(text) {
  navigator.clipboard?.writeText(text).then(
    () => ElMessage.success('已复制'),
    () => ElMessage.warning('复制失败，请手动选择'),
  )
}
function openExternal(url) {
  window.open(url, '_blank', 'noopener')
}

onBeforeUnmount(destroyPlayer)
reload()
</script>

<style scoped>
.filter-bar { display: flex; gap: 10px; align-items: center; }
.live-player { position: relative; background: #000; border-radius: 10px; overflow: hidden; }
.player-tip { position: absolute; inset: 0; display: flex; align-items: center; justify-content: center; color: #bbb; font-size: 13px; }
.mono { font-family: ui-monospace, SFMono-Regular, Menlo, monospace; font-size: 12px; word-break: break-all; }
.ban-reason { margin: 0 10px; color: #b42318; font-size: 12px; }
.msg-title { margin: 18px 0 8px; font-size: 14px; color: var(--ink, #1f2937); }
.msg-list { max-height: 220px; overflow: auto; border: 1px solid var(--line, #eee); border-radius: 8px; padding: 6px 10px; }
.msg-item { display: flex; gap: 8px; align-items: center; padding: 5px 0; font-size: 13px; border-bottom: 1px dashed #f0f0f0; }
.msg-item:last-child { border-bottom: none; }
.msg-item.hidden { opacity: .45; }
.msg-user { color: #6b7280; min-width: 120px; }
.msg-content { flex: 1; color: #111827; }
.msg-empty { color: #9ca3af; font-size: 13px; padding: 12px 0; text-align: center; }
</style>
