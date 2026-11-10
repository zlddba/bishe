-- 0006 钱包余额退款单
--
-- 背景：钱包没有提现接口。账号注销后用户登录不了，余额却还挂在 wallets 里，
-- 没有任何入口能把它取回来，只能靠运营翻库发现。
--
-- 做法：把「余额退款」做成独立单据。注销时自动把可用余额转成一张 pending
-- 提现单、余额扣到 0，后台「钱包管理 → 余额退款」可以确认打款或驳回；
-- 驳回会把钱原路退回钱包（同时写一条 refund 流水），不会凭空消失。
--
-- 不直接对接支付通道：线下打款后由运营点确认。没有对公账户信息时自动打款
-- 风险太高（打错人无法追回），人工确认是当前唯一安全的选择。

CREATE TABLE IF NOT EXISTS wallet_withdrawals (
    id           BIGSERIAL     PRIMARY KEY,
    -- 有未处理/历史退款单的用户不允许物理删除，财务记录必须留着
    user_id      BIGINT        NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    amount       NUMERIC(12,2) NOT NULL CHECK (amount > 0),
    status       VARCHAR(16)   NOT NULL DEFAULT 'pending'
                 CHECK (status IN ('pending','paid','rejected')),
    -- cancel = 注销时自动转出；manual = 用户主动申请（预留入口）
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

COMMENT ON TABLE wallet_withdrawals IS '钱包余额退款单：注销时自动转出，后台确认打款或驳回（0006）';
COMMENT ON COLUMN wallet_withdrawals.account_no IS '收款账号，可能脱敏，客服打款前需与用户核对';
