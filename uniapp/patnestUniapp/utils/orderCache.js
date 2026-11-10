// #=============================================================================
// 宠栖 PatNest · 订单本地快照
//
// 背景：服务端 `GET /pet-owner/orders` 与 `GET /pet-owner/orders/:id` 目前有缺陷
//      （详见 docs/09-主人端缺失接口清单.md 的 B-1 / B-2），
//      列表返回 total>0 但 items 为空，详情直接 404。
//      但 `POST /pet-owner/orders` 的下单响应里**带完整订单摘要**
//      （order_id / order_no / sitter_name / total_amount / total_days / pet_count …），
//      所以前端在下单成功时把这份摘要存下来，作为详情页与列表页的兜底数据源。
//
// 作用：
//   1. 订单详情页在服务端详情接口不可用时，仍能显示订单号、机构、金额、天数、状态，
//      操作按钮（支付 / 取消 / 改期 / 续费 / 退款）也能正常出现并可用；
//   2. 订单列表在服务端返回空 items 时，能显示本地下过的单（标记「本地记录」）。
//
// 服务端修好后，这里返回的数据会被服务端数据整体覆盖，不影响正确性。
// ============================================================================

const KEY = 'patnest_local_orders'
const MAX_KEEP = 50

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
	} catch (e) {
		// 存储失败不阻断主流程
	}
}

// 保存/更新一条订单快照
export function saveOrder(order) {
	if (!order || !order.id) return
	const map = readAll()
	map[order.id] = Object.assign({}, map[order.id], order, {
		_local: true,
		_saved_at: Date.now()
	})
	// 只保留最近的若干条，避免存储无限增长
	const ids = Object.keys(map)
	if (ids.length > MAX_KEEP) {
		ids
			.sort((a, b) => (map[a]._saved_at || 0) - (map[b]._saved_at || 0))
			.slice(0, ids.length - MAX_KEEP)
			.forEach((id) => delete map[id])
	}
	writeAll(map)
}

// 读取单条快照
export function getOrder(id) {
	const map = readAll()
	return map[id] || null
}

// 读取全部快照（按保存时间倒序）
export function listOrders() {
	const map = readAll()
	return Object.keys(map)
		.map((k) => map[k])
		.sort((a, b) => (b._saved_at || 0) - (a._saved_at || 0))
}

// 只更新某条订单的某些字段（例如支付后改状态）
export function patchOrder(id, patch) {
	const map = readAll()
	if (!map[id]) return
	map[id] = Object.assign({}, map[id], patch, { _local: true })
	writeAll(map)
}

// 清空（退出登录时调用，避免串号）
export function clearOrders() {
	try {
		uni.removeStorageSync(KEY)
	} catch (e) {}
}
