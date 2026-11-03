// 高德 JS API 按需加载：只在真正打开地图时才注入脚本，避免后台首屏被第三方 SDK 拖慢。
// Key 来自构建期环境变量 VITE_AMAP_JS_KEY（Web 服务 Key 不能用在浏览器里，会报 USERKEY_PLAT_NOMATCH）。
let loading = null

const JS_KEY = import.meta.env.VITE_AMAP_JS_KEY || ''
const SECURITY_CODE = import.meta.env.VITE_AMAP_SECURITY_CODE || ''

// 后台默认视野，机构大多分布在这座城市附近，搜索/选点会自行移动中心
export const DEFAULT_CENTER = [116.4074, 39.9042]
export const DEFAULT_ZOOM = 11

export function amapConfigured() {
  return Boolean(JS_KEY)
}

export function amapMissingHint() {
  return '未配置高德 JS API Key：在 webadmin/patnestServerAdminWeb/.env.local 设置 VITE_AMAP_JS_KEY' +
    '（控制台服务平台选 JS API），重启后台后即可选点。'
}

export function loadAmap() {
  if (!JS_KEY) return Promise.reject(new Error(amapMissingHint()))
  if (typeof window === 'undefined') return Promise.reject(new Error('当前环境不支持地图'))
  if (window.AMap) return Promise.resolve(window.AMap)
  if (loading) return loading

  loading = new Promise((resolve, reject) => {
    if (SECURITY_CODE) window._AMapSecurityConfig = { securityJsCode: SECURITY_CODE }
    const script = document.createElement('script')
    script.type = 'text/javascript'
    script.async = true
    script.src = `https://webapi.amap.com/maps?v=2.0&key=${encodeURIComponent(JS_KEY)}`
    script.onload = () => {
      if (!window.AMap) {
        loading = null
        reject(new Error('高德地图 SDK 未正确加载，请检查 Key 是否有效'))
        return
      }
      // 搜索建议与逆地理编码是懒加载插件，注册好再交给调用方
      window.AMap.plugin(['AMap.AutoComplete', 'AMap.Geocoder'], () => resolve(window.AMap))
    }
    script.onerror = () => {
      loading = null
      reject(new Error('高德地图脚本加载失败，请检查网络或 Key 配置'))
    }
    document.head.appendChild(script)
  })
  return loading
}

// 高德返回的坐标对象形态不固定（LngLat / 数组 / {lng,lat}），统一成 [lng, lat]
export function toXY(loc) {
  if (!loc) return null
  if (typeof loc.getLng === 'function') return [loc.getLng(), loc.getLat()]
  if (Array.isArray(loc)) return [Number(loc[0]), Number(loc[1])]
  if (loc.lng !== undefined && loc.lat !== undefined) return [Number(loc.lng), Number(loc.lat)]
  if (loc.longitude !== undefined && loc.latitude !== undefined) {
    return [Number(loc.longitude), Number(loc.latitude)]
  }
  return null
}
