<template>
  <div class="page-container">
    <div class="page-h">
      <div><h2>宠物管理</h2><p>平台全部宠物档案</p></div>
      <div class="ml-auto filter-bar">
        <el-input v-model="query.q" placeholder="搜索宠物名/品种" clearable style="width:180px" @keyup.enter="loadData" @clear="loadData" />
        <el-select v-model="query.species" placeholder="全部种类" clearable style="width:110px" @change="loadData">
          <el-option label="猫" value="cat" />
          <el-option label="狗" value="dog" />
          <el-option label="其他" value="other" />
        </el-select>
        <el-select v-model="query.vaccine_status" placeholder="免疫核验" clearable style="width:120px" @change="loadData">
          <el-option label="缺证明" value="missing" />
          <el-option label="待核验" value="pending" />
          <el-option label="已核验" value="verified" />
          <el-option label="未通过" value="rejected" />
        </el-select>
        <el-button type="primary" @click="loadData">查询</el-button>
        <el-button type="primary" @click="openCreate">新增宠物</el-button>
      </div>
    </div>

    <div class="table-card">
      <el-table :data="pets" v-loading="loading" stripe>
        <el-table-column prop="id" label="编号" width="60" />
        <el-table-column prop="name" label="宠物名" width="100" />
        <el-table-column prop="species" label="种类" width="70">
          <template #default="{ row }">{{ speciesLabel(row.species) }}</template>
        </el-table-column>
        <el-table-column prop="breed" label="品种" width="120">
          <template #default="{ row }">{{ row.breed || '-' }}</template>
        </el-table-column>
        <el-table-column prop="body_size" label="体型" width="70">
          <template #default="{ row }">{{ sizeLabel(row.body_size) }}</template>
        </el-table-column>
        <el-table-column prop="age_month" label="月龄" width="60">
          <template #default="{ row }">{{ row.age_month ?? '-' }}</template>
        </el-table-column>
        <el-table-column prop="vaccinated" label="疫苗" width="60">
          <template #default="{ row }">
            <span class="pn-tag" :class="row.vaccinated ? 'pn-tag-ok' : 'pn-tag-soft'">{{ row.vaccinated ? '已打' : '未打' }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="vaccine_status" label="免疫核验" width="100">
          <template #default="{ row }">
            <span class="pn-tag" :class="vaccineClass(row.vaccine_status)">{{ vaccineLabel(row.vaccine_status) }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="owner_name" label="主人" width="100" />
        <el-table-column prop="notes" label="备注" min-width="160">
          <template #default="{ row }">{{ row.notes || '-' }}</template>
        </el-table-column>
        <el-table-column prop="created_at" label="建档时间" width="150" />
        <el-table-column label="操作" width="210" fixed="right">
          <template #default="{ row }">
            <el-button size="small" @click="openEdit(row)">编辑</el-button>
            <el-button size="small" type="primary" @click="openVerify(row)">核验</el-button>
            <el-button type="danger" size="small" @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <div class="pagination-wrap">
        <el-pagination v-model:current-page="query.page" v-model:page-size="query.per_page" :total="total" :page-sizes="[10,20,50]" layout="total, sizes, prev, pager, next" @change="loadData" />
      </div>
    </div>

    <el-dialog v-model="formDialog" :title="editing ? '编辑宠物' : '新增宠物'" width="560px">
      <el-form :model="form" label-width="90px">
        <el-form-item label="所属主人">
          <el-select v-model="form.owner_id" filterable placeholder="选择主人" style="width:100%">
            <el-option v-for="u in ownerOptions" :key="u.id" :label="u.nickname ? `${u.nickname} (${u.username})` : u.username" :value="u.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="宠物名">
          <el-input v-model="form.name" placeholder="如：豆豆" />
        </el-form-item>
        <el-form-item label="种类">
          <el-select v-model="form.species" style="width:100%">
            <el-option label="猫" value="cat" />
            <el-option label="狗" value="dog" />
            <el-option label="其他" value="other" />
          </el-select>
        </el-form-item>
        <el-form-item label="品种">
          <el-input v-model="form.breed" placeholder="选填，如：金毛" />
        </el-form-item>
        <el-form-item label="体型">
          <el-select v-model="form.body_size" style="width:100%">
            <el-option label="迷你" value="tiny" />
            <el-option label="小型" value="small" />
            <el-option label="中型" value="medium" />
            <el-option label="大型" value="large" />
            <el-option label="特大型" value="xlarge" />
          </el-select>
        </el-form-item>
        <el-form-item label="月龄">
          <el-input-number v-model="form.age_month" :min="0" :max="360" :controls="false" placeholder="选填" style="width:100%" />
        </el-form-item>
        <el-form-item label="体重(kg)">
          <el-input-number v-model="form.weight_kg" :min="0" :precision="2" :controls="false" placeholder="选填" style="width:100%" />
        </el-form-item>
        <el-form-item label="疫苗/绝育">
          <el-switch v-model="form.vaccinated" active-text="已接种" style="margin-right:18px" />
          <el-switch v-model="form.sterilized" active-text="已绝育" />
        </el-form-item>
        <el-form-item label="宠物照片">
          <ImageUpload v-model="form.photo_urls" :limit="6" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="form.notes" type="textarea" :rows="2" placeholder="选填，如过敏史" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="formDialog = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submitForm">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="verifyDialog" title="免疫证明核验" width="560px">
      <template v-if="verifyTarget">
        <el-descriptions :column="2" border size="small">
          <el-descriptions-item label="宠物">{{ verifyTarget.name }}</el-descriptions-item>
          <el-descriptions-item label="主人">{{ verifyTarget.owner_name }}</el-descriptions-item>
          <el-descriptions-item label="当前状态" :span="2">
            <span class="pn-tag" :class="vaccineClass(verifyTarget.vaccine_status)">{{ vaccineLabel(verifyTarget.vaccine_status) }}</span>
          </el-descriptions-item>
        </el-descriptions>
        <div class="proof-box">
          <div v-if="verifyTarget.vaccine_urls?.length" class="proof-list">
            <el-image
              v-for="(url, idx) in verifyTarget.vaccine_urls"
              :key="url"
              :src="url"
              :preview-src-list="verifyTarget.vaccine_urls"
              :initial-index="idx"
              fit="cover"
              class="proof-img"
              preview-teleported
            />
          </div>
          <div v-else class="proof-empty">该宠物尚未上传免疫证明</div>
        </div>
        <el-form label-width="80px" style="margin-top:14px">
          <el-form-item label="核验结果">
            <el-select v-model="verifyForm.status" style="width:100%">
              <el-option label="缺证明" value="missing" />
              <el-option label="待核验" value="pending" />
              <el-option label="已核验" value="verified" />
              <el-option label="未通过" value="rejected" />
            </el-select>
          </el-form-item>
          <el-form-item label="核验备注">
            <el-input v-model="verifyForm.note" type="textarea" :rows="2" maxlength="255" show-word-limit placeholder="未通过时建议填写原因" />
          </el-form-item>
        </el-form>
      </template>
      <template #footer>
        <el-button @click="verifyDialog = false">取消</el-button>
        <el-button type="primary" :loading="verifySaving" @click="submitVerify">保存核验</el-button>
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
const pets = ref([])
const total = ref(0)
const ownerOptions = ref([])
const query = reactive({ page: 1, per_page: 20, species: '', q: '', vaccine_status: '' })

const formDialog = ref(false)
const editing = ref(false)
const form = reactive({
  id: null, owner_id: null, name: '', species: 'cat', breed: '',
  body_size: 'medium', age_month: undefined, weight_kg: undefined,
  vaccinated: false, sterilized: false, photo_urls: [], notes: '',
})

const verifyDialog = ref(false)
const verifySaving = ref(false)
const verifyTarget = ref(null)
const verifyForm = reactive({ status: 'verified', note: '' })

function speciesLabel(s) { return { cat: '猫', dog: '狗', other: '其他' }[s] || s }
function sizeLabel(s) { return { tiny: '迷你', small: '小', medium: '中', large: '大', xlarge: '特大' }[s] || '-' }
function vaccineLabel(s) { return { missing: '缺证明', pending: '待核验', verified: '已核验', rejected: '未通过' }[s] || '缺证明' }
function vaccineClass(s) { return { missing: 'pn-tag-soft', pending: 'pn-tag-warn', verified: 'pn-tag-ok', rejected: 'pn-tag-danger' }[s] || 'pn-tag-soft' }

async function loadData() {
  loading.value = true
  try {
    const res = await adminApi.getPets(query)
    pets.value = res.data?.items || []
    total.value = res.data?.total || 0
  } catch (e) {
    ElMessage.error(e.message || '加载宠物失败')
  } finally {
    loading.value = false
  }
}

async function loadOwners() {
  try {
    const res = await adminApi.getUsers({ role: 'owner', page: 1, per_page: 100 })
    ownerOptions.value = res.data?.items || []
  } catch (e) {
    console.error(e)
  }
}

function openCreate() {
  editing.value = false
  Object.assign(form, {
    id: null, owner_id: null, name: '', species: 'cat', breed: '',
    body_size: 'medium', age_month: undefined, weight_kg: undefined,
    vaccinated: false, sterilized: false, photo_urls: [], notes: '',
  })
  formDialog.value = true
}

function openEdit(row) {
  editing.value = true
  Object.assign(form, {
    id: row.id,
    owner_id: row.owner_id,
    name: row.name,
    species: row.species,
    breed: row.breed || '',
    body_size: row.body_size || 'medium',
    age_month: row.age_month ?? undefined,
    weight_kg: row.weight_kg ?? undefined,
    vaccinated: !!row.vaccinated,
    sterilized: !!row.sterilized,
    photo_urls: [...(row.photo_urls || [])],
    notes: row.notes || '',
  })
  formDialog.value = true
}

async function submitForm() {
  if (!form.owner_id) { ElMessage.warning('请选择所属主人'); return }
  if (!form.name.trim()) { ElMessage.warning('请填写宠物名'); return }

  const payload = {
    owner_id: form.owner_id,
    name: form.name.trim(),
    species: form.species,
    breed: form.breed,
    body_size: form.body_size,
    vaccinated: form.vaccinated,
    sterilized: form.sterilized,
    photo_urls: form.photo_urls,
    notes: form.notes,
  }
  if (form.age_month !== undefined && form.age_month !== null) payload.age_month = form.age_month
  if (form.weight_kg !== undefined && form.weight_kg !== null) payload.weight_kg = form.weight_kg

  saving.value = true
  try {
    if (editing.value) {
      await adminApi.updatePet(form.id, payload)
      ElMessage.success('宠物档案已更新')
    } else {
      await adminApi.createPet(payload)
      ElMessage.success('宠物档案已创建')
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
    await ElMessageBox.confirm('确认删除宠物「' + row.name + '」的档案吗？', '删除确认', { type: 'warning' })
    await adminApi.deletePet(row.id)
    ElMessage.success('宠物已删除')
    loadData()
  } catch (e) {
    if (e !== 'cancel') ElMessage.error(e.message || '删除失败')
  }
}

function openVerify(row) {
  verifyTarget.value = row
  verifyForm.status = row.vaccine_status || 'missing'
  verifyForm.note = row.vaccine_note || ''
  verifyDialog.value = true
}

async function submitVerify() {
  verifySaving.value = true
  try {
    await adminApi.verifyPetVaccine(verifyTarget.value.id, {
      status: verifyForm.status,
      note: verifyForm.note.trim(),
    })
    ElMessage.success('核验结果已保存')
    verifyDialog.value = false
    loadData()
  } catch (e) {
    ElMessage.error(e.message || '保存失败')
  } finally {
    verifySaving.value = false
  }
}

onMounted(() => {
  loadData()
  loadOwners()
})
</script>

<style scoped>
.proof-box { margin-top: 14px; }
.proof-list { display: flex; flex-wrap: wrap; gap: 10px; }
.proof-img { width: 96px; height: 96px; border-radius: 6px; border: 1px solid var(--line); }
.proof-empty { font-size: 13px; color: var(--ink3); padding: 18px 0; text-align: center; background: var(--line2); border-radius: 6px; }
</style>
