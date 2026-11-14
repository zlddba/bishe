<template>
	<view class="invite-page patnest-page">
		<view v-if="!logged" class="empty patnest-card">
			<text class="e-title">登录后查看我的邀请码</text>
			<button class="patnest-primary-btn" @click="goLogin">去登录</button>
		</view>

		<block v-else>
			<view class="hero patnest-card">
				<text class="h-title">邀请好友，各得 {{ points }} 积分</text>
				<text class="h-sub">好友注册时填写你的邀请码，首单完成后双方各得 {{ points }} 积分</text>
				<view class="code-box">
					<text class="code">{{ code || '加载中...' }}</text>
				</view>
				<view class="btn-row">
					<button class="ghost-btn" @click="copyCode">复制邀请码</button>
					<button class="patnest-primary-btn" @click="copyShare">复制分享文案</button>
				</view>
			</view>

			<view class="stats patnest-card">
				<view class="st-item">
					<text class="st-num">{{ invitedCount }}</text>
					<text class="st-label">已邀请</text>
				</view>
				<view class="st-item">
					<text class="st-num">{{ rewardedCount }}</text>
					<text class="st-label">完成首单</text>
				</view>
				<view class="st-item">
					<text class="st-num">{{ points }}</text>
					<text class="st-label">每次奖励</text>
				</view>
			</view>

			<view class="rules patnest-card">
				<text class="r-title">活动规则</text>
				<text class="r-line">1. 邀请码由平台按账号生成，不可自行修改。</text>
				<text class="r-line">2. 好友注册时填写邀请码才会绑定邀请关系。</text>
				<text class="r-line">3. 好友首单完成后双方各得 {{ points }} 积分；100 积分 = 1 元，可在下单时抵扣。</text>
				<text class="r-line">4. 每位被邀请人只奖励一次，奖励发放后不随该单退款回收。</text>
				<text v-if="myReward.granted" class="r-line ok">你已通过邀请码领取过首单奖励。</text>
			</view>
		</block>
	</view>
</template>

<script>
	import { inviteApi } from '../../utils/api.js'
	import { isLoggedIn } from '../../utils/auth.js'

	export default {
		data() {
			return {
				logged: false,
				code: '',
				invitedCount: 0,
				rewardedCount: 0,
				points: 100,
				myReward: {}
			}
		},
		onShow() {
			this.logged = isLoggedIn()
			if (this.logged) this.load()
		},
		methods: {
			async load() {
				try {
					const d = (await inviteApi.my()) || {}
					this.code = d.invite_code || ''
					this.invitedCount = d.invited_count || 0
					this.rewardedCount = d.rewarded_count || 0
					this.points = d.points_per_invite || 100
					this.myReward = d.my_reward || {}
				} catch (e) {
					// 错误提示已在 request 层统一处理
				}
			},
			copyCode() {
				if (!this.code) {
					uni.showToast({ title: '邀请码加载中', icon: 'none' })
					return
				}
				uni.setClipboardData({ data: this.code })
			},
			copyShare() {
				if (!this.code) {
					uni.showToast({ title: '邀请码加载中', icon: 'none' })
					return
				}
				uni.setClipboardData({
					data: '我在宠栖 PetNest 给毛孩子找寄养，用我的邀请码 ' + this.code +
						' 注册，我们各得 ' + this.points + ' 积分。'
				})
			},
			goLogin() {
				uni.navigateTo({ url: '/pages/login/login' })
			}
		}
	}
</script>

<style scoped>
	.invite-page {
		padding: 32rpx 28rpx;
		min-height: 100vh;
		box-sizing: border-box;
	}
	.hero {
		display: flex;
		flex-direction: column;
		align-items: center;
		padding: 48rpx 32rpx;
		margin-bottom: 24rpx;
	}
	.h-title {
		font-size: 36rpx;
		font-weight: 700;
		color: #1B1E23;
	}
	.h-sub {
		margin-top: 12rpx;
		font-size: 25rpx;
		color: #9AA3AF;
		text-align: center;
	}
	.code-box {
		margin: 36rpx 0;
		padding: 24rpx 48rpx;
		border-radius: 16rpx;
		background: #FDF1F3;
	}
	.code {
		font-size: 46rpx;
		font-weight: 700;
		letter-spacing: 6rpx;
		color: #AF4A62;
	}
	.btn-row {
		display: flex;
		width: 100%;
	}
	.btn-row button {
		flex: 1;
		margin: 0 10rpx;
		font-size: 27rpx;
	}
	.ghost-btn {
		background: #FFFFFF;
		color: #AF4A62;
		border: 1rpx solid #AF4A62;
		border-radius: 44rpx;
	}
	.stats {
		display: flex;
		padding: 32rpx 0;
		margin-bottom: 24rpx;
	}
	.st-item {
		flex: 1;
		display: flex;
		flex-direction: column;
		align-items: center;
	}
	.st-num {
		font-size: 40rpx;
		font-weight: 700;
		color: #1B1E23;
	}
	.st-label {
		margin-top: 8rpx;
		font-size: 24rpx;
		color: #9AA3AF;
	}
	.rules {
		display: flex;
		flex-direction: column;
		padding: 32rpx;
	}
	.r-title {
		font-size: 30rpx;
		font-weight: 600;
		color: #1B1E23;
		margin-bottom: 20rpx;
	}
	.r-line {
		font-size: 25rpx;
		line-height: 1.8;
		color: #6B7280;
	}
	.r-line.ok {
		color: #2E9E6B;
	}
	.empty {
		display: flex;
		flex-direction: column;
		align-items: center;
		padding: 80rpx 32rpx;
	}
	.e-title {
		font-size: 30rpx;
		color: #1B1E23;
		margin-bottom: 32rpx;
	}
</style>
