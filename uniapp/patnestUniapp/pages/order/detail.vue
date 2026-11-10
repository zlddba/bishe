<template>
	<view class="detail-page patnest-page">
		<!-- 服务端详情暂不可用时的提示（数据来自本地下单快照） -->
		<view v-if="fromCache && !notFound" class="sync-tip">
			<text class="st-text">订单详情暂未与服务端同步，当前显示的是本地下单记录。部分信息（如宠物明细）可能不完整。</text>
		</view>

		<!-- 服务端与本地都取不到该订单 -->
		<view v-if="notFound" class="notfound-box">
			<image class="nf-icon" src="/static/icons/order.png" mode="aspectFit" />
			<text class="nf-title">找不到这笔订单</text>
			<text class="nf-desc">它可能已被删除，或属于另一个账号。回订单列表看看其它订单吧。</text>
			<button class="nf-btn" @click="backToList">返回订单列表</button>
		</view>

		<block v-else>
		<!-- 订单头部 -->
		<view class="head patnest-card">
			<view class="h-top">
				<text class="h-no">{{ o.order_no || ('订单 #' + o.id) }}</text>
				<order-status :status="o.status" />
			</view>
			<text class="h-sitter">{{ o.sitter_name || '寄养机构' }}</text>
			<text class="h-date">{{ dateRange }}（共 {{ totalDays }} 天）</text>
			<view class="h-amount">
				<text class="h-amt-label">订单金额</text>
				<text class="h-amt">¥{{ fmtMoney(o.total_amount) }}</text>
			</view>
			<view class="h-tags">
				<text class="h-pay">支付：{{ payText(o.payment_status) }}</text>
				<text v-if="o.remark" class="h-remark">备注：{{ o.remark }}</text>
			</view>
			<!-- 金额为 0 时的说明（服务端走「无需支付」分支，直接置为已支付） -->
			<view v-if="amountZero" class="amount-warn">
				<text class="aw-text">该订单金额为 0（机构未设置寄养价格），支付后将直接确认订单，不产生支付流水。</text>
			</view>
		</view>

		<!-- 宠物 -->
		<view v-if="orderPets.length" class="section patnest-card">
			<view class="sec-title">寄养宠物（{{ orderPets.length }} 只）</view>
			<view v-for="p in orderPets" :key="p.id" class="pet-row">
				<view class="p-avatar">{{ (p.pet_name || '宠').slice(0, 1) }}</view>
				<view class="p-info">
					<text class="p-name">{{ p.pet_name || ('宠物#' + p.pet_id) }}</text>
					<text v-if="p.stay_date" class="p-sub">入住 {{ p.stay_date }}</text>
				</view>
				<text class="p-status">{{ petStatusText(p.status) }}</text>
			</view>
		</view>

		<!-- 寄养动态 -->
		<view v-if="showDynamics" class="section patnest-card">
			<view class="sec-head">
				<view class="sec-title no-margin">寄养动态</view>
				<text class="more" @click="goMyDynamic">我的动态 ›</text>
			</view>
			<view v-for="d in dynamics" :key="d.id" class="dyn-item">
				<view class="dyn-top">
					<text class="dyn-time">{{ fmtTime(d.created_at) }}</text>
					<text :class="['dyn-like', d.liked ? 'on' : '']" @click="toggleLike(d)">
						{{ d.liked ? '♥' : '♡' }} {{ d.like_count || 0 }}
					</text>
				</view>
				<text class="dyn-content">{{ d.content }}</text>
				<view v-if="d.photos && d.photos.length" class="dyn-photos">
					<image v-for="(ph, i) in d.photos" :key="i" class="dyn-photo" :src="ph" mode="aspectFill" />
				</view>
				<view v-if="d.tags && d.tags.length" class="dyn-tags">
					<text v-for="(t, i) in d.tags" :key="i" class="dyn-tag">{{ t }}</text>
				</view>
				<view class="dyn-comment" @click="goDynamic(d)"><image class="dc-icon" src="/static/icons/message.png" mode="aspectFit" /><text>留言</text></view>
			</view>
			<view v-if="dynamics.length === 0" class="dyn-empty">暂无动态</view>
		</view>

		<!-- 我的评价 -->
		<view v-if="review && review.id" class="section patnest-card">
			<view class="sec-title">我的评价</view>
			<view class="rev-row">
				<text class="rev-dim">综合</text>
				<score-stars :score="review.overall_score" />
			</view>
			<view class="rev-row">
				<text class="rev-dim">服务</text>
				<score-stars :score="review.service_score" />
			</view>
			<view class="rev-row">
				<text class="rev-dim">环境</text>
				<score-stars :score="review.environment_score" />
			</view>
			<view class="rev-row">
				<text class="rev-dim">卫生</text>
				<score-stars :score="review.hygiene_score" />
			</view>
			<text class="rev-comment">{{ review.owner_comment }}</text>
			<text v-if="review.reply_text" class="rev-reply">店家回复：{{ review.reply_text }}</text>

			<!-- 追评（24 小时内） -->
			<view v-if="canFollowup" class="followup">
				<textarea class="fu-input" v-model="followupText" placeholder="补充说明（2-1000 字）" maxlength="1000" />
				<view class="fu-btn" @click="submitFollowup">发表追评</view>
			</view>
			<text v-else-if="review.id" class="fu-tip">评价已超过 24 小时，无法追评</text>
		</view>

		<!-- 退款申请 -->
		<view v-if="refunds.length" class="section patnest-card">
			<view class="sec-title">退款申请</view>
			<view v-for="r in refunds" :key="r.id" class="rf-row">
				<text class="rf-amount">¥{{ fmtMoney(r.amount) }}</text>
				<text class="rf-status" :style="refundStyle(r.status)">{{ refundText(r.status) }}</text>
				<text class="rf-reason">{{ r.reason }}</text>
			</view>
		</view>

		<!-- 操作区 -->
		<view v-if="o.id" class="actions">
			<button v-if="o.status === 'pending_pay'" class="btn pay" @click="openPay">立即支付</button>
			<button v-if="canCancel" class="btn ghost" @click="cancel">取消订单</button>
			<button v-if="canReschedule" class="btn ghost" @click="openReschedule">改期</button>
			<button v-if="canRenew" class="btn ghost" @click="openRenew">续费</button>
			<button v-if="canRefund" class="btn ghost" @click="openRefund">申请退款</button>
			<button v-if="o.status === 'completed' && !review" class="btn pay" @click="goReview">去评价</button>
			<button v-if="review && review.id" class="btn ghost" @click="goReview">查看评价</button>
		</view>

		<!-- 支付方式选择弹层 -->
		<view v-if="showPay" class="mask" @click="showPay = false">
			<view class="sheet" @click.stop>
				<view class="sheet-title">选择支付方式</view>
				<text class="sheet-tip">订单金额 ¥{{ fmtMoney(o.total_amount) }}</text>

				<view
					v-for="m in payMethods"
					:key="m.value"
					:class="['pay-item', payMethod === m.value ? 'on' : '']"
					@click="payMethod = m.value"
				>
					<image class="pi-icon" :src="m.icon" mode="aspectFit" />
					<view class="pi-main">
						<text class="pi-title">{{ m.label }}</text>
						<text class="pi-desc">{{ m.desc }}</text>
					</view>
					<view v-if="payMethod === m.value" class="pi-check">
						<image class="pi-check-icon" src="/static/icons/check.png" mode="aspectFit" />
					</view>
				</view>

				<view class="sheet-actions">
					<view class="sheet-btn cancel" @click="showPay = false">取消</view>
					<view class="sheet-btn ok" :class="{ disabled: paying }" @click="doPay">
						{{ paying ? '支付中...' : '确认支付' }}
					</view>
				</view>
			</view>
		</view>

		<!-- 改期弹层 -->
		<view v-if="showReschedule" class="mask" @click="showReschedule = false">
			<view class="sheet" @click.stop>
				<view class="sheet-title">改期</view>
				<text class="sheet-tip">改期后金额高于原订单时，需先联系平台补差价。</text>
				<view class="sheet-row">
					<text class="sr-label">开始</text>
					<picker mode="date" :value="newStart" :start="today" @change="onNewStart">
						<view class="sr-value">{{ newStart }}</view>
					</picker>
				</view>
				<view class="sheet-row">
					<text class="sr-label">结束</text>
					<picker mode="date" :value="newEnd" :start="newStart" @change="newEnd = $event.detail.value">
						<view class="sr-value">{{ newEnd }}</view>
					</picker>
				</view>
				<!-- 改期费用明细：把每天多少钱、总额变化讲清楚 -->
				<view class="fee-box">
					<view class="fb-row">
						<text>机构日单价</text>
						<text class="fb-val">{{ dailyPrice > 0 ? ('¥' + fmtMoney(dailyPrice) + ' / 天') : '未获取到' }}</text>
					</view>
					<view class="fb-row">
						<text>寄养天数</text>
						<text class="fb-val">{{ rescheduleDays }} 天</text>
					</view>
					<view class="fb-row">
						<text>改期后预计金额</text>
						<text class="fb-val strong">¥{{ fmtMoney(rescheduleAmount) }}</text>
					</view>
					<view class="fb-row">
						<text>原订单金额</text>
						<text class="fb-val">¥{{ fmtMoney(o.total_amount) }}</text>
					</view>
					<view v-if="rescheduleDiff !== 0" class="fb-row diff">
						<text>{{ rescheduleDiff > 0 ? '需补差价' : '可退差额' }}</text>
						<text class="fb-val">{{ rescheduleDiff > 0 ? '+' : '' }}¥{{ fmtMoney(rescheduleDiff) }}</text>
					</view>
				</view>
				<view class="sheet-actions">
					<view class="sheet-btn cancel" @click="showReschedule = false">取消</view>
					<view class="sheet-btn ok" :class="{ disabled: rescheduling }" @click="doReschedule">
						{{ rescheduling ? '提交中...' : '确定改期' }}
					</view>
				</view>
			</view>
		</view>

		<!-- 续费弹层 -->
		<view v-if="showRenew" class="mask" @click="showRenew = false">
			<view class="sheet" @click.stop>
				<view class="sheet-title">续费</view>
				<text class="sheet-tip">只对新增的那段时间计费（原订单的加购服务、优惠券、积分抵扣保持不变），提交后立即收取差价。</text>
				<view class="sheet-row">
					<text class="sr-label">新的结束日期</text>
					<picker mode="date" :value="newEndDate" :start="o.end_date" @change="newEndDate = $event.detail.value">
						<view class="sr-value">{{ newEndDate }}</view>
					</picker>
				</view>
				<!-- 续费费用明细 -->
				<view class="fee-box">
					<view class="fb-row">
						<text>机构日单价</text>
						<text class="fb-val">{{ dailyPrice > 0 ? ('¥' + fmtMoney(dailyPrice) + ' / 天') : '未获取到' }}</text>
					</view>
					<view class="fb-row">
						<text>延长天数</text>
						<text class="fb-val">{{ renewDays }} 天</text>
					</view>
					<view class="fb-row">
						<text>需支付续费</text>
						<text class="fb-val strong">¥{{ fmtMoney(renewAmount) }}</text>
					</view>
					<view class="fb-row">
						<text>续费后总额</text>
						<text class="fb-val">¥{{ fmtMoney(Number(o.total_amount || 0) + renewAmount) }}</text>
					</view>
				</view>
				<view class="sheet-actions">
					<view class="sheet-btn cancel" @click="showRenew = false">取消</view>
					<view class="sheet-btn ok" :class="{ disabled: renewing }" @click="doRenew">
						{{ renewing ? '提交中...' : '确定续费' }}
					</view>
				</view>
			</view>
		</view>
		</block>

		<view class="patnest-safe-bottom" />
	</view>
</template>

<script>
	import { orderApi, dynamicApi, reviewApi, sitterApi, moneyApi } from '../../utils/api.js'
	import { ORDER_PET_STATUS_TEXT, REFUND_STATUS_TEXT, REFUND_STATUS_THEME } from '../../config.js'
	import { fmtMoney, fmtDateTime, diffDays, fromNow, today, addDays } from '../../utils/format.js'
	import { getOrder, saveOrder, patchOrder } from '../../utils/orderCache.js'

	export default {
		data() {
			return {
				id: null,
				o: {},
				today: '',
				orderPets: [],
				dynamics: [],
				review: null,
				refunds: [],
				followupText: '',
				// 当前展示的是本地快照（服务端详情接口不可用）
				fromCache: false,
				// 服务端与本地都取不到该订单
				notFound: false,
				// 机构信息（改期/续费算费用需要日单价）
				sitter: null,
				// 支付方式弹层
				showPay: false,
				paying: false,
				payMethod: 'mock',
				moneyBalance: 0,
				// 改期弹层
				showReschedule: false,
				rescheduling: false,
				newStart: '',
				newEnd: '',
				// 续费弹层
				showRenew: false,
				renewing: false,
				newEndDate: ''
			}
		},
		computed: {
			totalDays() {
				// 服务端有 total_days 时优先用它，否则按起止日期算
				if (this.o.total_days) return this.o.total_days
				return diffDays(this.o.start_date, this.o.end_date)
			},
			dateRange() {
				if (!this.o.start_date || !this.o.end_date) return '待确认寄养日期'
				return this.o.start_date + ' ~ ' + this.o.end_date
			},
			amountZero() {
				return this.o.payment_status !== 'paid' && Number(this.o.total_amount) === 0
			},
			// 机构日单价（改期/续费算费用用）
			dailyPrice() {
				if (this.sitter && this.sitter.daily_price != null) {
					return Number(this.sitter.daily_price) || 0
				}
				return 0
			},
			// 改期后的寄养天数
			rescheduleDays() {
				if (!this.newStart || !this.newEnd) return 0
				return Math.max(0, diffDays(this.newStart, this.newEnd))
			},
			// 改期后预计金额（按日单价 × 天数 × 宠物数）
			rescheduleAmount() {
				const pets = Number(this.o.pet_count) || 1
				return this.dailyPrice * this.rescheduleDays * pets
			},
			// 与原订单的差额：正数=需补差价，负数=可退差额
			rescheduleDiff() {
				return Number((this.rescheduleAmount - Number(this.o.total_amount || 0)).toFixed(2))
			},
			// 续费延长天数
			renewDays() {
				if (!this.newEndDate || !this.o.end_date) return 0
				return Math.max(0, diffDays(this.o.end_date, this.newEndDate))
			},
			// 续费需付金额
			renewAmount() {
				const pets = Number(this.o.pet_count) || 1
				return this.dailyPrice * this.renewDays * pets
			},
			// 可选支付方式
			payMethods() {
				const need = Number(this.o.total_amount) || 0
				const list = [
					{
						value: 'mock',
						label: '模拟支付',
						desc: '演示环境直接标记为已支付，不产生真实扣款',
						icon: '/static/icons/wallet.png'
					}
				]
				if (need > 0) {
					list.unshift({
						value: 'balance',
						label: '钱包余额支付',
						desc: this.moneyBalance >= need
							? ('余额 ¥' + fmtMoney(this.moneyBalance) + '，支付后剩余 ¥' + fmtMoney(this.moneyBalance - need))
							: ('余额 ¥' + fmtMoney(this.moneyBalance) + '，还差 ¥' + fmtMoney(need - this.moneyBalance)),
						icon: '/static/icons/coupon.png'
					})
				}
				return list
			},
			showDynamics() {
				return ['accepted', 'staying', 'completed', 'evaluated'].indexOf(this.o.status) >= 0
			},			canCancel() {
				return ['pending_pay', 'confirmed', 'accepted'].indexOf(this.o.status) >= 0
			},
			canReschedule() {
				// 后端要求：状态 ∈ {pending_pay, confirmed, accepted} 且订单尚未开始
				return ['pending_pay', 'confirmed', 'accepted'].indexOf(this.o.status) >= 0
			},
			canRenew() {
				// 后端要求：payment_status=paid 且 status ∈ {completed, cancelled, pending_pay}
				return this.o.payment_status === 'paid' && this.o.status === 'completed'
			},
			canRefund() {
				return this.o.payment_status === 'paid'
			},
			canFollowup() {
				if (!this.review || !this.review.created_at) return false
				const t = new Date(String(this.review.created_at).replace(' ', 'T')).getTime()
				if (isNaN(t)) return false
				return Date.now() - t <= 24 * 3600 * 1000
			}
		},
		onLoad(options) {
			this.id = options.id
			this.today = today()
			this.load()
		},
		onShow() {
			if (this.id) this.load()
		},
		methods: {
			async load() {
				// 先用本地快照兜底渲染（服务端订单详情接口对 sitter 角色账号会 404，
				// 见 docs/11-后端问题与需求清单.md 的 B-6），
				// 再尝试用服务端数据覆盖，避免详情页一片空白。
				const cached = getOrder(this.id)
				if (cached) {
					this.o = Object.assign({}, cached)
					this.orderPets = cached.pets || []
					this.fromCache = true
				}
				try {
					// 详情请求一律静默：服务端取不到时页面继续用快照显示，
					// 不弹「订单不存在或已删除」这种误导性提示
					const res = await orderApi.detail(this.id, true)
					if (res && res.order) {
						this.o = Object.assign({}, this.o, res.order)
						// 后端详情子表键名为 pets（不是 order_pet）
						this.orderPets = (res && res.pets) || this.orderPets
						this.fromCache = false
						saveOrder(Object.assign({}, this.o, { pets: this.orderPets }))
					} else if (!cached) {
						// 既没有快照、服务端也没返回数据：明确提示而不是空白页
						this.notFound = true
					}
				} catch (e) {
					// 服务端详情不可用时保留本地快照（fromCache 保持 true）
					if (!cached) this.notFound = true
				}
				// 评价与动态在后端详情里不会返回，需要单独拉取
				try {
					const r = await reviewApi.get(this.id)
					this.review = r && r.id ? r : null
				} catch (e) {
					// 未评价的订单返回 30001，属于正常状态（reviewApi.get 内部已静默）
					this.review = null
				}
				if (this.showDynamics) this.loadDynamics()
				try {
					const res = await orderApi.refunds()
					const items = (res && res.items) || []
					this.refunds = items.filter((x) => String(x.order_id) === String(this.id))
				} catch (e) {}
			},
			async loadDynamics() {
				try {
					const res = await dynamicApi.list(this.id, { per_page: 30 })
					this.dynamics = (res && res.items) || []
				} catch (e) {}
			},
			payText(s) {
				return { none: '未支付', paid: '已支付', refunded: '已退款' }[s] || s || '—'
			},
			petStatusText(s) {
				return ORDER_PET_STATUS_TEXT[s] || ''
			},
			refundText(s) {
				return REFUND_STATUS_TEXT[s] || s
			},
			refundStyle(s) {
				const c = REFUND_STATUS_THEME[s] || { fg: '#5C6470', bg: '#EFF1F3' }
				return 'color:' + c.fg + ';background:' + c.bg
			},
			async pay() {
				// 金额为 0 时服务端走「无需支付」分支（直接置为已支付，不写流水），
				// 所以这里不再拦截，只有服务端真失败时才提示。
				try {
					await orderApi.pay(this.id)
					// 支付成功后同步本地快照，避免详情页退回旧状态
					patchOrder(this.id, { status: 'confirmed', payment_status: 'paid' })
					this.o = Object.assign({}, this.o, { status: 'confirmed', payment_status: 'paid' })
					uni.showToast({ title: '支付成功', icon: 'success' })
					this.load()
				} catch (e) {
					if (e && e.code === 50001) {
						uni.showModal({
							title: '支付失败',
							content: '服务端处理支付时出错（50001）。若订单金额为 0，多半是机构未设置价格导致支付流水写入被拒，请联系机构或平台处理。',
							showCancel: false,
							confirmColor: '#C85B74'
						})
					}
				}
			},
			// 打开支付方式选择
			async openPay() {
				this.payMethod = 'mock'
				this.showPay = true
				// 拉一下钱包余额，用于展示余额支付是否够用
				try {
					const w = await moneyApi.overview()
					if (w) this.moneyBalance = Number(w.balance) || 0
				} catch (e) {}
			},
			// 确认支付
			async doPay() {
				if (this.paying) return
				if (this.payMethod === 'balance') {
					const need = Number(this.o.total_amount) || 0
					if (this.moneyBalance < need) {
						return uni.showModal({
							title: '余额不足',
							content: '钱包余额 ¥' + fmtMoney(this.moneyBalance) + '，还差 ¥' + fmtMoney(need - this.moneyBalance) + '。请先充值或改用其它支付方式。',
							confirmText: '去充值',
							cancelText: '换个方式',
							confirmColor: '#C85B74',
							success: (r) => {
								if (r.confirm) {
									this.showPay = false
									uni.navigateTo({ url: '/pages/wallet/index' })
								}
							}
						})
					}
				}
				this.paying = true
				try {
					await orderApi.pay(this.id, this.payMethod)
					this.showPay = false
					patchOrder(this.id, { status: 'confirmed', payment_status: 'paid' })
					this.o = Object.assign({}, this.o, { status: 'confirmed', payment_status: 'paid' })
					uni.showToast({ title: '支付成功', icon: 'success' })
					this.load()
				} catch (e) {
					if (e && e.code === 30002) {
						uni.showModal({
							title: '支付失败',
							content: String(e.message || '余额不足'),
							showCancel: false,
							confirmColor: '#C85B74'
						})
					} else if (e && e.code === 50001) {
						uni.showModal({
							title: '支付失败',
							content: '服务端处理支付时出错（50001），请稍后重试或联系机构。',
							showCancel: false,
							confirmColor: '#C85B74'
						})
					}
				} finally {
					this.paying = false
				}
			},
			cancel() {
				uni.showModal({
					title: '取消订单',
					content: '取消后系统将释放已占用的床位，确定取消？',
					confirmColor: '#C85B74',
					success: async (r) => {
						if (!r.confirm) return
						try {
							await orderApi.cancel(this.id)
							patchOrder(this.id, { status: 'cancelled' })
							this.o = Object.assign({}, this.o, { status: 'cancelled' })
							uni.showToast({ title: '已取消', icon: 'success' })
							this.load()
						} catch (e) {}
					}
				})
			},
			openReschedule() {
				this.newStart = this.o.start_date
				this.newEnd = this.o.end_date
				this.showReschedule = true
				this.loadSitter()
			},
			// 改期开始日期变更时，若结束日期不晚于新开始日期，自动顺延
			onNewStart(e) {
				this.newStart = e.detail.value
				if (this.newEnd <= this.newStart) {
					this.newEnd = addDays(this.newStart, 1)
				}
			},
			// 拉取机构信息，拿到日单价用于算改期/续费费用
			async loadSitter() {
				const sid = this.o.sitter_id
				if (!sid || this.sitter) return
				try {
					this.sitter = await sitterApi.detail(sid)
				} catch (e) {
					this.sitter = null
				}
			},
			async doReschedule() {
				if (!this.newStart || !this.newEnd || this.newEnd <= this.newStart) {
					return uni.showToast({ title: '结束日期需晚于开始日期', icon: 'none' })
				}
				this.rescheduling = true
				try {
					const res = await orderApi.reschedule(this.id, { start_date: this.newStart, end_date: this.newEnd })
					this.showReschedule = false
					// 改期接口会返回新的日期与金额，同步到本地快照
					const patch = { start_date: this.newStart, end_date: this.newEnd }
					if (res && res.total_amount != null) patch.total_amount = res.total_amount
					if (res && res.total_days != null) patch.total_days = res.total_days
					patchOrder(this.id, patch)
					this.o = Object.assign({}, this.o, patch)
					uni.showToast({ title: '改期成功', icon: 'success' })
					this.load()
				} catch (e) {
					// 后端 404 说明这笔单当前账号查不到（归属判断口径问题，见 docs/11 的 B-6）
					if (e && (e.code === 30001 || e.code === 404)) {
						uni.showModal({
							title: '改期失败',
							content: '服务端查不到这笔订单。它可能已被删除，或当前账号对这笔订单没有操作权限。',
							showCancel: false,
							confirmColor: '#C85B74'
						})
					}
				} finally {
					this.rescheduling = false
				}
			},
			openRenew() {
				this.newEndDate = this.o.end_date
				this.showRenew = true
				this.loadSitter()
			},
			async doRenew() {
				if (!this.newEndDate || this.newEndDate <= this.o.end_date) {
					return uni.showToast({ title: '续费日期需晚于原结束日期', icon: 'none' })
				}
				this.renewing = true
				try {
					const res = await orderApi.renew(this.id, { end_date: this.newEndDate })
					this.showRenew = false
					// 服务端返回实际差价与新总额（按新增服务段计费，含节假日上浮）
					const patch = {}
					if (res && res.end_date) patch.end_date = res.end_date
					if (res && res.total_days != null) patch.total_days = res.total_days
					if (res && res.total_amount != null) patch.total_amount = res.total_amount
					patchOrder(this.id, patch)
					this.o = Object.assign({}, this.o, patch)
					const extra = res && res.extra_amount != null ? ('，补差价 ¥' + fmtMoney(res.extra_amount)) : ''
					uni.showToast({ title: '续费成功' + extra, icon: 'success' })
					this.load()
				} catch (e) {
					if (e && (e.code === 30001 || e.code === 404)) {
						uni.showModal({
							title: '续费失败',
							content: '服务端查不到这笔订单。它可能已被删除，或当前账号对这笔订单没有操作权限。',
							showCancel: false,
							confirmColor: '#C85B74'
						})
					}
				} finally {
					this.renewing = false
				}
			},
			openRefund() {
				uni.showModal({
					title: '申请退款',
					editable: true,
					placeholderText: '请说明退款原因（2-500 字）',
					success: async (r) => {
						if (!r.confirm) return
						const reason = (r.content || '').trim()
						if (reason.length < 2) {
							return uni.showToast({ title: '请填写退款原因', icon: 'none' })
						}
						try {
							await orderApi.refundRequest(this.id, { reason })
							uni.showToast({ title: '已提交，等待审核', icon: 'none' })
							this.load()
						} catch (e) {}
					}
				})
			},
			goReview() {
				uni.navigateTo({ url: '/pages/order/review?id=' + this.id })
			},
			backToList() {
				uni.reLaunch({ url: '/pages/order/list' })
			},
			goDynamic(d) {
				uni.navigateTo({ url: '/pages/dynamic/detail?id=' + d.id + '&order_id=' + this.id })
			},
			goMyDynamic() {
				uni.navigateTo({ url: '/pages/dynamic/list' })
			},
			async toggleLike(d) {
				try {
					if (d.liked) {
						await dynamicApi.unlike(d.id)
						d.liked = false
						d.like_count = Math.max(0, (d.like_count || 1) - 1)
					} else {
						await dynamicApi.like(d.id)
						d.liked = true
						d.like_count = (d.like_count || 0) + 1
					}
					this.dynamics = this.dynamics.slice()
				} catch (e) {}
			},
			async submitFollowup() {
				const content = this.followupText.trim()
				if (content.length < 2) {
					return uni.showToast({ title: '追评内容至少 2 个字', icon: 'none' })
				}
				try {
					await reviewApi.followup(this.review.id, content)
					this.followupText = ''
					uni.showToast({ title: '追评成功', icon: 'success' })
				} catch (e) {}
			},
			fmtMoney,
			fmtTime(s) {
				return fmtDateTime(s) || fromNow(s)
			}
		}
	}
</script>

<style scoped>
	.detail-page {
		padding: 24rpx;
	}
	.sync-tip {
		background: #FDF3E3;
		border-radius: 16rpx;
		padding: 20rpx 24rpx;
		margin-bottom: 20rpx;
	}
	.st-text {
		font-size: 24rpx;
		color: #D97706;
		line-height: 1.6;
	}
	/* 找不到订单时的空态 */
	.notfound-box {
		display: flex;
		flex-direction: column;
		align-items: center;
		padding: 120rpx 40rpx 60rpx;
	}
	.nf-icon {
		width: 96rpx;
		height: 96rpx;
		opacity: 0.35;
		margin-bottom: 24rpx;
	}
	.nf-title {
		font-size: 32rpx;
		font-weight: 700;
		color: #1B1E23;
		margin-bottom: 12rpx;
	}
	.nf-desc {
		font-size: 25rpx;
		color: #9AA3AF;
		line-height: 1.6;
		text-align: center;
		margin-bottom: 36rpx;
	}
	.nf-btn {
		height: 80rpx;
		line-height: 80rpx;
		padding: 0 60rpx;
		border-radius: 40rpx;
		background: #C85B74;
		color: #fff;
		font-size: 28rpx;
	}
	.nf-btn::after {
		border: none;
	}
	.amount-warn {
		margin-top: 16rpx;
		padding: 16rpx 18rpx;
		background: #FAE7EC;
		border-radius: 12rpx;
	}
	.aw-text {
		font-size: 22rpx;
		color: #C2456A;
		line-height: 1.5;
	}
	.head {
		padding: 28rpx;
		margin-bottom: 24rpx;
	}
	.h-top {
		display: flex;
		justify-content: space-between;
		align-items: center;
	}
	.h-no {
		font-size: 26rpx;
		color: #9AA3AF;
	}
	.h-sitter {
		display: block;
		font-size: 34rpx;
		font-weight: 700;
		color: #1B1E23;
		margin-top: 12rpx;
	}
	.h-date {
		font-size: 24rpx;
		color: #9AA3AF;
		margin-top: 6rpx;
		display: block;
	}
	.h-amount {
		display: flex;
		align-items: baseline;
		justify-content: space-between;
		margin-top: 20rpx;
		padding-top: 20rpx;
		border-top: 1rpx solid #EFF1F3;
	}
	.h-amt-label {
		font-size: 24rpx;
		color: #9AA3AF;
	}
	.h-amt {
		font-size: 40rpx;
		font-weight: 700;
		color: #C2456A;
	}
	.h-tags {
		display: flex;
		flex-wrap: wrap;
		margin-top: 12rpx;
	}
	.h-pay,
	.h-remark {
		font-size: 24rpx;
		color: #9AA3AF;
		margin-right: 24rpx;
	}
	.section {
		padding: 28rpx;
		margin-bottom: 24rpx;
	}
	.sec-title {
		font-size: 30rpx;
		font-weight: 600;
		margin-bottom: 16rpx;
		color: #1B1E23;
	}
	.sec-title.no-margin {
		margin-bottom: 0;
	}
	.sec-head {
		display: flex;
		align-items: center;
		justify-content: space-between;
		margin-bottom: 16rpx;
	}
	.more {
		font-size: 24rpx;
		color: #C85B74;
	}
	.pet-row {
		display: flex;
		align-items: center;
		padding: 12rpx 0;
	}
	.p-avatar {
		width: 80rpx;
		height: 80rpx;
		border-radius: 16rpx;
		background: #FDF1F4;
		color: #AF4A62;
		font-size: 32rpx;
		font-weight: 600;
		display: flex;
		align-items: center;
		justify-content: center;
		flex-shrink: 0;
	}
	.p-info {
		margin-left: 20rpx;
		flex: 1;
	}
	.p-name {
		font-size: 28rpx;
		color: #1B1E23;
		display: block;
	}
	.p-sub {
		font-size: 22rpx;
		color: #9AA3AF;
		margin-top: 4rpx;
		display: block;
	}
	.p-status {
		font-size: 22rpx;
		color: #9AA3AF;
	}
	.dyn-item {
		padding: 16rpx 0;
		border-bottom: 1rpx solid #EFF1F3;
		position: relative;
	}
	.dyn-top {
		display: flex;
		justify-content: space-between;
		align-items: center;
	}
	.dyn-time {
		font-size: 22rpx;
		color: #B7BDC5;
	}
	.dyn-like {
		font-size: 24rpx;
		color: #B7BDC5;
	}
	.dyn-like.on {
		color: #C2456A;
	}
	.dyn-content {
		font-size: 28rpx;
		color: #1B1E23;
		margin-top: 8rpx;
		display: block;
		line-height: 1.6;
	}
	.dyn-photos {
		display: flex;
		flex-wrap: wrap;
		margin-top: 12rpx;
	}
	.dyn-photo {
		width: 160rpx;
		height: 160rpx;
		border-radius: 12rpx;
		margin: 0 12rpx 12rpx 0;
		background: #EFF1F3;
	}
	.dyn-tags {
		margin-top: 8rpx;
	}
	.dyn-tag {
		font-size: 22rpx;
		color: #3E7BFA;
		background: #EAF1FE;
		padding: 4rpx 14rpx;
		border-radius: 8rpx;
		margin-right: 10rpx;
	}
	.dyn-comment {
		display: inline-block;
		font-size: 22rpx;
		color: #9AA3AF;
		margin-top: 10rpx;
	}
	.dyn-empty {
		font-size: 26rpx;
		color: #B7BDC5;
		padding: 20rpx 0;
	}
	.rev-row {
		display: flex;
		align-items: center;
		margin-bottom: 8rpx;
	}
	.rev-dim {
		font-size: 26rpx;
		color: #5C6470;
		width: 80rpx;
	}
	.rev-comment {
		display: block;
		font-size: 28rpx;
		color: #1B1E23;
		margin-top: 12rpx;
		line-height: 1.6;
	}
	.rev-reply {
		display: block;
		font-size: 24rpx;
		color: #9AA3AF;
		margin-top: 10rpx;
	}
	.followup {
		margin-top: 20rpx;
		padding-top: 20rpx;
		border-top: 1rpx solid #EFF1F3;
	}
	.fu-input {
		width: 100%;
		height: 130rpx;
		background: #F6F7F8;
		border-radius: 12rpx;
		padding: 16rpx;
		font-size: 26rpx;
		box-sizing: border-box;
	}
	.fu-btn {
		margin-top: 16rpx;
		text-align: center;
		background: #FDF1F4;
		color: #AF4A62;
		padding: 18rpx;
		border-radius: 40rpx;
		font-size: 26rpx;
	}
	.fu-tip {
		display: block;
		font-size: 22rpx;
		color: #B7BDC5;
		margin-top: 16rpx;
	}
	.rf-row {
		display: flex;
		align-items: center;
		flex-wrap: wrap;
		padding: 12rpx 0;
	}
	.rf-amount {
		font-size: 28rpx;
		font-weight: 600;
		color: #1B1E23;
	}
	.rf-status {
		font-size: 22rpx;
		padding: 2rpx 12rpx;
		border-radius: 8rpx;
		margin-left: 16rpx;
	}
	.rf-reason {
		width: 100%;
		font-size: 24rpx;
		color: #9AA3AF;
		margin-top: 8rpx;
	}
	.actions {
		display: flex;
		flex-wrap: wrap;
		gap: 16rpx;
		padding: 0 8rpx;
	}
	.btn {
		flex: 1;
		min-width: 200rpx;
		height: 84rpx;
		line-height: 84rpx;
		border-radius: 44rpx;
		font-size: 28rpx;
		margin: 0;
	}
	.btn.pay {
		background: #C85B74;
		color: #fff;
	}
	.btn.ghost {
		background: #fff;
		color: #5C6470;
		border: 2rpx solid #E7E9EC;
	}
	.mask {
		position: fixed;
		left: 0;
		right: 0;
		top: 0;
		bottom: 0;
		background: rgba(27, 30, 35, 0.45);
		display: flex;
		align-items: flex-end;
		z-index: 100;
	}
	.sheet {
		width: 100%;
		background: #fff;
		border-radius: 28rpx 28rpx 0 0;
		padding: 32rpx 32rpx calc(32rpx + env(safe-area-inset-bottom));
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
	}
	.sheet-row {
		display: flex;
		align-items: center;
		justify-content: space-between;
		padding: 20rpx 0;
		border-bottom: 1rpx solid #EFF1F3;
	}
	/* 改期/续费费用明细 */
	.fee-box {
		margin-top: 16rpx;
		padding: 18rpx 20rpx;
		background: #F8F9FA;
		border-radius: 14rpx;
	}
	.fb-row {
		display: flex;
		align-items: center;
		justify-content: space-between;
		padding: 8rpx 0;
		font-size: 24rpx;
		color: #5C6470;
	}
	.fb-val {
		color: #1B1E23;
		font-weight: 600;
	}
	.fb-val.strong {
		color: #C2456A;
		font-size: 28rpx;
	}
	.fb-row.diff .fb-val {
		color: #D97706;
		font-size: 26rpx;
	}
	.sr-label {
		font-size: 27rpx;
		color: #5C6470;
	}
	.sr-value {
		font-size: 28rpx;
		color: #AF4A62;
		font-weight: 600;
	}
	.sheet-actions {
		display: flex;
		margin-top: 28rpx;
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
	/* 支付方式选择 */
	.pay-item {
		display: flex;
		align-items: center;
		padding: 22rpx 20rpx;
		margin-top: 16rpx;
		background: #F8F9FA;
		border: 2rpx solid #ECEEF0;
		border-radius: 14rpx;
	}
	.pay-item.on {
		background: #FDF1F4;
		border-color: #C85B74;
	}
	.pi-icon {
		width: 44rpx;
		height: 44rpx;
		margin-right: 18rpx;
		flex-shrink: 0;
	}
	.pi-main {
		flex: 1;
		min-width: 0;
	}
	.pi-title {
		display: block;
		font-size: 28rpx;
		font-weight: 600;
		color: #1B1E23;
	}
	.pi-desc {
		display: block;
		font-size: 21rpx;
		color: #9AA3AF;
		margin-top: 6rpx;
		line-height: 1.4;
	}
	.pi-check {
		flex-shrink: 0;
		margin-left: 12rpx;
	}
	.pi-check-icon {
		width: 36rpx;
		height: 36rpx;
	}
	.dc-icon {
		width: 32rpx;
		height: 32rpx;
		margin-right: 6rpx;
		flex-shrink: 0;
	}
</style>
