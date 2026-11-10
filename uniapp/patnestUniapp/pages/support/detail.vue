<template>
	<view class="td-page patnest-page">
		<!-- 工单头部 -->
		<view class="head patnest-card">
			<view class="h-top">
				<text class="h-no">{{ ticket.ticket_no || ('工单 #' + ticket.id) }}</text>
				<text class="h-status" :style="statusStyle">{{ statusText }}</text>
			</view>
			<text class="h-title">{{ ticket.title }}</text>
			<text class="h-time">提交于 {{ fmtTime(ticket.created_at) }}</text>
		</view>

		<!-- 对话 -->
		<view class="msg-list">
			<view v-for="(m, i) in messages" :key="i" :class="['msg', m.sender_role === 'user' ? 'mine' : 'theirs']">
				<view class="bubble">
					<text class="b-text">{{ m.content }}</text>
					<text class="b-time">{{ fmtTime(m.created_at) }}</text>
				</view>
				<text class="b-role">{{ m.sender_role === 'user' ? '我' : '平台客服' }}</text>
			</view>
			<view v-if="messages.length === 0" class="no-msg">暂无对话记录</view>
		</view>

		<!-- 回复输入 -->
		<view class="bar" v-if="ticket.status !== 'closed'">
			<input class="bar-input" v-model="text" placeholder="补充说明…" confirm-type="send" @confirm="send" />
			<text class="bar-send" @click="send">发送</text>
		</view>
		<view class="bar closed" v-else>
			<text class="closed-text">工单已关闭，如需继续处理请提交新工单</text>
		</view>
	</view>
</template>

<script>
	import { supportApi } from '../../utils/api.js'
	import { TICKET_STATUS_TEXT, TICKET_STATUS_THEME } from '../../config.js'
	import { fmtDateTime } from '../../utils/format.js'

	export default {
		data() {
			return { id: null, ticket: {}, messages: [], text: '', sending: false }
		},
		computed: {
			statusText() {
				return TICKET_STATUS_TEXT[this.ticket.status] || this.ticket.status || ''
			},
			statusStyle() {
				const c = TICKET_STATUS_THEME[this.ticket.status] || { fg: '#5C6470', bg: '#EFF1F3' }
				return 'color:' + c.fg + ';background:' + c.bg
			}
		},
		onLoad(options) {
			this.id = options.id
			this.load()
		},
		onShow() {
			if (this.id) this.load()
		},
		methods: {
			async load() {
				try {
					const res = await supportApi.detail(this.id)
					this.ticket = res || {}
					this.messages = (res && res.messages) || []
				} catch (e) {
					// 错误提示已在 request 层处理
				}
			},
			async send() {
				const content = (this.text || '').trim()
				if (!content) return
				if (this.sending) return
				this.sending = true
				try {
					await supportApi.reply(this.id, content)
					this.text = ''
					uni.showToast({ title: '已回复', icon: 'none' })
					await this.load()
				} catch (e) {
					// 错误提示已在 request 层处理
				} finally {
					this.sending = false
				}
			},
			fmtTime(s) {
				return fmtDateTime(s)
			}
		}
	}
</script>

<style scoped>
	.td-page {
		padding: 24rpx;
		padding-bottom: 140rpx;
	}
	.head {
		padding: 28rpx;
		margin-bottom: 24rpx;
	}
	.h-top {
		display: flex;
		justify-content: space-between;
		align-items: center;
	}
	.h-no {
		font-size: 24rpx;
		color: #9AA3AF;
	}
	.h-status {
		font-size: 22rpx;
		padding: 4rpx 16rpx;
		border-radius: 20rpx;
	}
	.h-title {
		display: block;
		font-size: 32rpx;
		font-weight: 700;
		color: #1B1E23;
		margin-top: 14rpx;
	}
	.h-time {
		display: block;
		font-size: 22rpx;
		color: #B7BDC5;
		margin-top: 10rpx;
	}
	.msg-list {
		margin-bottom: 20rpx;
	}
	.msg {
		display: flex;
		flex-direction: column;
		margin-bottom: 24rpx;
	}
	.msg.mine {
		align-items: flex-end;
	}
	.msg.theirs {
		align-items: flex-start;
	}
	.bubble {
		max-width: 76%;
		padding: 20rpx 24rpx;
		border-radius: 16rpx;
	}
	.mine .bubble {
		background: #FDF1F4;
		border-top-right-radius: 4rpx;
	}
	.theirs .bubble {
		background: #fff;
		border-top-left-radius: 4rpx;
		box-shadow: 0 4rpx 16rpx rgba(27, 30, 35, 0.04);
	}
	.b-text {
		font-size: 27rpx;
		color: #1B1E23;
		line-height: 1.6;
		display: block;
	}
	.b-time {
		font-size: 20rpx;
		color: #9AA3AF;
		display: block;
		margin-top: 10rpx;
	}
	.b-role {
		font-size: 20rpx;
		color: #B7BDC5;
		margin-top: 8rpx;
	}
	.no-msg {
		font-size: 26rpx;
		color: #B7BDC5;
		text-align: center;
		padding: 60rpx 0;
	}
	.bar {
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
	.bar.closed {
		justify-content: center;
	}
	.closed-text {
		font-size: 24rpx;
		color: #9AA3AF;
	}
	.bar-input {
		flex: 1;
		background: #F6F7F8;
		border-radius: 32rpx;
		height: 68rpx;
		padding: 0 24rpx;
		font-size: 26rpx;
	}
	.bar-send {
		color: #C85B74;
		font-size: 28rpx;
		padding: 0 20rpx;
		font-weight: 600;
	}
</style>
