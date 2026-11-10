// #=============================================================================
// 宠栖 PatNest · 鉴权与本地存储工具
// 负责 access_token / refresh_token / 当前用户信息的持久化与读取。
// 对应《03-接口文档.md》§0.4 双 token 机制。
// #=============================================================================

import { STORAGE_KEYS } from '../config.js'

// 读取 access_token
export function getAccessToken() {
  return uni.getStorageSync(STORAGE_KEYS.ACCESS_TOKEN) || ''
}

// 写入 access_token
export function setAccessToken(token) {
  uni.setStorageSync(STORAGE_KEYS.ACCESS_TOKEN, token)
}

// 读取 refresh_token
export function getRefreshToken() {
  return uni.getStorageSync(STORAGE_KEYS.REFRESH_TOKEN) || ''
}

// 写入 refresh_token
export function setRefreshToken(token) {
  uni.setStorageSync(STORAGE_KEYS.REFRESH_TOKEN, token)
}

// 读取当前用户信息
export function getUserInfo() {
  try {
    return uni.getStorageSync(STORAGE_KEYS.USER_INFO) || null
  } catch (e) {
    return null
  }
}

// 写入当前用户信息
export function setUserInfo(info) {
  uni.setStorageSync(STORAGE_KEYS.USER_INFO, info)
}

// 是否已登录（仅判断本地是否有 token，token 合法性由后端 401 校验）
export function isLoggedIn() {
  return !!getAccessToken()
}

// 当前用户角色
export function getRole() {
  const u = getUserInfo()
  return u ? u.role : ''
}

// 清空登录态（登出 / token 失效时调用）
export function clearAuth() {
  uni.removeStorageSync(STORAGE_KEYS.ACCESS_TOKEN)
  uni.removeStorageSync(STORAGE_KEYS.REFRESH_TOKEN)
  uni.removeStorageSync(STORAGE_KEYS.USER_INFO)
}

// 登录成功后的统一写入（注册/登录/刷新成功后调用）
export function applyAuthResult(data) {
  if (!data) return
  if (data.access_token) setAccessToken(data.access_token)
  // refresh_token 可能通过 Set-Cookie 下发，也可能在 body 内
  if (data.refresh_token) {
    setRefreshToken(data.refresh_token)
  } else if (data.refresh_token_cookie && data.refresh_token_cookie.value) {
    setRefreshToken(data.refresh_token_cookie.value)
  }
  if (data.user) setUserInfo(data.user)
}
