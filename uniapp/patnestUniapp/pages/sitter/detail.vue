<template>
	<view class="detail-page patnest-page">
		<!-- 加载失败：明确告诉用户发生了什么，而不是显示一张空卡片 -->
		<view v-if="loadError" class="err-box">
			<image class="err-icon" src="/static/icons/shop.png" mode="aspectFit" />
			<text class="err-title">{{ loadError.title }}</text>
			<text class="err-desc">{{ loadError.desc }}</text>
			<text v-if="loadError.detail" class="err-detail">{{ loadError.detail }}</text>
			<view class="err-actions">
				<view class="err-btn ghost" @click="goBack">返回</view>
				<view class="err-btn" @click="load">重试</view>
			</view>
		</view>

		<block v-else>
		<!-- 缓存数据提示：接口失败时用本地快照兜底渲染 -->
		<view v-if="stale" class="stale-bar">
			<text class="stale-text">当前为缓存数据，服务器暂时连不上，刷新后可能更新</text>
			<text class="stale-retry" @click="reload">刷新</text>
		</view>
		<!-- 画廊 -->
		<swiper
			v-if="photos.length"
			class="gallery"
			indicator-dots
			indicator-color="rgba(255,255,255,0.5)"
			indicator-active-color="#fff"
		>
			<swiper-item v-for="(p, i) in photos" :key="i">
				<image class="gallery-img" :src="p" mode="aspectFill" />
			</swiper-item>
		</swiper>
		<view v-else class="gallery gallery-empty">
			<image class="gallery-img" src="/static/avatar-default.jpg" mode="aspectFit" />
		</view>

		<view class="content">
			<!-- 头部 -->
			<view class="header patnest-card">
				<view class="h-row">
					<text class="name">{{ s.name }}</text>
					<text class="type-tag" :style="typeStyle">{{ typeText }}</text>
				</view>
				<view class="h-row sub">
					<text class="star">★ {{ score }}</text>
					<text class="muted">{{ s.review_count || 0 }} 条评价</text>
					<text class="muted">容量 {{ s.capacity != null ? s.capacity : '—' }}</text>
				</view>
				<view class="h-row addr">
					<text class="muted">{{ s.address || '地址未填写' }}</text>
					<text v-if="distanceText" class="h-distance">距你 {{ distanceText }}</text>
				</view>
				<!-- 联系电话：有号码时显示（脱敏），点底部「电话」可一键拨号 -->
				<view v-if="s.contact_phone" class="h-row addr">
					<view class="phone-line">
						<image class="phone-icon" src="/static/icons/phone.png" mode="aspectFit" />
						<text class="muted">{{ contactLabel }}</text>
					</view>
					<text class="muted">{{ maskPhone(s.contact_phone) }}</text>
				</view>
				<view class="price-box">
					<block v-if="s.price_mode === 'per_visit'">
						<text class="price">¥{{ s.visit_price }}</text>
						<text class="unit">/次（上门喂养）</text>
					</block>
					<block v-else>
						<text class="price">¥{{ s.daily_price }}</text>
						<text class="unit">/天（含首不含尾）</text>
					</block>
				</view>
				<!-- 机构未设置价格时提前提示，避免下单被服务端拦下 -->
				<view v-if="priceZero" class="price-warn">
					<text class="pw-text">该机构尚未设置寄养价格，暂时无法下单，建议先联系机构确认。</text>
				</view>
			</view>

			<!-- 导航 -->
			<view class="section patnest-card nav-card">
				<view class="sec-title">位置与导航</view>
				<view class="nav-row">
					<view class="nav-info">
						<text class="nav-addr">{{ s.address || '该机构暂未填写地址' }}</text>
						<text class="nav-hint">{{ navHint }}</text>
					</view>
					<view class="nav-btn" @click="goNav">
						<image class="nav-icon" src="/static/icons/navigation.png" mode="aspectFit" />
						<text class="nav-text">导航</text>
					</view>
				</view>
			</view>

			<!-- 简介 -->
			<view v-if="s.description" class="section patnest-card">
				<view class="sec-title">机构简介</view>
				<text class="sec-text">{{ s.description }}</text>
			</view>

			<!-- 加购服务 -->
			<view v-if="addons.length" class="section patnest-card">
				<view class="sec-title">可加购服务</view>
				<view v-for="a in addons" :key="a.id" class="addon-item">
					<text class="a-name">{{ a.name }}</text>
					<text class="a-price">¥{{ a.price }} / {{ unitText(a.unit) }}</text>
				</view>
			</view>

			<!-- 容量日历（未来 14 天） -->
			<view v-if="capacity.length" class="section patnest-card">
				<view class="sec-title">未来 14 天床位余量</view>
				<view class="capacity-grid">
					<view v-for="c in capacity" :key="c.date" class="cap-item" :class="c.available <= 0 ? 'full' : ''">
						<text class="cap-date">{{ shortDate(c.date) }}</text>
						<text class="cap-num">{{ c.available }}/{{ c.capacity }}</text>
					</view>
				</view>
				<text class="cap-hint">余量为 0 表示当日已满员，建议换个日期。</text>
			</view>

			<!-- 最新评价 -->
			<view v-if="reviews.length" class="section patnest-card">
				<view class="sec-head">
					<view class="sec-title no-margin">最新评价</view>
					<text class="more" @click="goReviews">查看全部 ›</text>
				</view>
				<view v-for="r in reviews" :key="r.id" class="review-item">
					<view class="r-top">
						<score-stars :score="r.overall_score" />
						<text class="r-time">{{ fmtTime(r.created_at) }}</text>
					</view>
					<text class="r-comment">{{ r.owner_comment }}</text>
					<text v-if="r.reply_text" class="r-reply">店家回复：{{ r.reply_text }}</text>
				</view>
			</view>
		</view>

		<!-- 底部操作栏 -->
		<view class="action-bar">
			<view class="fav" @click="toggleFav">
				<image
					class="fav-icon"
					:src="favorited ? '/static/icons/heart-fill.png' : '/static/icons/heart.png'"
					mode="aspectFit"
				/>
				<text class="fav-text">{{ favorited ? '已收藏' : '收藏' }}</text>
			</view>
			<view class="contact" @click="openCall">
				<image class="c-icon" src="/static/icons/phone.png" mode="aspectFit" />
				<text class="c-text">电话</text>
			</view>
			<view class="contact" @click="openContact">
				<image class="c-icon" src="/static/icons/message.png" mode="aspectFit" />
				<text class="c-text">留言</text>
			</view>
			<button class="patnest-primary-btn book" @click="goBook">立即预约</button>
		</view>
		</block>
	</view>
</template>

<script>
	import { sitterApi, favoriteApi } from '../../utils/api.js'
	import { isLoggedIn } from '../../utils/auth.js'
	import { SITTER_TYPE_TEXT, ADDON_UNIT_TEXT, PRICE_MODE } from '../../config.js'
	import { today, addDays, fmtDateTime } from '../../utils/format.js'
	import { getCurrentLocation, distanceTo, formatDistance, openNavigation } from '../../utils/location.js'
	import { dialPhone } from '../../utils/dial.js'
	import { maskPhone } from '../../utils/phone.js'
	import { mergeWithSnapshot, saveSnapshot } from '../../utils/sitterCache.js'

	export default {
		data() {
			return {
				id: null,
				// 从列表跳转时带过来的机构名，加载失败时也能告诉用户是哪个机构
				name: '',
				s: {},
				photos: [],
				reviews: [],
				addons: [],
				capacity: [],
				favorited: false,
				loading: false,
				// 加载失败时的错误信息（null = 正常）
				loadError: null,
				// true = 当前展示的是本地缓存数据（接口失败时的兜底）
				stale: false,
				loc: null
			}
		},
		computed: {
			typeText() {
				return SITTER_TYPE_TEXT[this.s.type] || '寄养'
			},
			typeStyle() {
				const map = {
					home: { bg: '#FDF1F4', fg: '#AF4A62' },
					shop: { bg: '#EAF1FE', fg: '#3E7BFA' },
					visit: { bg: '#EAF6EF', fg: '#2F9E63' }
				}
				const c = map[this.s.type] || { bg: '#EFF1F3', fg: '#5C6470' }
				return 'background:' + c.bg + ';color:' + c.fg
			},
			score() {
				const v = Number(this.s.avg_score) || 0
				return v.toFixed(1)
			},
			distanceText() {
				return formatDistance(distanceTo(this.s, this.loc))
			},
			// 联系人称呼：优先 contact_name，其次 contact_person
			contactLabel() {
				return this.s.contact_name || this.s.contact_person || '机构电话'
			},
			hasCoordinate() {
				const lat = Number(this.s.latitude)
				const lng = Number(this.s.longitude)
				return !isNaN(lat) && !isNaN(lng) && (lat !== 0 || lng !== 0)
			},
			navHint() {
				return this.hasCoordinate ? '点击「导航」打开微信内置地图' : '点击「导航」复制机构地址'
			},
			priceZero() {
				if (this.s.price_mode === PRICE_MODE.PER_VISIT) {
					return Number(this.s.visit_price) === 0
				}
				return Number(this.s.daily_price) === 0
			}
		},
		onLoad(options) {
			this.id = options.id
			// 列表跳转时带上机构名，出错时能告诉用户是哪个机构
			this.name = options.name ? decodeURIComponent(options.name) : ''
			this.load()
			this.loadLoc()
		},
		onReady() {
			// 导航栏标题显示机构名（而不是固定的「机构详情」）
			this.syncTitle()
		},
		methods: {
			// 同步导航栏标题为机构名
			syncTitle() {
				const t = this.s && this.s.name ? this.s.name : this.name
				if (!t) return
				uni.setNavigationBarTitle({ title: t })
			},
			reload() {
				this.load()
			},
			// 静默获取位置（失败不打扰用户）
			async loadLoc() {
				const loc = await getCurrentLocation()
				if (loc) this.loc = loc
			},
			goNav() {
				this.openNav()
			},
			async openNav() {
				// 没有坐标时先尝试用地址搜一下，仍然拿不到就复制地址
				if (!this.hasCoordinate) {
					const addr = this.s.address || this.s.name
					if (!addr) {
						return uni.showToast({ title: '该机构暂未提供位置信息', icon: 'none' })
					}
					return openNavigation(this.s)
				}
				await openNavigation(this.s)
			},
			async load() {
				this.loading = true
				this.loadError = null
				this.stale = false
				try {
					const res = await sitterApi.detail(this.id)
					if (!res || !res.id) {
						// 接口返回空 → 多半是机构已下架或 id 不对
						this.setLoadError({
							title: this.name ? ('「' + this.name + '」暂时打不开') : '该机构暂时打不开',
							desc: '机构可能已下架，或者链接里的编号不对。',
							detail: '请求编号：' + (this.id == null ? '（空）' : this.id)
						})
						return
					}
					// 服务端字段可能不全（如后端临时只回了部分列），
					// 用本地快照补齐缺字段，避免整页变成空白卡片
					const merged = mergeWithSnapshot(res, this.id)
					this.s = merged
					this.name = merged.name || this.name
					// 快照回填（含服务端缺字段时）也存一份，下次进页面更快
					saveSnapshot(merged)
					const detailPhotos = merged.detail_photos || []
					this.photos = detailPhotos.length ? detailPhotos : merged.cover_photo_url ? [merged.cover_photo_url] : []
					this.syncTitle()
				} catch (e) {
					// 详情失败时给出可读诊断，不能静默留一张空卡片
					const code = e && e.code
					const msg = (e && e.message) || '网络异常'
					// 兜底：本地有快照就先渲染快照 + 顶部提示，别让用户对着白屏发呆
					const snap = mergeWithSnapshot(null, this.id)
					if (snap && snap.id) {
						this.s = snap
						this.name = snap.name || this.name
						this.photos = snap.detail_photos && snap.detail_photos.length
							? snap.detail_photos
							: snap.cover_photo_url
							? [snap.cover_photo_url]
							: []
						this.stale = true
						this.loadError = null
						this.loading = false
						this.syncTitle()
						return
					}
					this.setLoadError({
						title: this.name ? ('「' + this.name + '」加载失败') : '机构加载失败',
						desc: code === 30001
							? '该机构不存在或已下架。'
							: '暂时连不上服务器，请检查网络后重试。',
						detail: '错误码 ' + (code == null ? '-' : code) + ' · ' + msg
					})
					return
				}
				// 以下区块独立容错，任一失败不影响其余展示
				try {
					this.addons = (await sitterApi.addons(this.id)).items || []
				} catch (e) {}
				try {
					const cap = await sitterApi.capacity(this.id, today(), addDays(today(), 13))
					this.capacity = cap.items || []
				} catch (e) {}
				try {
					this.reviews = (await sitterApi.reviews(this.id)).items || []
				} catch (e) {}
				if (isLoggedIn()) this.checkFav()
				this.loading = false
			},
			setLoadError(info) {
				this.loadError = Object.assign(
					{ title: '机构加载失败', desc: '请稍后重试。', detail: '' },
					info || {}
				)
				this.s = {}
				this.photos = []
				this.loading = false
			},
			goBack() {
				const pages = getCurrentPages()
				if (pages.length > 1) {
					uni.navigateBack()
				} else {
					uni.reLaunch({ url: '/pages/index/index' })
				}
			},
			async checkFav() {
				try {
					const res = await favoriteApi.list()
					const items = (res && res.items) || []
					const target = Number(this.id)
					this.favorited = items.some((f) => Number(f.sitter_id) === target)
				} catch (e) {}
			},
			async toggleFav() {
				if (!isLoggedIn()) return this.goLogin()
				try {
					if (this.favorited) {
						await favoriteApi.remove(this.id)
						this.favorited = false
						uni.showToast({ title: '已取消收藏', icon: 'none' })
					} else {
						await favoriteApi.add(this.id)
						this.favorited = true
						uni.showToast({ title: '已收藏', icon: 'success' })
					}
				} catch (e) {}
			},
			goBook() {
				if (!isLoggedIn()) return this.goLogin()
				uni.navigateTo({ url: '/pages/order/create?sitter_id=' + this.id })
			},
			goReviews() {
				uni.navigateTo({ url: '/pages/sitter/reviews?id=' + this.id })
			},
			openContact() {
				if (!isLoggedIn()) return this.goLogin()
				uni.showModal({
					title: '给机构留言',
					editable: true,
					placeholderText: '想咨询的问题（可留空）',
					success: async (r) => {
						if (!r.confirm) return
						try {
							await sitterApi.contact(this.id, r.content || '')
							uni.showToast({ title: '已发送', icon: 'success' })
						} catch (e) {}
					}
				})
			},
			// 拉起系统拨号界面，号码自动填好
			openCall() {
				if (!isLoggedIn()) return this.goLogin()
				// 后端 2026-11-05 起机构接口会下发 contact_phone / contact_person
				const phone = this.s.contact_phone || ''
				const who = this.s.contact_name || this.s.contact_person || this.s.name || '该机构'
				if (!phone) {
					// 机构没填电话 → 降级为站内留言，保证用户至少能联系上
					uni.showModal({
						title: '该机构未公开电话',
						content: '可以给机构留言，机构会在消息中心收到你的咨询。',
						confirmText: '去留言',
						cancelText: '取消',
						confirmColor: '#C85B74',
						success: (r) => {
							if (r.confirm) this.openContact()
						}
					})
					return
				}
				uni.showModal({
					title: '拨打机构电话',
					content: who + '\n' + maskPhone(phone),
					confirmText: '拨打',
					cancelText: '取消',
					confirmColor: '#C85B74',
					success: (r) => {
						if (r.confirm) dialPhone(phone, { name: this.s.name })
					}
				})
			},
			goLogin() {
				uni.navigateTo({ url: '/pages/login/login' })
			},
			unitText(u) {
				return ADDON_UNIT_TEXT[u] || '单'
			},
			shortDate(s) {
				return s ? String(s).slice(5) : ''
			},
			fmtTime(s) {
				return fmtDateTime(s)
			}
		}
	}
</script>

<style scoped>
	.detail-page {
		padding-bottom: 140rpx;
	}
	/* 缓存数据提示条 */
	.stale-bar {
		display: flex;
		align-items: center;
		justify-content: space-between;
		margin: 20rpx 24rpx 0;
		padding: 16rpx 20rpx;
		background: #FDF3E3;
		border-radius: 12rpx;
	}
	.stale-text {
		flex: 1;
		font-size: 22rpx;
		color: #D97706;
		line-height: 1.5;
	}
	.stale-retry {
		flex-shrink: 0;
		margin-left: 16rpx;
		padding: 4rpx 20rpx;
		border-radius: 20rpx;
		background: #D97706;
		color: #fff;
		font-size: 22rpx;
	}
	/* 加载失败的错误态 */
	.err-box {
		display: flex;
		flex-direction: column;
		align-items: center;
		padding: 160rpx 48rpx 80rpx;
	}
	.err-icon {
		width: 120rpx;
		height: 120rpx;
		opacity: 0.3;
		margin-bottom: 28rpx;
	}
	.err-title {
		font-size: 32rpx;
		font-weight: 700;
		color: #1B1E23;
		text-align: center;
	}
	.err-desc {
		font-size: 25rpx;
		color: #5C6470;
		margin-top: 14rpx;
		text-align: center;
		line-height: 1.6;
	}
	.err-detail {
		font-size: 21rpx;
		color: #9AA3AF;
		margin-top: 12rpx;
		text-align: center;
		line-height: 1.5;
	}
	.err-actions {
		display: flex;
		margin-top: 40rpx;
	}
	.err-btn {
		min-width: 160rpx;
		height: 76rpx;
		line-height: 76rpx;
		text-align: center;
		padding: 0 40rpx;
		border-radius: 38rpx;
		background: #C85B74;
		color: #fff;
		font-size: 27rpx;
	}
	.err-btn.ghost {
		background: #F6F7F8;
		color: #5C6470;
		margin-right: 20rpx;
	}
	.gallery {
		width: 100%;
		height: 420rpx;
		background: #EFF1F3;
	}
	.gallery-empty {
		display: flex;
		align-items: center;
		justify-content: center;
	}
	.gallery-img {
		width: 100%;
		height: 420rpx;
	}
	.content {
		padding: 24rpx;
	}
	.section {
		padding: 28rpx 28rpx;
		margin-bottom: 24rpx;
	}
	.header {
		padding: 28rpx;
		margin: -40rpx 24rpx 24rpx;
		position: relative;
	}
	.h-row {
		display: flex;
		align-items: center;
	}
	.phone-line {
		display: flex;
		align-items: center;
		margin-right: 12rpx;
	}
	.phone-icon {
		width: 28rpx;
		height: 28rpx;
		margin-right: 8rpx;
		flex-shrink: 0;
	}
	.name {
		font-size: 36rpx;
		font-weight: 700;
		color: #1B1E23;
	}
	.type-tag {
		font-size: 20rpx;
		padding: 4rpx 12rpx;
		border-radius: 8rpx;
		margin-left: 16rpx;
	}
	.sub {
		margin-top: 12rpx;
	}
	.star {
		color: #D97706;
		font-weight: 600;
		margin-right: 20rpx;
	}
	.muted {
		color: #9AA3AF;
		font-size: 24rpx;
		margin-right: 20rpx;
	}
	.addr {
		margin-top: 8rpx;
	}
	.h-distance {
		font-size: 22rpx;
		color: #C85B74;
		background: #FDF1F4;
		padding: 2rpx 12rpx;
		border-radius: 8rpx;
		margin-left: 16rpx;
	}
	.price-warn {
		margin-top: 16rpx;
		padding: 14rpx 18rpx;
		background: #FDF3E3;
		border-radius: 12rpx;
	}
	.pw-text {
		font-size: 22rpx;
		color: #D97706;
		line-height: 1.5;
	}
	.nav-card {
		padding: 28rpx;
	}
	.nav-row {
		display: flex;
		align-items: center;
	}
	.nav-info {
		flex: 1;
		min-width: 0;
	}
	.nav-addr {
		font-size: 27rpx;
		color: #1B1E23;
		display: block;
	}
	.nav-hint {
		font-size: 22rpx;
		color: #9AA3AF;
		display: block;
		margin-top: 8rpx;
	}
	.nav-btn {
		flex-shrink: 0;
		margin-left: 20rpx;
		width: 96rpx;
		height: 96rpx;
		border-radius: 20rpx;
		background: #FDF1F4;
		display: flex;
		flex-direction: column;
		align-items: center;
		justify-content: center;
	}
	.nav-icon {
		width: 34rpx;
		height: 34rpx;
		flex-shrink: 0;
	}
	.nav-text {
		font-size: 20rpx;
		color: #AF4A62;
		margin-top: 6rpx;
	}
	.price-box {
		margin-top: 20rpx;
		padding-top: 20rpx;
		border-top: 1rpx solid #EFF1F3;
	}
	.price {
		color: #C2456A;
		font-size: 40rpx;
		font-weight: 700;
	}
	.unit {
		color: #9AA3AF;
		font-size: 24rpx;
		margin-left: 8rpx;
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
	.sec-text {
		font-size: 26rpx;
		color: #5C6470;
		line-height: 1.7;
	}
	.addon-item {
		display: flex;
		justify-content: space-between;
		padding: 12rpx 0;
	}
	.a-name {
		font-size: 26rpx;
		color: #1B1E23;
	}
	.a-price {
		font-size: 26rpx;
		color: #C2456A;
	}
	.capacity-grid {
		display: flex;
		flex-wrap: wrap;
	}
	.cap-item {
		width: 25%;
		text-align: center;
		padding: 10rpx 0;
	}
	.cap-date {
		display: block;
		font-size: 22rpx;
		color: #9AA3AF;
	}
	.cap-num {
		display: block;
		font-size: 24rpx;
		color: #2F9E63;
		font-weight: 600;
	}
	.cap-item.full .cap-num {
		color: #C2456A;
	}
	.cap-hint {
		display: block;
		font-size: 22rpx;
		color: #9AA3AF;
		margin-top: 12rpx;
	}
	.review-item {
		padding: 16rpx 0;
		border-bottom: 1rpx solid #EFF1F3;
	}
	.review-item:last-child {
		border-bottom: none;
	}
	.r-top {
		display: flex;
		align-items: center;
		justify-content: space-between;
	}
	.r-time {
		font-size: 22rpx;
		color: #B7BDC5;
	}
	.r-comment {
		display: block;
		font-size: 26rpx;
		color: #1B1E23;
		margin-top: 8rpx;
		line-height: 1.6;
	}
	.r-reply {
		display: block;
		font-size: 24rpx;
		color: #9AA3AF;
		margin-top: 8rpx;
	}
	.action-bar {
		position: fixed;
		left: 0;
		right: 0;
		bottom: 0;
		display: flex;
		align-items: center;
		background: #fff;
		padding: 16rpx 24rpx;
		padding-bottom: calc(16rpx + env(safe-area-inset-bottom));
		box-shadow: 0 -4rpx 16rpx rgba(27, 30, 35, 0.06);
	}
	.fav {
		display: flex;
		flex-direction: column;
		align-items: center;
		width: 110rpx;
	}
	.fav-icon {
		width: 40rpx;
		height: 40rpx;
		flex-shrink: 0;
	}
	.fav-text {
		font-size: 22rpx;
		color: #9AA3AF;
		margin-top: 2rpx;
	}
	.contact {
		display: flex;
		flex-direction: column;
		align-items: center;
		width: 110rpx;
	}
	.c-icon {
		width: 36rpx;
		height: 36rpx;
		margin-bottom: 4rpx;
	}
	.c-text {
		font-size: 22rpx;
		color: #9AA3AF;
		margin-top: 2rpx;
	}
	.book {
		flex: 1;
		height: 84rpx;
		line-height: 84rpx;
		font-size: 30rpx;
		margin-left: 16rpx;
	}
</style>
