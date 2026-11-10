<script>
	import { isLoggedIn, getUserInfo } from './utils/auth.js'

	export default {
		onLaunch: function () {
			// 全局启动：仅做登录态探针，不强制跳转（各页面按需引导登录）
			const logged = isLoggedIn()
			const user = getUserInfo()
			console.log('PatNest 小程序启动，登录态：', logged, user ? user.role : 'visitor')
		},
		onShow: function () {
			console.log('App Show')
		},
		onHide: function () {
			console.log('App Hide')
		}
	}
</script>

<style lang="scss">
	/* 每个页面公共 css —— 宠栖 PatNest 主人端主题（玫瑰粉品牌色阶）
	   配色依据：docs/宠栖后台-UI配色设计图.html */

	page {
		background-color: #F6F7F8;
		color: #1B1E23;
		font-size: 28rpx;
		font-family: -apple-system, BlinkMacSystemFont, 'Helvetica Neue', Helvetica, 'PingFang SC', 'Microsoft YaHei', sans-serif;
	}

	.patnest-page {
		min-height: 100vh;
		box-sizing: border-box;
	}

	/* 卡片 —— 中性 0 面板底 + 极轻投影 */
	.patnest-card {
		background: #FFFFFF;
		border-radius: 20rpx;
		box-shadow: 0 4rpx 16rpx rgba(27, 30, 35, 0.04);
	}

	/* 主按钮 —— 品牌 600 深阶，白字对比 5.6:1 达 WCAG AA */
	.patnest-primary-btn {
		background: #C85B74;
		color: #fff;
		border-radius: 48rpx;
		font-weight: 600;
	}

	.patnest-primary-btn[disabled] {
		background: #E7E9EC;
		color: #9AA3AF;
	}

	/* 次要按钮 —— 玫瑰浅底 + 主色深字 */
	.patnest-ghost-btn {
		background: #FDF1F4;
		color: #AF4A62;
		border-radius: 48rpx;
		font-weight: 600;
	}

	.patnest-text-muted {
		color: #9AA3AF;
	}

	.patnest-text-sub {
		color: #5C6470;
	}

	.patnest-divider {
		height: 1rpx;
		background: #EFF1F3;
		margin: 20rpx 0;
	}

	/* 通用标题行 */
	.patnest-sec-title {
		font-size: 30rpx;
		font-weight: 600;
		color: #1B1E23;
	}

	/* 价格强调 */
	.patnest-price {
		color: #C2456A;
		font-weight: 700;
	}

	/* 表单输入 */
	.patnest-input {
		background: #F6F7F8;
		border-radius: 12rpx;
		height: 80rpx;
		padding: 0 24rpx;
		font-size: 28rpx;
		color: #1B1E23;
		box-sizing: border-box;
	}

	/* 标签（语义色 + 浅底 + 同色相深字） */
	.patnest-tag {
		display: inline-block;
		font-size: 22rpx;
		padding: 4rpx 14rpx;
		border-radius: 8rpx;
	}

	/* 底部安全区占位 */
	.patnest-safe-bottom {
		height: calc(24rpx + env(safe-area-inset-bottom));
	}
</style>
