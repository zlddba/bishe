-- 0023 管理后台两步验证（TOTP）
--
-- 目标：管理员账号可以绑定 Google Authenticator / Microsoft Authenticator 这类
-- 验证器，登录时在密码之外再输 6 位动态口令，防止密码泄露后被直接登进后台。
--
-- 口令算法在 internal/totp 里用标准库实现（RFC 6238 / SHA1 / 6 位 / 30 秒），
-- 不需要服务器额外装任何组件。
--
-- 字段说明：
--   totp_secret        当前密钥（base32）。绑定流程一开始就会写入，
--                      但只有 totp_enabled = true 才真正在登录时校验，
--                      所以「生成了密钥但没扫码确认」不会把人锁在门外。
--   totp_enabled       是否已启用。只有 POST /admin/security/totp/enable
--                      校验通过才会置 true。
--   totp_confirmed_at  启用时间，后台用来展示「已绑定」。
--   totp_last_step     最近一次校验成功的时间步（unix 秒 / 30）。
--                      同一口令在有效期内重复提交会被拒（防重放）。
--
-- 恢复码单独一张表，只存 sha256，一次一用。
--
-- 全新库走 init.sql（已内联本迁移的净效果），老库执行本脚本；两边结构一致。

ALTER TABLE users
    ADD COLUMN IF NOT EXISTS totp_secret       VARCHAR(64),
    ADD COLUMN IF NOT EXISTS totp_enabled      BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS totp_confirmed_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS totp_last_step    BIGINT;

COMMENT ON COLUMN users.totp_secret IS 'TOTP 密钥（base32），未启用时也保留，用于「已生成未确认」（0023）';
COMMENT ON COLUMN users.totp_enabled IS '是否已启用两步验证（0023）';
COMMENT ON COLUMN users.totp_confirmed_at IS '两步验证启用时间（0023）';
COMMENT ON COLUMN users.totp_last_step IS '最近一次校验通过的时间步，用于拒绝同一口令重放（0023）';

CREATE TABLE IF NOT EXISTS totp_recovery_codes (
    id          BIGSERIAL   PRIMARY KEY,
    user_id     BIGINT      NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    code_hash   CHAR(64)    NOT NULL,
    used_at     TIMESTAMPTZ,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- 同一个账号不会出现重复恢复码；登录校验按 (user_id, code_hash) 精确定位。
CREATE UNIQUE INDEX IF NOT EXISTS uq_totp_recovery_user_hash
    ON totp_recovery_codes (user_id, code_hash);
-- 后台展示「还剩几个可用」走这条索引。
CREATE INDEX IF NOT EXISTS idx_totp_recovery_unused
    ON totp_recovery_codes (user_id) WHERE used_at IS NULL;

COMMENT ON TABLE totp_recovery_codes IS '两步验证恢复码：仅存 sha256，一次一用（0023）';
