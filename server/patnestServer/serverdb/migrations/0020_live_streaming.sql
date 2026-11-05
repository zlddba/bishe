-- 0020 商家直播：直播间、观众在线表、弹幕，以及机构的直播封禁开关
-- 推拉流由自建 SRS 承担（见 deploy/live/），本表只保存「谁在播、播什么、是否被处理」。
-- stream_key 是推流路径里的随机串：拿到它就能推流，因此只下发给开播机构本人。

CREATE TABLE IF NOT EXISTS live_rooms (
    id            BIGSERIAL PRIMARY KEY,
    sitter_id     BIGINT       NOT NULL REFERENCES sitters(id) ON DELETE CASCADE,
    title         VARCHAR(128) NOT NULL,
    cover_url     TEXT,
    stream_key    VARCHAR(64)  NOT NULL UNIQUE,
    status        TEXT         NOT NULL DEFAULT 'living'
                               CHECK (status IN ('living','ended','banned')),
    viewer_count  INT          NOT NULL DEFAULT 0,   -- 最近一次心跳时的在线人数快照
    peak_viewers  INT          NOT NULL DEFAULT 0,
    total_views   BIGINT       NOT NULL DEFAULT 0,   -- 累计观看人次（去重前）
    started_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    ended_at      TIMESTAMPTZ,
    end_reason    TEXT,                              -- host_stop / admin_stop / banned / timeout
    ban_reason    TEXT,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_live_rooms_status ON live_rooms (status, started_at DESC);
CREATE INDEX IF NOT EXISTS idx_live_rooms_sitter ON live_rooms (sitter_id, id DESC);

-- 一个机构同一时间只能有一个直播间
CREATE UNIQUE INDEX IF NOT EXISTS uq_live_rooms_one_living
    ON live_rooms (sitter_id) WHERE status = 'living';

-- 在线观众：靠 last_seen_at 滑动窗口统计，客户端 30s 心跳一次，90s 没心跳视为离开
CREATE TABLE IF NOT EXISTS live_viewers (
    room_id      BIGINT      NOT NULL REFERENCES live_rooms(id) ON DELETE CASCADE,
    user_id      BIGINT      NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    joined_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    last_seen_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (room_id, user_id)
);
CREATE INDEX IF NOT EXISTS idx_live_viewers_alive ON live_viewers (room_id, last_seen_at DESC);

-- 弹幕：后台可隐藏（hidden），观看端只返回 visible
CREATE TABLE IF NOT EXISTS live_messages (
    id         BIGSERIAL PRIMARY KEY,
    room_id    BIGINT       NOT NULL REFERENCES live_rooms(id) ON DELETE CASCADE,
    user_id    BIGINT       NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    content    VARCHAR(200) NOT NULL,
    status     TEXT         NOT NULL DEFAULT 'visible'
                            CHECK (status IN ('visible','hidden')),
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_live_messages_room ON live_messages (room_id, id DESC);

-- 机构直播封禁：非空即禁止开播；已在播的直播间会被强制结束
ALTER TABLE sitters ADD COLUMN IF NOT EXISTS live_banned_at   TIMESTAMPTZ;
ALTER TABLE sitters ADD COLUMN IF NOT EXISTS live_ban_reason  TEXT;
COMMENT ON COLUMN sitters.live_banned_at  IS '直播封禁时间，非空表示禁止开播';
COMMENT ON COLUMN sitters.live_ban_reason IS '直播封禁原因（后台填写，机构端可见）';

-- 后台直播管理权限（权限矩阵缺行时中间件默认放行，这里补上让「权限管理」页能看到并可关闭）
INSERT INTO role_permissions (role, permission, allowed) VALUES
    ('admin', 'lives.manage', TRUE)
ON CONFLICT (role, permission) DO NOTHING;

