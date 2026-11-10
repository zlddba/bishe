// #=============================================================================
// 宠栖 PatNest · 定位与距离工具
//
// 坐标系说明（全链路统一 GCJ-02）：
//   - 微信 wx.getLocation 用 type: 'gcj02' 返回的就是 GCJ-02
//   - 后端 sitters.latitude/longitude 存的也是高德 GCJ-02
//   两者可直接混用计算距离。
//
// 降级策略（对应《10-定位与导航服务配置指南》§8 合规清单第 4 条）：
//   用户拒绝定位 / 定位失败时，**不阻塞主流程**，
//   找机构仍可按价格、评分排序，导航入口改为「搜索地址」。
// ============================================================================

// 静态导入：微信小程序不支持动态 import()。
// 编译到 mp-weixin 时 `import('./api.js')` 会被降级成字符串，调用 .then 直接报
// 「"./api.js".then is not a function」。这里没有循环依赖
// （location.js → api.js → request.js → config.js），静态导入是安全的。
import { geoApi } from './api.js'

const LOC_KEY = 'patnest_location'
const LOC_TTL = 10 * 60 * 1000 // 10 分钟内复用，避免反复请求定位


/**
 * 获取当前定位
 * @returns {Promise<{latitude:number, longitude:number, city:string}|null>} 失败返回 null（不抛异常）
 */
export function getCurrentLocation(force = false) {
	return new Promise((resolve) => {
		// 命中缓存
		if (!force) {
			const cached = readCache()
			if (cached) return resolve(cached)
		}
		if (typeof uni.getLocation !== 'function') {
			return resolve(null)
		}
		uni.getLocation({
			type: 'gcj02',
			geocode: false,
			success: (res) => {
				const loc = {
					latitude: Number(res.latitude),
					longitude: Number(res.longitude),
					city: '',
					district: '',
					township: '',
					_at: Date.now()
				}
				writeCache(loc)
				resolve(loc)
				// 行政区划异步补：拿到坐标先渲染，再补区县/街道（不影响主流程）
				resolveArea(loc).then((area) => {
					if (!area || (!area.city && !area.district && !area.township)) return
					Object.assign(loc, area)
					writeCache(loc)
				})
			},
			fail: () => {
				resolve(null)
			}
		})
	})
}

/**
 * 逆地理编码：坐标 → 行政区划（区县 + 街道/乡镇）
 * 用后端 POST /geo/reverse（高德 Web 服务，坐标系 GCJ-02 一致）
 *
 * 返回三段，调用方按需要的精度取：
 *   { city: '呼和浩特市', district: '赛罕区', township: '人民路街道' }
 *
 * ⚠️ 直辖市（北京/上海/天津/重庆）高德返回的 city 字段为空，
 *    此时回退到从详细地址里截取城市名；
 *    township 在部分开发区/新区也为空，用 district 兜底展示。
 * @returns {Promise<{city:string,district:string,township:string}>} 失败返回三个空串
 */
export function resolveArea(loc) {
	const empty = { province: '', city: '', district: '', township: '', formatted: '', adcode: '' }
	if (!loc) return Promise.resolve(empty)
	// 已经补过就不再请求（缓存命中时 loc 里带着上次的结果）
	if (loc.city && (loc.district || loc.township)) {
		return Promise.resolve({
			province: loc.province || '',
			city: loc.city || '',
			district: loc.district || '',
			township: loc.township || '',
			formatted: loc.formatted || '',
			adcode: loc.adcode || ''
		})
	}
	// 走自己的后端做逆地理（高德 Key 只在服务端，不下发小程序）
	return geoApi
		.reverse(loc.latitude, loc.longitude)
		.then((d) => {
			if (!d) return empty
			const city = (d.resolved_city || d.city || '').trim() || cityFromAddress(d.formatted_address || '')
			return {
				province: (d.province || '').trim(),
				city,
				district: (d.district || '').trim(),
				township: (d.township || '').trim(),
				formatted: (d.formatted_address || '').trim(),
				// adcode 是**区县**级编码，别拿它查下级（只会得到街道）
				adcode: (d.adcode || '').trim()
			}
		})
		.catch(() => empty)
}

/**
 * 只取城市名（保留给只需要城市的场景）
 * @returns {Promise<string>} 失败返回空串
 */
export function resolveCity(loc) {
	return resolveArea(loc).then((a) => a.city || '')
}

/**
 * 从详细地址里解析城市名
 * 「北京市海淀区青龙桥街道…」→「北京市」
 * 「广东省广州市越秀区…」→「广州市」
 * 「内蒙古自治区呼和浩特市赛罕区…」→「呼和浩特市」
 *
 * ⚠️ 两个坑：
 *   1) 不能用贪婪正则 /([\u4e00-\u9fa5]{2,10}市)/，「广东省广州市」会整体匹配上；
 *      做法是先切出「市」之前的部分，再剥掉省/自治区前缀。
 *   2) 不能取**最后一个**「市」—— 地址后半段的机构名里常带「市」字
 *      （如「北京市东城区东华门街道北京市人民政府」），会解析成「道北京」。
 *      所以取**第一个**「市」，它才是行政区划里的那个。
 */
export function cityFromAddress(addr) {
	if (!addr) return ''
	const s = String(addr)

	// 1) 找到第一个「市」
	const cityIdx = s.indexOf('市')
	if (cityIdx > 0) {
		let seg = s.slice(0, cityIdx + 1) // 含「市」
		// 2) 剥掉前面的省 / 自治区 / 直辖市前缀，只留城市名
		seg = seg.replace(/^.*?(?:省|自治区|特别行政区)/, '')
		// 3) 兜底：剥完还太长（说明地址里有机构名等噪声），取末尾 4 字
		if (seg.length > 5) seg = seg.slice(-4)
		if (seg.length >= 2) return seg
	}

	// 4) 没有「市」：省级单位（省/自治区/特别行政区）
	const p = s.match(/([\u4e00-\u9fa5]{2,8}(?:省|自治区|特别行政区))/)
	if (p && p[1]) return p[1].replace(/(自治区|特别行政区|省)$/, '')
	return ''
}

/**
 * 从机构地址里解析区县：「内蒙古自治区呼和浩特市赛罕区人民路街道…」→「赛罕区」
 *
 * 用途：位置页的「选择区县」筛选。机构表没有独立的区县字段，只能从 address 里取；
 * 取不到就返回空串，调用方把它归到「其他」，不要影响列表展示。
 *
 * 做法是先定位到城市名，再截城市之后的第一个「区/县/旗」——
 * 不能直接在整串里找，否则省名、机构名里的字都可能被误判。
 */
export function districtFromAddress(addr) {
	if (!addr) return ''
	const s = String(addr)
	const city = cityFromAddress(s)
	const rest = city && s.indexOf(city) >= 0 ? s.slice(s.indexOf(city) + city.length) : s

	// 区 / 县 / 自治县 / 旗（含「自治旗」「自治县」这类长后缀）
	const m = rest.match(/^([\u4e00-\u9fa5]{2,10}?(?:自治县|自治旗|区|县|旗))/)
	if (m && m[1]) return m[1]
	// 县级市：如「自治区直辖县级行政区划」之后的「阿尔山市」
	const m2 = rest.match(/^([\u4e00-\u9fa5]{2,8}?市)/)
	return m2 && m2[1] ? m2[1] : ''
}

/**
 * 导航栏展示文案（精确到区县/镇）：
 *   有区县   → 「赛罕区」
 *   只有街道 → 「人民路街道」（部分开发区高德不给 district）
 *   只有城市 → 「呼和浩特」（去掉「市」后缀）
 *   没定位到 → 「定位」
 */
export function cityText(loc) {
	if (!loc) return '定位'
	// 优先区县：导航栏就一个胶囊的位置，区县足够定位到「哪个区」，街道太长放不下
	const area = (loc.district || '').trim() || (loc.township || '').trim()
	if (area) return area.length > 7 ? area.slice(0, 7) : area
	const c = (loc.city || '').trim()
	if (!c) return '已定位'
	let short = c.replace(/(特别行政区|自治区|地区|盟|市)$/, '')
	if (!short) short = c
	return short.length > 4 ? short.slice(0, 4) : short
}

function readCache() {
	try {
		const raw = uni.getStorageSync(LOC_KEY)
		if (!raw) return null
		const loc = typeof raw === 'string' ? JSON.parse(raw) : raw
		if (!loc || typeof loc.latitude !== 'number') return null
		if (Date.now() - (loc._at || 0) > LOC_TTL) return null
		return loc
	} catch (e) {
		return null
	}
}

function writeCache(loc) {
	try {
		uni.setStorageSync(LOC_KEY, JSON.stringify(loc))
	} catch (e) {}
}

// ---------------------------------------------------------------------------
// 用户在「选择位置」页选中的地区
//
// 导航栏和首页列表共用：有值就只看这个地区，null 表示不限定（按距离推荐）。
// 结构：{ province, city, district, name, lat, lng }
//   name 用于导航栏展示（区县 > 城市 > 省）
//   lat/lng 是该级行政区中心点，作为距离基准
// ---------------------------------------------------------------------------
const REGION_KEY = 'patnest_region'

export function getSelectedRegion() {
	try {
		const raw = uni.getStorageSync(REGION_KEY)
		if (!raw) return null
		const r = typeof raw === 'string' ? JSON.parse(raw) : raw
		return r && r.name ? r : null
	} catch (e) {
		return null
	}
}

export function setSelectedRegion(region) {
	try {
		if (!region || !region.name) {
			uni.removeStorageSync(REGION_KEY)
			return
		}
		uni.setStorageSync(REGION_KEY, JSON.stringify(region))
	} catch (e) {}
}

export function clearSelectedRegion() {
	try {
		uni.removeStorageSync(REGION_KEY)
	} catch (e) {}
}

/**
 * Haversine 计算两点距离（公里）
 * @param {number} lat1 起点纬度
 * @param {number} lng1 起点经度
 * @param {number} lat2 终点纬度
 * @param {number} lng2 终点经度
 */
export function distanceKm(lat1, lng1, lat2, lng2) {
	const toRad = (d) => (d * Math.PI) / 180
	const R = 6371
	const dLat = toRad(lat2 - lat1)
	const dLng = toRad(lng2 - lng1)
	const a =
		Math.sin(dLat / 2) * Math.sin(dLat / 2) +
		Math.cos(toRad(lat1)) * Math.cos(toRad(lat2)) * Math.sin(dLng / 2) * Math.sin(dLng / 2)
	return R * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
}

/**
 * 计算到某机构的距离（公里），机构无坐标时返回 null
 */
export function distanceTo(sitter, loc) {
	if (!sitter || !loc) return null
	const lat = Number(sitter.latitude)
	const lng = Number(sitter.longitude)
	if (isNaN(lat) || isNaN(lng) || lat === 0 || lng === 0) return null
	return distanceKm(loc.latitude, loc.longitude, lat, lng)
}

/**
 * 距离文案：< 1 公里显示米，否则显示公里
 */
export function formatDistance(km) {
	if (km == null || isNaN(km)) return ''
	if (km < 1) return Math.round(km * 1000) + 'm'
	if (km < 10) return km.toFixed(1) + 'km'
	return Math.round(km) + 'km'
}

/**
 * 唤起微信内置地图导航
 * @param {Object} sitter 机构对象（需含 latitude / longitude / name / address）
 * @returns {Promise<boolean>} 是否成功唤起
 */
export function openNavigation(sitter) {
	return new Promise((resolve) => {
		const lat = Number(sitter && sitter.latitude)
		const lng = Number(sitter && sitter.longitude)
		if (!sitter || isNaN(lat) || isNaN(lng) || (lat === 0 && lng === 0)) {
			// 没有坐标 → 降级：复制地址，让用户自己搜
			const addr = (sitter && (sitter.address || sitter.name)) || ''
			if (addr) {
				uni.setClipboardData({
					data: addr,
					success: () => {
						uni.showModal({
							title: '该机构暂无位置信息',
							content: '机构地址已复制，可粘贴到微信「搜一搜」或地图 App 中查找。',
							showCancel: false,
							confirmColor: '#C85B74'
						})
					}
				})
			} else {
				uni.showToast({ title: '该机构暂未提供位置信息', icon: 'none' })
			}
			return resolve(false)
		}
		uni.openLocation({
			latitude: lat,
			longitude: lng,
			name: sitter.name || '寄养机构',
			address: sitter.address || '',
			fail: () => {
				uni.showToast({ title: '无法打开地图，请检查定位权限', icon: 'none' })
			},
			success: () => resolve(true)
		})
	})
}
