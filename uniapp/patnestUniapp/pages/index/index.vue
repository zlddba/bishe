<template>
	<view class="index-page patnest-page">
		<!-- 自定义导航栏（丰巢式）：左上角城市定位，中间标题，右边留胶囊按钮位 -->
		<view class="navbar">
			<view class="nb-status" :style="{ height: statusBarHeight + 'px' }" />
			<view class="nb-bar">
				<view class="nb-loc" @click="requestLocation">
					<image class="nb-loc-icon" src="/static/icons/location.png" mode="aspectFit" />
					<text class="nb-loc-text">{{ cityLabel }}</text>
					<text class="nb-loc-arrow">▾</text>
				</view>
				<text class="nb-title">宠栖 · 找寄养</text>
				<view class="nb-capsule" />
			</view>
		</view>

		<!-- 搜索栏 -->
		<view class="search-bar">
			<view class="search-input">
				<input
					class="input"
					v-model="keyword"
					placeholder="搜索寄养机构 / 地址 / 简介"
					confirm-type="search"
					@confirm="onSearch"
				/>
				<text v-if="keyword" class="clear" @click="clearKeyword">✕</text>
			</view>
			<view class="sort-btn" @click="showSort = !showSort">
				<text>{{ sortText }}</text>
				<text class="arrow">▾</text>
			</view>
		</view>

		<!-- 筛选行 -->
		<view class="filter-row">
			<scroll-view class="type-scroll" scroll-x>
				<view :class="['chip', !params.type ? 'on' : '']" @click="setType('')">全部</view>
				<view :class="['chip', params.type === 'home' ? 'on' : '']" @click="setType('home')">家庭寄养</view>
				<view :class="['chip', params.type === 'shop' ? 'on' : '']" @click="setType('shop')">门店寄养</view>
				<view :class="['chip', params.type === 'visit' ? 'on' : '']" @click="setType('visit')">上门喂养</view>
			</scroll-view>
		</view>

		<!-- 排序下拉 -->
		<view v-if="showSort" class="sort-panel">
			<view
				v-for="opt in sortOptions"
				:key="opt.value"
				:class="['sort-item', params.sort === opt.value ? 'on' : '']"
				@click="setSort(opt.value)"
			>
				{{ opt.label }}
			</view>
		</view>

		<!-- 价格筛选 -->
		<view class="price-filter">
			<input class="price-input" type="digit" v-model="priceMin" placeholder="最低价" placeholder-class="ph" @confirm="reload" />
			<text class="price-sep">—</text>
			<input class="price-input" type="digit" v-model="priceMax" placeholder="最高价" placeholder-class="ph" @confirm="reload" />
			<view class="apply-btn" @click="reload">筛选</view>
		</view>

		<!-- 访客提示（机构浏览公开，登录后才能下单） -->
		<view v-if="!logged" class="guest-bar" @click="goLogin">
			<text class="g-text">浏览无需登录，登录后即可预约下单</text>
			<text class="g-btn">去登录 ›</text>
		</view>

		<!-- 列表 -->
		<view class="list">
			<sitter-card
				v-for="s in list"
				:key="s.id"
				:sitter="s"
				:distance-text="distText(s)"
			/>
			<empty v-if="!loading && list.length === 0" text="没有找到匹配的机构" desc="换个关键词或放宽筛选条件试试" />
			<load-more :loading="loading" :has-more="hasMore" :empty="!loading && list.length === 0" empty-text="" />
		</view>
	</view>
</template>

<script>
	import { sitterApi } from '../../utils/api.js'
	import { isLoggedIn } from '../../utils/auth.js'
	import { SORT_OPTIONS } from '../../config.js'
	import { getCurrentLocation, distanceTo, formatDistance, cityText, resolveCity } from '../../utils/location.js'
	import { saveSnapshots } from '../../utils/sitterCache.js'

	export default {
		data() {
			return {
				keyword: '',
				params: {
					type: '',
					sort: '',
					price_min: '',
					price_max: '',
					page: 1,
					per_page: 10
				},
				priceMin: '',
				priceMax: '',
				sortOptions: SORT_OPTIONS,
				list: [],
				total: 0,
				loading: false,
				hasMore: true,
				showSort: false,
				logged: false,
				inited: false,
				loc: null,
				searchMode: false,
				// 自定义导航栏的状态栏高度（px）
				statusBarHeight: 20
			}
		},
		computed: {
			sortText() {
				const hit = SORT_OPTIONS.find((o) => o.value === this.params.sort)
				return hit ? hit.label : '综合推荐'
			},
			// 定位按钮上的城市名：已定位显示城市，未定位提示「定位」
			cityLabel() {
				return cityText(this.loc)
			}
		},
		onLoad() {
			// 自定义导航栏需要自己留出状态栏高度
			try {
				const sys = uni.getSystemInfoSync()
				this.statusBarHeight = sys.statusBarHeight || 20
			} catch (e) {
				this.statusBarHeight = 20
			}
		},
		onShow() {
			this.logged = isLoggedIn()
			if (!this.inited) {
				this.inited = true
				this.reload()
				// 静默尝试定位（用户之前授权过才有结果，不弹窗打扰）
				this.applyLocation(getCurrentLocation())
			}
		},
		onPullDownRefresh() {
			this.reload().then(() => uni.stopPullDownRefresh())
		},
		onReachBottom() {
			this.loadMore()
		},
		methods: {
			/**
			 * 应用定位结果并补城市名。
			 * ⚠️ 城市名必须用**新对象**整体替换 this.loc：
			 *    直接改 loc.city 改的是原始对象，Vue3 的响应式代理感知不到，
			 *    页面上的城市名不会更新（踩过这个坑）。
			 */
			async applyLocation(promise) {
				const loc = await promise
				if (!loc) return
				this.loc = loc
				// 逆地理编码取城市名（失败不阻塞）
				const city = await resolveCity(loc)
				if (city) {
					this.loc = Object.assign({}, this.loc, { city })
				}
			},
			async reload() {
				this.params.page = 1
				this.hasMore = true
				this.list = []
				await this.fetch()
			},
			async fetch() {
				if (this.loading) return
				this.loading = true
				try {
					const p = Object.assign({}, this.params)
					if (!p.type) delete p.type
					// distance 由前端本地排序，后端不认这个 sort 值
					if (!p.sort || p.sort === 'distance') delete p.sort
					const min = parseFloat(this.priceMin)
					const max = parseFloat(this.priceMax)
					if (!isNaN(min) && min > 0) p.price_min = min
					else delete p.price_min
					if (!isNaN(max) && max > 0) p.price_max = max
					else delete p.price_max

					let res
					if (this.searchMode && this.keyword) {
						res = await sitterApi.search(this.keyword, { page: p.page, per_page: p.per_page })
					} else {
						res = await sitterApi.list(p)
					}
				const items = (res && res.items) || []
				const merged = p.page === 1 ? items : this.list.concat(items)
				this.list = this.sortByDistance(merged)
				this.total = (res && res.total) || 0
				this.hasMore = this.list.length < this.total
				// 存一份本地快照：详情页接口失败时用它兜底渲染，避免整页空白
				saveSnapshots(items)
				} catch (e) {
					// 错误提示已在 request 层处理
				} finally {
					this.loading = false
				}
			},
			// 「离我最近」：后端暂未实现距离排序，这里按 Haversine 本地排
			// 没有坐标的机构统一排在最后
			sortByDistance(list) {
				if (this.params.sort !== 'distance' || !this.loc) return list
				return list.slice().sort((a, b) => {
					const da = distanceTo(a, this.loc)
					const db = distanceTo(b, this.loc)
					if (da == null && db == null) return 0
					if (da == null) return 1
					if (db == null) return -1
					return da - db
				})
			},
			// 机构距离文案
			distText(sitter) {
				return formatDistance(distanceTo(sitter, this.loc))
			},
			// 主动请求定位（用户点了「定位」按钮）
			async requestLocation() {
				uni.showLoading({ title: '定位中...' })
				const loc = await getCurrentLocation(true)
				uni.hideLoading()
				if (!loc) {
					return uni.showModal({
						title: '无法获取位置',
						content: '请在微信「设置 → 授权管理」中允许获取位置信息。未授权也不影响使用，可继续按价格与评分筛选。',
						showCancel: false,
						confirmColor: '#C85B74'
					})
				}
				this.loc = loc
				// 补城市名（逆地理编码），并用新对象触发响应式更新
				const city = await resolveCity(loc)
				if (city) {
					this.loc = Object.assign({}, this.loc, { city })
				}
				uni.showToast({ title: '定位成功', icon: 'none' })
				if (this.params.sort === 'distance') {
					this.reload()
				}
			},
			loadMore() {
				if (!this.hasMore || this.loading) return
				this.params.page += 1
				this.fetch()
			},
			setType(t) {
				this.params.type = t
				this.searchMode = false
				this.reload()
			},
			setSort(s) {
				this.params.sort = s
				this.showSort = false
				// 选「离我最近」但还没定位时，先要一次定位授权
				if (s === 'distance' && !this.loc) {
					this.requestLocation()
					return
				}
				this.reload()
			},
			onSearch() {
				this.searchMode = true
				this.reload()
			},
			clearKeyword() {
				this.keyword = ''
				this.searchMode = false
				this.reload()
			},
			goLogin() {
				uni.navigateTo({ url: '/pages/login/login' })
			}
		}
	}
</script>

<style scoped>
	.index-page {
		padding: 0 24rpx 40rpx;
		/* 自定义导航栏时，页面背景要顶到屏幕最上边，否则状态栏区域会露白 */
		background: #F6F7F8;
	}
	.search-bar {
		display: flex;
		align-items: center;
		padding: 20rpx 0;
	}
	.search-input {
		flex: 1;
		min-width: 0;
		display: flex;
		align-items: center;
		background: #fff;
		border-radius: 40rpx;
		padding: 0 24rpx;
		height: 76rpx;
		box-shadow: 0 2rpx 10rpx rgba(27, 30, 35, 0.04);
	}
	.search-input .input {
		flex: 1;
		font-size: 28rpx;
	}
	.search-input .clear {
		color: #9AA3AF;
		padding: 0 8rpx;
	}
	.filter-row {
		display: flex;
		align-items: center;
		padding: 4rpx 0 12rpx;
	}
	.type-scroll {
		flex: 1;
		white-space: nowrap;
	}
	.chip {
		display: inline-block;
		padding: 10rpx 24rpx;
		margin-right: 16rpx;
		background: #fff;
		border-radius: 28rpx;
		font-size: 24rpx;
		color: #5C6470;
	}
	.chip.on {
		background: #FDF1F4;
		color: #AF4A62;
		font-weight: 600;
	}
	.sort-btn {
		flex-shrink: 0;
		display: flex;
		align-items: center;
		padding: 10rpx 20rpx;
		margin-left: 12rpx;
		background: #fff;
		border-radius: 28rpx;
		font-size: 24rpx;
		color: #5C6470;
	}
	/* 顶部一行：城市定位在左，排序在右 */
	/* 自定义导航栏（丰巢式）：左上角城市定位 + 中间标题 */
	.navbar {
		background: linear-gradient(135deg, #D4687F 0%, #C85B74 55%, #B94E68 100%);
		padding: 0 24rpx;
		margin: 0 -24rpx 8rpx;
	}
	.nb-status {
		width: 100%;
	}
	.nb-bar {
		display: flex;
		align-items: center;
		height: 88rpx;
	}
	.nb-loc {
		display: flex;
		align-items: center;
		max-width: 260rpx;
		padding: 6rpx 14rpx 6rpx 10rpx;
		background: rgba(255, 255, 255, 0.18);
		border-radius: 999rpx;
	}
	.nb-loc-icon {
		width: 28rpx;
		height: 28rpx;
		margin-right: 6rpx;
		flex-shrink: 0;
	}
	.nb-loc-text {
		font-size: 25rpx;
		color: #fff;
		font-weight: 500;
		white-space: nowrap;
		overflow: hidden;
		text-overflow: ellipsis;
	}
	.nb-loc-arrow {
		font-size: 18rpx;
		color: rgba(255, 255, 255, 0.85);
		margin-left: 4rpx;
		flex-shrink: 0;
	}
	.nb-title {
		flex: 1;
		text-align: center;
		font-size: 32rpx;
		font-weight: 700;
		color: #fff;
		white-space: nowrap;
		overflow: hidden;
		text-overflow: ellipsis;
	}
	/* 右侧留出微信胶囊按钮的位置 */
	.nb-capsule {
		width: 150rpx;
		flex-shrink: 0;
	}
	.sort-btn .arrow {
		margin-left: 6rpx;
		font-size: 20rpx;
	}
	.sort-panel {
		background: #fff;
		border-radius: 16rpx;
		padding: 8rpx 0;
		margin-bottom: 16rpx;
		box-shadow: 0 4rpx 16rpx rgba(27, 30, 35, 0.06);
	}
	.sort-item {
		padding: 20rpx 28rpx;
		font-size: 26rpx;
		color: #1B1E23;
	}
	.sort-item.on {
		color: #AF4A62;
		font-weight: 600;
	}
	.price-filter {
		display: flex;
		align-items: center;
		margin-bottom: 16rpx;
	}
	.price-input {
		flex: 1;
		background: #fff;
		border-radius: 12rpx;
		height: 64rpx;
		padding: 0 20rpx;
		font-size: 24rpx;
		box-sizing: border-box;
	}
	.price-input .ph {
		color: #B7BDC5;
	}
	.price-sep {
		margin: 0 12rpx;
		color: #B7BDC5;
	}
	.apply-btn {
		margin-left: 12rpx;
		padding: 0 24rpx;
		height: 64rpx;
		line-height: 64rpx;
		background: #FDF1F4;
		color: #AF4A62;
		border-radius: 12rpx;
		font-size: 24rpx;
	}
	.guest-bar {
		display: flex;
		align-items: center;
		justify-content: space-between;
		background: #FDF1F4;
		border-radius: 16rpx;
		padding: 20rpx 24rpx;
		margin-bottom: 24rpx;
	}
	.g-text {
		font-size: 24rpx;
		color: #AF4A62;
	}
	.g-btn {
		font-size: 24rpx;
		color: #C85B74;
		font-weight: 600;
	}
	.list {
		padding-top: 8rpx;
	}
</style>
