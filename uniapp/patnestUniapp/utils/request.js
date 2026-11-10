// #=============================================================================
// 宠栖 PatNest · 统一请求封装
// 封装 uni.request：自动注入 JWT、统一解析 {code,message,data} 信封、
// 处理 401 无感刷新、登录失效跳转。
// 契约依据：server/patnestServer/internal/handler/response.go（统一信封）
//           server/patnestServer/internal/middleware/jwt.go（错误码）
// #=============================================================================

import { API_BASE_URL, SUCCESS_CODE, ERROR_CODE } from '../config.js'
import { friendlyError } from './errors.js'
import {
	getAccessToken,
	setAccessToken,
	getRefreshToken,
	setRefreshToken,
	clearAuth,
	applyAuthResult
} from './auth.js'

// 防止并发刷新：同一时刻只发一次 refresh，其余请求排队等待结果
let refreshing = false
let refreshWaiters = []

function waitRefresh() {
	return new Promise((resolve) => refreshWaiters.push(resolve))
}
function flushRefresh(token) {
	refreshWaiters.forEach((fn) => fn(token))
	refreshWaiters = []
}

// ---------------------------------------------------------------------------
// 刷新 access_token
//
// 小程序没有 Cookie 能力，而后端 Refresh 处理器的取值优先级为
//   ① PostForm("refresh_token")  ② Cookie("refresh_token")  ③ JSON body
// （server/patnestServer/internal/handler/auth_handler.go:219-229）
// 因此本端统一走 ③：POST /auth/refresh 携带 JSON body { refresh_token }。
//
// ⚠️ 后端不会在刷新响应里下发新的 refresh_token，
//    必须继续沿用本地保存的那一枚（auth_handler.go:272-275）。
// ---------------------------------------------------------------------------
async function doRefresh() {
	const rt = getRefreshToken()
	if (!rt) return null
	try {
		const res = await rawRequest({
			url: '/auth/refresh',
			method: 'POST',
			data: { refresh_token: rt },
			header: { 'Content-Type': 'application/json' },
			noAuth: true
		})
		if (res && res.access_token) {
			setAccessToken(res.access_token)
			if (res.refresh_token) setRefreshToken(res.refresh_token)
			return res.access_token
		}
		return null
	} catch (e) {
		return null
	}
}

// ---------------------------------------------------------------------------
// 底层请求：返回 uni.request 的原始响应
// ---------------------------------------------------------------------------
function rawRequest(options) {
	return new Promise((resolve, reject) => {
		uni.request({
			url: options.url.startsWith('http') ? options.url : API_BASE_URL + options.url,
			method: options.method || 'GET',
			data: options.data,
			header: options.header || {},
			dataType: options.dataType || 'json',
			responseType: options.responseType || 'text',
			success: (res) => resolve(res),
			fail: (err) => reject(err)
		})
	})
}

// ---------------------------------------------------------------------------
// 主请求方法
// ---------------------------------------------------------------------------
async function request(options) {
	const token = getAccessToken()
	const header = Object.assign(
		{ 'Content-Type': 'application/json' },
		options.header || {}
	)
	if (token && !options.noAuth) {
		header['Authorization'] = 'Bearer ' + token
	}

	let res
	try {
		res = await rawRequest({
			url: options.url,
			method: options.method || 'GET',
			data: options.data,
			header
		})
	} catch (err) {
		if (options.silent !== true) {
			uni.showToast({ title: '网络异常，请稍后重试', icon: 'none' })
		}
		return Promise.reject(err)
	}

	const statusCode = res.statusCode
	const body = res.data

	// --- HTTP 层非 2xx：400 参数错 / 401 认证 / 403 越权 / 404 不存在 /
	//     409 冲突 / 429 限流 / 500 内部错误
	if (statusCode < 200 || statusCode >= 300) {
		if (statusCode === 401) {
			return handleUnauthorized(options)
		}
		const msg = friendlyError(body) || '服务器开小差了(' + statusCode + ')'
		if (options.silent !== true) {
			uni.showToast({ title: msg, icon: 'none' })
		}
		return Promise.reject(Object.assign({ message: msg }, body || { code: statusCode }))
	}

	// --- 业务层统一信封 { code, message, data }
	// 后端 data 带 omitempty，值为 nil 时整个 key 消失（response.go:12）
	if (body && typeof body.code !== 'undefined') {
		if (body.code === SUCCESS_CODE) {
			return typeof body.data === 'undefined' ? null : body.data
		}
		if (body.code === ERROR_CODE.UNAUTHENTICATED) {
			return handleUnauthorized(options)
		}
		if (body.code === ERROR_CODE.ADMIN_CONFLICT) {
			clearAuth()
			uni.showToast({ title: '您的账号已在其他设备登录', icon: 'none' })
			redirectToLogin()
			return Promise.reject(body)
		}
		if (options.silent !== true) {
			uni.showToast({ title: friendlyError(body), icon: 'none' })
		}
		return Promise.reject(body)
	}

	// 非标准响应（如直接返回裸数据），原样返回
	return body
}

// ---------------------------------------------------------------------------
// 401 处理：尝试刷新 token 并仅重试一次
// ---------------------------------------------------------------------------
async function handleUnauthorized(options) {
	if (refreshing) {
		const newToken = await waitRefresh()
		if (newToken) return request(options)
		return Promise.reject({ code: ERROR_CODE.UNAUTHENTICATED, message: '登录已失效' })
	}

	refreshing = true
	const newToken = await doRefresh()
	refreshing = false

	if (newToken) {
		flushRefresh(newToken)
		return request(options)
	}

	// 刷新失败：清理登录态并跳转登录页
	flushRefresh(null)
	clearAuth()
	redirectToLogin()
	return Promise.reject({ code: ERROR_CODE.UNAUTHENTICATED, message: '登录已失效' })
}

function redirectToLogin() {
	// 避免在登录页反复跳转
	const pages = getCurrentPages()
	const cur = pages.length ? pages[pages.length - 1].route : ''
	if (cur && cur.indexOf('login') === -1) {
		uni.reLaunch({ url: '/pages/login/login' })
	}
}

// ---------------------------------------------------------------------------
// 对外导出便捷方法
// ---------------------------------------------------------------------------
export default {
	get(url, data, opts = {}) {
		return request({ url, method: 'GET', data, ...opts })
	},
	post(url, data, opts = {}) {
		return request({ url, method: 'POST', data, ...opts })
	},
	put(url, data, opts = {}) {
		return request({ url, method: 'PUT', data, ...opts })
	},
	del(url, data, opts = {}) {
		return request({ url, method: 'DELETE', data, ...opts })
	},
	// 原始请求（用于上传、blob 下载等需要自定义处理返回体的场景）
	raw: rawRequest
}
