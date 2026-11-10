#!/usr/bin/env node
// #=============================================================================
// 宠栖 PatNest · 城市名解析单测
//
// 背景：后端 POST /geo/reverse 对**直辖市**（北京/上海/天津/重庆）返回的
//       resolved_city 是空串（高德把直辖市的 city 放在 province 上），
//       首页导航栏就会一直显示「已定位」而不是城市名。
//       小程序端在 utils/location.js 的 cityFromAddress() 里做了地址解析兜底，
//       这个脚本验证兜底逻辑对各省市都解析正确。
//
// 运行：node tools/test-city.mjs
// ============================================================================

// 与 utils/location.js 中的实现保持一致（保持同步，改一处要改两处）
function cityFromAddress(addr) {
	if (!addr) return ''
	const s = String(addr)

	const cityIdx = s.indexOf('市')
	if (cityIdx > 0) {
		let seg = s.slice(0, cityIdx + 1)
		seg = seg.replace(/^.*?(?:省|自治区|特别行政区)/, '')
		if (seg.length > 5) seg = seg.slice(-4)
		if (seg.length >= 2) return seg
	}

	const p = s.match(/([\u4e00-\u9fa5]{2,8}(?:省|自治区|特别行政区))/)
	if (p && p[1]) return p[1].replace(/(自治区|特别行政区|省)$/, '')
	return ''
}

function cityText(city) {
	if (!city) return '已定位'
	let short = city.replace(/(特别行政区|自治区|地区|盟|市)$/, '')
	if (!short) short = city
	return short.length > 4 ? short.slice(0, 4) : short
}

// [详细地址, 期望在导航栏显示的城市名]
const CASES = [
	['北京市东城区东华门街道北京市人民政府', '北京'],
	['北京市海淀区青龙桥街道中共中央党校中共中央党校-北院', '北京'],
	['上海市浦东新区世纪大道', '上海'],
	['天津市和平区解放北路', '天津'],
	['重庆市渝中区解放碑', '重庆'],
	['内蒙古自治区呼和浩特市赛罕区巴彦街道内蒙古电子信息职业技术学院', '呼和浩特'],
	['广东省广州市越秀区北京街道广州市人民政府', '广州'],
	['海南省三亚市吉阳区吉阳区直辖狗岭三亚凤凰岭海誓山盟景区', '三亚'],
	['浙江省杭州市西湖区文三路', '杭州'],
	['四川省成都市武侯区', '成都'],
	['陕西省西安市雁塔区', '西安'],
	['黑龙江省哈尔滨市南岗区', '哈尔滨'],
	['新疆维吾尔自治区乌鲁木齐市天山区', '乌鲁木齐'],
	['西藏自治区拉萨市城关区', '拉萨'],
	['湖南省长沙市岳麓区', '长沙'],
	['福建省厦门市思明区', '厦门'],
	['山东省青岛市市南区', '青岛']
]

let pass = 0
for (const [addr, expect] of CASES) {
	const parsed = cityFromAddress(addr)
	const shown = cityText(parsed)
	const ok = shown === expect
	if (ok) pass++
	console.log((ok ? '✅' : '❌'), addr.slice(0, 22).padEnd(24), '→', JSON.stringify(parsed), '→ 显示:', shown)
}

console.log('\n' + pass + '/' + CASES.length + ' 通过')
if (pass !== CASES.length) {
	console.error('❌ 城市名解析有回归')
	process.exit(1)
}
console.log('✅ 城市名解析全部通过')
