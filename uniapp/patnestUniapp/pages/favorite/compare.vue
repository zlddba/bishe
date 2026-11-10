<template>
	<view class="cmp-page patnest-page">
		<view class="tip-bar">
			<text class="tb-text">横向对比最多 4 家机构，价格、评分、容量一目了然</text>
		</view>

		<scroll-view class="table-scroll" scroll-x>
			<view class="table">
				<!-- 表头：机构名 -->
				<view class="row head-row">
					<view class="cell label-cell">
						<text class="th-label">对比项</text>
					</view>
					<view v-for="s in items" :key="s.sitter_id" class="cell">
						<text class="th-name">{{ s.name }}</text>
						<text class="th-type">{{ typeText(s.type) }}</text>
					</view>
				</view>

				<!-- 对比行 -->
				<view v-for="row in rows" :key="row.key" class="row">
					<view class="cell label-cell">
						<text class="row-label">{{ row.label }}</text>
					</view>
					<view v-for="s in items" :key="s.sitter_id" class="cell">
						<text :class="['row-value', row.key === 'daily_price' ? 'price' : '']">
							{{ row.render(s) }}
						</text>
					</view>
				</view>

				<!-- 操作行 -->
				<view class="row">
					<view class="cell label-cell" />
					<view v-for="s in items" :key="s.sitter_id" class="cell">
						<view class="go-btn" @click="goBook(s)">去预约</view>
					</view>
				</view>
			</view>
		</scroll-view>

		<empty
			v-if="!loading && items.length === 0"
			text="暂无可对比的机构"
			image="/static/icons/shield.png"
			desc="先去收藏几家机构，再回来对比"
		/>
		<load-more :loading="loading" :has-more="false" :empty="!loading && items.length === 0" empty-text="" />
	</view>
</template>

<script>
	import { favoriteApi } from '../../utils/api.js'
	import { SITTER_TYPE_TEXT, PRICE_MODE } from '../../config.js'
	import { fmtMoney } from '../../utils/format.js'

	export default {
		data() {
			return {
				items: [],
				loading: false,
				rows: [
					{ key: 'daily_price', label: '日单价', render: (s) => (s.price_mode === PRICE_MODE.PER_VISIT ? '按次计费' : '¥' + fmtMoney(s.daily_price)) },
					{ key: 'visit_price', label: '次单价', render: (s) => '¥' + fmtMoney(s.visit_price) },
					{ key: 'avg_score', label: '综合评分', render: (s) => (Number(s.avg_score) || 0).toFixed(1) + ' 分' },
					{ key: 'review_count', label: '评价数', render: (s) => (s.review_count || 0) + ' 条' },
					{ key: 'capacity', label: '可接容量', render: (s) => (s.capacity != null ? s.capacity + ' 只' : '—') },
					{ key: 'type', label: '机构类型', render: (s) => this.typeText(s.type) },
					{ key: 'price_mode', label: '计价方式', render: (s) => (s.price_mode === PRICE_MODE.PER_VISIT ? '按次' : '按天') },
					{ key: 'address', label: '地址', render: (s) => s.address || '未填写' }
				]
			}
		},
		onLoad() {
			this.load()
		},
		methods: {
			async load() {
				this.loading = true
				try {
					const fav = await favoriteApi.list()
					const ids = ((fav && fav.items) || []).map((f) => f.sitter_id).slice(0, 4)
					if (!ids.length) {
						this.items = []
						return
					}
					const res = await favoriteApi.compare(ids)
					this.items = (res && res.items) || []
				} catch (e) {
					// 错误提示已在 request 层处理
				} finally {
					this.loading = false
				}
			},
			typeText(t) {
				return SITTER_TYPE_TEXT[t] || '寄养'
			},
			goBook(s) {
				uni.navigateTo({ url: '/pages/order/create?sitter_id=' + s.sitter_id })
			}
		}
	}
</script>

<style scoped>
	.cmp-page {
		padding: 24rpx;
	}
	.tip-bar {
		background: #FDF1F4;
		border-radius: 16rpx;
		padding: 20rpx 24rpx;
		margin-bottom: 24rpx;
	}
	.tb-text {
		font-size: 24rpx;
		color: #AF4A62;
	}
	.table-scroll {
		width: 100%;
	}
	.table {
		background: #fff;
		border-radius: 20rpx;
		padding: 8rpx 0;
		box-shadow: 0 4rpx 16rpx rgba(27, 30, 35, 0.04);
		display: inline-block;
		min-width: 100%;
	}
	.row {
		display: flex;
		align-items: stretch;
		border-bottom: 1rpx solid #EFF1F3;
	}
	.row:last-child {
		border-bottom: none;
	}
	.cell {
		width: 200rpx;
		min-width: 200rpx;
		padding: 22rpx 16rpx;
		box-sizing: border-box;
		display: flex;
		flex-direction: column;
		justify-content: center;
		align-items: center;
	}
	.label-cell {
		width: 140rpx;
		min-width: 140rpx;
		align-items: flex-start;
		background: #F6F7F8;
	}
	.head-row .cell {
		background: #FDF1F4;
	}
	.th-label {
		font-size: 24rpx;
		color: #AF4A62;
		font-weight: 600;
	}
	.th-name {
		font-size: 26rpx;
		font-weight: 600;
		color: #1B1E23;
		text-align: center;
	}
	.th-type {
		font-size: 20rpx;
		color: #9AA3AF;
		margin-top: 6rpx;
	}
	.row-label {
		font-size: 24rpx;
		color: #5C6470;
	}
	.row-value {
		font-size: 25rpx;
		color: #1B1E23;
		text-align: center;
	}
	.row-value.price {
		color: #C2456A;
		font-weight: 700;
	}
	.go-btn {
		padding: 10rpx 28rpx;
		background: #C85B74;
		color: #fff;
		border-radius: 28rpx;
		font-size: 23rpx;
	}
</style>
