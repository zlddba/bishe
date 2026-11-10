<template>
	<view class="pd-page patnest-page">
		<!-- 照片轮播 -->
		<swiper
			v-if="photos.length"
			class="gallery"
			indicator-dots
			indicator-color="rgba(255,255,255,0.5)"
			indicator-active-color="#fff"
		>
			<swiper-item v-for="(p, i) in photos" :key="i">
				<image class="gallery-img" :src="p" mode="aspectFill" />
			</swiper-item>
		</swiper>
		<view v-else class="gallery gallery-empty">
			<image class="gallery-img" src="/static/avatar-default.jpg" mode="aspectFit" />
		</view>

		<view class="content">
			<!-- 头部 -->
			<view class="head patnest-card">
				<view class="h-row">
					<text class="h-name">{{ pet.name || '宠物档案' }}</text>
					<text class="h-species">{{ speciesText }}</text>
				</view>
				<view class="h-tags">
					<text class="tag" :class="pet.vaccinated ? 'ok' : 'warn'">
						{{ pet.vaccinated ? '已接种疫苗' : '未接种疫苗' }}
					</text>
					<text class="tag" :class="pet.sterilized ? 'ok' : 'mute'">
						{{ pet.sterilized ? '已绝育' : '未绝育' }}
					</text>
					<text v-if="pet.vaccine_status" class="tag" :style="vaccineStyle">
						{{ vaccineText }}
					</text>
				</view>
			</view>

			<!-- 基本资料 -->
			<view class="section patnest-card">
				<view class="sec-title">基本资料</view>
				<view class="info-row">
					<text class="info-label">品种</text>
					<text class="info-value">{{ pet.breed || '未填写' }}</text>
				</view>
				<view class="info-row">
					<text class="info-label">体型</text>
					<text class="info-value">{{ sizeText }}</text>
				</view>
				<view class="info-row">
					<text class="info-label">年龄</text>
					<text class="info-value">{{ ageText }}</text>
				</view>
				<view class="info-row">
					<text class="info-label">体重</text>
					<text class="info-value">{{ pet.weight_kg != null ? pet.weight_kg + ' kg' : '未填写' }}</text>
				</view>
				<view class="info-row">
					<text class="info-label">建档时间</text>
					<text class="info-value">{{ fmtTime(pet.created_at) }}</text>
				</view>
			</view>

			<!-- 健康与行为备注 -->
			<view v-if="pet.notes" class="section patnest-card">
				<view class="sec-title">健康与行为备注</view>
				<text class="notes-text">{{ pet.notes }}</text>
			</view>

			<!-- 免疫证明 -->
			<view v-if="vaccineUrls.length" class="section patnest-card">
				<view class="sec-title">免疫证明</view>
				<view class="v-photos">
					<image v-for="(u, i) in vaccineUrls" :key="i" class="v-photo" :src="u" mode="aspectFill" />
				</view>
			</view>

			<!-- 寄养记录 -->
			<view class="section patnest-card">
				<view class="sec-title">寄养记录</view>
				<view v-if="stays.length === 0" class="no-stay">暂无寄养记录</view>
				<view v-for="s in stays" :key="s.id" class="stay-row" @click="openOrder(s)">
					<view class="s-info">
						<text class="s-sitter">{{ s.sitter_name || '寄养机构' }}</text>
						<text class="s-date">{{ s.start_date }} ~ {{ s.end_date }}</text>
					</view>
					<order-status :status="s.status" />
				</view>
			</view>
		</view>

		<!-- 底部操作栏 -->
		<view class="action-bar">
			<button class="ghost-btn" @click="remove">删除</button>
			<button class="ghost-btn" @click="edit">编辑档案</button>
			<button class="patnest-primary-btn book" @click="book">预约寄养</button>
		</view>
	</view>
</template>

<script>
	import { petApi } from '../../utils/api.js'
	import { SPECIES_TEXT, PET_SIZE_TEXT, VACCINE_STATUS_TEXT } from '../../config.js'
	import { fmtDateTime } from '../../utils/format.js'

	export default {
		data() {
			return {
				id: null,
				pet: {},
				vaccineUrls: [],
				stays: [],
				loading: false
			}
		},
		computed: {
			photos() {
				const urls = this.pet.photo_urls
				if (urls && urls.length) return urls
				return this.pet.avatar_url ? [this.pet.avatar_url] : []
			},
			speciesText() {
				return SPECIES_TEXT[this.pet.species] || '其他'
			},
			sizeText() {
				return PET_SIZE_TEXT[this.pet.body_size] || '未填写'
			},
			ageText() {
				const m = this.pet.age_month
				if (m == null || m === '') return '未填写'
				const n = Number(m)
				return n < 12 ? n + ' 个月' : Math.floor(n / 12) + ' 岁' + (n % 12 ? ' ' + (n % 12) + ' 个月' : '')
			},
			vaccineText() {
				return VACCINE_STATUS_TEXT[this.pet.vaccine_status] || ''
			},
			vaccineStyle() {
				const map = {
					missing: { fg: '#C2456A', bg: '#FAE7EC' },
					pending: { fg: '#D97706', bg: '#FDF3E3' },
					verified: { fg: '#2F9E63', bg: '#EAF6EF' },
					rejected: { fg: '#C2456A', bg: '#FAE7EC' }
				}
				const c = map[this.pet.vaccine_status] || { fg: '#5C6470', bg: '#EFF1F3' }
				return 'color:' + c.fg + ';background:' + c.bg
			}
		},
		onLoad(options) {
			this.id = options.id
			if (!this.id) {
				uni.showToast({ title: '缺少宠物 ID', icon: 'none' })
				return
			}
			this.load()
		},
		onShow() {
			if (this.id) this.load()
		},
		methods: {
			async load() {
				this.loading = true
				try {
					const p = await petApi.detail(this.id)
					this.pet = p || {}
					// 后端当前不返回免疫证明字段，取不到时不展示该区块
					this.vaccineUrls = p && p.vaccine_urls ? p.vaccine_urls : []
				} catch (e) {
					// 错误提示已在 request 层处理
				} finally {
					this.loading = false
				}
				this.loadStays()
			},
			// 该宠物相关的历史寄养记录（后端已提供按宠物聚合的接口）
			async loadStays() {
				try {
					const res = await petApi.stays(this.id)
					this.stays = (res && res.items) || []
				} catch (e) {
					this.stays = []
				}
			},
			edit() {
				uni.navigateTo({ url: '/pages/pet/edit?id=' + this.id })
			},
			book() {
				uni.navigateTo({ url: '/pages/order/create?pet_id=' + this.id })
			},
			openOrder(s) {
				uni.navigateTo({ url: '/pages/order/detail?id=' + s.id })
			},
			remove() {
				uni.showModal({
					title: '删除宠物',
					content: '确认删除「' + (this.pet.name || '该宠物') + '」？历史订单不受影响（软删除）。',
					confirmColor: '#C2456A',
					success: async (r) => {
						if (!r.confirm) return
						try {
							await petApi.remove(this.id)
							uni.showToast({ title: '已删除', icon: 'success' })
							setTimeout(() => uni.navigateBack(), 500)
						} catch (e) {}
					}
				})
			},
			fmtTime(s) {
				return fmtDateTime(s)
			}
		}
	}
</script>

<style scoped>
	.pd-page {
		padding-bottom: 140rpx;
	}
	.gallery {
		width: 100%;
		height: 420rpx;
		background: #EFF1F3;
	}
	.gallery-empty {
		display: flex;
		align-items: center;
		justify-content: center;
	}
	.gallery-img {
		width: 100%;
		height: 420rpx;
	}
	.content {
		padding: 24rpx;
	}
	.head {
		padding: 28rpx;
		margin: -40rpx 24rpx 24rpx;
		position: relative;
	}
	.h-row {
		display: flex;
		align-items: center;
	}
	.h-name {
		font-size: 36rpx;
		font-weight: 700;
		color: #1B1E23;
	}
	.h-species {
		font-size: 22rpx;
		color: #AF4A62;
		background: #FDF1F4;
		padding: 4rpx 14rpx;
		border-radius: 8rpx;
		margin-left: 16rpx;
	}
	.h-tags {
		display: flex;
		margin-top: 16rpx;
	}
	.tag {
		font-size: 22rpx;
		padding: 4rpx 16rpx;
		border-radius: 20rpx;
		margin-right: 14rpx;
	}
	.tag.ok {
		color: #2F9E63;
		background: #EAF6EF;
	}
	.tag.warn {
		color: #D97706;
		background: #FDF3E3;
	}
	.tag.mute {
		color: #5C6470;
		background: #EFF1F3;
	}
	.section {
		padding: 28rpx;
		margin-bottom: 24rpx;
	}
	.sec-title {
		font-size: 30rpx;
		font-weight: 600;
		margin-bottom: 16rpx;
		color: #1B1E23;
	}
	.info-row {
		display: flex;
		justify-content: space-between;
		align-items: center;
		padding: 14rpx 0;
		border-bottom: 1rpx solid #EFF1F3;
	}
	.info-row:last-child {
		border-bottom: none;
	}
	.info-label {
		font-size: 26rpx;
		color: #9AA3AF;
	}
	.info-value {
		font-size: 27rpx;
		color: #1B1E23;
	}
	.notes-text {
		font-size: 27rpx;
		color: #3A4149;
		line-height: 1.7;
	}
	.v-photos {
		display: flex;
		flex-wrap: wrap;
	}
	.v-photo {
		width: 200rpx;
		height: 200rpx;
		border-radius: 12rpx;
		margin: 0 12rpx 12rpx 0;
		background: #EFF1F3;
	}
	.no-stay {
		font-size: 26rpx;
		color: #B7BDC5;
		padding: 24rpx 0;
		text-align: center;
	}
	.stay-row {
		display: flex;
		align-items: center;
		justify-content: space-between;
		padding: 16rpx 0;
		border-bottom: 1rpx solid #EFF1F3;
	}
	.stay-row:last-child {
		border-bottom: none;
	}
	.s-sitter {
		font-size: 27rpx;
		color: #1B1E23;
		display: block;
	}
	.s-date {
		font-size: 22rpx;
		color: #9AA3AF;
		display: block;
		margin-top: 6rpx;
	}
	.action-bar {
		position: fixed;
		left: 0;
		right: 0;
		bottom: 0;
		display: flex;
		align-items: center;
		background: #fff;
		padding: 16rpx 24rpx;
		padding-bottom: calc(16rpx + env(safe-area-inset-bottom));
		box-shadow: 0 -4rpx 16rpx rgba(27, 30, 35, 0.06);
	}
	.ghost-btn {
		flex: 1;
		height: 84rpx;
		line-height: 84rpx;
		border-radius: 44rpx;
		font-size: 28rpx;
		margin: 0 12rpx 0 0;
		background: #fff;
		color: #5C6470;
		border: 2rpx solid #E7E9EC;
	}
	.book {
		flex: 1.4;
		height: 84rpx;
		line-height: 84rpx;
		font-size: 30rpx;
		margin: 0;
	}
</style>
