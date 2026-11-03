-- 0012 地图选点会话：桌面端（KMP Desktop）没有内嵌浏览器，
-- 改为"后端发一张一次性选点页 → 用户在系统浏览器里选点 → 桌面端轮询取回坐标"。
-- 只存哈希令牌，明文令牌仅在响应里出现一次，会话 5 分钟过期、取走即删。
CREATE TABLE IF NOT EXISTS geo_pick_sessions (
    token_hash    VARCHAR(64)    PRIMARY KEY,               -- sha256(令牌)
    user_id       BIGINT         NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    initial_lat   DOUBLE PRECISION NOT NULL DEFAULT 0,
    initial_lng   DOUBLE PRECISION NOT NULL DEFAULT 0,
    place_name    VARCHAR(128)   NOT NULL DEFAULT '',       -- 展示在选点页标题上的门店名
    latitude      DOUBLE PRECISION,                          -- 选点前为 NULL
    longitude     DOUBLE PRECISION,
    address       VARCHAR(512)   NOT NULL DEFAULT '',
    picked_at     TIMESTAMPTZ,
    expires_at    TIMESTAMPTZ    NOT NULL,
    created_at    TIMESTAMPTZ    NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_geo_pick_sessions_expires ON geo_pick_sessions (expires_at);
