<template>
	<view class="wallet-page patnest-page">
		<!-- 钱包余额（真实货币，用于订单支付） -->
		<view class="money-card patnest-card">
			<view class="m-top">
				<view class="m-left">
					<text class="m-num">¥{{ fmtMoney(money.balance) }}</text>
					<text class="m-label">钱包余额</text>
				</view>
				<view class="m-right">
					<text class="m-rate">累计充值 ¥{{ fmtMoney(money.total_recharge) }}</text>
					<text class="m-hint">累计消费 ¥{{ fmtMoney(money.total_consume) }}</text>
				</view>
			</view>
			<view class="m-actions">
				<view class="m-btn" @click="openRecharge">充值</view>
				<view class="m-btn" @click="openWithdraw">申请退款</view>
				<view class="m-btn ghost" @click="showMoneyLogs = !showMoneyLogs">
					{{ showMoneyLogs ? '收起流水' : '资金流水' }}
				</view>
			</view>
		</view>

		<!-- 资金流水 -->
		<view v-if="showMoneyLogs" class="block">
			<view class="b-title">资金流水</view>
			<view v-if="moneyLogs.length === 0" class="no-coupon">暂无资金流水</view>
			<view v-for="t in moneyLogs" :key="t.id" class="log-row">
				<view class="l-main">
					<text class="l-reason">{{ moneyTypeText(t.type) }}</text>
					<text class="l-time">{{ fmtTime(t.created_at) }}</text>
				</view>
				<view class="l-right">
					<text :class="['l-amount', t.amount >= 0 ? 'plus' : 'minus']">
						{{ t.amount >= 0 ? '+' : '' }}¥{{ fmtMoney(t.amount) }}
					</text>
					<text class="l-balance">余额 {{ fmtMoney(t.balance_after) }}</text>
				</view>
			</view>
		</view>

		<!-- 积分概览 -->
		<view class="points-card patnest-card">
			<view class="p-left">
				<text class="p-num">{{ balance }}</text>
				<text class="p-label">当前积分</text>
			</view>
			<view class="p-right">
				<text class="p-rate">100 积分 = 1 元</text>
				<text class="p-hint">下单时可直接抵扣</text>
			</view>
		</view>

		<!-- 我的优惠券 -->
		<view class="block">
			<view class="b-title">我的优惠券</view>
			<view v-if="myCoupons.length === 0" class="no-coupon">暂无可用优惠券</view>
			<view v-for="c in myCoupons" :key="c.id" class="coupon" :class="c.status !== 'unused' ? 'used' : ''">
				<view class="c-left">
					<text class="c-value">{{ couponText(c) }}</text>
					<text class="c-cond">{{ c.min_amount > 0 ? ('满 ' + c.min_amount + ' 元可用') : '无门槛' }}</text>
				</view>
				<view class="c-right">
					<text class="c-title">{{ c.title }}</text>
					<view class="c-meta">
						<text v-if="c.issuer" class="c-issuer">{{ c.issuer }}</text>
						<text class="c-date">有效期至 {{ c.ends_at || '长期' }}</text>
					</view>
					<text class="c-status">{{ statusText(c.status) }}</text>
				</view>
			</view>
		</view>

		<!-- 可领取优惠券 -->
		<view v-if="claimable.length" class="block">
			<view class="b-title">可领取</view>
			<view v-for="c in claimable" :key="c.coupon_id" class="coupon">
				<view class="c-left">
					<text class="c-value">{{ couponText(c) }}</text>
					<text class="c-cond">{{ c.min_amount > 0 ? ('满 ' + c.min_amount + ' 元可用') : '无门槛' }}</text>
				</view>
				<view class="c-right">
					<text class="c-title">{{ c.title }}</text>
					<text class="c-date">有效期至 {{ c.ends_at || '长期' }}</text>
				</view>
				<view class="c-btn" @click="claim(c)">领取</view>
			</view>
		</view>

		<!-- 积分流水 -->
		<view class="block">
			<view class="b-title">积分流水</view>
			<view v-if="pointLogs.length === 0" class="no-coupon">暂无积分流水</view>
			<view v-for="(l, i) in pointLogs" :key="i" class="log-row">
				<view class="l-info">
					<text class="l-reason">{{ l.reason || '积分变动' }}</text>
					<text class="l-time">{{ fmtTime(l.created_at) }}</text>
				</view>
				<view class="l-right">
					<text :class="['l-change', l.change >= 0 ? 'plus' : 'minus']">
						{{ l.change >= 0 ? '+' : '' }}{{ l.change }}
					</text>
					<text class="l-balance">余额 {{ l.balance }}</text>
				</view>
			</view>
		</view>

		<!-- 充值弹层 -->
		<view v-if="showRecharge" class="mask" @click="showRecharge = false">
			<view class="sheet" @click.stop>
				<view class="sheet-title">钱包充值</view>
				<text class="sheet-tip">演示环境使用模拟支付渠道，确认后立即到账。</text>
				<view class="amount-row">
					<text class="a-label">充值金额</text>
					<input class="a-input" type="digit" v-model="rechargeAmount" placeholder="0.00" />
					<text class="a-unit">元</text>
				</view>
				<view class="quick-row">
					<view
						v-for="v in quickAmounts"
						:key="v"
						:class="['quick', Number(rechargeAmount) === v ? 'on' : '']"
						@click="rechargeAmount = String(v)"
					>
						{{ v }} 元
					</view>
				</view>
				<view class="sheet-actions">
					<view class="sheet-btn cancel" @click="showRecharge = false">取消</view>
					<view class="sheet-btn ok" :class="{ disabled: recharging }" @click="doRecharge">
						{{ recharging ? '处理中...' : '确认充值' }}
					</view>
				</view>
			</view>
		</view>

		<!-- 余额退款弹层 -->
		<view v-if="showWithdraw" class="mask" @click="showWithdraw = false">
			<view class="sheet" @click.stop>
				<view class="sheet-title">申请余额退款</view>
				<text class="sheet-tip">提交后金额立即从余额扣除，由客服线下打款；若被驳回，金额自动退回钱包。</text>
				<view class="amount-row">
					<text class="a-label">退款金额</text>
					<input class="a-input" type="digit" v-model="wdAmount" placeholder="0.00" />
					<text class="a-unit">元</text>
				</view>
				<view class="amount-row">
					<text class="a-label">收款人</text>
					<input class="a-input small" v-model="wdName" placeholder="与银行卡/支付宝一致" maxlength="32" />
				</view>
				<view class="amount-row">
					<text class="a-label">收款账号</text>
					<input class="a-input small" v-model="wdNo" placeholder="银行卡号 / 支付宝账号" maxlength="64" />
				</view>
				<view class="amount-row">
					<text class="a-label">联系电话</text>
					<input class="a-input small" type="number" v-model="wdContact" placeholder="方便客服确认" maxlength="20" />
				</view>
				<view class="sheet-actions">
					<view class="sheet-btn cancel" @click="showWithdraw = false">取消</view>
					<view class="sheet-btn ok" :class="{ disabled: withdrawing }" @click="doWithdraw">
						{{ withdrawing ? '提交中...' : '提交申请' }}
					</view>
				</view>
			</view>
		</view>
	</view>
</template>

<script>
	import { walletApi, moneyApi } from '../../utils/api.js'
	import { USER_COUPON_STATUS_TEXT } from '../../config.js'
	import { fmtDateTime, fmtMoney } from '../../utils/format.js'

	export default {
		data() {
			return {
				balance: 0,
				rate: 100,
				myCoupons: [],
				claimable: [],
				pointLogs: [],
				// 钱包（真实货币）：与上面的积分是两套体系
				money: { balance: 0, frozen_balance: 0, total_recharge: 0, total_consume: 0 },
				moneyLogs: [],
				showMoneyLogs: false,
				// 充值弹层
				showRecharge: false,
				recharging: false,
				rechargeAmount: '',
				quickAmounts: [50, 100, 200, 500],
				// 余额退款弹层
				showWithdraw: false,
				withdrawing: false,
				wdAmount: '',
				wdName: '',
				wdNo: '',
				wdContact: ''
			}
		},
		onShow() {
			this.load()
		},
		methods: {
			async load() {
				try {
					const p = await walletApi.points()
					this.balance = (p && p.balance) || 0
					this.rate = (p && p.rate) || 100
					this.pointLogs = (p && p.items) || []
				} catch (e) {}
				try {
					const c = await walletApi.coupons()
					this.myCoupons = (c && c.items) || []
					this.claimable = (c && c.claimable) || []
				} catch (e) {}
				// 钱包概览（失败不影响积分与券）
				try {
					const w = await moneyApi.overview()
					if (w) this.money = w
				} catch (e) {}
				if (this.showMoneyLogs) {
					try {
						const res = await moneyApi.transactions({ page: 1, per_page: 30 })
						this.moneyLogs = (res && res.items) || []
					} catch (e) {}
				}
			},
			// 打开充值弹层
			openRecharge() {
				this.rechargeAmount = ''
				this.showRecharge = true
			},
			// 发起充值并确认（模拟网关同步到账）
			async doRecharge() {
				const amt = parseFloat(this.rechargeAmount)
				if (isNaN(amt) || amt <= 0) {
					return uni.showToast({ title: '请输入有效的充值金额', icon: 'none' })
				}
				this.recharging = true
				try {
					const r = await moneyApi.recharge(amt, 'mock')
					const rid = r && r.recharge_id
					if (!rid) {
						uni.showToast({ title: '充值单创建失败', icon: 'none' })
						return
					}
					const done = await moneyApi.confirmRecharge(rid)
					this.showRecharge = false
					uni.showToast({
						title: '充值成功，余额 ¥' + fmtMoney((done && done.balance) || 0),
						icon: 'success'
					})
					this.load()
				} catch (e) {
					// 错误提示已在 request 层处理
				} finally {
					this.recharging = false
				}
			},
			// 打开退款弹层
			openWithdraw() {
				this.wdAmount = ''
				this.wdName = ''
				this.wdNo = ''
				this.wdContact = ''
				this.showWithdraw = true
			},
			// 提交余额退款申请：钱包没有提现通道，这里只登记，客服线下打款
			async doWithdraw() {
				if (this.withdrawing) return
				const amt = parseFloat(this.wdAmount)
				if (isNaN(amt) || amt < 1) {
					return uni.showToast({ title: '退款金额至少 1 元', icon: 'none' })
				}
				if (amt > Number(this.money.balance)) {
					return uni.showToast({ title: '不能超过可用余额', icon: 'none' })
				}
				if (!this.wdName.trim()) {
					return uni.showToast({ title: '请填写收款人', icon: 'none' })
				}
				if (!this.wdNo.trim()) {
					return uni.showToast({ title: '请填写收款账号', icon: 'none' })
				}
				const confirm = await uni.showModal({
					title: '申请退款',
					content:
						'将申请退款 ¥' + fmtMoney(amt) +
						'，提交后金额立即从余额扣除，由客服线下打款。确定提交吗？',
					confirmText: '提交'
				})
				if (!confirm.confirm) return
				this.withdrawing = true
				try {
					await moneyApi.applyWithdrawal(
						amt,
						this.wdName.trim(),
						this.wdNo.trim(),
						this.wdContact.trim()
					)
					this.showWithdraw = false
					uni.showToast({ title: '已提交，客服会尽快联系你', icon: 'none' })
					this.load()
				} catch (e) {
					// 409（已有待处理单）等错误已由 request 层提示
				} finally {
					this.withdrawing = false
				}
			},
			// 资金流水类型文案
			moneyTypeText(t) {
				const map = {
					recharge: '充值',
					consume: '订单消费',
					refund: '退款',
					withdraw: '提现',
					settle_in: '机构结算',
					adjust_add: '平台加款',
					adjust_sub: '平台扣款'
				}
				return map[t] || '资金变动'
			},
			fmtMoney(n) {
				return fmtMoney(n)
			},
			couponText(c) {
				if (c.type === 'percent') return c.value + '%'
				return '¥' + Number(c.value || 0).toFixed(2)
			},
			statusText(s) {
				return USER_COUPON_STATUS_TEXT[s] || s
			},
			async claim(c) {
				try {
					await walletApi.claimCoupon(c.coupon_id)
					uni.showToast({ title: '领取成功', icon: 'success' })
					this.load()
				} catch (e) {}
			},
			fmtTime(s) {
				return fmtDateTime(s)
			}
		}
	}
</script>

<style scoped>
	.wallet-page {
		padding: 24rpx;
		padding-bottom: 60rpx;
	}
	/* 钱包（真实货币） */
	.money-card {
		padding: 32rpx;
		margin-bottom: 24rpx;
		background: linear-gradient(135deg, #C85B74 0%, #AF4A62 100%);
	}
	.m-top {
		display: flex;
		align-items: flex-start;
		justify-content: space-between;
	}
	.m-num {
		display: block;
		font-size: 54rpx;
		font-weight: 700;
		color: #fff;
		line-height: 1.15;
	}
	.m-label {
		display: block;
		font-size: 23rpx;
		color: rgba(255, 255, 255, 0.82);
		margin-top: 8rpx;
	}
	.m-right {
		text-align: right;
	}
	.m-rate {
		display: block;
		font-size: 22rpx;
		color: rgba(255, 255, 255, 0.85);
	}
	.m-hint {
		display: block;
		font-size: 22rpx;
		color: rgba(255, 255, 255, 0.7);
		margin-top: 6rpx;
	}
	.m-actions {
		display: flex;
		margin-top: 28rpx;
	}
	.m-btn {
		flex: 1;
		height: 68rpx;
		line-height: 68rpx;
		text-align: center;
		border-radius: 34rpx;
		background: #fff;
		color: #AF4A62;
		font-size: 26rpx;
		font-weight: 600;
	}
	.m-btn.ghost {
		margin-left: 20rpx;
		background: rgba(255, 255, 255, 0.22);
		color: #fff;
	}
	/* 资金流水行 */
	.l-main {
		flex: 1;
		min-width: 0;
	}
	.l-amount {
		display: block;
		font-size: 28rpx;
		font-weight: 600;
		text-align: right;
	}
	.l-amount.plus {
		color: #2F9E63;
	}
	.l-amount.minus {
		color: #C2456A;
	}
	/* 充值弹层 */
	.mask {
		position: fixed;
		left: 0;
		right: 0;
		top: 0;
		bottom: 0;
		background: rgba(0, 0, 0, 0.45);
		display: flex;
		align-items: flex-end;
		z-index: 300;
	}
	.sheet {
		width: 100%;
		background: #fff;
		border-radius: 28rpx 28rpx 0 0;
		padding: 36rpx 32rpx calc(36rpx + env(safe-area-inset-bottom));
		box-sizing: border-box;
	}
	.sheet-title {
		font-size: 32rpx;
		font-weight: 700;
		color: #1B1E23;
		text-align: center;
	}
	.sheet-tip {
		display: block;
		font-size: 22rpx;
		color: #9AA3AF;
		margin-top: 12rpx;
		line-height: 1.5;
		text-align: center;
	}
	.amount-row {
		display: flex;
		align-items: center;
		margin-top: 28rpx;
		padding: 20rpx 0;
		border-bottom: 1rpx solid #EFF1F3;
	}
	.a-label {
		font-size: 27rpx;
		color: #5C6470;
		margin-right: 20rpx;
	}
	.a-input {
		flex: 1;
		font-size: 34rpx;
		font-weight: 600;
		color: #1B1E23;
	}
	/* 姓名/账号/电话这类文本输入不需要金额那么大的字号 */
	.a-input.small {
		font-size: 28rpx;
		font-weight: 400;
	}
	.a-unit {
		font-size: 26rpx;
		color: #9AA3AF;
	}
	.quick-row {
		display: flex;
		margin-top: 20rpx;
	}
	.quick {
		flex: 1;
		text-align: center;
		padding: 16rpx 0;
		margin-right: 16rpx;
		background: #F6F7F8;
		border-radius: 10rpx;
		font-size: 24rpx;
		color: #5C6470;
	}
	.quick:last-child {
		margin-right: 0;
	}
	.quick.on {
		background: #FDF1F4;
		color: #AF4A62;
		font-weight: 600;
	}
	.sheet-actions {
		display: flex;
		margin-top: 36rpx;
	}
	.sheet-btn {
		flex: 1;
		text-align: center;
		height: 82rpx;
		line-height: 82rpx;
		border-radius: 41rpx;
		font-size: 28rpx;
	}
	.sheet-btn.cancel {
		background: #F6F7F8;
		color: #5C6470;
		margin-right: 20rpx;
	}
	.sheet-btn.ok {
		background: #C85B74;
		color: #fff;
	}
	.sheet-btn.ok.disabled {
		background: #E3B9C3;
	}
	.points-card {
		display: flex;
		align-items: center;
		justify-content: space-between;
		padding: 36rpx 32rpx;
		margin-bottom: 24rpx;
		background: linear-gradient(135deg, #FBE0E6 0%, #FDF1F4 100%);
	}
	.p-num {
		font-size: 60rpx;
		font-weight: 700;
		color: #AF4A62;
		line-height: 1.1;
		display: block;
	}
	.p-label {
		font-size: 24rpx;
		color: #8E3B4E;
	}
	.p-right {
		text-align: right;
	}
	.p-rate {
		font-size: 24rpx;
		color: #AF4A62;
		display: block;
	}
	.p-hint {
		font-size: 22rpx;
		color: #B4536A;
		display: block;
		margin-top: 8rpx;
	}
	.block {
		background: #fff;
		border-radius: 20rpx;
		padding: 28rpx;
		margin-bottom: 24rpx;
		box-shadow: 0 4rpx 16rpx rgba(27, 30, 35, 0.04);
	}
	.b-title {
		font-size: 30rpx;
		font-weight: 600;
		margin-bottom: 20rpx;
		color: #1B1E23;
	}
	.no-coupon {
		font-size: 26rpx;
		color: #B7BDC5;
		padding: 30rpx 0;
		text-align: center;
	}
	.coupon {
		display: flex;
		align-items: center;
		background: #FDF1F4;
		border-radius: 16rpx;
		padding: 24rpx;
		margin-bottom: 16rpx;
	}
	.coupon.used {
		background: #F6F7F8;
		opacity: 0.65;
	}
	.c-left {
		width: 180rpx;
		text-align: center;
		border-right: 1rpx dashed #F6C2CE;
		padding-right: 20rpx;
		flex-shrink: 0;
	}
	.c-value {
		font-size: 40rpx;
		font-weight: 700;
		color: #C2456A;
		display: block;
	}
	.c-cond {
		font-size: 20rpx;
		color: #8E3B4E;
		display: block;
		margin-top: 4rpx;
	}
	.c-right {
		flex: 1;
		margin-left: 20rpx;
		min-width: 0;
	}
	.c-title {
		font-size: 27rpx;
		color: #1B1E23;
		display: block;
	}
	.c-meta {
		display: flex;
		align-items: center;
		flex-wrap: wrap;
		margin-top: 6rpx;
	}
	.c-issuer {
		font-size: 20rpx;
		color: #3E7BFA;
		background: #EAF1FE;
		padding: 2rpx 10rpx;
		border-radius: 8rpx;
		margin-right: 10rpx;
	}
	.c-date {
		font-size: 20rpx;
		color: #9AA3AF;
	}
	.c-status {
		font-size: 20rpx;
		color: #D97706;
		display: inline-block;
		margin-top: 8rpx;
	}
	.c-btn {
		flex-shrink: 0;
		margin-left: 16rpx;
		padding: 12rpx 28rpx;
		background: #C85B74;
		color: #fff;
		border-radius: 28rpx;
		font-size: 24rpx;
	}
	.log-row {
		display: flex;
		align-items: center;
		justify-content: space-between;
		padding: 16rpx 0;
		border-bottom: 1rpx solid #EFF1F3;
	}
	.log-row:last-child {
		border-bottom: none;
	}
	.l-reason {
		font-size: 27rpx;
		color: #1B1E23;
		display: block;
	}
	.l-time {
		font-size: 20rpx;
		color: #B7BDC5;
		display: block;
		margin-top: 4rpx;
	}
	.l-right {
		text-align: right;
	}
	.l-change {
		font-size: 28rpx;
		font-weight: 600;
	}
	.l-change.plus {
		color: #2F9E63;
	}
	.l-change.minus {
		color: #C2456A;
	}
	.l-balance {
		font-size: 20rpx;
		color: #9AA3AF;
		display: block;
	}
</style>
