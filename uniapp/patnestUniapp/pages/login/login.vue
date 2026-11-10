<template>
	<view class="login-page patnest-page">
		<view class="brand">
			<image class="logo" src="/static/logo.png" mode="aspectFit" />
			<text class="title">宠栖 PatNest</text>
			<text class="subtitle">为爱宠找一个临时的家</text>
		</view>

		<view class="tabs">
			<view :class="['tab', mode === 'login' ? 'active' : '']" @click="mode = 'login'">登录</view>
			<view :class="['tab', mode === 'register' ? 'active' : '']" @click="mode = 'register'">注册</view>
		</view>

		<view class="form patnest-card">
			<view class="field">
				<text class="label">{{ mode === 'login' ? '账号 / 手机号' : '账号' }}</text>
				<input
					class="input"
					v-model="username"
					:placeholder="mode === 'login' ? '输入用户名或手机号' : '3-50 位字母数字下划线'"
				/>
				<text v-if="mode === 'login' && loginHint" class="hint">{{ loginHint }}</text>
			</view>
			<view class="field">
				<text class="label">密码</text>
				<input class="input" v-model="password" password placeholder="8 位以上，含大小写字母+数字+特殊字符" />
			</view>

			<view v-if="mode === 'register'" class="field">
				<text class="label">手机号<text class="req">*</text></text>
				<input
					class="input"
					type="number"
					maxlength="11"
					v-model="phone"
					placeholder="11 位手机号，用于登录与找回账号"
				/>
				<text v-if="phoneError" class="hint err">{{ phoneError }}</text>
				<text v-else class="hint">手机号是登录账号的唯一凭证，注册后不可随意更换</text>
			</view>

			<view v-if="mode === 'register'" class="field">
				<text class="label">身份</text>
				<view class="role-list">
					<view :class="['role-item', role === 'owner' ? 'active' : '']" @click="role = 'owner'">
						<image class="r-icon" src="/static/icons/pet.png" mode="aspectFit" />
						<text class="role-text">我是宠物主人</text>
					</view>
				</view>
				<text class="tip">小程序端仅支持主人账号；机构账号可在登录后于「我的 → 申请成为机构」提交入驻资料。</text>
			</view>

			<!-- 记住密码 -->
			<view v-if="mode === 'login'" class="remember-row" @click="remember = !remember">
				<view :class="['rm-box', remember ? 'on' : '']">
					<image v-if="remember" class="rm-check" src="/static/icons/check-white.png" mode="aspectFit" />
				</view>
				<text class="rm-text">记住账号密码</text>
			</view>

			<button class="submit patnest-primary-btn" :disabled="loading" @click="onSubmit">
				{{ loading ? '处理中...' : (mode === 'login' ? '登 录' : '注 册 并 登 录') }}
			</button>
		</view>
	</view>
</template>

<script>
	import { authApi } from '../../utils/api.js'
	import { applyAuthResult } from '../../utils/auth.js'
	import { ROLE } from '../../config.js'
	import { isPhone, looksLikePhone, checkPhone, checkUsername } from '../../utils/phone.js'
	import { getRemembered, saveRemembered } from '../../utils/remember.js'

	export default {
		data() {
			return {
				mode: 'login',
				username: '',
				password: '',
				phone: '',
				phoneError: '',
				role: ROLE.OWNER,
				loading: false,
				// 记住密码
				remember: false
			}
		},
		computed: {
			// 登录时根据输入内容提示用户这是用户名还是手机号
			loginHint() {
				const v = this.username.trim()
				if (!v) return ''
				if (looksLikePhone(v)) {
					return isPhone(v) ? '将使用手机号登录' : '手机号应为 11 位数字'
				}
				return '将使用用户名登录'
			}
		},
		watch: {
			phone(v) {
				// 输入时实时校验，但空值不立刻报错（用户还没填完）
				this.phoneError = v ? checkPhone(v) : ''
			}
		},
		onLoad() {
			// 自动填充上次记住的账号密码
			const saved = getRemembered()
			if (saved) {
				this.username = saved.username
				this.password = saved.password
				this.remember = true
			}
		},
		methods: {
			validate() {
				if (!this.username || !this.password) {
					uni.showToast({ title: '请填写账号和密码', icon: 'none' })
					return false
				}
				if (this.mode === 'login') {
					// 登录：用户名或手机号都可以
					const v = this.username.trim()
					if (looksLikePhone(v) && !isPhone(v)) {
						uni.showToast({ title: '手机号应为 11 位数字', icon: 'none' })
						return false
					}
					if (!looksLikePhone(v) && (v.length < 3 || v.length > 50)) {
						uni.showToast({ title: '账号长度需 3-50 位', icon: 'none' })
						return false
					}
				} else {
					const nameErr = checkUsername(this.username)
					if (nameErr) {
						uni.showToast({ title: nameErr, icon: 'none' })
						return false
					}
					const phoneErr = checkPhone(this.phone)
					if (phoneErr) {
						uni.showToast({ title: phoneErr, icon: 'none' })
						return false
					}
					const p = this.password
					if (
						p.length < 8 ||
						p.length > 128 ||
						!/[A-Z]/.test(p) ||
						!/[a-z]/.test(p) ||
						!/\d/.test(p) ||
						!/[^A-Za-z0-9]/.test(p)
					) {
						uni.showToast({ title: '密码需含大小写字母+数字+特殊字符', icon: 'none' })
						return false
					}
				}
				return true
			},
			async onSubmit() {
				if (!this.validate() || this.loading) return
				this.loading = true
				try {
					let res
					if (this.mode === 'login') {
						// client=app：后端据此拒绝管理员账号从小程序登录
						res = await authApi.login({
							username: this.username.trim(),
							password: this.password,
							client: 'app'
						})
					} else {
						res = await authApi.register({
							username: this.username.trim(),
							password: this.password,
							role: this.role,
							phone: this.phone.trim()
						})
					}
					applyAuthResult(res)
					// 记住账号密码（仅登录模式；注册模式不记密码）
					if (this.mode === 'login') {
						saveRemembered(this.username.trim(), this.password, this.remember)
					}
					const needBind = res && (res.need_bind_phone || (res.user && res.user.phone_bound === false))
					uni.showToast({ title: this.mode === 'login' ? '登录成功' : '注册成功', icon: 'success' })
					setTimeout(() => {
						if (needBind) {
							// 历史账号没有手机号，引导去个人资料页绑定
							this.promptBindPhone()
							return
						}
						const pages = getCurrentPages()
						if (pages.length > 1) {
							uni.navigateBack()
						} else {
							uni.reLaunch({ url: '/pages/index/index' })
						}
					}, 600)
				} catch (e) {
					// 错误提示已在 request 层处理
				} finally {
					this.loading = false
				}
			},
			// 引导绑定手机号：确认后跳个人资料页
			promptBindPhone() {
				uni.showModal({
					title: '请绑定手机号',
					content: '绑定手机号后就可以用手机号直接登录，也能在忘记密码时找回账号。',
					confirmText: '去绑定',
					cancelText: '稍后',
					confirmColor: '#C85B74',
					success: (r) => {
						if (r.confirm) {
							uni.reLaunch({ url: '/pages/mine/profile' })
						} else {
							const pages = getCurrentPages()
							if (pages.length > 1) {
								uni.navigateBack()
							} else {
								uni.reLaunch({ url: '/pages/index/index' })
							}
						}
					}
				})
			}
		}
	}
</script>

<style scoped>
	.login-page {
		padding: 80rpx 48rpx;
		min-height: 100vh;
		box-sizing: border-box;
	}
	.brand {
		display: flex;
		flex-direction: column;
		align-items: center;
		margin-bottom: 60rpx;
	}
	.logo {
		width: 160rpx;
		height: 160rpx;
	}
	.title {
		font-size: 44rpx;
		font-weight: 700;
		margin-top: 16rpx;
		color: #1B1E23;
	}
	.subtitle {
		font-size: 26rpx;
		color: #9AA3AF;
		margin-top: 8rpx;
	}
	.tabs {
		display: flex;
		justify-content: center;
		margin-bottom: 32rpx;
	}
	.tab {
		font-size: 30rpx;
		color: #9AA3AF;
		padding: 12rpx 40rpx;
		position: relative;
	}
	.tab.active {
		color: #AF4A62;
		font-weight: 600;
	}
	.tab.active::after {
		content: '';
		position: absolute;
		left: 50%;
		bottom: 0;
		transform: translateX(-50%);
		width: 48rpx;
		height: 6rpx;
		border-radius: 3rpx;
		background: #E0708A;
	}
	.form {
		padding: 40rpx 36rpx;
	}
	.field {
		margin-bottom: 28rpx;
	}
	.label {
		font-size: 26rpx;
		color: #5C6470;
		margin-bottom: 12rpx;
		display: block;
	}
	.input {
		background: #F6F7F8;
		border-radius: 12rpx;
		height: 84rpx;
		padding: 0 24rpx;
		font-size: 28rpx;
	}
	.req {
		color: #C2456A;
		margin-left: 4rpx;
	}
	.hint {
		display: block;
		font-size: 22rpx;
		color: #9AA3AF;
		margin-top: 10rpx;
		line-height: 1.5;
	}
	.hint.err {
		color: #D97706;
	}
	/* 记住密码 */
	.remember-row {
		display: flex;
		align-items: center;
		margin: -8rpx 0 24rpx;
	}
	.rm-box {
		width: 32rpx;
		height: 32rpx;
		border-radius: 8rpx;
		border: 2rpx solid #C9CED6;
		background: #F6F7F8;
		display: flex;
		align-items: center;
		justify-content: center;
		margin-right: 12rpx;
		box-sizing: border-box;
	}
	.rm-box.on {
		background: #C85B74;
		border-color: #C85B74;
	}
	.rm-check {
		width: 24rpx;
		height: 24rpx;
	}
	.rm-text {
		font-size: 25rpx;
		color: #5C6470;
	}
	.role-list {
		display: flex;
	}
	.role-item {
		display: inline-flex;
		align-items: center;
		justify-content: center;
		padding: 16rpx 28rpx;
		border-radius: 12rpx;
		background: #F6F7F8;
		color: #5C6470;
		font-size: 26rpx;
		border: 2rpx solid transparent;
	}
	.role-item.active {
		background: #FDF1F4;
		color: #AF4A62;
		font-weight: 600;
		border-color: #F6C2CE;
	}
	.r-icon {
		width: 40rpx;
		height: 40rpx;
		margin-right: 12rpx;
		flex-shrink: 0;
	}
	.role-text {
		font-size: 26rpx;
		flex-shrink: 0;
	}
	.tip {
		font-size: 22rpx;
		color: #9AA3AF;
		margin-top: 12rpx;
		display: block;
		line-height: 1.6;
	}
	.submit {
		margin-top: 20rpx;
		height: 88rpx;
		line-height: 88rpx;
		font-size: 30rpx;
	}
</style>
