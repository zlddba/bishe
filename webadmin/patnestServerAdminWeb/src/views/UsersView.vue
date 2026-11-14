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
        <el-button :type="cancelFailCount > 0 ? 'warning' : 'default'" @click="openCancelFailures">
          注销异常{{ cancelFailCount ? `（${cancelFailCount}）` : '' }}
        </el-button>
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
        <el-table-column label="操作" width="300" fixed="right">
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
                v-if="canResetTotp(row)"
                size="small"
                @click="handleResetTotp(row)"
              >重置两步验证</el-button>
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

    <!-- 注销执行异常：失败次数 / 停止自动重试 / 人工重试 -->
    <el-dialog v-model="cancelFailOpen" title="注销执行异常" width="900px">
      <el-alert
        type="info"
        :closable="false"
        show-icon
        style="margin-bottom:16px"
        title="注销失败会按 1/5/15/30/60 分钟退避重试；连续 5 次失败后自动停止，避免反复刷日志。处理完原因后点「立即重试」重新排队。"
      />
      <el-table :data="cancelFails" v-loading="cancelFailLoading" stripe>
        <el-table-column prop="user_id" label="ID" width="70" />
        <el-table-column prop="username" label="用户名" width="130" />
        <el-table-column prop="role" label="角色" width="80">
          <template #default="{ row }">{{ roleLabel(row.role) }}</template>
        </el-table-column>
        <el-table-column prop="phone" label="手机号" width="120">
          <template #default="{ row }">{{ row.phone || '-' }}</template>
        </el-table-column>
        <el-table-column label="注销类型" width="100">
          <template #default="{ row }">{{ row.cancel_mode === 'sitter' ? '仅机构' : '整个账号' }}</template>
        </el-table-column>
        <el-table-column prop="attempts" label="失败次数" width="90" />
        <el-table-column prop="last_error" label="失败原因" min-width="200" show-overflow-tooltip />
        <el-table-column label="状态" width="110">
          <template #default="{ row }">
            <span class="pn-tag" :class="row.blocked ? 'pn-tag-danger' : 'pn-tag-warn'">
              {{ row.blocked ? '已停止重试' : '等待重试' }}
            </span>
          </template>
        </el-table-column>
        <el-table-column label="下次 / 停止时间" width="150">
          <template #default="{ row }">{{ row.blocked_at || row.next_attempt_at || '-' }}</template>
        </el-table-column>
        <el-table-column label="操作" width="110" fixed="right">
          <template #default="{ row }">
            <el-button
              type="primary"
              size="small"
              :loading="retryingId === row.user_id"
              @click="retryCancel(row)"
            >立即重试</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-empty v-if="!cancelFailLoading && cancelFails.length === 0" description="没有注销异常" :image-size="60" />
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

/* ---------- 注销执行异常 ---------- */
const cancelFailOpen = ref(false)
const cancelFails = ref([])
const cancelFailLoading = ref(false)
const cancelFailCount = ref(0)
const retryingId = ref(null)

async function loadCancelFailCount() {
  try {
    const res = await adminApi.getCancelFailures({ per_page: 1 })
    cancelFailCount.value = res.data?.total || 0
  } catch {
    // 拿不到数量不影响用户列表本身
  }
}

async function openCancelFailures() {
  cancelFailOpen.value = true
  await loadCancelFails()
}

async function loadCancelFails() {
  cancelFailLoading.value = true
  try {
    const res = await adminApi.getCancelFailures({ per_page: 100 })
    cancelFails.value = res.data?.items || []
    cancelFailCount.value = res.data?.total || 0
  } catch (e) {
    ElMessage.error(e.message || '加载注销异常失败')
  } finally {
    cancelFailLoading.value = false
  }
}

async function retryCancel(row) {
  retryingId.value = row.user_id
  try {
    await adminApi.retryCancel(row.user_id)
    ElMessage.success('已重新排队，worker 下一轮会再试一次')
    await loadCancelFails()
  } catch (e) {
    ElMessage.error(e.message || '重试失败')
  } finally {
    retryingId.value = null
  }
}
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

// 只有内置超级管理员能重置别人的两步验证（换手机、验证器丢了、恢复码也用完）。
// 自己给自己重置没意义，直接用「账号安全」页的关闭流程。
function canResetTotp(row) {
  return auth.username === 'admin' && row.role === 'admin' && row.id !== currentUserId.value
}

async function handleResetTotp(row) {
  try {
    await ElMessageBox.confirm(
      `确认重置管理员「${row.username}」的两步验证吗？重置后该账号仅凭密码即可登录后台，需要对方重新绑定验证器。`,
      '重置两步验证',
      { type: 'warning', confirmButtonText: '确认重置' },
    )
  } catch {
    return
  }
  try {
    await adminApi.resetUserTotp(row.id)
    ElMessage.success('已重置该账号的两步验证')
  } catch (e) {
    ElMessage.error(e.response?.data?.message || e.message || '重置失败')
  }
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

onMounted(() => {
  loadData()
  loadCancelFailCount()
})
</script>
