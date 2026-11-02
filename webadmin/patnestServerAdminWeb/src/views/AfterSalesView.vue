<template>
  <div class="page-container">
    <div class="page-h">
      <div><h2>售后服务</h2><p>退款申请审批与用户工单处理</p></div>
    </div>

    <el-tabs v-model="activeTab" @tab-change="onTabChange">
      <el-tab-pane label="退款申请" name="refunds">
        <div class="filter-bar tab-tools">
          <el-select v-model="refundQuery.status" placeholder="全部状态" clearable style="width:130px" @change="loadRefunds">
            <el-option label="待处理" value="pending" />
            <el-option label="已通过" value="approved" />
            <el-option label="已驳回" value="rejected" />
            <el-option label="已取消" value="cancelled" />
          </el-select>
          <el-button type="primary" @click="loadRefunds">查询</el-button>
        </div>
        <div class="table-card">
          <el-table :data="refunds" v-loading="refundLoading" stripe>
            <el-table-column prop="id" label="编号" width="60" />
            <el-table-column prop="order_no" label="订单号" width="190" />
            <el-table-column prop="owner" label="主人" width="110" />
            <el-table-column prop="sitter" label="机构" min-width="140" />
            <el-table-column label="退款金额" width="110">
              <template #default="{ row }">¥{{ Number(row.amount).toFixed(2) }}</template>
            </el-table-column>
            <el-table-column prop="reason" label="退款原因" min-width="200" show-overflow-tooltip />
            <el-table-column label="状态" width="100">
              <template #default="{ row }">
                <span class="pn-tag" :class="refundClass(row.status)">{{ refundLabel(row.status) }}</span>
              </template>
            </el-table-column>
            <el-table-column prop="created_at" label="申请时间" width="150" />
            <el-table-column prop="handled_at" label="处理时间" width="150">
              <template #default="{ row }">{{ row.handled_at || '-' }}</template>
            </el-table-column>
            <el-table-column label="操作" width="100" fixed="right">
              <template #default="{ row }">
                <el-button v-if="row.status === 'pending'" size="small" type="primary" @click="openRefund(row)">审批</el-button>
                <el-button v-else size="small" @click="openRefund(row)">查看</el-button>
              </template>
            </el-table-column>
          </el-table>
          <div class="pagination-wrap">
            <el-pagination v-model:current-page="refundQuery.page" v-model:page-size="refundQuery.per_page" :total="refundTotal" :page-sizes="[10,20,50]" layout="total, sizes, prev, pager, next" @change="loadRefunds" />
          </div>
        </div>
      </el-tab-pane>

      <el-tab-pane label="售后工单" name="tickets">
        <div class="filter-bar tab-tools">
          <el-select v-model="ticketQuery.status" placeholder="全部状态" clearable style="width:130px" @change="loadTickets">
            <el-option label="待处理" value="open" />
            <el-option label="处理中" value="processing" />
            <el-option label="已关闭" value="closed" />
          </el-select>
          <el-button type="primary" @click="loadTickets">查询</el-button>
        </div>
        <div class="table-card">
          <el-table :data="tickets" v-loading="ticketLoading" stripe>
            <el-table-column prop="ticket_no" label="工单号" width="170" />
            <el-table-column prop="user" label="提交人" width="120" />
            <el-table-column label="分类" width="110">
              <template #default="{ row }">{{ categoryLabel(row.category) }}</template>
            </el-table-column>
            <el-table-column prop="title" label="标题" min-width="200" show-overflow-tooltip />
            <el-table-column label="优先级" width="90">
              <template #default="{ row }">
                <span class="pn-tag" :class="priorityClass(row.priority)">{{ priorityLabel(row.priority) }}</span>
              </template>
            </el-table-column>
            <el-table-column label="状态" width="100">
              <template #default="{ row }">
                <span class="pn-tag" :class="ticketClass(row.status)">{{ ticketLabel(row.status) }}</span>
              </template>
            </el-table-column>
            <el-table-column prop="message_count" label="消息" width="70" />
            <el-table-column prop="created_at" label="提交时间" width="150" />
            <el-table-column label="操作" width="90" fixed="right">
              <template #default="{ row }">
                <el-button size="small" @click="openTicket(row)">处理</el-button>
              </template>
            </el-table-column>
          </el-table>
          <div class="pagination-wrap">
            <el-pagination v-model:current-page="ticketQuery.page" v-model:page-size="ticketQuery.per_page" :total="ticketTotal" :page-sizes="[10,20,50]" layout="total, sizes, prev, pager, next" @change="loadTickets" />
          </div>
        </div>
      </el-tab-pane>
    </el-tabs>

    <el-dialog v-model="refundDialog" title="退款审批" width="560px">
      <template v-if="currentRefund">
        <el-descriptions :column="2" border size="small">
          <el-descriptions-item label="订单号">{{ currentRefund.order_no }}</el-descriptions-item>
          <el-descriptions-item label="申请金额">¥{{ Number(currentRefund.amount).toFixed(2) }}</el-descriptions-item>
          <el-descriptions-item label="主人">{{ currentRefund.owner }}</el-descriptions-item>
          <el-descriptions-item label="机构">{{ currentRefund.sitter }}</el-descriptions-item>
          <el-descriptions-item label="申请原因" :span="2">{{ currentRefund.reason }}</el-descriptions-item>
          <el-descriptions-item label="当前状态" :span="2">
            <span class="pn-tag" :class="refundClass(currentRefund.status)">{{ refundLabel(currentRefund.status) }}</span>
          </el-descriptions-item>
          <el-descriptions-item v-if="currentRefund.admin_note" label="审批备注" :span="2">{{ currentRefund.admin_note }}</el-descriptions-item>
        </el-descriptions>
        <el-form v-if="currentRefund.status === 'pending'" label-width="80px" style="margin-top:16px">
          <el-form-item label="审批备注">
            <el-input v-model="refundNote" type="textarea" :rows="3" maxlength="500" show-word-limit placeholder="驳回时建议填写原因" />
          </el-form-item>
        </el-form>
      </template>
      <template #footer>
        <el-button @click="refundDialog = false">关闭</el-button>
        <template v-if="currentRefund?.status === 'pending'">
          <el-button type="danger" :loading="refundSaving" @click="handleRefund('reject')">驳回</el-button>
          <el-button type="primary" :loading="refundSaving" @click="handleRefund('approve')">通过并退款</el-button>
        </template>
      </template>
    </el-dialog>

    <el-drawer v-model="ticketDrawer" title="工单详情" size="520px">
      <template v-if="currentTicket">
        <div class="ticket-head">
          <div class="ticket-title">{{ currentTicket.title }}</div>
          <div class="ticket-meta">{{ currentTicket.ticket_no }} · {{ currentTicket.user }} · {{ currentTicket.created_at }}</div>
          <div class="ticket-tags">
            <span class="pn-tag pn-tag-soft">{{ categoryLabel(currentTicket.category) }}</span>
            <span class="pn-tag" :class="ticketClass(currentTicket.status)">{{ ticketLabel(currentTicket.status) }}</span>
            <span class="pn-tag" :class="priorityClass(currentTicket.priority)">{{ priorityLabel(currentTicket.priority) }}</span>
          </div>
          <div class="ticket-content">{{ currentTicket.content }}</div>
        </div>

        <div class="messages">
          <div v-for="m in currentTicket.messages" :key="m.id" class="message" :class="{ mine: m.sender_role === 'admin' }">
            <div class="message-meta">{{ m.sender }} · {{ m.created_at }}</div>
            <div class="message-body">{{ m.content }}</div>
          </div>
          <div v-if="!currentTicket.messages?.length" class="empty-tip">暂无回复记录</div>
        </div>

        <el-form label-width="70px" class="ticket-form">
          <el-form-item label="状态">
            <el-select v-model="ticketStatus" style="width:150px">
              <el-option label="待处理" value="open" />
              <el-option label="处理中" value="processing" />
              <el-option label="已关闭" value="closed" />
            </el-select>
            <el-select v-model="ticketPriority" style="width:130px;margin-left:10px">
              <el-option label="低" value="low" />
              <el-option label="普通" value="normal" />
              <el-option label="高" value="high" />
            </el-select>
          </el-form-item>
          <el-form-item label="回复">
            <el-input v-model="ticketReply" type="textarea" :rows="3" maxlength="2000" show-word-limit placeholder="输入回复内容" />
          </el-form-item>
        </el-form>
      </template>
      <template #footer>
        <el-button @click="ticketDrawer = false">关闭</el-button>
        <el-button :loading="ticketSaving" @click="saveTicketMeta">保存状态</el-button>
        <el-button type="primary" :loading="ticketSaving" :disabled="!ticketReply.trim()" @click="sendTicketReply">发送回复</el-button>
      </template>
    </el-drawer>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { adminApi } from '@/api'
import { ElMessage } from 'element-plus'

const activeTab = ref('refunds')

const refundLoading = ref(false)
const refunds = ref([])
const refundTotal = ref(0)
const refundQuery = reactive({ page: 1, per_page: 20, status: '' })
const refundDialog = ref(false)
const refundSaving = ref(false)
const currentRefund = ref(null)
const refundNote = ref('')

const ticketLoading = ref(false)
const tickets = ref([])
const ticketTotal = ref(0)
const ticketQuery = reactive({ page: 1, per_page: 20, status: '' })
const ticketDrawer = ref(false)
const ticketSaving = ref(false)
const currentTicket = ref(null)
const ticketReply = ref('')
const ticketStatus = ref('processing')
const ticketPriority = ref('normal')

function refundLabel(s) { return { pending: '待处理', approved: '已通过', rejected: '已驳回', cancelled: '已取消' }[s] || s }
function refundClass(s) { return { pending: 'pn-tag-warn', approved: 'pn-tag-ok', rejected: 'pn-tag-danger', cancelled: 'pn-tag-soft' }[s] || 'pn-tag-soft' }
function ticketLabel(s) { return { open: '待处理', processing: '处理中', closed: '已关闭' }[s] || s }
function ticketClass(s) { return { open: 'pn-tag-warn', processing: 'pn-tag-brand', closed: 'pn-tag-soft' }[s] || 'pn-tag-soft' }
function priorityLabel(p) { return { low: '低', normal: '普通', high: '高' }[p] || p }
function priorityClass(p) { return { low: 'pn-tag-soft', normal: 'pn-tag-soft', high: 'pn-tag-danger' }[p] || 'pn-tag-soft' }
function categoryLabel(c) {
  return { order: '订单问题', refund: '退款问题', service: '服务投诉', account: '账号问题', other: '其他' }[c] || c || '其他'
}

async function loadRefunds() {
  refundLoading.value = true
  try {
    const res = await adminApi.getRefundRequests(refundQuery)
    refunds.value = res.data?.items || []
    refundTotal.value = res.data?.total || 0
  } catch (e) {
    ElMessage.error(e.message || '加载退款申请失败')
  } finally {
    refundLoading.value = false
  }
}

async function loadTickets() {
  ticketLoading.value = true
  try {
    const res = await adminApi.getTickets(ticketQuery)
    tickets.value = res.data?.items || []
    ticketTotal.value = res.data?.total || 0
  } catch (e) {
    ElMessage.error(e.message || '加载工单失败')
  } finally {
    ticketLoading.value = false
  }
}

function onTabChange(name) {
  if (name === 'refunds' && !refunds.value.length) loadRefunds()
  if (name === 'tickets' && !tickets.value.length) loadTickets()
}

function openRefund(row) {
  currentRefund.value = row
  refundNote.value = row.admin_note || ''
  refundDialog.value = true
}

async function handleRefund(action) {
  if (action === 'reject' && !refundNote.value.trim()) {
    ElMessage.warning('驳回时请填写原因')
    return
  }
  refundSaving.value = true
  try {
    await adminApi.handleRefundRequest(currentRefund.value.id, { action, note: refundNote.value.trim() })
    ElMessage.success(action === 'approve' ? '退款已完成' : '已驳回申请')
    refundDialog.value = false
    loadRefunds()
  } catch (e) {
    ElMessage.error(e.message || '处理失败')
  } finally {
    refundSaving.value = false
  }
}

async function openTicket(row) {
  try {
    const res = await adminApi.getTicket(row.id)
    currentTicket.value = res.data
    ticketStatus.value = res.data.status
    ticketPriority.value = res.data.priority
    ticketReply.value = ''
    ticketDrawer.value = true
  } catch (e) {
    ElMessage.error(e.message || '加载工单失败')
  }
}

async function saveTicketMeta() {
  ticketSaving.value = true
  try {
    await adminApi.updateTicket(currentTicket.value.id, {
      status: ticketStatus.value,
      priority: ticketPriority.value,
    })
    ElMessage.success('工单状态已更新')
    loadTickets()
  } catch (e) {
    ElMessage.error(e.message || '保存失败')
  } finally {
    ticketSaving.value = false
  }
}

async function sendTicketReply() {
  ticketSaving.value = true
  try {
    await adminApi.replyTicket(currentTicket.value.id, { content: ticketReply.value.trim() })
    ElMessage.success('回复已发送')
    ticketReply.value = ''
    const res = await adminApi.getTicket(currentTicket.value.id)
    currentTicket.value = res.data
    ticketStatus.value = res.data.status
    loadTickets()
  } catch (e) {
    ElMessage.error(e.message || '发送失败')
  } finally {
    ticketSaving.value = false
  }
}

onMounted(loadRefunds)
</script>

<style scoped>
.tab-tools { margin-bottom: 14px; }
.ticket-head { border-bottom: 1px solid var(--line2); padding-bottom: 14px; }
.ticket-title { font-size: 16px; font-weight: 600; color: var(--ink); }
.ticket-meta { font-size: 12.5px; color: var(--ink3); margin-top: 4px; }
.ticket-tags { display: flex; gap: 8px; margin-top: 10px; }
.ticket-content { margin-top: 14px; font-size: 13.5px; color: var(--ink2); line-height: 1.7; white-space: pre-wrap; }
.messages { padding: 16px 0; display: flex; flex-direction: column; gap: 12px; }
.message { max-width: 86%; }
.message.mine { align-self: flex-end; }
.message-meta { font-size: 12px; color: var(--ink3); margin-bottom: 4px; }
.message.mine .message-meta { text-align: right; }
.message-body {
  background: var(--line2); border-radius: 8px; padding: 9px 12px;
  font-size: 13.5px; color: var(--ink); white-space: pre-wrap;
}
.message.mine .message-body { background: var(--brand-soft); color: var(--brand-ink); }
.empty-tip { text-align: center; color: var(--ink3); font-size: 13px; padding: 20px 0; }
.ticket-form { border-top: 1px solid var(--line2); padding-top: 16px; }
</style>
