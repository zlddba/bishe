<template>
	<view class="fav-page patnest-page">
		<view class="top-bar">
			<text class="count">共 {{ list.length }} 家机构</text>
			<text v-if="list.length >= 2" class="compare" @click="goCompare">对比 ›</text>
		</view>

		<view class="list">
			<view v-for="(f, i) in list" :key="f.sitter_id" class="fav-item">
				<sitter-card :sitter="f" />
				<text class="remove" @click="remove(f)">取消收藏</text>
			</view>
			<empty
				v-if="!loading && list.length === 0"
				text="还没有收藏的机构"
				image="/static/icons/star.png"
				desc="在机构详情页点击「收藏」，方便下次快速下单"
			/>
			<load-more :loading="loading" :has-more="false" :empty="!loading && list.length === 0" empty-text="" />
		</view>
	</view>
</template>

<script>
	import { favoriteApi } from '../../utils/api.js'
	import { isLoggedIn } from '../../utils/auth.js'
	import { mergeWithSnapshot, saveSnapshots } from '../../utils/sitterCache.js'

	export default {
		data() {
			return { list: [], loading: false }
		},
		onShow() {
			if (!isLoggedIn()) {
				uni.showToast({ title: '请先登录', icon: 'none' })
				return uni.navigateTo({ url: '/pages/login/login' })
			}
			this.reload()
		},
		methods: {
			async reload() {
				this.loading = true
				try {
					const res = await favoriteApi.list()
					// 后端返回 { items:[{sitter_id, name, type, daily_price, avg_score, ...}] }，无 id 字段
					const items = (res && res.items) || []
					// 收藏接口字段比列表接口少（没有 description / cover_photo_url 等），
					// 用本地快照补齐，页面信息才完整
					this.list = items.map((f) => {
						const merged = mergeWithSnapshot(f, f.sitter_id)
						// 保持 sitter_id 在外层，组件取 id 时优先用它
						merged.sitter_id = f.sitter_id
						if (!merged.id) merged.id = f.sitter_id
						return merged
					})
					saveSnapshots(this.list)
				} catch (e) {
					// 错误提示已在 request 层处理
					this.list = []
				} finally {
					this.loading = false
				}
			},
			async remove(f) {
				const sitterId = f.sitter_id
				uni.showModal({
					title: '取消收藏',
					content: '确定取消收藏「' + (f.name || '该机构') + '」？',
					confirmColor: '#C2456A',
					success: async (r) => {
						if (!r.confirm) return
						try {
							await favoriteApi.remove(sitterId)
							uni.showToast({ title: '已取消', icon: 'none' })
							this.reload()
						} catch (e) {}
					}
				})
			},
			goCompare() {
				uni.navigateTo({ url: '/pages/favorite/compare' })
			}
		}
	}
</script>

<style scoped>
	.fav-page {
		padding: 24rpx;
	}
	.top-bar {
		display: flex;
		justify-content: space-between;
		align-items: center;
		margin-bottom: 20rpx;
	}
	.count {
		font-size: 24rpx;
		color: #9AA3AF;
	}
	.compare {
		font-size: 26rpx;
		color: #C85B74;
		font-weight: 600;
	}
	.fav-item {
		position: relative;
	}
	.remove {
		position: absolute;
		top: 16rpx;
		right: 16rpx;
		background: rgba(27, 30, 35, 0.5);
		color: #fff;
		font-size: 20rpx;
		padding: 6rpx 16rpx;
		border-radius: 20rpx;
		z-index: 2;
	}
</style>
