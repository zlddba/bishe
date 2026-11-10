<template>
	<view class="profile-edit-page patnest-page">
		<view class="form">
			<view class="avatar-row" @click="chooseAvatar">
				<image class="avatar" :src="form.avatar_url || '/static/avatar-default.jpg'" mode="aspectFill" />
				<text class="avatar-tip">点击更换头像</text>
			</view>

			<view class="field patnest-card">
				<text class="label">昵称</text>
				<input class="input" v-model="form.nickname" placeholder="展示名称" maxlength="64" />
			</view>
			<view class="field patnest-card">
				<view class="label-row">
					<text class="label no-margin">手机号</text>
					<text :class="['bind-tag', phoneBound ? 'ok' : 'warn']">
						{{ phoneBound ? '已绑定' : '未绑定' }}
					</text>
				</view>
				<input
					class="input"
					type="number"
					maxlength="11"
					v-model="form.phone"
					:placeholder="phoneBound ? '修改手机号（留空表示不修改）' : '请输入 11 位手机号'"
				/>
				<text v-if="phoneError" class="tip err">{{ phoneError }}</text>
				<text v-else-if="phoneBound" class="tip">
					手机号可用于登录与找回账号{{ maskedPhone ? '（当前 ' + maskedPhone + '）' : '' }}
				</text>
				<text v-else class="tip">
					你还没有绑定手机号，绑定后就能用手机号直接登录，也能在忘记密码时找回账号
				</text>
			</view>
			<view class="field patnest-card">
				<text class="label">邮箱</text>
				<input class="input" v-model="form.email" placeholder="可选" maxlength="128" />
			</view>
			<view class="field patnest-card">
				<text class="label">联系地址</text>
				<input class="input" v-model="form.address" placeholder="用于接宠/送宠" maxlength="255" />
			</view>
			<view class="field patnest-card">
				<text class="label">紧急联系人</text>
				<input class="input" v-model="form.contact_name" placeholder="姓名" maxlength="64" />
			</view>
			<view class="field patnest-card">
				<text class="label">紧急联系电话</text>
				<input class="input" type="number" v-model="form.contact_phone" placeholder="手机号" maxlength="20" />
			</view>
		</view>

		<button class="patnest-primary-btn save" :disabled="saving" @click="save">
			{{ saving ? '保存中...' : '保存' }}
		</button>
	</view>
</template>

<script>
	import { authApi, uploadApi } from '../../utils/api.js'
	import { getUserInfo, setUserInfo } from '../../utils/auth.js'
	import { checkPhone, maskPhone } from '../../utils/phone.js'

	export default {
		data() {
			return {
				saving: false,
				loading: false,
				// 服务端已绑定的手机号（用于判断是否需要提示绑定）
				boundPhone: '',
				phoneError: '',
				form: {
					nickname: '',
					phone: '',
					email: '',
					avatar_url: '',
					address: '',
					contact_name: '',
					contact_phone: ''
				}
			}
		},
		computed: {
			phoneBound() {
				return !!this.boundPhone
			},
			maskedPhone() {
				return maskPhone(this.boundPhone)
			}
		},
		watch: {
			'form.phone'(v) {
				// 输入时实时校验；已绑定状态下留空表示不修改，不报错
				if (!v) {
					this.phoneError = ''
					return
				}
				this.phoneError = checkPhone(v)
			}
		},
		onLoad() {
			// 登录响应里只有 id/username/nickname/role，
			// 其余字段必须走 GET /auth/me 才能拿到
			const cached = getUserInfo() || {}
			this.form.nickname = cached.nickname || ''
			this.form.avatar_url = cached.avatar_url || ''
			this.load()
		},
		methods: {
			async load() {
				this.loading = true
				try {
					const u = await authApi.getProfile()
					if (!u) return
					this.boundPhone = u.phone || ''
					this.form = {
						nickname: u.nickname || '',
						phone: '',
						email: u.email || '',
						avatar_url: u.avatar_url || '',
						address: u.address || '',
						contact_name: u.contact_name || '',
						contact_phone: u.contact_phone || ''
					}
				} catch (e) {
					// 错误提示已在 request 层处理
				} finally {
					this.loading = false
				}
			},
			chooseAvatar() {
				uni.chooseImage({
					count: 1,
					sizeType: ['compressed'],
					success: async (res) => {
						try {
							const data = await uploadApi.image(res.tempFilePaths[0])
							this.form.avatar_url = data.url
						} catch (e) {}
					}
				})
			},
			async save() {
				// 手机号：留空表示不修改（已绑定时），未绑定时必须填
				const phone = String(this.form.phone || '').trim()
				if (phone) {
					const err = checkPhone(phone)
					if (err) return uni.showToast({ title: err, icon: 'none' })
				} else if (!this.phoneBound) {
					return uni.showToast({ title: '请填写手机号', icon: 'none' })
				}
				if (this.form.contact_phone && !/^1[3-9]\d{9}$/.test(this.form.contact_phone)) {
					return uni.showToast({ title: '联系人手机号格式不正确', icon: 'none' })
				}
				this.saving = true
				try {
					const payload = {}
					Object.keys(this.form).forEach((k) => {
						if (this.form[k] !== '' && this.form[k] != null) payload[k] = this.form[k]
					})
					// 手机号留空时不要传空串，否则会被后端当成「清空手机号」
					if (!phone) delete payload.phone
					await authApi.updateProfile(payload)
					// 后端返回 { updated: true }，用最新资料覆盖本地缓存
					try {
						const u = await authApi.getProfile()
						if (u) {
							setUserInfo(u)
							this.boundPhone = u.phone || ''
						}
					} catch (e) {}
					uni.showToast({ title: '已保存', icon: 'success' })
					setTimeout(() => uni.navigateBack(), 500)
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
	.profile-edit-page {
		padding: 24rpx;
		padding-bottom: 160rpx;
	}
	.avatar-row {
		display: flex;
		flex-direction: column;
		align-items: center;
		margin-bottom: 24rpx;
	}
	.avatar {
		width: 150rpx;
		height: 150rpx;
		border-radius: 24rpx;
		background: #EFF1F3;
	}
	.avatar-tip {
		font-size: 22rpx;
		color: #9AA3AF;
		margin-top: 10rpx;
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
	.label-row {
		display: flex;
		align-items: center;
		justify-content: space-between;
		margin-bottom: 16rpx;
	}
	.label.no-margin {
		margin-bottom: 0;
	}
	.bind-tag {
		font-size: 21rpx;
		padding: 2rpx 14rpx;
		border-radius: 20rpx;
	}
	.bind-tag.ok {
		color: #2F9E63;
		background: #EAF6EF;
	}
	.bind-tag.warn {
		color: #D97706;
		background: #FDF3E3;
	}
	.input {
		font-size: 28rpx;
		color: #1B1E23;
	}
	.tip {
		display: block;
		font-size: 22rpx;
		color: #9AA3AF;
		margin-top: 12rpx;
		line-height: 1.5;
	}
	.tip.err {
		color: #D97706;
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
