// #=============================================================================
// 宠栖 PatNest · uni-app（微信小程序 · 主人端）全局配置
// 契约来源：server/patnestServer/internal/{router,handler,model}（以源码为准）
// 配色来源：docs/宠栖后台-UI配色设计图.html（玫瑰粉品牌色阶 + 语义色 + 中性色）
// #=============================================================================

// ---------------------------------------------------------------------------
// 后端 API 基础地址
// - 开发：本机 Compose 起的后端（9200），HBuilderX 需在「详情 → 本地设置」
//         勾选「不校验合法域名」
// - 演示：http://<公网IP>:9200/api/v1
// - 正式：https://petnest.example.com/api/v1
// 注意：健康检查挂在 /health（不在 /api/v1 下），见 system_api.js
// ---------------------------------------------------------------------------
// export const API_BASE_URL = 'http://192.168.3.100:9200/api/v1'
export const API_BASE_URL = 'http://10.109.248.94:9200/api/v1'

// 统一响应中业务成功码（handler/response.go:16）
export const SUCCESS_CODE = 0

// 全局错误码（handler/response.go + middleware/jwt.go）
export const ERROR_CODE = {
	SUCCESS: 0,
	PARAM_INVALID: 10001, // 400 参数校验失败 / 业务规则不满足
	BODY_INVALID: 10002, // 400 请求体无效
	PAGE_INVALID: 10003, // 400 分页参数非法
	UNAUTHENTICATED: 20001, // 401 令牌无效或过期
	FORBIDDEN: 20002, // 403 权限不足
	ADMIN_CONFLICT: 20003, // 401 管理员单会话冲突（主人端不会触发）
	NOT_FOUND: 30001, // 404 资源不存在
	CONFLICT: 30002, // 409 资源冲突（容量不足 / 重复评价 / 券已领完）
	ESTIMATE_ERROR: 40001, // 500 金额计算异常
	ORDER_TX_FAILED: 40002, // 500 下单事务失败
	INTERNAL: 50001, // 500 内部错误
	UPSTREAM: 50002, // 502 第三方调用失败
	LOGIN_RATE_LIMIT: 42901 // 429 登录频率限制
}

// 本地存储 Key
export const STORAGE_KEYS = {
	ACCESS_TOKEN: 'patnest_access_token',
	REFRESH_TOKEN: 'patnest_refresh_token',
	USER_INFO: 'patnest_user_info',
	// 记住密码：只存用户自己填的账号与密码（见 utils/remember.js）
	REMEMBER_CREDENTIAL: 'patnest_remember_credential'
}

// ---------------------------------------------------------------------------
// 品牌配色（docs/宠栖后台-UI配色设计图.html）
// 主色 500 为主按钮；白字对比仅 3.4:1，正文按钮文字用 700 深阶保证可读性
// ---------------------------------------------------------------------------
export const THEME = {
	// 品牌主色阶 · 玫瑰粉
	PRIMARY_50: '#FDF1F4',
	PRIMARY_100: '#FBE0E6',
	PRIMARY_200: '#F6C2CE',
	PRIMARY_300: '#EFA0B3',
	PRIMARY_400: '#E87D97',
	PRIMARY_500: '#E0708A',
	PRIMARY_600: '#C85B74',
	PRIMARY_700: '#AF4A62',
	PRIMARY_800: '#8E3B4E',
	PRIMARY_900: '#6F2A3B',

	// 语义色（只在反馈状态出现，克制使用）
	SUCCESS: '#2F9E63',
	WARNING: '#D97706',
	DANGER: '#C2456A',
	INFO: '#3E7BFA',

	// 中性色（信息层级主载体）
	N0: '#FFFFFF',
	N50: '#F6F7F8',
	N100: '#EFF1F3',
	N200: '#E7E9EC',
	N300: '#D4D8DD',
	N400: '#B7BDC5',
	N500: '#9AA3AF',
	N600: '#5C6470',
	N700: '#3A4149',
	N800: '#1B1E23',

	// 点缀色（评分与次级强调）
	STAR: '#EAB308',
	ROSE_LIGHT: '#F2AEBE',
	ROSE_LIGHT_BG: '#FAE7EC',
	HOVER: '#F3F3F5'
}

// 主人端角色常量
export const ROLE = {
	OWNER: 'owner',
	SITTER: 'sitter',
	ADMIN: 'admin'
}

// ---------------------------------------------------------------------------
// 枚举（取值来自 serverdb/init.sql 与 migrations/*.sql 的 PG enum 定义）
// ---------------------------------------------------------------------------

// 费用模式（sitters.price_mode）
export const PRICE_MODE = {
	PER_DAY: 'per_day', // 家庭/门店寄养：天数 × 日单价
	PER_VISIT: 'per_visit' // 上门喂养：次数 × 次单价
}

// 机构类型（sitters.type）
export const SITTER_TYPE = {
	HOME: 'home', // 家庭寄养
	SHOP: 'shop', // 门店寄养
	VISIT: 'visit' // 上门喂养
}

export const SITTER_TYPE_TEXT = {
	home: '家庭寄养',
	shop: '门店寄养',
	visit: '上门喂养'
}

// 机构状态（sitters.status）
export const SITTER_STATUS_TEXT = {
	pending: '待审核',
	active: '已上架',
	inactive: '已下架',
	banned: '已封禁'
}

// 订单状态（orders.status，init.sql:46）
// 注意：已评价的取值为 evaluated（不是 reviewed）
export const ORDER_STATUS = {
	PENDING_PAY: 'pending_pay', // 待支付
	CONFIRMED: 'confirmed', // 已支付 / 待机构确认
	ACCEPTED: 'accepted', // 机构已接单
	STAYING: 'staying', // 寄养中（枚举存在，当前后端无写入路径）
	COMPLETED: 'completed', // 已完成
	EVALUATED: 'evaluated', // 已评价
	CANCELLED: 'cancelled', // 已取消
	REJECTED: 'rejected' // 已拒绝
}

export const ORDER_STATUS_TEXT = {
	pending_pay: '待支付',
	confirmed: '待接单',
	accepted: '已接单',
	staying: '寄养中',
	completed: '已完成',
	evaluated: '已评价',
	cancelled: '已取消',
	rejected: '已拒绝'
}

// 订单状态标签配色：语义色 + 浅底 + 同色相深字
export const ORDER_STATUS_THEME = {
	pending_pay: { fg: '#D97706', bg: '#FDF3E3' },
	confirmed: { fg: '#3E7BFA', bg: '#EAF1FE' },
	accepted: { fg: '#AF4A62', bg: '#FDF1F4' },
	staying: { fg: '#AF4A62', bg: '#FBE0E6' },
	completed: { fg: '#2F9E63', bg: '#EAF6EF' },
	evaluated: { fg: '#2F9E63', bg: '#EAF6EF' },
	cancelled: { fg: '#5C6470', bg: '#EFF1F3' },
	rejected: { fg: '#C2456A', bg: '#FAE7EC' }
}

// 支付状态（orders.payment_status，init.sql:51）
export const PAYMENT_STATUS_TEXT = {
	none: '未支付',
	paid: '已支付',
	refunded: '已退款'
}

// 宠物类型（pets.species）
export const SPECIES = {
	CAT: 'cat',
	DOG: 'dog',
	OTHER: 'other'
}

export const SPECIES_TEXT = {
	cat: '猫',
	dog: '狗',
	other: '其他'
}

// 宠物体型（pets.body_size）
export const PET_SIZE = {
	TINY: 'tiny',
	SMALL: 'small',
	MEDIUM: 'medium',
	LARGE: 'large',
	XLARGE: 'xlarge'
}

export const PET_SIZE_TEXT = {
	tiny: '特小',
	small: '小型',
	medium: '中型',
	large: '大型',
	xlarge: '特大型'
}

// 免疫证明核验状态（pets.vaccine_status）
export const VACCINE_STATUS_TEXT = {
	missing: '缺证明',
	pending: '待核验',
	verified: '已核验',
	rejected: '未通过'
}

// 订单宠物子项状态（order_pet.status）
export const ORDER_PET_STATUS_TEXT = {
	assigned: '已分配',
	staying: '寄养中',
	picked_up: '已接回',
	cancelled: '已取消'
}

// 通知类型（notifications.type，init.sql:61）
export const NOTIFY_TYPE_TEXT = {
	order_created: '新订单',
	order_accepted: '订单已接单',
	order_cancelled: '订单已取消',
	order_rejected: '订单被拒绝',
	order_completed: '寄养已完成',
	dynamics_published: '新动态',
	review_received: '收到评价',
	system_announcement: '平台公告'
}

// 售后工单分类（支持接口建议值）
export const TICKET_CATEGORY_TEXT = {
	order: '订单问题',
	refund: '退款相关',
	service: '服务争议',
	account: '账号问题',
	other: '其他'
}

// 售后工单状态（support_tickets.status）
export const TICKET_STATUS_TEXT = {
	open: '待处理',
	processing: '处理中',
	closed: '已关闭'
}

export const TICKET_STATUS_THEME = {
	open: { fg: '#D97706', bg: '#FDF3E3' },
	processing: { fg: '#3E7BFA', bg: '#EAF1FE' },
	closed: { fg: '#5C6470', bg: '#EFF1F3' }
}

// 售后工单优先级（support_tickets.priority）
export const TICKET_PRIORITY_TEXT = {
	low: '低',
	normal: '普通',
	high: '高'
}

// 退款申请状态（refund_requests.status）
export const REFUND_STATUS_TEXT = {
	pending: '审核中',
	approved: '已通过',
	rejected: '已驳回',
	cancelled: '已撤销'
}

export const REFUND_STATUS_THEME = {
	pending: { fg: '#D97706', bg: '#FDF3E3' },
	approved: { fg: '#2F9E63', bg: '#EAF6EF' },
	rejected: { fg: '#C2456A', bg: '#FAE7EC' },
	cancelled: { fg: '#5C6470', bg: '#EFF1F3' }
}

// 优惠券类型（coupons.type）与用户券状态（user_coupons.status）
export const COUPON_TYPE_TEXT = {
	fixed: '满减券',
	percent: '折扣券'
}

export const USER_COUPON_STATUS_TEXT = {
	unused: '未使用',
	used: '已使用',
	expired: '已过期'
}

// 机构列表排序
// 后端 sort 仅支持 price_asc / price_desc / newest（其余按评分降序）；
// distance（离我最近）由前端按 Haversine 本地排序 —— 后端目前未实现距离排序。
export const SORT_OPTIONS = [
	{ value: '', label: '综合推荐' },
	{ value: 'distance', label: '离我最近', local: true },
	{ value: 'price_asc', label: '价格从低到高' },
	{ value: 'price_desc', label: '价格从高到低' },
	{ value: 'newest', label: '最新上架' }
]

// 加购服务计价单位（addons.unit）
export const ADDON_UNIT_TEXT = {
	per_order: '每单',
	per_day: '每天',
	per_visit: '每次'
}
