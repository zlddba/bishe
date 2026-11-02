<template>
  <div class="page-container">
    <div class="page-h">
      <div><h2>通知公告</h2><p>系统消息记录与全站公告推送</p></div>
      <div class="ml-auto filter-bar">
        <el-input v-model="query.q" placeholder="搜索标题/内容" clearable style="width:200px" @keyup.enter="loadData" @clear="loadData" />
        <el-select v-model="query.type" placeholder="全部类型" clearable style="width:150px" @change="loadData">
          <el-option v-for="t in typeOptions" :key="t.value" :label="t.label" :value="t.value" />
        </el-select>
        <el-button type="primary" @click="loadData">查询</el-button>
        <el-button type="primary" @click="openBroadcast">发布公告</el-button>
      </div>
    </div>

    <div class="table-card">
      <el-table :data="items" v-loading="loading" stripe>
        <el-table-column prop="id" label="编号" width="60" />
        <el-table-column prop="username" label="接收用户" width="120" />
        <el-table-column prop="type" label="类型" width="120">
          <template #default="{ row }">
            <span class="pn-tag" :class="row.type === 'system_announcement' ? 'pn-tag-brand' : 'pn-tag-soft'">
              {{ typeLabel(row.type) }}
            </span>
          </template>
        </el-table-column>
        <el-table-column prop="title" label="标题" width="180">
          <template #default="{ row }">{{ row.title || '-' }}</template>
        </el-table-column>
        <el-table-column prop="body" label="内容" min-width="280" show-overflow-tooltip />
        <el-table-column prop="is_read" label="状态" width="80">
          <template #default="{ row }">
            <span class="pn-tag" :class="row.is_read ? 'pn-tag-soft' : 'pn-tag-warn'">{{ row.is_read ? '已读' : '未读' }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="created_at" label="发送时间" width="150" />
        <el-table-column label="操作" width="80" fixed="right">
          <template #default="{ row }">
            <el-button type="danger" size="small" @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <div class="pagination-wrap">
        <el-pagination v-model:current-page="query.page" v-model:page-size="query.per_page" :total="total" :page-sizes="[10,20,50]" layout="total, sizes, prev, pager, next" @change="loadData" />
      </div>
    </div>

    <el-dialog v-model="broadcastDialog" title="发布系统公告" width="520px">
      <el-form :model="form" label-width="90px">
        <el-form-item label="接收对象">
          <el-radio-group v-model="form.target">
            <el-radio value="all">全部用户</el-radio>
            <el-radio value="owner">仅宠物主人</el-radio>
            <el-radio value="sitter">仅寄养机构</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="公告标题">
          <el-input v-model="form.title" maxlength="128" show-word-limit placeholder="如：国庆期间平台服务安排" />
        </el-form-item>
        <el-form-item label="公告内容">
          <el-input v-model="form.body" type="textarea" :rows="4" maxlength="2000" show-word-limit placeholder="请输入公告正文" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="broadcastDialog = false">取消</el-button>
        <el-button type="primary" :loading="sending" @click="submitBroadcast">立即推送</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { adminApi } from '@/api'
import { ElMessage, ElMessageBox } from 'element-plus'

const loading = ref(false)
const sending = ref(false)
const items = ref([])
const total = ref(0)
const query = reactive({ page: 1, per_page: 20, q: '', type: '' })

const broadcastDialog = ref(false)
const form = reactive({ target: 'all', title: '', body: '' })

const typeOptions = [
  { value: 'system_announcement', label: '系统公告' },
  { value: 'order_created', label: '新订单' },
  { value: 'order_accepted', label: '已接单' },
  { value: 'order_cancelled', label: '订单取消' },
  { value: 'order_rejected', label: '订单拒绝' },
  { value: 'order_completed', label: '订单完成' },
  { value: 'dynamics_published', label: '动态更新' },
  { value: 'review_received', label: '收到评价' },
]

function typeLabel(t) {
  return typeOptions.find(o => o.value === t)?.label || t
}

async function loadData() {
  loading.value = true
  try {
    const res = await adminApi.getNotifications(query)
    items.value = res.data?.items || []
    total.value = res.data?.total || 0
  } catch (e) {
    ElMessage.error(e.message || '加载通知失败')
  } finally {
    loading.value = false
  }
}

function openBroadcast() {
  Object.assign(form, { target: 'all', title: '', body: '' })
  broadcastDialog.value = true
}

async function submitBroadcast() {
  if (!form.title.trim()) { ElMessage.warning('请填写公告标题'); return }
  if (!form.body.trim()) { ElMessage.warning('请填写公告内容'); return }

  sending.value = true
  try {
    const res = await adminApi.broadcastNotification({
      title: form.title.trim(),
      body: form.body.trim(),
      target: form.target,
    })
    ElMessage.success(`公告已推送，共 ${res.data?.sent || 0} 位用户收到`)
    broadcastDialog.value = false
    loadData()
  } catch (e) {
    ElMessage.error(e.message || '推送失败')
  } finally {
    sending.value = false
  }
}

async function handleDelete(row) {
  try {
    await ElMessageBox.confirm('确认删除这条通知吗？', '删除确认', { type: 'warning' })
    await adminApi.deleteNotification(row.id)
    ElMessage.success('通知已删除')
    loadData()
  } catch (e) {
    if (e !== 'cancel') ElMessage.error(e.message || '删除失败')
  }
}

onMounted(loadData)
</script>
