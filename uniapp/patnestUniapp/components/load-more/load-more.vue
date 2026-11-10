<template>
	<view v-if="!(empty && !emptyText)" class="load-more">
		<text class="text">{{ text }}</text>
	</view>
</template>

<script>
	export default {
		name: 'LoadMore',
		props: {
			loading: { type: Boolean, default: false },
			hasMore: { type: Boolean, default: true },
			empty: { type: Boolean, default: false },
			emptyText: { type: String, default: '暂无数据' }
		},
		computed: {
			text() {
				if (this.empty) return this.emptyText
				if (this.loading) return '加载中...'
				return this.hasMore ? '上拉加载更多' : '没有更多了'
			}
		}
	}
</script>

<style scoped>
	.load-more {
		display: flex;
		align-items: center;
		justify-content: center;
		padding: 28rpx 0;
	}
	.text {
		font-size: 24rpx;
		color: #9AA3AF;
	}
</style>
