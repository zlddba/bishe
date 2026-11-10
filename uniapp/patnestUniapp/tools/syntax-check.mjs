#!/usr/bin/env node
/**
 * 语法体检：把 uniapp 小程序端所有 .js / .vue 的脚本块抽出来做纯语法检查。
 * 不执行代码、不解析 import（只做 parse），因此不需要安装依赖。
 * 用法：node tools/syntax-check.mjs
 */
import fs from 'node:fs'
import path from 'node:path'
import os from 'node:os'
import { execFileSync } from 'node:child_process'
import { fileURLToPath } from 'node:url'

const __dirname = path.dirname(fileURLToPath(import.meta.url))
const ROOT = path.resolve(__dirname, '..')
const SRC = path.join(ROOT, 'pages')
const COMP = path.join(ROOT, 'components')
const UTILS = path.join(ROOT, 'utils')

const tmp = fs.mkdtempSync(path.join(os.tmpdir(), 'patnest-syntax-'))
let checked = 0
const failures = []

function checkCode(code, label) {
	const file = path.join(tmp, 'chk.mjs')
	fs.writeFileSync(file, code, 'utf8')
	try {
		execFileSync(process.execPath, ['--check', file], { stdio: 'pipe' })
		checked++
	} catch (e) {
		failures.push({ label, message: (e.stderr || e.stdout || '').toString().trim() })
	}
}

function walk(dir, out = []) {
	if (!fs.existsSync(dir)) return out
	for (const name of fs.readdirSync(dir)) {
		const full = path.join(dir, name)
		const st = fs.statSync(full)
		if (st.isDirectory()) walk(full, out)
		else out.push(full)
	}
	return out
}

// 1) 纯 .js 文件
for (const f of walk(UTILS).concat([path.join(ROOT, 'config.js')])) {
	if (!f.endsWith('.js')) continue
	checkCode(fs.readFileSync(f, 'utf8'), path.relative(ROOT, f))
}

// 2) .vue 文件的 <script> 块
for (const f of walk(SRC).concat(walk(COMP))) {
	if (!f.endsWith('.vue')) continue
	const raw = fs.readFileSync(f, 'utf8')
	const m = raw.match(/<script[^>]*>([\s\S]*?)<\/script>/)
	if (!m) {
		failures.push({ label: path.relative(ROOT, f), message: '未找到 <script> 块' })
		continue
	}
	checkCode(m[1], path.relative(ROOT, f))
}

// 3) JSON 体检（pages.json / manifest.json）
for (const name of ['pages.json', 'manifest.json']) {
	const p = path.join(ROOT, name)
	const raw = fs.readFileSync(p, 'utf8')
	// manifest.json 含注释，先剥掉 /* */ 与行注释
	const stripped = raw.replace(/\/\*[\s\S]*?\*\//g, '').replace(/^\s*\/\/.*$/gm, '')
	try {
		JSON.parse(stripped)
		checked++
	} catch (e) {
		failures.push({ label: name, message: e.message })
	}
}

// 4) pages.json 路由与磁盘文件一致性
try {
	const pages = JSON.parse(fs.readFileSync(path.join(ROOT, 'pages.json'), 'utf8'))
	const listed = pages.pages.map((p) => p.path)
	for (const p of listed) {
		const vue = path.join(ROOT, p + '.vue')
		if (!fs.existsSync(vue)) {
			failures.push({ label: 'pages.json', message: '路由 ' + p + ' 对应的 .vue 不存在' })
		} else {
			checked++
		}
	}
	// 磁盘上存在但未注册的页面
	const onDisk = walk(SRC)
		.filter((f) => f.endsWith('.vue'))
		.map((f) => path.relative(ROOT, f).replace(/\\/g, '/').replace(/\.vue$/, ''))
	for (const p of onDisk) {
		if (!listed.includes(p)) {
			failures.push({ label: 'pages.json', message: '页面 ' + p + ' 未在 pages.json 中注册' })
		}
	}
	// tabBar 页面必须是首页（小程序要求 tabBar 首项为首页）
	const first = pages.pages[0].path
	if (pages.tabBar.list[0].pagePath !== first) {
		failures.push({ label: 'pages.json', message: 'tabBar 第一项必须是 pages 数组第一项 ' + first })
	}
} catch (e) {
	failures.push({ label: 'pages.json', message: e.message })
}

// 5) manifest.json 的微信定位声明校验
// ⚠️ requiredPrivateInfos 微信只认 8 个隐私接口，写错会导致开发者工具
//    「模拟器启动失败：requiredPrivateInfos 字段有误」。
//    详见 https://developers.weixin.qq.com/miniprogram/dev/reference/configuration/app.html
try {
	const rawManifest = fs.readFileSync(path.join(ROOT, 'manifest.json'), 'utf8')
	const manifest = JSON.parse(rawManifest.replace(/\/\*[\s\S]*?\*\//g, '').replace(/^\s*\/\/.*$/gm, ''))
	const mp = manifest['mp-weixin'] || {}
	const rpi = mp.requiredPrivateInfos || []
	const LEGAL = [
		'getFuzzyLocation',
		'getLocation',
		'onLocationChange',
		'startLocationUpdate',
		'startLocationUpdateBackground',
		'chooseLocation',
		'choosePoi',
		'chooseAddress'
	]
	for (const v of rpi) {
		if (!LEGAL.includes(v)) {
			failures.push({
				label: 'manifest.json',
				message:
					'requiredPrivateInfos 里的 "' + v + '" 不是微信支持的隐私接口。\n' +
					'         合法值只有：' + LEGAL.join(' / ') + '\n' +
					'         （openLocation 属于「打开地图」，不需要也不允许声明）'
			})
		}
	}
	if (rpi.includes('getFuzzyLocation') && rpi.includes('getLocation')) {
		failures.push({ label: 'manifest.json', message: 'getFuzzyLocation 与 getLocation 不能同时声明' })
	}
	const scope = (mp.permission || {})['scope.userLocation']
	if (scope) {
		if (!scope.desc) {
			failures.push({ label: 'manifest.json', message: 'permission.scope.userLocation.desc 必填' })
		} else if (scope.desc.length > 30) {
			failures.push({
				label: 'manifest.json',
				message: 'permission.scope.userLocation.desc 超长（' + scope.desc.length + '/30 字符）'
			})
		}
	}
	if (rpi.length) checked++
} catch (e) {
	failures.push({ label: 'manifest.json', message: e.message })
}

// 6) 图标检查：文件存在 + <image> 必须显式设宽高
// ⚠️ 微信小程序里 <image> 默认尺寸是 320×240px，不设 width/height 会把
//    容器撑爆（表现为「图标巨大、文字被挤出去」）。
try {
	const iconDir = path.join(ROOT, 'static', 'icons')
	const have = new Set(
		fs.existsSync(iconDir)
			? fs.readdirSync(iconDir).filter((f) => f.endsWith('.png')).map((f) => f.slice(0, -4))
			: []
	)
	const vueFiles = []
	const walkVue = (dir) => {
		for (const name of fs.readdirSync(dir)) {
			const f = path.join(dir, name)
			const st = fs.statSync(f)
			if (st.isDirectory()) walkVue(f)
			else if (name.endsWith('.vue')) vueFiles.push(f)
		}
	}
	walkVue(path.join(ROOT, 'pages'))
	walkVue(path.join(ROOT, 'components'))

	for (const f of vueFiles) {
		const src = fs.readFileSync(f, 'utf8')
		const rel = path.relative(ROOT, f).replace(/\\/g, '/')
		// 收集所有 class 的样式块
		const styles = {}
		for (const m of src.matchAll(/([.#])([a-zA-Z0-9_-]+)\s*\{([^}]*)\}/g)) {
			styles[m[2]] = m[3]
		}
		// 6a) 引用的图标文件必须存在
		for (const m of src.matchAll(/\/static\/icons\/([a-zA-Z0-9_-]+)\.png/g)) {
			if (!have.has(m[1])) {
				failures.push({
					label: rel,
					message: '引用的图标 /static/icons/' + m[1] + '.png 不存在（跑 python tools/gen-icons.py 生成）'
				})
			}
		}
		// 6b) <image> 必须显式设宽高（否则默认 320×240 会撑爆容器）
		for (const m of src.matchAll(/<image[^>]*class="([^"]+)"[^>]*\/>/g)) {
			const cls = m[1].trim()
			const sized = cls
				.split(/\s+/)
				.some((c) => {
					const b = styles[c] || ''
					return /\bwidth\s*:/.test(b) && /\bheight\s*:/.test(b)
				})
			if (!sized) {
				failures.push({
					label: rel,
					message:
						'<image class="' + cls + '"> 没有对应的 width/height 样式。\n' +
						'  微信小程序 <image> 默认 320×240px，不设尺寸会撑爆容器。'
				})
			}
		}
	}
	checked++
} catch (e) {
	failures.push({ label: '图标检查', message: e.message })
}

// 7) 编译产物检查：pages.json 与编译产物必须一致，且不能有孤儿产物
//
// ⚠️ 背景：删除页面后，微信开发者工具会报
//    「[WXSS 文件编译错误] ENOENT: ... pages/xxx/live.wxss」
//    原因不是源码有问题，而是**开发者工具缓存了已删除页面的文件列表**，
//    同时 HBuilderX 的增量编译没有清掉 unpackage 里的孤儿产物。
//    这里主动检查并提示，避免真机预览时才炸。
try {
	const distDir = path.join(ROOT, 'unpackage', 'dist', 'dev', 'mp-weixin')
	if (fs.existsSync(distDir)) {
		const appJsonPath = path.join(distDir, 'app.json')
		if (fs.existsSync(appJsonPath)) {
			const app = JSON.parse(fs.readFileSync(appJsonPath, 'utf8'))
			const declared = new Set(app.pages || [])

			// 7a) 产物里声明的页面，四个文件都必须存在
			for (const p of declared) {
				for (const ext of ['.js', '.wxml', '.wxss', '.json']) {
					const f = path.join(distDir, p + ext)
					if (!fs.existsSync(f)) {
						failures.push({
							label: '编译产物',
							message:
								'产物缺失：' + p + ext + '\n' +
								'  微信开发者工具会报 ENOENT。请在 HBuilderX 里重新运行到微信小程序模拟器，\n' +
								'  或删除 unpackage/dist/dev/mp-weixin 后重新编译。'
						})
					}
				}
			}

			// 7b) 孤儿产物：磁盘上存在页面文件，但 app.json 里没声明
			//     （页面被删了但产物没清干净，开发者工具会尝试编译它 → ENOENT）
			//
			//     路径规则（与 app.json 一致）：pages/<目录>/<文件名>
			//     例：pages/login/login.wxml → 页面路径 pages/login/login
			//     只扫 pages/ 下的两层：pages/xxx/xxx.wxml
			const pagesRoot = path.join(distDir, 'pages')
			if (fs.existsSync(pagesRoot)) {
				for (const dirName of fs.readdirSync(pagesRoot)) {
					const dir = path.join(pagesRoot, dirName)
					if (!fs.statSync(dir).isDirectory()) continue
					for (const fileName of fs.readdirSync(dir)) {
						if (!fileName.endsWith('.wxml')) continue
						const pagePath = 'pages/' + dirName + '/' + fileName.slice(0, -'.wxml'.length)
						if (!declared.has(pagePath)) {
							failures.push({
								label: '编译产物',
								message:
									'孤儿产物：' + pagePath + ' 不在 app.json 的 pages 列表里。\n' +
									'  说明该页面已删除但编译产物没清干净，微信开发者工具会报 ENOENT。\n' +
									'  处理：删除 unpackage/dist/dev/mp-weixin 整个目录后重新编译，\n' +
									'        并在微信开发者工具里「清缓存 → 全部清除」。'
							})
						}
					}
				}
			}
		}
	}
	checked++
} catch (e) {
	failures.push({ label: '编译产物检查', message: e.message })
}

fs.rmSync(tmp, { recursive: true, force: true })

console.log('✅ 通过检查项：' + checked)
if (failures.length) {
	console.log('❌ 失败 ' + failures.length + ' 项：')
	for (const f of failures) {
		console.log('--- ' + f.label + '\n' + f.message)
	}
	process.exit(1)
}
console.log('全部通过 ✅')
