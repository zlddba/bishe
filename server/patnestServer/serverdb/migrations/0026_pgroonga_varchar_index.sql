-- 0026 修正 pgroonga 的 varchar 索引
--
-- 0024 直接建了 (name) / (address) 的 pgroonga 索引，但 name/address 是 VARCHAR，
-- 查询表达式是 (name)::text &@~ ...，普通列索引匹配不上，会被 Planner 放弃。
-- 这里改为表达式索引，和查询的 cast 完全一致。
--
-- 只处理 pgroonga 存在的情况；扩展不存在时保持 0024 的兜底行为。

DO $$ BEGIN
    IF EXISTS (SELECT 1 FROM pg_extension WHERE extname = 'pgroonga') THEN
        DROP INDEX IF EXISTS idx_sitters_name_groonga;
        DROP INDEX IF EXISTS idx_sitters_addr_groonga;

        CREATE INDEX IF NOT EXISTS idx_sitters_name_groonga
            ON sitters USING pgroonga ((name::text));
        CREATE INDEX IF NOT EXISTS idx_sitters_addr_groonga
            ON sitters USING pgroonga ((address::text));

        -- description 本来就是 TEXT，保留 0024 建的普通列索引即可。
    END IF;
END $$;
