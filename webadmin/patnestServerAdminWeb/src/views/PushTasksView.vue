<template>
  <div class="page-container">
    <div class="page-h">
      <div><h2>推送任务</h2><p>立即或定时向指定用户群发送消息</p></div>
      <div class="ml-auto filter-bar">
        <el-select v-model="query.status" placeholder="全部状态" clearable style="width:120px" @change="loadData">
          <el-option label="待发送" value="pending" />
          <el-option label="定时中" value="scheduled" />
          <el-option label="已发送" value="sent" />
          <el-option label="已取消" value="cancelled" />
          <el-option label="失败" value="failed" />
        </el-select>
        <el-button type="primary" @click="loadData">查询</el-button>
        <el-button type="primary" @click="openCreate">新建推送</el-button>
      </div>
    </div>

    <div class="table-card">
      <el-table :data="items" v-loading="loading" stripe>
        <el-table-column prop="id" label="编号" width="60" />
        <el-table-column prop="title" label="标题" min-width="180" show-overflow-tooltip />
        <el-table-column prop="body" label="内容" min-width="240" show-overflow-tooltip />
        <el-table-column label="对象" width="100">
          <template #default="{ row }">{{ targetLabel(row.target) }}</template>
        </el-table-column>
        <el-table-column label="通道" width="90">
          <template #default="{ row }">{{ channelLabel(row.channel) }}</template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <span class="pn-tag" :class="statusClass(row.status)">{{ statusLabel(row.status) }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="scheduled_at" label="定时时间" width="150">
          <template #default="{ row }">{{ row.scheduled_at || '-' }}</template>
        </el-table-column>
        <el-table-column prop="sent_count" label="送达" width="70" />
        <el-table-column prop="sent_at" label="发送时间" width="150">
          <template #default="{ row }">{{ row.sent_at || '-' }}</template>
        </el-table-column>
        <el-table-column label="操作" width="170" fixed="right">
          <template #default="{ row }">
            <el-button
              v-if="['pending','scheduled','failed'].includes(row.status)"
              size="small"
              type="primary"
              @click="sendNow(row)"
            >立即发送</el-button>
            <el-button
              v-if="['pending','scheduled'].includes(row.status)"
              size="small"
              @click="cancel(row)"
            >取消</el-button>
          </template>
        </el-table-column>
      </el-table>
      <div class="pagination-wrap">
        <el-pagination v-model:current-page="query.page" v-model:page-size="query.per_page" :total="total" :page-sizes="[10,20,50]" layout="total, sizes, prev, pager, next" @change="loadData" />
      </div>
    </div>

    <el-dialog v-model="dialog" title="新建推送任务" width="540px">
      <el-form :model="form" label-width="96px">
        <el-form-item label="推送对象">
          <el-radio-group v-model="form.target">
            <el-radio value="all">全部用户</el-radio>
            <el-radio value="owner">仅主人</el-radio>
            <el-radio value="sitter">仅机构</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="发送通道">
          <el-select v-model="form.channel" style="width:100%">
            <el-option label="站内信" value="in_app" />
            <el-option label="短信（模拟）" value="sms" />
            <el-option label="微信（模拟）" value="wechat" />
          </el-select>
        </el-form-item>
        <el-form-item label="标题">
          <el-input v-model="form.title" maxlength="128" show-word-limit placeholder="推送标题" />
        </el-form-item>
        <el-form-item label="内容">
          <el-input v-model="form.body" type="textarea" :rows="4" maxlength="2000" show-word-limit placeholder="推送正文" />
        </el-form-item>
        <el-form-item label="发送方式">
          <el-radio-group v-model="form.mode">
            <el-radio value="now">立即发送</el-radio>
            <el-radio value="schedule">定时发送</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item v-if="form.mode === 'schedule'" label="发送时间">
          <el-date-picker
            v-model="form.scheduled_at"
            type="datetime"
            value-format="YYYY-MM-DD HH:mm:ss"
            placeholder="选择发送时间"
            style="width:100%"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialog = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submit">创建并发送</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { adminApi } from '@/api'
import { ElMessage, ElMessageBox } from 'element-plus'

const loading = ref(false)
const saving = ref(false)
const items = ref([])
const total = ref(0)
const query = reactive({ page: 1, per_page: 20, status: '' })

const dialog = ref(false)
const form = reactive({ target: 'all', channel: 'in_app', title: '', body: '', mode: 'now', scheduled_at: '' })

function targetLabel(t) { return { all: '全部用户', owner: '主人', sitter: '机构' }[t] || t }
function channelLabel(c) { return { in_app: '站内信', sms: '短信', wechat: '微信' }[c] || c }
function statusLabel(s) {
  return { pending: '待发送', scheduled: '定时中', sending: '发送中', sent: '已发送', cancelled: '已取消', failed: '失败' }[s] || s
}
function statusClass(s) {
  return { pending: 'pn-tag-warn', scheduled: 'pn-tag-brand', sending: 'pn-tag-brand', sent: 'pn-tag-ok', cancelled: 'pn-tag-soft', failed: 'pn-tag-danger' }[s] || 'pn-tag-soft'
}

async function loadData() {
  loading.value = true
  try {
    const res = await adminApi.getPushTasks(query)
    items.value = res.data?.items || []
    total.value = res.data?.total || 0
  } catch (e) {
    ElMessage.error(e.message || '加载推送任务失败')
  } finally {
    loading.value = false
  }
}

function openCreate() {
  Object.assign(form, { target: 'all', channel: 'in_app', title: '', body: '', mode: 'now', scheduled_at: '' })
  dialog.value = true
}

async function submit() {
  if (!form.title.trim()) { ElMessage.warning('请填写标题'); return }
  if (!form.body.trim()) { ElMessage.warning('请填写内容'); return }
  if (form.mode === 'schedule' && !form.scheduled_at) { ElMessage.warning('请选择发送时间'); return }
  saving.value = true
  try {
    const res = await adminApi.createPushTask({
      title: form.title.trim(),
      body: form.body.trim(),
      target: form.target,
      channel: form.channel,
      send_now: form.mode === 'now',
      scheduled_at: form.mode === 'schedule' ? form.scheduled_at : '',
    })
    ElMessage.success(res.data?.status === 'scheduled' ? '定时任务已创建' : `已发送给 ${res.data?.sent || 0} 位用户`)
    dialog.value = false
    loadData()
  } catch (e) {
    ElMessage.error(e.message || '创建失败')
  } finally {
    saving.value = false
  }
}

async function sendNow(row) {
  try {
    await ElMessageBox.confirm(`确认立即发送「${row.title}」吗？`, '发送确认', { type: 'warning' })
    const res = await adminApi.sendPushTask(row.id)
    ElMessage.success(`已发送给 ${res.data?.sent || 0} 位用户`)
    loadData()
  } catch (e) {
    if (e !== 'cancel') ElMessage.error(e.message || '发送失败')
  }
}

async function cancel(row) {
  try {
    await ElMessageBox.confirm(`确认取消推送「${row.title}」吗？`, '取消确认', { type: 'warning' })
    await adminApi.cancelPushTask(row.id)
    ElMessage.success('任务已取消')
    loadData()
  } catch (e) {
    if (e !== 'cancel') ElMessage.error(e.message || '取消失败')
  }
}

onMounted(loadData)
</script>
