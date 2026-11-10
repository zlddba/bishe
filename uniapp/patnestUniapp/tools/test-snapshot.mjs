#!/usr/bin/env node
// #=============================================================================
// 宠栖 PatNest · 机构快照兜底逻辑单测
//
// 背景：机构详情页只依赖 GET /sitters/:id。该接口一旦失败（弱网、后端短暂 5xx、
//       蓝绿发布切流瞬间），页面会变成「名称/地址/价格/评分全空」的空卡片。
//       utils/sitterCache.js 用列表页缓存的快照做兜底，本脚本验证其行为。
//
// 运行：node tools/test-snapshot.mjs
// ============================================================================

// 与 utils/sitterCache.js 保持一致（改一处要改两处）
const STORE = {}

function saveSnapshot(sitter) {
	if (!sitter) return
	const id = Number(sitter.id || sitter.sitter_id)
	if (!id || isNaN(id)) return
	STORE[id] = {
		id: id,
		name: sitter.name || '',
		type: sitter.type || '',
		description: sitter.description || '',
		daily_price: sitter.daily_price,
		capacity: sitter.capacity,
		address: sitter.address || '',
		avg_score: sitter.avg_score,
		review_count: sitter.review_count,
		cover_photo_url: sitter.cover_photo_url || '',
		detail_photos: sitter.detail_photos || [],
		contact_phone: sitter.contact_phone || ''
	}
}

function mergeWithSnapshot(serverData, id) {
	const snap = STORE[Number(id)]
	if (!snap) return serverData
	const out = Object.assign({}, snap)
	Object.keys(serverData || {}).forEach((k) => {
		const v = serverData[k]
		if (v !== undefined && v !== null && v !== '') out[k] = v
	})
	return out
}

let pass = 0
function check(name, actual, expected) {
	const ok = actual === expected
	if (ok) pass++
	console.log((ok ? '✅' : '❌'), name, '→', JSON.stringify(actual), ok ? '' : '(期望 ' + JSON.stringify(expected) + ')')
}

// 首页列表拿到「狗剩」
saveSnapshot({
	id: 7, name: '狗剩', type: 'home', description: '非常好使', daily_price: 50,
	capacity: 6, address: '中共中央党校', avg_score: 2, review_count: 1,
	cover_photo_url: null, detail_photos: [], contact_phone: '17547968227'
})

// 场景 1：详情接口完全失败 → 用快照兜底，字段不能空
const r1 = mergeWithSnapshot(null, 7)
check('接口失败·名称', r1.name, '狗剩')
check('接口失败·地址', r1.address, '中共中央党校')
check('接口失败·价格', r1.daily_price, 50)
check('接口失败·评分', r1.avg_score, 2)
check('接口失败·容量', r1.capacity, 6)

// 场景 2：详情接口只回部分字段 → 缺的用快照补
const r2 = mergeWithSnapshot({ id: 7, name: '狗剩', daily_price: 60 }, 7)
check('部分字段·价格取服务端', r2.daily_price, 60)
check('部分字段·地址用快照补', r2.address, '中共中央党校')
check('部分字段·评分用快照补', r2.avg_score, 2)

// 场景 3：收藏接口返回稀疏字段 → 补出简介与电话
const fav = {
	sitter_id: 7, name: '狗剩', type: 'home', daily_price: 50, avg_score: 2,
	review_count: 1, address: '中共中央党校', cover_photo_url: ''
}
const r3 = mergeWithSnapshot(fav, 7)
check('收藏项·简介补齐', r3.description, '非常好使')
check('收藏项·容量补齐', r3.capacity, 6)
check('收藏项·电话补齐', r3.contact_phone, '17547968227')

// 场景 4：服务端有值时以服务端为准（快照只做兜底，不覆盖真实数据）
const r4 = mergeWithSnapshot({ id: 7, daily_price: 99, address: '新地址' }, 7)
check('服务端优先·价格', r4.daily_price, 99)
check('服务端优先·地址', r4.address, '新地址')
check('服务端优先·评分沿用快照', r4.avg_score, 2)

// 场景 5：没有快照时原样返回服务端数据（不能凭空造数据）
const r5 = mergeWithSnapshot({ id: 99, name: '未知机构' }, 99)
check('无快照·原样返回', r5.name, '未知机构')

console.log('\n' + pass + '/15 通过')
if (pass !== 15) {
	console.error('❌ 快照兜底逻辑有回归')
	process.exit(1)
}
console.log('✅ 快照兜底逻辑全部通过')
