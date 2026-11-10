// #=============================================================================
// 宠栖 PatNest · 拨号工具
//
// 「联系商家」时拉起系统拨号界面，号码自动填好。
// 微信小程序用 `uni.makePhoneCall`，在 H5 / 其它端会退化为复制号码。
// ============================================================================

/**
 * 拉起系统拨号界面
 * @param {String} phone 号码
 * @param {Object} opts { name: 备注名, fallback: 无号码时的兜底文案 }
 * @returns {Boolean} 是否成功拉起
 */
export function dialPhone(phone, opts = {}) {
	const num = String(phone || '').trim()
	if (!num) {
		uni.showToast({ title: opts.fallback || '该机构暂未提供联系电话', icon: 'none' })
		return false
	}
	// 去掉号码里的分隔符（空格、-、括号），拨号盘只认数字
	const clean = num.replace(/[\s\-()]/g, '')
	uni.makePhoneCall({
		phoneNumber: clean,
		fail: (err) => {
			// 用户取消拨号也会走 fail，不弹提示；其它错误才提示
			const msg = String((err && err.errMsg) || '')
			if (msg.indexOf('cancel') >= 0) return
			uni.showModal({
				title: '无法拨打电话',
				content: '号码：' + num + '\n\n请在系统拨号盘中手动输入该号码。',
				confirmText: '复制号码',
				confirmColor: '#C85B74',
				success: (r) => {
					if (r.confirm) {
						uni.setClipboardData({
							data: num,
							success: () => uni.showToast({ title: '号码已复制', icon: 'success' })
						})
					}
				}
			})
		}
	})
	return true
}

/**
 * 复制号码到剪贴板
 */
export function copyPhone(phone) {
	const num = String(phone || '').trim()
	if (!num) return false
	uni.setClipboardData({
		data: num,
		success: () => uni.showToast({ title: '号码已复制', icon: 'success' }),
		fail: () => uni.showToast({ title: '复制失败，请手动记录', icon: 'none' })
	})
	return true
}
