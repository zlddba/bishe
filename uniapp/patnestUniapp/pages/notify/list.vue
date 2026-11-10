<template>
	<view class="notify-page patnest-page">
		<view class="top-bar">
			<text class="title">消息中心</text>
			<view class="right">
				<text class="read-all" @click="markAll">全部已读</text>
			</view>
		</view>

		<view class="list">
			<view v-for="n in list" :key="n.id" :class="['msg', n.is_read ? 'read' : 'unread']" @click="open(n)">
				<view v-if="!n.is_read" class="m-dot" />
				<view class="m-body">
					<view class="m-top">
						<text class="m-title">{{ n.title }}</text>
						<text class="m-time">{{ fromNow(n.created_at) }}</text>
					</view>
					<text class="m-text">{{ n.body }}</text>
					<text class="m-type">{{ typeText(n.type) }}</text>
				</view>
			</view>
			<empty v-if="!loading && list.length === 0" text="暂无消息" image="/static/icons/bell.png" desc="订单状态变化、寄养动态与平台公告都会出现在这里" />
			<load-more :loading="loading" :has-more="hasMore" :empty="!loading && list.length === 0" empty-text="" />
		</view>
	</view>
</template>

<script>
	import { notifyApi } from '../../utils/api.js'
	import { NOTIFY_TYPE_TEXT } from '../../config.js'
	import { fromNow, parseJson } from '../../utils/format.js'

	export default {
		data() {
			return { list: [], loading: false, page: 1, hasMore: true }
		},
		onShow() {
			this.reload()
		},
		onPullDownRefresh() {
			this.reload().then(() => uni.stopPullDownRefresh())
		},
		onReachBottom() {
			if (this.hasMore && !this.loading) {
				this.page += 1
				this.fetch()
			}
		},
		methods: {
			async reload() {
				this.page = 1
				this.hasMore = true
				this.list = []
				await this.fetch()
			},
			async fetch() {
				this.loading = true
				try {
					const res = await notifyApi.list({ page: this.page, per_page: 20 })
					const items = (res && res.items) || []
					this.list = this.page === 1 ? items : this.list.concat(items)
					this.hasMore = this.list.length < (res.total || 0)
				} catch (e) {
					// 错误提示已在 request 层处理
				} finally {
					this.loading = false
				}
			},
			async open(n) {
				if (!n.is_read) {
					try {
						await notifyApi.markRead(n.id)
					} catch (e) {}
					n.is_read = true
				}
				// 后端 extra 是 JSON 字符串（model.Notification.Extra string）
				const extra = parseJson(n.extra, {})
				const orderId = extra.order_id
				// 按通知类型跳到对应的页面，而不是一律跳订单详情：
				//   寄养已完成 → 去评价页
				//   新动态     → 去动态详情页
				//   收到评价   → 去订单详情（看评价内容）
				//   其余订单类 → 订单详情
				if (n.type === 'order_completed' && orderId) {
					// 已完成的订单引导评价；若已评价过则回订单详情
					uni.navigateTo({ url: '/pages/order/review?id=' + orderId })
					return
				}
				if (n.type === 'dynamics_published' && extra.dynamic_id) {
					const q = orderId ? ('&order_id=' + orderId) : ''
					uni.navigateTo({ url: '/pages/dynamic/detail?id=' + extra.dynamic_id + q })
					return
				}
				if (n.type === 'review_received' && orderId) {
					uni.navigateTo({ url: '/pages/order/detail?id=' + orderId })
					return
				}
				if (orderId) {
					uni.navigateTo({ url: '/pages/order/detail?id=' + orderId })
				} else if (extra.sitter_id) {
					uni.navigateTo({ url: '/pages/sitter/detail?id=' + extra.sitter_id })
				} else {
					// 没有可跳转的目标，提示一下而不是点了没反应
					uni.showToast({ title: '该消息没有可查看的详情', icon: 'none' })
				}
			},
			async markAll() {
				try {
					await notifyApi.markAllRead()
					this.list.forEach((n) => (n.is_read = true))
					uni.showToast({ title: '已标记全部已读', icon: 'none' })
				} catch (e) {}
			},
			typeText(t) {
				return NOTIFY_TYPE_TEXT[t] || '通知'
			},
			fromNow
		}
	}
</script>

<style scoped>
	.notify-page {
		padding: 24rpx;
	}
	.top-bar {
		display: flex;
		justify-content: space-between;
		align-items: center;
		margin-bottom: 20rpx;
	}
	.title {
		font-size: 34rpx;
		font-weight: 700;
		color: #1B1E23;
	}
	.read-all {
		font-size: 26rpx;
		color: #C85B74;
	}
	.msg {
		display: flex;
		background: #fff;
		border-radius: 16rpx;
		padding: 24rpx;
		margin-bottom: 16rpx;
		position: relative;
	}
	.msg.unread {
		background: #FDF1F4;
	}
	.m-dot {
		width: 14rpx;
		height: 14rpx;
		border-radius: 50%;
		background: #C2456A;
		margin: 12rpx 16rpx 0 0;
		flex-shrink: 0;
	}
	.m-body {
		flex: 1;
	}
	.m-top {
		display: flex;
		justify-content: space-between;
		align-items: center;
	}
	.m-title {
		font-size: 28rpx;
		font-weight: 600;
		color: #1B1E23;
	}
	.m-time {
		font-size: 22rpx;
		color: #B7BDC5;
	}
	.m-text {
		display: block;
		font-size: 26rpx;
		color: #5C6470;
		margin-top: 8rpx;
		line-height: 1.5;
	}
	.m-type {
		display: inline-block;
		font-size: 20rpx;
		color: #AF4A62;
		background: #FDF1F4;
		padding: 2rpx 12rpx;
		border-radius: 8rpx;
		margin-top: 12rpx;
	}
</style>
