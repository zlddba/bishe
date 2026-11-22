-- 0025 机构相似推荐（pgvector）
--
-- 目的：为 sitters 增加 16 维特征向量，用 HNSW 余弦索引支持 /sitters/:id/similar。
-- 向量由数据库触发器维护，不依赖外部 embedding 服务；
-- 特征固定为：类型 one-hot、价格/容量/评分/评价数归一化、经纬度、封面/简介完整度。
--
-- 要求：数据库镜像已包含 vector 扩展；缺少扩展时本迁移会直接失败，
-- 因为 sitters.embedding 的类型依赖它，不能静默降级。

CREATE EXTENSION IF NOT EXISTS vector;

ALTER TABLE sitters ADD COLUMN IF NOT EXISTS embedding vector(16);

CREATE OR REPLACE FUNCTION fn_sitter_embedding(
    p_type TEXT,
    p_daily_price NUMERIC,
    p_visit_price NUMERIC,
    p_capacity INT,
    p_avg_score NUMERIC,
    p_review_count INT,
    p_price_mode TEXT,
    p_latitude NUMERIC,
    p_longitude NUMERIC,
    p_has_cover BOOLEAN,
    p_has_desc BOOLEAN
) RETURNS vector(16)
LANGUAGE sql
IMMUTABLE
AS $fn$
SELECT ('[' || array_to_string(ARRAY[
    CASE WHEN p_type = 'home'  THEN 1::float8 ELSE 0::float8 END,
    CASE WHEN p_type = 'shop'  THEN 1::float8 ELSE 0::float8 END,
    CASE WHEN p_type = 'visit' THEN 1::float8 ELSE 0::float8 END,
    CASE WHEN p_type = 'other' THEN 1::float8 ELSE 0::float8 END,
    LEAST(GREATEST(COALESCE(p_daily_price, 0)::float8 / 500.0, 0), 1),
    LEAST(GREATEST(COALESCE(p_visit_price, 0)::float8 / 200.0, 0), 1),
    LEAST(GREATEST(COALESCE(p_capacity, 1)::float8 / 50.0, 0), 1),
    LEAST(GREATEST(COALESCE(p_avg_score, 0)::float8 / 5.0, 0), 1),
    LEAST(GREATEST(COALESCE(p_review_count, 0)::float8 / 100.0, 0), 1),
    CASE WHEN p_price_mode = 'per_visit' THEN 1::float8 ELSE 0::float8 END,
    LEAST(GREATEST((COALESCE(p_latitude, 0) - 18.0) / 36.0, 0), 1),
    LEAST(GREATEST((COALESCE(p_longitude, 0) - 73.0) / 62.0, 0), 1),
    CASE WHEN COALESCE(p_has_cover, FALSE) THEN 1::float8 ELSE 0::float8 END,
    CASE WHEN COALESCE(p_has_desc, FALSE)  THEN 1::float8 ELSE 0::float8 END,
    CASE WHEN p_latitude IS NOT NULL AND p_longitude IS NOT NULL THEN 1::float8 ELSE 0::float8 END,
    LEAST(GREATEST(
        (COALESCE(p_avg_score, 0)::float8 / 5.0) * 0.7
        + LEAST(COALESCE(p_review_count, 0)::float8 / 100.0, 1) * 0.3,
        0), 1)
], ',') || ']')::vector(16)
$fn$;

CREATE OR REPLACE FUNCTION fn_sitters_embedding_sync()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $fn$
BEGIN
    NEW.embedding := fn_sitter_embedding(
        NEW.type::text,
        NEW.daily_price,
        NEW.visit_price,
        NEW.capacity,
        NEW.avg_score,
        NEW.review_count,
        NEW.price_mode::text,
        NEW.latitude,
        NEW.longitude,
        NEW.cover_photo_url IS NOT NULL AND NEW.cover_photo_url <> '',
        NEW.description IS NOT NULL AND NEW.description <> ''
    );
    RETURN NEW;
END;
$fn$;

DROP TRIGGER IF EXISTS trg_sitters_embedding ON sitters;
CREATE TRIGGER trg_sitters_embedding
    BEFORE INSERT OR UPDATE OF type, daily_price, visit_price, capacity, avg_score, review_count,
        price_mode, latitude, longitude, cover_photo_url, description
    ON sitters
    FOR EACH ROW
    EXECUTE FUNCTION fn_sitters_embedding_sync();

-- 回填存量机构；UPDATE 会触发上面的同步函数，显式再算一次保证旧库也一致。
UPDATE sitters
SET embedding = fn_sitter_embedding(
    type::text,
    daily_price,
    visit_price,
    capacity,
    avg_score,
    review_count,
    price_mode::text,
    latitude,
    longitude,
    cover_photo_url IS NOT NULL AND cover_photo_url <> '',
    description IS NOT NULL AND description <> ''
)
WHERE embedding IS NULL;

CREATE INDEX IF NOT EXISTS idx_sitters_embedding
    ON sitters USING hnsw (embedding vector_cosine_ops);

COMMENT ON COLUMN sitters.embedding IS '16 维机构特征向量，由 trg_sitters_embedding 维护，用于 pgvector 相似推荐';
