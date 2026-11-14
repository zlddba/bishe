<template>
	<view class="index-page patnest-page">
		<!-- 自定义导航栏（丰巢式）：左上角城市定位，中间标题，右边留胶囊按钮位 -->
		<view class="navbar">
			<view class="nb-status" :style="{ height: statusBarHeight + 'px' }" />
			<view class="nb-bar">
				<view class="nb-loc" @click="openLocationPicker">
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
			<input class="price-input" type="digit" v-model="priceMin" placeholder="最低价" placeholder-class="ph" @confirm="applyPriceFilter" />
			<text class="price-sep">—</text>
			<input class="price-input" type="digit" v-model="priceMax" placeholder="最高价" placeholder-class="ph" @confirm="applyPriceFilter" />
			<view class="apply-btn" @click="applyPriceFilter">筛选</view>
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
				:distance-prefix="region ? '距该地区' : '距你'"
			/>
			<empty
				v-if="!loading && list.length === 0"
				:text="region ? '该地区暂无机构' : '没有找到匹配的机构'"
				:desc="region ? '点左上角换个地区，或选择「按距离推荐」' : '换个关键词或放宽筛选条件试试'"
			/>
			<load-more :loading="loading" :has-more="hasMore" :empty="!loading && list.length === 0" empty-text="" />
		</view>
	</view>
</template>

<script>
	import { sitterApi } from '../../utils/api.js'
	import { isLoggedIn } from '../../utils/auth.js'
	import { SORT_OPTIONS } from '../../config.js'
	import { getCurrentLocation, distanceTo, formatDistance, cityText, resolveArea, getSelectedRegion } from '../../utils/location.js'
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
				// 位置页选中的地区（null = 不限定，按距离推荐）
				region: null,
				regionKey: '',
				// 自定义导航栏的状态栏高度（px）
				statusBarHeight: 20
			}
		},
		computed: {
			sortText() {
				const hit = SORT_OPTIONS.find((o) => o.value === this.params.sort)
				return hit ? hit.label : '综合推荐'
			},
			// 定位按钮上的地名：选了地区显示地区名，否则显示定位到的城市/区县
			cityLabel() {
				if (this.region && this.region.name) return this.region.name
				return cityText(this.loc)
			},
			// 距离基准：选了地区用该地区中心点，否则用当前坐标
			// （用户可能选的是别的城市，用 GPS 距离会全是几千公里）
			refPoint() {
				const r = this.region
				if (r && r.lat && r.lng) return { latitude: r.lat, longitude: r.lng }
				return this.loc
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
			// 位置页可能刚改过地区：先同步，首次进入也要带上
			const regionChanged = this.syncRegion()
			if (!this.inited) {
				this.inited = true
				this.reload()
				// 静默尝试定位（用户之前授权过才有结果，不弹窗打扰）
				this.applyLocation(getCurrentLocation())
				return
			}
			if (regionChanged) {
				this.reload()
				return
			}
			// 定位可能变了，静默刷一次（命中缓存不发请求）；
			// 当前按距离排序时重拉列表，否则顺序还是旧的
			this.applyLocation(getCurrentLocation()).then(() => {
				if (this.params.sort === 'distance' && this.refPoint) this.reload()
			})
		},
		onPullDownRefresh() {
			this.reload().then(() => uni.stopPullDownRefresh())
		},
		onReachBottom() {
			this.loadMore()
		},
		methods: {
			/**
			 * 应用定位结果并补行政区划（区县/街道）。
			 * ⚠️ 必须用**新对象**整体替换 this.loc：
			 *    直接改 loc.city 改的是原始对象，Vue3 的响应式代理感知不到，
			 *    页面上的地名不会更新（踩过这个坑）。
			 */
			async applyLocation(promise) {
				const loc = await promise
				if (!loc) return
				this.loc = loc
				// 逆地理编码取区县/街道（失败不阻塞主流程）
				const area = await resolveArea(loc)
				if (area && (area.city || area.district || area.township)) {
					this.loc = Object.assign({}, this.loc, area)
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

					// 地区筛选：位置页选到区县就按区县，只选到市就按市
					if (this.region && this.region.district) {
						p.district = this.region.district
						delete p.city
					} else if (this.region && this.region.city) {
						p.city = this.region.city
						delete p.district
					} else {
						delete p.city
						delete p.district
					}

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
				const base = this.refPoint
				if (this.params.sort !== 'distance' || !base) return list
				return list.slice().sort((a, b) => {
					const da = distanceTo(a, base)
					const db = distanceTo(b, base)
					if (da == null && db == null) return 0
					if (da == null) return 1
					if (db == null) return -1
					return da - db
				})
			},
			// 机构距离文案（基准点同上：地区中心或当前坐标）
			distText(sitter) {
				const base = this.refPoint
				if (!base) return ''
				return formatDistance(distanceTo(sitter, base))
			},
			// 同步「选择位置」页选中的地区；返回 true 表示变了、需要重拉列表
			syncRegion() {
				const region = getSelectedRegion()
				const key = region ? [region.province, region.city, region.district].join('|') : ''
				if (key === this.regionKey) return false
				this.regionKey = key
				this.region = region
				return true
			},
			// 点左上角定位：进入「选择位置」页（自动定位 / 选省市区）
			openLocationPicker() {
				uni.navigateTo({ url: '/pages/location/index' })
			},
			// 主动请求定位（按距离排序时若还没有坐标，先要一次授权）
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
				// 补行政区划（逆地理编码），并用新对象触发响应式更新
				const area = await resolveArea(loc)
				if (area && (area.city || area.district || area.township)) {
					this.loc = Object.assign({}, this.loc, area)
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
				// 退出搜索态时同时清掉关键词，避免「输入框还显示关键词、结果是全量」的误导
				this.searchMode = false
				this.keyword = ''
				this.reload()
			},
			// 价格筛选 / 排序都走这个入口：/sitters/search 只接受 q，带不了任何筛选参数，
			// 停留在搜索态会让价格、排序、地区全部静默失效。
			applyPriceFilter() {
				this.exitSearch()
				this.reload()
			},
			exitSearch() {
				if (!this.searchMode) return
				this.searchMode = false
				this.keyword = ''
			},
			setSort(s) {
				this.params.sort = s
				this.showSort = false
				this.exitSearch()
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
		/* 左右两侧等宽，标题才是真正居中；240rpx ≈ 120px，足够让开微信胶囊（约 87px） */
		width: 240rpx;
		max-width: 240rpx;
		padding: 6rpx 14rpx 6rpx 10rpx;
		background: rgba(255, 255, 255, 0.18);
		border-radius: 999rpx;
		flex-shrink: 0;
		box-sizing: border-box;
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
	/* 右侧留出微信胶囊按钮的位置：与左侧城市块等宽，标题不会随城市名长度漂移 */
	.nb-capsule {
		width: 240rpx;
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
