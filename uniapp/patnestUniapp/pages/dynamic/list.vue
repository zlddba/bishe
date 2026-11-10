<template>
	<view class="dyn-list-page patnest-page">
		<view class="list">
			<view v-for="d in list" :key="d.id" class="dyn-card patnest-card" @click="open(d)">
				<view class="d-head">
					<text class="d-sitter">{{ d.sitter_name || '寄养机构' }}</text>
					<text class="d-time">{{ fromNow(d.created_at) }}</text>
				</view>
				<text class="d-content">{{ d.content }}</text>
				<view v-if="d.photos && d.photos.length" class="d-photos">
					<image v-for="(p, i) in d.photos" :key="i" class="d-photo" :src="p" mode="aspectFill" />
				</view>
				<view v-if="d.tags && d.tags.length" class="d-tags">
					<text v-for="(t, i) in d.tags" :key="i" class="d-tag">{{ t }}</text>
				</view>
				<view class="d-foot">
					<text class="d-order">{{ d.order_no || ('订单 #' + d.order_id) }}</text>
					<text class="d-more">查看与留言 ›</text>
				</view>
			</view>

			<empty
				v-if="!loading && list.length === 0"
				text="还没有寄养动态"
				image="/static/icons/camera.png"
				desc="寄养期间机构发布的图文动态会出现在这里"
			/>
			<load-more :loading="loading" :has-more="false" :empty="!loading && list.length === 0" empty-text="" />
		</view>
	</view>
</template>

<script>
	import { dynamicApi } from '../../utils/api.js'
	import { fromNow } from '../../utils/format.js'

	export default {
		data() {
			return { list: [], loading: false }
		},
		onShow() {
			this.reload()
		},
		onPullDownRefresh() {
			this.reload().then(() => uni.stopPullDownRefresh())
		},
		methods: {
			async reload() {
				this.loading = true
				try {
					// GET /pet-owner/dynamics：跨订单聚合，最多 50 条，无分页
					const res = await dynamicApi.mine()
					this.list = (res && res.items) || []
				} catch (e) {
					// 错误提示已在 request 层处理
				} finally {
					this.loading = false
				}
			},
			open(d) {
				uni.navigateTo({ url: '/pages/dynamic/detail?id=' + d.id + '&order_id=' + d.order_id })
			},
			fromNow
		}
	}
</script>

<style scoped>
	.dyn-list-page {
		padding: 24rpx;
	}
	.dyn-card {
		padding: 28rpx;
		margin-bottom: 20rpx;
	}
	.d-head {
		display: flex;
		justify-content: space-between;
		align-items: center;
	}
	.d-sitter {
		font-size: 28rpx;
		font-weight: 600;
		color: #1B1E23;
	}
	.d-time {
		font-size: 22rpx;
		color: #B7BDC5;
	}
	.d-content {
		display: block;
		font-size: 28rpx;
		color: #3A4149;
		line-height: 1.7;
		margin-top: 14rpx;
	}
	.d-photos {
		display: flex;
		flex-wrap: wrap;
		margin-top: 14rpx;
	}
	.d-photo {
		width: 200rpx;
		height: 200rpx;
		border-radius: 12rpx;
		margin: 0 12rpx 12rpx 0;
		background: #EFF1F3;
	}
	.d-tags {
		margin-top: 6rpx;
	}
	.d-tag {
		font-size: 22rpx;
		color: #3E7BFA;
		background: #EAF1FE;
		padding: 4rpx 14rpx;
		border-radius: 8rpx;
		margin-right: 10rpx;
	}
	.d-foot {
		display: flex;
		justify-content: space-between;
		align-items: center;
		margin-top: 16rpx;
		padding-top: 16rpx;
		border-top: 1rpx solid #EFF1F3;
	}
	.d-order {
		font-size: 22rpx;
		color: #9AA3AF;
	}
	.d-more {
		font-size: 24rpx;
		color: #C85B74;
	}
</style>
