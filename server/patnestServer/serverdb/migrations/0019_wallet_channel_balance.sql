-- 0019 钱包流水的 channel 增加 'balance'（订单余额支付）
-- 背景：0017 的 CHECK 只允许 mock/wechat/alipay/bank/admin，而「订单余额支付」写的是 channel='balance'，
-- 于是 N-1：PUT /orders/:id/pay {"method":"balance"} 在写流水时撞约束 → 50001「写入钱包流水失败」，整笔事务回滚。
-- 语义：channel 表示这笔资金「从哪条通道进出」——balance 是钱包内部扣减，和充值通道不是一回事。
--
-- 约束名可能因环境而异（0017 是内联 CHECK，PostgreSQL 自动命名为 wallet_transactions_channel_check），
-- 这里按「约束定义里带 channel」来定位，避免硬编码名字在别的库上找不到。

DO $$
DECLARE
    c record;
BEGIN
    FOR c IN
        SELECT conname
        FROM pg_constraint
        WHERE conrelid = 'wallet_transactions'::regclass
          AND contype = 'c'
          AND pg_get_constraintdef(oid) ILIKE '%channel%'
    LOOP
        EXECUTE format('ALTER TABLE wallet_transactions DROP CONSTRAINT %I', c.conname);
    END LOOP;
END $$;

ALTER TABLE wallet_transactions
    ADD CONSTRAINT ck_wallet_transactions_channel
    CHECK (channel IN ('balance', 'mock', 'wechat', 'alipay', 'bank', 'admin'));

COMMENT ON COLUMN wallet_transactions.channel IS
    '资金通道：balance=钱包余额扣减，mock/wechat/alipay/bank=充值/外部支付，admin=后台人工调账';
