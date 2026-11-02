-- 0004 通知推送任务：支持立即发送、定时发送与取消。

CREATE TABLE IF NOT EXISTS notification_push_tasks (
    id              BIGSERIAL       PRIMARY KEY,
    title           VARCHAR(128)    NOT NULL,
    body            TEXT            NOT NULL,
    target          VARCHAR(16)     NOT NULL DEFAULT 'all'
                    CHECK (target IN ('all','owner','sitter')),
    channel         VARCHAR(16)     NOT NULL DEFAULT 'in_app'
                    CHECK (channel IN ('in_app','sms','wechat')),
    status          VARCHAR(16)     NOT NULL DEFAULT 'pending'
                    CHECK (status IN ('pending','scheduled','sending','sent','cancelled','failed')),
    scheduled_at    TIMESTAMPTZ,
    sent_at         TIMESTAMPTZ,
    sent_count      INT             NOT NULL DEFAULT 0,
    error_message   VARCHAR(500),
    creator_id      BIGINT          REFERENCES users(id) ON DELETE SET NULL,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ     NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_push_tasks_due
    ON notification_push_tasks (status, scheduled_at);

COMMENT ON TABLE notification_push_tasks IS '后台创建的站内信/短信/微信推送任务';
COMMENT ON COLUMN notification_push_tasks.channel IS 'in_app 实际写入通知表；sms/wechat 为演示环境的模拟通道';
