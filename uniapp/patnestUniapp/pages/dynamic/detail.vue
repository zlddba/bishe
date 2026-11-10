<template>
	<view class="dyn-detail-page patnest-page">
		<!-- 动态主体 -->
		<view class="main patnest-card">
			<view class="m-head">
				<text class="m-sitter">{{ dyn.sitter_name || '寄养机构' }}</text>
				<text class="m-time">{{ fmtTime(dyn.created_at) }}</text>
			</view>
			<text class="m-content">{{ dyn.content || '（动态内容加载失败）' }}</text>
			<view v-if="dyn.photos && dyn.photos.length" class="m-photos">
				<image
					v-for="(p, i) in dyn.photos"
					:key="i"
					class="m-photo"
					:src="p"
					mode="aspectFill"
					@click="previewPhoto(i)"
				/>
			</view>
			<view v-if="dyn.tags && dyn.tags.length" class="m-tags">
				<text v-for="(t, i) in dyn.tags" :key="i" class="m-tag">{{ t }}</text>
			</view>
			<view class="m-foot">
				<text class="m-order" v-if="dyn.order_no">{{ dyn.order_no }}</text>
				<view class="m-like" @click="toggleLike">
					<image
						class="ml-icon"
						:src="liked ? '/static/icons/heart-fill.png' : '/static/icons/heart.png'"
						mode="aspectFit"
					/>
					<text class="ml-count">{{ likes }}</text>
				</view>
			</view>
		</view>

		<!-- 留言列表 -->
		<view class="cmt-block patnest-card">
			<view class="sec-title">留言（{{ comments.length }}）</view>
			<view v-if="comments.length === 0" class="cmt-empty">还没有留言，来抢个沙发～</view>
			<view v-for="c in comments" :key="c.id" class="cmt-item">
				<view class="c-avatar">{{ (c.user || '友').slice(0, 1) }}</view>
				<view class="c-body">
					<view class="c-top">
						<text class="c-user">{{ c.user || '匿名用户' }}</text>
						<text class="c-time">{{ fmtTime(c.created_at) }}</text>
					</view>
					<text class="c-content">{{ c.content }}</text>
				</view>
			</view>
		</view>

		<!-- 留言输入 -->
		<view class="bar">
			<input class="bar-input" v-model="text" placeholder="说点什么…" confirm-type="send" @confirm="send" />
			<text class="bar-send" @click="send">发送</text>
		</view>
	</view>
</template>

<script>
	import { dynamicApi } from '../../utils/api.js'
	import { fmtDateTime } from '../../utils/format.js'

	export default {
		data() {
			return {
				id: null,
				orderId: null,
				dyn: {},
				comments: [],
				likes: 0,
				liked: false,
				text: '',
				sending: false
			}
		},
		onLoad(options) {
			this.id = options.id
			this.orderId = options.order_id
			this.load()
		},
		methods: {
			// 点击照片看大图（原生图片预览，支持左右滑动与缩放）
			previewPhoto(index) {
				const photos = (this.dyn && this.dyn.photos) || []
				if (!photos.length) return
				const urls = photos.filter((p) => !!p)
				if (!urls.length) return
				// current 要是 urls 里的下标
				let cur = 0
				if (typeof index === 'number' && urls.indexOf(photos[index]) >= 0) {
					cur = urls.indexOf(photos[index])
				}
				uni.previewImage({
					urls,
					current: urls[cur],
					fail: () => {
						uni.showToast({ title: '无法预览图片', icon: 'none' })
					}
				})
			},
			async load() {
				// 优先用后端提供的单条动态详情接口（含 order_no / sitter_name）
				try {
					const res = await dynamicApi.detail(this.id)
					if (res && res.id) {
						this.dyn = res
						this.likes = res.like_count || 0
						this.liked = !!res.liked
						await this.loadComments()
						return
					}
				} catch (e) {
					// 退回用订单动态列表定位
				}
				// 兜底：通过所属订单的动态列表定位该条动态
				if (this.orderId) {
					try {
						const res = await dynamicApi.list(this.orderId, { per_page: 50 })
						const items = (res && res.items) || []
						const hit = items.find((d) => String(d.id) === String(this.id))
						if (hit) this.dyn = hit
					} catch (e) {}
				}
				await this.loadComments()
			},
			async loadComments() {
				try {
					const res = await dynamicApi.comments(this.id)
					this.comments = (res && res.items) || []
					this.likes = (res && res.likes) || 0
				} catch (e) {}
			},
			async toggleLike() {
				try {
					if (this.liked) {
						await dynamicApi.unlike(this.id)
						this.liked = false
						this.likes = Math.max(0, this.likes - 1)
					} else {
						await dynamicApi.like(this.id)
						this.liked = true
						this.likes += 1
					}
				} catch (e) {}
			},
			async send() {
				const content = (this.text || '').trim()
				if (!content) return
				if (this.sending) return
				this.sending = true
				try {
					await dynamicApi.comment(this.id, content)
					this.text = ''
					uni.showToast({ title: '留言成功', icon: 'none' })
					await this.loadComments()
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
	.dyn-detail-page {
		padding: 24rpx;
		padding-bottom: 140rpx;
	}
	.main {
		padding: 28rpx;
		margin-bottom: 24rpx;
	}
	.m-head {
		display: flex;
		justify-content: space-between;
		align-items: center;
	}
	.m-sitter {
		font-size: 30rpx;
		font-weight: 600;
		color: #1B1E23;
	}
	.m-time {
		font-size: 22rpx;
		color: #B7BDC5;
	}
	.m-content {
		display: block;
		font-size: 30rpx;
		color: #3A4149;
		line-height: 1.7;
		margin-top: 16rpx;
	}
	.m-photos {
		display: flex;
		flex-wrap: wrap;
		margin-top: 16rpx;
	}
	.m-photo {
		width: 210rpx;
		height: 210rpx;
		border-radius: 12rpx;
		margin: 0 12rpx 12rpx 0;
		background: #EFF1F3;
	}
	.m-tags {
		margin-top: 6rpx;
	}
	.m-tag {
		font-size: 22rpx;
		color: #3E7BFA;
		background: #EAF1FE;
		padding: 4rpx 14rpx;
		border-radius: 8rpx;
		margin-right: 10rpx;
	}
	.m-foot {
		display: flex;
		justify-content: space-between;
		align-items: center;
		margin-top: 16rpx;
		padding-top: 16rpx;
		border-top: 1rpx solid #EFF1F3;
	}
	.m-order {
		font-size: 22rpx;
		color: #9AA3AF;
	}
.m-like {
		display: flex;
		align-items: center;
	}
	.ml-icon {
		width: 34rpx;
		height: 34rpx;
		margin-right: 8rpx;
		flex-shrink: 0;
	}
	.ml-count {
		font-size: 26rpx;
		color: #B7BDC5;
	}
	.cmt-block {
		padding: 28rpx;
	}
	.sec-title {
		font-size: 30rpx;
		font-weight: 600;
		margin-bottom: 16rpx;
		color: #1B1E23;
	}
	.cmt-empty {
		font-size: 26rpx;
		color: #B7BDC5;
		padding: 30rpx 0;
		text-align: center;
	}
	.cmt-item {
		display: flex;
		padding: 16rpx 0;
		border-bottom: 1rpx solid #EFF1F3;
	}
	.cmt-item:last-child {
		border-bottom: none;
	}
	.c-avatar {
		width: 60rpx;
		height: 60rpx;
		border-radius: 50%;
		background: #FDF1F4;
		color: #AF4A62;
		font-size: 26rpx;
		display: flex;
		align-items: center;
		justify-content: center;
		flex-shrink: 0;
	}
	.c-body {
		flex: 1;
		margin-left: 16rpx;
	}
	.c-top {
		display: flex;
		justify-content: space-between;
		align-items: center;
	}
	.c-user {
		font-size: 26rpx;
		color: #5C6470;
	}
	.c-time {
		font-size: 20rpx;
		color: #B7BDC5;
	}
	.c-content {
		display: block;
		font-size: 27rpx;
		color: #1B1E23;
		margin-top: 6rpx;
		line-height: 1.6;
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
