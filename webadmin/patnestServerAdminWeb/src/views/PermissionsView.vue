<template>
  <div class="page-container">
    <div class="page-h">
      <div><h2>权限配置</h2><p>管理员各功能模块的访问权限</p></div>
      <div class="ml-auto filter-bar">
        <el-button @click="loadData">刷新</el-button>
      </div>
    </div>

    <div class="panel">
      <div class="panel-h">
        <h3>管理员权限点</h3>
        <p>关闭后，除系统超级管理员外的管理员将无法访问对应模块</p>
      </div>
      <div class="panel-b">
        <div class="perm-grid" v-loading="loading">
          <div v-for="p in adminPermissions" :key="p.permission" class="perm-row">
            <div class="perm-info">
              <div class="perm-name">{{ p.label }}</div>
              <div class="perm-code">{{ p.permission }}</div>
            </div>
            <el-switch
              :model-value="p.allowed"
              :loading="savingKey === p.permission"
              :disabled="corePermissions.includes(p.permission)"
              @change="toggle(p)"
            />
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { adminApi } from '@/api'
import { ElMessage } from 'element-plus'

const labels = {
  'users.read': '查看用户',
  'users.write': '管理用户',
  'sitters.manage': '机构管理',
  'orders.manage': '订单管理',
  'refunds.approve': '退款审批',
  'pets.verify': '宠物与免疫核验',
  'dynamics.moderate': '寄养动态管理',
  'reviews.moderate': '评价管理',
  'coupons.manage': '优惠券管理',
  'notifications.manage': '通知、模板与推送',
  'settlements.manage': '机构结算',
  'wallet.view': '钱包查看',
  'wallet.adjust': '钱包人工调账',
  'tickets.handle': '售后工单',
  'system.settings': '系统设置',
  'audit.read': '审计日志与导出',
}

const loading = ref(false)
const savingKey = ref('')
const items = ref([])
const corePermissions = ['users.read', 'system.settings']

const adminPermissions = computed(() =>
  items.value
    .filter(i => i.role === 'admin')
    .map(i => ({ ...i, label: labels[i.permission] || i.permission }))
)

async function loadData() {
  loading.value = true
  try {
    const res = await adminApi.getPermissions()
    items.value = res.data?.items || []
  } catch (e) {
    ElMessage.error(e.message || '加载权限失败')
  } finally {
    loading.value = false
  }
}

async function toggle(p) {
  savingKey.value = p.permission
  try {
    await adminApi.updatePermission({ role: 'admin', permission: p.permission, allowed: !p.allowed })
    p.allowed = !p.allowed
    ElMessage.success('权限已更新')
  } catch (e) {
    ElMessage.error(e.message || '更新失败')
  } finally {
    savingKey.value = ''
  }
}

onMounted(loadData)
</script>

<style scoped>
.perm-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(300px, 1fr));
  gap: 0 28px;
}
.perm-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 12px 2px;
  border-bottom: 1px solid var(--line2);
}
.perm-name { font-size: 13.5px; color: var(--ink); font-weight: 500; }
.perm-code { font-size: 12px; color: var(--ink3); margin-top: 2px; }
</style>
