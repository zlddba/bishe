<template>
  <div class="page-container">
    <div class="page-h">
      <div><h2>用户管理</h2><p>平台全部注册用户</p></div>
      <div class="ml-auto filter-bar">
        <el-input v-model="query.q" placeholder="搜索用户名/昵称" clearable style="width:180px" @keyup.enter="loadData" @clear="loadData" />
        <el-select v-model="query.role" placeholder="全部角色" clearable style="width:110px" @change="loadData">
          <el-option label="主人" value="owner" />
          <el-option label="机构" value="sitter" />
          <el-option label="管理员" value="admin" />
        </el-select>
        <el-select v-model="query.deleted" style="width:110px" @change="loadData">
          <el-option label="未删除" value="" />
          <el-option label="已注销" value="only" />
          <el-option label="全部" value="all" />
        </el-select>
        <el-button type="primary" @click="loadData">查询</el-button>
        <el-button :loading="exporting" @click="exportCSV">导出 CSV</el-button>
        <el-button type="primary" @click="openCreate">新增用户</el-button>
      </div>
    </div>

    <div class="table-card">
      <el-table :data="users" v-loading="loading" stripe>
        <el-table-column prop="id" label="编号" width="60" />
        <el-table-column prop="username" label="用户名" width="140" />
        <el-table-column prop="nickname" label="昵称" width="120">
          <template #default="{ row }">{{ row.nickname || '-' }}</template>
        </el-table-column>
        <el-table-column prop="phone" label="手机号" width="130">
          <template #default="{ row }">{{ row.phone || '-' }}</template>
        </el-table-column>
        <el-table-column prop="role" label="角色" width="90">
          <template #default="{ row }">
            <span class="pn-tag" :class="roleClass(row.role)">{{ roleLabel(row.role) }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="80">
          <template #default="{ row }">
            <span v-if="row.deleted_at" class="pn-tag pn-tag-soft">已注销</span>
            <span v-else class="pn-tag" :class="row.status === 1 ? 'pn-tag-ok' : 'pn-tag-danger'">
              {{ row.status === 1 ? '正常' : '禁用' }}
            </span>
          </template>
        </el-table-column>
        <el-table-column prop="created_at" label="注册时间" min-width="160" />
        <el-table-column label="操作" width="220" fixed="right">
          <template #default="{ row }">
            <template v-if="row.deleted_at">
              <el-button
                v-if="canDelete(row)"
                type="danger"
                size="small"
                @click="handlePurge(row)"
              >彻底删除</el-button>
              <span v-else style="font-size:12px;color:var(--ink3)">-</span>
            </template>
            <template v-else>
              <el-button size="small" @click="openEdit(row)">编辑</el-button>
              <el-button size="small" @click="openReset(row)">重置密码</el-button>
              <el-button
                v-if="canDelete(row)"
                type="danger"
                size="small"
                @click="handleDelete(row)"
              >删除</el-button>
            </template>
          </template>
        </el-table-column>
      </el-table>
      <div class="pagination-wrap">
        <el-pagination v-model:current-page="query.page" v-model:page-size="query.per_page" :total="total" :page-sizes="[10,20,50]" layout="total, sizes, prev, pager, next" @change="loadData" />
      </div>
    </div>

    <el-dialog v-model="formDialog" :title="editing ? '编辑用户' : '新增用户'" width="440px">
      <el-form :model="form" label-width="70px">
        <el-form-item label="用户名" v-if="!editing">
          <el-input v-model="form.username" placeholder="3-50位字符" />
        </el-form-item>
        <el-form-item label="密码" v-if="!editing">
          <el-input v-model="form.password" type="password" placeholder="至少8位" show-password />
        </el-form-item>
        <el-form-item label="昵称">
          <el-input v-model="form.nickname" placeholder="选填" />
        </el-form-item>
        <el-form-item label="手机号">
          <el-input v-model="form.phone" placeholder="选填" />
        </el-form-item>
        <el-form-item label="角色">
          <el-select v-model="form.role" style="width:100%" :disabled="isSelf">
            <el-option label="主人" value="owner" />
            <el-option label="机构" value="sitter" />
            <el-option label="管理员" value="admin" />
          </el-select>
          <div v-if="isSelf" style="font-size:12px;color:var(--ink3);margin-top:4px">不能变更自己的角色</div>
        </el-form-item>
        <el-form-item label="状态" v-if="editing">
          <el-select v-model="form.status" style="width:100%" :disabled="isSelf">
            <el-option label="正常" :value="1" />
            <el-option label="禁用" :value="0" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="formDialog = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submitForm">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="resetDialog" title="重置密码" width="400px">
      <el-form label-width="70px">
        <el-form-item label="新密码">
          <el-input v-model="newPassword" type="password" placeholder="至少8位" show-password />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="resetDialog = false">取消</el-button>
        <el-button type="primary" @click="confirmReset">确认重置</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { adminApi } from '@/api'
import { useAuthStore } from '@/stores/auth'
import { ElMessage, ElMessageBox } from 'element-plus'
import { downloadCSV } from '@/utils/download'

const auth = useAuthStore()
const loading = ref(false)
const saving = ref(false)
const users = ref([])
const total = ref(0)
const query = reactive({ page: 1, per_page: 20, role: '', q: '', deleted: '' })

const formDialog = ref(false)
const editing = ref(false)
const form = reactive({ id: null, username: '', password: '', nickname: '', phone: '', role: 'owner', status: 1 })

const resetDialog = ref(false)
const resetTarget = ref(null)
const newPassword = ref('')
const exporting = ref(false)

const currentUserId = computed(() => auth.user?.id || 0)
const isSelf = computed(() => editing.value && form.id === currentUserId.value)

function roleClass(r) { return { admin: 'pn-tag-danger', sitter: 'pn-tag-warn', owner: 'pn-tag-brand' }[r] || 'pn-tag-soft' }
function roleLabel(r) { return { admin: '管理员', sitter: '机构', owner: '主人' }[r] || r }

// Can delete: not self, not system admin (username='admin')
function canDelete(row) {
  if (row.id === currentUserId.value) return false
  if (row.username === 'admin') return false
  return true
}

async function loadData() {
  loading.value = true
  try {
    const res = await adminApi.getUsers(query)
    users.value = res.data?.items || []
    total.value = res.data?.total || 0
  } catch (e) { console.error(e) } finally { loading.value = false }
}

function openCreate() {
  editing.value = false
  Object.assign(form, { id: null, username: '', password: '', nickname: '', phone: '', role: 'owner', status: 1 })
  formDialog.value = true
}

function openEdit(row) {
  editing.value = true
  Object.assign(form, { id: row.id, username: row.username, password: '', nickname: row.nickname || '', phone: row.phone || '', role: row.role, status: row.status })
  formDialog.value = true
}

async function submitForm() {
  const phone = (form.phone || '').trim()
  if (phone && !/^1[3-9]\d{9}$/.test(phone)) {
    ElMessage.warning('请输入正确的 11 位手机号')
    return
  }
  saving.value = true
  try {
    if (editing.value) {
      // 手机号必须始终提交：空字符串表示清空，后端会写成 NULL。
      const data = { phone }
      if (form.nickname) data.nickname = form.nickname
      // Only send role if not editing self
      if (form.role && !isSelf.value) data.role = form.role
      if (!isSelf.value) data.status = form.status
      await adminApi.updateUser(form.id, data)
      ElMessage.success('用户已更新')
    } else {
      await adminApi.createUser({
        username: form.username,
        password: form.password,
        nickname: form.nickname,
        phone: phone,
        role: form.role,
      })
      ElMessage.success('用户创建成功')
    }
    formDialog.value = false
    loadData()
  } catch (e) {
    ElMessage.error(e.message || '操作失败')
  } finally {
    saving.value = false
  }
}

function openReset(row) {
  resetTarget.value = row
  newPassword.value = ''
  resetDialog.value = true
}

async function confirmReset() {
  if (newPassword.value.length < 8) {
    ElMessage.warning('密码至少8位')
    return
  }
  try {
    await adminApi.resetPassword(resetTarget.value.id, { password: newPassword.value })
    ElMessage.success('密码已重置')
    resetDialog.value = false
  } catch (e) {
    ElMessage.error(e.message || '重置失败')
  }
}

async function handleDelete(row) {
  try {
    await ElMessageBox.confirm(
      '确认删除用户「' + row.username + '」吗？删除后进入"已注销"列表，可在该列表中彻底删除。',
      '删除确认',
      { type: 'warning' },
    )
    await adminApi.deleteUser(row.id)
    ElMessage.success('用户已删除')
    loadData()
  } catch (e) {
    if (e !== 'cancel') ElMessage.error(e.message || '删除失败')
  }
}

async function handlePurge(row) {
  try {
    await ElMessageBox.confirm(
      '彻底删除用户「' + row.username + '」将物理移除账号及个人数据，不可恢复。存在订单等业务数据时将无法删除。',
      '彻底删除确认',
      { type: 'warning', confirmButtonText: '彻底删除', confirmButtonClass: 'el-button--danger' },
    )
    await adminApi.purgeUser(row.id)
    ElMessage.success('已彻底删除')
    loadData()
  } catch (e) {
    if (e !== 'cancel') ElMessage.error(e.message || '彻底删除失败')
  }
}

async function exportCSV() {
  exporting.value = true
  try {
    const res = await adminApi.exportCSV('users')
    downloadCSV(res, 'patnest-users')
  } catch (e) {
    ElMessage.error(e.message || '导出失败')
  } finally {
    exporting.value = false
  }
}

onMounted(loadData)
</script>
