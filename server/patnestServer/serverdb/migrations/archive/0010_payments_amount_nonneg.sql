-- 0010 payments.amount 允许 0：0 元订单（全额抵扣/免费服务）需要能落支付流水。
-- 原约束 CHECK (amount > 0) 会让这类流水写入失败，进而使支付接口在事务提交阶段报 commit failed。
DO $$
DECLARE
    r RECORD;
BEGIN
    FOR r IN
        SELECT con.conname
        FROM pg_constraint con
        WHERE con.conrelid = 'payments'::regclass
          AND con.contype = 'c'
          AND pg_get_constraintdef(con.oid) LIKE '%amount%'
          AND pg_get_constraintdef(con.oid) !~ 'amount >= 0'
    LOOP
        EXECUTE format('ALTER TABLE payments DROP CONSTRAINT %I', r.conname);
    END LOOP;

    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conrelid = 'payments'::regclass
          AND conname = 'payments_amount_check'
    ) THEN
        ALTER TABLE payments ADD CONSTRAINT payments_amount_check CHECK (amount >= 0);
    END IF;
END
$$;
