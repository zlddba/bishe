<template>
	<view class="picker-page patnest-page">
		<view class="hint">选择寄养机构</view>
		<view class="list">
			<view v-for="s in list" :key="s.id" class="row" @click="pick(s)">
				<image class="cov" :src="s.cover_photo_url || '/static/avatar-default.jpg'" mode="aspectFill" />
				<view class="info">
					<text class="name">{{ s.name }}</text>
					<text class="sub">★ {{ (s.avg_score || 0).toFixed(1) }} · ¥{{ price(s) }}/{{ s.price_mode === 'per_visit' ? '次' : '天' }}</text>
				</view>
				<text class="go">选择</text>
			</view>
			<empty v-if="!loading && list.length === 0" text="暂无机构" image="/static/icons/shop.png" />
			<load-more :loading="loading" :has-more="hasMore" :empty="!loading && list.length === 0" empty-text="" />
		</view>
	</view>
</template>

<script>
	import { sitterApi } from '../../utils/api.js'

	export default {
		data() {
			return { list: [], loading: false, page: 1, hasMore: true }
		},
		onLoad() {
			this.fetch()
		},
		onReachBottom() {
			if (this.hasMore && !this.loading) {
				this.page += 1
				this.fetch()
			}
		},
		methods: {
			async fetch() {
				this.loading = true
				try {
					const res = await sitterApi.list({ page: this.page, per_page: 20 })
					const items = (res && res.items) || []
					this.list = this.page === 1 ? items : this.list.concat(items)
					this.hasMore = this.list.length < (res.total || 0)
				} catch (e) {
					// 错误提示已在 request 层处理
				} finally {
					this.loading = false
				}
			},
			price(s) {
				return s.price_mode === 'per_visit' ? s.visit_price || 0 : s.daily_price || 0
			},
			pick(s) {
				// 不能 redirectTo 回下单页：那会在栈里再压一个新的下单页，
				// 用户返回时回到「机构为空」的旧页面，已选宠物/日期全丢。
				// 改为把选择结果写到本地，返回上一页由 create.vue 的 onShow 消费。
				const pages = getCurrentPages()
				if (pages.length > 1) {
					uni.setStorageSync('patnest_picked_sitter', String(s.id))
					uni.navigateBack()
				} else {
					// 直接从外部打开选择页（无上一页）时才重建下单页
					uni.redirectTo({ url: '/pages/order/create?sitter_id=' + s.id })
				}
			}
		}
	}
</script>

<style scoped>
	.picker-page {
		padding: 24rpx;
	}
	.hint {
		font-size: 28rpx;
		color: #5C6470;
		margin-bottom: 20rpx;
	}
	.row {
		display: flex;
		align-items: center;
		background: #fff;
		border-radius: 16rpx;
		padding: 20rpx;
		margin-bottom: 16rpx;
	}
	.cov {
		width: 100rpx;
		height: 100rpx;
		border-radius: 12rpx;
		background: #EFF1F3;
	}
	.info {
		flex: 1;
		margin-left: 20rpx;
	}
	.name {
		font-size: 30rpx;
		font-weight: 600;
		display: block;
		color: #1B1E23;
	}
	.sub {
		font-size: 24rpx;
		color: #9AA3AF;
		margin-top: 6rpx;
		display: block;
	}
	.go {
		color: #C85B74;
		font-size: 26rpx;
		font-weight: 600;
		padding: 0 16rpx;
	}
</style>
