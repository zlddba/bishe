-- 0016 消息按归属视角拆分（个人消息 / 商家消息）
--
-- 背景：/api/v1/notifications 原本只有一个接口，主人和机构收到的通知混在同一个列表里，
--       "全部已读"也会把两类消息一起清掉。本次新增两套接口：
--         /api/v1/pet-owner/notifications  → scope = personal（主人视角）
--         /api/v1/sitter/notifications     → scope = business（机构视角）
--       旧 /notifications 保留兼容，返回全部。
--
-- 归属口径（与 fn_notify 各调用点显式传入的值一致）：
--   personal：订单状态变化、寄养动态、退款/工单回复、发给主人的系统公告
--   business：新订单、订单已支付、新评价、结算、联系请求、发给机构的系统公告
--
-- 幂等：类型/列/索引带 IF NOT EXISTS，函数先 DROP 再 CREATE，回填只处理 personal 行。
-- 注意：历史行没有归属标记，回填按 type + 标题 + 收件人角色推断，属于一次性近似；
--      今后所有新通知由调用点显式指定 scope，不再依赖推断。

-- ---------------------------------------------------------------- 1) 归属枚举与列
DO $$ BEGIN
    CREATE TYPE notification_scope AS ENUM ('personal', 'business');
EXCEPTION WHEN duplicate_object THEN NULL;
END $$;

ALTER TABLE notifications
    ADD COLUMN IF NOT EXISTS scope notification_scope NOT NULL DEFAULT 'personal';

COMMENT ON COLUMN notifications.scope IS
    'personal=主人视角消息（pet-owner 接口）；business=机构视角消息（sitter 接口）';

-- 两套接口都按 (user_id, scope) + 时间倒序分页，索引覆盖列表与未读数统计。
CREATE INDEX IF NOT EXISTS idx_notifications_user_scope_time
    ON notifications (user_id, scope, created_at DESC);

-- ---------------------------------------------------------------- 2) 历史数据回填
-- order_created / review_received 固定发给机构；"订单已支付" 复用了 order_accepted
-- 这个 type 但收件人是机构，按标题特判；其余 system_announcement 按收件人角色区分。
UPDATE notifications n
   SET scope = 'business'
 WHERE n.scope = 'personal'
   AND (
        n.type::text IN ('order_created', 'review_received')
        OR (n.type::text = 'order_accepted' AND n.title LIKE '%已支付%')
        OR (n.type::text = 'system_announcement' AND EXISTS (
               SELECT 1 FROM users u WHERE u.id = n.user_id AND u.role::text = 'sitter'))
   );

-- ---------------------------------------------------------------- 3) 通知写入函数
-- 删除 5 参数旧签名：要求所有调用点显式给出 scope，漏改会直接报错，
-- 而不是静默把所有消息都归到 personal。
DROP FUNCTION IF EXISTS fn_notify(BIGINT, notification_type, VARCHAR, TEXT, JSONB);

CREATE OR REPLACE FUNCTION fn_notify(
    p_user_id BIGINT,
    p_type    notification_type,
    p_title   VARCHAR,
    p_body    TEXT,
    p_extra   JSONB DEFAULT '{}',
    p_scope   VARCHAR DEFAULT 'personal'
) RETURNS BIGINT
LANGUAGE plpgsql
AS $fn$
DECLARE
    v_id BIGINT;
BEGIN
    INSERT INTO notifications (user_id, type, title, body, extra, scope)
    VALUES (p_user_id, p_type, p_title, p_body, p_extra,
            COALESCE(NULLIF(p_scope, ''), 'personal')::notification_scope)
    RETURNING id INTO v_id;
    RETURN v_id;
END;
$fn$;

COMMENT ON FUNCTION fn_notify(BIGINT, notification_type, VARCHAR, TEXT, JSONB, VARCHAR) IS
    '写入站内通知；p_scope 决定消息归到个人还是商家视图，默认 personal';
