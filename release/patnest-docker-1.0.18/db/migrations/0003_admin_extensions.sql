-- 0003 后台治理与角色扩展：管理员单会话、免疫核验、容量、优惠券、积分、
-- 收藏、节假日定价、加购、动态互动、追评、退款申请、工单、结算、模板、权限点。

-- ---------- users ----------
ALTER TABLE users ADD COLUMN IF NOT EXISTS session_key VARCHAR(64);
ALTER TABLE users ADD COLUMN IF NOT EXISTS points INT NOT NULL DEFAULT 0 CHECK (points >= 0);
ALTER TABLE users ADD COLUMN IF NOT EXISTS address VARCHAR(255);
ALTER TABLE users ADD COLUMN IF NOT EXISTS contact_name VARCHAR(64);
ALTER TABLE users ADD COLUMN IF NOT EXISTS contact_phone VARCHAR(20);

COMMENT ON COLUMN users.session_key IS '管理员当前会话标识；非空时旧会话的令牌全部失效';
COMMENT ON COLUMN users.points IS '积分余额，100 积分 = 1 元';

-- ---------- pets: 免疫证明核验 ----------
ALTER TABLE pets ADD COLUMN IF NOT EXISTS vaccine_urls TEXT[] DEFAULT '{}';
ALTER TABLE pets ADD COLUMN IF NOT EXISTS vaccine_status VARCHAR(16) NOT NULL DEFAULT 'missing'
    CHECK (vaccine_status IN ('missing','pending','verified','rejected'));
ALTER TABLE pets ADD COLUMN IF NOT EXISTS vaccine_note VARCHAR(255);
ALTER TABLE pets ADD COLUMN IF NOT EXISTS vaccine_verified_by BIGINT REFERENCES users(id) ON DELETE SET NULL;
ALTER TABLE pets ADD COLUMN IF NOT EXISTS vaccine_verified_at TIMESTAMPTZ;

-- ---------- orders: 优惠与变动记录 ----------
ALTER TABLE orders ADD COLUMN IF NOT EXISTS coupon_id BIGINT;
ALTER TABLE orders ADD COLUMN IF NOT EXISTS discount_amount NUMERIC(12,2) NOT NULL DEFAULT 0;
ALTER TABLE orders ADD COLUMN IF NOT EXISTS points_used INT NOT NULL DEFAULT 0;
ALTER TABLE orders ADD COLUMN IF NOT EXISTS addon_amount NUMERIC(12,2) NOT NULL DEFAULT 0;
ALTER TABLE orders ADD COLUMN IF NOT EXISTS rescheduled_count INT NOT NULL DEFAULT 0;

-- ---------- 优惠券 ----------
CREATE TABLE IF NOT EXISTS coupons (
    id              BIGSERIAL       PRIMARY KEY,
    code            VARCHAR(32)     NOT NULL UNIQUE,
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
    updated_at      TIMESTAMPTZ     NOT NULL DEFAULT now()
);

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

-- ---------- 积分流水 ----------
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

-- ---------- 收藏 ----------
CREATE TABLE IF NOT EXISTS favorites (
    id              BIGSERIAL       PRIMARY KEY,
    user_id         BIGINT          NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    sitter_id       BIGINT          NOT NULL REFERENCES sitters(id) ON DELETE CASCADE,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT now(),
    CONSTRAINT uq_favorite UNIQUE (user_id, sitter_id)
);

-- ---------- 机构节假日定价 ----------
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

-- ---------- 加购服务 ----------
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

-- ---------- 动态点赞 / 留言 ----------
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

-- ---------- 评价追评 ----------
CREATE TABLE IF NOT EXISTS review_followups (
    id              BIGSERIAL       PRIMARY KEY,
    review_id       BIGINT          NOT NULL REFERENCES reviews(id) ON DELETE CASCADE,
    owner_id        BIGINT          NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    content         VARCHAR(1000)   NOT NULL,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT now()
);

-- ---------- 退款申请 ----------
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

-- ---------- 售后工单 ----------
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

-- ---------- 机构结算 ----------
CREATE TABLE IF NOT EXISTS sitter_settlements (
    id              BIGSERIAL       PRIMARY KEY,
    sitter_id       BIGINT          NOT NULL REFERENCES sitters(id) ON DELETE CASCADE,
    period_start    DATE            NOT NULL,
    period_end      DATE            NOT NULL,
    order_count     INT             NOT NULL DEFAULT 0,
    gross_amount    NUMERIC(12,2)   NOT NULL DEFAULT 0,
    commission_amount NUMERIC(12,2) NOT NULL DEFAULT 0,
    net_amount      NUMERIC(12,2)   NOT NULL DEFAULT 0,
    status          VARCHAR(16)     NOT NULL DEFAULT 'pending'
                    CHECK (status IN ('pending','paid','rejected')),
    applied_at      TIMESTAMPTZ     NOT NULL DEFAULT now(),
    paid_at         TIMESTAMPTZ,
    admin_id        BIGINT          REFERENCES users(id) ON DELETE SET NULL,
    note            VARCHAR(500),
    CONSTRAINT chk_settlement_dates CHECK (period_end >= period_start)
);

CREATE INDEX IF NOT EXISTS idx_settlements_status ON sitter_settlements (status, applied_at DESC);

-- ---------- 通知模板 ----------
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

-- ---------- 角色权限点 ----------
CREATE TABLE IF NOT EXISTS role_permissions (
    role            VARCHAR(16)     NOT NULL,
    permission      VARCHAR(64)     NOT NULL,
    allowed         BOOLEAN         NOT NULL DEFAULT FALSE,
    updated_at      TIMESTAMPTZ     NOT NULL DEFAULT now(),
    PRIMARY KEY (role, permission)
);

-- 默认权限矩阵
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

-- 默认通知模板
INSERT INTO notification_templates (code, name, title, content, channel) VALUES
  ('order_created','新订单提醒','收到新预约 {{order_no}}','宠物主人 {{owner}} 提交了 {{start}} 至 {{end}} 的预约，请尽快确认。','in_app'),
  ('order_accepted','预约已确认','预约 {{order_no}} 已确认','机构已确认您的预约，请按时送宠入住。','in_app'),
  ('order_completed','寄养已结束','预约 {{order_no}} 已完成','寄养服务已结束，欢迎对本次服务做出评价。','in_app'),
  ('refund_approved','退款已通过','订单 {{order_no}} 退款已处理','退款 {{amount}} 元已原路退回，请注意查收。','in_app'),
  ('settlement_paid','结算已打款','{{period}} 结算已完成','本期结算金额 {{amount}} 元已打款。','in_app'),
  ('announcement','平台公告','{{title}}','{{content}}','in_app')
ON CONFLICT (code) DO NOTHING;
