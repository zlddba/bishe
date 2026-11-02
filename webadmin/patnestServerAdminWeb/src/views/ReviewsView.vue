<template>
  <div class="page-container">
    <div class="page-h">
      <div><h2>评价管理</h2><p>用户对寄养机构的评价与回复</p></div>
      <div class="ml-auto filter-bar">
        <el-input v-model="query.q" placeholder="搜索机构/评价内容" clearable style="width:200px" @keyup.enter="loadData" @clear="loadData" />
        <el-button type="primary" @click="loadData">查询</el-button>
        <el-button type="primary" @click="openCreate">新增评价</el-button>
      </div>
    </div>

    <div class="table-card">
      <el-table :data="reviews" v-loading="loading" stripe>
        <el-table-column prop="id" label="编号" width="60" />
        <el-table-column prop="order_id" label="订单" width="80" />
        <el-table-column prop="sitter_name" label="机构" width="140" />
        <el-table-column prop="owner_name" label="评价人" width="100" />
        <el-table-column label="评分" width="80">
          <template #default="{ row }">
            <span style="font-weight:600;color:var(--brand)">{{ row.overall_score }}</span><span style="color:var(--ink3)"> / 5</span>
          </template>
        </el-table-column>
        <el-table-column label="维度" width="170">
          <template #default="{ row }">
            服务{{ row.service_score }} 环境{{ row.environment_score }} 卫生{{ row.hygiene_score }}
          </template>
        </el-table-column>
        <el-table-column prop="owner_comment" label="评价内容" min-width="220">
          <template #default="{ row }">
            {{ (row.owner_comment||'').slice(0,60) }}{{ (row.owner_comment||'').length > 60 ? '...' : '' }}
          </template>
        </el-table-column>
        <el-table-column prop="reply_text" label="机构回复" min-width="150">
          <template #default="{ row }">
            <span v-if="row.reply_text">{{ row.reply_text.slice(0,40) }}{{ row.reply_text.length > 40 ? '...' : '' }}</span>
            <span v-else style="color:var(--ink3)">未回复</span>
          </template>
        </el-table-column>
        <el-table-column label="展示" width="80">
          <template #default="{ row }">
            <el-switch :model-value="row.is_visible" size="small" @change="v => toggleVisible(row, v)" />
          </template>
        </el-table-column>
        <el-table-column prop="created_at" label="评价时间" width="150" />
        <el-table-column label="操作" width="150" fixed="right">
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

    <el-dialog v-model="formDialog" :title="editing ? '编辑评价' : '新增评价'" width="560px">
      <el-form :model="form" label-width="96px">
        <el-form-item label="关联订单">
          <el-select v-model="form.order_id" filterable placeholder="选择订单" style="width:100%" :disabled="editing">
            <el-option v-for="o in orderOptions" :key="o.id" :label="`${o.order_no}｜${o.sitter_name}｜${o.owner_name}`" :value="o.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="综合评分">
          <el-input-number v-model="form.overall_score" :min="1" :max="5" style="width:140px" />
          <span style="margin-left:10px;color:var(--ink3);font-size:12px">1-5 分</span>
        </el-form-item>
        <el-form-item label="服务评分">
          <el-input-number v-model="form.service_score" :min="1" :max="5" style="width:140px" />
        </el-form-item>
        <el-form-item label="环境评分">
          <el-input-number v-model="form.environment_score" :min="1" :max="5" style="width:140px" />
        </el-form-item>
        <el-form-item label="卫生评分">
          <el-input-number v-model="form.hygiene_score" :min="1" :max="5" style="width:140px" />
        </el-form-item>
        <el-form-item label="评价内容">
          <el-input v-model="form.owner_comment" type="textarea" :rows="3" maxlength="1000" show-word-limit placeholder="选填" />
        </el-form-item>
        <el-form-item label="机构回复">
          <el-input v-model="form.reply_text" type="textarea" :rows="2" maxlength="1000" placeholder="选填" />
        </el-form-item>
        <el-form-item label="评价图片">
          <ImageUpload v-model="form.owner_photos" :limit="6" />
        </el-form-item>
        <el-form-item label="前台展示">
          <el-switch v-model="form.is_visible" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="formDialog = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submitForm">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { adminApi } from '@/api'
import { ElMessage, ElMessageBox } from 'element-plus'
import ImageUpload from '@/components/ImageUpload.vue'

const loading = ref(false)
const saving = ref(false)
const reviews = ref([])
const total = ref(0)
const orderOptions = ref([])
const query = reactive({ page: 1, per_page: 20, q: '' })

const formDialog = ref(false)
const editing = ref(false)
const form = reactive({
  id: null, order_id: null, overall_score: 5, service_score: 5,
  environment_score: 5, hygiene_score: 5, owner_comment: '',
  reply_text: '', owner_photos: [], is_visible: true,
})

async function loadData() {
  loading.value = true
  try {
    const res = await adminApi.getReviews(query)
    reviews.value = res.data?.items || []
    total.value = res.data?.total || 0
  } catch (e) {
    ElMessage.error(e.message || '加载评价失败')
  } finally {
    loading.value = false
  }
}

async function loadOrders() {
  try {
    const res = await adminApi.getOrders({ page: 1, per_page: 100 })
    orderOptions.value = res.data?.items || []
  } catch (e) {
    console.error(e)
  }
}

function openCreate() {
  editing.value = false
  Object.assign(form, {
    id: null, order_id: null, overall_score: 5, service_score: 5,
    environment_score: 5, hygiene_score: 5, owner_comment: '',
    reply_text: '', owner_photos: [], is_visible: true,
  })
  formDialog.value = true
}

function openEdit(row) {
  editing.value = true
  Object.assign(form, {
    id: row.id,
    order_id: row.order_id,
    overall_score: row.overall_score,
    service_score: row.service_score,
    environment_score: row.environment_score,
    hygiene_score: row.hygiene_score,
    owner_comment: row.owner_comment || '',
    reply_text: row.reply_text || '',
    owner_photos: [...(row.owner_photos || [])],
    is_visible: !!row.is_visible,
  })
  formDialog.value = true
}

async function submitForm() {
  if (!editing.value && !form.order_id) { ElMessage.warning('请选择关联订单'); return }

  const payload = {
    overall_score: form.overall_score,
    service_score: form.service_score,
    environment_score: form.environment_score,
    hygiene_score: form.hygiene_score,
    owner_comment: form.owner_comment,
    owner_photos: form.owner_photos,
    reply_text: form.reply_text,
    is_visible: form.is_visible,
  }

  saving.value = true
  try {
    if (editing.value) {
      await adminApi.updateReview(form.id, payload)
      ElMessage.success('评价已更新')
    } else {
      await adminApi.createReview({ ...payload, order_id: form.order_id })
      ElMessage.success('评价已创建')
    }
    formDialog.value = false
    loadData()
  } catch (e) {
    ElMessage.error(e.message || '操作失败')
  } finally {
    saving.value = false
  }
}

async function toggleVisible(row, value) {
  try {
    await adminApi.updateReview(row.id, { is_visible: value })
    row.is_visible = value
    ElMessage.success(value ? '评价已展示' : '评价已隐藏')
  } catch (e) {
    ElMessage.error(e.message || '操作失败')
  }
}

async function handleDelete(row) {
  try {
    await ElMessageBox.confirm('确认删除该条评价吗？删除后机构评分会重新计算。', '删除确认', { type: 'warning' })
    await adminApi.deleteReview(row.id)
    ElMessage.success('评价已删除')
    loadData()
  } catch (e) {
    if (e !== 'cancel') ElMessage.error(e.message || '删除失败')
  }
}

onMounted(() => {
  loadData()
  loadOrders()
})
</script>
