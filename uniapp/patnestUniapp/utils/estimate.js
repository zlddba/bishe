// #=============================================================================
// 宠栖 PatNest · 前端费用预估
//
// ⚠️ 后端目前没有「下单前预估」接口（见 docs/08 §A.1），本模块按后端
//    service 层同口径在本地复算，仅用于下单前的透明化展示。
//    最终金额以服务端下单返回值为准。
//
// 口径依据：
//   server/patnestServer/internal/handler/order_handler.go:55-190
//     per_day   → 逐日 daily_price × 节假日 multiplier，再 × pet_count
//     per_visit → visit_price × pet_count
//     subtotal  = estimated_amount + addon_amount
//     discount  = fixed → value；percent → subtotal × value / 100
//     points    → points_used / 100（100 积分 = 1 元）
//     total     = subtotal - discount - points_discount（下限 0）
// #=============================================================================

import { PRICE_MODE } from '../config.js'
import { diffDays } from './format.js'

// 积分兑换率：100 积分 = 1 元
export const POINTS_PER_YUAN = 100

/**
 * 计算费用明细
 * @param {Object} o
 * @param {string} o.priceMode      per_day | per_visit
 * @param {number} o.dailyPrice     日单价
 * @param {number} o.visitPrice     次单价
 * @param {string} o.startDate      YYYY-MM-DD
 * @param {string} o.endDate        YYYY-MM-DD
 * @param {number} o.petCount       宠物数量
 * @param {Array}  o.addons         [{ id, name, price, qty }]
 * @param {Object} o.coupon         { type, value, min_amount }，可为 null
 * @param {number} o.pointsUsed     使用积分
 * @param {number} o.pointsBalance  积分余额（用于校验上限）
 * @param {number} o.pointsRate     兑换率，默认 100
 */
export function calcEstimate(o) {
	const opt = Object.assign(
		{
			priceMode: PRICE_MODE.PER_DAY,
			dailyPrice: 0,
			visitPrice: 0,
			startDate: '',
			endDate: '',
			petCount: 0,
			addons: [],
			coupon: null,
			pointsUsed: 0,
			pointsBalance: Infinity,
			pointsRate: POINTS_PER_YUAN
		},
		o || {}
	)

	const days = diffDays(opt.startDate, opt.endDate)
	const petCount = Number(opt.petCount) || 0
	const dailyPrice = Number(opt.dailyPrice) || 0
	const visitPrice = Number(opt.visitPrice) || 0

	// 基础寄养费
	let baseAmount = 0
	let totalDays = 0
	if (opt.priceMode === PRICE_MODE.PER_VISIT) {
		baseAmount = visitPrice * petCount
		totalDays = 0
	} else {
		totalDays = days
		baseAmount = dailyPrice * totalDays * petCount
	}

	// 加购服务费
	let addonAmount = 0
	const addonLines = []
	;(opt.addons || []).forEach((a) => {
		const qty = Number(a.qty) || 0
		if (qty <= 0) return
		const amount = (Number(a.price) || 0) * qty
		addonAmount += amount
		addonLines.push({ name: a.name, qty, amount })
	})

	const subtotal = baseAmount + addonAmount

	// 优惠券抵扣
	let discountAmount = 0
	let couponUsable = false
	let couponHint = ''
	const coupon = opt.coupon
	if (coupon && subtotal > 0) {
		const minAmount = Number(coupon.min_amount) || 0
		if (subtotal < minAmount) {
			couponHint = '未满足 ' + minAmount.toFixed(2) + ' 元可用门槛'
		} else {
			couponUsable = true
			if (coupon.type === 'percent') {
				discountAmount = (subtotal * (Number(coupon.value) || 0)) / 100
			} else {
				discountAmount = Number(coupon.value) || 0
			}
			if (discountAmount > subtotal) discountAmount = subtotal
		}
	}

	// 积分抵扣（100 积分 = 1 元）
	// 可抵扣上限同时受两个条件约束（与 order_pricing.go:205-221 同口径）：
	//   ① 积分余额（单位：积分，不能再乘兑换率）
	//   ② 优惠后剩余应付金额 × 兑换率
	let pointsUsed = Math.max(0, Math.floor(Number(opt.pointsUsed) || 0))
	let pointsDiscount = 0
	if (pointsUsed > 0) {
		const remaining = Math.max(0, subtotal - discountAmount)
		const balancePoints = Math.floor(Number(opt.pointsBalance) || 0)
		const maxPoints = Math.min(balancePoints, Math.floor(remaining * opt.pointsRate))
		if (pointsUsed > maxPoints) pointsUsed = maxPoints
		pointsDiscount = pointsUsed / opt.pointsRate
	}

	const totalAmount = Math.max(0, subtotal - discountAmount - pointsDiscount)

	return {
		days: totalDays,
		petCount,
		baseAmount,
		addonAmount,
		addonLines,
		subtotal,
		discountAmount,
		couponUsable,
		couponHint,
		pointsUsed,
		pointsDiscount,
		totalAmount
	}
}

// 优惠券可抵扣金额（用于列表展示）
export function couponWorth(coupon) {
	if (!coupon) return 0
	const value = Number(coupon.value) || 0
	return coupon.type === 'percent' ? value + '%' : '¥' + value.toFixed(2)
}
