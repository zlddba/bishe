<template>
  <div class="page-container">
    <div class="page-h">
      <div><h2>机构结算</h2><p>机构提现申请与平台打款记录</p></div>
      <div class="ml-auto filter-bar">
        <el-select v-model="query.status" placeholder="全部状态" clearable style="width:120px" @change="loadData">
          <el-option label="待打款" value="pending" />
          <el-option label="已打款" value="paid" />
          <el-option label="已驳回" value="rejected" />
        </el-select>
        <el-button type="primary" @click="loadData">查询</el-button>
      </div>
    </div>

    <div class="table-card">
      <el-table :data="items" v-loading="loading" stripe>
        <el-table-column prop="id" label="单号" width="70" />
        <el-table-column prop="sitter" label="机构" min-width="150" />
        <el-table-column label="结算周期" min-width="190">
          <template #default="{ row }">{{ row.period_start }} ~ {{ row.period_end }}</template>
        </el-table-column>
        <el-table-column prop="order_count" label="订单数" width="80" />
        <el-table-column label="流水金额" width="110">
          <template #default="{ row }">¥{{ Number(row.gross_amount).toFixed(2) }}</template>
        </el-table-column>
        <el-table-column label="平台佣金" width="110">
          <template #default="{ row }">¥{{ Number(row.commission_amount).toFixed(2) }}</template>
        </el-table-column>
        <el-table-column label="应打款" width="110">
          <template #default="{ row }"><strong>¥{{ Number(row.net_amount).toFixed(2) }}</strong></template>
        </el-table-column>
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <span class="pn-tag" :class="statusClass(row.status)">{{ statusLabel(row.status) }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="applied_at" label="申请时间" width="150" />
        <el-table-column prop="paid_at" label="打款时间" width="150">
          <template #default="{ row }">{{ row.paid_at || '-' }}</template>
        </el-table-column>
        <el-table-column label="操作" width="100" fixed="right">
          <template #default="{ row }">
            <el-button v-if="row.status === 'pending'" type="primary" size="small" @click="openHandle(row)">处理</el-button>
            <el-button v-else size="small" @click="openHandle(row)">查看</el-button>
          </template>
        </el-table-column>
      </el-table>
      <div class="pagination-wrap">
        <el-pagination v-model:current-page="query.page" v-model:page-size="query.per_page" :total="total" :page-sizes="[10,20,50]" layout="total, sizes, prev, pager, next" @change="loadData" />
      </div>
    </div>

    <el-dialog v-model="dialog" title="结算处理" width="520px">
      <template v-if="current">
        <el-descriptions :column="2" border size="small">
          <el-descriptions-item label="机构" :span="2">{{ current.sitter }}</el-descriptions-item>
          <el-descriptions-item label="结算周期" :span="2">{{ current.period_start }} ~ {{ current.period_end }}</el-descriptions-item>
          <el-descriptions-item label="订单数">{{ current.order_count }}</el-descriptions-item>
          <el-descriptions-item label="状态">
            <span class="pn-tag" :class="statusClass(current.status)">{{ statusLabel(current.status) }}</span>
          </el-descriptions-item>
          <el-descriptions-item label="流水金额">¥{{ Number(current.gross_amount).toFixed(2) }}</el-descriptions-item>
          <el-descriptions-item label="平台佣金">¥{{ Number(current.commission_amount).toFixed(2) }}</el-descriptions-item>
          <el-descriptions-item label="应打款" :span="2"><strong>¥{{ Number(current.net_amount).toFixed(2) }}</strong></el-descriptions-item>
          <el-descriptions-item v-if="current.note" label="备注" :span="2">{{ current.note }}</el-descriptions-item>
        </el-descriptions>
        <el-form v-if="current.status === 'pending'" label-width="80px" style="margin-top:16px">
          <el-form-item label="处理备注">
            <el-input v-model="note" type="textarea" :rows="3" maxlength="500" show-word-limit placeholder="驳回时建议填写原因" />
          </el-form-item>
        </el-form>
      </template>
      <template #footer>
        <el-button @click="dialog = false">关闭</el-button>
        <template v-if="current?.status === 'pending'">
          <el-button type="danger" :loading="saving" @click="handle('reject')">驳回</el-button>
          <el-button type="primary" :loading="saving" @click="handle('pay')">确认打款</el-button>
        </template>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { adminApi } from '@/api'
import { ElMessage } from 'element-plus'

const loading = ref(false)
const saving = ref(false)
const items = ref([])
const total = ref(0)
const query = reactive({ page: 1, per_page: 20, status: '' })

const dialog = ref(false)
const current = ref(null)
const note = ref('')

function statusLabel(s) { return { pending: '待打款', paid: '已打款', rejected: '已驳回' }[s] || s }
function statusClass(s) { return { pending: 'pn-tag-warn', paid: 'pn-tag-ok', rejected: 'pn-tag-danger' }[s] || 'pn-tag-soft' }

async function loadData() {
  loading.value = true
  try {
    const res = await adminApi.getSettlements(query)
    items.value = res.data?.items || []
    total.value = res.data?.total || 0
  } catch (e) {
    ElMessage.error(e.message || '加载结算单失败')
  } finally {
    loading.value = false
  }
}

function openHandle(row) {
  current.value = row
  note.value = row.note || ''
  dialog.value = true
}

async function handle(action) {
  if (action === 'reject' && !note.value.trim()) {
    ElMessage.warning('驳回时请填写原因')
    return
  }
  saving.value = true
  try {
    await adminApi.paySettlement(current.value.id, { action, note: note.value.trim() })
    ElMessage.success(action === 'pay' ? '已确认打款' : '已驳回')
    dialog.value = false
    loadData()
  } catch (e) {
    ElMessage.error(e.message || '处理失败')
  } finally {
    saving.value = false
  }
}

onMounted(loadData)
</script>
