# 宠物寄养服务预约系统（宠栖 PatNest）· UML 图集

> 版本：v2.3.0
> 格式：PlantUML 源码，可直接粘贴到任意 PlantUML 渲染器生成白底图片
> 内容依据：`docs/项目说明.md`、`docs/02-数据库设计.md`、`docs/release/v2.3.0.md`

本图集共 7 张标准 UML 图，覆盖软件架构的静态结构与动态行为：

| 序号 | 图名 | 视图类型 | 说明 |
| --- | --- | --- | --- |
| 1 | 用例图 | 功能视图 | 主人 / 机构 / 管理员三角色功能 |
| 2 | 系统架构图 | 逻辑视图 | 表现层→共享模块→业务层→数据访问层→数据层 |
| 3 | 部署图 | 物理视图 | 双服务器拓扑、对外端口、蓝绿槽位、白名单 |
| 4 | 功能结构图 | 功能树 | 主人端 / 机构端 / 管理后台 / 后端服务 |
| 5 | 时序图 | 交互视图 | 下单→支付→接单→履约→评价→结算主流程 |
| 6 | 状态图 | 状态视图 | 订单状态机（含取消/拒单分支与容量释放） |
| 7 | ER 图 | 数据视图 | 核心实体及关系（11 个主干实体） |

---

## 1. 用例图（Use Case Diagram）

横向布局：`left to right direction` + 去掉嵌套 package 分区（分区在用例图里会被竖着堆），所有用例在系统边界内按「主人端 → 机构端 → 管理端」从左向右平铺，三个 actor 位于左侧，并用背景色区分角色归属。

```plantuml
@startuml
title 宠物寄养服务预约系统 · 用例图（横向布局，v2.3.0）

skinparam defaultFontName "Microsoft YaHei"
skinparam shadowing false
left to right direction

actor "宠物主人\n(owner)" as Owner
actor "寄养机构\n(sitter)" as Sitter
actor "管理员\n(admin)" as Admin

rectangle "宠物寄养服务预约系统" as Sys {
  usecase "注册 / 登录" as U_Login

  usecase "维护宠物档案" as U_Pet #355C7D
  usecase "浏览 / 搜索机构" as U_Browse #355C7D
  usecase "预约下单" as U_Order #355C7D
  usecase "在线支付 / 退款" as U_Pay #355C7D
  usecase "查看寄养动态" as U_Dyn #355C7D
  usecase "评价 / 追评" as U_Review #355C7D
  usecase "钱包 / 优惠券" as U_Wallet #355C7D

  usecase "入驻与资质" as U_Settle #3E7D5A
  usecase "建店 / 定价 / 容量" as U_Shop #3E7D5A
  usecase "接单 / 发布动态" as U_Fulfill #3E7D5A
  usecase "收益 / 结算申请" as U_Income #3E7D5A

  usecase "审核机构 / 订单 / 资金" as U_Audit #8A5A2E
  usecase "用户 / 权限 / 内容管理" as U_Manage #8A5A2E
  usecase "审计日志 / 系统运维" as U_Ops #8A5A2E
}

Owner --> U_Login
Owner --> U_Pet
Owner --> U_Browse
Owner --> U_Order
Owner --> U_Pay
Owner --> U_Dyn
Owner --> U_Review
Owner --> U_Wallet

Sitter --> U_Login
Sitter --> U_Settle
Sitter --> U_Shop
Sitter --> U_Fulfill
Sitter --> U_Income

Admin --> U_Login
Admin --> U_Audit
Admin --> U_Manage
Admin --> U_Ops

U_Order ..> U_Pay : <<include>>

legend right
  |= 颜色 |= 角色 |
  | <#355C7D> | 主人端用例 |
  | <#3E7D5A> | 机构端用例 |
  | <#8A5A2E> | 管理端用例 |
end legend
@enduml
```

---

## 2. 系统架构图（Component Diagram · 逻辑视图）

严格区分逻辑视图与物理视图：本图只表现分层与依赖，Nginx/端口/服务器见部署图。组件统一用标准 UML 组件符号，依赖用虚线 `..>` + `<<use>>`。

```plantuml
@startuml
title 宠物寄养服务预约系统 · 软件架构图（逻辑视图，v2.3.0）

skinparam defaultFontName "Microsoft YaHei"
skinparam componentStyle uml
skinparam shadowing false
skinparam nodesep 60
skinparam ranksep 60

package "表现层 Presentation Layer（多端交互）" {
  [Android 客户端\nKotlin Multiplatform + Compose · 61 屏] as Android
  [桌面客户端\nCompose for JVM · 24 页] as Desktop
  [微信小程序\nuni-app (Vue 3) · 28 页] as MiniApp
  [管理后台\nVue 3 + Element Plus · 18 页] as Admin
}

package "共享核心模块（KMP commonMain · 编译期复用）" {
  [数据模型 · Ktor 网络层\n会话缓存 · 金额与校验工具] as Shared
}

package "业务逻辑层 Business Logic Layer（Go + Gin · 217 路由）" {
  [业务服务\n认证 / 机构 / 宠物 / 订单 / 支付\n结算 / 内容 / 售后 / 运维] as Service
}

package "数据访问层 Data Access Layer" {
  [仓储层 Repository\npgx 连接池 · MinIO SDK] as Repo
  database "Redis\n热点缓存 / 限流（不承担正确性）" as Redis
}

package "数据层 Data Layer（持久化）" {
  database "PostgreSQL 18 · 41 表\npg_trgm / PGroonga / pgvector\n金额/天数/单价快照落库" as PG
  database "MinIO 对象存储\n图片 / 附件（Go 中转上传）" as OSS
}

Android ..> Shared : <<use>>
Desktop ..> Shared : <<use>>

Android ..> Service : HTTPS / JSON / JWT
Desktop ..> Service : HTTPS / JSON / JWT
MiniApp ..> Service : HTTPS / JSON
Admin ..> Service : HTTPS / JSON

Service ..> Repo : <<use>>
Service ..> Redis : <<use>>
Repo ..> PG : pgx
Repo ..> OSS : SDK
@enduml
```

---

## 3. 部署图（Deployment Diagram · 物理视图）

双服务器拓扑。端口按实际配置标注：对外只暴露 9200（Nginx），API 槽位 Docker 容器内 9201 / systemd 蓝 9200 绿 9201。说明性 note 置于节点下方，避免超出画布被裁切。

```plantuml
@startuml
title 宠物寄养服务预约系统 · 部署图（v2.3.0）

skinparam defaultFontName "Microsoft YaHei"
skinparam shadowing false

node "客户端 / 公网" as Client {
  [Android APK\nbus arm64-v8a]
  [桌面安装包\nWindows / Linux / macOS]
  [微信小程序]
  [浏览器 - 管理后台]
}

node "后端机（唯一公网入口 · 只暴露 9200）" as Backend {
  [Nginx\n对外 :9200\nhttp/https 分流 · 管理端静态资源\nupstream 原子切换]
  [Go API 蓝槽\n当前活跃\nDocker:容器内 9201\nsystemd:9200]
  [Go API 绿槽\n待命 :9201]
  [Redis\n回环绑定 · 不对外]
}

node "存储机（安全组 IP 白名单）" as Storage {
  database "PostgreSQL 18\nssl=on · 非默认端口"
  [MinIO\n对象存储]
}

Client ..> Backend : 公网 HTTPS :9200
Backend ..> Storage : 公网直连\n安全组白名单 + TLS

note bottom of Client
  /health 健康门禁 · 蓝绿失败自动回滚
end note

note bottom of Storage
  Redis 回环绑定不对外
  MinIO 不开匿名桶，客户端经 Go 中转
end note
@enduml
```

---

## 4. 功能结构图（功能树）

四个子系统嵌套进根节点矩形，形成标准树状层次，避免根节点被甩到角落、连线绕圈。

```plantuml
@startuml
title 宠物寄养服务预约系统 · 功能结构图（v2.3.0）

skinparam defaultFontName "Microsoft YaHei"
skinparam shadowing false

rectangle "宠物寄养服务预约系统" as Root {
  rectangle "主人端" as Owner {
    rectangle "账号与认证" as O1
    rectangle "宠物档案管理" as O2
    rectangle "机构浏览 / 搜索 / 收藏 / 对比" as O3
    rectangle "预约下单与费用预览" as O4
    rectangle "支付 / 钱包 / 优惠券" as O5
    rectangle "寄养动态 / 消息" as O6
    rectangle "评价与追评" as O7
  }
  rectangle "机构端" as Sitter {
    rectangle "入驻与资质" as S1
    rectangle "建店 / 定价 / 容量" as S2
    rectangle "接单与履约" as S3
    rectangle "动态发布" as S4
    rectangle "评价回复 / 收益结算" as S5
  }
  rectangle "管理后台" as Admin {
    rectangle "机构 / 订单 / 资金审核" as A1
    rectangle "用户 / 权限 / 内容管理" as A2
    rectangle "工单 / 退款 / 结算" as A3
    rectangle "审计日志 / 系统运维" as A4
  }
  rectangle "后端服务" as Server {
    rectangle "认证授权 / RBAC" as B1
    rectangle "机构 / 宠物 / 订单" as B2
    rectangle "支付 / 钱包 / 结算" as B3
    rectangle "内容 / 售后 / 检索推荐" as B4
  }
}
@enduml
```

---

## 5. 时序图（Sequence Diagram · 下单支付主流程）

participant 采用「引号显示名 + `as` 别名」写法，兼容新版 PlantUML 严格解析。

```plantuml
@startuml
title 宠物寄养预约与履约 · 端到端时序图（v2.3.0）

skinparam defaultFontName "Microsoft YaHei"
skinparam shadowing false
skinparam sequenceMessageAlign center
skinparam maxMessageSize 180
scale 0.85

participant "主人" as O
participant "机构" as S
participant "后台" as A
participant "Go API" as API
participant "数据库" as DB

== 下单与支付 ==
O -> API: 创建订单
API -> DB: 事务：锁行→占容量→写快照
DB --> API: 订单号与金额
API --> O: 待支付
O -> API: 支付
API -> DB: 写支付 + 流水
API --> O: 已预约

== 接单与履约 ==
S -> API: 接单
API -> DB: 状态→已接单
S -> API: 发布动态
API -> DB: 写动态 + 通知
O -> API: 查看动态

== 完成与结算 ==
S -> API: 完成订单
API -> DB: 完成 + 归还容量
O -> API: 评价
API -> DB: 写评价 + 聚合评分
S -> API: 回复 / 结算申请
A -> API: 审核结算
@enduml
```

---

## 6. 状态图（State Machine Diagram · 订单状态机）

```plantuml
@startuml
title 订单状态机（v2.3.0）

skinparam defaultFontName "Microsoft YaHei"
skinparam shadowing false

' 显式定义状态，格式：state "显示名称" as 英文别名
state "待支付" as PendingPayment
state "已预约" as Reserved
state "已取消" as Cancelled
state "已接单" as Accepted
state "已拒绝" as Rejected
state "寄养中" as Boarding
state "已完成" as Completed
state "已评价" as Reviewed

[*] --> PendingPayment : 提交订单

PendingPayment --> Reserved : 支付成功
PendingPayment --> Cancelled : 超时未支付 / 主人取消

Reserved --> Accepted : 机构接单
Reserved --> Cancelled : 主人取消
Reserved --> Rejected : 机构拒单

Accepted --> Boarding : 开始寄养
Boarding --> Completed : 完成订单
Completed --> Reviewed : 主人评价

Reviewed --> [*]
Cancelled --> [*]
Rejected --> [*]

note right of Cancelled
  已取消 / 已拒绝均释放占用容量
end note
note left of Completed
  归还未消费的未来夜间
end note
@enduml
```

---

## 7. ER 图（Entity Relationship Diagram · 核心数据模型）

仅绘制主干实体（41 表中的核心 11 个），适合放入报告；如需完整 41 表可在此基础上扩展。

```plantuml
@startuml
title 宠物寄养服务预约系统 · ER 图（核心实体，v2.3.0）

skinparam defaultFontName "Microsoft YaHei"
skinparam shadowing false

entity "users\n用户" as users {
  * id : bigint <<PK>>
  --
  * phone : varchar <<UQ>>
  role : enum(owner/sitter/admin)
  password_hash : varchar
  deleted_at : timestamp
}

entity "pets\n宠物档案" as pets {
  * id : bigint <<PK>>
  --
  * owner_id : bigint <<FK>>
  name : varchar
  species : varchar
  vaccinated : boolean
}

entity "sitters\n机构" as sitters {
  * id : bigint <<PK>>
  --
  * owner_id : bigint <<FK>>
  name : varchar
  price_mode : enum
  avg_score : numeric
  review_count : int
}

entity "orders\n订单" as orders {
  * id : bigint <<PK>>
  --
  * sitter_id : bigint <<FK>>
  * owner_id : bigint <<FK>>
  status : enum(order_status)
  amount_snapshot : numeric
  days_snapshot : int
}

entity "order_pet\n订单-宠物" as order_pet {
  * id : bigint <<PK>>
  --
  * order_id : bigint <<FK>>
  * pet_id : bigint <<FK>>
}

entity "dynamics\n寄养动态" as dynamics {
  * id : bigint <<PK>>
  --
  * order_id : bigint <<FK>>
  sitter_user_id : bigint <<FK>>
  content : text
}

entity "reviews\n评价" as reviews {
  * id : bigint <<PK>>
  --
  * order_id : bigint <<FK, UQ>>
  * sitter_id : bigint <<FK>>
  score : numeric
}

entity "payments\n支付流水" as payments {
  * id : bigint <<PK>>
  --
  * order_id : bigint <<FK>>
  amount : numeric
  channel : varchar
}

entity "sitter_daily_capacity\n每日容量" as capacity {
  * id : bigint <<PK>>
  --
  * sitter_id : bigint <<FK>>
  capacity_date : date
  used : int
}

entity "wallets\n钱包" as wallets {
  * user_id : bigint <<PK, FK>>
  --
  balance : numeric
}

entity "wallet_transactions\n资金流水" as wallet_tx {
  * id : bigint <<PK>>
  --
  * user_id : bigint <<FK>>
  amount : numeric
}

users ||--o{ pets : "主人拥有"
users ||--o{ sitters : "绑定机构账号"
users ||--o| wallets : "拥有钱包"
wallets ||--o{ wallet_tx : "产生流水"
sitters ||--o{ orders : "接收"
users ||--o{ orders : "主人下单"
orders ||--o{ order_pet : "包含宠物"
pets ||--o{ order_pet : "被寄养"
orders ||--o{ dynamics : "发布动态"
orders ||--o| reviews : "一单一评"
sitters ||--o{ reviews : "收到评价"
orders ||--o{ payments : "产生支付"
sitters ||--o{ capacity : "配置容量"
@enduml
```

---

## 使用说明

- 每张图的 `@startuml` ... `@enduml` 之间是独立的 PlantUML 源码，复制到 [PlantUML Web Server](https://www.plantuml.com/plantuml/) 或任意支持 PlantUML 的编辑器（如 IntelliJ / VS Code 插件）即可渲染。
- 均为白底渲染（未设置深色 skinparam），可直接截图插入 Word 报告。
- 若需调整尺寸，在对应图的 `skinparam` 区加 `scale 1.5` 或 `skinparam nodesep/ranksep` 控制间距。
