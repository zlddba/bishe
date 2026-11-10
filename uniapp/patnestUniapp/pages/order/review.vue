<template>
	<view class="review-page patnest-page">
		<view v-if="readonly" class="block patnest-card">
			<view class="b-title">我的评价</view>
			<view v-for="d in dims" :key="d.key" class="score-row">
				<text class="d-name">{{ d.label }}</text>
				<score-stars :score="form[d.key]" />
			</view>
			<text class="comment-text">{{ form.owner_comment }}</text>
			<view v-if="form.owner_photos && form.owner_photos.length" class="photos">
				<image v-for="(p, i) in form.owner_photos" :key="i" class="photo" :src="p" mode="aspectFill" />
			</view>
			<text v-if="replyText" class="reply">店家回复：{{ replyText }}</text>
			<text class="readonly-tip">一单一评，评价发布后不可修改</text>
		</view>

		<block v-else>
			<view class="block patnest-card">
				<view class="b-title">为这次寄养打分</view>
				<view v-for="d in dims" :key="d.key" class="score-row">
					<text class="d-name">{{ d.label }}</text>
					<score-stars
						:score="form[d.key]"
						interactive
						:desc="scoreDesc(d.key)"
						@change="setScore(d.key, $event)"
					/>
				</view>
			</view>

			<view class="block patnest-card">
				<view class="b-title">评价内容</view>
				<textarea
					class="textarea"
					v-model="form.owner_comment"
					placeholder="分享你的体验，帮助更多主人做选择（1-1000 字）"
					maxlength="1000"
				/>
				<view class="photos">
					<view v-for="(p, i) in form.owner_photos" :key="i" class="photo-item">
						<image class="photo" :src="p" mode="aspectFill" />
						<text class="photo-del" @click="delPhoto(i)">✕</text>
					</view>
					<view v-if="form.owner_photos.length < 3" class="photo-add" @click="choosePhoto">＋</view>
				</view>
				<text class="tip">最多 3 张照片</text>
			</view>

			<button class="patnest-primary-btn submit" :disabled="submitting" @click="submit">
				{{ submitting ? '提交中...' : '提交评价' }}
			</button>
		</block>
	</view>
</template>

<script>
	import { reviewApi, uploadApi } from '../../utils/api.js'

	const SCORE_DESC = ['', '很差', '较差', '一般', '满意', '非常满意']

	export default {
		data() {
			return {
				orderId: null,
				readonly: false,
				submitting: false,
				replyText: '',
				dims: [
					{ key: 'overall_score', label: '综合' },
					{ key: 'service_score', label: '服务' },
					{ key: 'environment_score', label: '环境' },
					{ key: 'hygiene_score', label: '卫生' }
				],
				form: {
					overall_score: 5,
					service_score: 5,
					environment_score: 5,
					hygiene_score: 5,
					owner_comment: '',
					owner_photos: []
				}
			}
		},
		onLoad(options) {
			this.orderId = options.id
			this.loadExisting()
		},
		methods: {
			async loadExisting() {
				try {
					const r = await reviewApi.get(this.orderId)
					if (r && r.id) {
						this.readonly = true
						this.replyText = r.reply_text || ''
						this.form = {
							overall_score: r.overall_score,
							service_score: r.service_score,
							environment_score: r.environment_score,
							hygiene_score: r.hygiene_score,
							owner_comment: r.owner_comment || '',
							owner_photos: r.owner_photos || []
						}
					}
				} catch (e) {
					// 404 表示尚未评价，正常进入编辑态
				}
			},
			scoreDesc(key) {
				return SCORE_DESC[this.form[key]] || ''
			},
			setScore(key, n) {
				this.form[key] = n
			},
			choosePhoto() {
				uni.chooseImage({
					count: 3 - this.form.owner_photos.length,
					sizeType: ['compressed'],
					success: async (res) => {
						for (const p of res.tempFilePaths) {
							try {
								const data = await uploadApi.image(p)
								this.form.owner_photos.push(data.url)
							} catch (e) {}
						}
					}
				})
			},
			delPhoto(i) {
				this.form.owner_photos.splice(i, 1)
			},
			async submit() {
				const comment = (this.form.owner_comment || '').trim()
				if (!comment) {
					return uni.showToast({ title: '请填写评价内容', icon: 'none' })
				}
				if (comment.length > 1000) {
					return uni.showToast({ title: '评价内容最多 1000 字', icon: 'none' })
				}
				this.submitting = true
				try {
					await reviewApi.submit(this.orderId, {
						overall_score: this.form.overall_score,
						service_score: this.form.service_score,
						environment_score: this.form.environment_score,
						hygiene_score: this.form.hygiene_score,
						owner_comment: comment,
						owner_photos: this.form.owner_photos
					})
					uni.showToast({ title: '评价成功', icon: 'success' })
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
	.review-page {
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
	.score-row {
		display: flex;
		align-items: center;
		justify-content: space-between;
		padding: 14rpx 0;
	}
	.d-name {
		font-size: 28rpx;
		color: #1B1E23;
		flex-shrink: 0;
	}
	.textarea {
		width: 100%;
		height: 160rpx;
		font-size: 28rpx;
		color: #1B1E23;
	}
	.comment-text {
		display: block;
		font-size: 28rpx;
		color: #1B1E23;
		line-height: 1.7;
		margin-top: 12rpx;
	}
	.reply {
		display: block;
		font-size: 24rpx;
		color: #9AA3AF;
		margin-top: 16rpx;
		padding: 16rpx;
		background: #F6F7F8;
		border-radius: 12rpx;
	}
	.readonly-tip {
		display: block;
		font-size: 22rpx;
		color: #B7BDC5;
		margin-top: 16rpx;
	}
	.photos {
		display: flex;
		flex-wrap: wrap;
		margin-top: 16rpx;
	}
	.photo-item {
		position: relative;
		width: 150rpx;
		height: 150rpx;
		margin: 0 16rpx 16rpx 0;
	}
	.photo {
		width: 150rpx;
		height: 150rpx;
		border-radius: 12rpx;
		background: #EFF1F3;
	}
	.photo-del {
		position: absolute;
		top: -10rpx;
		right: -10rpx;
		width: 36rpx;
		height: 36rpx;
		line-height: 36rpx;
		text-align: center;
		background: rgba(27, 30, 35, 0.6);
		color: #fff;
		border-radius: 50%;
		font-size: 22rpx;
	}
	.photo-add {
		width: 150rpx;
		height: 150rpx;
		border-radius: 12rpx;
		border: 2rpx dashed #D4D8DD;
		display: flex;
		align-items: center;
		justify-content: center;
		font-size: 60rpx;
		color: #B7BDC5;
	}
	.tip {
		display: block;
		font-size: 22rpx;
		color: #9AA3AF;
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
