-- 0018 优惠券 percent 语义与取值口径
-- 计算口径在 internal/handler/order_pricing.go：percent 券的减免额 = 小计 * value / 100，
-- 即 value 是「减免百分比」（99 表示减 99%，不是 9.9 折）。
-- 应用层（AdminCreateCoupon / CreateSitterCoupon）已限制 percent 在 1-100；
-- 这里补一条库级约束，避免脚本/后台直插出现 9900 这类会瞬间打穿订单金额的脏值。

DO $$
DECLARE
    bad_count INT;
BEGIN
    SELECT COUNT(*) INTO bad_count
    FROM coupons
    WHERE type = 'percent' AND (value <= 0 OR value > 100);
    IF bad_count > 0 THEN
        RAISE EXCEPTION 'coupons 表有 % 条 percent 券面值不在 1-100（value 语义为减免百分比），请先修正数据再执行 0018', bad_count;
    END IF;
END $$;

ALTER TABLE coupons DROP CONSTRAINT IF EXISTS ck_coupons_percent_range;
ALTER TABLE coupons
    ADD CONSTRAINT ck_coupons_percent_range
    CHECK (type <> 'percent' OR (value > 0 AND value <= 100));

COMMENT ON COLUMN coupons.value IS 'fixed=减免金额（元）；percent=减免百分比（1-100），折扣额 = 小计 * value / 100';
