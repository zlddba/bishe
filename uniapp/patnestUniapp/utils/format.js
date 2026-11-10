// #=============================================================================
// 宠栖 PatNest · 通用格式化工具
// 后端时间字段格式不统一，需要统一处理：
//   - orders.start_date / end_date : "2026-11-01"（纯日期）
//   - orders.created_at 等           : "2026-10-29T10:00:00Z"（RFC3339）
//   - refund_requests.created_at     : "2026-11-02 10:30"（PG to_char）
//   - notifications.created_at       : "2026-11-02T03:00:00Z"
// #=============================================================================

// 把各种格式的时间字符串统一转成 Date（失败返回 null）
export function parseTime(s) {
	if (!s) return null
	if (s instanceof Date) return s
	const str = String(s).trim()
	// "2026-11-02 10:30" → "2026-11-02T10:30:00"（Safari 不认空格分隔）
	const normalized = str.replace(' ', 'T')
	let d = new Date(normalized)
	if (isNaN(d.getTime()) && /^\d{4}-\d{2}-\d{2}$/.test(str)) {
		d = new Date(str + 'T00:00:00')
	}
	return isNaN(d.getTime()) ? null : d
}

// 补零
function pad(n) {
	return n < 10 ? '0' + n : '' + n
}

// Date → "YYYY-MM-DD"
export function fmtDate(d) {
	if (!d) return ''
	const dt = d instanceof Date ? d : parseTime(d)
	if (!dt) return ''
	return dt.getFullYear() + '-' + pad(dt.getMonth() + 1) + '-' + pad(dt.getDate())
}

// Date → "YYYY-MM-DD HH:mm"
export function fmtDateTime(d) {
	if (!d) return ''
	const dt = d instanceof Date ? d : parseTime(d)
	if (!dt) return ''
	return fmtDate(dt) + ' ' + pad(dt.getHours()) + ':' + pad(dt.getMinutes())
}

// 今天的 "YYYY-MM-DD"
export function today() {
	return fmtDate(new Date())
}

// 日期加减天数，返回 "YYYY-MM-DD"
export function addDays(date, n) {
	const d = date instanceof Date ? new Date(date.getTime()) : parseTime(date)
	if (!d) return ''
	d.setDate(d.getDate() + n)
	return fmtDate(d)
}

// 相差天数（结束 - 开始，含首不含尾）
export function diffDays(start, end) {
	const s = parseTime(start)
	const e = parseTime(end)
	if (!s || !e) return 0
	const n = Math.round((e.getTime() - s.getTime()) / 86400000)
	return n > 0 ? n : 0
}

// 金额格式化，保留两位小数
export function fmtMoney(n) {
	const v = Number(n)
	if (isNaN(v)) return '0.00'
	return v.toFixed(2)
}

// 紧凑金额：整数不带小数
export function fmtPrice(n) {
	const v = Number(n)
	if (isNaN(v)) return '0'
	return Number.isInteger(v) ? String(v) : v.toFixed(2)
}

// 相对时间：刚刚 / N 分钟前 / N 小时前 / N 天前 / 具体日期
export function fromNow(s) {
	const d = parseTime(s)
	if (!d) return ''
	const diff = Date.now() - d.getTime()
	if (diff < 0) return fmtDateTime(d)
	const min = Math.floor(diff / 60000)
	if (min < 1) return '刚刚'
	if (min < 60) return min + ' 分钟前'
	const hour = Math.floor(min / 60)
	if (hour < 24) return hour + ' 小时前'
	const day = Math.floor(hour / 24)
	if (day < 30) return day + ' 天前'
	return fmtDate(d)
}

// 评分保留一位小数
export function fmtScore(n) {
	const v = Number(n)
	if (isNaN(v) || v === 0) return '0.0'
	return v.toFixed(1)
}

// 安全解析后端返回的 JSON 字符串字段（如 notifications.extra）
export function parseJson(str, fallback = {}) {
	if (!str) return fallback
	if (typeof str === 'object') return str
	try {
		const v = JSON.parse(str)
		return v && typeof v === 'object' ? v : fallback
	} catch (e) {
		return fallback
	}
}
