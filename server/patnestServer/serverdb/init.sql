-- ==================================================================
-- PatNest Database Init Script (PostgreSQL 16+)
-- 唯一基线：已合并原迁移 0001~0021 的全部「净效果」，新库只需执行本文件。
-- Run: psql -v ON_ERROR_STOP=1 -U <user> -d <database> -f init.sql
--
-- 幂等性：本脚本可重复执行 —— 建表/索引/视图/函数用 IF NOT EXISTS / OR REPLACE，
--   触发器先 DROP IF EXISTS 再建，种子数据按唯一键判重。重跑不会报错、不会产生重复行。
-- 编码：文件为 UTF-8（无 BOM）。下面的 SET client_encoding 必须在任何中文注释之前执行，
--   否则 Windows 上 client_encoding=GBK 的 psql 会在读到中文时直接报 invalid byte sequence。
--
-- 合并口径（2026-11-09）：
--   * 35 张在用的表全部内联定义；列、约束、索引、函数、触发器、视图均为最终状态。
--   * 不再包含 live_rooms / live_viewers / live_messages（0020 建、0021 删，净效果为零）。
--   * 不再包含 sitters.live_banned_at / live_ban_reason，也不含 lives.manage 权限行。
--   * 不含只针对老库的一次性数据修复（0015 的容量账目重算、0016 的 scope 回填、
--     0018 的脏值检查、0019 的约束替换）——新库没有历史数据，这些语句无对象可作用。
--   * 补上了原本缺失的 fn_sync_capacity_rows 定义（旧文件创建了引用它的触发器却没定义函数，
--     单独执行会在 CREATE TRIGGER 处失败）。
--   * migrations/ 目录只保留 0001_baseline.sql（内容不得改动，老库依赖它的校验和）；
--     0002~0021 已归档到 migrations/archive/，仅供追溯，不再被 cmd/migrate 加载。
-- ==================================================================

SET client_encoding = 'UTF8';

BEGIN;

SET timezone = 'Asia/Shanghai';

-- ==================================================================
-- 1. Extensions
-- ==================================================================
-- 托管型 PostgreSQL（云数据库）常不给普通账号建扩展的权限。这里捕获异常降级：
-- pg_trgm 缺失只会跳过下面 3 个模糊搜索索引，其余对象照常创建，脚本不再整块回滚。
DO $$ BEGIN
    CREATE EXTENSION IF NOT EXISTS pg_trgm;
EXCEPTION WHEN OTHERS THEN
    RAISE NOTICE 'pg_trgm 不可用（%），跳过模糊搜索索引', SQLERRM;
END $$;

DO $$ BEGIN
    CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
EXCEPTION WHEN OTHERS THEN
    RAISE NOTICE 'uuid-ossp 不可用（%），当前无对象依赖它，可忽略', SQLERRM;
END $$;

-- ==================================================================
-- 2. Custom Types (ENUM)
-- ==================================================================
DO $$ BEGIN
    CREATE TYPE user_role AS ENUM ('owner','sitter','admin');
EXCEPTION WHEN duplicate_object THEN NULL;
END $$;

DO $$ BEGIN
    CREATE TYPE pet_species AS ENUM ('cat','dog','other');
EXCEPTION WHEN duplicate_object THEN NULL;
END $$;

DO $$ BEGIN
    CREATE TYPE pet_size AS ENUM ('tiny','small','medium','large','xlarge');
EXCEPTION WHEN duplicate_object THEN NULL;
END $$;

DO $$ BEGIN
    CREATE TYPE sitter_type AS ENUM ('home','shop','visit','other');
EXCEPTION WHEN duplicate_object THEN NULL;
END $$;

DO $$ BEGIN
    CREATE TYPE sitter_status AS ENUM ('pending','active','inactive','banned','rejected');
EXCEPTION WHEN duplicate_object THEN NULL;
END $$;

DO $$ BEGIN
    CREATE TYPE order_status AS ENUM ('pending_pay','confirmed','accepted','staying','completed','evaluated','cancelled','rejected');
EXCEPTION WHEN duplicate_object THEN NULL;
END $$;

DO $$ BEGIN
    CREATE TYPE payment_status AS ENUM ('none','paid','refunded');
EXCEPTION WHEN duplicate_object THEN NULL;
END $$;

DO $$ BEGIN
    CREATE TYPE pay_method AS ENUM ('mock','alipay','wechat','balance');
EXCEPTION WHEN duplicate_object THEN NULL;
END $$;

DO $$ BEGIN
    CREATE TYPE notification_type AS ENUM ('order_created','order_accepted','order_cancelled','order_rejected','order_completed','dynamics_published','review_received','system_announcement');
EXCEPTION WHEN duplicate_object THEN NULL;
END $$;

DO $$ BEGIN
    CREATE TYPE notification_scope AS ENUM ('personal','business');
EXCEPTION WHEN duplicate_object THEN NULL;
END $$;

DO $$ BEGIN
    CREATE TYPE price_mode AS ENUM ('per_day','per_visit');
EXCEPTION WHEN duplicate_object THEN NULL;
END $$;
-- ==================================================================
-- 3. Tables
-- ==================================================================

-- 3.1 users
CREATE TABLE IF NOT EXISTS users (
    id              BIGSERIAL       PRIMARY KEY,
    username        VARCHAR(50)     NOT NULL,
    password_hash   VARCHAR(256)    NOT NULL,
    nickname        VARCHAR(64),
    phone           VARCHAR(20),
    email           VARCHAR(128),
    role            user_role       NOT NULL DEFAULT 'owner',
    avatar_url      TEXT,
    status          SMALLINT        NOT NULL DEFAULT 1,
    login_fail_count SMALLINT       NOT NULL DEFAULT 0,
    locked_until    TIMESTAMPTZ,
    -- 0003 / 0006 / 0013 净效果：管理员会话、积分、联系信息、注销冷静期、令牌吊销点
    session_key     VARCHAR(64),
    points          INT             NOT NULL DEFAULT 0 CHECK (points >= 0),
    address         VARCHAR(255),
    contact_name    VARCHAR(64),
    contact_phone   VARCHAR(20),
    cancel_mode         VARCHAR(16),
    cancel_requested_at TIMESTAMPTZ,
    cancel_deadline     TIMESTAMPTZ,
    -- 0007：注销执行失败的重试控制
    cancel_attempts     INT             NOT NULL DEFAULT 0,
    cancel_last_error   VARCHAR(255),
    cancel_blocked_at   TIMESTAMPTZ,
    token_invalid_at    TIMESTAMPTZ,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ     NOT NULL DEFAULT now(),
    deleted_at      TIMESTAMPTZ,
    -- 手机号全表唯一（含软删行）；用户名改由部分唯一索引约束，仅未删除行唯一（0005）
    CONSTRAINT uq_users_phone UNIQUE (phone)
);

COMMENT ON TABLE users IS 'Platform users: pet owners, sitters, admins';
COMMENT ON COLUMN users.status IS '1=active, 0=disabled';
COMMENT ON COLUMN users.locked_until IS 'Account lock expiry after repeated login failures';

-- 3.2 pets
CREATE TABLE IF NOT EXISTS pets (
    id              BIGSERIAL       PRIMARY KEY,
    owner_id        BIGINT          NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    name            VARCHAR(64)     NOT NULL,
    species         pet_species     NOT NULL,
    breed           VARCHAR(100),
    body_size       pet_size        DEFAULT 'medium',
    age_month       INT             CHECK (age_month >= 0 AND age_month <= 360),
    weight_kg       NUMERIC(6,2),
    vaccinated      BOOLEAN         NOT NULL DEFAULT FALSE,
    sterilized      BOOLEAN         NOT NULL DEFAULT FALSE,
    photo_urls      TEXT[]          DEFAULT '{}',
    avatar_url      TEXT,
    notes           TEXT,
    -- 0003 净效果：免疫证明上传与后台核验
    vaccine_urls        TEXT[]      DEFAULT '{}',
    vaccine_status      VARCHAR(16) NOT NULL DEFAULT 'missing'
                        CHECK (vaccine_status IN ('missing','pending','verified','rejected')),
    vaccine_note        VARCHAR(255),
    vaccine_verified_by BIGINT      REFERENCES users(id) ON DELETE SET NULL,
    vaccine_verified_at TIMESTAMPTZ,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ     NOT NULL DEFAULT now(),
    deleted_at      TIMESTAMPTZ
);

COMMENT ON TABLE pets IS 'Pet profiles owned by users (soft-delete via deleted_at)';

-- 3.3 sitters (boarding facilities)
CREATE TABLE IF NOT EXISTS sitters (
    id              BIGSERIAL       PRIMARY KEY,
    user_id         BIGINT          NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    name            VARCHAR(128)    NOT NULL,
    type            sitter_type     NOT NULL DEFAULT 'home',
    description     TEXT,
    daily_price     NUMERIC(10,2)   NOT NULL DEFAULT 0 CHECK (daily_price >= 0),
    visit_price     NUMERIC(10,2)   DEFAULT 0 CHECK (visit_price >= 0),
    price_mode      price_mode      NOT NULL DEFAULT 'per_day',
    capacity        INT             NOT NULL DEFAULT 1 CHECK (capacity >= 1),
    address         VARCHAR(255),
    latitude        NUMERIC(10,7),
    longitude       NUMERIC(10,7),
    avg_score       NUMERIC(3,2)    NOT NULL DEFAULT 0 CHECK (avg_score BETWEEN 0 AND 5),
    review_count    INT             NOT NULL DEFAULT 0,
    cover_photo_url TEXT,
    detail_photos   TEXT[]          DEFAULT '{}',
    contact_person  VARCHAR(64),
    license_photo_url TEXT,
    id_card_photo_url TEXT,
    review_note     VARCHAR(255),
    status          sitter_status   NOT NULL DEFAULT 'pending',
    banned_at       TIMESTAMPTZ,
    banned_reason   VARCHAR(255),
    commission_rate NUMERIC(5,4)    CHECK (commission_rate IS NULL OR (commission_rate >= 0 AND commission_rate <= 1)),
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ     NOT NULL DEFAULT now(),
    deleted_at      TIMESTAMPTZ
);

COMMENT ON TABLE sitters IS 'Boarding facilities / individual sitters';
COMMENT ON COLUMN sitters.avg_score IS 'Denormalized average, updated by trigger on review insert';
COMMENT ON COLUMN sitters.commission_rate IS '机构单独佣金比例 0-1，NULL 表示跟随 platform_settings.commission_rate';

-- 3.4 sitter_daily_capacity
CREATE TABLE IF NOT EXISTS sitter_daily_capacity (
    id              BIGSERIAL       PRIMARY KEY,
    sitter_id       BIGINT          NOT NULL REFERENCES sitters(id) ON DELETE CASCADE,
    cap_date        DATE            NOT NULL,
    used            INT             NOT NULL DEFAULT 0 CHECK (used >= 0),
    capacity        INT             NOT NULL DEFAULT 1 CHECK (capacity >= 1),
    is_override     BOOLEAN         NOT NULL DEFAULT FALSE, -- TRUE=机构显式设置的该日容量，FALSE=跟随机构默认容量
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ     NOT NULL DEFAULT now(),
    CONSTRAINT uq_sitter_date UNIQUE (sitter_id, cap_date)
);

COMMENT ON TABLE sitter_daily_capacity IS 'Per-day capacity tracking for each sitter';

-- 3.5 orders
CREATE TABLE IF NOT EXISTS orders (
    id                BIGSERIAL       PRIMARY KEY,
    order_no          VARCHAR(64)     NOT NULL,
    owner_id          BIGINT          NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    sitter_id         BIGINT          NOT NULL REFERENCES sitters(id) ON DELETE RESTRICT,
    status            order_status    NOT NULL DEFAULT 'pending_pay',
    start_date        DATE            NOT NULL,
    end_date          DATE            NOT NULL,
    total_days        INT             NOT NULL DEFAULT 0,
    pet_count         SMALLINT        NOT NULL DEFAULT 1 CHECK (pet_count BETWEEN 1 AND 10),
    estimated_amount  NUMERIC(12,2)   NOT NULL DEFAULT 0,
    total_amount      NUMERIC(12,2)   NOT NULL DEFAULT 0,
    -- 0003 净效果：券 / 积分 / 加购的金额拆分与改期次数
    coupon_id         BIGINT,
    discount_amount   NUMERIC(12,2)   NOT NULL DEFAULT 0,
    points_used       INT             NOT NULL DEFAULT 0,
    addon_amount      NUMERIC(12,2)   NOT NULL DEFAULT 0,
    rescheduled_count INT             NOT NULL DEFAULT 0,
    payment_status    payment_status  NOT NULL DEFAULT 'none',
    cancel_reason     VARCHAR(255),
    cancel_time       TIMESTAMPTZ,
    paid_at           TIMESTAMPTZ,
    accepted_at       TIMESTAMPTZ,
    completed_at      TIMESTAMPTZ,
    evaluated_at      TIMESTAMPTZ,
    remark            TEXT,
    created_at        TIMESTAMPTZ     NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ     NOT NULL DEFAULT now(),
    deleted_at        TIMESTAMPTZ,
    CONSTRAINT uq_orders_no UNIQUE (order_no),
    CONSTRAINT chk_orders_dates CHECK (end_date > start_date)
);

COMMENT ON TABLE orders IS 'Core booking orders';

-- 3.6 order_pet (many-to-many: order <-> pet)
CREATE TABLE IF NOT EXISTS order_pet (
    id              BIGSERIAL       PRIMARY KEY,
    order_id        BIGINT          NOT NULL REFERENCES orders(id) ON DELETE CASCADE,
    pet_id          BIGINT          NOT NULL REFERENCES pets(id) ON DELETE RESTRICT,
    stay_date       DATE            NOT NULL,
    checkin_time    TIME            DEFAULT '09:00:00',
    checkout_time   TIME,
    status          VARCHAR(20)     DEFAULT 'assigned' CHECK (status IN ('assigned','staying','picked_up','cancelled')),
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ     NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_order_pet ON order_pet (order_id, pet_id, stay_date);

-- 3.7 dynamics (boarding updates posted by sitter)
CREATE TABLE IF NOT EXISTS dynamics (
    id              BIGSERIAL       PRIMARY KEY,
    order_id        BIGINT          NOT NULL REFERENCES orders(id) ON DELETE CASCADE,
    sitter_id       BIGINT          NOT NULL REFERENCES sitters(id) ON DELETE RESTRICT,
    author_id       BIGINT          NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    content         TEXT            NOT NULL CHECK (length(content) <= 2000),
    photos          TEXT[]          DEFAULT '{}',
    tags            TEXT[]          DEFAULT '{}',
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ     NOT NULL DEFAULT now()
);

COMMENT ON TABLE dynamics IS 'Boarding progress updates with photos/tags';

-- 3.8 reviews
CREATE TABLE IF NOT EXISTS reviews (
    id                BIGSERIAL       PRIMARY KEY,
    order_id          BIGINT          NOT NULL REFERENCES orders(id) ON DELETE RESTRICT,
    sitter_id         BIGINT          NOT NULL REFERENCES sitters(id) ON DELETE RESTRICT,
    owner_id          BIGINT          NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    overall_score     SMALLINT        NOT NULL CHECK (overall_score BETWEEN 1 AND 5),
    service_score     SMALLINT        NOT NULL CHECK (service_score BETWEEN 1 AND 5),
    environment_score SMALLINT        NOT NULL CHECK (environment_score BETWEEN 1 AND 5),
    hygiene_score     SMALLINT        NOT NULL CHECK (hygiene_score BETWEEN 1 AND 5),
    owner_comment     TEXT            CHECK (length(owner_comment) <= 1000),
    owner_photos      TEXT[]          DEFAULT '{}',
    reply_text        TEXT,
    reply_at          TIMESTAMPTZ,
    is_visible        BOOLEAN         NOT NULL DEFAULT TRUE,
    created_at        TIMESTAMPTZ     NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ     NOT NULL DEFAULT now(),
    CONSTRAINT uq_reviews_order UNIQUE (order_id)
);

COMMENT ON TABLE reviews IS 'One review per completed order';

-- 3.9 payments
CREATE TABLE IF NOT EXISTS payments (
    id              BIGSERIAL       PRIMARY KEY,
    order_id        BIGINT          NOT NULL REFERENCES orders(id) ON DELETE RESTRICT,
    amount          NUMERIC(12,2)   NOT NULL CONSTRAINT payments_amount_check CHECK (amount >= 0), -- 允许 0：全额抵扣/免费订单（0010）
    method          pay_method      NOT NULL DEFAULT 'mock',
    transaction_id  VARCHAR(128),
    status          VARCHAR(20)     NOT NULL DEFAULT 'created' CHECK (status IN ('created','paid','failed','refunded')),
    raw_response    JSONB,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT now(),
    paid_at         TIMESTAMPTZ,
    refunded_at     TIMESTAMPTZ
);

-- 3.10 notifications
CREATE TABLE IF NOT EXISTS notifications (
    id              BIGSERIAL       PRIMARY KEY,
    user_id         BIGINT          NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    type            notification_type NOT NULL,
    scope           notification_scope NOT NULL DEFAULT 'personal',
    title           VARCHAR(128)    DEFAULT '',
    body            TEXT            NOT NULL,
    extra           JSONB           DEFAULT '{}',
    is_read         BOOLEAN         NOT NULL DEFAULT FALSE,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT now()
);

COMMENT ON COLUMN notifications.scope IS
    'personal=主人视角消息（pet-owner 接口）；business=机构视角消息（sitter 接口）';
CREATE INDEX IF NOT EXISTS idx_notifications_user_scope_time
    ON notifications (user_id, scope, created_at DESC);

-- 3.11 audit_logs
CREATE TABLE IF NOT EXISTS audit_logs (
    id              BIGSERIAL       PRIMARY KEY,
    actor_id        BIGINT          REFERENCES users(id) ON DELETE SET NULL,
    action          VARCHAR(100)    NOT NULL,
    resource_type   VARCHAR(50)     NOT NULL,
    resource_id     BIGINT,
    detail          JSONB           DEFAULT '{}',
    ip_address      INET,
    user_agent      TEXT,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT now()
);

COMMENT ON TABLE audit_logs IS 'Platform audit trail for admin actions';

-- 3.12 refresh_tokens (JWT refresh token storage)
CREATE TABLE IF NOT EXISTS refresh_tokens (
    id              BIGSERIAL       PRIMARY KEY,
    user_id         BIGINT          NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token_hash      VARCHAR(128)    NOT NULL,
    expires_at      TIMESTAMPTZ     NOT NULL,
    revoked_at      TIMESTAMPTZ,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_refresh_token_hash ON refresh_tokens (token_hash);
-- ==================================================================
-- 3.13 ~ 3.32 原迁移 0002/0003/0004/0011/0012 建表语句的最终状态
-- 依赖关系已排序：先被引用的表在前。全部定义放在函数/触发器之前，
-- 否则 fn_restore_order_benefits 里的 coupons / user_coupons / points_ledger
-- 会在 CREATE FUNCTION 的语法检查阶段报「表不存在」。
-- ==================================================================

-- 3.13 发布记录（0002）
CREATE TABLE IF NOT EXISTS system_release (
    id          BIGSERIAL    PRIMARY KEY,
    app         VARCHAR(32)  NOT NULL,
    version     VARCHAR(64)  NOT NULL,
    commit_sha  VARCHAR(64)  DEFAULT '',
    slot        VARCHAR(16)  DEFAULT '',
    note        TEXT         DEFAULT '',
    deployed_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_system_release_app_time ON system_release (app, deployed_at DESC);

-- 3.14 优惠券模板（0003 + 0009 sitter_id + 0018 percent 范围）
CREATE TABLE IF NOT EXISTS coupons (
    id              BIGSERIAL       PRIMARY KEY,
    code            VARCHAR(32)     NOT NULL,
    sitter_id       BIGINT          REFERENCES sitters(id) ON DELETE CASCADE,
    title           VARCHAR(128)    NOT NULL,
    type            VARCHAR(16)     NOT NULL DEFAULT 'fixed' CHECK (type IN ('fixed','percent')),
    value           NUMERIC(10,2)   NOT NULL CHECK (value > 0),
    min_amount      NUMERIC(10,2)   NOT NULL DEFAULT 0 CHECK (min_amount >= 0),
    total_qty       INT             NOT NULL DEFAULT 0 CHECK (total_qty >= 0),
    claimed_qty     INT             NOT NULL DEFAULT 0 CHECK (claimed_qty >= 0),
    used_qty        INT             NOT NULL DEFAULT 0 CHECK (used_qty >= 0),
    per_user        INT             NOT NULL DEFAULT 1 CHECK (per_user >= 1),
    starts_at       TIMESTAMPTZ     NOT NULL DEFAULT now(),
    ends_at         TIMESTAMPTZ,
    enabled         BOOLEAN         NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ     NOT NULL DEFAULT now(),
    CONSTRAINT ck_coupons_percent_range
        CHECK (type <> 'percent' OR (value > 0 AND value <= 100))
);
-- 0009 净效果：code 从全局唯一改为「同一发放主体内唯一」
CREATE UNIQUE INDEX IF NOT EXISTS uq_coupons_platform_code ON coupons (code) WHERE sitter_id IS NULL;
CREATE UNIQUE INDEX IF NOT EXISTS uq_coupons_sitter_code ON coupons (sitter_id, code);
CREATE INDEX IF NOT EXISTS idx_coupons_sitter ON coupons (sitter_id, enabled) WHERE sitter_id IS NOT NULL;

-- 3.15 用户持有的券（0003）
CREATE TABLE IF NOT EXISTS user_coupons (
    id              BIGSERIAL       PRIMARY KEY,
    coupon_id       BIGINT          NOT NULL REFERENCES coupons(id) ON DELETE CASCADE,
    user_id         BIGINT          NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    status          VARCHAR(16)     NOT NULL DEFAULT 'unused' CHECK (status IN ('unused','used','expired')),
    order_id        BIGINT          REFERENCES orders(id) ON DELETE SET NULL,
    claimed_at      TIMESTAMPTZ     NOT NULL DEFAULT now(),
    used_at         TIMESTAMPTZ,
    CONSTRAINT uq_user_coupon UNIQUE (coupon_id, user_id)
);
CREATE INDEX IF NOT EXISTS idx_user_coupons_user ON user_coupons (user_id, status);

-- 3.16 积分流水（0003）
CREATE TABLE IF NOT EXISTS points_ledger (
    id              BIGSERIAL       PRIMARY KEY,
    user_id         BIGINT          NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    change_amount   INT             NOT NULL,
    balance         INT             NOT NULL,
    reason          VARCHAR(64)     NOT NULL,
    ref_type        VARCHAR(32),
    ref_id          BIGINT,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_points_ledger_user ON points_ledger (user_id, created_at DESC);

-- 3.17 收藏（0003）
CREATE TABLE IF NOT EXISTS favorites (
    id              BIGSERIAL       PRIMARY KEY,
    user_id         BIGINT          NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    sitter_id       BIGINT          NOT NULL REFERENCES sitters(id) ON DELETE CASCADE,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT now(),
    CONSTRAINT uq_favorite UNIQUE (user_id, sitter_id)
);

-- 3.18 节假日 / 旺季上浮规则（0003）
CREATE TABLE IF NOT EXISTS sitter_price_rules (
    id              BIGSERIAL       PRIMARY KEY,
    sitter_id       BIGINT          NOT NULL REFERENCES sitters(id) ON DELETE CASCADE,
    name            VARCHAR(64)     NOT NULL,
    start_date      DATE            NOT NULL,
    end_date        DATE            NOT NULL,
    multiplier      NUMERIC(4,2)    NOT NULL DEFAULT 1.20 CHECK (multiplier BETWEEN 0.50 AND 5.00),
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT now(),
    CONSTRAINT chk_price_rule_dates CHECK (end_date >= start_date)
);
CREATE INDEX IF NOT EXISTS idx_price_rules_sitter ON sitter_price_rules (sitter_id, start_date);

-- 3.19 加购服务与订单加购明细（0003）
CREATE TABLE IF NOT EXISTS sitter_addons (
    id              BIGSERIAL       PRIMARY KEY,
    sitter_id       BIGINT          NOT NULL REFERENCES sitters(id) ON DELETE CASCADE,
    name            VARCHAR(64)     NOT NULL,
    price           NUMERIC(10,2)   NOT NULL CHECK (price >= 0),
    unit            VARCHAR(16)     NOT NULL DEFAULT 'per_order',
    enabled         BOOLEAN         NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT now()
);
CREATE TABLE IF NOT EXISTS order_addons (
    id              BIGSERIAL       PRIMARY KEY,
    order_id        BIGINT          NOT NULL REFERENCES orders(id) ON DELETE CASCADE,
    addon_id        BIGINT          REFERENCES sitter_addons(id) ON DELETE SET NULL,
    name            VARCHAR(64)     NOT NULL,
    price           NUMERIC(10,2)   NOT NULL DEFAULT 0,
    qty             INT             NOT NULL DEFAULT 1 CHECK (qty >= 1),
    amount          NUMERIC(12,2)   NOT NULL DEFAULT 0
);

-- 3.20 动态点赞与留言（0003）
CREATE TABLE IF NOT EXISTS dynamic_likes (
    id              BIGSERIAL       PRIMARY KEY,
    dynamic_id      BIGINT          NOT NULL REFERENCES dynamics(id) ON DELETE CASCADE,
    user_id         BIGINT          NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT now(),
    CONSTRAINT uq_dynamic_like UNIQUE (dynamic_id, user_id)
);
CREATE TABLE IF NOT EXISTS dynamic_comments (
    id              BIGSERIAL       PRIMARY KEY,
    dynamic_id      BIGINT          NOT NULL REFERENCES dynamics(id) ON DELETE CASCADE,
    user_id         BIGINT          NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    content         VARCHAR(500)    NOT NULL,
    status          VARCHAR(16)     NOT NULL DEFAULT 'visible' CHECK (status IN ('visible','hidden')),
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_dynamic_comments ON dynamic_comments (dynamic_id, created_at);

-- 3.21 追评（0003）
CREATE TABLE IF NOT EXISTS review_followups (
    id              BIGSERIAL       PRIMARY KEY,
    review_id       BIGINT          NOT NULL REFERENCES reviews(id) ON DELETE CASCADE,
    owner_id        BIGINT          NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    content         VARCHAR(1000)   NOT NULL,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT now()
);

-- 3.22 退款申请（0003）
CREATE TABLE IF NOT EXISTS refund_requests (
    id              BIGSERIAL       PRIMARY KEY,
    order_id        BIGINT          NOT NULL REFERENCES orders(id) ON DELETE CASCADE,
    owner_id        BIGINT          NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    amount          NUMERIC(12,2)   NOT NULL CHECK (amount > 0),
    reason          VARCHAR(500)    NOT NULL,
    status          VARCHAR(16)     NOT NULL DEFAULT 'pending'
                    CHECK (status IN ('pending','approved','rejected','cancelled')),
    admin_id        BIGINT          REFERENCES users(id) ON DELETE SET NULL,
    admin_note      VARCHAR(500),
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT now(),
    handled_at      TIMESTAMPTZ
);
CREATE INDEX IF NOT EXISTS idx_refund_requests_status ON refund_requests (status, created_at DESC);

-- 3.23 售后工单与会话（0003）
CREATE TABLE IF NOT EXISTS support_tickets (
    id              BIGSERIAL       PRIMARY KEY,
    ticket_no       VARCHAR(32)     NOT NULL UNIQUE,
    user_id         BIGINT          NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    category        VARCHAR(32)     NOT NULL DEFAULT 'other',
    title           VARCHAR(128)    NOT NULL,
    content         TEXT            NOT NULL,
    status          VARCHAR(16)     NOT NULL DEFAULT 'open'
                    CHECK (status IN ('open','processing','closed')),
    priority        VARCHAR(16)     NOT NULL DEFAULT 'normal'
                    CHECK (priority IN ('low','normal','high')),
    assigned_admin_id BIGINT        REFERENCES users(id) ON DELETE SET NULL,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ     NOT NULL DEFAULT now()
);
CREATE TABLE IF NOT EXISTS ticket_messages (
    id              BIGSERIAL       PRIMARY KEY,
    ticket_id       BIGINT          NOT NULL REFERENCES support_tickets(id) ON DELETE CASCADE,
    sender_id       BIGINT          NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    sender_role     VARCHAR(16)     NOT NULL,
    content         TEXT            NOT NULL,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_ticket_messages ON ticket_messages (ticket_id, created_at);

-- 3.24 机构结算（0003）
CREATE TABLE IF NOT EXISTS sitter_settlements (
    id              BIGSERIAL       PRIMARY KEY,
    -- 财务凭证：机构有结算记录时不允许物理删除（0005 由 CASCADE 改为 RESTRICT）
    sitter_id       BIGINT          NOT NULL REFERENCES sitters(id) ON DELETE RESTRICT,
    period_start    DATE            NOT NULL,
    period_end      DATE            NOT NULL,
    order_count     INT             NOT NULL DEFAULT 0,
    gross_amount    NUMERIC(12,2)   NOT NULL DEFAULT 0,
    commission_amount NUMERIC(12,2) NOT NULL DEFAULT 0,
    net_amount      NUMERIC(12,2)   NOT NULL DEFAULT 0,
    commission_rate NUMERIC(5,4),
    status          VARCHAR(16)     NOT NULL DEFAULT 'pending'
                    CHECK (status IN ('pending','paid','rejected')),
    applied_at      TIMESTAMPTZ     NOT NULL DEFAULT now(),
    paid_at         TIMESTAMPTZ,
    admin_id        BIGINT          REFERENCES users(id) ON DELETE SET NULL,
    note            VARCHAR(500),
    CONSTRAINT chk_settlement_dates CHECK (period_end >= period_start)
);
CREATE INDEX IF NOT EXISTS idx_settlements_status ON sitter_settlements (status, applied_at DESC);

-- 3.25 通知模板（0003）
CREATE TABLE IF NOT EXISTS notification_templates (
    id              BIGSERIAL       PRIMARY KEY,
    code            VARCHAR(64)     NOT NULL UNIQUE,
    name            VARCHAR(64)     NOT NULL,
    title           VARCHAR(128)    NOT NULL,
    content         TEXT            NOT NULL,
    channel         VARCHAR(16)     NOT NULL DEFAULT 'in_app'
                    CHECK (channel IN ('in_app','sms','wechat')),
    enabled         BOOLEAN         NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ     NOT NULL DEFAULT now()
);

-- 3.26 后台权限点（0003；不含已下线的 lives.manage）
CREATE TABLE IF NOT EXISTS role_permissions (
    role            VARCHAR(16)     NOT NULL,
    permission      VARCHAR(64)     NOT NULL,
    allowed         BOOLEAN         NOT NULL DEFAULT FALSE,
    updated_at      TIMESTAMPTZ     NOT NULL DEFAULT now(),
    PRIMARY KEY (role, permission)
);

-- 3.27 推送任务（0004）
CREATE TABLE IF NOT EXISTS notification_push_tasks (
    id              BIGSERIAL       PRIMARY KEY,
    title           VARCHAR(128)    NOT NULL,
    body            TEXT            NOT NULL,
    target          VARCHAR(16)     NOT NULL DEFAULT 'all'
                    CHECK (target IN ('all','owner','sitter')),
    channel         VARCHAR(16)     NOT NULL DEFAULT 'in_app'
                    CHECK (channel IN ('in_app','sms','wechat')),
    status          VARCHAR(16)     NOT NULL DEFAULT 'pending'
                    CHECK (status IN ('pending','scheduled','sending','sent','cancelled','failed')),
    scheduled_at    TIMESTAMPTZ,
    sent_at         TIMESTAMPTZ,
    sent_count      INT             NOT NULL DEFAULT 0,
    error_message   VARCHAR(500),
    creator_id      BIGINT          REFERENCES users(id) ON DELETE SET NULL,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ     NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_push_tasks_due ON notification_push_tasks (status, scheduled_at);

-- 3.28 地理编码缓存（0011）
CREATE TABLE IF NOT EXISTS geocode_cache (
    cache_key         VARCHAR(64)    PRIMARY KEY,
    city              VARCHAR(64)    NOT NULL DEFAULT '',
    address           VARCHAR(512)   NOT NULL,
    latitude          DOUBLE PRECISION NOT NULL CHECK (latitude BETWEEN -90 AND 90),
    longitude         DOUBLE PRECISION NOT NULL CHECK (longitude BETWEEN -180 AND 180),
    formatted_address VARCHAR(512)   NOT NULL DEFAULT '',
    resolved_city     VARCHAR(64)    NOT NULL DEFAULT '',
    adcode            VARCHAR(16)    NOT NULL DEFAULT '',
    district          VARCHAR(64)    NOT NULL DEFAULT '',
    level             VARCHAR(64)    NOT NULL DEFAULT '',
    hit_count         INT            NOT NULL DEFAULT 1 CHECK (hit_count >= 1),
    created_at        TIMESTAMPTZ    NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ    NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_geocode_cache_updated ON geocode_cache (updated_at);

-- 3.29 一次性地图选点会话（0012）
CREATE TABLE IF NOT EXISTS geo_pick_sessions (
    token_hash    VARCHAR(64)    PRIMARY KEY,
    user_id       BIGINT         NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    initial_lat   DOUBLE PRECISION NOT NULL DEFAULT 0,
    initial_lng   DOUBLE PRECISION NOT NULL DEFAULT 0,
    place_name    VARCHAR(128)   NOT NULL DEFAULT '',
    latitude      DOUBLE PRECISION,
    longitude     DOUBLE PRECISION,
    address       VARCHAR(512)   NOT NULL DEFAULT '',
    picked_at     TIMESTAMPTZ,
    expires_at    TIMESTAMPTZ    NOT NULL,
    created_at    TIMESTAMPTZ    NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_geo_pick_sessions_expires ON geo_pick_sessions (expires_at);

-- 3.30 平台配置（0003_commission_rates）
-- 后台可改的全局参数，值统一存文本，读的时候按业务自行解析。
CREATE TABLE IF NOT EXISTS platform_settings (
    key        TEXT PRIMARY KEY,
    value      TEXT        NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_by BIGINT      REFERENCES users(id) ON DELETE SET NULL
);
COMMENT ON TABLE platform_settings IS '平台级运行时配置（键值对），后台可改，不需要发版';

-- ==================================================================
-- 4. Indexes
-- ==================================================================

-- users（0005：用户名仅未删除行唯一；0006：注销冷静期扫描）
CREATE UNIQUE INDEX IF NOT EXISTS uq_users_username
    ON users (username) WHERE deleted_at IS NULL;
CREATE INDEX IF NOT EXISTS idx_users_role       ON users (role) WHERE deleted_at IS NULL;
CREATE INDEX IF NOT EXISTS idx_users_created    ON users (created_at DESC);
CREATE INDEX IF NOT EXISTS idx_users_cancel_due ON users (cancel_deadline)
    WHERE cancel_deadline IS NOT NULL;

-- pets
CREATE INDEX IF NOT EXISTS idx_pets_owner       ON pets (owner_id) WHERE deleted_at IS NULL;
CREATE INDEX IF NOT EXISTS idx_pets_species     ON pets (species) WHERE deleted_at IS NULL;
-- 模糊搜索索引依赖 pg_trgm，扩展没装上时跳过（脚本开头会打 NOTICE）
DO $$ BEGIN
    IF EXISTS (SELECT 1 FROM pg_extension WHERE extname = 'pg_trgm') THEN
        CREATE INDEX IF NOT EXISTS idx_pets_name_trgm ON pets USING gin (name gin_trgm_ops);
    END IF;
END $$;

-- sitters
CREATE INDEX IF NOT EXISTS idx_sitters_user     ON sitters (user_id) WHERE deleted_at IS NULL;
CREATE INDEX IF NOT EXISTS idx_sitters_status   ON sitters (status) WHERE deleted_at IS NULL;
CREATE INDEX IF NOT EXISTS idx_sitters_type     ON sitters (type) WHERE deleted_at IS NULL;
CREATE INDEX IF NOT EXISTS idx_sitters_score    ON sitters (avg_score DESC) WHERE deleted_at IS NULL;
CREATE INDEX IF NOT EXISTS idx_sitters_price    ON sitters (daily_price) WHERE deleted_at IS NULL;
DO $$ BEGIN
    IF EXISTS (SELECT 1 FROM pg_extension WHERE extname = 'pg_trgm') THEN
        CREATE INDEX IF NOT EXISTS idx_sitters_name_trgm ON sitters USING gin (name gin_trgm_ops);
        CREATE INDEX IF NOT EXISTS idx_sitters_addr_trgm ON sitters USING gin (address gin_trgm_ops);
    END IF;
END $$;

-- sitter_daily_capacity
CREATE INDEX IF NOT EXISTS idx_capacity_sitter_date ON sitter_daily_capacity (sitter_id, cap_date);

-- orders
CREATE INDEX IF NOT EXISTS idx_orders_owner     ON orders (owner_id, created_at DESC) WHERE deleted_at IS NULL;
CREATE INDEX IF NOT EXISTS idx_orders_sitter    ON orders (sitter_id, created_at DESC) WHERE deleted_at IS NULL;
CREATE INDEX IF NOT EXISTS idx_orders_status    ON orders (status) WHERE deleted_at IS NULL;
CREATE INDEX IF NOT EXISTS idx_orders_dates     ON orders (start_date, end_date) WHERE deleted_at IS NULL;
-- 收益 / 结算按机构 + 完成时间区间查，idx_orders_sitter 用的是 created_at，命中不了
CREATE INDEX IF NOT EXISTS idx_orders_sitter_completed ON orders (sitter_id, completed_at) WHERE deleted_at IS NULL;

-- order_pet
CREATE INDEX IF NOT EXISTS idx_order_pet_pet    ON order_pet (pet_id);

-- dynamics
CREATE INDEX IF NOT EXISTS idx_dynamics_order   ON dynamics (order_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_dynamics_sitter  ON dynamics (sitter_id);

-- reviews
CREATE INDEX IF NOT EXISTS idx_reviews_sitter   ON reviews (sitter_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_reviews_owner    ON reviews (owner_id);

-- payments
CREATE INDEX IF NOT EXISTS idx_payments_order   ON payments (order_id);
CREATE INDEX IF NOT EXISTS idx_payments_status  ON payments (status);

-- notifications
CREATE INDEX IF NOT EXISTS idx_notifications_user ON notifications (user_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_notifications_unread ON notifications (user_id) WHERE is_read = FALSE;

-- audit_logs
CREATE INDEX IF NOT EXISTS idx_audit_actor      ON audit_logs (actor_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_audit_action     ON audit_logs (action);
CREATE INDEX IF NOT EXISTS idx_audit_resource   ON audit_logs (resource_type, resource_id);

-- refresh_tokens
CREATE INDEX IF NOT EXISTS idx_refresh_user     ON refresh_tokens (user_id) WHERE revoked_at IS NULL;
-- ==================================================================
-- 5. Functions
-- ==================================================================

-- 5.1 Auto-update updated_at timestamp
CREATE OR REPLACE FUNCTION fn_set_updated_at()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $fn$
BEGIN
    NEW.updated_at := now();
    RETURN NEW;
END;
$fn$;

-- 5.2 Generate order number: PATN-YYYYMMDD-SEQ
CREATE OR REPLACE FUNCTION fn_generate_order_no()
RETURNS VARCHAR(64)
LANGUAGE plpgsql
AS $fn$
DECLARE
    v_date_part TEXT;
    v_seq       BIGINT;
    v_order_no  VARCHAR(64);
BEGIN
    v_date_part := to_char(now(), 'YYYYMMDD');
    SELECT COALESCE(MAX(id), 0) + 1 INTO v_seq FROM orders;
    v_order_no := 'PATN-' || v_date_part || '-' || lpad(v_seq::text, 6, '0');
    RETURN v_order_no;
END;
$fn$;

-- 5.3 Calculate order cost
CREATE OR REPLACE FUNCTION fn_calculate_order_cost(
    p_price_mode    price_mode,
    p_daily_price   NUMERIC,
    p_visit_price   NUMERIC,
    p_start_date    DATE,
    p_end_date      DATE,
    p_pet_count     INT
) RETURNS TABLE (total_amount NUMERIC, total_days INT)
LANGUAGE plpgsql IMMUTABLE
AS $fn$
DECLARE
    v_days INT;
BEGIN
    v_days := (p_end_date - p_start_date);
    IF v_days <= 0 THEN
        RAISE EXCEPTION 'end_date must be after start_date';
    END IF;

    IF p_price_mode = 'per_day' THEN
        total_amount := v_days * p_daily_price * p_pet_count;
        total_days := v_days;
    ELSE
        total_amount := p_visit_price * p_pet_count;
        total_days := 0;
    END IF;

    RETURN NEXT;
END;
$fn$;

-- 5.4 Update sitter average score after review
CREATE OR REPLACE FUNCTION fn_update_sitter_score()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $fn$
DECLARE
    v_sitter_id BIGINT;
BEGIN
    IF TG_OP = 'DELETE' THEN
        v_sitter_id := OLD.sitter_id;
    ELSE
        v_sitter_id := NEW.sitter_id;
    END IF;

    UPDATE sitters
    SET avg_score = COALESCE(
            (SELECT ROUND(AVG(overall_score)::numeric, 2)
             FROM reviews
             WHERE sitter_id = v_sitter_id AND is_visible = TRUE), 0),
        review_count = (
            SELECT COUNT(*)
            FROM reviews
            WHERE sitter_id = v_sitter_id AND is_visible = TRUE
        )
    WHERE id = v_sitter_id;

    IF TG_OP = 'DELETE' THEN
        RETURN OLD;
    END IF;
    RETURN NEW;
END;
$fn$;

-- 5.5 Reserve capacity (call within transaction when creating order)
CREATE OR REPLACE FUNCTION fn_reserve_capacity(
    p_sitter_id  BIGINT,
    p_start_date DATE,
    p_end_date   DATE,
    p_pet_count  INT
) RETURNS VOID
LANGUAGE plpgsql
AS $fn$
DECLARE
    v_date     DATE;
    v_capacity INT;
    v_used     INT;
    v_max_cap  INT;
BEGIN
    SELECT capacity INTO v_max_cap FROM sitters WHERE id = p_sitter_id;
    IF v_max_cap IS NULL THEN
        RAISE EXCEPTION 'Sitter % not found', p_sitter_id;
    END IF;

    v_date := p_start_date;
    WHILE v_date < p_end_date LOOP
        -- 机构默认容量已变化时，未被按日覆盖的行跟着回到新默认值。
    -- 建行时记下当前默认容量；已有行只有在"未被机构覆盖"时才跟着刷新。
        -- ON CONFLICT DO UPDATE 会拿到该行的写锁，天然串行化同一天的并发占用。
        INSERT INTO sitter_daily_capacity (sitter_id, cap_date, used, capacity, is_override)
        VALUES (p_sitter_id, v_date, p_pet_count, v_max_cap, FALSE)
        ON CONFLICT (sitter_id, cap_date) DO UPDATE
           SET used     = sitter_daily_capacity.used + p_pet_count,
               capacity = CASE WHEN sitter_daily_capacity.is_override
                               THEN sitter_daily_capacity.capacity
                               ELSE v_max_cap END,
               updated_at = now()
        RETURNING sitter_daily_capacity.capacity, sitter_daily_capacity.used
           INTO v_capacity, v_used;

        IF v_used > v_capacity THEN
            RAISE EXCEPTION 'Capacity exceeded on %: used=%, max=%',
                to_char(v_date, 'YYYY-MM-DD'), v_used, v_capacity;
        END IF;

        v_date := v_date + INTERVAL '1 day';
    END LOOP;
END;
$fn$;

-- 5.6 Release capacity on order cancellation
CREATE OR REPLACE FUNCTION fn_release_capacity(
    p_sitter_id  BIGINT,
    p_start_date DATE,
    p_end_date   DATE,
    p_pet_count  INT
) RETURNS VOID
LANGUAGE plpgsql
AS $fn$
DECLARE
    v_date DATE;
BEGIN
    v_date := p_start_date;
    WHILE v_date < p_end_date LOOP
        UPDATE sitter_daily_capacity
        SET used = GREATEST(used - p_pet_count, 0)
        WHERE sitter_id = p_sitter_id AND cap_date = v_date;

        v_date := v_date + INTERVAL '1 day';
    END LOOP;
END;
$fn$;

-- 5.7 Check pet availability for date range
CREATE OR REPLACE FUNCTION fn_check_pet_available(
    p_pet_id     BIGINT,
    p_start_date DATE,
    p_end_date   DATE
) RETURNS BOOLEAN
LANGUAGE plpgsql STABLE
AS $fn$
DECLARE
    v_conflict INT;
BEGIN
    SELECT COUNT(*) INTO v_conflict
    FROM order_pet op
    JOIN orders o ON o.id = op.order_id
    WHERE op.pet_id = p_pet_id
      AND o.status IN ('pending_pay','confirmed','accepted','staying')
      AND o.deleted_at IS NULL
      AND o.start_date < p_end_date
      AND o.end_date > p_start_date;
    RETURN v_conflict = 0;
END;
$fn$;

-- 5.8 Create notification helper
CREATE OR REPLACE FUNCTION fn_notify(
    p_user_id BIGINT,
    p_type    notification_type,
    p_title   VARCHAR,
    p_body    TEXT,
    p_extra   JSONB DEFAULT '{}',
    p_scope   VARCHAR DEFAULT 'personal'
) RETURNS BIGINT
LANGUAGE plpgsql
AS $fn$
DECLARE
    v_id BIGINT;
BEGIN
    INSERT INTO notifications (user_id, type, title, body, extra, scope)
    VALUES (p_user_id, p_type, p_title, p_body, p_extra,
            COALESCE(NULLIF(p_scope, ''), 'personal')::notification_scope)
    RETURNING id INTO v_id;
    RETURN v_id;
END;
$fn$;

-- 5.9 Audit log helper
CREATE OR REPLACE FUNCTION fn_audit(
    p_actor_id      BIGINT,
    p_action        VARCHAR,
    p_resource_type VARCHAR,
    p_resource_id   BIGINT,
    p_detail        JSONB DEFAULT '{}',
    p_ip            INET DEFAULT NULL
) RETURNS VOID
LANGUAGE plpgsql
AS $fn$
BEGIN
    INSERT INTO audit_logs (actor_id, action, resource_type, resource_id, detail, ip_address)
    VALUES (p_actor_id, p_action, p_resource_type, p_resource_id, p_detail, p_ip);
END;
$fn$;

-- 5.10 Soft delete helper
CREATE OR REPLACE FUNCTION fn_soft_delete(p_table TEXT, p_id BIGINT)
RETURNS VOID
LANGUAGE plpgsql
AS $fn$
BEGIN
    EXECUTE format('UPDATE %I SET deleted_at = now() WHERE id = $1 AND deleted_at IS NULL', p_table)
    USING p_id;
END;
$fn$;
-- ==================================================================
-- 6. Triggers
-- ==================================================================

-- 6.0 机构默认容量变化时同步未覆盖的日容量行（0015 净效果）
-- 旧 init.sql 只在 6.2 创建了引用本函数的触发器，却漏了函数定义，
-- 导致单独执行本文件在 CREATE TRIGGER 处失败。这里补齐。
CREATE OR REPLACE FUNCTION fn_sync_capacity_rows()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $fn$
BEGIN
    IF NEW.capacity IS DISTINCT FROM OLD.capacity THEN
        UPDATE sitter_daily_capacity
           SET capacity = NEW.capacity, updated_at = now()
         WHERE sitter_id = NEW.id
           AND NOT is_override
           AND capacity IS DISTINCT FROM NEW.capacity;
    END IF;
    RETURN NEW;
END;
$fn$;

-- 6.1 updated_at triggers (auto-maintain timestamp on UPDATE)
-- PostgreSQL 没有 CREATE TRIGGER IF NOT EXISTS，只能先删后建，否则重跑直接报错。
DROP TRIGGER IF EXISTS trg_users_updated ON users;
CREATE TRIGGER trg_users_updated     BEFORE UPDATE ON users     FOR EACH ROW EXECUTE FUNCTION fn_set_updated_at();
DROP TRIGGER IF EXISTS trg_pets_updated ON pets;
CREATE TRIGGER trg_pets_updated      BEFORE UPDATE ON pets      FOR EACH ROW EXECUTE FUNCTION fn_set_updated_at();
DROP TRIGGER IF EXISTS trg_sitters_updated ON sitters;
CREATE TRIGGER trg_sitters_updated   BEFORE UPDATE ON sitters   FOR EACH ROW EXECUTE FUNCTION fn_set_updated_at();
DROP TRIGGER IF EXISTS trg_orders_updated ON orders;
CREATE TRIGGER trg_orders_updated    BEFORE UPDATE ON orders    FOR EACH ROW EXECUTE FUNCTION fn_set_updated_at();
DROP TRIGGER IF EXISTS trg_order_pet_updated ON order_pet;
CREATE TRIGGER trg_order_pet_updated BEFORE UPDATE ON order_pet FOR EACH ROW EXECUTE FUNCTION fn_set_updated_at();
DROP TRIGGER IF EXISTS trg_dynamics_updated ON dynamics;
CREATE TRIGGER trg_dynamics_updated  BEFORE UPDATE ON dynamics  FOR EACH ROW EXECUTE FUNCTION fn_set_updated_at();
DROP TRIGGER IF EXISTS trg_reviews_updated ON reviews;
CREATE TRIGGER trg_reviews_updated   BEFORE UPDATE ON reviews   FOR EACH ROW EXECUTE FUNCTION fn_set_updated_at();
DROP TRIGGER IF EXISTS trg_capacity_updated ON sitter_daily_capacity;
CREATE TRIGGER trg_capacity_updated  BEFORE UPDATE ON sitter_daily_capacity FOR EACH ROW EXECUTE FUNCTION fn_set_updated_at();

-- 6.2 机构默认容量变化时同步未覆盖的日容量行
DROP TRIGGER IF EXISTS trg_sitter_capacity_sync ON sitters;
CREATE TRIGGER trg_sitter_capacity_sync
    AFTER UPDATE OF capacity ON sitters
    FOR EACH ROW
    EXECUTE FUNCTION fn_sync_capacity_rows();

-- 6.3 Review score aggregation triggers
DROP TRIGGER IF EXISTS trg_review_score_insert ON reviews;
CREATE TRIGGER trg_review_score_insert
    AFTER INSERT ON reviews
    FOR EACH ROW EXECUTE FUNCTION fn_update_sitter_score();

DROP TRIGGER IF EXISTS trg_review_score_update ON reviews;
CREATE TRIGGER trg_review_score_update
    AFTER UPDATE OF is_visible, overall_score ON reviews
    FOR EACH ROW EXECUTE FUNCTION fn_update_sitter_score();

DROP TRIGGER IF EXISTS trg_review_score_delete ON reviews;
CREATE TRIGGER trg_review_score_delete
    AFTER DELETE ON reviews
    FOR EACH ROW EXECUTE FUNCTION fn_update_sitter_score();

-- 6.4 Auto-release capacity and restore coupon/points when an order is abandoned
-- 权益退回单独成函数：取消、拒单、后台退款、超时自动取消等多个入口都会走到这里，
-- 集中在触发器里处理才不会漏。函数按行状态与流水判重，重复调用安全。
CREATE OR REPLACE FUNCTION fn_restore_order_benefits(p_order_id BIGINT)
RETURNS VOID
LANGUAGE plpgsql
AS $fn$
DECLARE
    v_owner_id BIGINT;
    v_points   INT;
BEGIN
    SELECT owner_id, points_used INTO v_owner_id, v_points
      FROM orders
     WHERE id = p_order_id;
    IF NOT FOUND THEN
        RETURN;
    END IF;

    -- 先回退券模板已用量，再解绑 user_coupons（后者靠 order_id + status 定位这一单的券）
    UPDATE coupons
       SET used_qty = GREATEST(used_qty - 1, 0)
     WHERE id IN (SELECT coupon_id FROM user_coupons
                   WHERE order_id = p_order_id AND status = 'used');

    UPDATE user_coupons
       SET status = 'unused', order_id = NULL, used_at = NULL
     WHERE order_id = p_order_id AND status = 'used';

    IF COALESCE(v_points, 0) > 0 AND NOT EXISTS (
        SELECT 1 FROM points_ledger
         WHERE ref_type = 'order' AND ref_id = p_order_id AND change_amount > 0
    ) THEN
        UPDATE users SET points = points + v_points WHERE id = v_owner_id;
        INSERT INTO points_ledger (user_id, change_amount, balance, reason, ref_type, ref_id)
        SELECT v_owner_id, v_points, u.points, '订单作废退回', 'order', p_order_id
          FROM users u WHERE u.id = v_owner_id;
    END IF;
END;
$fn$;

-- 5.11 Order status / soft-delete side effects (0015: 完成与软删都要归还夜间)
CREATE OR REPLACE FUNCTION fn_order_status_changed()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $fn$
BEGIN
    IF OLD.status IN ('pending_pay','confirmed','accepted','staying')
       AND NEW.status IN ('cancelled','rejected') THEN
        IF OLD.status IN ('pending_pay','confirmed','accepted') THEN
            PERFORM fn_release_capacity(NEW.sitter_id, NEW.start_date, NEW.end_date, NEW.pet_count);
        END IF;
        PERFORM fn_restore_order_benefits(NEW.id);
    ELSIF OLD.status IN ('pending_pay','confirmed','accepted','staying')
          AND NEW.status IN ('completed','evaluated') THEN
        -- 服务已结束，把还没住的未来夜间还给机构；过去的夜间不影响新订单，保持原样。
        IF NEW.end_date > CURRENT_DATE THEN
            PERFORM fn_release_capacity(NEW.sitter_id, CURRENT_DATE, NEW.end_date, NEW.pet_count);
        END IF;
    END IF;
    RETURN NEW;
END;
$fn$;

CREATE OR REPLACE FUNCTION fn_order_deleted()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $fn$
BEGIN
    -- 后台删单是数据纠偏：资金与券仍按退款流程处理，但占用的夜间必须释放，
    -- 否则这笔订单从所有重算口径里消失，used 就成了谁也说不清的残留数。
    IF NEW.status IN ('pending_pay','confirmed','accepted','staying') THEN
        PERFORM fn_release_capacity(NEW.sitter_id, NEW.start_date, NEW.end_date, NEW.pet_count);
    END IF;
    RETURN NEW;
END;
$fn$;

DROP TRIGGER IF EXISTS trg_order_deleted_release ON orders;
CREATE TRIGGER trg_order_deleted_release
    AFTER UPDATE OF deleted_at ON orders
    FOR EACH ROW
    WHEN (OLD.deleted_at IS NULL AND NEW.deleted_at IS NOT NULL)
    EXECUTE FUNCTION fn_order_deleted();

DROP TRIGGER IF EXISTS trg_order_cancel_release ON orders;
CREATE TRIGGER trg_order_cancel_release
    AFTER UPDATE OF status ON orders
    FOR EACH ROW
    WHEN (OLD.status IS DISTINCT FROM NEW.status)
    EXECUTE FUNCTION fn_order_status_changed();

-- ==================================================================
-- 7. Views
-- ==================================================================

-- 7.1 Active sitters (public listing)
CREATE OR REPLACE VIEW v_active_sitters AS
SELECT
    s.id, s.name, s.type, s.description,
    s.daily_price, s.visit_price, s.price_mode, s.capacity,
    s.address, s.latitude, s.longitude,
    s.avg_score, s.review_count,
    s.cover_photo_url, s.detail_photos,
    s.created_at
FROM sitters s
WHERE s.status = 'active' AND s.deleted_at IS NULL;

-- 7.2 Order detail with joins
CREATE OR REPLACE VIEW v_order_detail AS
SELECT
    o.id, o.order_no, o.status, o.payment_status,
    o.start_date, o.end_date, o.total_days, o.pet_count,
    o.estimated_amount, o.total_amount,
    o.created_at, o.updated_at,
    o.cancel_reason, o.cancel_time,
    u.id AS owner_user_id, u.username AS owner_username, u.nickname AS owner_nickname,
    s.id AS sitter_facility_id, s.name AS sitter_name, s.address AS sitter_address
FROM orders o
JOIN users u ON u.id = o.owner_id
JOIN sitters s ON s.id = o.sitter_id
WHERE o.deleted_at IS NULL;

-- 7.3 Pending orders for sitter dashboard
CREATE OR REPLACE VIEW v_pending_orders AS
SELECT
    o.id, o.order_no, o.owner_id, o.sitter_id,
    o.start_date, o.end_date, o.pet_count, o.estimated_amount,
    o.status, o.created_at,
    u.username AS owner_username, u.nickname AS owner_nickname
FROM orders o
JOIN users u ON u.id = o.owner_id
WHERE o.status = 'pending_pay' AND o.deleted_at IS NULL;

-- 7.4 Sitter review summary
CREATE OR REPLACE VIEW v_sitter_reviews AS
SELECT
    r.id, r.order_id, r.sitter_id, r.owner_id,
    r.overall_score, r.service_score, r.environment_score, r.hygiene_score,
    r.owner_comment, r.owner_photos, r.reply_text, r.reply_at,
    r.is_visible, r.created_at,
    u.username AS reviewer_name, u.nickname AS reviewer_nickname
FROM reviews r
JOIN users u ON u.id = r.owner_id
WHERE r.is_visible = TRUE;

-- 7.5 Unread notification count per user
CREATE OR REPLACE VIEW v_unread_notifications AS
SELECT user_id, COUNT(*) AS unread_count
FROM notifications
WHERE is_read = FALSE
GROUP BY user_id;

-- 7.6 Order with pet names (useful for listing)
CREATE OR REPLACE VIEW v_order_pets AS
SELECT
    o.id AS order_id,
    o.order_no,
    STRING_AGG(p.name, ', ' ORDER BY p.name) AS pet_names,
    COUNT(p.id) AS actual_pet_count
FROM orders o
JOIN order_pet op ON op.order_id = o.id
JOIN pets p ON p.id = op.pet_id
WHERE o.deleted_at IS NULL
GROUP BY o.id, o.order_no;

-- 7.7 Sitter capacity availability (next 30 days)
CREATE OR REPLACE VIEW v_sitter_availability AS
SELECT
    s.id AS sitter_id,
    s.name AS sitter_name,
    c.cap_date,
    c.capacity,
    c.used,
    c.capacity - c.used AS available
FROM sitters s
         CROSS JOIN LATERAL (
    SELECT generate_series(
                           CURRENT_DATE,
                           CURRENT_DATE + INTERVAL '30 days',
                           INTERVAL '1 day'
           )::date AS cap_date
    ) d
         LEFT JOIN sitter_daily_capacity c
                   ON c.sitter_id = s.id AND c.cap_date = d.cap_date
WHERE s.status = 'active' AND s.deleted_at IS NULL;
-- ==================================================================
-- 8. 种子数据
-- 全部按业务唯一键判重，且不再写死自增 id（旧写法假设 users 从 1 开始，重跑或
-- 在已有数据的库上执行会插错外键、或直接撞唯一约束把整个事务打回滚）。
-- ==================================================================

-- 8.1 Admin account
-- Password: Admin@123456 (bcrypt cost=10)
-- 注意：这里的哈希必须与上面注释的明文一一对应。旧版本三个账号共用同一个
-- 占位哈希，既无法登录也不对应任何真实密码，换成按明文生成的独立哈希。
INSERT INTO users (username, password_hash, nickname, role, status)
SELECT 'admin', '$2a$10$b.GIPaOYUtKJ6Xvv850/9Ou5XWW7qVaTarxQUgbh1kJwrfbRIOljW',
       'System Admin', 'admin'::user_role, 1
WHERE NOT EXISTS (SELECT 1 FROM users WHERE username = 'admin' AND deleted_at IS NULL);

-- 8.2 Demo pet owner（Password: Owner@123456）
INSERT INTO users (username, password_hash, nickname, phone, role, status)
SELECT 'petowner01', '$2a$10$78MbN6hn1tOduusslUkW2uMwzNYeWDJLkqd02WEhcxdHWQGnOJ4Za',
       'Zhang San', '13800001111', 'owner'::user_role, 1
WHERE NOT EXISTS (SELECT 1 FROM users WHERE username = 'petowner01' AND deleted_at IS NULL);

-- 8.3 Demo sitter（Password: Sitter@123456）
INSERT INTO users (username, password_hash, nickname, phone, role, status)
SELECT 'sitter01', '$2a$10$h9WMBMNRScIKEjhXw62EpO8es/c2/HxNpYwF1isxQhkc0PCYukDeS',
       'Li Si', '13800002222', 'sitter'::user_role, 1
WHERE NOT EXISTS (SELECT 1 FROM users WHERE username = 'sitter01' AND deleted_at IS NULL);

-- 8.4 Demo sitter facilities：owner 通过用户名取，不依赖 id=3
INSERT INTO sitters (user_id, name, type, description, daily_price, visit_price, price_mode, capacity, address, latitude, longitude, status)
SELECT u.id, 'Anxin Pet Home', 'home'::sitter_type,
       'Warm home boarding with garden and indoor play area. 24h care.',
       50.00, 30.00, 'per_day'::price_mode, 5,
       'Beijing Chaoyang District XX Street No.8', 39.9042000, 116.4074000, 'active'::sitter_status
FROM users u
WHERE u.username = 'sitter01'
  AND NOT EXISTS (SELECT 1 FROM sitters s WHERE s.name = 'Anxin Pet Home' AND s.deleted_at IS NULL);

INSERT INTO sitters (user_id, name, type, description, daily_price, visit_price, price_mode, capacity, address, latitude, longitude, status)
SELECT u.id, 'Happy Paws Shop', 'shop'::sitter_type,
       'Professional pet boarding shop with 24h video monitoring and vet on call.',
       80.00, 0, 'per_day'::price_mode, 10,
       'Beijing Haidian District YY Road No.15', 39.9590000, 116.2980000, 'active'::sitter_status
FROM users u
WHERE u.username = 'sitter01'
  AND NOT EXISTS (SELECT 1 FROM sitters s WHERE s.name = 'Happy Paws Shop' AND s.deleted_at IS NULL);

-- 8.5 Demo pets
INSERT INTO pets (owner_id, name, species, breed, body_size, age_month, weight_kg, vaccinated, sterilized, notes)
SELECT u.id, 'Doudou', 'dog'::pet_species, 'Golden Retriever', 'large'::pet_size,
       18, 28.5, TRUE, TRUE, 'Allergic to peanuts'
FROM users u
WHERE u.username = 'petowner01'
  AND NOT EXISTS (SELECT 1 FROM pets p WHERE p.owner_id = u.id AND p.name = 'Doudou' AND p.deleted_at IS NULL);

INSERT INTO pets (owner_id, name, species, breed, body_size, age_month, weight_kg, vaccinated, sterilized, notes)
SELECT u.id, 'Mimi', 'cat'::pet_species, 'British Shorthair', 'small'::pet_size,
       24, 4.2, TRUE, FALSE, NULL
FROM users u
WHERE u.username = 'petowner01'
  AND NOT EXISTS (SELECT 1 FROM pets p WHERE p.owner_id = u.id AND p.name = 'Mimi' AND p.deleted_at IS NULL);

-- 8.6 Demo order (pending_pay)
INSERT INTO orders (order_no, owner_id, sitter_id, status, start_date, end_date, total_days, pet_count, estimated_amount, total_amount, payment_status)
SELECT 'PATN-20261029-000001', u.id, s.id, 'pending_pay'::order_status,
       DATE '2026-11-01', DATE '2026-11-04', 3, 1, 150.00, 150.00, 'none'::payment_status
FROM users u, sitters s
WHERE u.username = 'petowner01'
  AND s.name = 'Anxin Pet Home' AND s.deleted_at IS NULL
  AND NOT EXISTS (SELECT 1 FROM orders o WHERE o.order_no = 'PATN-20261029-000001');

INSERT INTO order_pet (order_id, pet_id, stay_date)
SELECT o.id, p.id, DATE '2026-11-01'
FROM orders o
JOIN pets p ON p.owner_id = o.owner_id AND p.name = 'Doudou' AND p.deleted_at IS NULL
WHERE o.order_no = 'PATN-20261029-000001'
ON CONFLICT DO NOTHING;

-- 8.7 Pre-fill capacity for demo dates
INSERT INTO sitter_daily_capacity (sitter_id, cap_date, used, capacity)
SELECT s.id, d.cap_date, 1, 5
FROM sitters s
CROSS JOIN (VALUES (DATE '2026-11-01'), (DATE '2026-11-02'), (DATE '2026-11-03')) AS d(cap_date)
WHERE s.name = 'Anxin Pet Home' AND s.deleted_at IS NULL
ON CONFLICT (sitter_id, cap_date) DO NOTHING;

-- 8.8 Demo notification
INSERT INTO notifications (user_id, type, scope, title, body, extra)
SELECT u.id, 'order_created'::notification_type, 'business'::notification_scope,
       'New Order Received',
       'Order PATN-20261029-000001 from Zhang San, 1 pet, Nov 1-4.',
       '{"order_id": 1}'::jsonb
FROM users u
WHERE u.username = 'sitter01'
  AND NOT EXISTS (SELECT 1 FROM notifications n
                   WHERE n.user_id = u.id AND n.title = 'New Order Received');

-- ==================================================================
-- 9. Permissions (uncomment and adjust for your app DB user)
-- ==================================================================
-- CREATE ROLE patnest_app LOGIN PASSWORD 'change_me_in_env';
-- GRANT CONNECT ON DATABASE patnest TO patnest_app;
-- GRANT USAGE ON SCHEMA public TO patnest_app;
-- GRANT ALL PRIVILEGES ON ALL TABLES IN SCHEMA public TO patnest_app;
-- GRANT ALL PRIVILEGES ON ALL SEQUENCES IN SCHEMA public TO patnest_app;
-- GRANT EXECUTE ON ALL FUNCTIONS IN SCHEMA public TO patnest_app;

-- ==================================================================
-- 钱包（真实货币）：与迁移 0017_wallet.sql 保持一致
-- ==================================================================
CREATE TABLE IF NOT EXISTS wallets (
    user_id         BIGINT       PRIMARY KEY REFERENCES users(id) ON DELETE RESTRICT,
    balance         NUMERIC(12,2) NOT NULL DEFAULT 0,
    frozen_balance  NUMERIC(12,2) NOT NULL DEFAULT 0,
    total_recharge  NUMERIC(14,2) NOT NULL DEFAULT 0,
    total_consume   NUMERIC(14,2) NOT NULL DEFAULT 0,
    version         BIGINT        NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ   NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_wallets_balance ON wallets (balance DESC);

CREATE TABLE IF NOT EXISTS wallet_transactions (
    id             BIGSERIAL PRIMARY KEY,
    user_id        BIGINT       NOT NULL REFERENCES wallets(user_id),
    type           TEXT         NOT NULL CHECK (type IN ('recharge','consume','refund','withdraw','settle_in','adjust_add','adjust_sub')),
    amount         NUMERIC(12,2) NOT NULL CHECK (amount > 0),
    balance_after  NUMERIC(12,2) NOT NULL,
    status         TEXT         NOT NULL DEFAULT 'success' CHECK (status IN ('pending','success','failed')),
    -- 0019 净效果：订单余额支付写 channel='balance'，原 CHECK 漏掉它会导致支付事务回滚
    channel        TEXT         NOT NULL DEFAULT 'mock'
                   CONSTRAINT ck_wallet_transactions_channel
                   CHECK (channel IN ('balance','mock','wechat','alipay','bank','admin')),
    ref_type       TEXT,
    ref_id         BIGINT,
    transaction_id TEXT,
    remark         TEXT,
    created_at     TIMESTAMPTZ   NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_wtx_user_time ON wallet_transactions (user_id, id DESC);
CREATE INDEX IF NOT EXISTS idx_wtx_ref       ON wallet_transactions (ref_type, ref_id);

-- 3.26 钱包余额退款单（0006）
-- 钱包没有提现接口，账号注销时余额会悬空；这里把余额转成退款单，
-- 由后台确认打款或驳回（驳回原路退回钱包）。
CREATE TABLE IF NOT EXISTS wallet_withdrawals (
    id           BIGSERIAL     PRIMARY KEY,
    user_id      BIGINT        NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    amount       NUMERIC(12,2) NOT NULL CHECK (amount > 0),
    status       VARCHAR(16)   NOT NULL DEFAULT 'pending'
                 CHECK (status IN ('pending','paid','rejected')),
    -- cancel = 注销时自动转出；manual = 用户主动申请（预留）
    source       VARCHAR(16)   NOT NULL DEFAULT 'manual'
                 CHECK (source IN ('cancel','manual')),
    account_name VARCHAR(64),
    account_no   VARCHAR(128),
    contact      VARCHAR(64),
    note         VARCHAR(255),
    admin_id     BIGINT        REFERENCES users(id) ON DELETE SET NULL,
    admin_note   VARCHAR(255),
    applied_at   TIMESTAMPTZ   NOT NULL DEFAULT now(),
    paid_at      TIMESTAMPTZ
);
CREATE INDEX IF NOT EXISTS idx_wd_status ON wallet_withdrawals (status, applied_at DESC);
CREATE INDEX IF NOT EXISTS idx_wd_user   ON wallet_withdrawals (user_id, id DESC);

CREATE TABLE IF NOT EXISTS recharge_orders (
    id             BIGSERIAL PRIMARY KEY,
    user_id        BIGINT       NOT NULL REFERENCES wallets(user_id),
    amount         NUMERIC(12,2) NOT NULL CHECK (amount > 0),
    channel        TEXT         NOT NULL DEFAULT 'mock' CHECK (channel IN ('mock','wechat','alipay')),
    status         TEXT         NOT NULL DEFAULT 'pending' CHECK (status IN ('pending','paid','failed','closed')),
    transaction_id TEXT UNIQUE,
    paid_at        TIMESTAMPTZ,
    created_at     TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ   NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_recharge_user ON recharge_orders (user_id, id DESC);

-- 为已存在用户补齐钱包记录（新注册用户由 ensureWallet 在访问时自动创建）
INSERT INTO wallets (user_id) SELECT id FROM users ON CONFLICT DO NOTHING;

-- ==================================================================
-- 10. 原迁移脚本里的初始化数据（净效果）
-- ==================================================================

-- 0002：发布记录初始行
INSERT INTO system_release (app, version, commit_sha, slot, note)
SELECT 'db', 'migrations-enabled', '', '', '启用增量迁移与发布记录'
WHERE NOT EXISTS (SELECT 1 FROM system_release
                   WHERE app = 'db' AND version = 'migrations-enabled');

-- 0003：默认权限矩阵（不含已下线的 lives.manage）
INSERT INTO role_permissions (role, permission, allowed) VALUES
  ('admin','users.read',TRUE),('admin','users.write',TRUE),
  ('admin','sitters.manage',TRUE),('admin','orders.manage',TRUE),
  ('admin','refunds.approve',TRUE),('admin','pets.verify',TRUE),
  ('admin','dynamics.moderate',TRUE),('admin','reviews.moderate',TRUE),
  ('admin','coupons.manage',TRUE),('admin','notifications.manage',TRUE),
  ('admin','settlements.manage',TRUE),('admin','tickets.handle',TRUE),
  ('admin','system.settings',TRUE),('admin','audit.read',TRUE),
  ('owner','pets.manage',TRUE),('owner','orders.create',TRUE),
  ('owner','reviews.write',TRUE),('owner','favorites.manage',TRUE),
  ('sitter','shop.manage',TRUE),('sitter','orders.fulfill',TRUE),
  ('sitter','dynamics.publish',TRUE),('sitter','settlements.apply',TRUE)
ON CONFLICT (role, permission) DO NOTHING;

-- 0003_commission_rates：平台默认佣金比例（机构可在 sitters.commission_rate 单独覆盖）
INSERT INTO platform_settings (key, value) VALUES ('commission_rate', '0.10')
ON CONFLICT (key) DO NOTHING;

-- 0003：默认通知模板
INSERT INTO notification_templates (code, name, title, content, channel) VALUES
  ('order_created','新订单提醒','收到新预约 {{order_no}}','宠物主人 {{owner}} 提交了 {{start}} 至 {{end}} 的预约，请尽快确认。','in_app'),
  ('order_accepted','预约已确认','预约 {{order_no}} 已确认','机构已确认您的预约，请按时送宠入住。','in_app'),
  ('order_completed','寄养已结束','预约 {{order_no}} 已完成','寄养服务已结束，欢迎对本次服务做出评价。','in_app'),
  ('refund_approved','退款已通过','订单 {{order_no}} 退款已处理','退款 {{amount}} 元已原路退回，请注意查收。','in_app'),
  ('settlement_paid','结算已打款','{{period}} 结算已完成','本期结算金额 {{amount}} 元已打款。','in_app'),
  ('announcement','平台公告','{{title}}','{{content}}','in_app')
ON CONFLICT (code) DO NOTHING;

-- ==================================================================
-- 11. 迁移基线记录
-- 结构与 cmd/migrate 的 createMigrationsTable 完全一致；预写 0001 记录，
-- 让新库跑 `migrate -status` 时显示 0001 已应用，且重复执行不会报错。
-- 注意：0001_baseline.sql 的内容与校验和必须保持不变，老库依赖它做校验和比对。
-- ==================================================================
CREATE TABLE IF NOT EXISTS schema_migrations (
    version      VARCHAR(32)  PRIMARY KEY,
    name         TEXT         NOT NULL,
    checksum     VARCHAR(64)  NOT NULL,
    applied_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    execution_ms INTEGER      NOT NULL DEFAULT 0
);
COMMENT ON TABLE schema_migrations IS '已应用的数据库增量迁移记录，由 cmd/migrate 维护';

INSERT INTO schema_migrations (version, name, checksum, execution_ms)
VALUES ('0001', '0001_baseline.sql',
        '18b884e99a89993ed8a10f354ccf4c261451c56eda944bcf64d969753793f783', 0)
ON CONFLICT (version) DO NOTHING;

COMMIT;

-- ==================================================================
-- PatNest database initialization complete.
-- 38 张表 = 37 张业务表 + 迁移记录表 schema_migrations：
--   users, pets, sitters, sitter_daily_capacity, orders, order_pet,
--   dynamics, reviews, payments, notifications, audit_logs, refresh_tokens,
--   wallets, wallet_transactions, recharge_orders, wallet_withdrawals,
--   system_release, coupons, user_coupons, points_ledger, favorites,
--   sitter_price_rules, sitter_addons, order_addons, dynamic_likes,
--   dynamic_comments, review_followups, refund_requests, support_tickets,
--   ticket_messages, sitter_settlements, notification_templates,
--   role_permissions, notification_push_tasks, geocode_cache, geo_pick_sessions,
--   platform_settings,
--   schema_migrations
-- 函数：fn_set_updated_at, fn_generate_order_no, fn_calculate_order_cost,
--   fn_update_sitter_score, fn_reserve_capacity, fn_release_capacity,
--   fn_check_pet_available, fn_notify, fn_audit, fn_soft_delete,
--   fn_restore_order_benefits, fn_order_status_changed, fn_order_deleted,
--   fn_sync_capacity_rows
-- 触发器：updated_at 自动维护、机构默认容量同步、评分聚合、
--   订单取消/拒单释放容量并退回券积分、完成/评价归还夜间、软删释放容量
-- 视图：v_active_sitters, v_order_detail, v_pending_orders,
--   v_sitter_reviews, v_unread_notifications, v_order_pets, v_sitter_availability
--
-- 本脚本重复执行是安全的（对象幂等 + 种子判重），可以拿来修“脚本跑到一半失败”
-- 留下的半成品库；但它只负责“新库基线”，不会给旧库补列 —— 旧库请走
-- cmd/migrate 的增量迁移，或直接删库重建。
-- ==================================================================
