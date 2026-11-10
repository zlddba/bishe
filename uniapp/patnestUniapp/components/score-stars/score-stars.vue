<template>
	<view :class="['stars', interactive ? 'interactive' : '']">
		<text
			v-for="i in 5"
			:key="i"
			class="star"
			:class="i <= score ? 'on' : ''"
			@click.stop="onPick(i)"
		>★</text>
		<text v-if="showText" class="score-text">{{ scoreText }}</text>
	</view>
</template>

<script>
	export default {
		name: 'ScoreStars',
		props: {
			// 当前分值
			score: { type: Number, default: 0 },
			// 是否可点击打分
			interactive: { type: Boolean, default: false },
			// 是否在星级后显示分值文案
			showText: { type: Boolean, default: false },
			// 文案，如「服务」「环境」
			label: { type: String, default: '' },
			// 评分描述文案，如「非常满意」
			desc: { type: String, default: '' }
		},
		computed: {
			scoreText() {
				const s = this.score || 0
				const suffix = this.desc ? ' ' + this.desc : ''
				return (this.label ? this.label + ' ' : '') + s.toFixed(1) + suffix
			}
		},
		methods: {
			onPick(i) {
				if (!this.interactive) return
				this.$emit('change', i)
			}
		}
	}
</script>

<style scoped>
	.stars {
		display: flex;
		align-items: center;
	}
	.star {
		font-size: 34rpx;
		color: #D4D8DD;
		margin-right: 6rpx;
	}
	.star.on {
		color: #EAB308;
	}
	.interactive .star {
		font-size: 44rpx;
		margin-right: 14rpx;
	}
	.score-text {
		font-size: 24rpx;
		color: #5C6470;
		margin-left: 12rpx;
	}
</style>
