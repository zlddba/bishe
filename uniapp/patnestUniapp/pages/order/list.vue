<template>
	<view class="order-list-page patnest-page">
		<view class="tabs">
			<view
				v-for="t in tabs"
				:key="t.value"
				:class="['tab', tab === t.value ? 'on' : '']"
				@click="switchTab(t.value)"
			>
				{{ t.label }}
			</view>
		</view>

		<view class="list">
			<view v-for="o in shown" :key="o.id" class="order-card patnest-card" @click="open(o)">
				<view class="o-top">
					<text class="o-no">{{ o.order_no || ('订单 #' + o.id) }}</text>
					<view class="o-tags">
						<text v-if="o._local" class="o-local">本地记录</text>
						<order-status :status="o.status" />
					</view>
				</view>
				<view class="o-mid">
					<text class="o-sitter">{{ o.sitter_name || '寄养机构' }}</text>
					<text class="o-date">{{ o.start_date }} ~ {{ o.end_date }}（{{ o.total_days || 0 }} 天）</text>
				</view>
				<view class="o-bottom">
					<text class="o-pet">宠物 ×{{ o.pet_count || 1 }}</text>
					<text class="o-amount">¥{{ fmtMoney(o.total_amount) }}</text>
				</view>
			</view>

			<!-- 服务端返回 total>0 但 items 为空时说明订单行扫描失败，给出明确诊断而不是误报「没有订单」 -->
			<view v-if="scanFailed" class="error-box">
				<text class="e-title">订单数据加载异常</text>
				<text class="e-desc">服务端返回了 {{ total }} 条订单记录但明细为空，通常是后端订单查询的日期字段类型不匹配所致，请联系管理员更新服务端版本。</text>
			</view>

			<empty
				v-else-if="!loading && shown.length === 0"
				:text="logged ? '还没有订单' : '登录后查看订单'"
				:desc="logged ? '去「找寄养」挑选一家心仪的机构吧' : '登录后可以看到你下的全部订单'"
			/>
			<load-more :loading="loading" :has-more="hasMore" :empty="!loading && shown.length === 0" empty-text="" />
		</view>
	</view>
</template>

<script>
	import { orderApi } from '../../utils/api.js'
	import { fmtMoney } from '../../utils/format.js'
	import { listOrders, saveOrder } from '../../utils/orderCache.js'
	import { isLoggedIn } from '../../utils/auth.js'

	export default {
		data() {
			return {
				// 后端 status_filter 已支持逗号分隔多状态，这里走服务端筛选；
				// 客户端再按状态过滤一次作为兜底（服务端修好前 items 可能为空）。
				tabs: [
					{ value: 'all', label: '全部', statuses: [], filter: '' },
					{ value: 'pending_pay', label: '待支付', statuses: ['pending_pay'], filter: 'pending_pay' },
					{ value: 'ongoing', label: '进行中', statuses: ['confirmed', 'accepted', 'staying'], filter: 'confirmed,accepted,staying' },
					{ value: 'done', label: '已完成', statuses: ['completed', 'evaluated'], filter: 'completed,evaluated' },
					{ value: 'closed', label: '已取消', statuses: ['cancelled', 'rejected'], filter: 'cancelled,rejected' }
				],
				tab: 'all',
				logged: false,
				list: [],
				total: 0,
				page: 1,
				hasMore: true,
				loading: false
			}
		},
		computed: {
			shown() {
				const cur = this.tabs.find((t) => t.value === this.tab)
				if (!cur || !cur.statuses.length) return this.list
				return this.list.filter((o) => cur.statuses.indexOf(o.status) >= 0)
			},
			// 服务端 total 与返回条数对不上 → 订单行扫描失败（历史日期字段类型不匹配）
			// 若本地有下单快照则不报这个错（列表里已经能看到本地记录了）
			scanFailed() {
				return !this.loading && this.total > 0 && this.list.length === 0
			}
		},
		onShow() {
			// 未登录不要打订单接口（必然 401），直接展示登录引导
			this.logged = isLoggedIn()
			if (!this.logged) {
				this.list = []
				this.total = 0
				this.loading = false
				return
			}
			this.reload()
		},
		onPullDownRefresh() {
			this.reload().then(() => uni.stopPullDownRefresh())
		},
		onReachBottom() {
			if (this.hasMore && !this.loading) {
				this.page += 1
				this.fetch()
			}
		},
		methods: {
			switchTab(t) {
				if (this.tab === t) return
				this.tab = t
				// 切换筛选条件必须从头拉：否则会把「新筛选条件的第 2 页」拼到
				// 「旧筛选条件的第 1 页」后面，出现漏单和重复。
				if (!this.logged) return
				this.reload()
			},
			async reload() {
				this.page = 1
				this.hasMore = true
				this.list = []
				await this.fetch()
			},
			async fetch() {
				this.loading = true
				try {
					const params = { page: this.page, per_page: 15 }
					const cur = this.tabs.find((t) => t.value === this.tab)
					if (cur && cur.filter) params.status_filter = cur.filter
					const res = await orderApi.list(params)
					const items = (res && res.items) || []
					this.total = (res && res.total) || 0
					this.list = this.page === 1 ? this.mergeLocal(items) : items
					this.hasMore = this.list.length < this.total
					// 把服务端返回的列表项存成快照：
					// 详情接口偶发不可用时，从列表点进详情页至少不会白屏。
					this.list.forEach((o) => {
						if (o && o.id && !o._local) saveOrder(o)
					})
				} catch (e) {
					// 错误提示已在 request 层处理
				} finally {
					this.loading = false
				}
			},
			// 服务端订单列表接口返回空 items 时，把本地下过的单补进来，
			// 至少让用户能看到自己刚下的订单（标注「本地记录」）。
			// 服务端修好后，这条路径不会再被触发。
			mergeLocal(items, allowLocal = true) {
				if (items.length) return items
				if (!allowLocal) return []
				const cur = this.tabs.find((t) => t.value === this.tab)
				const statuses = (cur && cur.statuses) || []
				return listOrders()
					.map((o) => ({
						id: o.id,
						order_no: o.order_no,
						status: o.status || 'pending_pay',
						payment_status: o.payment_status || 'none',
						start_date: o.start_date,
						end_date: o.end_date,
						total_days: o.total_days,
						pet_count: o.pet_count || 1,
						total_amount: o.total_amount,
						sitter_name: o.sitter_name,
						_local: true
					}))
					.filter((o) => !statuses.length || statuses.indexOf(o.status) >= 0)
			},
			open(o) {
				uni.navigateTo({ url: '/pages/order/detail?id=' + o.id })
			},
			fmtMoney
		}
	}
</script>

<style scoped>
	.order-list-page {
		padding: 24rpx;
	}
	.tabs {
		display: flex;
		background: #fff;
		border-radius: 16rpx;
		padding: 8rpx;
		margin-bottom: 24rpx;
	}
	.tab {
		flex: 1;
		text-align: center;
		padding: 18rpx 0;
		font-size: 26rpx;
		color: #5C6470;
		border-radius: 12rpx;
	}
	.tab.on {
		background: #FDF1F4;
		color: #AF4A62;
		font-weight: 600;
	}
	.order-card {
		padding: 28rpx;
		margin-bottom: 20rpx;
	}
	.o-top {
		display: flex;
		justify-content: space-between;
		align-items: center;
	}
	.o-tags {
		display: flex;
		align-items: center;
	}
	.o-local {
		font-size: 20rpx;
		color: #D97706;
		background: #FDF3E3;
		padding: 2rpx 12rpx;
		border-radius: 8rpx;
		margin-right: 12rpx;
	}
	.o-no {
		font-size: 26rpx;
		color: #9AA3AF;
	}
	.o-mid {
		margin-top: 16rpx;
		display: flex;
		flex-direction: column;
	}
	.o-sitter {
		font-size: 30rpx;
		font-weight: 600;
		color: #1B1E23;
	}
	.o-date {
		font-size: 24rpx;
		color: #9AA3AF;
		margin-top: 6rpx;
	}
	.o-bottom {
		display: flex;
		justify-content: space-between;
		align-items: center;
		margin-top: 16rpx;
		padding-top: 16rpx;
		border-top: 1rpx solid #EFF1F3;
	}
	.o-pet {
		font-size: 26rpx;
		color: #5C6470;
	}
	.o-amount {
		font-size: 32rpx;
		font-weight: 700;
		color: #C2456A;
	}
	.error-box {
		background: #FAE7EC;
		border-radius: 16rpx;
		padding: 28rpx;
		margin-top: 8rpx;
	}
	.e-title {
		display: block;
		font-size: 28rpx;
		font-weight: 600;
		color: #C2456A;
	}
	.e-desc {
		display: block;
		font-size: 24rpx;
		color: #8E3B4E;
		margin-top: 12rpx;
		line-height: 1.6;
	}
</style>
