// #=============================================================================
// 宠栖 PatNest · 后端错误消息中文化
//
// 后端部分业务错误返回的是英文 message（例如宠物日期冲突
// "pet 6 is already booked in this date range"），
// 直接透给用户不友好，这里按「错误码 + 关键词」映射成中文提示。
// ============================================================================

// 按错误码给的兜底文案
const BY_CODE = {
	10001: '提交的信息有误，请检查后重试',
	10002: '请求内容格式不正确',
	10003: '分页参数不正确',
	20001: '登录已失效，请重新登录',
	20002: '没有权限执行该操作',
	30001: '没有找到对应的数据',
	30002: '操作冲突，请刷新后重试',
	40001: '金额计算异常，请检查日期与价格',
	40002: '下单失败，请稍后重试',
	50001: '服务器处理失败，请稍后重试',
	50002: '第三方服务调用失败，请稍后重试',
	42901: '操作过于频繁，请稍后再试'
}

// 按 message 关键词精确匹配（优先于错误码）
const BY_KEYWORD = [
	{
		test: /already booked in this date range/i,
		msg: '该宠物在所选日期区间已被预订，请换个日期或换只宠物'
	},
	{
		test: /only for the issuing sitter|仅限发券机构/i,
		msg: '该优惠券仅限发券机构使用'
	},
	{
		test: /commit failed/i,
		msg: '服务器处理支付时出错。若订单金额为 0，多半是机构未设置价格导致支付流水写入被拒，请联系机构或平台处理'
	},
	{
		test: /already paid|无需重复支付/i,
		msg: '该订单已支付，无需重复支付'
	},
	{
		test: /not in pending_pay status|当前订单状态不支持支付/i,
		msg: '当前订单状态不支持支付'
	},
	{
		test: /钱包余额不足|余额不足/i,
		msg: '钱包余额不足，请先充值或改用其它支付方式'
	},
	{
		test: /手机号已被注册/i,
		msg: '该手机号已被注册，请换个手机号或直接用手机号登录'
	},
	{
		test: /账号不能使用手机号格式/i,
		msg: '账号不能使用手机号格式，请换一个用户名'
	},
	{
		test: /请填写手机号|手机号格式不正确|11 位手机号/i,
		msg: '请填写正确的 11 位手机号'
	},
	{
		test: /该机构未设置有效价格|未设置有效价格/i,
		msg: '该机构尚未设置寄养价格，暂时无法下单，请先联系机构确认'
	},
	{
		test: /不能使用手机号格式/i,
		msg: '账号不能使用手机号格式，请换一个用户名'
	},
	{
		test: /cannot be cancelled/i,
		msg: '当前订单状态不支持取消'
	},
	{
		test: /order is not found|order not found/i,
		msg: '订单不存在或已删除'
	},
	{
		test: /pet not found/i,
		msg: '宠物档案不存在或已删除'
	},
	{
		test: /sitter not found or not active/i,
		msg: '该机构不存在或未上架'
	},
	{
		test: /coupon.*not exist|优惠券不存在/i,
		msg: '优惠券不存在'
	},
	{
		test: /capacity|床位/i,
		msg: '所选日期床位不足，请更换日期'
	},
	{
		test: /invalid.*id/i,
		msg: '请求参数不正确'
	}
]

/**
 * 把后端返回的 { code, message } 转成可展示的中文提示
 * @param {Object} err  后端错误对象（request 层 reject 出来的）
 * @returns {string} 中文提示
 */
export function friendlyError(err) {
	if (!err) return '请求失败，请稍后重试'
	const msg = String(err.message || '')
	for (const rule of BY_KEYWORD) {
		if (rule.test.test(msg)) return rule.msg
	}
	const byCode = BY_CODE[err.code]
	if (byCode) return byCode
	return msg || '请求失败，请稍后重试'
}
