<template>
  <div class="img-upload">
    <div class="img-grid">
      <div v-for="(url, i) in urls" :key="url + i" class="img-item">
        <img :src="url" alt="" />
        <button type="button" class="img-del" title="移除" @click="remove(i)">×</button>
      </div>
      <label v-if="urls.length < maxCount" class="img-add" title="上传图片">
        <input type="file" accept="image/*" :multiple="multiple" hidden @change="onFiles" />
        <span v-if="uploading" class="img-uploading">上传中</span>
        <el-icon v-else :size="20"><Plus /></el-icon>
      </label>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, watch } from 'vue'
import { Plus } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { adminApi } from '@/api'

const props = defineProps({
  modelValue: { type: [String, Array], default: '' },
  multiple: { type: Boolean, default: true },
  limit: { type: Number, default: 9 },
})
const emit = defineEmits(['update:modelValue'])

const urls = ref([])
const uploading = ref(false)
const maxCount = computed(() => (props.multiple ? props.limit : 1))

watch(() => props.modelValue, (val) => {
  const list = Array.isArray(val) ? val.filter(Boolean) : (val ? [val] : [])
  if (list.join('|') !== urls.value.join('|')) urls.value = list
}, { immediate: true })

function setUrls(list) {
  urls.value = list
  emit('update:modelValue', props.multiple ? list : (list[0] || ''))
}

async function onFiles(e) {
  const files = Array.from(e.target.files || [])
  e.target.value = ''
  if (!files.length) return

  uploading.value = true
  try {
    for (const file of files) {
      if (urls.value.length >= maxCount.value) {
        ElMessage.warning(`最多上传 ${maxCount.value} 张图片`)
        break
      }
      if (file.size > 5 * 1024 * 1024) {
        ElMessage.warning(`${file.name} 超过 5MB`)
        continue
      }
      const res = await adminApi.uploadImage(file)
      const url = res.data?.url
      if (url) setUrls([...urls.value, url])
    }
  } catch (err) {
    ElMessage.error(err.message || '图片上传失败')
  } finally {
    uploading.value = false
  }
}

function remove(index) {
  const list = [...urls.value]
  list.splice(index, 1)
  setUrls(list)
}
</script>

<style scoped>
.img-grid {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}
.img-item {
  position: relative;
  width: 88px;
  height: 88px;
  border-radius: 8px;
  overflow: hidden;
  border: 1px solid var(--line);
  background: var(--bg-soft, #f7f8fa);
}
.img-item img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
}
.img-del {
  position: absolute;
  top: 2px;
  right: 2px;
  width: 20px;
  height: 20px;
  border: none;
  border-radius: 50%;
  background: rgba(20, 22, 26, .62);
  color: #fff;
  font-size: 14px;
  line-height: 18px;
  cursor: pointer;
  padding: 0;
}
.img-del:hover { background: var(--danger, #d9534f); }
.img-add {
  width: 88px;
  height: 88px;
  border: 1px dashed var(--line-strong, #cfd4dc);
  border-radius: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  color: var(--ink3, #9aa3af);
  background: var(--bg-soft, #fafbfc);
  transition: border-color .2s, color .2s;
}
.img-add:hover { border-color: var(--brand, #e0708a); color: var(--brand, #e0708a); }
.img-uploading { font-size: 12px; }
</style>
