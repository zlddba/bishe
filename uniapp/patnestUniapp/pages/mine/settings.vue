<template>
	<view class="settings-page patnest-page">
		<view class="menu patnest-card">
			<view class="menu-item" @click="go('/pages/mine/password')">
				<image class="mi-icon" src="/static/icons/lock.png" mode="aspectFit" />
				<text class="mi-text">修改密码</text>
				<text class="mi-arrow">›</text>
			</view>
			<view class="menu-item" @click="go('/pages/mine/profile')">
				<image class="mi-icon" src="/static/icons/edit.png" mode="aspectFit" />
				<text class="mi-text">个人资料</text>
				<text class="mi-arrow">›</text>
			</view>
			<view class="menu-item" @click="go('/pages/support/list')">
				<image class="mi-icon" src="/static/icons/shield.png" mode="aspectFit" />
				<text class="mi-text">联系客服 / 售后工单</text>
				<text class="mi-arrow">›</text>
			</view>
		</view>

		<!-- 危险操作 -->
		<view class="menu patnest-card">
			<view v-if="hasCancel" class="menu-item">
				<image class="mi-icon" src="/static/icons/arrow.png" mode="aspectFit" />
				<text class="mi-text">撤销账号注销申请</text>
				<text class="mi-link" @click="withdrawCancel">撤销</text>
			</view>
			<view class="menu-item" @click="requestCancel">
				<image class="mi-icon" src="/static/icons/shield.png" mode="aspectFit" />
				<text class="mi-text danger">注销账号</text>
				<text class="mi-arrow">›</text>
			</view>
		</view>

		<!-- 关于 -->
		<view class="about patnest-card">
			<view class="a-row">
				<text class="a-label">小程序版本</text>
				<text class="a-value">{{ version }}</text>
			</view>
			<view class="a-row">
				<text class="a-label">服务端版本</text>
				<text class="a-value">{{ apiVersion || '—' }}</text>
			</view>
			<view class="a-row">
				<text class="a-label">账号角色</text>
				<text class="a-value">{{ roleText }}</text>
			</view>
		</view>

		<view class="notice">
			<text class="notice-text">
				注销账号有 72 小时冷静期，期间登录会自动撤销注销申请；到期后账号数据将被清理。
			</text>
		</view>
	</view>
</template>

<script>
	import { accountApi, systemApi } from '../../utils/api.js'
	import { getUserInfo } from '../../utils/auth.js'

	export default {
		data() {
			return { version: '1.0.0', apiVersion: '', user: {} }
		},
		computed: {
			roleText() {
				const r = this.user.role
				return { owner: '宠物主人', sitter: '寄养机构', admin: '管理员' }[r] || '—'
			},
			hasCancel() {
				return !!this.user.cancel_mode
			}
		},
		onShow() {
			this.user = getUserInfo() || {}
			this.loadVersion()
		},
		methods: {
			async loadVersion() {
				try {
					const v = await systemApi.version()
					this.apiVersion = v && v.version
				} catch (e) {}
			},
			go(url) {
				uni.navigateTo({ url })
			},
			requestCancel() {
				uni.showModal({
					title: '注销账号',
					content: '注销后账号将进入 72 小时冷静期，期间可随时撤销。确定申请注销吗？',
					confirmColor: '#C2456A',
					success: async (r) => {
						if (!r.confirm) return
						try {
							const res = await accountApi.requestCancel('account')
							const deadline = (res && res.deadline) || ''
							uni.showModal({
								title: '申请已提交',
								content: '冷静期至 ' + deadline + '，期间登录可自动撤销。',
								showCancel: false
							})
						} catch (e) {}
					}
				})
			},
			withdrawCancel() {
				uni.showModal({
					title: '撤销注销',
					content: '撤销后账号恢复正常状态，确定撤销吗？',
					success: async (r) => {
						if (!r.confirm) return
						try {
							await accountApi.withdrawCancel()
							uni.showToast({ title: '已撤销', icon: 'success' })
							this.user = getUserInfo() || {}
						} catch (e) {}
					}
				})
			}
		}
	}
</script>

<style scoped>
	.settings-page {
		padding: 24rpx;
	}
	.menu {
		padding: 8rpx 28rpx;
		margin-bottom: 24rpx;
	}
	.menu-item {
		display: flex;
		align-items: center;
		padding: 28rpx 0;
		border-bottom: 1rpx solid #EFF1F3;
	}
	.menu-item:last-child {
		border-bottom: none;
	}
	.mi-icon {
		width: 40rpx;
		height: 40rpx;
		margin-right: 16rpx;
		flex-shrink: 0;
	}
	.mi-text {
		flex: 1;
		font-size: 28rpx;
		color: #1B1E23;
	}
	.mi-text.danger {
		color: #C2456A;
	}
	.mi-arrow {
		color: #D4D8DD;
		font-size: 32rpx;
	}
	.mi-link {
		font-size: 26rpx;
		color: #C85B74;
		font-weight: 600;
	}
	.about {
		padding: 8rpx 28rpx;
		margin-bottom: 24rpx;
	}
	.a-row {
		display: flex;
		justify-content: space-between;
		align-items: center;
		padding: 24rpx 0;
		border-bottom: 1rpx solid #EFF1F3;
	}
	.a-row:last-child {
		border-bottom: none;
	}
	.a-label {
		font-size: 26rpx;
		color: #5C6470;
	}
	.a-value {
		font-size: 26rpx;
		color: #9AA3AF;
	}
	.notice {
		padding: 0 8rpx;
	}
	.notice-text {
		font-size: 22rpx;
		color: #9AA3AF;
		line-height: 1.6;
	}
</style>
