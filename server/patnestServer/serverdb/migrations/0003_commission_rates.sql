-- 0003 平台佣金比例：全局默认 + 单机构覆盖
--
-- 以前平台抽佣是代码里的常量（platformCommission = 0.10），改一次比例要发一次版。
-- 这里改成数据库配置：
--   1. platform_settings 存平台级默认值（键值表，后续其它全局开关也能放进来）；
--   2. sitters.commission_rate 存单个机构的单独比例，NULL 表示跟随平台默认。
-- 收益接口取「机构单独设置优先，否则平台默认」的生效值。

CREATE TABLE IF NOT EXISTS platform_settings (
    key        TEXT PRIMARY KEY,
    value      TEXT        NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_by BIGINT      REFERENCES users(id) ON DELETE SET NULL
);

COMMENT ON TABLE platform_settings IS '平台级运行时配置（键值对），后台可改，不需要发版';

INSERT INTO platform_settings (key, value)
VALUES ('commission_rate', '0.10')
ON CONFLICT (key) DO NOTHING;

-- NULL = 跟随平台默认，收益计算时不需要区分「0」和「未设置」
ALTER TABLE sitters ADD COLUMN IF NOT EXISTS commission_rate NUMERIC(5,4);

COMMENT ON COLUMN sitters.commission_rate IS '机构单独佣金比例 0-1，NULL 表示跟随 platform_settings.commission_rate';

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'chk_sitter_commission_rate') THEN
        ALTER TABLE sitters
            ADD CONSTRAINT chk_sitter_commission_rate
            CHECK (commission_rate IS NULL OR (commission_rate >= 0 AND commission_rate <= 1));
    END IF;
END $$;
