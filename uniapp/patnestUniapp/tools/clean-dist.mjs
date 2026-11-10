#!/usr/bin/env node
// #=============================================================================
// 宠栖 PatNest · 小程序编译产物清理
//
// 为什么需要：
//   删除/重命名页面后，HBuilderX 的**增量编译**不会清掉 unpackage 里的旧产物，
//   微信开发者工具就会报：
//       [WXSS 文件编译错误] ENOENT: no such file or directory,
//       open '.../unpackage/dist/dev/mp-weixin/pages/xxx/xxx.wxss'
//   这不是源码有问题，而是**产物与开发者工具的缓存不一致**。
//
// 清理策略（默认「精准模式」）：
//   只删「孤儿产物」—— 即磁盘上存在、但 app.json 里没声明的页面文件。
//   优点：不整目录清空，微信开发者工具占用的文件不会被误删，
//         也不会让开发者工具缓存整个失效。
//
// 用法：
//   node tools/clean-dist.mjs        # 精准模式：只删孤儿产物（默认）
//   node tools/clean-dist.mjs --all  # 全清：删掉整个 mp-weixin 产物目录
//   node tools/clean-dist.mjs --dry  # 只看会删什么，不真删
//
// ⚠️ 精准模式删不掉被占用的文件（微信开发者工具正开着时会遇到），
//    此时改用 --all 全清，或先关掉开发者工具再删。
// ============================================================================

import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..')
const args = process.argv.slice(2)
const DRY = args.includes('--dry')
const ALL = args.includes('--all')

const DIST = path.join(ROOT, 'unpackage', 'dist', 'dev', 'mp-weixin')
const SOURCEMAP = path.join(ROOT, 'unpackage', 'dist', 'dev', '.sourcemap')

function rmSafe(p) {
	try {
		fs.rmSync(p, { recursive: true, force: true })
		return true
	} catch (e) {
		return false
	}
}

// ---------- 全清模式 ----------
if (ALL) {
	for (const t of [DIST, SOURCEMAP]) {
		if (!fs.existsSync(t)) {
			console.log('· 跳过（不存在）：' + path.relative(ROOT, t))
			continue
		}
		if (DRY) {
			console.log('· 将删除：' + path.relative(ROOT, t))
			continue
		}
		if (rmSafe(t)) {
			console.log('✓ 已删除：' + path.relative(ROOT, t))
		} else {
			console.log('✗ 删除失败（文件被占用）：' + path.relative(ROOT, t))
			console.log('  请先关闭微信开发者工具，再执行一次。')
		}
	}
	console.log('')
	console.log(DRY ? '（dry-run，未实际删除）' : '下一步：HBuilderX「运行到小程序模拟器 → 微信开发者工具」重新编译。')
	process.exit(0)
}

// ---------- 精准模式：只删孤儿产物 ----------
if (!fs.existsSync(DIST)) {
	console.log('· 没有编译产物，无需清理')
	process.exit(0)
}

const appJsonPath = path.join(DIST, 'app.json')
if (!fs.existsSync(appJsonPath)) {
	console.log('✗ 找不到 ' + path.relative(ROOT, appJsonPath) + '，请先在 HBuilderX 里编译一次')
	process.exit(1)
}

const app = JSON.parse(fs.readFileSync(appJsonPath, 'utf8'))
const declared = new Set(app.pages || [])

const orphans = []
const pagesRoot = path.join(DIST, 'pages')
if (fs.existsSync(pagesRoot)) {
	for (const dirName of fs.readdirSync(pagesRoot)) {
		const dir = path.join(pagesRoot, dirName)
		if (!fs.statSync(dir).isDirectory()) continue
		for (const fileName of fs.readdirSync(dir)) {
			if (!fileName.endsWith('.wxml')) continue
			const pagePath = 'pages/' + dirName + '/' + fileName.slice(0, -'.wxml'.length)
			if (!declared.has(pagePath)) {
				orphans.push({ pagePath, dir })
			}
		}
	}
}

if (!orphans.length) {
	console.log('✓ 产物干净，没有孤儿页面')
	console.log('  如果开发者工具仍报 ENOENT，说明是它自己的缓存：')
	console.log('  工具栏 →「清缓存 → 全部清除」，再点「编译」。')
	process.exit(0)
}

console.log('发现 ' + orphans.length + ' 个孤儿产物（页面已删但产物没清）：')
let done = 0
let failed = 0
for (const o of orphans) {
	console.log('  · ' + o.pagePath)
	if (DRY) continue
	// 删该页面的全部产物文件（.js/.wxml/.wxss/.json）
	let allGone = true
	for (const ext of ['.js', '.wxml', '.wxss', '.json']) {
		const f = path.join(o.dir, path.basename(o.pagePath) + ext)
		if (!fs.existsSync(f)) continue
		if (!rmSafe(f)) allGone = false
	}
	// 页面文件删干净后，顺带清掉空的子目录
	if (allGone) {
		try {
			if (fs.readdirSync(o.dir).length === 0) fs.rmdirSync(o.dir)
		} catch (e) {}
	}
	if (allGone) done++
	else failed++
}

// sourcemap 里对应的 .map 也是孤儿，一并清掉
if (!DRY && fs.existsSync(SOURCEMAP)) {
	const smRoot = path.join(SOURCEMAP, 'pages')
	if (fs.existsSync(smRoot)) {
		for (const o of orphans) {
			const rel = o.pagePath.replace(/^pages\//, '')
			const mapFile = path.join(smRoot, rel + '.js.map')
			if (fs.existsSync(mapFile)) rmSafe(mapFile)
		}
	}
}

console.log('')
if (DRY) {
	console.log('（dry-run，未实际删除）')
} else {
	console.log('✓ 已清理 ' + done + ' 个孤儿页面')
	if (failed) {
		console.log('✗ ' + failed + ' 个删不掉（文件被微信开发者工具占用）')
		console.log('  处理：关闭微信开发者工具后执行 node tools/clean-dist.mjs --all，')
		console.log('        或在开发者工具里「清缓存 → 全部清除」。')
	}
	console.log('  下一步：在微信开发者工具点「编译」。')
}
