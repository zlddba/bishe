<template>
	<text class="status-tag" :style="style">{{ text }}</text>
</template>

<script>
	import { ORDER_STATUS_TEXT, ORDER_STATUS_THEME } from '../../config.js'

	export default {
		name: 'OrderStatus',
		props: {
			status: { type: String, default: '' }
		},
		computed: {
			text() {
				return ORDER_STATUS_TEXT[this.status] || this.status || '未知'
			},
			style() {
				const c = ORDER_STATUS_THEME[this.status] || { fg: '#5C6470', bg: '#EFF1F3' }
				return 'color:' + c.fg + ';background:' + c.bg
			}
		}
	}
</script>

<style scoped>
	.status-tag {
		font-size: 22rpx;
		padding: 4rpx 16rpx;
		border-radius: 20rpx;
		font-weight: 500;
	}
</style>
