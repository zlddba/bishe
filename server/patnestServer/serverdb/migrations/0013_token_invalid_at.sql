-- 0013 会话即时吊销时间点。
-- 访问令牌是无状态 JWT，此前"禁用账号 / 删除账号 / 重置密码"之后，
-- 对方手里的 access token 仍能用满 2 小时。加一列吊销时间，
-- 中间件比对令牌的 iat 与它：早于吊销点即失效，不需要维护 jti 黑名单。
ALTER TABLE users ADD COLUMN IF NOT EXISTS token_invalid_at TIMESTAMPTZ;

COMMENT ON COLUMN users.token_invalid_at IS '该时间点之前签发的访问令牌一律失效；NULL 表示从未吊销';
