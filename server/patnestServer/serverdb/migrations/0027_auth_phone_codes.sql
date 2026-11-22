-- 0027 验证码登录（演示模式）
--
-- 只存 bcrypt 哈希，不存明文验证码；一条手机号只取最新一条未消费记录。
-- AUTH_CODE_DEMO=true 时，验证码会随 send-code 响应返回给 App 本地通知展示；
-- 生产环境必须关闭该开关，并接入独立短信/邮件通道。

CREATE TABLE IF NOT EXISTS auth_phone_codes (
    id          BIGSERIAL       PRIMARY KEY,
    phone       VARCHAR(20)     NOT NULL,
    code_hash   VARCHAR(100)    NOT NULL,
    expires_at  TIMESTAMPTZ     NOT NULL,
    attempts    INT             NOT NULL DEFAULT 0 CHECK (attempts >= 0),
    consumed_at TIMESTAMPTZ,
    request_ip  VARCHAR(64),
    created_at  TIMESTAMPTZ     NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_auth_phone_codes_phone
    ON auth_phone_codes (phone, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_auth_phone_codes_active
    ON auth_phone_codes (phone, id DESC)
    WHERE consumed_at IS NULL;

COMMENT ON TABLE auth_phone_codes IS '短信/演示验证码登录的一次性验证码（哈希存储）';
COMMENT ON COLUMN auth_phone_codes.code_hash IS 'bcrypt 哈希，不保存明文验证码';
