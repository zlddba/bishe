-- 0007 注销执行失败的重试控制
--
-- 注销 worker 原来失败只打日志、不计数：cancel_deadline 不变，
-- 于是每轮扫描都排在队首（扫描按 cancel_deadline 排序 LIMIT 20），
-- 一条永久失败的数据会把后面所有待注销账号堵死，日志也会无限刷。
--
-- 这里加三个字段：
--   cancel_attempts   —— 连续失败次数；
--   cancel_last_error —— 最近一次失败原因，后台直接展示，不用翻日志；
--   cancel_blocked_at —— 超过上限后停止自动处理的时间点，需人工重试。
--
-- 另外失败时会把 cancel_deadline 往后推（指数退避），让位给其它账号。

ALTER TABLE users
    ADD COLUMN IF NOT EXISTS cancel_attempts   INT          NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS cancel_last_error VARCHAR(255),
    ADD COLUMN IF NOT EXISTS cancel_blocked_at TIMESTAMPTZ;

COMMENT ON COLUMN users.cancel_attempts
    IS '注销执行连续失败次数（0007）';
COMMENT ON COLUMN users.cancel_last_error
    IS '最近一次注销失败原因，超过上限后由后台展示（0007）';
COMMENT ON COLUMN users.cancel_blocked_at
    IS '连续失败达到上限后停止自动注销的时间点，需后台人工重试（0007）';

-- 不新建索引：0006 之前已有 idx_users_cancel_due ON users (cancel_deadline)
-- WHERE cancel_deadline IS NOT NULL，扫描条件仍然命中它，
-- 多出来的 `cancel_blocked_at IS NULL` 只是行内过滤。
