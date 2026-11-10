<template>
	<view class="pet-list-page patnest-page">
		<view class="filter-row">
			<view :class="['chip', species === '' ? 'on' : '']" @click="setSpecies('')">全部</view>
			<view :class="['chip', species === 'dog' ? 'on' : '']" @click="setSpecies('dog')">狗</view>
			<view :class="['chip', species === 'cat' ? 'on' : '']" @click="setSpecies('cat')">猫</view>
			<view :class="['chip', species === 'other' ? 'on' : '']" @click="setSpecies('other')">其他</view>
		</view>

		<view class="list">
			<pet-card v-for="p in list" :key="p.id" :pet="p" @open="openDetail" />

			<empty
				v-if="!loading && list.length === 0"
				text="还没有宠物档案"
				desc="先给爱宠建一份档案，下单时可直接选择"
			/>
			<load-more :loading="loading" :has-more="false" :empty="!loading && list.length === 0" empty-text="" />
		</view>

		<button class="patnest-primary-btn fab" @click="add">+ 添加宠物</button>
	</view>
</template>

<script>
	import { petApi } from '../../utils/api.js'

	export default {
		data() {
			return {
				list: [],
				loading: false,
				species: ''
			}
		},
		onShow() {
			this.reload()
		},
		methods: {
			async reload() {
				this.loading = true
				try {
					const params = { per_page: 50 }
					if (this.species) params.species = this.species
					const res = await petApi.list(params)
					this.list = (res && res.items) || []
				} catch (e) {
					// 错误提示已在 request 层处理
				} finally {
					this.loading = false
				}
			},
			setSpecies(s) {
				this.species = s
				this.reload()
			},
			add() {
				uni.navigateTo({ url: '/pages/pet/edit' })
			},
			// 进入宠物详情页，由详情页再进入编辑页
			openDetail(p) {
				if (!p || !p.id) return
				uni.navigateTo({ url: '/pages/pet/detail?id=' + p.id })
			}
		}
	}
</script>

<style scoped>
	.pet-list-page {
		padding: 24rpx;
		padding-bottom: 160rpx;
	}
	.filter-row {
		display: flex;
		margin-bottom: 24rpx;
	}
	.chip {
		padding: 10rpx 28rpx;
		margin-right: 16rpx;
		background: #fff;
		border-radius: 28rpx;
		font-size: 24rpx;
		color: #5C6470;
	}
	.chip.on {
		background: #FDF1F4;
		color: #AF4A62;
		font-weight: 600;
	}
	.list {
		padding-top: 4rpx;
	}
	.fab {
		position: fixed;
		left: 24rpx;
		right: 24rpx;
		bottom: calc(24rpx + env(safe-area-inset-bottom));
		height: 88rpx;
		line-height: 88rpx;
	}
</style>
