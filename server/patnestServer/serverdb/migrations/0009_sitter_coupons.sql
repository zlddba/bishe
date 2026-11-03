-- 0009 机构优惠券：coupons 增加发放主体维度。
-- sitter_id 为 NULL 表示平台官方券；机构券由机构在机构端发布与管理。
-- code 从全局唯一改为同一发放主体内唯一。
ALTER TABLE coupons ADD COLUMN IF NOT EXISTS sitter_id BIGINT REFERENCES sitters(id) ON DELETE CASCADE;
COMMENT ON COLUMN coupons.sitter_id IS '发放机构 id，NULL=平台官方券';

-- 机构在有效期内可见自己的券，主人侧仅可见 active 机构的券（查询层控制，无 RLS）
CREATE INDEX IF NOT EXISTS idx_coupons_sitter ON coupons (sitter_id, enabled) WHERE sitter_id IS NOT NULL;

-- code 唯一性调整：去掉表级 UNIQUE，改为按主体分区唯一
ALTER TABLE coupons DROP CONSTRAINT IF EXISTS coupons_code_key;
CREATE UNIQUE INDEX IF NOT EXISTS uq_coupons_platform_code ON coupons (code) WHERE sitter_id IS NULL;
CREATE UNIQUE INDEX IF NOT EXISTS uq_coupons_sitter_code ON coupons (sitter_id, code);
