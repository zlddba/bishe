// #=============================================================================
// 宠栖 PatNest · 机构信息本地快照
//
// 为什么需要：
//   机构详情页只依赖 GET /sitters/:id，一旦这条请求失败（弱网抖动、后端短暂
//   5xx、路由未命中），页面就会变成一张「名称/地址/价格/评分全空」的空卡片，
//   用户完全看不出是哪个机构出了问题（真机上就是这样：截图里一整页空白）。
//
// 做法：
//   列表页（首页 / 收藏 / 对比）渲染时把整条机构对象按 id 存一份到本地；
//   详情页请求失败或返回字段缺失时，用这份快照兜底渲染，并提示「当前为缓存数据」。
//   快照只做展示兜底，不参与下单 —— 下单仍以服务端为准。
// ============================================================================

const KEY = 'patnest_sitter_snapshots'
// 最多保留 50 条，避免无限增长
const MAX = 50

function readAll() {
	try {
		const raw = uni.getStorageSync(KEY)
		if (!raw) return {}
		return typeof raw === 'string' ? JSON.parse(raw) : raw
	} catch (e) {
		return {}
	}
}

function writeAll(map) {
	try {
		uni.setStorageSync(KEY, JSON.stringify(map))
	} catch (e) {}
}

/**
 * 保存一条机构快照（列表页每次渲染时调用，覆盖更新）
 * @param {Object} sitter 机构对象，至少含 id 或 sitter_id
 */
export function saveSnapshot(sitter) {
	if (!sitter) return
	const id = Number(sitter.id || sitter.sitter_id)
	if (!id || isNaN(id)) return
	const map = readAll()
	map[id] = {
		id: id,
		sitter_id: sitter.sitter_id || id,
		name: sitter.name || '',
		type: sitter.type || '',
		description: sitter.description || '',
		daily_price: sitter.daily_price,
		visit_price: sitter.visit_price,
		price_mode: sitter.price_mode,
		capacity: sitter.capacity,
		address: sitter.address || '',
		latitude: sitter.latitude,
		longitude: sitter.longitude,
		avg_score: sitter.avg_score,
		review_count: sitter.review_count,
		cover_photo_url: sitter.cover_photo_url || '',
		detail_photos: sitter.detail_photos || [],
		contact_phone: sitter.contact_phone || '',
		contact_name: sitter.contact_name || '',
		contact_person: sitter.contact_person || '',
		_at: Date.now()
	}
	// 只保留最新的 MAX 条
	const keys = Object.keys(map)
	if (keys.length > MAX) {
		keys
			.map((k) => ({ k: k, t: map[k] && map[k]._at ? map[k]._at : 0 }))
			.sort((a, b) => a.t - b.t)
			.slice(0, keys.length - MAX)
			.forEach((o) => delete map[o.k])
	}
	writeAll(map)
}

/**
 * 批量保存（列表页一次拿到多条时用）
 */
export function saveSnapshots(list) {
	;(list || []).forEach((s) => saveSnapshot(s))
}

/**
 * 读取某机构的快照
 * @returns {Object|null}
 */
export function getSnapshot(id) {
	const n = Number(id)
	if (!n || isNaN(n)) return null
	const map = readAll()
	return map[n] || null
}

/**
 * 合并服务端数据与快照：服务端字段优先，缺失的用快照补
 * 用于详情页「服务端只返回部分字段」的情况
 */
export function mergeWithSnapshot(serverData, id) {
	const snap = getSnapshot(id)
	if (!snap) return serverData
	const out = Object.assign({}, snap)
	Object.keys(serverData || {}).forEach((k) => {
		const v = serverData[k]
		if (v !== undefined && v !== null && v !== '') out[k] = v
	})
	return out
}

/**
 * 清空（退出登录时用，避免把上一个账号看到的机构留在本地）
 */
export function clearSnapshots() {
	try {
		uni.removeStorageSync(KEY)
	} catch (e) {}
}
