import { fileURLToPath, URL } from 'node:url'
import { execSync } from 'node:child_process'
import { mkdirSync, readFileSync, writeFileSync } from 'node:fs'
import { join } from 'node:path'
import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

const pkg = JSON.parse(readFileSync(new URL('./package.json', import.meta.url), 'utf-8'))

function gitCommit() {
  try {
    return execSync('git rev-parse --short HEAD', { stdio: ['ignore', 'pipe', 'ignore'] }).toString().trim()
  } catch {
    return 'unknown'
  }
}

const buildTime = new Date().toISOString()
const commit = gitCommit()
const version = process.env.APP_VERSION || pkg.version || '0.0.0'
// build_id 每次构建都不同，用它判断“线上静态资源是否已更新”
const buildId = `${version}.${commit}.${buildTime.slice(0, 19).replace(/[-:T]/g, '')}`

function versionFilePlugin() {
  return {
    name: 'patnest-version-file',
    apply: 'build',
    writeBundle(options) {
      const dir = options.dir || 'dist'
      mkdirSync(dir, { recursive: true })
      writeFileSync(
        join(dir, 'version.json'),
        JSON.stringify({ version, commit, build_time: buildTime, build_id: buildId }, null, 2)
      )
    },
  }
}

export default defineConfig({
  plugins: [vue(), versionFilePlugin()],
  define: {
    __APP_VERSION__: JSON.stringify(version),
    __APP_COMMIT__: JSON.stringify(commit),
    __APP_BUILD_ID__: JSON.stringify(buildId),
    __APP_BUILD_TIME__: JSON.stringify(buildTime),
  },
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
    },
  },
  server: {
    port: 5173,
    proxy: {
      '/api': {
        // 用 127.0.0.1，避免 Windows 上 localhost 先解析到 ::1 导致代理连不上。
        target: process.env.VITE_PROXY_TARGET || 'http://127.0.0.1:9200',
        changeOrigin: true,
      },
    },
  },
})
