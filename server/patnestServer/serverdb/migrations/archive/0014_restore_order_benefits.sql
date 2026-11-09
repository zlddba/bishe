-- 0014 订单作废时退回优惠券与积分。
--
-- 此前 fn_order_status_changed 只释放容量：主人取消一个"待支付"订单、或者机构拒单之后，
-- 券已经被置成 used、积分已经被扣掉，却永远退不回来。取消一个根本没付钱的订单
-- 要损失一张券和一笔积分，是实打实的资损，而且没有任何一条路径能补救。
--
-- 做成数据库函数而不是在每个 Go 出口各写一遍：取消/拒单/后台退款/超时自动取消共六处入口，
-- 漏一处就是线上资损。挂在既有触发器上和容量释放保持同一时机，天然覆盖全部路径。
-- 函数按行状态与流水存在性判重，所以是幂等的，重复调用不会退两次。

CREATE OR REPLACE FUNCTION fn_restore_order_benefits(p_order_id BIGINT)
RETURNS VOID
LANGUAGE plpgsql
AS $fn$
DECLARE
    v_owner_id BIGINT;
    v_points   INT;
BEGIN
    SELECT owner_id, points_used INTO v_owner_id, v_points
      FROM orders
     WHERE id = p_order_id;
    IF NOT FOUND THEN
        RETURN;
    END IF;

    -- 先回退券模板的已用量，再解绑 user_coupons：
    -- 后者靠 order_id + status 定位这一单用掉的券，置空后就找不到关联了。
    UPDATE coupons
       SET used_qty = GREATEST(used_qty - 1, 0)
     WHERE id IN (SELECT coupon_id FROM user_coupons
                   WHERE order_id = p_order_id AND status = 'used');

    UPDATE user_coupons
       SET status = 'unused', order_id = NULL, used_at = NULL
     WHERE order_id = p_order_id AND status = 'used';

    -- 积分只在确实扣过、且这笔订单还没有退回流水时回补；反向流水保证可对账。
    IF COALESCE(v_points, 0) > 0 AND NOT EXISTS (
        SELECT 1 FROM points_ledger
         WHERE ref_type = 'order' AND ref_id = p_order_id AND change_amount > 0
    ) THEN
        UPDATE users
           SET points = points + v_points
         WHERE id = v_owner_id;

        INSERT INTO points_ledger (user_id, change_amount, balance, reason, ref_type, ref_id)
        SELECT v_owner_id, v_points, u.points, '订单作废退回', 'order', p_order_id
          FROM users u
         WHERE u.id = v_owner_id;
    END IF;
END;
$fn$;

COMMENT ON FUNCTION fn_restore_order_benefits(BIGINT) IS
    '退回指定订单占用的优惠券与积分；幂等，可重复调用';

-- 与容量释放同一时机触发。staying 也纳入权益退回（住到一半被后台取消，券和积分同样该退），
-- 但容量保持原有三种状态才释放：staying 的容量由后台退款逻辑按已住天数显式处理，
-- 在这里重复释放会把已消耗的天数也退成空位。
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
    END IF;
    RETURN NEW;
END;
$fn$;
