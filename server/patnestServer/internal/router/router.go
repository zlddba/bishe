package router

import (
	"patnestServer/internal/handler"
	"patnestServer/internal/middleware"

	"github.com/gin-gonic/gin"
)

func Setup() *gin.Engine {
	r := gin.New()
	// 只信任内网反代（Nginx / 容器网络），避免 X-Forwarded-For 被伪造绕过登录限流
	_ = r.SetTrustedProxies([]string{"127.0.0.1", "::1", "10.0.0.0/8", "172.16.0.0/12", "192.168.0.0/16"})
	r.Use(gin.Logger())
	r.Use(gin.Recovery())
	r.Use(middleware.CORS())
	// 文件链接：库里存相对路径，出入站各做一次转换（详见 middleware/fileurl.go）
	r.Use(middleware.FileURL())

	// 健康检查与版本信息（发布门禁、管理面板热更新提示共用）
	r.GET("/health", handler.Health)

	v1 := r.Group("/api/v1")

	v1.GET("/version", handler.Version)

	// 游客可浏览的机构信息（只读，无需登录）
	v1.GET("/sitters", handler.ListSitters)
	v1.GET("/sitters/search", handler.SearchSitters)
	v1.GET("/sitters/:id", handler.GetSitter)
	v1.GET("/sitters/:id/capacity", handler.SitterCapacityPublic)
	v1.GET("/sitters/:id/addons", handler.ListAddons)
	v1.GET("/sitters/:id/reviews", handler.SitterReviewsPublic)

	// 逆地理编码：首页顶部地名要把坐标换成区县/街道，游客（未登录）浏览机构时
	// 同样需要显示，因此放在公开组；按 IP 限流兜住高德配额（handler.reverseWithinQuota）。
	v1.POST("/geo/reverse", handler.ReverseGeocode)
	// 行政区划：城市 adcode → 下辖区县（带中心点），「选择位置」页用来做区县筛选
	v1.GET("/geo/districts", handler.GeoDistricts)

	// 地图选点页：浏览器打开，凭一次性令牌访问，不带登录态
	v1.GET("/geo/pick/:token", handler.GeoPickerPage)
	v1.POST("/geo/pick/:token", handler.SubmitGeoPick)

	// === Public routes ===
	auth := v1.Group("/auth")
	{
		auth.POST("/register", handler.Register)
		auth.POST("/login", handler.Login)
		// 两步验证第二步：密码通过后拿一次性票据换令牌（0023）
		auth.POST("/login/totp", handler.LoginTOTP)
		auth.POST("/refresh", handler.Refresh)
	}

	// === 支付异步回调（公开，无需登录；真实微信/支付宝网关回调入口）===
	v1.POST("/payments/recharge/notify/:channel", handler.PaymentNotify)

	// === Protected routes ===
	protected := v1.Group("")
	protected.Use(middleware.JWTAuth())
	{
		// Auth (logged-in)
		protected.POST("/auth/logout", handler.Logout)
		protected.GET("/auth/me", handler.GetProfile)
		protected.PUT("/auth/profile", handler.UpdateMyProfile)
		protected.PUT("/auth/password", handler.ChangeMyPassword)

		// --- 售后工单（任何登录用户）---
		protected.POST("/support/tickets", handler.CreateTicket)
		protected.GET("/support/tickets", handler.ListMyTickets)
		protected.GET("/support/tickets/:id", handler.GetMyTicket)
		protected.POST("/support/tickets/:id/messages", handler.ReplyMyTicket)

		// --- Pet Owner namespace ---
		owner := protected.Group("/pet-owner")
		// 机构账号也可以切换到主人视角，因此这里同时允许 owner / sitter；
		// 具体数据仍然按当前 user_id 过滤，不会串号。
		owner.Use(middleware.RequireRole("owner", "sitter"))
		{
			owner.GET("/pets", handler.ListPets)
			owner.POST("/pets", handler.CreatePet)
			owner.GET("/pets/:id", handler.GetPet)
			owner.PUT("/pets/:id", handler.UpdatePet)
			owner.DELETE("/pets/:id", handler.DeletePet)
			owner.PUT("/pets/:id/vaccine", handler.UpdatePetVaccine)
			owner.GET("/pets/:id/stays", handler.GetPetStays)

			owner.POST("/orders", handler.CreateOrder)
			owner.GET("/orders", handler.ListMyOrders)
			owner.GET("/orders/:id", handler.GetOrderDetail)
			owner.PUT("/orders/:id/pay", handler.PayOrder)
			owner.PUT("/orders/:id/cancel", handler.CancelOrder)

			owner.GET("/orders/:id/dynamics", handler.ListDynamics)
			owner.GET("/orders/:id/review", handler.GetReview)
			owner.POST("/orders/:id/review", handler.SubmitReview)

			owner.PUT("/orders/:id/reschedule", handler.RescheduleOrder)
			owner.PUT("/orders/:id/renew", handler.RenewOrder)
			owner.POST("/orders/:id/refund-request", handler.RequestRefund)
			owner.GET("/refunds", handler.ListMyRefundRequests)

			owner.GET("/favorites", handler.ListFavorites)
			owner.POST("/favorites", handler.AddFavorite)
			owner.DELETE("/favorites/:sitter_id", handler.RemoveFavorite)
			owner.GET("/favorites/compare", handler.CompareFavorites)

			owner.GET("/points", handler.MyPoints)
			owner.GET("/invite", handler.MyInvite)

			// --- 钱包（真实货币）---
			owner.GET("/wallet", handler.WalletOverview)
			owner.GET("/wallet/transactions", handler.WalletTransactions)
			owner.POST("/wallet/recharge", handler.CreateRecharge)
			owner.POST("/wallet/recharge/:id/pay", handler.ConfirmRecharge)
			// 余额退款：钱包没有提现通道，这里只是登记申请，线下打款后由后台确认
			owner.POST("/wallet/withdrawals", handler.ApplyWalletWithdrawal)

			owner.GET("/coupons", handler.MyCoupons)
			owner.POST("/coupons/:id/claim", handler.ClaimCoupon)

			owner.POST("/dynamics/:id/like", handler.LikeDynamic)
			owner.DELETE("/dynamics/:id/like", handler.UnlikeDynamic)
			owner.GET("/dynamics/:id", handler.GetDynamic)
			owner.GET("/dynamics/:id/comments", handler.ListDynamicComments)
			owner.POST("/dynamics/:id/comments", handler.CreateDynamicComment)
			owner.POST("/reviews/:id/followup", handler.FollowUpReview)
			owner.GET("/dynamics", handler.ListOwnerDynamics)
			owner.POST("/sitters/:id/contact", handler.ContactSitter)
			owner.POST("/apply-sitter", handler.ApplySitter)

			// --- 注销账户（冷静期3天，期间登录自动撤销）---
			owner.POST("/account/cancel-request", handler.RequestAccountCancel)
			owner.POST("/account/cancel-withdraw", handler.WithdrawAccountCancel)

			// --- 个人消息（主人视角，scope=personal）---
			// 机构账号也能切到主人视角，所以这里沿用 owner/sitter 双角色；
			// 具体数据仍按 user_id + scope 过滤，不会串号。
			owner.GET("/notifications", handler.ListPersonalNotifications)
			owner.PUT("/notifications/:id/read", handler.MarkPersonalNotificationRead)
			owner.PUT("/notifications/read-all", handler.MarkAllPersonalNotificationsRead)
		}

		// --- Public sitter browsing (any authenticated user) ---
		browse := protected.Group("/pet-owner")
		{
			browse.GET("/sitters", handler.ListSitters)
			browse.GET("/sitters/search", handler.SearchSitters)
			browse.GET("/sitters/:id", handler.GetSitter)
			browse.GET("/sitters/:id/capacity", handler.SitterCapacityPublic)
			browse.GET("/sitters/:id/addons", handler.ListAddons)
			browse.GET("/sitters/:id/estimate", handler.EstimateOrder)
		}

		// --- 旧版统一通知接口（兼容保留：返回全部、不做视角过滤）---
		// 新端请用 /pet-owner/notifications（个人）或 /sitter/notifications（商家）。
		protected.GET("/notifications", handler.ListNotifications)
		protected.PUT("/notifications/:id/read", handler.MarkNotificationRead)
		protected.PUT("/notifications/read-all", handler.MarkAllNotificationsRead)

		// --- 机构资料自助维护（任何登录用户）---
		// 这里刻意不挂 RequireRole("sitter")：能不能操作不取决于令牌里的角色，
		// 而取决于这个账号名下有没有机构记录——两个 handler 都按 user_id 定位，
		// 没有记录就是 404，不会串号。
		// 之前一刀切要求角色是 sitter，遇到"机构已通过审核但角色还没同步"、
		// "后台直接建的机构"、"申请被驳回要改材料"这类账号，改自己的资料会直接 403。
		protected.GET("/sitter/shop/me", handler.GetMyShop)
		protected.PUT("/sitter/shop/me", handler.UpdateMyShop)
		protected.POST("/sitter/shop", handler.CreateMyShop)

		// --- Sitter namespace ---
		sitter := protected.Group("/sitter")
		sitter.Use(middleware.RequireRole("sitter"))
		{
			sitter.GET("/orders/manage", handler.ListReceivedOrders)
			sitter.GET("/orders/:id", handler.GetOrderDetail)
			sitter.PUT("/orders/:id/accept", handler.AcceptOrder)
			sitter.PUT("/orders/:id/reject", handler.RejectOrder)
			sitter.PUT("/orders/:id/complete", handler.CompleteOrder)

			sitter.POST("/orders/:id/dynamics", handler.PublishDynamic)
			sitter.PUT("/reviews/:id/reply", handler.ReplyReview)

			sitter.GET("/capacity", handler.MySitterCapacity)
			sitter.PUT("/capacity", handler.SetSitterCapacity)

			sitter.GET("/reviews", handler.ListSitterReviews)
			sitter.GET("/dynamics", handler.ListSitterDynamics)
			sitter.DELETE("/dynamics/:id", handler.DeleteSitterDynamic)
			sitter.GET("/stats", handler.SitterStats)

			sitter.GET("/pricing-rules", handler.ListPriceRules)
			sitter.POST("/pricing-rules", handler.CreatePriceRule)
			sitter.DELETE("/pricing-rules/:id", handler.DeletePriceRule)

			sitter.GET("/addons", handler.ListAddons)
			sitter.POST("/addons", handler.CreateAddon)
			sitter.PUT("/addons/:id", handler.UpdateAddon)
			sitter.DELETE("/addons/:id", handler.DeleteAddon)

			sitter.GET("/earnings", handler.SitterEarnings)
			sitter.GET("/settlements", handler.ListMySettlements)
			sitter.GET("/settlements/window", handler.SitterSettlementWindow)
			sitter.POST("/settlements", handler.ApplySettlement)

			// --- 机构优惠券：自助发布与管理，仅限本机构订单核销 ---
			sitter.GET("/coupons", handler.ListSitterCoupons)
			sitter.POST("/coupons", handler.CreateSitterCoupon)
			sitter.PUT("/coupons/:id", handler.UpdateSitterCoupon)
			sitter.DELETE("/coupons/:id", handler.DeleteSitterCoupon)

			// --- 商家消息（机构视角，scope=business，仅 sitter 可访问）---
			sitter.GET("/notifications", handler.ListBusinessNotifications)
			sitter.PUT("/notifications/:id/read", handler.MarkBusinessNotificationRead)
			sitter.PUT("/notifications/read-all", handler.MarkAllBusinessNotificationsRead)
		}

		// --- Admin namespace ---
		admin := protected.Group("/admin")
		admin.Use(middleware.RequireRole("admin"))
		admin.Use(middleware.AdminPermission())
		{
			admin.GET("/users", handler.AdminListUsers)
			admin.POST("/users", handler.AdminCreateUser)
			admin.PUT("/users/:id", handler.AdminUpdateUser)
			admin.DELETE("/users/:id", handler.AdminDeleteUser)
			admin.DELETE("/users/:id/purge", handler.AdminPurgeUser)
			admin.PUT("/users/:id/password", handler.AdminResetPassword)

			admin.GET("/sitters", handler.AdminListSitters)
			admin.POST("/sitters", handler.AdminCreateSitter)
			admin.GET("/sitters/:id", handler.AdminGetSitterDetail)
			admin.PUT("/sitters/:id", handler.AdminUpdateSitter)
			admin.PUT("/sitters/:id/status", handler.AdminUpdateSitterStatus)
			admin.DELETE("/sitters/:id", handler.AdminDeleteSitter)

			admin.GET("/orders", handler.AdminListOrders)
			admin.POST("/orders", handler.AdminCreateOrder)
			admin.GET("/orders/:id", handler.AdminGetOrderDetail)
			admin.PUT("/orders/:id", handler.AdminUpdateOrder)
			admin.DELETE("/orders/:id", handler.AdminDeleteOrder)
			admin.POST("/orders/:id/refund", handler.AdminRefundOrder)

			admin.GET("/pets", handler.AdminListPets)
			admin.POST("/pets", handler.AdminCreatePet)
			admin.PUT("/pets/:id", handler.AdminUpdatePet)
			admin.DELETE("/pets/:id", handler.AdminDeletePet)

			admin.GET("/dynamics", handler.AdminListDynamics)
			admin.POST("/dynamics", handler.AdminCreateDynamic)
			admin.PUT("/dynamics/:id", handler.AdminUpdateDynamic)
			admin.DELETE("/dynamics/:id", handler.AdminDeleteDynamic)

			admin.GET("/reviews", handler.AdminListReviews)
			admin.POST("/reviews", handler.AdminCreateReview)
			admin.PUT("/reviews/:id", handler.AdminUpdateReview)
			admin.DELETE("/reviews/:id", handler.AdminDeleteReview)

			admin.GET("/audit", handler.AdminAuditLogs)
			admin.GET("/stats", handler.AdminStats)

			admin.GET("/system/info", handler.AdminSystemInfo)
			admin.GET("/updates", handler.AdminUpdates)
			admin.POST("/updates/scan", handler.AdminScanUpdates)
			admin.POST("/updates/upload", handler.AdminUploadUpdate)
			admin.POST("/updates/apply", handler.AdminApplyUpdate)
			admin.GET("/updates/jobs/:id", handler.AdminUpdateJob)

			admin.GET("/notifications", handler.AdminListNotifications)
			admin.POST("/notifications/broadcast", handler.AdminBroadcastNotification)
			admin.DELETE("/notifications/:id", handler.AdminDeleteNotification)

			admin.PUT("/pets/:id/vaccine", handler.AdminVerifyPetVaccine)

			admin.GET("/coupons", handler.AdminListCoupons)
			admin.POST("/coupons", handler.AdminCreateCoupon)
			admin.PUT("/coupons/:id", handler.AdminUpdateCoupon)
			admin.DELETE("/coupons/:id", handler.AdminDeleteCoupon)

			admin.GET("/notification-templates", handler.AdminListTemplates)
			admin.POST("/notification-templates", handler.AdminCreateTemplate)
			admin.PUT("/notification-templates/:id", handler.AdminUpdateTemplate)
			admin.DELETE("/notification-templates/:id", handler.AdminDeleteTemplate)

			admin.GET("/permissions", handler.AdminGetPermissions)
			admin.PUT("/permissions", handler.AdminUpdatePermission)

			admin.GET("/export/:kind", handler.AdminExportCSV)

			admin.GET("/refunds", handler.AdminListRefundRequests)
			admin.PUT("/refunds/:id", handler.AdminHandleRefundRequest)

			admin.GET("/tickets", handler.AdminListTickets)
			admin.GET("/tickets/:id", handler.AdminGetTicket)
			admin.POST("/tickets/:id/reply", handler.AdminReplyTicket)
			admin.PUT("/tickets/:id", handler.AdminUpdateTicket)

			admin.GET("/settlements", handler.AdminListSettlements)
			admin.PUT("/settlements/:id", handler.AdminPaySettlement)

			// --- 平台参数（全局佣金比例；单机构覆盖走 /admin/sitters/:id）---
			admin.GET("/settings/commission", handler.AdminGetCommissionSetting)
			admin.PUT("/settings/commission", handler.AdminUpdateCommissionSetting)

			// --- 钱包管理 ---
			admin.GET("/wallets", handler.AdminListWallets)
			admin.GET("/wallets/:user_id", handler.AdminGetWallet)
			admin.POST("/wallets/:user_id/adjust", handler.AdminAdjustWallet)
			admin.GET("/wallet/recharges", handler.AdminListRecharges)
			admin.POST("/wallet/recharges/:id/confirm", handler.AdminConfirmRecharge)

			// --- 钱包余额退款（注销时自动转出，人工确认打款） ---
			admin.GET("/wallet/withdrawals", handler.AdminListWalletWithdrawals)
			admin.POST("/wallet/withdrawals/:id/pay", handler.AdminPayWalletWithdrawal)
			admin.POST("/wallet/withdrawals/:id/reject", handler.AdminRejectWalletWithdrawal)

			// --- 注销执行异常（失败 / 停止自动重试）---
			admin.GET("/account-cancels/failed", handler.AdminListCancelFailures)
			admin.POST("/account-cancels/:user_id/retry", handler.AdminRetryCancel)

			// --- HTTPS 证书（上传后由 nginx 容器守护脚本校验并 reload）---
			admin.GET("/system/tls", handler.AdminGetTLS)
			admin.POST("/system/tls", handler.AdminUploadTLS)
			admin.POST("/system/tls/backend", handler.AdminSetBackendTLS)
			admin.DELETE("/system/tls", handler.AdminDeleteTLS)

			// --- Let's Encrypt 自动证书（镜像内置 acme.sh，80 端口做 HTTP-01 验证）---
			admin.GET("/system/acme", handler.AdminGetACME)
			admin.POST("/system/acme/issue", handler.AdminIssueACME)
			admin.POST("/system/acme/renew", handler.AdminRenewACME)
			admin.POST("/system/acme/settings", handler.AdminUpdateACMESettings)
			admin.POST("/system/acme/check", handler.AdminCheckACME)

			admin.GET("/push-tasks", handler.AdminListPushTasks)
			admin.POST("/push-tasks", handler.AdminCreatePushTask)
			admin.POST("/push-tasks/:id/send", handler.AdminSendPushTask)
			admin.POST("/push-tasks/:id/cancel", handler.AdminCancelPushTask)

			// --- 两步验证（TOTP，0023）：管理员给自己的账号绑定验证器 ---
			// 这里不挂模块权限：能不能改自己的安全设置，不该取决于别人配的模块权限。
			admin.GET("/security/totp", handler.AdminTOTPStatus)
			admin.POST("/security/totp/setup", handler.AdminTOTPSetup)
			admin.POST("/security/totp/enable", handler.AdminTOTPEnable)
			admin.POST("/security/totp/disable", handler.AdminTOTPDisable)
			admin.POST("/security/totp/recovery-codes", handler.AdminTOTPRecoveryCodes)
			// 兜底：超级管理员帮丢了手机的同事重置（handler 内二次校验调用者身份）
			admin.POST("/users/:id/totp/reset", handler.AdminResetUserTOTP)
		}

		// --- Upload (any authenticated user) ---
		protected.POST("/upload/image", handler.UploadImage)

		// --- 地理位置（任何登录用户）：桌面端等无系统定位的客户端用地址换坐标 ---
		protected.POST("/geo/geocode", handler.GeocodeAddress)
		protected.GET("/geo/static-map", handler.GeoStaticMap)
		protected.GET("/geo/tile", handler.GeoTile)
		protected.POST("/geo/pick", handler.CreateGeoPick)
		protected.GET("/geo/pick-result/:token", handler.PollGeoPick)
	}

	return r
}
