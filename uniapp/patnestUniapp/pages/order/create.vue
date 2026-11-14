<template>
	<view class="create-page patnest-page">
		<!-- 首屏加载遮罩：数据还在拉的时候给明确反馈，避免用户以为页面卡死 -->
		<view v-if="firstLoading" class="loading-mask">
			<view class="lm-box">
				<view class="lm-spinner" />
				<text class="lm-text">{{ loadingText }}</text>
			</view>
		</view>

		<!-- 机构 -->
		<view class="block patnest-card">
			<view class="b-title">寄养机构</view>
			<view v-if="sitter.id" class="sitter-line" @click="chooseSitter">
				<view class="s-info">
					<text class="s-name">{{ sitter.name }}</text>
					<text class="s-type">{{ typeText }}</text>
				</view>
				<text class="s-arrow">更换 ›</text>
			</view>
			<view v-else class="sitter-picker" @click="chooseSitter">
				<text>请选择机构</text>
				<text class="arrow">›</text>
			</view>
		</view>

		<!-- 宠物选择 -->
		<view class="block patnest-card">
			<view class="b-title">
				<text>选择宠物</text>
				<text class="link" @click="goPetManage">管理宠物 ›</text>
			</view>
			<view v-if="pets.length === 0" class="empty-hint" @click="goPetManage">
				还没有宠物档案，去添加 ›
			</view>
			<pet-card
				v-for="p in pets"
				:key="p.id"
				:pet="p"
				selectable
				:selected="selectedPets.indexOf(p.id) >= 0"
				@select="togglePet"
			/>
		</view>

		<!-- 日期冲突提示（提交前预检） -->
		<view v-if="conflicts.length" class="block conflict-box">
			<view class="cb-title">⚠️ 以下宠物在所选日期已被预订</view>
			<view v-for="c in conflicts" :key="c.petId" class="cb-row">
				<text class="cb-pet">{{ petName(c.petId) }}</text>
				<text class="cb-range">{{ c.stay.start_date }} ~ {{ c.stay.end_date }}</text>
				<text class="cb-order" @click="openOrder(c.stay)">{{ c.stay.order_no || ('订单 #' + c.stay.order_id) }}</text>
			</view>
			<text class="cb-tip">请调整寄养日期，或换一只未在该时段寄养过的宠物。</text>
		</view>

		<!-- 日期 -->
		<view class="block patnest-card">
			<view class="b-title">寄养时间（含首不含尾）</view>
			<view class="date-row">
				<picker mode="date" :value="startDate" :start="today" @change="onStart">
					<view class="date-cell">
						<text class="d-label">开始</text>
						<text class="d-val">{{ startDate }}</text>
					</view>
				</picker>
				<text class="date-sep">→</text>
				<picker mode="date" :value="endDate" :start="startDate" @change="onEnd">
					<view class="date-cell">
						<text class="d-label">结束</text>
						<text class="d-val">{{ endDate }}</text>
					</view>
				</picker>
			</view>
			<text class="day-hint">共 {{ est.days }} 天</text>

			<!-- 容量提示 -->
			<view v-if="capacityChecked" class="cap-warn" :class="fullDays.length ? 'bad' : 'good'">
				<text v-if="fullDays.length">以下日期已满员：{{ fullDays.join('、') }}，建议调整</text>
				<text v-else>所选日期床位充足</text>
			</view>
		</view>

		<!-- 加购服务 -->
		<view v-if="addons.length" class="block patnest-card">
			<view class="b-title">加购服务（可选）</view>
			<view v-for="a in addons" :key="a.id" class="addon-row">
				<view class="a-info">
					<text class="a-name">{{ a.name }}</text>
					<text class="a-price">¥{{ a.price }}/{{ unitText(a.unit) }}</text>
				</view>
				<view class="stepper">
					<text class="step-btn" @click="decAddon(a)">−</text>
					<text class="step-num">{{ addonQty[a.id] || 0 }}</text>
					<text class="step-btn" @click="incAddon(a)">＋</text>
				</view>
			</view>
		</view>

		<!-- 优惠券 -->
		<view class="block patnest-card">
			<view class="b-title">
				<text>优惠券</text>
				<text v-if="usableCoupons.length" class="b-extra">{{ usableCoupons.length }} 张可用</text>			</view>

			<!-- 已领取的券：直接平铺出来，让用户看得见有什么券 -->
			<view v-if="usableCoupons.length || unusableCoupons.length" class="coupon-list">
				<view
					v-for="c in usableCoupons"
					:key="'u' + c.id"
					:class="['coupon-item', selectedCoupon && selectedCoupon.id === c.id ? 'on' : '']"
					@click="pickCoupon(c)"
				>
					<view class="ci-left">
						<text class="ci-value">{{ couponValueText(c) }}</text>
						<text class="ci-cond">{{ couponCondText(c) }}</text>
					</view>
					<view class="ci-right">
						<text class="ci-title">{{ c.title }}</text>
						<text class="ci-date">{{ c.ends_at ? ('有效期至 ' + c.ends_at) : '长期有效' }}</text>
					</view>
					<view v-if="selectedCoupon && selectedCoupon.id === c.id" class="ci-check">
						<image class="ci-check-icon" src="/static/icons/check.png" mode="aspectFit" />
					</view>
				</view>

				<!-- 其它机构专属券：置灰显示并说明原因，避免用户点了才知道不能用 -->
				<view v-for="c in unusableCoupons" :key="'x' + c.id" class="coupon-item disabled">
					<view class="ci-left">
						<text class="ci-value">{{ couponValueText(c) }}</text>
						<text class="ci-cond">{{ couponCondText(c) }}</text>
					</view>
					<view class="ci-right">
						<text class="ci-title">{{ c.title }}</text>
						<text class="ci-date">{{ c.issuer ? (c.issuer + ' 专属，本单不可用') : '本单不可用' }}</text>
					</view>
				</view>
			</view>

			<!-- 还没领的券：提示可领，点一下先领取 -->
			<view v-if="claimableCoupons.length" class="coupon-list">
				<text class="cl-title">可领取</text>
				<view
					v-for="c in claimableCoupons"
					:key="'c' + c.coupon_id"
					:class="['coupon-item', 'claimable', claiming === c.coupon_id ? 'claiming' : '']"
					@click="claimAndPick(c)"
				>
					<view class="ci-left">
						<text class="ci-value">{{ couponValueText(c) }}</text>
						<text class="ci-cond">{{ couponCondText(c) }}</text>
					</view>
					<view class="ci-right">
						<text class="ci-title">{{ c.title }}</text>
						<text class="ci-date">{{ c.issuer ? (c.issuer + ' 专属') : '平台券' }} · 点击领取</text>
					</view>
					<text class="ci-claim">{{ claiming === c.coupon_id ? '领取中' : '领取' }}</text>
				</view>
			</view>

			<!-- 选中的券摘要行 -->
			<view v-if="selectedCoupon" class="picker-row selected-row">
				<text class="p-text on">已选：{{ selectedCoupon.title }}（{{ couponValueText(selectedCoupon) }}）</text>
				<text class="p-clear" @click="clearCoupon">不使用</text>
			</view>
			<view v-else-if="!usableCoupons.length && !claimableCoupons.length" class="picker-row">
				<text class="p-text muted">暂无可用优惠券</text>
			</view>

			<text v-if="selectedCoupon && !est.couponUsable" class="fee-note warn">{{ est.couponHint }}</text>
		</view>

		<!-- 积分抵扣 -->
		<view class="block patnest-card">
			<view class="b-title">
				<text>积分抵扣</text>
				<text class="points-balance">可用 {{ pointsBalance }} 分（100 分 = 1 元{{ pointsHint }}）</text>
			</view>
			<view class="points-row">
				<input
					class="points-input"
					type="number"
					v-model="pointsInput"
					placeholder="0"
					@input="onPointsInput"
				/>
				<text class="points-unit">分</text>
				<text class="points-max" @click="useMaxPoints">全部使用</text>
			</view>
		</view>

		<!-- 备注 -->
		<view class="block patnest-card">
			<view class="b-title">备注（可选）</view>
			<textarea class="textarea" v-model="remark" placeholder="如：小狗有点挑食，请单独喂食" maxlength="200" />
		</view>

		<!-- 机构未设置价格时的提示（服务端下单前会拦） -->
		<view v-if="priceZero" class="block price-zero-box">
			<text class="pz-text">该机构尚未设置寄养价格，暂时无法下单，请先联系机构确认价格。</text>
		</view>

		<!-- 费用明细 -->
		<view class="block patnest-card">
			<view class="b-title">费用明细</view>
			<block v-if="serverEst && serverEst.breakdown && serverEst.breakdown.length">
				<view v-for="(b, i) in serverEst.breakdown" :key="i" class="fee-line">
					<text>{{ b.label }}</text>
					<text class="fee-val" :class="b.amount < 0 ? 'minus' : ''">¥{{ fmtMoney(b.amount) }}</text>
				</view>
			</block>
			<block v-else>
				<view class="fee-line">
					<text>{{ est.priceMode === 'per_visit' ? ('上门喂养费（' + selectedPets.length + ' 只）') : ('基础寄养费（' + est.days + ' 天 × ' + selectedPets.length + ' 只）') }}</text>
					<text class="fee-val">¥{{ fmtMoney(est.baseAmount) }}</text>
				</view>
				<view v-if="est.addonAmount > 0" class="fee-line">
					<text>加购服务</text>
					<text class="fee-val">¥{{ fmtMoney(est.addonAmount) }}</text>
				</view>
				<view v-if="est.discountAmount > 0" class="fee-line discount">
					<text>优惠券抵扣</text>
					<text class="fee-val">−¥{{ fmtMoney(est.discountAmount) }}</text>
				</view>
				<view v-if="est.pointsDiscount > 0" class="fee-line discount">
					<text>积分抵扣（{{ est.pointsUsed }} 分）</text>
					<text class="fee-val">−¥{{ fmtMoney(est.pointsDiscount) }}</text>
				</view>
			</block>
			<view class="fee-line total">
				<text>预计支付</text>
				<text class="fee-val">¥{{ fmtMoney(displayAmount) }}</text>
			</view>
			<text class="fee-note">* 费用由服务端核算（含节假日上浮），提交后以订单实际金额为准</text>
		</view>

		<button class="patnest-primary-btn submit" :disabled="submitting || priceZero" @click="submit">
			{{ submitting ? '提交中...' : (priceZero ? '该机构暂不可下单' : '提交订单') }}
		</button>
	</view>
</template>

<script>
	import { sitterApi, petApi, orderApi, walletApi } from '../../utils/api.js'
	import { PRICE_MODE, SITTER_TYPE_TEXT, ADDON_UNIT_TEXT } from '../../config.js'
	import { calcEstimate } from '../../utils/estimate.js'
	import { today, addDays, fmtMoney } from '../../utils/format.js'
	import { saveOrder } from '../../utils/orderCache.js'

	// 只有这些状态的订单才真正占用宠物档期（与后端 CreateOrder 的冲突查询一致）。
	// completed / evaluated / cancelled / rejected 都不占档期，可以再次下单。
	const ACTIVE_STAY_STATUS = ['pending_pay', 'confirmed', 'accepted', 'staying']

	export default {
		data() {
			return {
				sitterId: null,
				sitter: {},
				pets: [],
				selectedPets: [],
				presetPetId: null,
				startDate: '',
				endDate: '',
				today: '',
				addons: [],
				addonQty: {},
				coupons: [],
				couponIndex: 0,
			// 选中的券对象（直接存对象，避免索引与列表不同步）
			pickedCoupon: null,
			// 还没领取的券（coupon_id 是模板 id）
			claimableCoupons: [],
			// 正在领取的券模板 id
			claiming: null,
				pointsBalance: 0,
				pointsRate: 100,
				pointsInput: '',
				remark: '',
				capacityItems: [],
				// 服务端预估结果（含节假日上浮，优先于本地估算）
				serverEst: null,
				// 各资源的加载状态：用于显示加载提示，避免页面「卡住」没反馈
				loadingSitter: false,
				loadingPets: false,
				loadingWallet: false,
				loadingCapacity: false,
				loadingEstimate: false,
				// 每只宠物的历史寄养记录，用于提交前预检日期冲突
				petStays: {},
				submitting: false,
				// onShow 在 onLoad 后还会触发一次，用它区分「首次进入」与「从别的页面回来」
				shownOnce: false
			}
		},
		computed: {
			typeText() {
				return SITTER_TYPE_TEXT[this.sitter.type] || '寄养'
			},
			// 可选优惠券（未使用 + 归属机构匹配）
			// 机构专属券带 sitter_id，只能用于该机构的订单，服务端会二次校验
			usableCoupons() {
				const sid = Number(this.sitterId) || 0
				return this.coupons.filter((c) => {
					if (c.status !== 'unused') return false
					if (c.sitter_id && sid && Number(c.sitter_id) !== sid) return false
					return true
				})
			},
			// 当前机构用不了的券（其它机构专属），单独展示并标注原因
			unusableCoupons() {
				const sid = Number(this.sitterId) || 0
				return this.coupons.filter((c) => {
					if (c.status !== 'unused') return false
					return c.sitter_id && sid && Number(c.sitter_id) !== sid
				})
			},
			selectedCoupon() {
				return this.pickedCoupon
			},
			pointsUsed() {
				const n = parseInt(this.pointsInput, 10)
				return isNaN(n) || n < 0 ? 0 : n
			},
			// 本次最多可抵扣的积分数：
			//   ① 不超过积分余额；② 不超过「优惠后应付金额 × 兑换率」（后端 order_pricing.go 同口径）。
			// 注意 pointsBalance 本身已经是积分数，不能再乘兑换率。
			maxUsablePoints() {
				const rate = Number(this.pointsRate) || 100
				const remaining = Math.max(0, (this.est.subtotal || 0) - (this.est.discountAmount || 0))
				return Math.max(0, Math.min(Number(this.pointsBalance) || 0, Math.floor(remaining * rate)))
			},
			// 余额够但本单金额不足以全部抵扣时，提示本单上限（避免用户以为「全部使用」没用）
			pointsHint() {
				const max = this.maxUsablePoints
				const bal = Number(this.pointsBalance) || 0
				if (max > 0 && max === bal) return ''
				if (max > 0) return '，本单最多抵扣 ' + max + ' 分'
				return ''
			},
			// 与所选日期区间重叠的寄养记录（提交前预检）
			// 判定规则必须与服务端完全一致：只有「进行中」的订单才占用宠物档期
			// （order_handler.go:86-92 的 status IN ('pending_pay','confirmed','accepted','staying')）。
			// 之前只排除了 cancelled / rejected，把 completed / evaluated 也算成冲突：
			// 订单被拒、被取消、甚至提前完成后，同一只宠物在同日期段仍然下不了单。
			conflicts() {
				if (!this.startDate || !this.endDate || !this.selectedPets.length) return []
				const list = []
				this.selectedPets.forEach((pid) => {
					const stays = this.petStays[pid] || []
					stays.forEach((st) => {
						if (!st.start_date || !st.end_date) return
						if (ACTIVE_STAY_STATUS.indexOf(st.status) < 0) return
						if (st.start_date < this.endDate && st.end_date > this.startDate) {
							list.push({ petId: pid, stay: st })
						}
					})
				})
				return list
			},
			// 页面展示的金额：优先服务端预估，其次本地估算
			displayAmount() {
				if (this.serverEst && this.serverEst.total_amount != null) {
					return this.serverEst.total_amount
				}
				return this.est.totalAmount
			},
			// 机构未设置有效价格时服务端会拦下下单，这里提前告知
			priceZero() {
				if (!this.sitter.id) return false
				if (this.sitter.price_mode === PRICE_MODE.PER_VISIT) {
					return Number(this.sitter.visit_price) === 0
				}
				return Number(this.sitter.daily_price) === 0
			},
			est() {
				return calcEstimate({
					priceMode: this.sitter.price_mode || PRICE_MODE.PER_DAY,
					dailyPrice: this.sitter.daily_price,
					visitPrice: this.sitter.visit_price,
					startDate: this.startDate,
					endDate: this.endDate,
					petCount: this.selectedPets.length,
					addons: this.addons.map((a) => ({ name: a.name, price: a.price, qty: this.addonQty[a.id] || 0 })),
					coupon: this.selectedCoupon,
					pointsUsed: this.pointsUsed,
					pointsBalance: this.pointsBalance,
					pointsRate: this.pointsRate
				})
			},
			capacityChecked() {
				return this.capacityItems.length > 0
			},
			fullDays() {
				return this.capacityItems.filter((c) => c.available <= 0).map((c) => c.date)
			},
			// 首屏还在加载（机构/宠物/券包），显示全屏遮罩
			firstLoading() {
				return this.loadingSitter || this.loadingPets || this.loadingWallet
			},
			loadingText() {
				if (this.loadingSitter) return '正在加载寄养机构…'
				if (this.loadingPets) return '正在加载宠物档案…'
				if (this.loadingWallet) return '正在加载优惠券与积分…'
				return '加载中…'
			}
		},
		onLoad(options) {
			this.today = today()
			this.startDate = this.today
			this.endDate = addDays(this.today, 3)
			if (options.sitter_id) {
				this.sitterId = options.sitter_id
				this.loadSitter()
			}
			// 从宠物详情页「预约寄养」进来时预选该宠物
			if (options.pet_id) {
				const pid = parseInt(options.pet_id, 10)
				if (!isNaN(pid)) this.presetPetId = pid
			}
			this.loadPets()
			this.loadWallet()
		},
		onShow() {
			const firstShow = !this.shownOnce
			this.shownOnce = true
			// 从机构选择页返回：picker 只负责写标记，真正切换在这里做，
			// 这样页面栈里始终只有一个下单页，已选宠物/日期也不会丢。
			const picked = uni.getStorageSync('patnest_picked_sitter')
			if (picked) {
				uni.removeStorageSync('patnest_picked_sitter')
				const sid = parseInt(picked, 10)
				if (sid && String(sid) !== String(this.sitterId)) {
					this.sitterId = sid
					this.sitter = {}
					this.addons = []
					this.addonQty = {}
					this.serverEst = null
					this.capacityItems = []
					// 机构专属券可能不属于新机构，重置选择避免提交时被服务端拒绝
					this.pickedCoupon = null
					this.couponIndex = 0
					this.loadSitter()
				}
			}
			// 首次进入由 onLoad 负责加载；之后每次回到本页（例如刚在订单页取消 / 被机构拒单）
			// 都要重拉档期与容量，否则页面还拿着旧数据，会把已经释放的日期继续判成冲突。
			if (firstShow) return
			this.refreshAvailability()
		},
		methods: {
			async loadSitter() {
				this.loadingSitter = true
				try {
					this.sitter = await sitterApi.detail(this.sitterId)
				} catch (e) {
					return
				} finally {
					this.loadingSitter = false
				}
				try {
					this.addons = (await sitterApi.addons(this.sitterId)).items || []
				} catch (e) {}
				this.loadCapacity()
				this.loadEstimate()
			},
			async loadCapacity() {
				this.loadingCapacity = true
				try {
					const cap = await sitterApi.capacity(this.sitterId, this.startDate, this.endDate)
					this.capacityItems = cap.items || []
				} catch (e) {
					this.capacityItems = []
				} finally {
					this.loadingCapacity = false
				}
			},
			async loadPets() {
				this.loadingPets = true
				try {
					const res = await petApi.list({ per_page: 50 })
					this.pets = (res && res.items) || []
					// 预选从宠物详情页带过来的宠物
					if (this.presetPetId && this.pets.some((p) => p.id === this.presetPetId)) {
						this.selectedPets = [this.presetPetId]
						this.presetPetId = null
						this.loadStays(this.selectedPets[0])
					}
					this.loadEstimate()
				} catch (e) {
				} finally {
					this.loadingPets = false
				}
			},
			async loadWallet() {
				this.loadingWallet = true
				try {
					const [pts, cps] = await Promise.all([walletApi.points(), walletApi.coupons()])
					this.pointsBalance = (pts && pts.balance) || 0
					this.pointsRate = (pts && pts.rate) || 100
					// items = 已领取到券包的券（id 是用户券 id，下单传这个）
					this.coupons = (cps && cps.items) || []
					// claimable = 还没领的券（coupon_id 是模板 id，领取时传这个）
					this.claimableCoupons = (cps && cps.claimable) || []
				} catch (e) {
				} finally {
					this.loadingWallet = false
				}
			},
			chooseSitter() {
				uni.navigateTo({ url: '/pages/sitter/picker?sitter_id=' + (this.sitterId || '') })
			},
			goPetManage() {
				uni.navigateTo({ url: '/pages/pet/list' })
			},
			togglePet(p) {
				const i = this.selectedPets.indexOf(p.id)
				if (i >= 0) {
					this.selectedPets.splice(i, 1)
				} else {
					this.selectedPets.push(p.id)
					// 拉该宠物的历史寄养记录，用于提前发现日期冲突
					this.loadStays(p.id)
				}
				this.loadEstimate()
			},
			// 拉取宠物的历史寄养记录
			async loadStays(petId) {
				try {
					const res = await petApi.stays(petId)
					this.petStays = Object.assign({}, this.petStays, {
						[petId]: (res && res.items) || []
					})
				} catch (e) {
					this.petStays = Object.assign({}, this.petStays, { [petId]: [] })
				}
			},
			// 重新校准「档期 + 容量」：订单被取消/拒单/完成后占用的资源会释放，
			// 回到本页时必须重新拉一次，否则冲突提示和床位提示都停在旧状态。
			refreshAvailability() {
				this.selectedPets.forEach((pid) => this.loadStays(pid))
				if (this.sitterId) this.loadCapacity()
			},
			// 服务端费用预估（与下单同一套核算口径，含节假日上浮与券归属校验）
			async loadEstimate() {
				if (!this.sitterId || !this.startDate || !this.endDate) {
					this.serverEst = null
					return
				}
				if (this.sitter.price_mode !== PRICE_MODE.PER_VISIT && this.est.days <= 0) {
					this.serverEst = null
					return
				}
				if (!this.selectedPets.length) {
					this.serverEst = null
					return
				}
				const params = {
					start_date: this.startDate,
					end_date: this.endDate,
					pet_ids: this.selectedPets.join(',')
				}
				if (this.selectedCoupon) params.user_coupon_id = this.selectedCoupon.id
				if (this.pointsUsed > 0) params.points_used = this.pointsUsed
				const addonParam = this.addons
					.filter((a) => (this.addonQty[a.id] || 0) > 0)
					.map((a) => a.id + ':' + this.addonQty[a.id])
					.join(',')
				if (addonParam) params.addons = addonParam
			try {
				const res = await sitterApi.estimate(this.sitterId, params)
				this.serverEst = res
			} catch (e) {
				// 预估失败时回退到本地估算，不阻断下单
				this.serverEst = null
				// 券不适用于当前机构（机构专属券）时，取消选中并提示，
				// 否则用户会一直卡在「预估失败 → 显示不出优惠」
				if (e && (e.code === 10001 || e.code === 30002) && this.pickedCoupon) {
					const t = e.message || ''
					if (t.indexOf('券') >= 0 || t.indexOf('优惠') >= 0 || t.indexOf('coupon') >= 0) {
						uni.showToast({ title: t || '该券不适用于当前机构', icon: 'none' })
						this.pickedCoupon = null
						this.couponIndex = 0
					}
				}
			}
		},
			petName(pid) {
				const p = this.pets.find((x) => x.id === pid)
				return p ? p.name : '宠物#' + pid
			},
			openOrder(stay) {
				if (!stay || !stay.order_id) return
				// 先把这份记录存成本地快照，
				// 这样即使服务端详情接口取不到（sitter 角色账号会 404），
				// 详情页也能显示订单号与日期，不会出现「订单 #undefined」
				saveOrder({
					id: stay.order_id,
					order_no: stay.order_no,
					status: stay.status,
					start_date: stay.start_date,
					end_date: stay.end_date,
					total_days: stay.total_days,
					pet_count: 1,
					total_amount: stay.total_amount,
					sitter_name: stay.sitter_name
				})
				uni.navigateTo({ url: '/pages/order/detail?id=' + stay.order_id })
			},
			onStart(e) {
				this.startDate = e.detail.value
				if (this.endDate <= this.startDate) {
					this.endDate = addDays(this.startDate, 1)
				}
				this.loadCapacity()
				this.loadEstimate()
			},
			onEnd(e) {
				this.endDate = e.detail.value
				this.loadCapacity()
				this.loadEstimate()
			},
			incAddon(a) {
				this.addonQty[a.id] = Math.min(20, (this.addonQty[a.id] || 0) + 1)
				this.addonQty = Object.assign({}, this.addonQty)
				this.loadEstimate()
			},
			decAddon(a) {
				if ((this.addonQty[a.id] || 0) > 0) {
					this.addonQty[a.id] -= 1
					this.addonQty = Object.assign({}, this.addonQty)
					this.loadEstimate()
				}
			},
			// 选择优惠券
			pickCoupon(c) {
				this.pickedCoupon = c
				this.couponIndex = this.usableCoupons.findIndex((x) => x.id === c.id) + 1
				this.loadEstimate()
			},
			// 取消使用优惠券
			clearCoupon() {
				this.pickedCoupon = null
				this.couponIndex = 0
				this.loadEstimate()
			},
			// 领取一张还没领的券，领取成功后自动选中
			async claimAndPick(c) {
				if (this.claiming) return
				this.claiming = c.coupon_id
				try {
					await walletApi.claimCoupon(c.coupon_id)
					// 重新拉券包，拿到这张券的用户券 id
					await this.loadWallet()
					const claimed = this.coupons.find(
						(x) => x.coupon_id === c.coupon_id && x.status === 'unused'
					)
					if (claimed) {
						this.pickCoupon(claimed)
						uni.showToast({ title: '领取成功', icon: 'success' })
					} else {
						uni.showToast({ title: '领取成功', icon: 'success' })
					}
				} catch (e) {
					// 错误提示已在 request 层处理
				} finally {
					this.claiming = null
				}
			},
			// 券面额文案：fixed=直减金额，percent=折扣，discount=折扣率
			couponValueText(c) {
				if (!c) return ''
				const t = c.type || 'fixed'
				if (t === 'percent') return c.value + '% off'
				if (t === 'discount') return (10 - Number(c.value)) + ' 折'
				return '¥' + fmtMoney(c.value)
			},
			// 门槛文案
			couponCondText(c) {
				if (!c) return ''
				const min = Number(c.min_amount) || 0
				return min > 0 ? ('满 ' + min + ' 元可用') : '无门槛'
			},
			onPointsInput(e) {
				let v = e.detail.value
				const max = this.maxUsablePoints
				if (v !== '' && Number(v) > max) v = String(max)
				this.pointsInput = v
				this.loadEstimate()
			},
			useMaxPoints() {
				const max = this.maxUsablePoints
				if (max <= 0) {
					return uni.showToast({ title: '本单暂无可抵扣积分', icon: 'none' })
				}
				this.pointsInput = String(max)
				this.loadEstimate()
			},
			unitText(u) {
				return ADDON_UNIT_TEXT[u] || '单'
			},
			async submit() {
				if (!this.sitter.id) return uni.showToast({ title: '请选择机构', icon: 'none' })
				if (this.selectedPets.length === 0) return uni.showToast({ title: '请至少选择一只宠物', icon: 'none' })
				if (this.priceZero) {
					return uni.showToast({ title: '该机构未设置价格，暂时无法下单', icon: 'none' })
				}
				if (this.est.days <= 0 && this.sitter.price_mode !== PRICE_MODE.PER_VISIT) {
					return uni.showToast({ title: '结束日期需晚于开始日期', icon: 'none' })
				}
				if (this.fullDays.length) {
					return uni.showToast({ title: '所选日期床位已满，请换个日期', icon: 'none' })
				}
				// 提交前本地预检：宠物在该日期区间是否已被预订
				if (this.conflicts.length) {
					const names = this.conflicts.map((c) => this.petName(c.petId)).join('、')
					return uni.showModal({
						title: '日期冲突',
						content: names + ' 在所选日期区间已被预订，请调整日期或换一只宠物。',
						showCancel: false,
						confirmColor: '#C85B74'
					})
				}
				const payload = {
					sitter_id: Number(this.sitterId),
					start_date: this.startDate,
					end_date: this.endDate,
					pets: this.selectedPets.map((id) => ({ pet_id: id }))
				}
				const addons = this.addons
					.filter((a) => (this.addonQty[a.id] || 0) > 0)
					.map((a) => ({ addon_id: a.id, qty: this.addonQty[a.id] }))
				if (addons.length) payload.addons = addons
				if (this.selectedCoupon) payload.user_coupon_id = this.selectedCoupon.id
				if (this.pointsUsed > 0) payload.points_used = this.pointsUsed
				if (this.remark.trim()) payload.remark = this.remark.trim()

				this.submitting = true
				try {
					const res = await orderApi.create(payload)
					// 下单响应里带完整订单摘要，先存成本地快照，
					// 这样即使服务端订单详情/列表接口暂时不可用，详情页也能正常显示与操作。
					if (res && res.order_id) {
						saveOrder({
							id: res.order_id,
							order_no: res.order_no,
							sitter_id: Number(this.sitterId),
							sitter_name: res.sitter_name || this.sitter.name,
							start_date: this.startDate,
							end_date: this.endDate,
							total_days: res.total_days != null ? res.total_days : this.est.days,
							pet_count: res.pet_count != null ? res.pet_count : this.selectedPets.length,
							estimated_amount: res.estimated_amount,
							total_amount: res.total_amount != null ? res.total_amount : this.est.totalAmount,
							status: 'pending_pay',
							payment_status: 'none',
							remark: this.remark.trim(),
							pets: this.selectedPets.map((pid) => ({ pet_id: pid }))
						})
					}
					uni.showToast({ title: '下单成功', icon: 'success' })
					setTimeout(() => {
						uni.redirectTo({ url: '/pages/order/detail?id=' + res.order_id })
					}, 600)
				} catch (e) {
					// 错误提示已在 request 层处理
				} finally {
					this.submitting = false
				}
			},
			fmtMoney
		}
	}
</script>

<style scoped>
	.create-page {
		padding: 24rpx;
		padding-bottom: 160rpx;
	}
	.block {
		padding: 28rpx;
		margin-bottom: 24rpx;
	}
	.b-title {
		font-size: 30rpx;
		font-weight: 600;
		color: #1B1E23;
		margin-bottom: 20rpx;
		display: flex;
		justify-content: space-between;
		align-items: center;
	}
	.link {
		font-size: 24rpx;
		color: #C85B74;
		font-weight: 400;
	}
	.sitter-line {
		display: flex;
		align-items: center;
		justify-content: space-between;
	}
	.s-info {
		display: flex;
		align-items: center;
	}
	.s-name {
		font-size: 30rpx;
		font-weight: 600;
		color: #1B1E23;
	}
	.s-type {
		font-size: 22rpx;
		color: #AF4A62;
		background: #FDF1F4;
		padding: 2rpx 12rpx;
		border-radius: 8rpx;
		margin-left: 16rpx;
	}
	.s-arrow {
		font-size: 24rpx;
		color: #9AA3AF;
	}
	.sitter-picker {
		display: flex;
		justify-content: space-between;
		align-items: center;
		color: #9AA3AF;
	}
	.arrow {
		font-size: 32rpx;
	}
	.empty-hint {
		color: #9AA3AF;
		font-size: 26rpx;
		padding: 16rpx 0;
	}
	.date-row {
		display: flex;
		align-items: center;
	}
	.date-cell {
		display: flex;
		flex-direction: column;
		align-items: center;
		background: #F6F7F8;
		border-radius: 12rpx;
		padding: 16rpx 40rpx;
	}
	.d-label {
		font-size: 22rpx;
		color: #9AA3AF;
	}
	.d-val {
		font-size: 28rpx;
		color: #1B1E23;
		margin-top: 4rpx;
	}
	.date-sep {
		margin: 0 24rpx;
		color: #B7BDC5;
	}
	.day-hint {
		display: block;
		font-size: 24rpx;
		color: #9AA3AF;
		margin-top: 16rpx;
	}
	.cap-warn {
		display: block;
		font-size: 22rpx;
		margin-top: 12rpx;
		padding: 12rpx 16rpx;
		border-radius: 8rpx;
	}
	.cap-warn.bad {
		color: #C2456A;
		background: #FAE7EC;
	}
	.cap-warn.good {
		color: #2F9E63;
		background: #EAF6EF;
	}
	.addon-row {
		display: flex;
		justify-content: space-between;
		align-items: center;
		padding: 14rpx 0;
	}
	.a-name {
		font-size: 26rpx;
		color: #1B1E23;
	}
	.a-price {
		font-size: 24rpx;
		color: #C2456A;
		margin-left: 12rpx;
	}
	.stepper {
		display: flex;
		align-items: center;
	}
	.step-btn {
		width: 48rpx;
		height: 48rpx;
		line-height: 48rpx;
		text-align: center;
		background: #F6F7F8;
		border-radius: 10rpx;
		font-size: 32rpx;
		color: #5C6470;
	}
	.step-num {
		width: 60rpx;
		text-align: center;
		font-size: 28rpx;
	}
	.picker-row {
		display: flex;
		justify-content: space-between;
		align-items: center;
	}
	.p-text {
		font-size: 26rpx;
		color: #9AA3AF;
	}
	.p-text.on {
		color: #AF4A62;
		font-weight: 600;
	}
	.p-text.muted {
		color: #B7BDC5;
	}
	/* 选中的券摘要行 */
	.selected-row {
		margin-top: 12rpx;
		padding: 14rpx 18rpx;
		background: #FDF1F4;
		border-radius: 12rpx;
	}
	.p-clear {
		font-size: 24rpx;
		color: #AF4A62;
		flex-shrink: 0;
		padding-left: 20rpx;
	}
	/* 券列表 */
	.coupon-list {
		margin-top: 4rpx;
	}
	.coupon-item {
		display: flex;
		align-items: center;
		position: relative;
		padding: 20rpx 18rpx;
		margin-top: 12rpx;
		background: #FFF7F0;
		border: 2rpx solid #F3E0D3;
		border-radius: 14rpx;
	}
	.coupon-item.on {
		background: #FDF1F4;
		border-color: #C85B74;
	}
	.ci-left {
		width: 168rpx;
		flex-shrink: 0;
		display: flex;
		flex-direction: column;
		align-items: center;
		padding-right: 18rpx;
		border-right: 2rpx dashed #E8D5CE;
	}
	.coupon-item.on .ci-left {
		border-right-color: #EFC9D3;
	}
	.ci-value {
		font-size: 34rpx;
		font-weight: 700;
		color: #C2456A;
	}
	.ci-cond {
		font-size: 20rpx;
		color: #9AA3AF;
		margin-top: 4rpx;
	}
	.ci-right {
		flex: 1;
		min-width: 0;
		padding-left: 18rpx;
	}
	.ci-title {
		display: block;
		font-size: 26rpx;
		color: #1B1E23;
		overflow: hidden;
		text-overflow: ellipsis;
		white-space: nowrap;
	}
	.ci-date {
		display: block;
		font-size: 20rpx;
		color: #9AA3AF;
		margin-top: 6rpx;
	}
	.ci-check {
		flex-shrink: 0;
		margin-left: 12rpx;
	}
	.ci-check-icon {
		width: 36rpx;
		height: 36rpx;
	}
	/* 待领取的券 */
	.cl-title {
		display: block;
		font-size: 22rpx;
		color: #9AA3AF;
		margin: 16rpx 0 4rpx;
	}
	.coupon-item.claimable {
		background: #F7F8FA;
		border-color: #E4E7EB;
	}
	.coupon-item.claimable .ci-value {
		color: #5C6470;
	}
	.coupon-item.claiming {
		opacity: 0.6;
	}
	/* 不可用的券（其它机构专属） */
	.coupon-item.disabled {
		background: #FAFAFB;
		border-color: #ECEEF0;
		opacity: 0.6;
	}
	.coupon-item.disabled .ci-value {
		color: #9AA3AF;
	}
	.ci-claim {
		flex-shrink: 0;
		font-size: 23rpx;
		color: #AF4A62;
		border: 2rpx solid #EFC9D3;
		border-radius: 999rpx;
		padding: 6rpx 20rpx;
		margin-left: 12rpx;
	}
	.b-extra {
		font-size: 22rpx;
		color: #AF4A62;
		font-weight: 400;
	}
	/* 首屏加载遮罩 */
	.loading-mask {
		position: fixed;
		left: 0;
		right: 0;
		top: 0;
		bottom: 0;
		background: rgba(255, 255, 255, 0.92);
		display: flex;
		align-items: center;
		justify-content: center;
		z-index: 200;
	}
	.lm-box {
		display: flex;
		flex-direction: column;
		align-items: center;
	}
	.lm-spinner {
		width: 56rpx;
		height: 56rpx;
		border: 5rpx solid #F3D5DD;
		border-top-color: #C85B74;
		border-radius: 50%;
		animation: lm-spin 0.8s linear infinite;
	}
	@keyframes lm-spin {
		to {
			transform: rotate(360deg);
		}
	}
	.lm-text {
		margin-top: 20rpx;
		font-size: 25rpx;
		color: #5C6470;
	}
	.points-balance {
		font-size: 22rpx;
		color: #9AA3AF;
		font-weight: 400;
	}
	.points-row {
		display: flex;
		align-items: center;
	}
	.points-input {
		width: 200rpx;
		background: #F6F7F8;
		border-radius: 12rpx;
		height: 72rpx;
		padding: 0 20rpx;
		font-size: 28rpx;
		box-sizing: border-box;
	}
	.points-unit {
		font-size: 26rpx;
		color: #5C6470;
		margin-left: 12rpx;
	}
	.points-max {
		margin-left: auto;
		font-size: 24rpx;
		color: #C85B74;
	}
	.textarea {
		width: 100%;
		height: 120rpx;
		font-size: 26rpx;
		color: #1B1E23;
	}
	.fee-line {
		display: flex;
		justify-content: space-between;
		padding: 10rpx 0;
		font-size: 26rpx;
		color: #5C6470;
	}
	.fee-line.discount .fee-val {
		color: #2F9E63;
	}
	.fee-line.total {
		border-top: 1rpx solid #EFF1F3;
		margin-top: 8rpx;
		padding-top: 18rpx;
	}
	.fee-val {
		color: #1B1E23;
		font-weight: 600;
	}
	.fee-val.minus {
		color: #2F9E63;
	}
	.fee-line.total .fee-val {
		color: #C2456A;
		font-size: 34rpx;
	}
	.fee-note {
		display: block;
		font-size: 22rpx;
		color: #9AA3AF;
		margin-top: 12rpx;
	}
	.fee-note.warn {
		color: #D97706;
	}
	.conflict-box {
		background: #FAE7EC;
	}
	.price-zero-box {
		background: #FDF3E3;
	}
	.pz-text {
		font-size: 24rpx;
		color: #D97706;
		line-height: 1.5;
	}
	.cb-title {
		font-size: 28rpx;
		font-weight: 600;
		color: #C2456A;
		margin-bottom: 16rpx;
	}
	.cb-row {
		display: flex;
		align-items: center;
		flex-wrap: wrap;
		padding: 10rpx 0;
	}
	.cb-pet {
		font-size: 26rpx;
		color: #1B1E23;
		font-weight: 600;
		margin-right: 16rpx;
	}
	.cb-range {
		font-size: 24rpx;
		color: #8E3B4E;
		margin-right: 16rpx;
	}
	.cb-order {
		font-size: 22rpx;
		color: #C85B74;
		text-decoration: underline;
	}
	.cb-tip {
		display: block;
		font-size: 22rpx;
		color: #8E3B4E;
		margin-top: 12rpx;
		line-height: 1.5;
	}
	.submit {
		position: fixed;
		left: 24rpx;
		right: 24rpx;
		bottom: calc(24rpx + env(safe-area-inset-bottom));
		height: 88rpx;
		line-height: 88rpx;
	}
</style>
