<template>
	<view class="empty">
		<image v-if="image" class="icon-img" :src="image" mode="aspectFit" />
		<text v-else-if="icon" class="icon">{{ icon }}</text>
		<text class="text">{{ text }}</text>
		<text v-if="desc" class="desc">{{ desc }}</text>
		<slot name="action" />
	</view>
</template>

<script>
	export default {
		name: 'Empty',
		props: {
			text: { type: String, default: '暂无数据' },
			desc: { type: String, default: '' },
			// 文字图标（兜底，优先用 image）
			icon: { type: String, default: '' },
			// 图片图标路径，传入 static/icons 目录下的图标文件名即可
			image: { type: String, default: '' }
		}
	}
</script>

<style scoped>
	.empty {
		display: flex;
		flex-direction: column;
		align-items: center;
		justify-content: center;
		padding: 120rpx 0;
	}
	.icon-img {
		width: 96rpx;
		height: 96rpx;
		opacity: 0.5;
	}
	.icon {
		font-size: 80rpx;
		line-height: 1;
		opacity: 0.55;
	}
	.text {
		margin-top: 20rpx;
		color: #5C6470;
		font-size: 26rpx;
	}
	.desc {
		margin-top: 10rpx;
		color: #9AA3AF;
		font-size: 22rpx;
		text-align: center;
		padding: 0 60rpx;
	}
</style>
