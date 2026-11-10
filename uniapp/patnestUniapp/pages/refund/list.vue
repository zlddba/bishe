<template>
	<view class="refund-page patnest-page">
		<view class="list">
			<view v-for="r in list" :key="r.id" class="rf-item patnest-card" @click="openOrder(r)">
				<view class="r-head">
					<text class="r-order">{{ r.order_no || ('订单 #' + r.order_id) }}</text>
					<text class="r-status" :style="statusStyle(r.status)">{{ statusText(r.status) }}</text>
				</view>
				<view class="r-amount-row">
					<text class="r-label">退款金额</text>
					<text class="r-amount">¥{{ fmtMoney(r.amount) }}</text>
				</view>
				<text class="r-reason">申请原因：{{ r.reason }}</text>
				<text v-if="r.admin_note" class="r-note">平台回复：{{ r.admin_note }}</text>
				<text class="r-time">{{ fmtTime(r.created_at) }}</text>
			</view>

			<empty
				v-if="!loading && list.length === 0"
				text="暂无退款申请"
				image="/static/icons/wallet.png"
				desc="在订单详情页可对已支付订单发起退款申请"
			/>
			<load-more :loading="loading" :has-more="false" :empty="!loading && list.length === 0" empty-text="" />
		</view>
	</view>
</template>

<script>
	import { orderApi } from '../../utils/api.js'
	import { REFUND_STATUS_TEXT, REFUND_STATUS_THEME } from '../../config.js'
	import { fmtMoney, fmtDateTime } from '../../utils/format.js'

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
					// GET /pet-owner/refunds：最多 50 条，无分页
					const res = await orderApi.refunds()
					this.list = (res && res.items) || []
				} catch (e) {
					// 错误提示已在 request 层处理
				} finally {
					this.loading = false
				}
			},
			statusText(s) {
				return REFUND_STATUS_TEXT[s] || s
			},
			statusStyle(s) {
				const c = REFUND_STATUS_THEME[s] || { fg: '#5C6470', bg: '#EFF1F3' }
				return 'color:' + c.fg + ';background:' + c.bg
			},
			openOrder(r) {
				uni.navigateTo({ url: '/pages/order/detail?id=' + r.order_id })
			},
			fmtMoney,
			fmtTime(s) {
				return fmtDateTime(s)
			}
		}
	}
</script>

<style scoped>
	.refund-page {
		padding: 24rpx;
	}
	.rf-item {
		padding: 28rpx;
		margin-bottom: 20rpx;
	}
	.r-head {
		display: flex;
		justify-content: space-between;
		align-items: center;
	}
	.r-order {
		font-size: 27rpx;
		color: #5C6470;
	}
	.r-status {
		font-size: 22rpx;
		padding: 4rpx 16rpx;
		border-radius: 20rpx;
	}
	.r-amount-row {
		display: flex;
		align-items: baseline;
		justify-content: space-between;
		margin-top: 16rpx;
	}
	.r-label {
		font-size: 24rpx;
		color: #9AA3AF;
	}
	.r-amount {
		font-size: 36rpx;
		font-weight: 700;
		color: #C2456A;
	}
	.r-reason {
		display: block;
		font-size: 26rpx;
		color: #1B1E23;
		margin-top: 14rpx;
		line-height: 1.6;
	}
	.r-note {
		display: block;
		font-size: 24rpx;
		color: #5C6470;
		margin-top: 12rpx;
		padding: 14rpx 16rpx;
		background: #F6F7F8;
		border-radius: 12rpx;
	}
	.r-time {
		display: block;
		font-size: 22rpx;
		color: #B7BDC5;
		margin-top: 14rpx;
	}
</style>
