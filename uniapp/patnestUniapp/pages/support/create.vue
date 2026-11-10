<template>
	<view class="create-ticket-page patnest-page">
		<view class="block patnest-card">
			<view class="b-title">问题分类</view>
			<view class="radio-row wrap">
				<view
					v-for="c in categories"
					:key="c.value"
					:class="['radio', form.category === c.value ? 'on' : '']"
					@click="form.category = c.value"
				>
					{{ c.label }}
				</view>
			</view>
		</view>

		<view class="block patnest-card">
			<view class="b-title">标题<text class="req">*</text></view>
			<input class="input" v-model="form.title" placeholder="一句话描述问题" maxlength="128" />
		</view>

		<view class="block patnest-card">
			<view class="b-title">详细描述<text class="req">*</text></view>
			<textarea
				class="textarea"
				v-model="form.content"
				placeholder="请描述问题经过、期望的解决方式（≤2000 字）"
				maxlength="2000"
			/>
		</view>

		<view class="note">
			<text class="note-text">提交后平台客服会在工单中回复，请留意消息中心通知。</text>
		</view>

		<button class="patnest-primary-btn submit" :disabled="submitting" @click="submit">
			{{ submitting ? '提交中...' : '提交工单' }}
		</button>
	</view>
</template>

<script>
	import { supportApi } from '../../utils/api.js'
	import { TICKET_CATEGORY_TEXT } from '../../config.js'

	export default {
		data() {
			return {
				submitting: false,
				categories: Object.keys(TICKET_CATEGORY_TEXT).map((k) => ({ value: k, label: TICKET_CATEGORY_TEXT[k] })),
				form: {
					category: 'order',
					title: '',
					content: ''
				}
			}
		},
		methods: {
			async submit() {
				const title = (this.form.title || '').trim()
				const content = (this.form.content || '').trim()
				if (!title) return uni.showToast({ title: '请填写标题', icon: 'none' })
				if (!content) return uni.showToast({ title: '请填写详细描述', icon: 'none' })
				this.submitting = true
				try {
					await supportApi.create({
						category: this.form.category,
						title,
						content
					})
					uni.showToast({ title: '工单已提交', icon: 'success' })
					setTimeout(() => uni.navigateBack(), 600)
				} catch (e) {
					// 错误提示已在 request 层处理
				} finally {
					this.submitting = false
				}
			}
		}
	}
</script>

<style scoped>
	.create-ticket-page {
		padding: 24rpx;
		padding-bottom: 160rpx;
	}
	.block {
		padding: 28rpx;
		margin-bottom: 24rpx;
	}
	.b-title {
		font-size: 30rpx;
		font-weight: 600;
		margin-bottom: 20rpx;
		color: #1B1E23;
	}
	.req {
		color: #C2456A;
		margin-left: 4rpx;
	}
	.radio-row {
		display: flex;
	}
	.radio-row.wrap {
		flex-wrap: wrap;
	}
	.radio {
		padding: 14rpx 28rpx;
		margin: 0 16rpx 12rpx 0;
		background: #F6F7F8;
		border-radius: 12rpx;
		font-size: 26rpx;
		color: #5C6470;
	}
	.radio.on {
		background: #FDF1F4;
		color: #AF4A62;
		font-weight: 600;
	}
	.input {
		font-size: 28rpx;
		color: #1B1E23;
	}
	.textarea {
		width: 100%;
		height: 220rpx;
		font-size: 28rpx;
		color: #1B1E23;
	}
	.note {
		padding: 0 8rpx 24rpx;
	}
	.note-text {
		font-size: 22rpx;
		color: #9AA3AF;
		line-height: 1.6;
	}
	.submit {
		position: fixed;
		left: 24rpx;
		right: 24rpx;
		bottom: calc(24rpx + env(safe-area-inset-bottom));
		height: 88rpx;
		line-height: 88rpx;
	}
</style>
