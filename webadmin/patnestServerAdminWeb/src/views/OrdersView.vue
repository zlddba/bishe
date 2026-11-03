<template>
  <div class="page-container">
    <div class="page-h">
      <div><h2>订单管理</h2><p>全部寄养订单与支付状态</p></div>
      <div class="ml-auto filter-bar">
        <el-input v-model="query.q" placeholder="订单号/机构/主人" clearable style="width:180px" @keyup.enter="loadData" @clear="loadData" />
        <el-select v-model="query.status" placeholder="全部状态" clearable style="width:130px" @change="loadData">
          <el-option v-for="s in statusOptions" :key="s.value" :label="s.label" :value="s.value" />
        </el-select>
        <el-button type="primary" @click="loadData">查询</el-button>
        <el-button :loading="exporting" @click="exportCSV">导出 CSV</el-button>
        <el-button type="primary" @click="openCreate">新增订单</el-button>
      </div>
    </div>

    <div class="table-card">
      <el-table :data="orders" v-loading="loading" stripe>
        <el-table-column prop="id" label="编号" width="60" />
        <el-table-column prop="order_no" label="订单号" width="190" />
        <el-table-column prop="sitter_name" label="机构" min-width="140" />
        <el-table-column prop="owner_name" label="主人" width="110" />
        <el-table-column label="寄养日期" width="200">
          <template #default="{ row }">{{ row.start_date }} ~ {{ row.end_date }}</template>
        </el-table-column>
        <el-table-column prop="pet_count" label="宠物" width="60" />
        <el-table-column label="金额" width="90">
          <template #default="{ row }">{{ Number(row.total_amount || row.estimated_amount || 0).toFixed(2) }}</template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="90">
          <template #default="{ row }">
            <span class="pn-tag" :class="statusClass(row.status)">{{ statusLabel(row.status) }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="payment_status" label="支付" width="80">
          <template #default="{ row }">
            <span class="pn-tag" :class="row.payment_status === 'paid' ? 'pn-tag-ok' : row.payment_status === 'refunded' ? 'pn-tag-warn' : 'pn-tag-soft'">
              {{ paymentLabel(row.payment_status) }}
            </span>
          </template>
        </el-table-column>
        <el-table-column prop="created_at" label="创建时间" min-width="150" />
        <el-table-column label="操作" width="210" fixed="right">
          <template #default="{ row }">
            <el-button size="small" @click="openDetail(row)">详情</el-button>
            <el-button size="small" @click="openEdit(row)">编辑</el-button>
            <el-button type="danger" size="small" @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <div class="pagination-wrap">
        <el-pagination v-model:current-page="query.page" v-model:page-size="query.per_page" :total="total" :page-sizes="[10,20,50]" layout="total, sizes, prev, pager, next" @change="loadData" />
      </div>
    </div>

    <el-dialog v-model="formDialog" :title="editing ? '编辑订单' : '新增订单'" width="560px">
      <el-form :model="form" label-width="96px">
        <el-form-item label="下单主人">
          <el-select v-model="form.owner_id" filterable placeholder="选择主人" style="width:100%" :disabled="editing" @change="onOwnerChange">
            <el-option v-for="u in ownerOptions" :key="u.id" :label="u.nickname ? `${u.nickname} (${u.username})` : u.username" :value="u.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="寄养机构">
          <el-select v-model="form.sitter_id" filterable placeholder="选择机构" style="width:100%" :disabled="editing">
            <el-option v-for="s in sitterOptions" :key="s.id" :label="`${s.name}（${typeLabel(s.type)}）`" :value="s.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="寄养宠物">
          <el-select v-model="form.pet_ids" multiple placeholder="选择宠物" style="width:100%" no-data-text="该主人暂无宠物">
            <el-option v-for="p in petOptions" :key="p.id" :label="`${p.name}（${speciesLabel(p.species)}）`" :value="p.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="寄养日期">
          <el-date-picker
            v-model="form.date_range"
            type="daterange"
            value-format="YYYY-MM-DD"
            range-separator="至"
            start-placeholder="开始日期"
            end-placeholder="结束日期"
            style="width:100%"
          />
        </el-form-item>
        <el-form-item label="订单状态">
          <el-select v-model="form.status" style="width:100%">
            <el-option v-for="s in statusOptions" :key="s.value" :label="s.label" :value="s.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="支付状态">
          <el-select v-model="form.payment_status" style="width:100%">
            <el-option label="未支付" value="none" />
            <el-option label="已支付" value="paid" />
            <el-option label="已退款" value="refunded" />
          </el-select>
        </el-form-item>
        <el-form-item label="预估金额">
          <el-input-number v-model="form.estimated_amount" :min="0" :precision="2" :controls="false" placeholder="留空按机构价格计算" style="width:100%" />
        </el-form-item>
        <el-form-item label="订单金额">
          <el-input-number v-model="form.total_amount" :min="0" :precision="2" :controls="false" placeholder="留空同预估金额" style="width:100%" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="form.remark" type="textarea" :rows="2" placeholder="选填" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="formDialog = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submitForm">保存</el-button>
      </template>
    </el-dialog>

    <el-drawer v-model="detailDrawer" title="订单详情" size="640px">
      <div v-if="detail" class="order-detail">
        <div class="od-head">
          <div>
            <div class="od-no">{{ detail.order.order_no }}</div>
            <div class="od-meta">{{ detail.order.sitter_name }} · {{ detail.order.owner_name }}</div>
          </div>
          <div class="od-tags">
            <span class="pn-tag" :class="statusClass(detail.order.status)">{{ statusLabel(detail.order.status) }}</span>
            <span class="pn-tag" :class="detail.order.payment_status === 'paid' ? 'pn-tag-ok' : detail.order.payment_status === 'refunded' ? 'pn-tag-warn' : 'pn-tag-soft'">
              {{ paymentLabel(detail.order.payment_status) }}
            </span>
          </div>
        </div>

        <el-descriptions :column="2" border size="small">
          <el-descriptions-item label="寄养日期">{{ detail.order.start_date }} ~ {{ detail.order.end_date }}</el-descriptions-item>
          <el-descriptions-item label="寄养天数">{{ detail.order.total_days }} 天</el-descriptions-item>
          <el-descriptions-item label="宠物数量">{{ detail.order.pet_count }} 只</el-descriptions-item>
          <el-descriptions-item label="订单金额">{{ Number(detail.order.total_amount).toFixed(2) }} 元</el-descriptions-item>
          <el-descriptions-item label="创建时间">{{ detail.order.created_at }}</el-descriptions-item>
          <el-descriptions-item label="备注">{{ detail.remark || '-' }}</el-descriptions-item>
        </el-descriptions>

        <div class="od-sub">寄养宠物</div>
        <el-table :data="detail.pets" size="small" border empty-text="暂无宠物">
          <el-table-column prop="pet_name" label="宠物名称" />
          <el-table-column prop="pet_id" label="宠物编号" width="100" />
        </el-table>

        <div class="od-sub">支付 / 退款流水</div>
        <el-table :data="detail.payments" size="small" border empty-text="暂无支付记录">
          <el-table-column label="金额" width="90">
            <template #default="{ row }">{{ Number(row.amount).toFixed(2) }}</template>
          </el-table-column>
          <el-table-column prop="method" label="方式" width="90">
            <template #default="{ row }">{{ methodLabel(row.method) }}</template>
          </el-table-column>
          <el-table-column prop="status" label="状态" width="90">
            <template #default="{ row }">{{ payRecordStatusLabel(row.status) }}</template>
          </el-table-column>
          <el-table-column prop="paid_at" label="支付时间" width="130" />
          <el-table-column prop="refunded_at" label="退款时间" width="130" />
          <el-table-column prop="raw_response" label="备注" min-width="160" show-overflow-tooltip />
        </el-table>

        <div class="od-sub">寄养动态 <span class="od-count">{{ (detail.dynamics || []).length }}</span></div>
        <div v-if="(detail.dynamics || []).length" class="od-dyn">
          <div v-for="d in detail.dynamics" :key="d.id" class="od-dyn-item">
            <div class="od-dyn-time">{{ d.created_at }}</div>
            <div class="od-dyn-text">{{ d.content }}</div>
            <div v-if="d.photos && d.photos.length" class="od-dyn-imgs">
              <img v-for="p in d.photos" :key="p" :src="p" alt="" />
            </div>
          </div>
        </div>
        <div v-else class="od-empty">暂无寄养动态</div>

        <div class="od-sub">评价</div>
        <div v-if="detail.review">
          <div class="od-review">
            <span class="od-score">{{ detail.review.overall_score }}</span>
            <span class="od-review-text">{{ detail.review.owner_comment || '无文字评价' }}</span>
          </div>
          <div v-if="detail.review.reply_text" class="od-reply">机构回复：{{ detail.review.reply_text }}</div>
        </div>
        <div v-else class="od-empty">暂无评价</div>
      </div>
      <template #footer>
        <div style="display:flex;justify-content:flex-end;gap:8px">
          <el-button @click="detailDrawer = false">关闭</el-button>
          <el-button v-if="detail && detail.order.payment_status === 'paid'" type="warning" @click="openRefund">退款</el-button>
          <el-button type="primary" @click="editFromDetail">编辑订单</el-button>
        </div>
      </template>
    </el-drawer>

    <el-dialog v-model="refundDialog" title="订单退款" width="460px">
      <el-form label-width="90px">
        <el-form-item label="订单金额">
          <span>{{ Number(detail?.order?.total_amount || 0).toFixed(2) }} 元</span>
        </el-form-item>
        <el-form-item label="退款金额">
          <el-input-number v-model="refundForm.amount" :min="0.01" :max="Number(detail?.order?.total_amount || 0)" :precision="2" style="width:100%" />
        </el-form-item>
        <el-form-item label="退款原因">
          <el-input v-model="refundForm.reason" type="textarea" :rows="3" maxlength="255" show-word-limit placeholder="如：机构临时停业，全额退款" />
        </el-form-item>
      </el-form>
      <div class="od-tip">退款后订单标记为已退款，进行中的订单会同时取消并释放机构容量。</div>
      <template #footer>
        <el-button @click="refundDialog = false">取消</el-button>
        <el-button type="warning" :loading="refunding" @click="submitRefund">确认退款</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { adminApi } from '@/api'
import { ElMessage, ElMessageBox } from 'element-plus'
import { downloadCSV } from '@/utils/download'

const loading = ref(false)
const exporting = ref(false)
const saving = ref(false)
const orders = ref([])
const total = ref(0)
const query = reactive({ page: 1, per_page: 20, status: '', q: '' })

const ownerOptions = ref([])
const sitterOptions = ref([])
const petOptions = ref([])

const detailDrawer = ref(false)
const detail = ref(null)
const refundDialog = ref(false)
const refunding = ref(false)
const refundForm = reactive({ amount: 0, reason: '' })

const statusOptions = [
  { label: '待支付', value: 'pending_pay' },
  { label: '已确认', value: 'confirmed' },
  { label: '已接单', value: 'accepted' },
  { label: '寄养中', value: 'staying' },
  { label: '已完成', value: 'completed' },
  { label: '已评价', value: 'evaluated' },
  { label: '已取消', value: 'cancelled' },
  { label: '已拒绝', value: 'rejected' },
]

const formDialog = ref(false)
const editing = ref(false)
const form = reactive({
  id: null,
  owner_id: null,
  sitter_id: null,
  pet_ids: [],
  date_range: [],
  status: 'pending_pay',
  payment_status: 'none',
  estimated_amount: undefined,
  total_amount: undefined,
  remark: '',
})

function statusClass(s) {
  return { pending_pay:'pn-tag-warn', confirmed:'pn-tag-brand', accepted:'pn-tag-ok', staying:'pn-tag-ok', completed:'pn-tag-ok', evaluated:'pn-tag-ok', cancelled:'pn-tag-soft', rejected:'pn-tag-danger' }[s] || 'pn-tag-soft'
}
function statusLabel(s) {
  return statusOptions.find(o => o.value === s)?.label || s
}
function paymentLabel(s) {
  return { none: '未付', paid: '已付', refunded: '已退' }[s] || s
}
function methodLabel(m) {
  return { mock: '模拟支付', alipay: '支付宝', wechat: '微信支付', balance: '余额支付' }[m] || m
}
function payRecordStatusLabel(s) {
  return { created: '已创建', paid: '支付成功', failed: '支付失败', refunded: '已退款' }[s] || s
}
function typeLabel(t) {
  return { home: '家庭', shop: '门店', visit: '上门', other: '其他' }[t] || t
}
function speciesLabel(s) {
  return { cat: '猫', dog: '狗', other: '其他' }[s] || s
}

async function loadData() {
  loading.value = true
  try {
    const res = await adminApi.getOrders(query)
    orders.value = res.data?.items || []
    total.value = res.data?.total || 0
  } catch (e) {
    ElMessage.error(e.message || '加载订单失败')
  } finally {
    loading.value = false
  }
}

async function loadOptions() {
  try {
    const [owners, sitters] = await Promise.all([
      adminApi.getUsers({ role: 'owner', page: 1, per_page: 100 }),
      adminApi.getSitters({ page: 1, per_page: 100 }),
    ])
    ownerOptions.value = owners.data?.items || []
    sitterOptions.value = sitters.data?.items || []
  } catch (e) {
    console.error(e)
  }
}

async function loadPetOptions(ownerId) {
  petOptions.value = []
  if (!ownerId) return
  try {
    const res = await adminApi.getPets({ owner_id: ownerId, page: 1, per_page: 100 })
    petOptions.value = res.data?.items || []
  } catch (e) {
    console.error(e)
  }
}

function onOwnerChange(ownerId) {
  form.pet_ids = []
  loadPetOptions(ownerId)
}

function openCreate() {
  editing.value = false
  Object.assign(form, {
    id: null, owner_id: null, sitter_id: null, pet_ids: [], date_range: [],
    status: 'pending_pay', payment_status: 'none',
    estimated_amount: undefined, total_amount: undefined, remark: '',
  })
  petOptions.value = []
  formDialog.value = true
}

async function openEdit(row) {
  editing.value = true
  try {
    const res = await adminApi.getOrder(row.id)
    const data = res.data || {}
    const o = data.order || {}
    await loadPetOptions(data.owner_id)
    Object.assign(form, {
      id: o.id,
      owner_id: data.owner_id,
      sitter_id: data.sitter_id,
      pet_ids: data.pet_ids || [],
      date_range: [o.start_date, o.end_date],
      status: o.status,
      payment_status: o.payment_status,
      estimated_amount: Number(o.estimated_amount ?? 0),
      total_amount: Number(o.total_amount ?? 0),
      remark: data.remark || '',
    })
    formDialog.value = true
  } catch (e) {
    ElMessage.error(e.message || '加载订单详情失败')
  }
}

async function openDetail(row) {
  try {
    const res = await adminApi.getOrder(row.id)
    detail.value = res.data || null
    detailDrawer.value = true
  } catch (e) {
    ElMessage.error(e.message || '加载订单详情失败')
  }
}

function editFromDetail() {
  if (!detail.value) return
  detailDrawer.value = false
  openEdit(detail.value.order)
}

function openRefund() {
  refundForm.amount = Number(detail.value?.order?.total_amount || 0)
  refundForm.reason = ''
  refundDialog.value = true
}

async function submitRefund() {
  if (!refundForm.reason.trim()) {
    ElMessage.warning('请填写退款原因')
    return
  }
  refunding.value = true
  try {
    await adminApi.refundOrder(detail.value.order.id, {
      amount: refundForm.amount,
      reason: refundForm.reason.trim(),
    })
    ElMessage.success('退款已完成')
    refundDialog.value = false
    const res = await adminApi.getOrder(detail.value.order.id)
    detail.value = res.data || null
    loadData()
  } catch (e) {
    ElMessage.error(e.message || '退款失败')
  } finally {
    refunding.value = false
  }
}

async function submitForm() {
  if (!editing.value && (!form.owner_id || !form.sitter_id)) {
    ElMessage.warning('请选择主人和机构')
    return
  }
  if (!form.date_range || form.date_range.length !== 2) {
    ElMessage.warning('请选择寄养日期')
    return
  }
  if (!form.pet_ids || form.pet_ids.length === 0) {
    ElMessage.warning('请至少选择一只宠物')
    return
  }

  const payload = {
    start_date: form.date_range[0],
    end_date: form.date_range[1],
    pet_ids: form.pet_ids,
    status: form.status,
    payment_status: form.payment_status,
    remark: form.remark || '',
  }
  if (form.estimated_amount !== undefined && form.estimated_amount !== null) payload.estimated_amount = form.estimated_amount
  if (form.total_amount !== undefined && form.total_amount !== null) payload.total_amount = form.total_amount
  if (!editing.value) {
    payload.owner_id = form.owner_id
    payload.sitter_id = form.sitter_id
  }

  saving.value = true
  try {
    if (editing.value) {
      await adminApi.updateOrder(form.id, payload)
      ElMessage.success('订单已更新')
    } else {
      await adminApi.createOrder(payload)
      ElMessage.success('订单创建成功')
    }
    formDialog.value = false
    loadData()
  } catch (e) {
    ElMessage.error(e.message || '操作失败')
  } finally {
    saving.value = false
  }
}

async function handleDelete(row) {
  try {
    await ElMessageBox.confirm('确认删除订单「' + row.order_no + '」吗？删除后机构容量会被释放。', '删除确认', { type: 'warning' })
    await adminApi.deleteOrder(row.id)
    ElMessage.success('订单已删除')
    loadData()
  } catch (e) {
    if (e !== 'cancel') ElMessage.error(e.message || '删除失败')
  }
}

async function exportCSV() {
  exporting.value = true
  try {
    const res = await adminApi.exportCSV('orders')
    downloadCSV(res, 'patnest-orders')
  } catch (e) {
    ElMessage.error(e.message || '导出失败')
  } finally {
    exporting.value = false
  }
}

onMounted(() => {
  loadData()
  loadOptions()
})
</script>

<style scoped>
.od-head {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: 14px;
}
.od-no { font-size: 16px; font-weight: 600; color: var(--ink); }
.od-meta { font-size: 12px; color: var(--ink3); margin-top: 4px; }
.od-tags { display: flex; gap: 6px; }
.od-sub {
  font-size: 13px;
  font-weight: 600;
  color: var(--ink);
  margin: 18px 0 10px;
}
.od-count { color: var(--ink3); font-weight: 400; }
.od-dyn { display: flex; flex-direction: column; gap: 10px; }
.od-dyn-item {
  border: 1px solid var(--line);
  border-radius: 8px;
  padding: 10px 12px;
}
.od-dyn-time { font-size: 12px; color: var(--ink3); margin-bottom: 4px; }
.od-dyn-text { font-size: 13px; color: var(--ink2); white-space: pre-wrap; }
.od-dyn-imgs { display: flex; gap: 6px; margin-top: 8px; flex-wrap: wrap; }
.od-dyn-imgs img { width: 72px; height: 72px; object-fit: cover; border-radius: 6px; }
.od-empty { font-size: 13px; color: var(--ink3); padding: 6px 0 2px; }
.od-review { display: flex; align-items: baseline; gap: 10px; }
.od-score { font-size: 20px; font-weight: 700; color: var(--brand); }
.od-review-text { font-size: 13px; color: var(--ink2); }
.od-reply { margin-top: 6px; font-size: 12px; color: var(--ink3); }
.od-tip { font-size: 12px; color: var(--ink3); line-height: 1.6; }
</style>
