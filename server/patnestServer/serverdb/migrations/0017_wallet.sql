-- 0017 钱包（真实货币）
-- 依赖：pay_method 枚举（init.sql 已定义）。余额支付需要 method='balance'。

-- 余额支付：在 pay_method 枚举中新增 'balance'。
-- 注意：ALTER TYPE ADD VALUE 不能在事务块内执行；与 0007/0008 一致，迁移执行器按文件非事务执行。
ALTER TYPE pay_method ADD VALUE IF NOT EXISTS 'balance';

-- 每个用户一条钱包记录（机构复用同一张表，方案 A：机构提现仍走 sitter_settlements）
CREATE TABLE IF NOT EXISTS wallets (
    user_id         BIGINT       PRIMARY KEY REFERENCES users(id) ON DELETE RESTRICT,
    balance         NUMERIC(12,2) NOT NULL DEFAULT 0,
    frozen_balance  NUMERIC(12,2) NOT NULL DEFAULT 0,
    total_recharge  NUMERIC(14,2) NOT NULL DEFAULT 0,
    total_consume   NUMERIC(14,2) NOT NULL DEFAULT 0,
    version         BIGINT        NOT NULL DEFAULT 0,   -- 乐观锁
    created_at      TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ   NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_wallets_balance ON wallets (balance DESC);

-- 资金流水：对账与用户明细的核心依据。balance_after 由服务端计算，绝不让客户端传。
CREATE TABLE IF NOT EXISTS wallet_transactions (
    id             BIGSERIAL PRIMARY KEY,
    user_id        BIGINT       NOT NULL REFERENCES wallets(user_id),
    type           TEXT         NOT NULL CHECK (type IN ('recharge','consume','refund','withdraw','settle_in','adjust_add','adjust_sub')),
    amount         NUMERIC(12,2) NOT NULL CHECK (amount > 0),
    balance_after  NUMERIC(12,2) NOT NULL,
    status         TEXT         NOT NULL DEFAULT 'success' CHECK (status IN ('pending','success','failed')),
    channel        TEXT         NOT NULL DEFAULT 'mock' CHECK (channel IN ('mock','wechat','alipay','bank','admin')),
    ref_type       TEXT,               -- order / recharge / withdraw / settlement / admin
    ref_id         BIGINT,
    transaction_id TEXT,               -- 第三方流水号（充值/支付回调对账）
    remark         TEXT,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_wtx_user_time ON wallet_transactions (user_id, id DESC);
CREATE INDEX IF NOT EXISTS idx_wtx_ref       ON wallet_transactions (ref_type, ref_id);

-- 充值单：异步回调 / 幂等对账。transaction_id UNIQUE 防止重复入账。
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

-- 为存量库已有用户补齐钱包记录；新注册用户由 ensureWallet() 在访问时自动创建。
INSERT INTO wallets (user_id) SELECT id FROM users ON CONFLICT DO NOTHING;
