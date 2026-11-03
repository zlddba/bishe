<template>
  <div class="page-container">
    <div class="page-h">
      <div><h2>机构管理</h2><p>寄养机构入驻审核与运营管理</p></div>
      <div class="ml-auto filter-bar">
        <el-select v-model="query.status" placeholder="全部状态" clearable style="width:120px" @change="loadData">
          <el-option label="待审核" value="pending" />
          <el-option label="已上架" value="active" />
          <el-option label="已下架" value="inactive" />
          <el-option label="已封禁" value="banned" />
          <el-option label="已驳回" value="rejected" />
        </el-select>
        <el-button type="primary" @click="loadData">筛选</el-button>
        <el-button :loading="exporting" @click="exportCSV">导出 CSV</el-button>
        <el-button type="primary" @click="openCreate">新增机构</el-button>
      </div>
    </div>

    <div class="table-card">
      <el-table :data="sitters" v-loading="loading" stripe>
        <el-table-column prop="id" label="编号" width="60" />
        <el-table-column prop="name" label="机构名称" min-width="160" />
        <el-table-column prop="username" label="所属账号" width="120" />
        <el-table-column prop="type" label="类型" width="80">
          <template #default="{ row }">
            <span class="pn-tag pn-tag-soft">{{ typeLabel(row.type) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="日价" width="90">
          <template #default="{ row }">{{ Number(row.daily_price).toFixed(2) }}</template>
        </el-table-column>
        <el-table-column prop="capacity" label="容量" width="70" />
        <el-table-column label="评分" width="70">
          <template #default="{ row }">{{ Number(row.avg_score).toFixed(1) }}</template>
        </el-table-column>
        <el-table-column prop="review_count" label="评价数" width="70" />
        <el-table-column prop="status" label="状态" width="90">
          <template #default="{ row }">
            <span class="pn-tag" :class="statusClass(row.status)">{{ statusLabel(row.status) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="420" fixed="right">
          <template #default="{ row }">
            <el-button size="small" @click="openDetail(row)">详情</el-button>
            <el-button size="small" @click="openEdit(row)">编辑</el-button>
            <el-button v-if="row.status==='pending'" type="success" size="small" @click="updateStatus(row.id,'active')">通过</el-button>
            <el-button v-if="row.status==='pending'" type="danger" plain size="small" @click="handleReject(row)">驳回</el-button>
            <el-button v-if="row.status==='active'" type="warning" size="small" @click="updateStatus(row.id,'inactive')">下架</el-button><el-button v-if="row.status==='inactive'" type="success" size="small" @click="updateStatus(row.id,'active')">上架</el-button>
            <el-button v-if="row.status==='banned'" type="success" size="small" @click="updateStatus(row.id,'active')">解封</el-button>
            <el-button v-if="row.status!=='banned'" type="danger" size="small" @click="handleBan(row)">封禁</el-button>
            <el-button type="danger" size="small" @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <div class="pagination-wrap">
        <el-pagination v-model:current-page="query.page" v-model:page-size="query.per_page" :total="total" :page-sizes="[10,20,50]" layout="total, sizes, prev, pager, next" @change="loadData" />
      </div>
    </div>

    <el-dialog v-model="formDialog" :title="editing ? '编辑机构' : '新增机构'" width="520px">
      <el-form :model="form" label-width="90px">
        <el-form-item label="所属用户编号" v-if="!editing">
          <el-input v-model.number="form.user_id" placeholder="绑定用户编号" />
        </el-form-item>
        <el-form-item label="机构名称">
          <el-input v-model="form.name" placeholder="如：安心宠物之家" />
        </el-form-item>
        <el-form-item label="类型">
          <el-select v-model="form.type" style="width:100%">
            <el-option label="家庭寄养" value="home" />
            <el-option label="门店寄养" value="shop" />
            <el-option label="上门喂养" value="visit" />
            <el-option label="其他" value="other" />
          </el-select>
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="form.description" type="textarea" :rows="2" placeholder="选填" />
        </el-form-item>
        <el-form-item label="日单价">
          <el-input-number v-model="form.daily_price" :min="0" :precision="2" style="width:100%" />
        </el-form-item>
        <el-form-item label="上门单价">
          <el-input-number v-model="form.visit_price" :min="0" :precision="2" style="width:100%" />
        </el-form-item>
        <el-form-item label="计价方式">
          <el-select v-model="form.price_mode" style="width:100%">
            <el-option label="按天" value="per_day" />
            <el-option label="按次" value="per_visit" />
          </el-select>
        </el-form-item>
        <el-form-item label="容量">
          <el-input-number v-model="form.capacity" :min="1" style="width:100%" />
        </el-form-item>
        <el-form-item label="地址">
          <el-input v-model="form.address" placeholder="选填" />
        </el-form-item>
        <el-form-item label="经纬度">
          <div style="display:flex;gap:8px;width:100%">
            <el-input-number v-model="form.latitude" :precision="7" :controls="false" placeholder="纬度" style="flex:1" />
            <el-input-number v-model="form.longitude" :precision="7" :controls="false" placeholder="经度" style="flex:1" />
          </div>
        </el-form-item>
        <el-form-item label="封面图">
          <ImageUpload v-model="form.cover_photo_url" :multiple="false" />
        </el-form-item>
        <el-form-item label="详情图">
          <ImageUpload v-model="form.detail_photos" :limit="9" />
        </el-form-item>
        <el-form-item label="入驻状态" v-if="!editing">
          <el-select v-model="form.status" style="width:100%">
            <el-option label="待审核" value="pending" />
            <el-option label="已上架" value="active" />
            <el-option label="已下架" value="inactive" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="formDialog = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submitForm">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="banDialog" title="封禁机构" width="400px">
      <el-form>
        <el-form-item label="封禁原因">
          <el-input v-model="banReason" type="textarea" :rows="3" placeholder="请输入封禁原因" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="banDialog = false">取消</el-button>
        <el-button type="danger" @click="confirmBan">确认封禁</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="rejectDialog" title="驳回机构申请" width="400px">
      <el-form>
        <el-form-item label="驳回原因">
          <el-input v-model="rejectReason" type="textarea" :rows="3" placeholder="请说明资质材料不符合要求的原因" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="rejectDialog = false">取消</el-button>
        <el-button type="danger" @click="confirmReject">确认驳回</el-button>
      </template>
    </el-dialog>

    <el-drawer v-model="detailDrawer" title="机构详情" size="520px">
      <div v-if="detail" class="sitter-detail">
        <div class="sd-cover">
          <img v-if="detail.cover_photo_url" :src="detail.cover_photo_url" alt="" />
          <span v-else>暂无封面图</span>
        </div>
        <el-descriptions :column="2" border size="small">
          <el-descriptions-item label="机构名称" :span="2">{{ detail.name }}</el-descriptions-item>
          <el-descriptions-item label="所属账号">{{ detail.username }}</el-descriptions-item>
          <el-descriptions-item label="联系人">{{ detail.contact_person || '-' }}</el-descriptions-item>
          <el-descriptions-item label="状态">{{ statusLabel(detail.status) }}</el-descriptions-item>
          <el-descriptions-item label="类型">{{ typeLabel(detail.type) }}</el-descriptions-item>
          <el-descriptions-item label="容量">{{ detail.capacity }}</el-descriptions-item>
          <el-descriptions-item label="日单价">{{ Number(detail.daily_price).toFixed(2) }}</el-descriptions-item>
          <el-descriptions-item label="上门单价">{{ Number(detail.visit_price).toFixed(2) }}</el-descriptions-item>
          <el-descriptions-item label="评分">{{ Number(detail.avg_score).toFixed(1) }}</el-descriptions-item>
          <el-descriptions-item label="评价数">{{ detail.review_count }}</el-descriptions-item>
          <el-descriptions-item label="地址" :span="2">{{ detail.address || '-' }}</el-descriptions-item>
          <el-descriptions-item label="经纬度" :span="2">
            {{ detail.latitude ?? '-' }}, {{ detail.longitude ?? '-' }}
          </el-descriptions-item>
          <el-descriptions-item label="创建时间" :span="2">{{ detail.created_at }}</el-descriptions-item>
          <el-descriptions-item label="机构介绍" :span="2">{{ detail.description || '-' }}</el-descriptions-item>
          <el-descriptions-item label="驳回原因" :span="2" v-if="detail.review_note">{{ detail.review_note }}</el-descriptions-item>
          <el-descriptions-item label="封禁原因" :span="2" v-if="detail.banned_reason">{{ detail.banned_reason }}</el-descriptions-item>
        </el-descriptions>
        <div class="sd-sub">资质材料</div>
        <div class="sd-photos" v-if="detail.license_photo_url || detail.id_card_photo_url">
          <figure v-if="detail.license_photo_url" class="sd-cert">
            <img :src="detail.license_photo_url" alt="" />
            <figcaption>营业执照</figcaption>
          </figure>
          <figure v-if="detail.id_card_photo_url" class="sd-cert">
            <img :src="detail.id_card_photo_url" alt="" />
            <figcaption>联系人身份证</figcaption>
          </figure>
        </div>
        <div v-else class="sd-empty">未上传资质材料</div>
        <div class="sd-sub">详情图</div>
        <div class="sd-photos" v-if="(detail.detail_photos || []).length">
          <img v-for="p in detail.detail_photos" :key="p" :src="p" alt="" />
        </div>
        <div v-else class="sd-empty">暂无详情图</div>
      </div>
    </el-drawer>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { adminApi } from '@/api'
import { ElMessage, ElMessageBox } from 'element-plus'
import ImageUpload from '@/components/ImageUpload.vue'
import { downloadCSV } from '@/utils/download'

const loading = ref(false)
const saving = ref(false)
const exporting = ref(false)
const sitters = ref([])
const total = ref(0)
const query = reactive({ page: 1, per_page: 20, status: '' })

const formDialog = ref(false)
const editing = ref(false)
const form = reactive({
  id: null, user_id: null, name: '', type: 'home', description: '',
  daily_price: 50, visit_price: 0, price_mode: 'per_day', capacity: 5,
  address: '', status: 'pending', latitude: undefined, longitude: undefined,
  cover_photo_url: '', detail_photos: [],
})

const banDialog = ref(false)
const banReason = ref('')
const banTarget = ref(null)

const rejectDialog = ref(false)
const rejectReason = ref('')
const rejectTarget = ref(null)

const detailDrawer = ref(false)
const detail = ref(null)

function typeLabel(t) { return { home: '家庭', shop: '门店', visit: '上门', other: '其他' }[t] || t }
function statusClass(s) { return { active: 'pn-tag-ok', pending: 'pn-tag-warn', banned: 'pn-tag-danger', rejected: 'pn-tag-danger', inactive: 'pn-tag-soft' }[s] || 'pn-tag-soft' }
function statusLabel(s) { return { active: '已上架', pending: '待审核', banned: '已封禁', rejected: '已驳回', inactive: '已下架' }[s] || s }

async function loadData() {
  loading.value = true
  try {
    const res = await adminApi.getSitters(query)
    sitters.value = res.data?.items || []
    total.value = res.data?.total || 0
  } catch (e) { console.error(e) } finally { loading.value = false }
}

function openCreate() {
  editing.value = false
  Object.assign(form, {
    id: null, user_id: null, name: '', type: 'home', description: '',
    daily_price: 50, visit_price: 0, price_mode: 'per_day', capacity: 5,
    address: '', status: 'pending', latitude: undefined, longitude: undefined,
    cover_photo_url: '', detail_photos: [],
  })
  formDialog.value = true
}

async function openEdit(row) {
  editing.value = true
  try {
    const res = await adminApi.getSitterDetail(row.id)
    const d = res.data || {}
    Object.assign(form, {
      id: d.id, user_id: d.user_id, name: d.name, type: d.type,
      description: d.description || '', daily_price: d.daily_price, visit_price: d.visit_price,
      price_mode: d.price_mode, capacity: d.capacity, address: d.address || '',
      latitude: d.latitude ?? undefined, longitude: d.longitude ?? undefined,
      cover_photo_url: d.cover_photo_url || '', detail_photos: [...(d.detail_photos || [])],
    })
    formDialog.value = true
  } catch (e) {
    ElMessage.error(e.message || '加载机构详情失败')
  }
}

async function openDetail(row) {
  try {
    const res = await adminApi.getSitterDetail(row.id)
    detail.value = res.data || null
    detailDrawer.value = true
  } catch (e) {
    ElMessage.error(e.message || '加载机构详情失败')
  }
}

async function submitForm() {
  saving.value = true
  try {
    if (editing.value) {
      await adminApi.updateSitter(form.id, {
        name: form.name, type: form.type, description: form.description,
        daily_price: form.daily_price, visit_price: form.visit_price,
        price_mode: form.price_mode, capacity: form.capacity, address: form.address,
        latitude: form.latitude, longitude: form.longitude,
        cover_photo_url: form.cover_photo_url, detail_photos: form.detail_photos,
      })
      ElMessage.success('机构已更新')
    } else {
      await adminApi.createSitter({ ...form, status: form.status })
      ElMessage.success('机构创建成功')
    }
    formDialog.value = false
    loadData()
  } catch (e) {
    ElMessage.error(e.message || '操作失败')
  } finally {
    saving.value = false
  }
}

async function updateStatus(id, status, reason) {
  try {
    await adminApi.updateSitterStatus(id, { status, reason: reason || '' })
    ElMessage.success('状态已更新')
    loadData()
  } catch (e) { ElMessage.error(e.message || '操作失败') }
}

function handleBan(row) { banTarget.value = row; banReason.value = ''; banDialog.value = true }
async function confirmBan() {
  if (!banReason.value.trim()) { ElMessage.warning('请输入封禁原因'); return }
  await updateStatus(banTarget.value.id, 'banned', banReason.value)
  banDialog.value = false
}

function handleReject(row) { rejectTarget.value = row; rejectReason.value = ''; rejectDialog.value = true }
async function confirmReject() {
  if (!rejectReason.value.trim()) { ElMessage.warning('请输入驳回原因'); return }
  await updateStatus(rejectTarget.value.id, 'rejected', rejectReason.value)
  rejectDialog.value = false
}

async function handleDelete(row) {
  try {
    await ElMessageBox.confirm('确认删除机构「' + row.name + '」吗？该操作不可恢复。', '删除确认', { type: 'warning' })
    await adminApi.deleteSitter(row.id)
    ElMessage.success('机构已删除')
    loadData()
  } catch (e) {
    if (e !== 'cancel') ElMessage.error(e.message || '删除失败')
  }
}

async function exportCSV() {
  exporting.value = true
  try {
    const res = await adminApi.exportCSV('sitters')
    downloadCSV(res, 'patnest-sitters')
  } catch (e) {
    ElMessage.error(e.message || '导出失败')
  } finally {
    exporting.value = false
  }
}

onMounted(loadData)
</script>

<style scoped>
.sd-cover {
  width: 100%;
  height: 180px;
  border-radius: 8px;
  overflow: hidden;
  background: #f4f5f7;
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--ink3);
  font-size: 13px;
  margin-bottom: 14px;
}
.sd-cover img { width: 100%; height: 100%; object-fit: cover; }
.sd-sub { font-size: 13px; font-weight: 600; margin: 18px 0 10px; color: var(--ink); }
.sd-photos { display: flex; flex-wrap: wrap; gap: 8px; }
.sd-photos img {
  width: 104px; height: 104px; object-fit: cover;
  border-radius: 8px; border: 1px solid var(--line);
}
.sd-cert { margin: 0; text-align: center; }
.sd-cert img {
  width: 148px; height: 104px; object-fit: cover;
  border-radius: 8px; border: 1px solid var(--line); display: block;
}
.sd-cert figcaption { font-size: 12px; color: var(--ink3); margin-top: 4px; }
.sd-empty { font-size: 13px; color: var(--ink3); }
</style>
