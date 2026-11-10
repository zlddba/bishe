-- 0004 结算审计字段 + 收益查询索引
--
-- 1. sitter_settlements.commission_rate
--    结算单落库时把「当时生效的佣金比例」一起存下来。
--    以前只能靠 commission_amount / gross_amount 反算，gross_amount = 0 时
--    反算不出来（除以 0）；也无法回答「这张单当时扣的是 10% 还是 15%」。
--    历史单保持 NULL，后台按金额反算兜底。
--
-- 2. orders (sitter_id, completed_at) 部分索引
--    收益 / 结算 / 今日收入三处查询都是同一个形状：
--      WHERE sitter_id = $1 AND completed_at >= $2::date
--        AND completed_at < $3::date + 1 AND deleted_at IS NULL
--    原有 idx_orders_sitter 是 (sitter_id, created_at DESC)，created_at 帮不上
--    completed_at 的范围过滤，数据量上来后会退化成对单个机构的全部订单做过滤。

ALTER TABLE sitter_settlements
    ADD COLUMN IF NOT EXISTS commission_rate NUMERIC(5,4);

COMMENT ON COLUMN sitter_settlements.commission_rate
    IS '结算时生效的佣金比例（0-1），0004 之前的历史单为 NULL';

CREATE INDEX IF NOT EXISTS idx_orders_sitter_completed
    ON orders (sitter_id, completed_at)
    WHERE deleted_at IS NULL;
