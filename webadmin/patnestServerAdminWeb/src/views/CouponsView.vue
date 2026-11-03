<template>
  <div class="page-container">
    <div class="page-h">
      <div><h2>优惠券</h2><p>平台优惠券发放与使用情况</p></div>
      <div class="ml-auto filter-bar">
        <el-select v-model="query.enabled" placeholder="全部状态" clearable style="width:120px" @change="loadData">
          <el-option label="启用中" value="1" />
          <el-option label="已停用" value="0" />
        </el-select>
        <el-button type="primary" @click="loadData">查询</el-button>
        <el-button type="primary" @click="openCreate">新建优惠券</el-button>
      </div>
    </div>

    <div class="table-card">
      <el-table :data="items" v-loading="loading" stripe>
        <el-table-column prop="code" label="券码" width="140" />
        <el-table-column prop="title" label="名称" min-width="160" />
        <el-table-column label="发放方" width="130">
          <template #default="{ row }">
            <span v-if="row.sitter_id > 0" class="pn-tag pn-tag-warn">{{ row.issuer }}</span>
            <span v-else class="pn-tag pn-tag-brand">平台官方</span>
          </template>
        </el-table-column>
        <el-table-column label="类型" width="90">
          <template #default="{ row }">
            <span class="pn-tag" :class="row.type === 'fixed' ? 'pn-tag-brand' : 'pn-tag-warn'">
              {{ row.type === 'fixed' ? '满减' : '折扣' }}
            </span>
          </template>
        </el-table-column>
        <el-table-column label="优惠" width="110">
          <template #default="{ row }">
            {{ row.type === 'fixed' ? `¥${Number(row.value).toFixed(2)}` : `减免 ${Number(row.value)}%` }}
          </template>
        </el-table-column>
        <el-table-column label="门槛" width="100">
          <template #default="{ row }">{{ Number(row.min_amount) > 0 ? `满 ¥${Number(row.min_amount).toFixed(2)}` : '无门槛' }}</template>
        </el-table-column>
        <el-table-column label="发放/使用" width="120">
          <template #default="{ row }">{{ row.claimed_qty }} / {{ row.used_qty }}</template>
        </el-table-column>
        <el-table-column label="总量" width="90">
          <template #default="{ row }">{{ row.total_qty > 0 ? row.total_qty : '不限' }}</template>
        </el-table-column>
        <el-table-column label="有效期" min-width="190">
          <template #default="{ row }">{{ row.starts_at }} ~ {{ row.ends_at || '长期' }}</template>
        </el-table-column>
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-switch :model-value="row.enabled" :loading="togglingId === row.id" @change="toggleEnabled(row)" />
          </template>
        </el-table-column>
        <el-table-column label="操作" width="120" fixed="right">
          <template #default="{ row }">
            <el-button size="small" @click="openEdit(row)">编辑</el-button>
            <el-button type="danger" size="small" @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <div class="pagination-wrap">
        <el-pagination v-model:current-page="query.page" v-model:page-size="query.per_page" :total="total" :page-sizes="[10,20,50]" layout="total, sizes, prev, pager, next" @change="loadData" />
      </div>
    </div>

    <el-dialog v-model="dialog" :title="editing ? '编辑优惠券' : '新建优惠券'" width="520px">
      <el-form :model="form" label-width="96px">
        <el-form-item label="券码">
          <el-input v-model="form.code" maxlength="32" placeholder="如 SUMMER20" />
        </el-form-item>
        <el-form-item label="名称">
          <el-input v-model="form.title" maxlength="128" placeholder="如 夏季寄养立减" />
        </el-form-item>
        <el-form-item label="类型">
          <el-radio-group v-model="form.type">
            <el-radio value="fixed">满减券</el-radio>
            <el-radio value="percent">折扣券</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item :label="form.type === 'fixed' ? '立减金额' : '减免比例'">
          <el-input-number v-model="form.value" :min="0.01" :max="form.type === 'percent' ? 100 : 99999" :precision="2" :controls="false" style="width:100%" />
          <div class="field-tip">{{ form.type === 'fixed' ? '单位：元' : '单位：%，20 表示减免 20%' }}</div>
        </el-form-item>
        <el-form-item label="使用门槛">
          <el-input-number v-model="form.min_amount" :min="0" :precision="2" :controls="false" style="width:100%" />
        </el-form-item>
        <el-form-item label="发放总量">
          <el-input-number v-model="form.total_qty" :min="0" :controls="false" style="width:100%" />
          <div class="field-tip">0 表示不限量</div>
        </el-form-item>
        <el-form-item label="每人限领">
          <el-input-number v-model="form.per_user" :min="1" :max="99" :controls="false" style="width:100%" />
        </el-form-item>
        <el-form-item label="生效日期">
          <el-date-picker v-model="form.starts_at" type="date" value-format="YYYY-MM-DD" placeholder="默认立即生效" style="width:100%" />
        </el-form-item>
        <el-form-item label="截止日期">
          <el-date-picker v-model="form.ends_at" type="date" value-format="YYYY-MM-DD" placeholder="留空表示长期有效" style="width:100%" />
        </el-form-item>
        <el-form-item label="启用">
          <el-switch v-model="form.enabled" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialog = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submit">保存</el-button>
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
const togglingId = ref(null)
const items = ref([])
const total = ref(0)
const query = reactive({ page: 1, per_page: 20, enabled: '' })

const dialog = ref(false)
const editing = ref(false)
const form = reactive({
  id: null, code: '', title: '', type: 'fixed', value: 10, min_amount: 0,
  total_qty: 0, per_user: 1, starts_at: '', ends_at: '', enabled: true,
})

function resetForm() {
  Object.assign(form, {
    id: null, code: '', title: '', type: 'fixed', value: 10, min_amount: 0,
    total_qty: 0, per_user: 1, starts_at: '', ends_at: '', enabled: true,
  })
}

function payloadFrom(row, overrides = {}) {
  return {
    code: row.code,
    title: row.title,
    type: row.type,
    value: Number(row.value),
    min_amount: Number(row.min_amount),
    total_qty: Number(row.total_qty),
    per_user: Number(row.per_user),
    starts_at: row.starts_at || '',
    ends_at: row.ends_at || '',
    enabled: !!row.enabled,
    ...overrides,
  }
}

async function loadData() {
  loading.value = true
  try {
    const res = await adminApi.getCoupons(query)
    items.value = res.data?.items || []
    total.value = res.data?.total || 0
  } catch (e) {
    ElMessage.error(e.message || '加载优惠券失败')
  } finally {
    loading.value = false
  }
}

function openCreate() {
  editing.value = false
  resetForm()
  dialog.value = true
}

function openEdit(row) {
  editing.value = true
  Object.assign(form, {
    id: row.id,
    code: row.code,
    title: row.title,
    type: row.type,
    value: Number(row.value),
    min_amount: Number(row.min_amount),
    total_qty: Number(row.total_qty),
    per_user: Number(row.per_user),
    starts_at: row.starts_at || '',
    ends_at: row.ends_at || '',
    enabled: !!row.enabled,
  })
  dialog.value = true
}

async function submit() {
  if (!form.code.trim()) { ElMessage.warning('请填写券码'); return }
  if (!form.title.trim()) { ElMessage.warning('请填写名称'); return }
  saving.value = true
  try {
    const data = {
      code: form.code.trim().toUpperCase(),
      title: form.title.trim(),
      type: form.type,
      value: Number(form.value),
      min_amount: Number(form.min_amount || 0),
      total_qty: Number(form.total_qty || 0),
      per_user: Number(form.per_user || 1),
      starts_at: form.starts_at || '',
      ends_at: form.ends_at || '',
      enabled: form.enabled,
    }
    if (editing.value) {
      await adminApi.updateCoupon(form.id, data)
      ElMessage.success('优惠券已更新')
    } else {
      await adminApi.createCoupon(data)
      ElMessage.success('优惠券已创建')
    }
    dialog.value = false
    loadData()
  } catch (e) {
    ElMessage.error(e.message || '保存失败')
  } finally {
    saving.value = false
  }
}

async function toggleEnabled(row) {
  togglingId.value = row.id
  try {
    await adminApi.updateCoupon(row.id, payloadFrom(row, { enabled: !row.enabled }))
    row.enabled = !row.enabled
    ElMessage.success(row.enabled ? '已启用' : '已停用')
  } catch (e) {
    ElMessage.error(e.message || '操作失败')
  } finally {
    togglingId.value = null
  }
}

async function handleDelete(row) {
  try {
    await ElMessageBox.confirm(
      `确认删除优惠券「${row.title}」吗？已被订单核销的券无法删除；主人已领取但未使用的券会同时从券包移除。如需暂时下线，请使用状态开关停用。`,
      '删除确认',
      { type: 'warning' },
    )
    await adminApi.deleteCoupon(row.id)
    ElMessage.success('优惠券已删除')
    loadData()
  } catch (e) {
    if (e !== 'cancel') ElMessage.error(e.message || '操作失败')
  }
}

onMounted(loadData)
</script>

<style scoped>
.field-tip { width: 100%; font-size: 12px; color: var(--ink3); margin-top: 2px; }
</style>
