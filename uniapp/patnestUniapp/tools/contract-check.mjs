#!/usr/bin/env node
/**
 * 契约体检：把 utils/api.js 里用到的每个接口路径，与后端 router.go 实际注册的
 * 路由逐条比对，确保小程序端不会调到一个后端并不存在的地址。
 *
 * 之所以需要这个脚本：《03-接口文档.md》与后端源码存在多处偏差
 * （如通知在 /notifications 而非 /pet-owner/notifications），
 * 光看文档容易写错，以源码为准才不会踩坑。
 *
 * 用法：node tools/contract-check.mjs
 */
import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const __dirname = path.dirname(fileURLToPath(import.meta.url))
const ROOT = path.resolve(__dirname, '..')
const REPO = path.resolve(ROOT, '../..')
const ROUTER = path.join(REPO, 'server/patnestServer/internal/router/router.go')
const API = path.join(ROOT, 'utils/api.js')

// ---------------------------------------------------------------------------
// 1) 解析后端路由
// ---------------------------------------------------------------------------
const routerSrc = fs.readFileSync(ROUTER, 'utf8')

/**
 * 把 Go 的路由注册语句还原成「完整路径 + 方法」。
 * 需要处理 gin 的分组前缀：v1 := r.Group("/api/v1")、owner := protected.Group("/pet-owner") 等。
 */
function parseRoutes(src) {
	const groupPrefix = new Map() // 变量名 -> 前缀
	const routes = []

	// 先收集所有分组声明
	const groupRe = /(\w+)\s*:?=\s*(?:r|v1|protected|owner|browse|sitter|admin|auth)\.Group\("([^"]*)"\)/g
	let m
	while ((m = groupRe.exec(src))) {
		groupPrefix.set(m[1], m[2])
	}

	// 变量前缀 = 自身 + 父分组前缀（按声明顺序推断，这里用启发式）
	function resolvePrefix(varName) {
		// 常见命名：xxx := v1.Group(...) → v1 的前缀 + 自身
		const own = groupPrefix.get(varName)
		if (own == null) return null
		// 找出该变量声明所在的父级：搜索 "varName := X.Group" 并取 X
		const re = new RegExp(varName + '\\s*:=\\s*(\\w+)\\.Group')
		const pm = src.match(re)
		if (!pm) return own
		const parent = resolvePrefix(pm[1])
		return (parent == null ? '' : parent) + own
	}

	// 解析注册语句：xxx.GET("/path", handler.X)
	const callRe = /(\w+)\.(GET|POST|PUT|DELETE|PATCH)\("([^"]*)"/g
	while ((m = callRe.exec(src))) {
		const [, varName, method, p] = m
		let prefix = resolvePrefix(varName)
		if (prefix == null) continue
		routes.push({ method: method.toUpperCase(), path: (prefix + p).replace(/\/+/g, '/') })
	}
	return routes
}

const backendRoutes = parseRoutes(routerSrc)

// ---------------------------------------------------------------------------
// 2) 解析小程序端用到的路径
// ---------------------------------------------------------------------------
const apiSrc = fs.readFileSync(API, 'utf8')
const used = []

/**
 * 取出每个 http.get/post/put/del 调用的**第一个参数**。
 * 不能用简单正则，因为参数里可能含函数调用（如 toId(id)），
 * 其右括号会让正则提前截断，所以这里做括号/引号配平扫描。
 */
const callRe = /http\.(get|post|put|del)\(/g
let mm
while ((mm = callRe.exec(apiSrc))) {
	const method = mm[1] === 'del' ? 'DELETE' : mm[1].toUpperCase()
	const expr = readFirstArg(apiSrc, mm.index + mm[0].length)
	if (expr) used.push({ method, path: '/api/v1' + normalizePath(expr) })
	callRe.lastIndex = mm.index + mm[0].length + expr.length
}

/** 从 start 开始读取到第一个顶层逗号（或调用结束）为止的实参文本 */
function readFirstArg(src, start) {
	let depth = 0
	let quote = null
	for (let i = start; i < src.length; i++) {
		const ch = src[i]
		if (quote) {
			if (ch === '\\') {
				i++
				continue
			}
			if (ch === quote) quote = null
			continue
		}
		if (ch === "'" || ch === '"' || ch === '`') {
			quote = ch
			continue
		}
		if (ch === '(' || ch === '[' || ch === '{') {
			depth++
			continue
		}
		if (ch === ')' || ch === ']' || ch === '}') {
			if (depth === 0) return src.slice(start, i).trim()
			depth--
			continue
		}
		if (ch === ',' && depth === 0) return src.slice(start, i).trim()
	}
	return ''
}

/**
 * 把 JS 表达式还原成路径模板。
 *   "'/pet-owner/pets/' + toId(id) + '/stays'"  →  "/pet-owner/pets/:id/stays"
 *   "'/pet-owner/pets'"                          →  "/pet-owner/pets"
 */
function normalizePath(expr) {
	const parts = expr.split('+').map((s) => s.trim())
	let out = ''
	for (const part of parts) {
		if (/^'.*'$/.test(part) || /^".*"$/.test(part)) {
			out += part.slice(1, -1)
		} else {
			// 变量、toId(x) 之类的调用一律视为路径参数
			out += ':id'
		}
	}
	return out
}

// ---------------------------------------------------------------------------
// 3) 比对
// ---------------------------------------------------------------------------
function toPattern(p) {
	const segs = p.split('/').filter(Boolean)
	return '/' + segs
		.map((s) => (s.startsWith(':') ? '[^/]+' : s.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')))
		.join('/')
}

function match(method, path) {
	const re = new RegExp('^' + toPattern(path) + '$')
	return backendRoutes.find((r) => r.method === method && re.test(r.path))
}

const problems = []
const warnings = []
const okCount = []

// 例外清单：后端已在服务器部署但仓库 router.go 尚未同步的接口
let exceptions = []
const EX_FILE = path.join(__dirname, 'contract-exceptions.json')
if (fs.existsSync(EX_FILE)) {
	try {
		exceptions = JSON.parse(fs.readFileSync(EX_FILE, 'utf8')).例外 || []
	} catch (e) {
		console.log('⚠️  contract-exceptions.json 解析失败：' + e.message)
	}
}

function isException(method, path) {
	// 把 :id 之类的参数段统一成 :id 再比对
	const norm = (p) => p.replace(/:[^/]+/g, ':id')
	return exceptions.some((x) => x.method === method && norm(x.path) === norm(path))
}

for (const u of used) {
	const hit = match(u.method, u.path)
	if (hit) {
		okCount.push(u.method + ' ' + u.path + '  →  ' + hit.path)
	} else {
		// 也许只是参数名不同（例如 :id vs :sitter_id），做一次宽松匹配
		const loose = backendRoutes.find(
			(r) =>
				r.method === u.method &&
				toPattern(r.path)
					.replace(/:\w+/g, ':x')
					.split('/')
					.filter(Boolean).length === u.path.split('/').filter(Boolean).length &&
				new RegExp('^' + toPattern(r.path).replace(/:\w+/g, '[^/]+') + '$').test(
					u.path.replace(/:\w+/g, 'x')
				)
		)
		if (loose) {
			okCount.push(u.method + ' ' + u.path + '  ~  ' + loose.path + '（参数名不同，路径可用）')
		} else if (isException(u.method, u.path)) {
			warnings.push(u.method + ' ' + u.path + '  ⚠ 已登记例外（服务器已实现，仓库 router.go 未同步）')
		} else {
			problems.push(u.method + ' ' + u.path + '  ✗ 后端无此路由')
		}
	}
}

// ---------------------------------------------------------------------------
// 4) 反向检查：后端有但前端没用到的主人端路由（提示，不算失败）
// ---------------------------------------------------------------------------
const ownerPrefixes = ['/api/v1/pet-owner', '/api/v1/sitters', '/api/v1/support', '/api/v1/auth', '/api/v1/notifications']
const unused = backendRoutes.filter(
	(r) =>
		ownerPrefixes.some((p) => r.path.startsWith(p)) &&
		!used.some((u) => {
			const re = new RegExp('^' + toPattern(u.path).replace(/:\w+/g, '[^/]+') + '$')
			return re.test(r.path)
		})
)

console.log('后端已注册路由：' + backendRoutes.length + ' 条')
console.log('小程序端调用接口：' + used.length + ' 个')
console.log('✅ 匹配成功 ' + okCount.length + ' 个')
if (warnings.length) {
	console.log('⚠️  已登记例外 ' + warnings.length + ' 个（服务器已实现，仓库 router.go 未同步）：')
	warnings.forEach((w) => console.log('   ' + w))
}
if (problems.length) {
	console.log('❌ 不匹配 ' + problems.length + ' 个：')
	problems.forEach((p) => console.log('   ' + p))
}
if (unused.length) {
	console.log('ℹ️  后端有但小程序端暂未使用的主人端路由 ' + unused.length + ' 条：')
	unused.forEach((u) => console.log('   ' + u.method + ' ' + u.path))
}
if (problems.length) process.exit(1)
console.log('接口契约对齐 ✅')
