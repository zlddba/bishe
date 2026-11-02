<template>
  <div class="page-container">
    <div class="page-h">
      <div><h2>寄养动态</h2><p>机构发布的寄养进展与日常更新</p></div>
      <div class="ml-auto filter-bar">
        <el-input v-model="query.q" placeholder="搜索动态内容" clearable style="width:200px" @keyup.enter="loadData" @clear="loadData" />
        <el-button type="primary" @click="loadData">查询</el-button>
        <el-button type="primary" @click="openCreate">发布动态</el-button>
      </div>
    </div>

    <div class="table-card">
      <el-table :data="dynamics" v-loading="loading" stripe>
        <el-table-column prop="id" label="编号" width="60" />
        <el-table-column prop="order_id" label="订单" width="80" />
        <el-table-column prop="sitter_name" label="发布机构" width="140" />
        <el-table-column prop="author_name" label="发布人" width="100" />
        <el-table-column prop="content" label="内容" min-width="280">
          <template #default="{ row }">
            <div style="font-weight:500">{{ (row.content||'').slice(0,60) }}{{ (row.content||'').length > 60 ? '...' : '' }}</div>
          </template>
        </el-table-column>
        <el-table-column label="图片" width="70">
          <template #default="{ row }">{{ (row.photos||[]).length }} 张</template>
        </el-table-column>
        <el-table-column label="标签" width="150">
          <template #default="{ row }">
            <span v-for="t in (row.tags||[]).slice(0,3)" :key="t" class="pn-tag pn-tag-brand" style="margin-right:4px">{{ t }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="created_at" label="发布时间" width="150" />
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

    <el-dialog v-model="formDialog" :title="editing ? '编辑动态' : '发布动态'" width="560px">
      <el-form :model="form" label-width="90px">
        <el-form-item label="关联订单">
          <el-select v-model="form.order_id" filterable placeholder="选择订单" style="width:100%" :disabled="editing">
            <el-option v-for="o in orderOptions" :key="o.id" :label="`${o.order_no}｜${o.sitter_name}｜${o.owner_name}`" :value="o.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="动态内容">
          <el-input v-model="form.content" type="textarea" :rows="4" maxlength="2000" show-word-limit placeholder="今天宝贝吃了两碗饭，状态很好" />
        </el-form-item>
        <el-form-item label="标签">
          <el-input v-model="form.tag_text" placeholder="多个标签用逗号分隔，选填" />
        </el-form-item>
        <el-form-item label="动态配图">
          <ImageUpload v-model="form.photos" :limit="9" />
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
const dynamics = ref([])
const total = ref(0)
const orderOptions = ref([])
const query = reactive({ page: 1, per_page: 20, q: '' })

const formDialog = ref(false)
const editing = ref(false)
const form = reactive({ id: null, order_id: null, content: '', tag_text: '', photos: [] })

async function loadData() {
  loading.value = true
  try {
    const res = await adminApi.getDynamics(query)
    dynamics.value = res.data?.items || []
    total.value = res.data?.total || 0
  } catch (e) {
    ElMessage.error(e.message || '加载动态失败')
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
  Object.assign(form, { id: null, order_id: null, content: '', tag_text: '', photos: [] })
  formDialog.value = true
}

function openEdit(row) {
  editing.value = true
  Object.assign(form, {
    id: row.id,
    order_id: row.order_id,
    content: row.content || '',
    tag_text: (row.tags || []).join('，'),
    photos: [...(row.photos || [])],
  })
  formDialog.value = true
}

function splitTags() {
  return (form.tag_text || '')
    .split(/[,，]/)
    .map(s => s.trim())
    .filter(Boolean)
}

async function submitForm() {
  if (!editing.value && !form.order_id) { ElMessage.warning('请选择关联订单'); return }
  if (!form.content.trim()) { ElMessage.warning('请填写动态内容'); return }

  saving.value = true
  try {
    if (editing.value) {
      await adminApi.updateDynamic(form.id, {
        content: form.content.trim(),
        tags: splitTags(),
        photos: form.photos,
      })
      ElMessage.success('动态已更新')
    } else {
      await adminApi.createDynamic({
        order_id: form.order_id,
        content: form.content.trim(),
        tags: splitTags(),
        photos: form.photos,
      })
      ElMessage.success('动态已发布')
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
    await ElMessageBox.confirm('确认删除该条寄养动态吗？', '删除确认', { type: 'warning' })
    await adminApi.deleteDynamic(row.id)
    ElMessage.success('动态已删除')
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
