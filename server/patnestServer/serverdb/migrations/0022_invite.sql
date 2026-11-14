-- 0022 邀请码与邀请奖励闭环
--
-- 现状（0022 之前）：Android 的「邀请有礼」页把 "PET"+userId 当邀请码展示并分享，
-- 但 users 表没有邀请码/邀请人字段，注册接口不收邀请码，积分也只有下单扣减与
-- 订单作废退回两条流水，没有任何正向发放路径——「各得 100 积分」是空文案。
--
-- 本迁移补齐后端闭环：
--   1. users.invite_code / invited_by，存量用户回填邀请码；
--   2. 邀请码由 BEFORE INSERT 触发器按主键生成，客户端无法伪造；
--   3. 被邀请人首单进入 completed/evaluated 时，双方各 +100 积分并写流水，
--      invite_rewards.invitee_id 主键保证只发一次，并发完成两笔首单也只奖励一次；
--   4. 奖励以「首单完成」为触发点，发放后不随该单后续退款回收（口径见 docs/03）。
--
-- 全新库走 init.sql（已内联本迁移的净效果），老库执行本脚本；两边结构一致。

-- ------------------------------------------------------------------
-- 1. 用户表新增字段
-- ------------------------------------------------------------------
ALTER TABLE users
    ADD COLUMN IF NOT EXISTS invite_code VARCHAR(16),
    ADD COLUMN IF NOT EXISTS invited_by BIGINT REFERENCES users(id);

-- 允许多个 NULL（注销/历史账号尚未回填时），已回填的值必须唯一。
CREATE UNIQUE INDEX IF NOT EXISTS uq_users_invite_code
    ON users (invite_code) WHERE invite_code IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_users_invited_by
    ON users (invited_by) WHERE invited_by IS NOT NULL;

COMMENT ON COLUMN users.invite_code IS '我的邀请码，PET+用户ID补零至6位，由触发器生成（0022）';
COMMENT ON COLUMN users.invited_by IS '邀请人 user_id，NULL 表示自然注册（0022）';

-- ------------------------------------------------------------------
-- 2. 邀请奖励台账（invitee_id 即幂等键）
-- ------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS invite_rewards (
    invitee_id  BIGINT       PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
    inviter_id  BIGINT       NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    order_id    BIGINT       NOT NULL REFERENCES orders(id),
    points      INT          NOT NULL DEFAULT 100 CHECK (points > 0),
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_invite_reward_inviter_order UNIQUE (inviter_id, order_id)
);
CREATE INDEX IF NOT EXISTS idx_invite_rewards_inviter ON invite_rewards (inviter_id, created_at DESC);
COMMENT ON TABLE invite_rewards IS '邀请奖励台账：被邀请人首单完成时双方各得积分，一行即一次奖励（0022）';

-- ------------------------------------------------------------------
-- 3. 邀请码生成触发器
-- ------------------------------------------------------------------
CREATE OR REPLACE FUNCTION fn_set_invite_code()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $fn$
BEGIN
    -- BEFORE INSERT 阶段 BIGSERIAL 的默认值已经算好，NEW.id 可直接用。
    -- 与 Android 演示页原有的展示格式保持一致：PET000123。
    IF NEW.invite_code IS NULL THEN
        NEW.invite_code := 'PET' || lpad(NEW.id::text, 6, '0');
    END IF;
    RETURN NEW;
END;
$fn$;

DROP TRIGGER IF EXISTS trg_users_invite_code ON users;
CREATE TRIGGER trg_users_invite_code
    BEFORE INSERT ON users
    FOR EACH ROW
    EXECUTE FUNCTION fn_set_invite_code();

-- 存量用户回填（新注册用户由上面的触发器自动带上）。
UPDATE users
   SET invite_code = 'PET' || lpad(id::text, 6, '0')
 WHERE invite_code IS NULL;

-- ------------------------------------------------------------------
-- 4. 首单完成发放奖励
-- ------------------------------------------------------------------
CREATE OR REPLACE FUNCTION fn_grant_invite_reward(p_order_id BIGINT, p_invitee_id BIGINT)
RETURNS VOID
LANGUAGE plpgsql
AS $fn$
DECLARE
    v_inviter_id BIGINT;
    v_points     INT := 100;
    v_balance    INT;
BEGIN
    SELECT invited_by INTO v_inviter_id
      FROM users
     WHERE id = p_invitee_id
       AND invited_by IS NOT NULL
       AND deleted_at IS NULL;
    IF NOT FOUND OR v_inviter_id IS NULL THEN
        RETURN;
    END IF;

    -- 一个被邀请人只奖励一次；并发完成两笔首单时只有一条 INSERT 能成功。
    INSERT INTO invite_rewards (invitee_id, inviter_id, order_id, points)
    VALUES (p_invitee_id, v_inviter_id, p_order_id, v_points)
    ON CONFLICT (invitee_id) DO NOTHING;
    IF NOT FOUND THEN
        RETURN;
    END IF;

    -- 与下单扣减同一口径：先 UPDATE ... RETURNING points 拿到新余额，
    -- 再把 balance 快照写进 points_ledger。ref_type='invite' 不会干扰
    -- fn_restore_order_benefits 对 'order' 流水的退回判重。
    UPDATE users SET points = points + v_points, updated_at = now()
     WHERE id = p_invitee_id
    RETURNING points INTO v_balance;
    INSERT INTO points_ledger (user_id, change_amount, balance, reason, ref_type, ref_id)
    VALUES (p_invitee_id, v_points, v_balance, '邀请奖励-被邀请人', 'invite', p_order_id);

    UPDATE users SET points = points + v_points, updated_at = now()
     WHERE id = v_inviter_id AND deleted_at IS NULL
    RETURNING points INTO v_balance;
    IF FOUND THEN
        INSERT INTO points_ledger (user_id, change_amount, balance, reason, ref_type, ref_id)
        VALUES (v_inviter_id, v_points, v_balance, '邀请奖励-邀请人', 'invite', p_order_id);
    END IF;
END;
$fn$;

-- 订单进入完成态时触发；OLD.status 用原有条件限住「进行中 → 完成」这一次跃迁，
-- completed → evaluated 不会重复调用，invite_rewards 主键再做一层兜底。
CREATE OR REPLACE FUNCTION fn_order_status_changed()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $fn$
BEGIN
    IF OLD.status IN ('pending_pay','confirmed','accepted','staying')
       AND NEW.status IN ('cancelled','rejected') THEN
        IF OLD.status IN ('pending_pay','confirmed','accepted') THEN
            PERFORM fn_release_capacity(NEW.sitter_id, NEW.start_date, NEW.end_date, NEW.pet_count);
        END IF;
        PERFORM fn_restore_order_benefits(NEW.id);
    ELSIF OLD.status IN ('pending_pay','confirmed','accepted','staying')
          AND NEW.status IN ('completed','evaluated') THEN
        -- 服务已结束，把还没住的未来夜间还给机构；过去的夜间不影响新订单，保持原样。
        IF NEW.end_date > CURRENT_DATE THEN
            PERFORM fn_release_capacity(NEW.sitter_id, CURRENT_DATE, NEW.end_date, NEW.pet_count);
        END IF;
        PERFORM fn_grant_invite_reward(NEW.id, NEW.owner_id);
    END IF;
    RETURN NEW;
END;
$fn$;
