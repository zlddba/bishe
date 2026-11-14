<template>
	<view class="mine-page patnest-page">
		<!-- 未登录：引导登录，不展示任何依赖登录态的数据 -->
		<view v-if="!logged" class="profile patnest-card" @click="goLogin">
			<image class="avatar" src="/static/avatar-default.jpg" mode="aspectFill" />
			<view class="u-info">
				<text class="u-name">未登录</text>
				<text class="u-sub">登录后查看订单、收藏与优惠券</text>
			</view>
			<text class="u-login">去登录 ›</text>
		</view>

		<!-- 用户信息 -->
		<view v-else class="profile patnest-card" @click="editProfile">
			<image class="avatar" :src="user.avatar_url || '/static/avatar-default.jpg'" mode="aspectFill" />
			<view class="u-info">
				<text class="u-name">{{ user.nickname || user.username || '宠栖用户' }}</text>
				<text class="u-sub">{{ user.username ? '@' + user.username : '—' }}</text>
			</view>
			<text v-if="user.points != null" class="u-points">积分 {{ user.points }}</text>
		</view>

		<!-- 数据概览（未登录时没有数据，不展示三个 0） -->
		<view v-if="logged" class="stats patnest-card">
			<view class="st-item" @click="go('/pages/favorite/list')">
				<text class="st-num">{{ favCount }}</text>
				<text class="st-label">收藏机构</text>
			</view>
			<view class="st-item" @click="go('/pages/wallet/index')">
				<text class="st-num">{{ couponsCount }}</text>
				<text class="st-label">优惠券</text>
			</view>
			<view class="st-item" @click="go('/pages/support/list')">
				<text class="st-num">{{ ticketCount }}</text>
				<text class="st-label">售后工单</text>
			</view>
		</view>

		<!-- 功能菜单 -->
		<view class="menu patnest-card">
			<view class="menu-item" @click="go('/pages/pet/list')">
				<image class="mi-icon" src="/static/icons/pet.png" mode="aspectFit" /><text class="mi-text">我的宠物</text><text class="mi-arrow">›</text>
			</view>
			<view class="menu-item" @click="go('/pages/order/list')">
				<image class="mi-icon" src="/static/icons/order.png" mode="aspectFit" /><text class="mi-text">我的订单</text><text class="mi-arrow">›</text>
			</view>
			<view class="menu-item" @click="go('/pages/dynamic/list')">
				<image class="mi-icon" src="/static/icons/camera.png" mode="aspectFit" /><text class="mi-text">寄养动态</text><text class="mi-arrow">›</text>
			</view>
			<view class="menu-item" @click="go('/pages/favorite/list')">
				<image class="mi-icon" src="/static/icons/star.png" mode="aspectFit" /><text class="mi-text">我的收藏</text><text class="mi-arrow">›</text>
			</view>
			<view class="menu-item" @click="go('/pages/wallet/index')">
				<image class="mi-icon" src="/static/icons/coupon.png" mode="aspectFit" /><text class="mi-text">积分与优惠券</text><text class="mi-arrow">›</text>
			</view>
			<view class="menu-item" @click="go('/pages/mine/invite')">
				<image class="mi-icon" src="/static/icons/star.png" mode="aspectFit" /><text class="mi-text">邀请有礼</text><text class="mi-arrow">›</text>
			</view>
			<view class="menu-item" @click="go('/pages/refund/list')">
				<image class="mi-icon" src="/static/icons/wallet.png" mode="aspectFit" /><text class="mi-text">退款进度</text><text class="mi-arrow">›</text>
			</view>
			<view class="menu-item" @click="go('/pages/support/list')">
				<image class="mi-icon" src="/static/icons/shield.png" mode="aspectFit" /><text class="mi-text">售后工单</text><text class="mi-arrow">›</text>
			</view>
			<view class="menu-item" @click="go('/pages/notify/list')">
				<image class="mi-icon" src="/static/icons/bell.png" mode="aspectFit" /><text class="mi-text">消息中心</text>
				<text v-if="unread > 0" class="mi-badge">{{ unread }}</text>
				<text class="mi-arrow">›</text>
			</view>
		</view>

		<!-- 设置 -->
		<view class="menu patnest-card">
			<view class="menu-item" @click="editProfile">
				<image class="mi-icon" src="/static/icons/edit.png" mode="aspectFit" /><text class="mi-text">编辑资料</text><text class="mi-arrow">›</text>
			</view>
			<view class="menu-item" @click="go('/pages/mine/settings')">
				<image class="mi-icon" src="/static/icons/setting.png" mode="aspectFit" /><text class="mi-text">账号与安全</text><text class="mi-arrow">›</text>
			</view>
		</view>

		<!-- 注销申请提示 -->
		<view v-if="user.cancel_mode" class="cancel-tip patnest-card">
			<text class="ct-text">账号注销申请中，冷静期至 {{ user.cancel_deadline || '—' }}</text>
			<text class="ct-btn" @click="withdrawCancel">撤销申请</text>
		</view>

		<button class="logout" @click="logout">退出登录</button>
	</view>
</template>

<script>
	import { authApi, notifyApi, favoriteApi, walletApi, supportApi, accountApi } from '../../utils/api.js'
	import { getUserInfo, clearAuth, isLoggedIn } from '../../utils/auth.js'
	import { clearOrders } from '../../utils/orderCache.js'

	export default {
		data() {
			return {
				logged: false,
				user: {},
				unread: 0,
				favCount: 0,
				couponsCount: 0,
				ticketCount: 0
			}
		},
		onShow() {
			// 未登录时不能发请求：/auth/me 与四个统计接口都会 401，
			// 控制台会刷一屏红色错误，页面却还是空白
			this.logged = isLoggedIn()
			if (!this.logged) {
				this.user = {}
				this.unread = 0
				this.favCount = 0
				this.couponsCount = 0
				this.ticketCount = 0
				return
			}
			const cached = getUserInfo()
			if (cached) this.user = cached
			this.loadProfile()
			this.loadCounts()
		},
		methods: {
			async loadProfile() {
				try {
					const u = await authApi.getProfile()
					this.user = u || this.user
				} catch (e) {}
			},
			async loadCounts() {
				try {
					const res = await notifyApi.list({ is_read: 'false', per_page: 1 })
					this.unread = (res && res.total) || 0
				} catch (e) {}
				try {
					const res = await favoriteApi.list()
					this.favCount = ((res && res.items) || []).length
				} catch (e) {}
				try {
					const res = await walletApi.coupons()
					this.couponsCount = (((res && res.items) || []).filter((c) => c.status === 'unused')).length
				} catch (e) {}
				try {
					const res = await supportApi.list()
					this.ticketCount = ((res && res.items) || []).filter((t) => t.status !== 'closed').length
				} catch (e) {}
			},
			go(url) {
				// 未登录时除公开页（首页、登录、机构浏览）外一律先引导登录
				if (!this.logged && !/^\/(pages\/index|pages\/login|pages\/sitter)/.test(url)) {
					return this.goLogin()
				}
				// tabBar 页面需用 switchTab，其余用 navigateTo
				const tabs = ['/pages/index/index', '/pages/order/list', '/pages/notify/list', '/pages/mine/index']
				if (tabs.indexOf(url) >= 0) {
					uni.switchTab({ url })
				} else {
					uni.navigateTo({ url })
				}
			},
			editProfile() {
				uni.navigateTo({ url: '/pages/mine/profile' })
			},
			goLogin() {
				uni.navigateTo({ url: '/pages/login/login' })
			},
			withdrawCancel() {
				uni.showModal({
					title: '撤销注销',
					content: '撤销后账号将恢复正常状态，确定撤销吗？',
					success: async (r) => {
						if (!r.confirm) return
						try {
							await accountApi.withdrawCancel()
							uni.showToast({ title: '已撤销', icon: 'success' })
							this.loadProfile()
						} catch (e) {}
					}
				})
			},
			logout() {
				uni.showModal({
					title: '退出登录',
					content: '确定退出当前账号？',
					confirmColor: '#C2456A',
					success: async (r) => {
						if (!r.confirm) return
						try {
							await authApi.logout()
						} catch (e) {}
						clearAuth()
						// 清掉本地下单快照，避免下一个账号看到上一个账号的订单
						clearOrders()
						uni.reLaunch({ url: '/pages/login/login' })
					}
				})
			}
		}
	}
</script>

<style scoped>
	.mine-page {
		padding: 24rpx;
		padding-bottom: 60rpx;
	}
	.profile {
		display: flex;
		align-items: center;
		padding: 36rpx 28rpx;
		margin-bottom: 24rpx;
	}
	.avatar {
		width: 110rpx;
		height: 110rpx;
		border-radius: 24rpx;
		background: #EFF1F3;
	}
	.u-info {
		flex: 1;
		margin-left: 24rpx;
	}
	.u-name {
		font-size: 34rpx;
		font-weight: 700;
		color: #1B1E23;
		display: block;
	}
	.u-sub {
		font-size: 24rpx;
		color: #9AA3AF;
		margin-top: 6rpx;
		display: block;
	}
	.u-points {
		font-size: 24rpx;
		color: #AF4A62;
		background: #FDF1F4;
		padding: 6rpx 18rpx;
		border-radius: 20rpx;
	}
	.u-login {
		font-size: 26rpx;
		color: #AF4A62;
		font-weight: 600;
		flex-shrink: 0;
	}
	.stats {
		display: flex;
		padding: 28rpx 0;
		margin-bottom: 24rpx;
	}
	.st-item {
		flex: 1;
		display: flex;
		flex-direction: column;
		align-items: center;
		border-right: 1rpx solid #EFF1F3;
	}
	.st-item:last-child {
		border-right: none;
	}
	.st-num {
		font-size: 34rpx;
		font-weight: 700;
		color: #AF4A62;
	}
	.st-label {
		font-size: 22rpx;
		color: #9AA3AF;
		margin-top: 6rpx;
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
	.mi-arrow {
		color: #D4D8DD;
		font-size: 32rpx;
	}
	.mi-badge {
		background: #C2456A;
		color: #fff;
		font-size: 20rpx;
		padding: 2rpx 12rpx;
		border-radius: 20rpx;
		margin-right: 12rpx;
	}
	.cancel-tip {
		display: flex;
		align-items: center;
		justify-content: space-between;
		padding: 24rpx 28rpx;
		margin-bottom: 24rpx;
		background: #FDF3E3;
	}
	.ct-text {
		font-size: 24rpx;
		color: #D97706;
	}
	.ct-btn {
		font-size: 24rpx;
		color: #C85B74;
		font-weight: 600;
		margin-left: 20rpx;
		flex-shrink: 0;
	}
	.logout {
		margin-top: 16rpx;
		height: 84rpx;
		line-height: 84rpx;
		background: #fff;
		color: #C2456A;
		border-radius: 44rpx;
		font-size: 28rpx;
	}
</style>
