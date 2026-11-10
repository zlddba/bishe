// #=============================================================================
// 宠栖 PatNest · 手机号工具
//
// 后端规则（见 docs/16-手机号注册与登录调整-20261105.md）：
//   · 注册时 phone 必填，格式 ^1[3-9]\d{9}$
//   · 手机号全表唯一，重复返回 409「该手机号已被注册」
//   · 用户名不能是手机号格式（否则和手机号登录撞车）
//   · 登录时 username 字段既可传用户名，也可传手机号
// ============================================================================

// 中国大陆手机号：1 开头，第二位 3-9，共 11 位
export const PHONE_RE = /^1[3-9]\d{9}$/

/** 是否是合法手机号格式 */
export function isPhone(s) {
	return PHONE_RE.test(String(s || '').trim())
}

/** 是否是手机号格式（用于判断输入框该显示什么提示） */
export function looksLikePhone(s) {
	return /^\d+$/.test(String(s || '').trim())
}

/** 脱敏显示：138****8000 */
export function maskPhone(s) {
	const t = String(s || '').trim()
	if (!t) return ''
	if (t.length < 7) return t
	return t.slice(0, 3) + '****' + t.slice(t.length - 4)
}

/**
 * 校验注册用的手机号
 * @param {string} phone
 * @returns {string} 错误提示，空串表示通过
 */
export function checkPhone(phone) {
	const t = String(phone || '').trim()
	if (!t) return '请填写手机号'
	if (!PHONE_RE.test(t)) return '请输入正确的 11 位手机号'
	return ''
}

/**
 * 校验注册用的用户名（不能是手机号格式）
 * @param {string} name
 * @returns {string} 错误提示，空串表示通过
 */
export function checkUsername(name) {
	const t = String(name || '').trim()
	if (!t) return '请填写账号'
	if (t.length < 3 || t.length > 50) return '账号长度需 3-50 位'
	if (!/^[A-Za-z0-9_]+$/.test(t)) return '账号只能含字母数字下划线'
	if (PHONE_RE.test(t)) return '账号不能使用手机号格式，请换一个用户名'
	return ''
}
