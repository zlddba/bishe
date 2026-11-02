-- 0006 注销冷静期：申请注销后 3 天冷静期，到期自动执行；冷静期内登录自动撤销。
--
-- cancel_mode       注销类型：account=整个账户，sitter=仅注销机构
-- cancel_requested_at 申请时间
-- cancel_deadline   冷静期截止时间（= 申请时间 + 3 天），到期由后台 worker 扫描执行

ALTER TABLE users ADD COLUMN IF NOT EXISTS cancel_mode VARCHAR(16);
ALTER TABLE users ADD COLUMN IF NOT EXISTS cancel_requested_at TIMESTAMPTZ;
ALTER TABLE users ADD COLUMN IF NOT EXISTS cancel_deadline TIMESTAMPTZ;

CREATE INDEX IF NOT EXISTS idx_users_cancel_due
    ON users (cancel_deadline)
    WHERE cancel_deadline IS NOT NULL;

COMMENT ON COLUMN users.cancel_mode IS '注销申请类型：account=整个账户，sitter=仅机构；冷静期3天，期间登录自动撤销';
COMMENT ON COLUMN users.cancel_deadline IS '注销冷静期截止时间，到期由 account-cancel worker 自动执行';
