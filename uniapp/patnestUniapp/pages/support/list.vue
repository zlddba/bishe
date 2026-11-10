<template>
	<view class="ticket-page patnest-page">
		<view class="top-bar">
			<text class="count">共 {{ list.length }} 个工单</text>
			<text class="create" @click="create">+ 提交工单</text>
		</view>

		<view class="list">
			<view v-for="t in list" :key="t.id" class="tk-item patnest-card" @click="open(t)">
				<view class="t-head">
					<text class="t-title">{{ t.title }}</text>
					<text class="t-status" :style="statusStyle(t.status)">{{ statusText(t.status) }}</text>
				</view>
				<view class="t-meta">
					<text class="t-no">{{ t.ticket_no }}</text>
					<text class="t-cat">{{ categoryText(t.category) }}</text>
					<text v-if="t.priority === 'high'" class="t-priority">高优先级</text>
				</view>
				<text class="t-time">{{ fmtTime(t.created_at) }}</text>
			</view>

			<empty
				v-if="!loading && list.length === 0"
				text="还没有售后工单"
				image="/static/icons/shield.png"
				desc="寄养过程中遇到问题，可随时提交工单联系平台客服"
			/>
			<load-more :loading="loading" :has-more="false" :empty="!loading && list.length === 0" empty-text="" />
		</view>

		<button class="patnest-primary-btn fab" @click="create">+ 提交工单</button>
	</view>
</template>

<script>
	import { supportApi } from '../../utils/api.js'
	import { TICKET_CATEGORY_TEXT, TICKET_STATUS_TEXT, TICKET_STATUS_THEME } from '../../config.js'
	import { fmtDateTime } from '../../utils/format.js'

	export default {
		data() {
			return { list: [], loading: false }
		},
		onShow() {
			this.reload()
		},
		methods: {
			async reload() {
				this.loading = true
				try {
					const res = await supportApi.list()
					this.list = (res && res.items) || []
				} catch (e) {
					// 错误提示已在 request 层处理
				} finally {
					this.loading = false
				}
			},
			statusText(s) {
				return TICKET_STATUS_TEXT[s] || s
			},
			statusStyle(s) {
				const c = TICKET_STATUS_THEME[s] || { fg: '#5C6470', bg: '#EFF1F3' }
				return 'color:' + c.fg + ';background:' + c.bg
			},
			categoryText(c) {
				return TICKET_CATEGORY_TEXT[c] || '其他'
			},
			create() {
				uni.navigateTo({ url: '/pages/support/create' })
			},
			open(t) {
				uni.navigateTo({ url: '/pages/support/detail?id=' + t.id })
			},
			fmtTime(s) {
				return fmtDateTime(s)
			}
		}
	}
</script>

<style scoped>
	.ticket-page {
		padding: 24rpx;
		padding-bottom: 160rpx;
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
	.create {
		font-size: 26rpx;
		color: #C85B74;
		font-weight: 600;
	}
	.tk-item {
		padding: 28rpx;
		margin-bottom: 20rpx;
	}
	.t-head {
		display: flex;
		justify-content: space-between;
		align-items: center;
	}
	.t-title {
		font-size: 30rpx;
		font-weight: 600;
		color: #1B1E23;
		flex: 1;
		margin-right: 16rpx;
	}
	.t-status {
		font-size: 22rpx;
		padding: 4rpx 16rpx;
		border-radius: 20rpx;
		flex-shrink: 0;
	}
	.t-meta {
		display: flex;
		align-items: center;
		flex-wrap: wrap;
		margin-top: 12rpx;
	}
	.t-no {
		font-size: 22rpx;
		color: #9AA3AF;
		margin-right: 20rpx;
	}
	.t-cat {
		font-size: 22rpx;
		color: #AF4A62;
		background: #FDF1F4;
		padding: 2rpx 12rpx;
		border-radius: 8rpx;
	}
	.t-priority {
		font-size: 22rpx;
		color: #C2456A;
		background: #FAE7EC;
		padding: 2rpx 12rpx;
		border-radius: 8rpx;
		margin-left: 12rpx;
	}
	.t-time {
		display: block;
		font-size: 22rpx;
		color: #B7BDC5;
		margin-top: 12rpx;
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
