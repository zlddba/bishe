-- 0002 发布记录表：每次热更新成功后写入一条，可在后台“系统信息”查看。
CREATE TABLE IF NOT EXISTS system_release (
    id          BIGSERIAL    PRIMARY KEY,
    app         VARCHAR(32)  NOT NULL,
    version     VARCHAR(64)  NOT NULL,
    commit_sha  VARCHAR(64)  DEFAULT '',
    slot        VARCHAR(16)  DEFAULT '',
    note        TEXT         DEFAULT '',
    deployed_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_system_release_app_time
    ON system_release (app, deployed_at DESC);

COMMENT ON TABLE system_release IS '后端/管理面板热更新发布记录';
COMMENT ON COLUMN system_release.app IS 'api 或 web';
COMMENT ON COLUMN system_release.slot IS '蓝绿槽位 blue/green，前端发布可留空';

INSERT INTO system_release (app, version, commit_sha, slot, note)
VALUES ('db', 'migrations-enabled', '', '', '启用增量迁移与发布记录');
