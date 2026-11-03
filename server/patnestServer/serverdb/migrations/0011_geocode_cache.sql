-- 0011 地理编码缓存：地址 → GCJ-02 坐标。
-- 桌面端等没有 GPS 的客户端靠"城市 + 地址"换坐标，同一地址只请求高德一次，避免重复消耗配额。
CREATE TABLE IF NOT EXISTS geocode_cache (
    cache_key         VARCHAR(64)    PRIMARY KEY,          -- md5(city|address)，地址做归一化后再取哈希
    city              VARCHAR(64)    NOT NULL DEFAULT '',
    address           VARCHAR(512)   NOT NULL,
    latitude          DOUBLE PRECISION NOT NULL CHECK (latitude BETWEEN -90 AND 90),
    longitude         DOUBLE PRECISION NOT NULL CHECK (longitude BETWEEN -180 AND 180),
    formatted_address VARCHAR(512)   NOT NULL DEFAULT '',
    resolved_city     VARCHAR(64)    NOT NULL DEFAULT '',   -- 高德解析出的城市，与请求里的 city 不是一回事
    adcode            VARCHAR(16)    NOT NULL DEFAULT '',
    district          VARCHAR(64)    NOT NULL DEFAULT '',
    level             VARCHAR(64)    NOT NULL DEFAULT '',   -- 高德匹配精度，如"门牌号""街道"
    hit_count         INT            NOT NULL DEFAULT 1 CHECK (hit_count >= 1),
    created_at        TIMESTAMPTZ    NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ    NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_geocode_cache_updated ON geocode_cache (updated_at);
