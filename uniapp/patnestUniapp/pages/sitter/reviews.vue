<template>
	<view class="rev-page patnest-page">
		<!-- 评分概览（服务端四维均分聚合） -->
		<view v-if="list.length" class="summary patnest-card">
			<view class="sum-left">
				<text class="s-num">{{ avgOverall }}</text>
				<score-stars :score="avgOverall" />
				<text class="s-count">{{ total }} 条评价</text>
			</view>
			<view class="sum-right">
				<view v-for="d in dims" :key="d.key" class="dim-row">
					<text class="d-label">{{ d.label }}</text>
					<score-stars :score="dimAvg(d.key)" />
				</view>
			</view>
		</view>

		<view class="list">
			<view v-for="r in list" :key="r.id" class="rev-item patnest-card">
				<view class="r-head">
					<score-stars :score="r.overall_score" />
					<text class="r-time">{{ fmtTime(r.created_at) }}</text>
				</view>
				<view class="r-dims">
					<text v-for="d in dims" :key="d.key" class="r-dim">
						{{ d.label }} {{ r[d.key] }}
					</text>
				</view>
				<text class="r-comment">{{ r.owner_comment }}</text>
				<view v-if="r.owner_photos && r.owner_photos.length" class="r-photos">
					<image v-for="(p, i) in r.owner_photos" :key="i" class="r-photo" :src="p" mode="aspectFill" />
				</view>
				<text v-if="r.reply_text" class="r-reply">店家回复：{{ r.reply_text }}</text>
				<text v-if="r.owner_name" class="r-owner">{{ r.owner_name }}</text>
			</view>

			<empty
				v-if="!loading && list.length === 0"
				text="还没有评价"
				image="/static/icons/star.png"
				desc="成为第一个分享寄养体验的人吧"
			/>
			<load-more :loading="loading" :has-more="hasMore" :empty="!loading && list.length === 0" empty-text="" />
		</view>
	</view>
</template>

<script>
	import { sitterApi } from '../../utils/api.js'
	import { fmtDateTime } from '../../utils/format.js'

	export default {
		data() {
			return {
				id: null,
				list: [],
				total: 0,
				page: 1,
				perPage: 20,
				hasMore: false,
				loading: false,
				// 服务端返回的四维均分聚合
				avg: { overall: 0, service: 0, environment: 0, hygiene: 0 },
				dims: [
					{ key: 'service_score', label: '服务' },
					{ key: 'environment_score', label: '环境' },
					{ key: 'hygiene_score', label: '卫生' }
				]
			}
		},
		computed: {
			avgOverall() {
				const v = Number(this.avg.overall) || 0
				return v.toFixed(1)
			}
		},
		onLoad(options) {
			this.id = options.id
			this.reload()
		},
		onReachBottom() {
			if (this.hasMore && !this.loading) {
				this.page += 1
				this.fetch()
			}
		},
		methods: {
			async reload() {
				this.page = 1
				this.hasMore = false
				this.list = []
				await this.fetch()
			},
			async fetch() {
				this.loading = true
				try {
					// GET /sitters/:id/reviews：分页 + 四维均分聚合
					const res = await sitterApi.reviews(this.id, {
						page: this.page,
						per_page: this.perPage
					})
					const items = (res && res.items) || []
					this.list = this.page === 1 ? items : this.list.concat(items)
					this.total = (res && res.total) || 0
					this.avg = {
						overall: (res && res.avg_overall) || 0,
						service: (res && res.avg_service) || 0,
						environment: (res && res.avg_environment) || 0,
						hygiene: (res && res.avg_hygiene) || 0
					}
					this.hasMore = this.list.length < this.total
				} catch (e) {
					// 错误提示已在 request 层处理
				} finally {
					this.loading = false
				}
			},
			dimAvg(key) {
				const map = {
					service_score: 'service',
					environment_score: 'environment',
					hygiene_score: 'hygiene'
				}
				return Number(this.avg[map[key]]) || 0
			},
			fmtTime(s) {
				return fmtDateTime(s)
			}
		}
	}
</script>

<style scoped>
	.rev-page {
		padding: 24rpx;
	}
	.summary {
		display: flex;
		padding: 32rpx 28rpx;
		margin-bottom: 24rpx;
	}
	.sum-left {
		display: flex;
		flex-direction: column;
		align-items: center;
		justify-content: center;
		padding-right: 32rpx;
		border-right: 1rpx solid #EFF1F3;
	}
	.s-num {
		font-size: 56rpx;
		font-weight: 700;
		color: #AF4A62;
		line-height: 1.2;
	}
	.s-count {
		font-size: 22rpx;
		color: #9AA3AF;
		margin-top: 8rpx;
	}
	.sum-right {
		flex: 1;
		padding-left: 32rpx;
		justify-content: center;
	}
	.dim-row {
		display: flex;
		align-items: center;
		margin-bottom: 12rpx;
	}
	.d-label {
		font-size: 24rpx;
		color: #5C6470;
		width: 70rpx;
	}
	.rev-item {
		padding: 28rpx;
		margin-bottom: 20rpx;
	}
	.r-head {
		display: flex;
		justify-content: space-between;
		align-items: center;
	}
	.r-time {
		font-size: 22rpx;
		color: #B7BDC5;
	}
	.r-dims {
		display: flex;
		flex-wrap: wrap;
		margin-top: 12rpx;
	}
	.r-dim {
		font-size: 22rpx;
		color: #9AA3AF;
		margin-right: 20rpx;
	}
	.r-comment {
		display: block;
		font-size: 28rpx;
		color: #1B1E23;
		margin-top: 12rpx;
		line-height: 1.7;
	}
	.r-reply {
		display: block;
		font-size: 24rpx;
		color: #5C6470;
		margin-top: 14rpx;
		padding: 16rpx;
		background: #F6F7F8;
		border-radius: 12rpx;
	}
	.r-photos {
		display: flex;
		flex-wrap: wrap;
		margin-top: 12rpx;
	}
	.r-photo {
		width: 150rpx;
		height: 150rpx;
		border-radius: 12rpx;
		margin: 0 12rpx 12rpx 0;
		background: #EFF1F3;
	}
	.r-owner {
		display: block;
		font-size: 22rpx;
		color: #B7BDC5;
		margin-top: 12rpx;
	}
</style>
