<template>
	<view class="pwd-page patnest-page">
		<view class="form">
			<view class="field patnest-card">
				<text class="label">原密码<text class="req">*</text></text>
				<input class="input" v-model="form.old_password" password placeholder="请输入当前密码" />
			</view>
			<view class="field patnest-card">
				<text class="label">新密码<text class="req">*</text></text>
				<input class="input" v-model="form.new_password" password placeholder="8-128 位，需含大小写字母+数字+特殊字符" />
			</view>
			<view class="field patnest-card">
				<text class="label">确认新密码<text class="req">*</text></text>
				<input class="input" v-model="confirm" password placeholder="再次输入新密码" />
			</view>
		</view>

		<view class="notice">
			<text class="notice-text">修改密码后，当前账号在所有设备上的登录状态都会失效，需要重新登录。</text>
		</view>

		<button class="patnest-primary-btn save" :disabled="saving" @click="save">
			{{ saving ? '提交中...' : '确认修改' }}
		</button>
	</view>
</template>

<script>
	import { authApi } from '../../utils/api.js'
	import { clearAuth } from '../../utils/auth.js'

	export default {
		data() {
			return {
				saving: false,
				confirm: '',
				form: {
					old_password: '',
					new_password: ''
				}
			}
		},
		methods: {
			async save() {
				const { old_password, new_password } = this.form
				if (!old_password) return uni.showToast({ title: '请输入原密码', icon: 'none' })
				if (!new_password) return uni.showToast({ title: '请输入新密码', icon: 'none' })
				if (new_password.length < 8 || new_password.length > 128) {
					return uni.showToast({ title: '新密码长度需 8-128 位', icon: 'none' })
				}
				if (
					!/[A-Z]/.test(new_password) ||
					!/[a-z]/.test(new_password) ||
					!/\d/.test(new_password) ||
					!/[^A-Za-z0-9]/.test(new_password)
				) {
					return uni.showToast({ title: '新密码需含大小写字母+数字+特殊字符', icon: 'none' })
				}
				if (new_password !== this.confirm) {
					return uni.showToast({ title: '两次输入的密码不一致', icon: 'none' })
				}
				this.saving = true
				try {
					await authApi.updatePassword({ old_password, new_password })
					uni.showToast({ title: '密码已修改，请重新登录', icon: 'none' })
					clearAuth()
					setTimeout(() => uni.reLaunch({ url: '/pages/login/login' }), 1200)
				} catch (e) {
					// 错误提示已在 request 层处理
				} finally {
					this.saving = false
				}
			}
		}
	}
</script>

<style scoped>
	.pwd-page {
		padding: 24rpx;
		padding-bottom: 160rpx;
	}
	.field {
		padding: 24rpx 28rpx;
		margin-bottom: 20rpx;
	}
	.label {
		font-size: 26rpx;
		color: #5C6470;
		display: block;
		margin-bottom: 16rpx;
	}
	.req {
		color: #C2456A;
		margin-left: 4rpx;
	}
	.input {
		font-size: 28rpx;
		color: #1B1E23;
	}
	.notice {
		padding: 0 8rpx 24rpx;
	}
	.notice-text {
		font-size: 22rpx;
		color: #9AA3AF;
		line-height: 1.6;
	}
	.save {
		position: fixed;
		left: 24rpx;
		right: 24rpx;
		bottom: calc(24rpx + env(safe-area-inset-bottom));
		height: 88rpx;
		line-height: 88rpx;
	}
</style>
