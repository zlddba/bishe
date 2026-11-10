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
					_at: Date.now()
				}
				writeCache(loc)
				resolve(loc)
				// 城市名异步补：拿到坐标先渲染，再补城市（不影响主流程）
				resolveCity(loc).then((city) => {
					if (city) {
						loc.city = city
						writeCache(loc)
					}
				})
			},
			fail: () => {
				resolve(null)
			}
		})
	})
}

/**
 * 逆地理编码：坐标 → 城市名（如「呼和浩特市」）
 * 用后端 POST /geo/reverse（高德 Web 服务，坐标系 GCJ-02 一致）
 *
 * ⚠️ 直辖市（北京/上海/天津/重庆）高德返回的 city 字段为空，
 *    后端 resolved_city 也会是空串。此时回退到从详细地址里截取城市名，
 *    否则首页导航栏会一直显示「已定位」而不是城市名。
 * @returns {Promise<string>} 失败返回空串
 */
export function resolveCity(loc) {
	if (!loc || loc.city) return Promise.resolve(loc && loc.city ? loc.city : '')
	// 动态 import 避免与 api.js 的循环依赖
	return import('./api.js')
		.then((m) => m.geoApi.reverse(loc.latitude, loc.longitude))
		.then((d) => {
			if (!d) return ''
			const city = (d.resolved_city || d.city || '').trim()
			if (city) return city
			// 直辖市兜底：从详细地址截「北京市」这类城市名
			return cityFromAddress(d.formatted_address || '') || (d.district || '')
		})
		.catch(() => '')
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
 * 城市名展示文案：
 *   有城市 → 「呼和浩特市」→ 去掉后缀显示「呼和浩特」
 *   没定位到 → 「定位」
 */
export function cityText(loc) {
	if (!loc) return '定位'
	const c = (loc.city || '').trim()
	if (!c) return '已定位'
	// 去掉「市」「特别行政区」等后缀：「呼和浩特市」→「呼和浩特」
	let short = c.replace(/(特别行政区|自治区|地区|盟|市)$/, '')
	if (!short) short = c
	// 太长时再截一次（最多 4 字）
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
