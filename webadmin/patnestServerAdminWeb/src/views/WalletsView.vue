<template>
  <div class="page-container">
    <el-tabs v-model="activeTab" class="wallet-tabs">
      <!-- 钱包列表 -->
      <el-tab-pane label="钱包列表" name="wallets">
        <div class="page-h">
          <div><h2>钱包管理</h2><p>用户与机构的资金账户总览</p></div>
          <div class="ml-auto filter-bar">
            <el-input
              v-model="query.keyword"
              placeholder="用户名 / 昵称 / 手机号"
              clearable
              style="width:230px"
              @keyup.enter="loadWallets"
              @clear="loadWallets"
            />
            <el-button type="primary" @click="loadWallets">查询</el-button>
          </div>
        </div>

        <div class="table-card">
          <el-table
            :data="wallets"
            v-loading="walletLoading"
            stripe
            @row-click="openDetail"
            style="cursor:pointer"
          >
            <el-table-column prop="username" label="用户名" min-width="120" />
            <el-table-column prop="nickname" label="昵称" min-width="120" />
            <el-table-column label="角色" width="100">
              <template #default="{ row }">{{ roleLabel(row.role) }}</template>
            </el-table-column>
            <el-table-column label="可用余额" width="130" sortable :sort-method="(a,b)=>a.balance-b.balance">
              <template #default="{ row }"><span class="amt">{{ money(row.balance) }}</span></template>
            </el-table-column>
            <el-table-column label="冻结余额" width="130" sortable :sort-method="(a,b)=>a.frozen_balance-b.frozen_balance">
              <template #default="{ row }">{{ money(row.frozen_balance) }}</template>
            </el-table-column>
            <el-table-column label="累计充值" width="130">
              <template #default="{ row }">{{ money(row.total_recharge) }}</template>
            </el-table-column>
            <el-table-column label="累计消费" width="130">
              <template #default="{ row }">{{ money(row.total_consume) }}</template>
            </el-table-column>
            <el-table-column prop="updated_at" label="更新时间" min-width="160" />
            <el-table-column label="操作" width="90" fixed="right">
              <template #default="{ row }">
                <el-button size="small" @click.stop="openDetail(row)">详情</el-button>
              </template>
            </el-table-column>
          </el-table>
          <div class="pagination-wrap">
            <el-pagination
              v-model:current-page="query.page"
              v-model:page-size="query.per_page"
              :total="walletTotal"
              :page-sizes="[10,20,50]"
              layout="total, sizes, prev, pager, next"
              @change="loadWallets"
            />
          </div>
        </div>
      </el-tab-pane>

      <!-- 充值对账 -->
      <el-tab-pane label="充值对账" name="recharges">
        <div class="page-h">
          <div><h2>充值对账</h2><p>用户充值单与支付渠道核对</p></div>
          <div class="ml-auto filter-bar">
            <el-select v-model="rechargeQuery.status" placeholder="全部状态" clearable style="width:130px" @change="loadRecharges">
              <el-option label="待确认" value="pending" />
              <el-option label="已支付" value="paid" />
              <el-option label="失败" value="failed" />
            </el-select>
            <el-button type="primary" @click="loadRecharges">查询</el-button>
          </div>
        </div>

        <div class="table-card">
          <el-table :data="recharges" v-loading="rechargeLoading" stripe>
            <el-table-column prop="id" label="充值单号" width="90" />
            <el-table-column prop="username" label="用户" min-width="120" />
            <el-table-column prop="nickname" label="昵称" min-width="120" />
            <el-table-column label="金额" width="130">
              <template #default="{ row }"><span class="amt">{{ money(row.amount) }}</span></template>
            </el-table-column>
            <el-table-column prop="channel" label="渠道" width="110" />
            <el-table-column label="状态" width="100">
              <template #default="{ row }">
                <span class="pn-tag" :class="rechargeStatusClass(row.status)">{{ rechargeStatusLabel(row.status) }}</span>
              </template>
            </el-table-column>
            <el-table-column prop="transaction_id" label="交易号" min-width="180" />
            <el-table-column prop="paid_at" label="支付时间" min-width="160">
              <template #default="{ row }">{{ row.paid_at || '-' }}</template>
            </el-table-column>
            <el-table-column prop="created_at" label="创建时间" min-width="160" />
            <el-table-column label="操作" width="120" fixed="right">
              <template #default="{ row }">
                <el-button v-if="row.status === 'pending'" type="primary" size="small" :loading="confirmingId === row.id" @click="confirmRecharge(row)">手动确认</el-button>
                <span v-else style="color:var(--ink3);font-size:12px">已处理</span>
              </template>
            </el-table-column>
          </el-table>
          <div class="pagination-wrap">
            <el-pagination
              v-model:current-page="rechargeQuery.page"
              v-model:page-size="rechargeQuery.per_page"
              :total="rechargeTotal"
              :page-sizes="[10,20,50]"
              layout="total, sizes, prev, pager, next"
              @change="loadRecharges"
            />
          </div>
        </div>
      </el-tab-pane>

      <!-- 余额退款：账号注销时钱包余额自动转成退款单 -->
      <el-tab-pane label="余额退款" name="withdrawals">
        <div class="page-h">
          <div>
            <h2>余额退款</h2>
            <p>账号注销时钱包余额自动转成退款单，线下打款后在这里确认</p>
          </div>
          <div class="ml-auto filter-bar">
            <el-input
              v-model="withdrawalQuery.keyword"
              placeholder="用户名 / 昵称 / 手机号"
              clearable
              style="width:220px"
              @keyup.enter="loadWithdrawals"
              @clear="loadWithdrawals"
            />
            <el-select
              v-model="withdrawalQuery.status"
              placeholder="全部状态"
              clearable
              style="width:130px"
              @change="loadWithdrawals"
            >
              <el-option label="待打款" value="pending" />
              <el-option label="已打款" value="paid" />
              <el-option label="已驳回" value="rejected" />
            </el-select>
            <el-button type="primary" @click="loadWithdrawals">查询</el-button>
          </div>
        </div>

        <el-alert
          type="info"
          :closable="false"
          show-icon
          class="mb-16"
          title="这是「钱包余额」的退款，与机构收益结算无关。线下打款完成后点「确认打款」；点「驳回」会把金额原路退回用户钱包。"
        />

        <div class="table-card">
          <el-table :data="withdrawals" v-loading="withdrawalLoading" stripe>
            <el-table-column prop="id" label="单号" width="70" />
            <el-table-column label="用户" min-width="150">
              <template #default="{ row }">
                {{ row.nickname || row.username }}
                <span class="wd-role">{{ roleLabel(row.role) }}</span>
              </template>
            </el-table-column>
            <el-table-column label="退款金额" width="120">
              <template #default="{ row }"><strong class="amt">{{ money(row.amount) }}</strong></template>
            </el-table-column>
            <el-table-column label="联系方式" min-width="130">
              <template #default="{ row }">{{ row.contact || '-' }}</template>
            </el-table-column>
            <el-table-column label="来源" width="100">
              <template #default="{ row }">{{ row.source === 'cancel' ? '注销转出' : '主动申请' }}</template>
            </el-table-column>
            <el-table-column label="状态" width="95">
              <template #default="{ row }">
                <el-tag :type="wdTagType(row.status)" size="small">{{ wdStatusLabel(row.status) }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="applied_at" label="申请时间" width="150" />
            <el-table-column label="打款时间" width="150">
              <template #default="{ row }">{{ row.paid_at || '-' }}</template>
            </el-table-column>
            <el-table-column label="操作" width="168" fixed="right">
              <template #default="{ row }">
                <template v-if="row.status === 'pending'">
                  <el-button type="primary" size="small" @click="openWithdrawal(row, 'pay')">确认打款</el-button>
                  <el-button size="small" type="danger" plain @click="openWithdrawal(row, 'reject')">驳回</el-button>
                </template>
                <span v-else class="wd-done">{{ row.admin_note || '已处理' }}</span>
              </template>
            </el-table-column>
          </el-table>
          <div class="pagination-wrap">
            <el-pagination
              v-model:current-page="withdrawalQuery.page"
              v-model:page-size="withdrawalQuery.per_page"
              :total="withdrawalTotal"
              :page-sizes="[10,20,50]"
              layout="total, sizes, prev, pager, next"
              @change="loadWithdrawals"
            />
          </div>
        </div>
      </el-tab-pane>
    </el-tabs>

    <!-- 钱包详情抽屉 -->
    <el-drawer v-model="detailOpen" :title="`钱包详情 · ${detailUser}`" size="560px" @closed="currentWallet = null">
      <template v-if="currentWallet">
        <el-descriptions :column="2" border size="small" class="mb-16">
          <el-descriptions-item label="用户名">{{ walletDetail.username || currentWallet.username }}</el-descriptions-item>
          <el-descriptions-item label="角色">{{ roleLabel(walletDetail.role || currentWallet.role) }}</el-descriptions-item>
          <el-descriptions-item label="可用余额"><strong class="amt">{{ money(walletDetail.balance) }}</strong></el-descriptions-item>
          <el-descriptions-item label="冻结余额">{{ money(walletDetail.frozen_balance) }}</el-descriptions-item>
          <el-descriptions-item label="累计充值">{{ money(walletDetail.total_recharge) }}</el-descriptions-item>
          <el-descriptions-item label="累计消费">{{ money(walletDetail.total_consume) }}</el-descriptions-item>
        </el-descriptions>

        <div class="drawer-actions">
          <el-button type="warning" @click="openAdjust">人工调账</el-button>
          <el-button @click="refreshDetail">刷新流水</el-button>
        </div>

        <h4 class="section-title">资金流水</h4>
        <el-timeline v-loading="txLoading">
          <el-timeline-item
            v-for="tx in transactions"
            :key="tx.id"
            :timestamp="tx.created_at"
            placement="top"
          >
            <div class="tx-head">
              <span class="tx-type">{{ txTypeLabel(tx.type) }}</span>
              <span class="tx-amount" :class="tx.amount >= 0 ? 'pos' : 'neg'">
                {{ tx.amount >= 0 ? '+' : '' }}{{ money(tx.amount, false) }}
              </span>
            </div>
            <div class="tx-meta">
              <span>渠道：{{ tx.channel || '-' }}</span>
              <span>状态：{{ txStatusLabel(tx.status) }}</span>
              <span v-if="tx.ref_type">关联：{{ tx.ref_type }}#{{ tx.ref_id }}</span>
            </div>
            <div class="tx-meta">
              <span>交易号：{{ tx.transaction_id || '-' }}</span>
              <span v-if="tx.remark">备注：{{ tx.remark }}</span>
            </div>
            <div class="tx-balance">账后余额：{{ money(tx.balance_after) }}</div>
          </el-timeline-item>
          <el-empty v-if="!txLoading && transactions.length === 0" description="暂无流水" :image-size="60" />
        </el-timeline>
      </template>
    </el-drawer>

    <!-- 余额退款处理弹窗 -->
    <el-dialog
      v-model="wdDialogOpen"
      :title="wdAction === 'pay' ? '确认打款' : '驳回退款'"
      width="460px"
      @closed="wdCurrent = {}"
    >
      <el-descriptions :column="1" border size="small" class="mb-16">
        <el-descriptions-item label="单号">{{ wdCurrent.id }}</el-descriptions-item>
        <el-descriptions-item label="用户">{{ wdCurrent.nickname || wdCurrent.username }}</el-descriptions-item>
        <el-descriptions-item label="退款金额">
          <strong class="amt">{{ money(wdCurrent.amount) }}</strong>
        </el-descriptions-item>
        <el-descriptions-item label="联系方式">{{ wdCurrent.contact || '-' }}</el-descriptions-item>
      </el-descriptions>
      <el-alert
        v-if="wdAction === 'reject'"
        type="warning"
        :closable="false"
        show-icon
        class="mb-16"
        title="驳回后金额会原路退回用户钱包余额，并生成一条退款流水"
      />
      <template v-if="wdAction === 'pay'">
        <el-form label-width="86px" class="mb-8">
          <el-form-item label="收款人">
            <el-input v-model="wdAccountName" maxlength="64" placeholder="客服与用户确认后填写" />
          </el-form-item>
          <el-form-item label="收款账号">
            <el-input v-model="wdAccountNo" maxlength="128" placeholder="银行卡号 / 支付宝账号，建议脱敏保存" />
          </el-form-item>
        </el-form>
      </template>
      <el-input
        v-model="wdNote"
        type="textarea"
        :rows="2"
        maxlength="255"
        show-word-limit
        placeholder="备注（驳回时建议写明原因，用户可在客服处查询）"
      />
      <template #footer>
        <el-button @click="wdDialogOpen = false">取消</el-button>
        <el-button
          :type="wdAction === 'pay' ? 'primary' : 'danger'"
          :loading="wdSaving"
          @click="submitWithdrawal"
        >
          {{ wdAction === 'pay' ? '确认已打款' : '确认驳回' }}
        </el-button>
      </template>
    </el-dialog>

    <!-- 人工调账弹窗 -->
    <el-dialog v-model="adjustOpen" title="人工调账" width="460px" @closed="resetAdjust">
      <el-form label-width="80px">
        <el-alert
          type="warning"
          :closable="false"
          show-icon
          title="正数入账、负数扣减，操作将直接改动用户可用余额并生成流水记录"
          class="mb-16"
        />
        <el-form-item label="调整金额">
          <el-input-number v-model="adjust.amount" :precision="2" :step="10" :min="-100000" :max="100000" style="width:100%" />
          <div class="hint">可正可负，单位：元</div>
        </el-form-item>
        <el-form-item label="原因">
          <el-input v-model="adjust.reason" type="textarea" :rows="3" maxlength="200" show-word-limit placeholder="请填写调账原因，便于对账追溯" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="adjustOpen = false">取消</el-button>
        <el-button type="primary" :loading="adjusting" @click="submitAdjust">确认调账</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, watch } from 'vue'
import { adminApi } from '@/api'
import { ElMessage } from 'element-plus'

const activeTab = ref('wallets')

/* ---------- 钱包列表 ---------- */
const wallets = ref([])
const walletTotal = ref(0)
const walletLoading = ref(false)
const query = reactive({ keyword: '', page: 1, per_page: 20 })

async function loadWallets() {
  walletLoading.value = true
  try {
    const res = await adminApi.getWallets(query)
    wallets.value = res.data?.items || []
    walletTotal.value = res.data?.total || 0
  } catch (e) {
    ElMessage.error(e.message || '加载钱包列表失败')
  } finally {
    walletLoading.value = false
  }
}

/* ---------- 充值对账 ---------- */
const recharges = ref([])
const rechargeTotal = ref(0)
const rechargeLoading = ref(false)
const confirmingId = ref(null)
const rechargeQuery = reactive({ status: '', page: 1, per_page: 20 })

async function loadRecharges() {
  rechargeLoading.value = true
  try {
    const res = await adminApi.getRecharges(rechargeQuery)
    recharges.value = res.data?.items || []
    rechargeTotal.value = res.data?.total || 0
  } catch (e) {
    ElMessage.error(e.message || '加载充值单失败')
  } finally {
    rechargeLoading.value = false
  }
}

async function confirmRecharge(row) {
  confirmingId.value = row.id
  try {
    await adminApi.confirmRecharge(row.id)
    ElMessage.success('充值已确认')
    loadRecharges()
  } catch (e) {
    ElMessage.error(e.message || '确认失败')
  } finally {
    confirmingId.value = null
  }
}

/* ---------- 钱包详情 + 流水 + 调账 ---------- */
const detailOpen = ref(false)
const detailUser = ref('')
const currentWallet = ref(null)
const walletDetail = reactive({ username: '', role: '', balance: 0, frozen_balance: 0, total_recharge: 0, total_consume: 0 })
const transactions = ref([])
const txLoading = ref(false)

async function openDetail(row) {
  currentWallet.value = row
  detailUser.value = row.username || `#${row.user_id}`
  detailOpen.value = true
  await refreshDetail()
}

async function refreshDetail() {
  if (!currentWallet.value) return
  txLoading.value = true
  try {
    const res = await adminApi.getWalletDetail(currentWallet.value.user_id)
    const w = res.data?.wallet || {}
    Object.assign(walletDetail, {
      username: w.username ?? currentWallet.value.username,
      role: w.role ?? currentWallet.value.role,
      balance: w.balance ?? 0,
      frozen_balance: w.frozen_balance ?? 0,
      total_recharge: w.total_recharge ?? 0,
      total_consume: w.total_consume ?? 0,
    })
    transactions.value = res.data?.transactions || []
  } catch (e) {
    ElMessage.error(e.message || '加载钱包详情失败')
  } finally {
    txLoading.value = false
  }
}

/* 调账弹窗 */
const adjustOpen = ref(false)
const adjusting = ref(false)
const adjust = reactive({ amount: 0, reason: '' })

function openAdjust() {
  adjust.amount = 0
  adjust.reason = ''
  adjustOpen.value = true
}
function resetAdjust() {
  adjust.amount = 0
  adjust.reason = ''
}

async function submitAdjust() {
  if (!adjust.reason.trim()) {
    ElMessage.warning('请填写调账原因')
    return
  }
  adjusting.value = true
  try {
    const res = await adminApi.adjustWallet(currentWallet.value.user_id, {
      amount: Number(adjust.amount),
      reason: adjust.reason.trim(),
    })
    ElMessage.success('调账成功')
    adjustOpen.value = false
    // 联动：刷新详情余额 + 列表
    if (typeof res.data?.balance === 'number') {
      walletDetail.balance = res.data.balance
    }
    await refreshDetail()
    if (activeTab.value === 'wallets') loadWallets()
  } catch (e) {
    ElMessage.error(e.message || '调账失败')
  } finally {
    adjusting.value = false
  }
}

/* ---------- 余额退款 ---------- */
const withdrawals = ref([])
const withdrawalTotal = ref(0)
const withdrawalLoading = ref(false)
const withdrawalQuery = reactive({ status: '', keyword: '', page: 1, per_page: 20 })
const wdDialogOpen = ref(false)
const wdAction = ref('pay')
const wdCurrent = ref({})
const wdNote = ref('')
const wdAccountName = ref('')
const wdAccountNo = ref('')
const wdSaving = ref(false)

async function loadWithdrawals() {
  withdrawalLoading.value = true
  try {
    const res = await adminApi.getWalletWithdrawals(withdrawalQuery)
    withdrawals.value = res.data?.items || []
    withdrawalTotal.value = res.data?.total || 0
  } catch (e) {
    ElMessage.error(e.message || '加载退款单失败')
  } finally {
    withdrawalLoading.value = false
  }
}

function openWithdrawal(row, action) {
  wdCurrent.value = row
  wdAction.value = action
  wdNote.value = ''
  // 已有收款信息就带出来，客服只需补空缺
  wdAccountName.value = row.account_name || ''
  wdAccountNo.value = row.account_no || ''
  wdDialogOpen.value = true
}

async function submitWithdrawal() {
  const row = wdCurrent.value
  if (!row?.id) return
  wdSaving.value = true
  try {
    if (wdAction.value === 'pay') {
      await adminApi.payWalletWithdrawal(row.id, {
        note: wdNote.value.trim(),
        account_name: wdAccountName.value.trim(),
        account_no: wdAccountNo.value.trim(),
      })
      ElMessage.success('已确认打款')
    } else {
      await adminApi.rejectWalletWithdrawal(row.id, { note: wdNote.value.trim() })
      ElMessage.success('已驳回，金额已退回用户钱包')
    }
    wdDialogOpen.value = false
    loadWithdrawals()
  } catch (e) {
    ElMessage.error(e.message || '处理失败')
  } finally {
    wdSaving.value = false
  }
}

function wdStatusLabel(s) {
  return { pending: '待打款', paid: '已打款', rejected: '已驳回' }[s] || s
}
function wdTagType(s) {
  return { pending: 'warning', paid: 'success', rejected: 'danger' }[s] || 'info'
}

/* ---------- 格式化与映射 ---------- */
function money(v, withSymbol = true) {
  const n = Number(v || 0)
  const s = n.toFixed(2)
  return withSymbol ? `¥${s}` : s
}
function roleLabel(r) {
  return { owner: '主人', sitter: '机构', admin: '管理员' }[r] || r || '-'
}
function txTypeLabel(t) {
  return {
    recharge: '充值',
    consume: '消费',
    refund: '退款',
    withdraw: '提现',
    adjust_add: '调账入账',
    adjust_sub: '调账扣减',
  }[t] || t || '-'
}
function txStatusLabel(s) {
  return { success: '成功', pending: '处理中', failed: '失败' }[s] || s || '-'
}
function rechargeStatusLabel(s) {
  return { pending: '待确认', paid: '已支付', failed: '失败' }[s] || s || '-'
}
function rechargeStatusClass(s) {
  return { pending: 'pn-tag-warn', paid: 'pn-tag-ok', failed: 'pn-tag-danger' }[s] || 'pn-tag-soft'
}

onMounted(loadWallets)

// 切到对应 tab 再按需拉数据，避免一进页面就打三份请求
watch(activeTab, (name) => {
  if (name === 'recharges' && recharges.value.length === 0) loadRecharges()
  if (name === 'withdrawals' && withdrawals.value.length === 0) loadWithdrawals()
})
</script>

<style scoped>
.wallet-tabs { width: 100%; }
.mb-16 { margin-bottom: 16px; }
.mb-8 { margin-bottom: 8px; }
.drawer-actions { display: flex; gap: 12px; margin-bottom: 12px; }
.section-title { font-size: 14px; color: var(--ink); margin: 8px 0 12px; }
.tx-head { display: flex; align-items: center; justify-content: space-between; }
.tx-type { font-weight: 600; color: var(--ink); }
.tx-amount { font-weight: 600; }
.tx-amount.pos { color: #f5222d; }   /* 中国习惯：正数红 */
.tx-amount.neg { color: #52c41a; }   /* 负数绿 */
.tx-meta { font-size: 12.5px; color: var(--ink3); display: flex; flex-wrap: wrap; gap: 12px; margin-top: 4px; }
.tx-balance { font-size: 12.5px; color: var(--ink2); margin-top: 2px; }
.amt { color: #f5222d; font-weight: 600; }
.hint { font-size: 12px; color: var(--ink3); margin-top: 4px; }
.wd-role { margin-left: 6px; font-size: 11.5px; color: var(--ink3); }
.wd-done { font-size: 12px; color: var(--ink3); }
</style>
