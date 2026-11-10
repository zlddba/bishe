<template>
	<view class="loc-page patnest-page">
		<!-- 当前定位 -->
		<view class="cur patnest-card">
			<view class="cur-row">
				<view class="cur-icon-wrap">
					<image class="cur-icon" src="/static/icons/location.png" mode="aspectFit" />
				</view>
				<view class="cur-main">
					<text class="cur-title">{{ curTitle }}</text>
					<text class="cur-sub">{{ curSub }}</text>
				</view>
				<view class="cur-btn" :class="{ busy: locating }" @click="relocate">
					{{ locating ? '定位中' : '重新定位' }}
				</view>
			</view>
		</view>

		<!-- 省 / 市 / 区 -->
		<view class="sec">
			<view class="sec-head">
				<text class="sec-title">选择地区</text>
				<text class="sec-link" @click="followLocation">用当前定位</text>
			</view>
			<view class="rows patnest-card">
				<picker
					mode="selector"
					:range="provinceNames"
					:value="pIdx < 0 ? 0 : pIdx"
					:disabled="!provinces.length"
					@change="onProvinceChange"
				>
					<view class="row">
						<text class="row-label">省</text>
						<text class="row-value" :class="{ muted: pIdx < 0 }">
							{{ pIdx >= 0 ? provinces[pIdx].name : '请选择省份' }}
						</text>
						<text class="row-arrow">›</text>
					</view>
				</picker>
				<picker
					mode="selector"
					:range="cityNames"
					:value="cIdx < 0 ? 0 : cIdx"
					:disabled="!cities.length"
					@change="onCityChange"
				>
					<view class="row">
						<text class="row-label">市</text>
						<text class="row-value" :class="{ muted: cIdx < 0 }">
							{{ cIdx >= 0 ? cities[cIdx].name : (pIdx >= 0 ? '请选择城市' : '请先选择省份') }}
						</text>
						<text class="row-arrow">›</text>
					</view>
				</picker>
				<picker
					mode="selector"
					:range="districtNames"
					:value="dIdx < 0 ? 0 : dIdx"
					:disabled="!districts.length"
					@change="onDistrictChange"
				>
					<view class="row last">
						<text class="row-label">区</text>
						<text class="row-value" :class="{ muted: dIdx <= 0 }">
							{{ dIdx > 0 ? districts[dIdx].name : (cIdx >= 0 ? '全部区县' : '请先选择城市') }}
						</text>
						<text class="row-arrow">›</text>
					</view>
				</picker>
			</view>
			<text class="rows-hint">选好地区后点下方按钮，返回首页只看该地区的机构；不选则按距离推荐最近的。</text>
		</view>

		<view class="foot-space" />

		<!-- 底部操作 -->
		<view class="foot">
			<view class="foot-btn ghost" @click="useNearby">按距离推荐</view>
			<view class="foot-btn primary" @click="confirm">查看{{ selectedName || '全部' }}的机构</view>
		</view>
	</view>
</template>

<script>
	import { geoApi } from '../../utils/api.js'
	import {
		getCurrentLocation,
		resolveArea,
		getSelectedRegion,
		setSelectedRegion,
		clearSelectedRegion
	} from '../../utils/location.js'

	// 区县列表的第一项：不限定区县（等价于「整个市」）
	const ALL_DISTRICTS = { name: '全部区县', adcode: '', center_lng: 0, center_lat: 0 }

	export default {
		data() {
			return {
				loc: null,
				// 逆地理结果：{ province, city, district, township, formatted, adcode }
				area: { province: '', city: '', district: '', township: '', formatted: '', adcode: '' },
				locating: false,

				// 省 / 市 / 区（高德行政区划）
				provinces: [],
				cities: [],
				districts: [],
				pIdx: -1,
				cIdx: -1,
				dIdx: -1
			}
		},
		computed: {
			curTitle() {
				const a = this.area
				if (a.district) return a.city ? a.city + ' ' + a.district : a.district
				if (a.city) return a.city
				return this.loc ? '已获取坐标' : '尚未开启定位'
			},
			curSub() {
				if (this.area.formatted) return this.area.formatted
				if (this.loc) return '正在解析地名…'
				return '开启定位后可按距离推荐附近机构'
			},
			provinceNames() {
				return this.provinces.map((p) => p.name)
			},
			cityNames() {
				return this.cities.map((c) => c.name)
			},
			districtNames() {
				return this.districts.map((d) => d.name)
			},
			// 当前选中的地区名（区 > 市 > 省）；没选返回空串
			selectedName() {
				if (this.dIdx > 0) return this.districts[this.dIdx].name
				if (this.cIdx >= 0) return this.cities[this.cIdx].name
				if (this.pIdx >= 0) return this.provinces[this.pIdx].name
				return ''
			},
			// 选中层级最深的那一项，用于取中心点
			selectedItem() {
				if (this.dIdx > 0) return this.districts[this.dIdx]
				if (this.cIdx >= 0) return this.cities[this.cIdx]
				if (this.pIdx >= 0) return this.provinces[this.pIdx]
				return null
			}
		},
		onLoad() {
			this.init()
		},
		methods: {
			async init() {
				await this.loadProvinces()
				await this.silentLocate()
			},
			// 全国 → 省（后端缓存一天）
			async loadProvinces() {
				try {
					const res = await geoApi.districts({ city: '中国' })
					this.provinces = (res && res.items) || []
				} catch (e) {
					this.provinces = []
				}
			},
			async loadCities(province) {
				this.cities = []
				this.cIdx = -1
				this.districts = []
				this.dIdx = -1
				if (!province) return
				try {
					const res = await geoApi.districts({ adcode: province.adcode })
					this.cities = (res && res.items) || []
				} catch (e) {
					this.cities = []
				}
			},
			async loadDistricts(city) {
				this.districts = []
				this.dIdx = -1
				if (!city) return
				try {
					const params = city.adcode ? { adcode: city.adcode } : { city: city.name }
					const res = await geoApi.districts(params)
					this.districts = [ALL_DISTRICTS].concat((res && res.items) || [])
					this.dIdx = 0 // 默认「全部区县」
				} catch (e) {
					this.districts = []
				}
			},
			findIndex(list, name) {
				if (!name) return -1
				const exact = list.findIndex((x) => x.name === name)
				if (exact >= 0) return exact
				return list.findIndex((x) => name.indexOf(x.name) >= 0 || x.name.indexOf(name) >= 0)
			},
			// 定位结果 → 省/市/区 选中项
			async applyAreaToPickers(area) {
				if (!this.provinces.length) await this.loadProvinces()

				const pi = this.findIndex(this.provinces, area.province)
				this.pIdx = pi >= 0 ? pi : this.findIndex(this.provinces, area.city)
				if (this.pIdx < 0) return

				await this.loadCities(this.provinces[this.pIdx])
				let ci = this.findIndex(this.cities, area.city)
				// 直辖市：区划里是「北京城区」这种伪市级，城市名对不上就取唯一的那个
				if (ci < 0 && this.cities.length === 1) ci = 0
				if (ci < 0) return
				this.cIdx = ci

				await this.loadDistricts(this.cities[ci])
				const di = this.findIndex(this.districts, area.district)
				if (di > 0) this.dIdx = di
			},
			// 上次选过的地区（用户可能选的是别的城市，优先于定位）
			async applySavedRegion(saved) {
				if (!this.provinces.length) await this.loadProvinces()
				const pi = this.findIndex(this.provinces, saved.province)
				if (pi < 0) return false
				this.pIdx = pi
				await this.loadCities(this.provinces[pi])
				const ci = this.findIndex(this.cities, saved.city)
				if (ci < 0) return false
				this.cIdx = ci
				await this.loadDistricts(this.cities[ci])
				const di = this.findIndex(this.districts, saved.district)
				if (di > 0) this.dIdx = di
				return true
			},
			async silentLocate() {
				const loc = await getCurrentLocation()
				if (loc) {
					this.loc = loc
					this.area = await resolveArea(loc)
				}
				const saved = getSelectedRegion()
				if (saved && (await this.applySavedRegion(saved))) return
				if (this.area.city) await this.applyAreaToPickers(this.area)
			},
			async relocate() {
				if (this.locating) return
				this.locating = true
				uni.showLoading({ title: '定位中...' })
				try {
					const loc = await getCurrentLocation(true)
					if (!loc) {
						uni.hideLoading()
						uni.showModal({
							title: '无法获取位置',
							content: '请在微信「设置 → 授权管理」中允许获取位置信息。也可以直接选择省/市/区浏览机构。',
							showCancel: false,
							confirmColor: '#C85B74'
						})
						return
					}
					this.loc = loc
					this.area = await resolveArea(loc)
					await this.applyAreaToPickers(this.area)
					uni.hideLoading()
					uni.showToast({ title: '定位成功', icon: 'none' })
				} finally {
					this.locating = false
				}
			},
			// 「用当前定位」：把选择项恢复成定位所在地
			async followLocation() {
				if (!this.area.city && !this.area.district) {
					uni.showToast({ title: '还没有定位，先点「重新定位」', icon: 'none' })
					return
				}
				await this.applyAreaToPickers(this.area)
			},
			async onProvinceChange(e) {
				this.pIdx = Number(e.detail.value)
				await this.loadCities(this.provinces[this.pIdx])
			},
			async onCityChange(e) {
				this.cIdx = Number(e.detail.value)
				await this.loadDistricts(this.cities[this.cIdx])
			},
			onDistrictChange(e) {
				this.dIdx = Number(e.detail.value)
			},
			// 确认：写入选中的地区，返回首页
			confirm() {
				const item = this.selectedItem
				if (!item) {
					clearSelectedRegion()
					uni.navigateBack()
					return
				}
				const province = this.pIdx >= 0 ? this.provinces[this.pIdx] : null
				const city = this.cIdx >= 0 ? this.cities[this.cIdx] : null
				const district = this.dIdx > 0 ? this.districts[this.dIdx] : null
				setSelectedRegion({
					province: province ? province.name : '',
					city: city ? city.name : '',
					district: district ? district.name : '',
					name: this.selectedName,
					// 距离基准：区中心 → 市中心 → 省中心
					lat: item.center_lat || 0,
					lng: item.center_lng || 0
				})
				uni.navigateBack()
			},
			// 不限定地区：回首页按距离推荐
			useNearby() {
				clearSelectedRegion()
				uni.navigateBack()
			}
		}
	}
</script>

<style scoped>
	.loc-page {
		background: #F6F7F8;
		padding: 24rpx 24rpx 0;
	}

	/* 当前定位 */
	.cur {
		padding: 28rpx 24rpx;
		margin-bottom: 24rpx;
	}
	.cur-row {
		display: flex;
		align-items: center;
	}
	.cur-icon-wrap {
		width: 72rpx;
		height: 72rpx;
		border-radius: 20rpx;
		background: #FDF1F4;
		display: flex;
		align-items: center;
		justify-content: center;
		flex-shrink: 0;
	}
	.cur-icon {
		width: 36rpx;
		height: 36rpx;
	}
	.cur-main {
		flex: 1;
		min-width: 0;
		margin-left: 18rpx;
	}
	.cur-title {
		display: block;
		font-size: 32rpx;
		font-weight: 700;
		color: #1B1E23;
	}
	.cur-sub {
		display: block;
		font-size: 22rpx;
		color: #8A9099;
		margin-top: 8rpx;
		overflow: hidden;
		text-overflow: ellipsis;
		white-space: nowrap;
	}
	.cur-btn {
		flex-shrink: 0;
		margin-left: 16rpx;
		padding: 12rpx 22rpx;
		border-radius: 999rpx;
		background: #C85B74;
		color: #fff;
		font-size: 24rpx;
		font-weight: 600;
	}
	.cur-btn.busy {
		background: #E7AFBC;
	}

	/* 分区 */
	.sec {
		margin-bottom: 28rpx;
	}
	.sec-head {
		display: flex;
		align-items: baseline;
		justify-content: space-between;
		margin-bottom: 16rpx;
		padding: 0 6rpx;
	}
	.sec-title {
		font-size: 28rpx;
		font-weight: 700;
		color: #1B1E23;
	}
	.sec-link {
		font-size: 24rpx;
		color: #C85B74;
	}

	/* 省市区三行 */
	.rows {
		overflow: hidden;
	}
	.row {
		display: flex;
		align-items: center;
		padding: 28rpx 24rpx;
		border-bottom: 1rpx solid #F2F3F5;
	}
	.row.last {
		border-bottom: none;
	}
	.row-label {
		width: 60rpx;
		font-size: 26rpx;
		color: #8A9099;
		flex-shrink: 0;
	}
	.row-value {
		flex: 1;
		font-size: 28rpx;
		color: #1B1E23;
		overflow: hidden;
		text-overflow: ellipsis;
		white-space: nowrap;
	}
	.row-value.muted {
		color: #A9AFB7;
	}
	.row-arrow {
		font-size: 32rpx;
		color: #C9CED6;
		flex-shrink: 0;
		margin-left: 10rpx;
	}
	.rows-hint {
		display: block;
		font-size: 21rpx;
		color: #9AA0A8;
		padding: 12rpx 6rpx 0;
		line-height: 1.5;
	}

	/* 底部操作条 */
	.foot-space {
		height: 160rpx;
	}
	.foot {
		position: fixed;
		left: 0;
		right: 0;
		bottom: 0;
		display: flex;
		padding: 20rpx 24rpx calc(20rpx + env(safe-area-inset-bottom));
		background: #fff;
		box-shadow: 0 -4rpx 16rpx rgba(27, 30, 35, 0.05);
	}
	.foot-btn {
		flex: 1;
		height: 84rpx;
		line-height: 84rpx;
		text-align: center;
		border-radius: 999rpx;
		font-size: 28rpx;
		font-weight: 600;
	}
	.foot-btn.ghost {
		background: #F3F4F6;
		color: #5C6470;
		margin-right: 18rpx;
	}
	.foot-btn.primary {
		background: #C85B74;
		color: #fff;
	}
</style>
