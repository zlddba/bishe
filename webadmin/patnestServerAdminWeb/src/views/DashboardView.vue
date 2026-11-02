<template>
  <div class="page-container">
    <div class="page-h">
      <div><h2>经营看板</h2><p>今日经营与数据一览</p></div>
    </div>

    <div class="stat-grid">
      <div class="stat">
        <div class="lbl">用户总数</div>
        <div class="num">{{ stats.overview?.users || 0 }}</div>
      </div>
      <div class="stat">
        <div class="lbl">活跃机构</div>
        <div class="num">{{ stats.overview?.sitters || 0 }}</div>
      </div>
      <div class="stat">
        <div class="lbl">进行中订单</div>
        <div class="num">{{ stats.overview?.active_orders || 0 }}</div>
      </div>
      <div class="stat">
        <div class="lbl">订单总数</div>
        <div class="num">{{ stats.overview?.orders || 0 }}</div>
      </div>
    </div>

    <div class="chart-row">
      <div class="chart-box">
        <div class="chart-title">近 14 日收入趋势</div>
        <div ref="revenueChart" class="chart-canvas"></div>
      </div>
      <div class="chart-box">
        <div class="chart-title">订单状态分布</div>
        <div ref="statusChart" class="chart-canvas"></div>
      </div>
    </div>

    <div class="chart-row">
      <div class="chart-box">
        <div class="chart-title">机构类型占比</div>
        <div ref="typeChart" class="chart-canvas"></div>
      </div>
      <div class="chart-box">
        <div class="chart-title">用户角色分布</div>
        <div ref="roleChart" class="chart-canvas"></div>
      </div>
    </div>

    <div class="chart-row">
      <div class="chart-box">
        <div class="chart-title">评分 TOP 机构</div>
        <div ref="topChart" class="chart-canvas"></div>
      </div>
      <div class="chart-box">
        <div class="chart-title">机构收益排行</div>
        <div ref="revenueRankChart" class="chart-canvas"></div>
      </div>
    </div>

    <div class="chart-row">
      <div class="chart-box" style="grid-column: 1 / -1">
        <div class="chart-title">机构容量日历（未来 14 天占用率）</div>
        <div ref="heatChart" class="chart-canvas" style="height:300px"></div>
      </div>
    </div>

    <div class="chart-row">
      <div class="chart-box" style="grid-column: 1 / -1">
        <div class="chart-title">容量预警（近 7 日）</div>
        <div class="alert-list">
          <div v-for="a in stats?.capacity_alerts || []" :key="a.name + a.date" class="alert-row">
            <span class="alert-dot" :style="{background: a.utilization === '100%' ? 'var(--danger)' : 'var(--warn)'}"></span>
            <span class="alert-name">{{ a.name }}</span>
            <span class="alert-date">{{ a.date }}</span>
            <span class="alert-pct">{{ a.used }}/{{ a.capacity }} ({{ a.utilization }})</span>
          </div>
          <div v-if="!(stats?.capacity_alerts||[]).length" class="alert-empty">暂无容量预警</div>
        </div>
      </div>
    </div>

    <div class="chart-row">
      <div class="chart-box" style="grid-column: 1 / -1">
        <div class="chart-title">差评预警（近 7 日）</div>
        <el-table :data="stats.review_alerts" stripe size="small">
          <el-table-column prop="sitter_name" label="机构" width="160" />
          <el-table-column prop="reviewer" label="评价人" width="120" />
          <el-table-column prop="score" label="评分" width="60">
            <template #default="{ row }">
              <span style="color:var(--danger);font-weight:600">{{ row.score }}</span>
            </template>
          </el-table-column>
          <el-table-column prop="comment" label="评价内容" min-width="300">
            <template #default="{ row }">{{ row.comment || '-' }}</template>
          </el-table-column>
          <el-table-column prop="created_at" label="时间" width="160" />
        </el-table>
        <div v-if="!stats.review_alerts.length" class="alert-empty">暂无差评记录</div>
      </div>
    </div>

    <div class="chart-row">
      <div class="chart-box" style="grid-column: 1 / -1">
        <div class="chart-title">系统与发布信息</div>
        <el-descriptions :column="3" size="small" border>
          <el-descriptions-item label="后端版本">v{{ system.api.version || '-' }} · {{ system.api.slot || '-' }}</el-descriptions-item>
          <el-descriptions-item label="提交">{{ system.api.commit || '-' }}</el-descriptions-item>
          <el-descriptions-item label="构建时间">{{ system.api.build_time || '-' }}</el-descriptions-item>
          <el-descriptions-item label="运行时长">{{ uptimeText }}</el-descriptions-item>
          <el-descriptions-item label="数据库">{{ system.database.server_version || '-' }}</el-descriptions-item>
          <el-descriptions-item label="迁移状态">
            <span v-if="system.database.migrations?.ready">
              {{ system.database.migrations.applied }} 个 · {{ system.database.migrations.latest }}
            </span>
            <span v-else>尚未启用迁移</span>
          </el-descriptions-item>
          <el-descriptions-item label="对象存储">{{ system.storage.bucket || '-' }}</el-descriptions-item>
          <el-descriptions-item label="存储状态">
            <span class="pn-tag" :class="system.storage.ready ? 'pn-tag-ok' : 'pn-tag-danger'">
              {{ system.storage.ready ? '正常' : '异常' }}
            </span>
          </el-descriptions-item>
          <el-descriptions-item label="发布记录">{{ (system.releases || []).length }} 条</el-descriptions-item>
        </el-descriptions>
        <el-table v-if="system.releases.length" :data="system.releases" size="small" style="margin-top:12px">
          <el-table-column prop="app" label="应用" width="80" />
          <el-table-column prop="version" label="版本" min-width="160" />
          <el-table-column prop="commit" label="提交" width="120" />
          <el-table-column prop="slot" label="槽位" width="90" />
          <el-table-column prop="note" label="说明" min-width="180" />
          <el-table-column prop="deployed_at" label="发布时间" width="150" />
        </el-table>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, onUnmounted, nextTick } from 'vue'
import { adminApi } from '@/api'
import * as echarts from 'echarts'

function emptyStats() {
  return {
    overview: { users: 0, sitters: 0, orders: 0, active_orders: 0 },
    order_status_dist: [],
    sitter_type_dist: [],
    user_role_dist: [],
    top_sitters: [],
    revenue_trend: [],
    capacity_alerts: [],
    review_alerts: [],
    capacity_calendar: { dates: [], sitters: [] },
    sitter_revenue: [],
  }
}

const stats = reactive(emptyStats())
const system = reactive({ api: {}, database: { migrations: {} }, storage: {}, releases: [] })
const revenueChart = ref(null)
const statusChart = ref(null)
const typeChart = ref(null)
const roleChart = ref(null)
const topChart = ref(null)
const revenueRankChart = ref(null)
const heatChart = ref(null)
let charts = []

const uptimeText = computed(() => {
  const seconds = Number(system.api.uptime_seconds || 0)
  if (!seconds) return '-'
  const days = Math.floor(seconds / 86400)
  const hours = Math.floor((seconds % 86400) / 3600)
  const mins = Math.floor((seconds % 3600) / 60)
  if (days) return `${days} 天 ${hours} 小时`
  if (hours) return `${hours} 小时 ${mins} 分`
  return `${mins} 分钟`
})

async function loadSystemInfo() {
  try {
    const res = await adminApi.getSystemInfo()
    const data = res.data || {}
    Object.assign(system, {
      api: data.api || {},
      database: { migrations: {}, ...(data.database || {}) },
      storage: data.storage || {},
      releases: data.releases || [],
    })
  } catch (e) {
    console.error('[Dashboard] 加载系统信息失败', e)
  }
}

async function loadStats() {
  try {
    const res = await adminApi.getStats()
    const data = res.data || {}
    Object.assign(stats, emptyStats(), data, {
      overview: { ...emptyStats().overview, ...(data.overview || {}) },
    })
    await nextTick()
    renderCharts()
  } catch (e) {
    console.error('[Dashboard] 加载统计数据失败', e)
  }
}

function renderCharts() {
  disposeCharts()
  renderRevenue()
  renderStatus()
  renderType()
  renderRole()
  renderTop()
  renderRevenueRank()
  renderCapacity()
}

function disposeCharts() {
  charts.forEach(c => c?.dispose())
  charts = []
}

function renderRevenue() {
  if (!revenueChart.value) return
  const data = stats.revenue_trend
  const chart = echarts.init(revenueChart.value)
  chart.setOption({
    tooltip: { trigger: 'axis' },
    grid: { left: 50, right: 20, top: 20, bottom: 30 },
    xAxis: {
      type: 'category',
      data: data.map(d => d.date?.slice(5)),
      axisLabel: { fontSize: 11, color: '#9AA3AF' },
    },
    yAxis: { type: 'value', axisLabel: { fontSize: 11, color: '#9AA3AF' } },
    series: [
      { name: '收入', type: 'bar', data: data.map(d => d.revenue), itemStyle: { color: '#E0708A', borderRadius: [3,3,0,0] } },
      { name: '订单数', type: 'line', data: data.map(d => d.orders), smooth: true, itemStyle: { color: '#3E7BFA' }, lineStyle: { width: 2 } },
    ],
  })
  charts.push(chart)
}

function renderStatus() {
  if (!statusChart.value) return
  const data = stats.order_status_dist
  const chart = echarts.init(statusChart.value)
  chart.setOption({
    tooltip: { trigger: 'item', formatter: '{b}: {c} ({d}%)' },
    legend: { bottom: 0, textStyle: { fontSize: 11, color: '#5C6470' } },
    series: [{
      type: 'pie', radius: ['40%', '65%'], center: ['50%', '42%'],
      data, itemStyle: { borderRadius: 4, borderColor: '#fff', borderWidth: 2 },
      label: { fontSize: 11 },
    }],
    color: ['#E0708A', '#F2AEBE', '#3E7BFA', '#2F9E63', '#D97706', '#C2456A', '#9AA3AF', '#EAB308'],
  })
  charts.push(chart)
}

function renderType() {
  if (!typeChart.value) return
  const data = stats.sitter_type_dist
  const chart = echarts.init(typeChart.value)
  chart.setOption({
    tooltip: { trigger: 'item', formatter: '{b}: {c} ({d}%)' },
    legend: { bottom: 0, textStyle: { fontSize: 11, color: '#5C6470' } },
    series: [{
      type: 'pie', radius: '60%', center: ['50%', '45%'],
      data, itemStyle: { borderRadius: 4, borderColor: '#fff', borderWidth: 2 },
      label: { fontSize: 11 },
    }],
    color: ['#E0708A', '#F2AEBE', '#FBE0E6'],
  })
  charts.push(chart)
}

function renderRole() {
  if (!roleChart.value) return
  const data = stats.user_role_dist
  const chart = echarts.init(roleChart.value)
  chart.setOption({
    tooltip: { trigger: 'item', formatter: '{b}: {c} ({d}%)' },
    legend: { bottom: 0, textStyle: { fontSize: 11, color: '#5C6470' } },
    series: [{
      type: 'pie', radius: '60%', center: ['50%', '45%'],
      data, itemStyle: { borderRadius: 4, borderColor: '#fff', borderWidth: 2 },
      label: { fontSize: 11 },
    }],
    color: ['#E0708A', '#D97706', '#C2456A'],
  })
  charts.push(chart)
}

function renderTop() {
  if (!topChart.value) return
  const data = stats.top_sitters
  const chart = echarts.init(topChart.value)
  chart.setOption({
    tooltip: { trigger: 'axis' },
    grid: { left: 100, right: 30, top: 10, bottom: 20 },
    xAxis: { type: 'value', max: 5, axisLabel: { fontSize: 11, color: '#9AA3AF' } },
    yAxis: {
      type: 'category', data: data.map(d => d.name).reverse(),
      axisLabel: { fontSize: 11, color: '#5C6470' },
    },
    series: [{
      type: 'bar', data: data.map(d => d.score).reverse(),
      itemStyle: { color: '#E0708A', borderRadius: [0,3,3,0] },
      barWidth: '60%',
      label: { show: true, position: 'right', fontSize: 11 },
    }],
  })
  charts.push(chart)
}

function renderRevenueRank() {
  if (!revenueRankChart.value) return
  const data = [...(stats.sitter_revenue || [])].reverse()
  const chart = echarts.init(revenueRankChart.value)
  if (!data.length) {
    chart.setOption({
      title: { text: '暂无收益数据', left: 'center', top: 'middle', textStyle: { fontSize: 12, color: '#9AA3AF', fontWeight: 'normal' } },
    })
    charts.push(chart)
    return
  }
  chart.setOption({
    tooltip: {
      trigger: 'axis',
      formatter: (p) => {
        const item = p[0] || {}
        const row = data[item.dataIndex] || {}
        return `${item.name}<br/>收益：${Number(row.revenue || 0).toFixed(2)} 元<br/>订单：${row.orders || 0} 单`
      },
    },
    grid: { left: 100, right: 60, top: 10, bottom: 20 },
    xAxis: { type: 'value', axisLabel: { fontSize: 10, color: '#9AA3AF' } },
    yAxis: {
      type: 'category',
      data: data.map(d => d.name),
      axisLabel: { fontSize: 11, color: '#5C6470' },
    },
    series: [{
      type: 'bar',
      data: data.map(d => d.revenue),
      barWidth: '60%',
      itemStyle: { color: '#E0708A', borderRadius: [0, 3, 3, 0] },
      label: { show: true, position: 'right', fontSize: 10, formatter: p => Number(p.value).toFixed(0) },
    }],
  })
  charts.push(chart)
}

function renderCapacity() {
  if (!heatChart.value) return
  const cal = stats.capacity_calendar || { dates: [], sitters: [] }
  const chart = echarts.init(heatChart.value)
  if (!cal.sitters || !cal.sitters.length) {
    chart.setOption({
      title: { text: '暂无在架机构', left: 'center', top: 'middle', textStyle: { fontSize: 12, color: '#9AA3AF', fontWeight: 'normal' } },
    })
    charts.push(chart)
    return
  }
  const heat = []
  cal.sitters.forEach((s, y) => {
    (s.values || []).forEach((v, x) => heat.push([x, y, v]))
  })
  chart.setOption({
    tooltip: {
      formatter: (p) => `${cal.sitters[p.value[1]]?.name || ''}<br/>${cal.dates[p.value[0]]}：占用 ${p.value[2]}%`,
    },
    grid: { left: 100, right: 20, top: 10, bottom: 58 },
    xAxis: {
      type: 'category',
      data: cal.dates,
      splitArea: { show: true },
      axisLabel: { fontSize: 10, color: '#9AA3AF' },
    },
    yAxis: {
      type: 'category',
      data: cal.sitters.map(s => s.name),
      splitArea: { show: true },
      axisLabel: { fontSize: 11, color: '#5C6470' },
    },
    visualMap: {
      min: 0,
      max: 100,
      orient: 'horizontal',
      left: 'center',
      bottom: 0,
      itemWidth: 10,
      itemHeight: 90,
      textStyle: { fontSize: 10, color: '#9AA3AF' },
      inRange: { color: ['#FBEDF1', '#F2AEBE', '#E0708A', '#C2456A'] },
    },
    series: [{
      type: 'heatmap',
      data: heat,
      label: { show: true, fontSize: 9, formatter: p => (p.value[2] ? p.value[2] : '') },
      itemStyle: { borderColor: '#fff', borderWidth: 1, borderRadius: 2 },
      emphasis: { itemStyle: { shadowBlur: 6, shadowColor: 'rgba(0,0,0,.18)' } },
    }],
  })
  charts.push(chart)
}

function resizeCharts() {
  charts.forEach(c => c?.resize())
}

onMounted(() => {
  window.addEventListener('resize', resizeCharts)
  loadStats()
  loadSystemInfo()
})

onUnmounted(() => {
  window.removeEventListener('resize', resizeCharts)
  disposeCharts()
})
</script>

<style scoped>
.chart-row {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 14px;
  margin-bottom: 14px;
}
.chart-box {
  background: var(--panel);
  border: 1px solid var(--line);
  border-radius: 10px;
  padding: 18px 20px;
}
.chart-title {
  font-size: 14px;
  font-weight: 600;
  margin-bottom: 12px;
  color: var(--ink);
}
.chart-canvas {
  width: 100%;
  height: 260px;
}
.alert-list { display: flex; flex-direction: column; gap: 10px; padding-top: 4px; }
.alert-row {
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 13px;
  padding: 8px 0;
  border-bottom: 1px solid var(--line2);
}
.alert-row:last-child { border-bottom: none; }
.alert-dot { width: 7px; height: 7px; border-radius: 50%; flex-shrink: 0; }
.alert-name { font-weight: 500; min-width: 100px; }
.alert-date { color: var(--ink3); font-size: 12px; }
.alert-pct { margin-left: auto; color: var(--ink2); font-size: 12px; }
.alert-empty { padding: 30px; text-align: center; color: var(--ink3); font-size: 13px; }
</style>
