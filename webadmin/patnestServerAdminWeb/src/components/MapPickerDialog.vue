<template>
  <el-dialog
    :model-value="visible"
    title="地图选点"
    width="860px"
    top="6vh"
    append-to-body
    destroy-on-close
    @update:model-value="(v) => emit('update:visible', v)"
    @opened="init"
    @closed="dispose"
  >
    <div class="mp-search">
      <el-input
        v-model="keyword"
        placeholder="搜索地点、街道或门店名称"
        clearable
        @keyup.enter="doSearch"
        @clear="tips = []"
      >
        <template #prefix><el-icon><Search /></el-icon></template>
      </el-input>
      <el-button :loading="searching" @click="doSearch">搜索</el-button>
      <el-tooltip content="回到当前已保存的坐标" placement="top">
        <el-button :icon="Aim" :disabled="!hasSaved" @click="gotoSaved" />
      </el-tooltip>
    </div>

    <div v-if="tips.length" class="mp-tips">
      <button
        v-for="(t, i) in tips"
        :key="`${t.name}-${i}`"
        type="button"
        class="mp-tip"
        @click="pickTip(t)"
      >
        <span class="mp-tip-name">{{ t.name }}</span>
        <span class="mp-tip-addr">{{ [t.district, t.address].filter(Boolean).join(' ') }}</span>
      </button>
    </div>

    <div class="mp-map-wrap">
      <div ref="mapEl" class="mp-map" />
      <div v-if="loadError" class="mp-empty">{{ loadError }}</div>
      <div v-else-if="!picked" class="mp-hint">点击地图放置标记，或直接拖动标记微调</div>
    </div>

    <div class="mp-foot">
      <div class="mp-picked">
        <template v-if="picked">
          <span class="mp-addr">{{ picked.address || '未命名位置' }}</span>
          <span class="mp-coord">{{ picked.latitude }}, {{ picked.longitude }}</span>
        </template>
        <span v-else class="mp-coord">尚未选择位置</span>
      </div>
      <div class="mp-btns">
        <el-button @click="emit('update:visible', false)">取消</el-button>
        <el-button type="primary" :disabled="!picked" @click="confirm">确定</el-button>
      </div>
    </div>
  </el-dialog>
</template>

<script setup>
import { computed, onBeforeUnmount, ref } from 'vue'
import { Aim, Search } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { DEFAULT_CENTER, DEFAULT_ZOOM, loadAmap, toXY } from '@/composables/useAmap'

const props = defineProps({
  visible: { type: Boolean, default: false },
  latitude: { type: [Number, String], default: null },
  longitude: { type: [Number, String], default: null },
  address: { type: String, default: '' },
})
const emit = defineEmits(['update:visible', 'confirm'])

const mapEl = ref(null)
const keyword = ref('')
const tips = ref([])
const searching = ref(false)
const picked = ref(null)
const loadError = ref('')

let AMap = null
let map = null
let marker = null
let geocoder = null

const hasSaved = computed(() => {
  const lat = Number(props.latitude)
  const lng = Number(props.longitude)
  return Number.isFinite(lat) && Number.isFinite(lng) && !(lat === 0 && lng === 0)
})

function coordOf() {
  return [Number(props.longitude), Number(props.latitude)]
}

async function init() {
  loadError.value = ''
  picked.value = null
  tips.value = []
  try {
    AMap = await loadAmap()
  } catch (e) {
    loadError.value = e.message || '地图加载失败'
    return
  }
  if (!mapEl.value) return

  const center = hasSaved.value ? coordOf() : DEFAULT_CENTER
  map = new AMap.Map(mapEl.value, {
    center,
    zoom: hasSaved.value ? 16 : DEFAULT_ZOOM,
    resizeEnable: true,
    viewMode: '2D',
  })
  geocoder = new AMap.Geocoder({ city: '全国' })
  map.on('click', (e) => place(toXY(e.lnglat), {}))
  // 打开时只按已存坐标落点，不重复请求逆地理（地址表单里已有）
  if (hasSaved.value) place(center, { address: props.address, skipRegeo: true })
}

function place(xy, { address = '', name = '', skipRegeo = false } = {}) {
  if (!xy || !AMap || !map) return
  const [lng, lat] = xy
  if (!marker) {
    marker = new AMap.Marker({ position: [lng, lat], draggable: true, cursor: 'grab' })
    map.add(marker)
    marker.on('dragend', (e) => place(toXY(e.lnglat), {}))
  } else {
    marker.setPosition([lng, lat])
  }
  marker.setMap(map)
  picked.value = { latitude: round6(lat), longitude: round6(lng), address, name }
  if (!skipRegeo) fillAddress([lng, lat])
}

// 逆地理编码只是方便回填地址文本，失败时保留坐标即可，不打断选点
function fillAddress(xy) {
  if (!geocoder) return
  geocoder.getAddress(xy, (status, result) => {
    const regeo = status === 'complete' && result.regeocode
    if (!picked.value) return
    picked.value = {
      ...picked.value,
      address: regeo ? regeo.formattedAddress : picked.value.address,
      name: regeo?.pois?.[0]?.name || picked.value.name,
    }
  })
}

async function doSearch() {
  const word = keyword.value.trim()
  if (!word || !AMap) return
  searching.value = true
  tips.value = []
  try {
    const list = await new Promise((resolve, reject) => {
      const ac = new AMap.AutoComplete({ city: '全国' })
      ac.search(word, (status, result) => {
        if (status === 'complete' || status === 'no_result') resolve(result?.tips || [])
        else reject(new Error(result?.info || '搜索失败'))
      })
    })
    tips.value = list.filter((t) => toXY(t.location)).slice(0, 8)
    if (!tips.value.length) ElMessage.info('没有找到带坐标的结果，试试更具体的名称')
  } catch (e) {
    ElMessage.error(e.message || '搜索失败')
  } finally {
    searching.value = false
  }
}

function pickTip(t) {
  const xy = toXY(t.location)
  if (!xy) return
  tips.value = []
  map.setZoomAndCenter(16, xy)
  // 先用搜索结果的名称占位，逆地理返回后再补成完整地址
  place(xy, { address: String(t.address || t.name || ''), name: t.name || '' })
}

function gotoSaved() {
  if (!map || !hasSaved.value) return
  const xy = coordOf()
  map.setZoomAndCenter(16, xy)
  place(xy, { address: props.address, skipRegeo: true })
}

function round6(n) {
  return Number(Number(n).toFixed(6))
}

function confirm() {
  if (!picked.value) return
  emit('confirm', { ...picked.value })
  emit('update:visible', false)
}

function dispose() {
  if (marker) { marker.setMap(null); marker = null }
  if (map) { map.destroy(); map = null }
  geocoder = null
  AMap = null
}

onBeforeUnmount(dispose)
</script>

<style scoped>
.mp-search { display: flex; gap: 8px; margin-bottom: 10px; }
.mp-tips {
  display: grid;
  grid-template-columns: 1fr;
  gap: 2px;
  margin-bottom: 10px;
  max-height: 150px;
  overflow: auto;
  border: 1px solid var(--line);
  border-radius: var(--radius);
}
.mp-tip {
  display: flex;
  align-items: baseline;
  gap: 8px;
  min-height: 34px;
  padding: 6px 10px;
  background: none;
  border: none;
  border-bottom: 1px solid var(--line2);
  text-align: left;
  cursor: pointer;
  font-size: 13px;
  color: var(--ink);
}
.mp-tip:last-child { border-bottom: none; }
.mp-tip:hover { background: var(--brand-soft); }
.mp-tip-name { font-weight: 600; white-space: nowrap; }
.mp-tip-addr { color: var(--ink3); font-size: 12px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.mp-map-wrap { position: relative; }
.mp-map { width: 100%; height: 420px; border: 1px solid var(--line); border-radius: var(--radius); }
.mp-empty {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 0 40px;
  text-align: center;
  font-size: 13px;
  color: var(--ink2);
  background: var(--panel);
  border: 1px solid var(--line);
  border-radius: var(--radius);
  line-height: 1.6;
}
.mp-hint {
  position: absolute;
  left: 50%;
  top: 12px;
  transform: translateX(-50%);
  padding: 4px 10px;
  font-size: 12px;
  color: var(--ink2);
  background: rgba(255, 255, 255, .9);
  border: 1px solid var(--line);
  border-radius: 999px;
  pointer-events: none;
}
.mp-foot { display: flex; align-items: center; gap: 12px; margin-top: 12px; }
.mp-picked { display: flex; flex-direction: column; min-width: 0; }
.mp-addr { font-size: 13px; color: var(--ink); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; max-width: 480px; }
.mp-coord { font-size: 12px; color: var(--ink3); }
.mp-btns { margin-left: auto; display: flex; gap: 8px; }
</style>
