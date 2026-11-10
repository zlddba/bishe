<template>
	<view :class="['pet-card', selectable ? 'pickable' : '', selected ? 'on' : '']" @click="onTap">
		<image class="avatar" :src="cover" mode="aspectFill" />
		<view class="info">
			<view class="line1">
				<text class="name">{{ pet.name }}</text>
				<text class="species">{{ speciesText }}</text>
				<text v-if="pet.vaccinated" class="vac">已接种</text>
			</view>
			<text class="sub">{{ subText }}</text>
			<text v-if="pet.notes" class="notes">{{ pet.notes }}</text>
		</view>
		<view v-if="selectable" class="check" :class="selected ? 'checked' : ''">
			<text v-if="selected">✓</text>
		</view>
	</view>
</template>

<script>
	import { SPECIES_TEXT, PET_SIZE_TEXT } from '../../config.js'

	export default {
		name: 'PetCard',
		// ⚠️ 必须显式声明 emits：否则 'tap' 会被 Vue 当成原生事件透传到根节点，
		//    父组件的 @tap 处理器会先收到原生事件对象（没有 id），导致跳转参数变成 undefined。
		//    这里统一用 'open' / 'select' 这类不会与原生事件冲突的名字。
		emits: ['open', 'select'],
		props: {
			pet: { type: Object, required: true },
			// 是否可选（下单页多选场景）
			selectable: { type: Boolean, default: false },
			selected: { type: Boolean, default: false }
		},
		computed: {
			cover() {
				const urls = this.pet.photo_urls
				if (urls && urls.length) return urls[0]
				return this.pet.avatar_url || '/static/avatar-default.jpg'
			},
			speciesText() {
				return SPECIES_TEXT[this.pet.species] || '其他'
			},
			subText() {
				const parts = []
				if (this.pet.breed) parts.push(this.pet.breed)
				if (this.pet.body_size) parts.push(PET_SIZE_TEXT[this.pet.body_size] || this.pet.body_size)
				if (this.pet.age_month != null && this.pet.age_month !== '') {
					const m = Number(this.pet.age_month)
					if (m < 12) parts.push(m + '个月')
					else parts.push(Math.floor(m / 12) + '岁')
				}
				if (this.pet.weight_kg) parts.push(this.pet.weight_kg + 'kg')
				return parts.join(' · ') || '资料待完善'
			}
		},
		methods: {
			onTap() {
				if (this.selectable) {
					this.$emit('select', this.pet)
				} else {
					this.$emit('open', this.pet)
				}
			}
		}
	}
</script>

<style scoped>
	.pet-card {
		display: flex;
		align-items: center;
		background: #FFFFFF;
		border-radius: 16rpx;
		border: 2rpx solid transparent;
		padding: 20rpx;
		margin-bottom: 20rpx;
	}
	.pet-card.pickable.on {
		border-color: #E0708A;
		background: #FDF1F4;
	}
	.avatar {
		width: 100rpx;
		height: 100rpx;
		border-radius: 16rpx;
		background: #EFF1F3;
		flex-shrink: 0;
	}
	.info {
		flex: 1;
		margin-left: 20rpx;
		min-width: 0;
	}
	.line1 {
		display: flex;
		align-items: center;
	}
	.name {
		font-size: 30rpx;
		font-weight: 600;
		color: #1B1E23;
	}
	.species {
		font-size: 20rpx;
		color: #AF4A62;
		background: #FDF1F4;
		padding: 2rpx 12rpx;
		border-radius: 8rpx;
		margin-left: 12rpx;
	}
	.vac {
		font-size: 20rpx;
		color: #2F9E63;
		background: #EAF6EF;
		padding: 2rpx 12rpx;
		border-radius: 8rpx;
		margin-left: 10rpx;
	}
	.sub {
		display: block;
		font-size: 24rpx;
		color: #5C6470;
		margin-top: 8rpx;
	}
	.notes {
		display: block;
		font-size: 22rpx;
		color: #9AA3AF;
		margin-top: 6rpx;
		overflow: hidden;
		text-overflow: ellipsis;
		white-space: nowrap;
	}
	.check {
		width: 40rpx;
		height: 40rpx;
		border-radius: 50%;
		border: 2rpx solid #D4D8DD;
		flex-shrink: 0;
		display: flex;
		align-items: center;
		justify-content: center;
		color: #fff;
		font-size: 24rpx;
	}
	.check.checked {
		background: #E0708A;
		border-color: #E0708A;
	}
</style>
