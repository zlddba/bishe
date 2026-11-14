<template>
	<view class="pet-edit-page patnest-page">
		<view class="form">
			<!-- 照片 -->
			<view class="field patnest-card">
				<text class="label">宠物照片</text>
				<view class="photo-row">
					<view v-for="(u, i) in form.photo_urls" :key="i" class="photo-item">
						<image class="photo" :src="u" mode="aspectFill" />
						<text class="photo-del" @click="removePhoto(i)">✕</text>
					</view>
					<view v-if="form.photo_urls.length < 5" class="photo-add" @click="choosePhoto">
						<text class="add-icon">＋</text>
						<text class="add-text">上传</text>
					</view>
				</view>
				<text class="tip">最多 5 张，单张不超过 5MB</text>
			</view>

			<!-- 基本信息 -->
			<view class="field patnest-card">
				<text class="label">名字<text class="req">*</text></text>
				<input class="input" v-model="form.name" placeholder="宠物昵称" maxlength="64" />
			</view>

			<view class="field patnest-card">
				<text class="label">类型<text class="req">*</text></text>
				<view class="radio-row">
					<view :class="['radio', form.species === 'cat' ? 'on' : '']" @click="form.species = 'cat'">猫</view>
					<view :class="['radio', form.species === 'dog' ? 'on' : '']" @click="form.species = 'dog'">狗</view>
					<view :class="['radio', form.species === 'other' ? 'on' : '']" @click="form.species = 'other'">其他</view>
				</view>
			</view>

			<view class="field patnest-card">
				<text class="label">品种</text>
				<input class="input" v-model="form.breed" placeholder="如：金毛 / 英短" maxlength="100" />
			</view>

			<view class="field patnest-card">
				<text class="label">体型</text>
				<view class="radio-row wrap">
					<view
						v-for="sz in sizes"
						:key="sz.v"
						:class="['radio', form.body_size === sz.v ? 'on' : '']"
						@click="form.body_size = sz.v"
					>
						{{ sz.t }}
					</view>
				</view>
			</view>

			<view class="field-row patnest-card">
				<view class="half">
					<text class="label">年龄（个月）</text>
					<input class="input" v-model="form.age_month" type="number" placeholder="0-360" />
				</view>
				<view class="half">
					<text class="label">体重（kg）</text>
					<input class="input" v-model="form.weight_kg" type="digit" placeholder="如 5.5" />
				</view>
			</view>

			<!-- 健康 -->
			<view class="field patnest-card">
				<text class="label">健康状况</text>
				<view class="radio-row">
					<view :class="['radio', form.vaccinated ? 'on' : '']" @click="form.vaccinated = true">已接种疫苗</view>
					<view :class="['radio', !form.vaccinated ? 'on' : '']" @click="form.vaccinated = false">未接种</view>
				</view>
				<view class="radio-row" style="margin-top: 16rpx">
					<view :class="['radio', form.sterilized ? 'on' : '']" @click="form.sterilized = true">已绝育</view>
					<view :class="['radio', !form.sterilized ? 'on' : '']" @click="form.sterilized = false">未绝育</view>
				</view>
			</view>

			<!-- 免疫证明 -->
			<view v-if="isEdit" class="field patnest-card">
				<view class="label-row">
					<text class="label no-margin">免疫证明</text>
					<text v-if="vaccineStatus" class="v-status" :style="vaccineStyle">{{ vaccineStatusText }}</text>
				</view>
				<view class="photo-row">
					<view v-for="(u, i) in vaccineUrls" :key="i" class="photo-item">
						<image class="photo" :src="u" mode="aspectFill" />
					</view>
					<view v-if="vaccineUrls.length < 6" class="photo-add" @click="chooseVaccine">
						<text class="add-icon">＋</text>
						<text class="add-text">接种证</text>
					</view>
				</view>
				<text class="tip">上传接种证/疫苗本照片，平台核验通过后即可正常下单（最多 6 张）</text>
			</view>

			<!-- 备注 -->
			<view class="field patnest-card">
				<text class="label">健康与行为备注</text>
				<textarea
					class="textarea"
					v-model="form.notes"
					placeholder="忌口、性格、过敏史、常用用品等（≤512字）"
					maxlength="512"
				/>
			</view>
		</view>

		<button class="patnest-primary-btn save" :disabled="saving" @click="save">
			{{ saving ? '保存中...' : (isEdit ? '保存修改' : '创建档案') }}
		</button>
	</view>
</template>

<script>
	import { petApi, uploadApi } from '../../utils/api.js'
	import { SPECIES, PET_SIZE, VACCINE_STATUS_TEXT } from '../../config.js'

	export default {
		data() {
			return {
				isEdit: false,
				petId: null,
				saving: false,
				loading: false,
				vaccineUrls: [],
				vaccineStatus: '',
				vaccineUploading: false,
				sizes: [
					{ v: PET_SIZE.TINY, t: '特小' },
					{ v: PET_SIZE.SMALL, t: '小型' },
					{ v: PET_SIZE.MEDIUM, t: '中型' },
					{ v: PET_SIZE.LARGE, t: '大型' },
					{ v: PET_SIZE.XLARGE, t: '特大型' }
				],
				form: {
					name: '',
					species: SPECIES.DOG,
					breed: '',
					body_size: '',
					age_month: '',
					weight_kg: '',
					vaccinated: false,
					sterilized: false,
					notes: '',
					photo_urls: []
				}
			}
		},
		computed: {
			vaccineStatusText() {
				return VACCINE_STATUS_TEXT[this.vaccineStatus] || ''
			},
			vaccineStyle() {
				const map = {
					missing: { fg: '#C2456A', bg: '#FAE7EC' },
					pending: { fg: '#D97706', bg: '#FDF3E3' },
					verified: { fg: '#2F9E63', bg: '#EAF6EF' },
					rejected: { fg: '#C2456A', bg: '#FAE7EC' }
				}
				const c = map[this.vaccineStatus] || { fg: '#5C6470', bg: '#EFF1F3' }
				return 'color:' + c.fg + ';background:' + c.bg
			}
		},
		onLoad(options) {
			if (options.id) {
				const id = parseInt(options.id, 10)
				if (isNaN(id) || id <= 0) {
					uni.showToast({ title: '宠物 ID 无效', icon: 'none' })
					setTimeout(() => uni.navigateBack(), 1200)
					return
				}
				this.isEdit = true
				this.petId = id
				this.load()
			}
		},
		methods: {
			async load() {
				this.loading = true
				try {
					const p = await petApi.detail(this.petId)
					if (!p || !p.id) {
						uni.showToast({ title: '宠物档案不存在或已删除', icon: 'none' })
						return
					}
					this.form = {
						name: p.name || '',
						species: p.species || SPECIES.DOG,
						breed: p.breed || '',
						body_size: p.body_size || '',
						age_month: p.age_month != null ? String(p.age_month) : '',
						weight_kg: p.weight_kg != null ? String(p.weight_kg) : '',
						vaccinated: !!p.vaccinated,
						sterilized: !!p.sterilized,
						notes: p.notes || '',
						photo_urls: p.photo_urls || []
					}
					// 免疫证明相关字段后端当前不返回，取不到时留空由用户上传
					this.vaccineUrls = p.vaccine_urls || []
					this.vaccineStatus = p.vaccine_status || ''
				} catch (e) {
					// 错误提示已在 request 层处理
				} finally {
					this.loading = false
				}
			},
			choosePhoto() {
				uni.chooseImage({
					count: 1,
					sizeType: ['compressed'],
					success: async (res) => {
						const path = res.tempFilePaths[0]
						try {
							const data = await uploadApi.image(path)
							this.form.photo_urls = this.form.photo_urls.concat([data.url])
						} catch (e) {}
					}
				})
			},
			removePhoto(i) {
				this.form.photo_urls.splice(i, 1)
			},
			chooseVaccine() {
				if (this.vaccineUploading) return
				uni.chooseImage({
					count: 1,
					sizeType: ['compressed'],
					success: async (res) => {
						const path = res.tempFilePaths[0]
						this.vaccineUploading = true
						let uploadedUrl = ''
						try {
							const data = await uploadApi.image(path)
							uploadedUrl = data.url
							this.vaccineUrls = this.vaccineUrls.concat([uploadedUrl])
							await this.submitVaccine()
						} catch (e) {
						} finally {
							this.vaccineUploading = false
						}
					}
				})
			},
			async submitVaccine() {
				// 传快照：并发场景下 this.vaccineUrls 可能已被改过
				const snapshot = this.vaccineUrls.slice()
				try {
					const res = await petApi.uploadVaccine(this.petId, snapshot)
					this.vaccineStatus = (res && res.vaccine_status) || 'pending'
					uni.showToast({ title: '已提交，等待平台核验', icon: 'none' })
				} catch (e) {
					// 失败时只回滚本次新增的那张，不能盲删最后一张
					// （并发上传时最后一张可能是另一次请求刚加进来且已成功的）
					const added = snapshot[snapshot.length - 1]
					if (added) {
						this.vaccineUrls = this.vaccineUrls.filter((u) => u !== added)
					}
				}
			},
			async save() {
				if (!this.form.name || !this.form.name.trim()) {
					uni.showToast({ title: '请填写宠物名字', icon: 'none' })
					return
				}
				const age = this.form.age_month === '' ? undefined : Number(this.form.age_month)
				if (age !== undefined && (isNaN(age) || age < 0 || age > 360)) {
					uni.showToast({ title: '年龄需在 0-360 个月之间', icon: 'none' })
					return
				}
				const weight = this.form.weight_kg === '' ? undefined : Number(this.form.weight_kg)
				if (weight !== undefined && (isNaN(weight) || weight <= 0)) {
					uni.showToast({ title: '体重需为正数', icon: 'none' })
					return
				}
				const payload = {
					name: this.form.name.trim(),
					species: this.form.species,
					breed: this.form.breed || undefined,
					body_size: this.form.body_size || undefined,
					age_month: age,
					weight_kg: weight,
					vaccinated: this.form.vaccinated,
					sterilized: this.form.sterilized,
					notes: this.form.notes || undefined,
					photo_urls: this.form.photo_urls
				}
				this.saving = true
				try {
					if (this.isEdit) {
						await petApi.update(this.petId, payload)
						uni.showToast({ title: '已保存', icon: 'success' })
					} else {
						await petApi.create(payload)
						uni.showToast({ title: '创建成功', icon: 'success' })
					}
					setTimeout(() => uni.navigateBack(), 500)
				} catch (e) {
					// 错误提示已在 request 层处理
				} finally {
					this.saving = false
				}
			}
		}
	}
</script>

<style scoped>
	.pet-edit-page {
		padding: 24rpx;
		padding-bottom: 160rpx;
	}
	.field {
		padding: 24rpx 28rpx;
		margin-bottom: 20rpx;
	}
	.field-row {
		display: flex;
		padding: 24rpx 28rpx;
		margin-bottom: 20rpx;
	}
	.half {
		flex: 1;
	}
	.half + .half {
		margin-left: 24rpx;
	}
	.label {
		font-size: 26rpx;
		color: #5C6470;
		display: block;
		margin-bottom: 16rpx;
	}
	.label.no-margin {
		margin-bottom: 0;
	}
	.label-row {
		display: flex;
		align-items: center;
		justify-content: space-between;
		margin-bottom: 16rpx;
	}
	.v-status {
		font-size: 22rpx;
		padding: 4rpx 14rpx;
		border-radius: 8rpx;
	}
	.req {
		color: #C2456A;
		margin-left: 4rpx;
	}
	.input {
		font-size: 28rpx;
		color: #1B1E23;
	}
	.textarea {
		width: 100%;
		height: 140rpx;
		font-size: 28rpx;
		color: #1B1E23;
	}
	.radio-row {
		display: flex;
	}
	.radio-row.wrap {
		flex-wrap: wrap;
	}
	.radio {
		padding: 12rpx 28rpx;
		margin-right: 16rpx;
		margin-bottom: 8rpx;
		background: #F6F7F8;
		border-radius: 12rpx;
		font-size: 26rpx;
		color: #5C6470;
	}
	.radio.on {
		background: #FDF1F4;
		color: #AF4A62;
		font-weight: 600;
	}
	.photo-row {
		display: flex;
		flex-wrap: wrap;
	}
	.photo-item {
		position: relative;
		margin: 0 16rpx 16rpx 0;
	}
	.photo {
		width: 140rpx;
		height: 140rpx;
		border-radius: 16rpx;
		background: #EFF1F3;
	}
	.photo-del {
		position: absolute;
		top: -8rpx;
		right: -8rpx;
		width: 36rpx;
		height: 36rpx;
		line-height: 34rpx;
		text-align: center;
		border-radius: 50%;
		background: #1B1E23;
		color: #fff;
		font-size: 20rpx;
	}
	.photo-add {
		width: 140rpx;
		height: 140rpx;
		border-radius: 16rpx;
		border: 2rpx dashed #D4D8DD;
		display: flex;
		flex-direction: column;
		align-items: center;
		justify-content: center;
		margin-bottom: 16rpx;
	}
	.add-icon {
		font-size: 40rpx;
		color: #9AA3AF;
		line-height: 1;
	}
	.add-text {
		font-size: 20rpx;
		color: #9AA3AF;
		margin-top: 8rpx;
	}
	.tip {
		display: block;
		font-size: 22rpx;
		color: #9AA3AF;
		margin-top: 8rpx;
		line-height: 1.5;
	}
	.save {
		position: fixed;
		left: 24rpx;
		right: 24rpx;
		bottom: calc(24rpx + env(safe-area-inset-bottom));
		height: 88rpx;
		line-height: 88rpx;
	}
</style>
