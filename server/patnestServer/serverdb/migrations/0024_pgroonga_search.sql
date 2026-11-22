-- 0024 机构中文全文检索（PGroonga）
--
-- 镜像已包含 pgroonga 时启用；扩展不可用时只打 NOTICE，不阻断迁移。
-- 搜索接口会检测 pgroonga 是否存在：
--   - 存在：sitters.name/address/description 走 &@~ 全文检索
--   - 不存在或查询失败：退回 pg_trgm 的 ILIKE + similarity
--
-- 三个单列索引配合 OR 查询，PostgreSQL 可以用 BitmapOr 合并。

DO $$ BEGIN
    CREATE EXTENSION IF NOT EXISTS pgroonga;
EXCEPTION WHEN OTHERS THEN
    RAISE NOTICE 'pgroonga 不可用（%），机构搜索继续使用 pg_trgm', SQLERRM;
END $$;

DO $$ BEGIN
    IF EXISTS (SELECT 1 FROM pg_extension WHERE extname = 'pgroonga') THEN
        CREATE INDEX IF NOT EXISTS idx_sitters_name_groonga ON sitters USING pgroonga (name);
        CREATE INDEX IF NOT EXISTS idx_sitters_addr_groonga ON sitters USING pgroonga (address);
        CREATE INDEX IF NOT EXISTS idx_sitters_desc_groonga ON sitters USING pgroonga (description);
    END IF;
END $$;
