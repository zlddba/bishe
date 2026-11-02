<template>
  <div class="page-container">
    <div class="page-h">
      <div><h2>审计日志</h2><p>平台全部操作审计记录</p></div>
      <div class="ml-auto filter-bar">
        <el-select v-model="query.action" placeholder="全部操作" clearable style="width:190px" @change="search">
          <el-option v-for="a in actionOptions" :key="a.value" :label="a.label" :value="a.value" />
        </el-select>
        <el-button type="primary" @click="search">查询</el-button>
      </div>
    </div>

    <div class="table-card">
      <el-table :data="logs" v-loading="loading" stripe>
        <el-table-column prop="id" label="编号" width="60" />
        <el-table-column prop="actor_id" label="操作人" width="80">
          <template #default="{ row }">{{ row.actor_id || '系统' }}</template>
        </el-table-column>
        <el-table-column prop="action" label="操作" width="170">
          <template #default="{ row }">
            <span :title="row.action">{{ actionLabel(row.action) }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="resource_type" label="资源类型" width="110">
          <template #default="{ row }">{{ resourceLabel(row.resource_type) }}</template>
        </el-table-column>
        <el-table-column prop="resource_id" label="资源编号" width="92" />
        <el-table-column prop="detail" label="详情" min-width="250">
          <template #default="{ row }">
            <span style="font-family:monospace;font-size:12px;color:var(--ink2);word-break:break-all">{{ row.detail || '-' }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="ip_address" label="IP地址" width="130">
          <template #default="{ row }">{{ row.ip_address || '-' }}</template>
        </el-table-column>
        <el-table-column prop="created_at" label="时间" width="170" />
      </el-table>
      <div class="pagination-wrap">
        <el-pagination v-model:current-page="query.page" v-model:page-size="query.per_page" :total="total" :page-sizes="[20,50,100]" layout="total, sizes, prev, pager, next" @change="loadData" />
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { adminApi } from '@/api'

// 操作码与资源类型存的是英文标识，展示时统一转成中文，鼠标悬停可看原始标识。
const ACTION_LABELS = {
  CREATE_USER: '新增用户',
  UPDATE_USER: '修改用户',
  DELETE_USER: '删除用户',
  RESET_PASSWORD: '重置密码',
  CREATE_SITTER: '新增机构',
  UPDATE_SITTER: '修改机构',
  UPDATE_SITTER_STATUS: '变更机构状态',
  DELETE_SITTER: '删除机构',
  CREATE_ORDER: '新增订单',
  UPDATE_ORDER: '修改订单',
  DELETE_ORDER: '删除订单',
  REFUND_ORDER: '订单退款',
  CREATE_PET: '新增宠物',
  UPDATE_PET: '修改宠物',
  DELETE_PET: '删除宠物',
  VERIFY_PET_VACCINE: '核验免疫证明',
  CREATE_DYNAMIC: '发布寄养动态',
  UPDATE_DYNAMIC: '修改寄养动态',
  DELETE_DYNAMIC: '删除寄养动态',
  CREATE_REVIEW: '新增评价',
  UPDATE_REVIEW: '修改评价',
  DELETE_REVIEW: '删除评价',
  CREATE_COUPON: '新建优惠券',
  UPDATE_COUPON: '修改优惠券',
  DISABLE_COUPON: '停用优惠券',
  CREATE_TEMPLATE: '新建消息模板',
  UPDATE_TEMPLATE: '修改消息模板',
  DELETE_TEMPLATE: '删除消息模板',
  CREATE_PUSH_TASK: '新建推送任务',
  SEND_PUSH_TASK: '发送推送任务',
  CANCEL_PUSH_TASK: '取消推送任务',
  BROADCAST_NOTIFICATION: '发布系统公告',
  DELETE_NOTIFICATION: '删除系统公告',
  APPROVE_REFUND_REQUEST: '同意退款申请',
  REJECT_REFUND_REQUEST: '驳回退款申请',
  REPLY_TICKET: '回复售后工单',
  UPDATE_TICKET: '处理售后工单',
  SETTLE_SITTER: '机构结算',
  UPDATE_PERMISSION: '调整权限配置',
  EXPORT_CSV: '导出数据',
  APPLY_UPDATE: '应用系统更新',
}

const RESOURCE_LABELS = {
  user: '用户',
  sitter: '机构',
  order: '订单',
  pet: '宠物',
  dynamic: '寄养动态',
  review: '评价',
  coupon: '优惠券',
  notification: '通知公告',
  notification_template: '消息模板',
  push_task: '推送任务',
  refund_request: '退款申请',
  ticket: '售后工单',
  settlement: '机构结算',
  export: '数据导出',
  role: '权限配置',
  update_job: '系统更新',
}

const actionOptions = Object.entries(ACTION_LABELS).map(([value, label]) => ({ value, label }))

function actionLabel(code) {
  return ACTION_LABELS[code] || code || '-'
}

function resourceLabel(code) {
  return RESOURCE_LABELS[code] || code || '-'
}

const loading = ref(false)
const logs = ref([])
const total = ref(0)
const query = reactive({ page: 1, per_page: 20, action: '' })

function search() {
  query.page = 1
  loadData()
}

async function loadData() {
  loading.value = true
  try {
    const res = await adminApi.getAuditLogs(query)
    logs.value = res.data?.items || []
    total.value = res.data?.total || 0
  } catch (e) { console.error(e) } finally { loading.value = false }
}
onMounted(loadData)
</script>
