// #=============================================================================
// 宠栖 PatNest · 记住密码
//
// 需求：登录页提供「记住账号密码」勾选框，勾选后下次打开自动填充。
//
// 安全说明（重要）：
//   1. 只在用户**主动勾选**后才落盘，关闭登录态不会清掉（用户下次还要用）。
//   2. 密码以明文存在小程序本地存储里 —— 这是「记住密码」这个需求本身的
//      代价，个人版小程序没有 keystore 可用。若产品要求更高安全性，
//      应改成「只记住账号」+ 短信验证码登录。
//   3. 退出登录时**不**清除这里的数据（清除会违背「记住密码」的预期）；
//      但提供 clearRemembered() 供用户主动关闭。
// ============================================================================

import { STORAGE_KEYS } from '../config.js'

/** 读取记住的凭据 */
export function getRemembered() {
	try {
		const raw = uni.getStorageSync(STORAGE_KEYS.REMEMBER_CREDENTIAL)
		if (!raw) return null
		const c = typeof raw === 'string' ? JSON.parse(raw) : raw
		if (!c || !c.username) return null
		return { username: c.username || '', password: c.password || '' }
	} catch (e) {
		return null
	}
}

/** 保存凭据（remember=false 时清除） */
export function saveRemembered(username, password, remember) {
	try {
		if (!remember) {
			uni.removeStorageSync(STORAGE_KEYS.REMEMBER_CREDENTIAL)
			return
		}
		uni.setStorageSync(
			STORAGE_KEYS.REMEMBER_CREDENTIAL,
			JSON.stringify({ username: username || '', password: password || '' })
		)
	} catch (e) {
		// 存储失败不阻断登录
	}
}

/** 主动清除记住的凭据 */
export function clearRemembered() {
	try {
		uni.removeStorageSync(STORAGE_KEYS.REMEMBER_CREDENTIAL)
	} catch (e) {}
}
