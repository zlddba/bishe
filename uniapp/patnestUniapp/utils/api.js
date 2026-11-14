// #=============================================================================
// 宠栖 PatNest · 接口聚合（主人端 · 微信小程序）
//
// ⚠️ 路径以「后端源码」为准，而非《03-接口文档.md》——两者存在若干偏差，
//    逐条差异见 docs/08-接口疑点与待确认.md。
//    关键偏差：
//      1. 通知在 /notifications，不在 /pet-owner/notifications
//      2. 机构浏览有一组「公开路由」/sitters（游客可浏览），登录态也可用
//      3. 容量日历查询参数是 from / to，不是 start / end
//      4. 主人端下单只认 pets:[{pet_id}]，不认 pet_ids
//      5. 订单详情子表键名是 pets，不是 order_pet
// #=============================================================================

import http from './request.js'
import { API_BASE_URL } from '../config.js'

// ---------------------------------------------------------------------------
// ID 归一化
//
// ⚠️ 页面里的 id 多半来自 URL query（如 options.id），是**字符串**。
//    后端用 int64 接收 body 里的 id，Go 的 json 绑定遇到字符串会直接报错：
//    例如 POST /pet-owner/favorites 传 {"sitter_id":"5"} 会返回
//    10001「缺少机构ID」。所以凡是放进**请求体**的 id 都必须转成数字。
//    （放在 URL 路径里的没这个问题，后端用 strconv.ParseInt 解析。）
// ---------------------------------------------------------------------------
function toId(v) {
	const n = parseInt(v, 10)
	return isNaN(n) ? 0 : n
}

// ---------------------------------------------------------------------------
// 认证模块（§1 / §12.1）
// ---------------------------------------------------------------------------
export const authApi = {
	// 注册（role 固定 owner）
	// noAuth：这三个接口本身就在「未登录」状态下调用，
	// 不标的话会被 request 层的「无令牌不发请求」直接拦掉，登录页永远进不去
	register(data) {
		return http.post('/auth/register', data, { noAuth: true })
	},
	// 登录
	login(data) {
		return http.post('/auth/login', data, { noAuth: true })
	},
	// 刷新 token（由 request.js 自动处理，一般不手动调用）
	refresh(refreshToken) {
		return http.post('/auth/refresh', { refresh_token: refreshToken }, { noAuth: true })
	},
	// 登出（吊销该用户全部 refresh_token）
	logout() {
		return http.post('/auth/logout', {})
	},
	// 当前登录用户资料（GET /auth/me）
	getProfile() {
		return http.get('/auth/me')
	},
	// 修改个人资料（PUT /auth/profile，仅传需要改的字段）
	updateProfile(data) {
		return http.put('/auth/profile', data)
	},
	// 修改密码（PUT /auth/password，会吊销全部 refresh_token）
	updatePassword(data) {
		return http.put('/auth/password', data)
	}
}

// ---------------------------------------------------------------------------
// 宠物档案（router.go:63-68）
// ---------------------------------------------------------------------------
export const petApi = {
	// 列表（species / name 过滤 + 分页，默认 per_page=10）
	list(params = {}) {
		return http.get('/pet-owner/pets', params)
	},
	// 详情
	detail(id) {
		return http.get('/pet-owner/pets/' + id)
	},
	// 新增
	create(data) {
		return http.post('/pet-owner/pets', data)
	},
	// 修改（PATCH 语义：全部字段可选，一个字段都不传会 400）
	update(id, data) {
		return http.put('/pet-owner/pets/' + id, data)
	},
	// 软删除
	remove(id) {
		return http.del('/pet-owner/pets/' + id)
	},
	// 上传免疫证明（PUT /pet-owner/pets/{id}/vaccine，状态置 pending）
	uploadVaccine(id, vaccine_urls) {
		return http.put('/pet-owner/pets/' + toId(id) + '/vaccine', { vaccine_urls })
	},
	// 宠物历史寄养记录（按宠物维度聚合）
	// 返回 { pet_id, total, items:[{order_id,order_no,sitter_id,sitter_name,
	//        status,start_date,end_date,total_days,stay_date,total_amount}] }
	stays(id) {
		return http.get('/pet-owner/pets/' + toId(id) + '/stays')
	}
}

// ---------------------------------------------------------------------------
// 机构浏览（router.go:26-31 公开路由 / router.go:111-116 登录态等价路由）
// 采用公开路由，游客未登录也能浏览机构（角色说明 V2 §一「访客」）
// ---------------------------------------------------------------------------
export const sitterApi = {
	// 列表：type(逗号分隔多选) / price_min / price_max / sort / 分页
	// sort 仅支持 price_asc | price_desc | newest，其余按 avg_score 降序
	list(params = {}) {
		return http.get('/sitters', params, { noAuth: true })
	},
	// 全文模糊搜索（q 必填）
	search(q, params = {}) {
		return http.get('/sitters/search', Object.assign({ q }, params), { noAuth: true })
	},
	// 详情（含 detail_photos）
	detail(id) {
		return http.get('/sitters/' + id, {}, { noAuth: true })
	},
	// 容量日历：参数为 from / to，跨度上限 90 天
	// 返回 { sitter_id, items:[{date,used,capacity,available,full}] }
	capacity(id, from, to) {
		return http.get('/sitters/' + id + '/capacity', { from, to }, { noAuth: true })
	},
	// 可加购服务：返回 { sitter_id, items:[{id,name,price,unit,enabled}] }
	addons(id) {
		return http.get('/sitters/' + id + '/addons', {}, { noAuth: true })
	},
	// 机构评价（分页）：返回 { items, total, page, per_page,
	//                          avg_overall, avg_service, avg_environment, avg_hygiene }
	reviews(id, params = {}) {
		return http.get('/sitters/' + id + '/reviews', params, { noAuth: true })
	},
	// 下单前费用预估（与下单同一套核算口径，含节假日上浮/加购/券/积分）
	// 返回 { sitter_id, sitter_name, total_days, pet_count, estimated_amount,
	//        addon_amount, subtotal, discount_amount, points_used, points_discount,
	//        total_amount, breakdown[] }
	estimate(id, params = {}) {
		return http.get('/pet-owner/sitters/' + id + '/estimate', params)
	},
	// 联系机构（需登录，POST /pet-owner/sitters/{id}/contact）
	contact(id, message) {
		return http.post('/pet-owner/sitters/' + toId(id) + '/contact', { message })
	}
}

// ---------------------------------------------------------------------------
// 收藏与对比（router.go:85-88）
// ---------------------------------------------------------------------------
export const favoriteApi = {
	// 收藏列表：返回 { items:[{sitter_id,name,type,daily_price,avg_score,...}] }
	// 注意键名是 sitter_id，没有 id
	list() {
		return http.get('/pet-owner/favorites')
	},
	// 收藏机构（sitter_id 必须是数字，见 toId 说明）
	add(sitter_id) {
		return http.post('/pet-owner/favorites', { sitter_id: toId(sitter_id) })
	},
	// 取消收藏
	remove(sitter_id) {
		return http.del('/pet-owner/favorites/' + toId(sitter_id))
	},
	// 对比：ids 为逗号分隔的机构 id，最多取前 4 个
	compare(ids) {
		const list = (ids || []).map(toId).filter((n) => n > 0)
		return http.get('/pet-owner/favorites/compare', { ids: list.join(',') })
	}
}

// ---------------------------------------------------------------------------
// 订单核心流程（router.go:70-83）
// ---------------------------------------------------------------------------
export const orderApi = {
	// 创建订单（主人端只认 pets:[{pet_id, stay_date}]）
	// 可选：remark / user_coupon_id(用户券id) / points_used / addons
	create(data) {
		const payload = Object.assign({}, data)
		if (payload.sitter_id != null) payload.sitter_id = toId(payload.sitter_id)
		if (Array.isArray(payload.pets)) {
			payload.pets = payload.pets.map((p) =>
				Object.assign({}, p, { pet_id: toId(p.pet_id) })
			)
		}
		if (payload.user_coupon_id != null) payload.user_coupon_id = toId(payload.user_coupon_id)
		return http.post('/pet-owner/orders', payload)
	},
	// 我的订单列表（仅支持 status_filter + 分页）
	list(params = {}) {
		return http.get('/pet-owner/orders', params)
	},
	// 订单详情：{ order, pets[], dynamics?, review? }
	// silent=true 时不弹错误提示：订单详情页有本地快照兜底，
	// 服务端暂时取不到也不该用 toast 打扰用户（否则页面有数据却弹「没有找到对应的数据」）
	detail(id, silent = false) {
		return http.get('/pet-owner/orders/' + toId(id), {}, silent ? { silent: true } : {})
	},
	// 模拟支付（PUT /pet-owner/orders/{id}/pay，无请求体）
	// 支付订单（PUT /pet-owner/orders/{id}/pay）
	// method: 'mock'（默认，模拟支付）/ 'balance'（钱包余额真实扣减）
	// 'wechat' / 'alipay' 演示阶段与 mock 同效
	pay(id, method = 'mock') {
		return http.put('/pet-owner/orders/' + id + '/pay', { method })
	},
	// 取消订单（PUT /pet-owner/orders/{id}/cancel）
	cancel(id, reason = '行程变更') {
		return http.put('/pet-owner/orders/' + id + '/cancel', { reason })
	},
	// 改期（PUT /pet-owner/orders/{id}/reschedule）
	reschedule(id, data) {
		return http.put('/pet-owner/orders/' + id + '/reschedule', data)
	},
	// 续费（PUT /pet-owner/orders/{id}/renew）
	renew(id, data) {
		return http.put('/pet-owner/orders/' + id + '/renew', data)
	},
	// 申请退款（POST /pet-owner/orders/{id}/refund-request）
	refundRequest(id, data) {
		return http.post('/pet-owner/orders/' + id + '/refund-request', data)
	},
	// 我的退款申请进度（GET /pet-owner/refunds，无分页）
	refunds() {
		return http.get('/pet-owner/refunds')
	}
}

// ---------------------------------------------------------------------------
// 寄养动态（§5 / §12.9）
// ---------------------------------------------------------------------------
export const dynamicApi = {
	// 订单动态列表（分页）
	list(orderId, params = {}) {
		return http.get('/pet-owner/orders/' + toId(orderId) + '/dynamics', params)
	},
	// 我的全部寄养动态（跨订单聚合，最多 50 条，无分页）
	mine() {
		return http.get('/pet-owner/dynamics')
	},
	// 单条动态详情（返回 order_no / sitter_name，仅订单主人可见）
	detail(id) {
		return http.get('/pet-owner/dynamics/' + toId(id))
	},
	// 点赞
	like(id) {
		return http.post('/pet-owner/dynamics/' + toId(id) + '/like', {})
	},
	// 取消点赞
	unlike(id) {
		return http.del('/pet-owner/dynamics/' + toId(id) + '/like')
	},
	// 留言列表：返回 { dynamic_id, likes, items:[] }
	comments(id) {
		return http.get('/pet-owner/dynamics/' + toId(id) + '/comments')
	},
	// 发表留言
	comment(id, content) {
		return http.post('/pet-owner/dynamics/' + toId(id) + '/comments', { content })
	}
}

// ---------------------------------------------------------------------------
// 评价（§6 / §12.9）
// ---------------------------------------------------------------------------
export const reviewApi = {
	// 查询已有评价
	// 未评价的订单会返回 30001「no review found」，这属于**正常状态**，
	// 不能弹错误提示（否则订单详情页会莫名其妙弹「没有找到对应的数据」）
	get(orderId) {
		return http.get('/pet-owner/orders/' + toId(orderId) + '/review', {}, { silent: true })
	},
	// 提交评价（四维评分 + 文字 + 照片）
	submit(orderId, data) {
		return http.post('/pet-owner/orders/' + toId(orderId) + '/review', data)
	},
	// 追评（24 小时内）
	followup(id, content) {
		return http.post('/pet-owner/reviews/' + toId(id) + '/followup', { content })
	}
}

// ---------------------------------------------------------------------------
// 消息通知（router.go:119-121 —— 注意：不在 /pet-owner 命名空间下）
// ---------------------------------------------------------------------------
export const notifyApi = {
	// 个人消息列表（scope=personal）：订单状态变化、寄养动态、退款、工单回复
	// 后端 2026-11-05 起按视角隔离，旧的 /notifications 已兼容保留但不做过滤
	list(params = {}) {
		return http.get('/pet-owner/notifications', params)
	},
	// 标记单条已读
	markRead(id) {
		return http.put('/pet-owner/notifications/' + toId(id) + '/read', {})
	},
	// 全部标记已读，返回 { updated }
	markAllRead() {
		return http.put('/pet-owner/notifications/read-all', {})
	}
}

// ---------------------------------------------------------------------------
// 积分与优惠券（router.go:90-92）
// ---------------------------------------------------------------------------
export const walletApi = {
	// 积分余额与流水：{ balance, rate(100积分=1元), items[] }
	points() {
		return http.get('/pet-owner/points')
	},
	// 我的券 + 可领取券：{ items[], claimable[] }
	// items[].id 是「用户券 id」（下单时传 user_coupon_id）
	// claimable[].coupon_id 是「券模板 id」（领券时传）
	// 机构专属券会带 sitter_id / issuer，只能用于该机构的订单
	coupons() {
		return http.get('/pet-owner/coupons')
	},
	// 领取优惠券：路径参数是券模板 id
	claimCoupon(couponId) {
		return http.post('/pet-owner/coupons/' + toId(couponId) + '/claim', {})
	}
}

// ---------------------------------------------------------------------------
// 邀请码（迁移 0022，GET /pet-owner/invite）
// ---------------------------------------------------------------------------
export const inviteApi = {
	// 我的邀请码与邀请统计：
	// { invite_code, invited_by, invited_count, rewarded_count,
	//   points_per_invite, my_reward: { granted, order_id } }
	my() {
		return http.get('/pet-owner/invite')
	}
}

// ---------------------------------------------------------------------------
// 钱包（真实货币，docs/03 §13；与「积分（虚拟）」是两套体系）
// ---------------------------------------------------------------------------
export const moneyApi = {
	// 钱包概览：{ user_id, balance, frozen_balance, total_recharge, total_consume, updated_at }
	overview() {
		return http.get('/pet-owner/wallet')
	},
	// 资金流水：{ items[], total, page, per_page }
	// type: recharge / consume / refund / withdraw / settle_in / adjust_add / adjust_sub
	transactions(params = {}) {
		return http.get('/pet-owner/wallet/transactions', params)
	},
	// 发起充值：{ amount, channel: 'mock'|'wechat'|'alipay' }
	// 返回 { recharge_id, amount, channel, status, pay_params }
	recharge(amount, channel = 'mock') {
		return http.post('/pet-owner/wallet/recharge', { amount, channel })
	},
	// 确认充值（模拟网关）：{ status: 'paid', balance }
	confirmRecharge(rechargeId) {
		return http.post('/pet-owner/wallet/recharge/' + toId(rechargeId) + '/pay', {})
	},
	// 申请余额退款：钱包没有提现通道，提交后余额立即扣走，由客服线下打款；
	// 驳回时金额原路退回钱包。同一时间只允许一笔 pending 单（后端返回 409）
	applyWithdrawal(amount, accountName, accountNo, contact) {
		return http.post('/pet-owner/wallet/withdrawals', {
			amount,
			account_name: accountName,
			account_no: accountNo,
			contact
		})
	}
}

// ---------------------------------------------------------------------------
// 售后工单（router.go:52-55，任意登录用户）
// ---------------------------------------------------------------------------
export const supportApi = {
	// 提交工单
	create(data) {
		return http.post('/support/tickets', data)
	},
	// 我的工单列表（最多 50 条，无分页）
	list() {
		return http.get('/support/tickets')
	},
	// 工单详情与消息
	detail(id) {
		return http.get('/support/tickets/' + id)
	},
	// 追加回复
	reply(id, content) {
		return http.post('/support/tickets/' + id + '/messages', { content })
	}
}

// ---------------------------------------------------------------------------
// 账号（router.go:101-105）
// ---------------------------------------------------------------------------
export const accountApi = {
	// 申请注销账户：mode = account | sitter
	requestCancel(mode = 'account') {
		return http.post('/pet-owner/account/cancel-request', { mode })
	},
	// 撤销注销申请
	withdrawCancel() {
		return http.post('/pet-owner/account/cancel-withdraw', {})
	}
}

// ---------------------------------------------------------------------------
// 地理服务（§6 定位与导航，GCJ-02）
// ---------------------------------------------------------------------------
export const geoApi = {
	// 逆地理编码：坐标 → 城市/区县/详细地址
	// 返回 { latitude, longitude, formatted_address, resolved_city, district, township, adcode }
	//
	// noAuth：首页顶部地名在**未登录**时也要显示（游客可以直接浏览机构列表），
	// 后端把这个接口放在公开组，按 IP/账号限流兜住高德配额。
	reverse(latitude, longitude) {
		return http.post('/geo/reverse', {
			latitude: Number(latitude),
			longitude: Number(longitude)
		}, { silent: true, noAuth: true })
	},
	// 城市 → 下辖区县（带中心点坐标）
	// 参数：{ city: '呼和浩特市' } 或 { adcode: '150100' }（市级 6 位）
	// 返回 { keywords, items: [{ name, adcode, level, center_lng, center_lat }] }
	// 公开接口：选择位置页未登录也要能列出区县
	districts(params = {}) {
		return http.get('/geo/districts', params, { silent: true, noAuth: true })
	},
	// 地址 → 坐标
	geocode(address) {
		return http.post('/geo/geocode', { address }, { silent: true })
	}
}

// ---------------------------------------------------------------------------
// 文件上传（§7，multipart/form-data，字段名 file）
// ---------------------------------------------------------------------------
export const uploadApi = {
	// 上传图片到 MinIO，返回 { url, key, size, name }
	// access_token 只有 30 分钟，上传走的是 uni.uploadFile 而非 request.js，
	// 不接刷新逻辑的话，挂机半小时后选图会永久失败（只能退出重登）。
	image(filePath, retried = false) {
		return new Promise((resolve, reject) => {
			const token = uni.getStorageSync('patnest_access_token')
			uni.uploadFile({
				url: API_BASE_URL + '/upload/image',
				filePath,
				name: 'file',
				header: token ? { Authorization: 'Bearer ' + token } : {},
				success: async (res) => {
					// 令牌过期：刷新一次后重传（只重试一次，避免死循环）
					if (res.statusCode === 401 && !retried) {
						let newToken = null
						try {
							newToken = await http.refresh()
						} catch (e) {
							newToken = null
						}
						if (newToken) {
							try {
								return resolve(await uploadApi.image(filePath, true))
							} catch (e) {
								return reject(e)
							}
						}
						uni.showToast({ title: '登录已失效，请重新登录', icon: 'none' })
						return reject({ code: 20001, message: '登录已失效' })
					}
					try {
						const body = JSON.parse(res.data)
						if (body && body.code === 0) {
							resolve(body.data)
						} else {
							uni.showToast({ title: (body && body.message) || '上传失败', icon: 'none' })
							reject(body)
						}
					} catch (e) {
						uni.showToast({ title: '上传失败，请重试', icon: 'none' })
						reject(e)
					}
				},
				fail: (err) => {
					uni.showToast({ title: '上传失败，请重试', icon: 'none' })
					reject(err)
				}
			})
		})
	}
}

// ---------------------------------------------------------------------------
// 系统信息（公开）
// ---------------------------------------------------------------------------
export const systemApi = {
	// GET /api/v1/version
	version() {
		return http.get('/version', {}, { noAuth: true })
	}
}
