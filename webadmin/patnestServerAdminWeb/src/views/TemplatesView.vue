<template>
  <div class="page-container">
    <div class="page-h">
      <div><h2>消息模板</h2><p>订单、退款、结算等业务通知模板</p></div>
      <div class="ml-auto filter-bar">
        <el-button type="primary" @click="openCreate">新建模板</el-button>
      </div>
    </div>

    <div class="table-card">
      <el-table :data="items" v-loading="loading" stripe>
        <el-table-column prop="id" label="编号" width="60" />
        <el-table-column prop="code" label="模板编码" width="170" />
        <el-table-column prop="name" label="名称" width="140" />
        <el-table-column prop="title" label="标题" min-width="200" show-overflow-tooltip />
        <el-table-column prop="content" label="内容" min-width="260" show-overflow-tooltip />
        <el-table-column label="通道" width="90">
          <template #default="{ row }">
            <span class="pn-tag pn-tag-soft">{{ channelLabel(row.channel) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="80">
          <template #default="{ row }">
            <span class="pn-tag" :class="row.enabled ? 'pn-tag-ok' : 'pn-tag-soft'">{{ row.enabled ? '启用' : '停用' }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="updated_at" label="更新时间" width="150" />
        <el-table-column label="操作" width="140" fixed="right">
          <template #default="{ row }">
            <el-button size="small" @click="openEdit(row)">编辑</el-button>
            <el-button type="danger" size="small" @click="remove(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <el-dialog v-model="dialog" :title="editing ? '编辑模板' : '新建模板'" width="560px">
      <el-form :model="form" label-width="88px">
        <el-form-item label="模板编码">
          <el-input v-model="form.code" maxlength="64" placeholder="如 order_created" />
        </el-form-item>
        <el-form-item label="模板名称">
          <el-input v-model="form.name" maxlength="64" placeholder="如 新订单提醒" />
        </el-form-item>
        <el-form-item label="通知标题">
          <el-input v-model="form.title" maxlength="128" placeholder="支持 {{order_no}} 等占位符" />
        </el-form-item>
        <el-form-item label="通知内容">
          <el-input v-model="form.content" type="textarea" :rows="5" maxlength="2000" show-word-limit placeholder="支持 {{owner}}、{{start}}、{{end}} 等占位符" />
        </el-form-item>
        <el-form-item label="发送通道">
          <el-select v-model="form.channel" style="width:100%">
            <el-option label="站内信" value="in_app" />
            <el-option label="短信" value="sms" />
            <el-option label="微信" value="wechat" />
          </el-select>
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
const items = ref([])
const dialog = ref(false)
const editing = ref(false)
const form = reactive({ id: null, code: '', name: '', title: '', content: '', channel: 'in_app', enabled: true })

function channelLabel(c) { return { in_app: '站内信', sms: '短信', wechat: '微信' }[c] || c }

async function loadData() {
  loading.value = true
  try {
    const res = await adminApi.getTemplates()
    items.value = res.data?.items || []
  } catch (e) {
    ElMessage.error(e.message || '加载模板失败')
  } finally {
    loading.value = false
  }
}

function openCreate() {
  editing.value = false
  Object.assign(form, { id: null, code: '', name: '', title: '', content: '', channel: 'in_app', enabled: true })
  dialog.value = true
}

function openEdit(row) {
  editing.value = true
  Object.assign(form, {
    id: row.id, code: row.code, name: row.name, title: row.title,
    content: row.content, channel: row.channel, enabled: !!row.enabled,
  })
  dialog.value = true
}

async function submit() {
  if (!form.code.trim() || !form.name.trim() || !form.title.trim() || !form.content.trim()) {
    ElMessage.warning('请完整填写模板信息')
    return
  }
  saving.value = true
  try {
    const data = {
      code: form.code.trim(),
      name: form.name.trim(),
      title: form.title.trim(),
      content: form.content.trim(),
      channel: form.channel,
      enabled: form.enabled,
    }
    if (editing.value) {
      await adminApi.updateTemplate(form.id, data)
      ElMessage.success('模板已更新')
    } else {
      await adminApi.createTemplate(data)
      ElMessage.success('模板已创建')
    }
    dialog.value = false
    loadData()
  } catch (e) {
    ElMessage.error(e.message || '保存失败')
  } finally {
    saving.value = false
  }
}

async function remove(row) {
  try {
    await ElMessageBox.confirm(`确认删除模板「${row.name}」吗？`, '删除确认', { type: 'warning' })
    await adminApi.deleteTemplate(row.id)
    ElMessage.success('模板已删除')
    loadData()
  } catch (e) {
    if (e !== 'cancel') ElMessage.error(e.message || '删除失败')
  }
}

onMounted(loadData)
</script>
