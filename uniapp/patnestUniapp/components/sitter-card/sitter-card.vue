<template>
	<view class="sitter-card" @click="onTap">
		<image class="cover" :src="cover" mode="aspectFill" />
		<view class="body">
			<view class="row1">
				<text class="name">{{ sitter.name }}</text>
				<text class="type-tag" :style="typeStyle">{{ typeText }}</text>
			</view>
			<view class="addr">
				<text class="addr-text">{{ sitter.address || '地址未填写' }}</text>
			</view>
			<text class="desc">{{ sitter.description || '暂无简介' }}</text>
			<view class="row2">
				<view class="score">
					<text class="star">★</text>
					<text class="score-num">{{ score }}</text>
					<text class="review-count">({{ sitter.review_count || 0 }})</text>
					<text v-if="distanceText" class="dist">· 距你 {{ distanceText }}</text>
				</view>
				<view class="price">
					<text class="price-num">¥{{ price }}</text>
					<text class="price-unit">/{{ priceUnit }}</text>
				</view>
			</view>
		</view>
	</view>
</template>

<script>
	import { SITTER_TYPE_TEXT, PRICE_MODE } from '../../config.js'

	export default {
		name: 'SitterCard',
		// ⚠️ 必须显式声明 emits：否则 'tap' 会被当成原生事件透传到根节点，
		//    父组件的 @tap 处理器会先收到原生事件对象而不是 sitter 数据。
		emits: ['open'],
		props: {
			sitter: {
				type: Object,
				required: true
			},
			// 到用户的距离文案（由页面用 Haversine 算好后传入），为空则不显示
			distanceText: {
				type: String,
				default: ''
			},
			// 为 false 时不跳转详情（用于收藏对比等内嵌场景）
			linkable: {
				type: Boolean,
				default: true
			}
		},
		computed: {
			cover() {
				return this.sitter.cover_photo_url || '/static/avatar-default.jpg'
			},
			// 列表接口返回 id，收藏接口返回 sitter_id
			sitterId() {
				return this.sitter.id || this.sitter.sitter_id
			},
			typeText() {
				return SITTER_TYPE_TEXT[this.sitter.type] || '寄养'
			},
			typeStyle() {
				const map = {
					home: { bg: '#FDF1F4', fg: '#AF4A62' },
					shop: { bg: '#EAF1FE', fg: '#3E7BFA' },
					visit: { bg: '#EAF6EF', fg: '#2F9E63' }
				}
				const c = map[this.sitter.type] || { bg: '#EFF1F3', fg: '#5C6470' }
				return 'background:' + c.bg + ';color:' + c.fg
			},
			score() {
				const v = Number(this.sitter.avg_score) || 0
				return v.toFixed(1)
			},
			price() {
				// per_day 显示日单价，per_visit 显示次单价
				if (this.sitter.price_mode === PRICE_MODE.PER_VISIT) {
					return this.sitter.visit_price != null ? this.sitter.visit_price : '—'
				}
				return this.sitter.daily_price != null ? this.sitter.daily_price : '—'
			},
			priceUnit() {
				return this.sitter.price_mode === PRICE_MODE.PER_VISIT ? '次' : '天'
			}
		},
		methods: {
			onTap() {
				this.$emit('open', this.sitter)
				if (!this.linkable || !this.sitterId) return
				// 带上机构名：详情页加载失败时能告诉用户是哪个机构出了问题
				const nm = this.sitter.name ? encodeURIComponent(this.sitter.name) : ''
				uni.navigateTo({
					url: '/pages/sitter/detail?id=' + this.sitterId + (nm ? '&name=' + nm : '')
				})
			}
		}
	}
</script>

<style scoped>
	.sitter-card {
		display: flex;
		background: #FFFFFF;
		border-radius: 20rpx;
		overflow: hidden;
		box-shadow: 0 4rpx 16rpx rgba(27, 30, 35, 0.04);
		margin-bottom: 24rpx;
	}
	.cover {
		width: 220rpx;
		height: 220rpx;
		flex-shrink: 0;
		background: #EFF1F3;
	}
	.body {
		flex: 1;
		padding: 20rpx 24rpx;
		display: flex;
		flex-direction: column;
		justify-content: space-between;
		min-width: 0;
	}
	.row1 {
		display: flex;
		align-items: center;
		justify-content: space-between;
	}
	.name {
		font-size: 30rpx;
		font-weight: 600;
		color: #1B1E23;
		overflow: hidden;
		text-overflow: ellipsis;
		white-space: nowrap;
		max-width: 360rpx;
	}
	.type-tag {
		font-size: 20rpx;
		padding: 4rpx 12rpx;
		border-radius: 8rpx;
		flex-shrink: 0;
	}
	.addr {
		display: flex;
		align-items: center;
		margin-top: 8rpx;
	}
	.addr-text {
		font-size: 22rpx;
		color: #9AA3AF;
		overflow: hidden;
		text-overflow: ellipsis;
		white-space: nowrap;
	}
	.desc {
		font-size: 24rpx;
		color: #5C6470;
		margin-top: 8rpx;
		overflow: hidden;
		text-overflow: ellipsis;
		white-space: nowrap;
	}
	.row2 {
		display: flex;
		align-items: center;
		justify-content: space-between;
		margin-top: 12rpx;
	}
	.score .star {
		color: #EAB308;
		font-size: 24rpx;
	}
	.score-num {
		color: #D97706;
		font-weight: 600;
		margin: 0 4rpx;
		font-size: 26rpx;
	}
	.review-count {
		color: #9AA3AF;
		font-size: 22rpx;
	}
	.dist {
		color: #C85B74;
		font-size: 22rpx;
		margin-left: 8rpx;
	}
	.price-num {
		color: #C2456A;
		font-weight: 700;
		font-size: 30rpx;
	}
	.price-unit {
		color: #9AA3AF;
		font-size: 22rpx;
		margin-left: 2rpx;
	}
</style>
