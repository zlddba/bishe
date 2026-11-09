-- 0015 容量账目一致性
--
-- 现场表现：下单返回 409 "capacity exceeded for selected dates"，而机构明明还有空位。
-- 对账（cmd/capprobe）查出三处叠加的账目错误：
--   A. fn_reserve_capacity 建日容量行时把当时的 sitters.capacity 写进行内，之后机构改容量
--      这些行不会跟着变；而 COALESCE(c.capacity, s.capacity) 的读法让"存在的行"优先，
--      于是一次性快照永久伪装成机构的按日覆盖上限（线上 42 行仍是旧值 1，机构实际 6）。
--   B. 订单走到 completed / evaluated 时不释放容量。演示里订单常被提前完成，
--      它占用的未来夜间就再也还不回去（机构 7 有 39 天被一笔 evaluated 订单占死）。
--   C. 管理员软删进行中的订单同样不释放，used 比任何有效订单都多，且无从对账。
--
-- 本迁移的处置：
--   1) 新增 is_override，只有机构显式设置过的日期才算覆盖，其余跟随机构默认容量；
--   2) 机构默认容量变化时同步刷新未覆盖的日容量行（触发器，覆盖所有写入路径）；
--   3) fn_reserve_capacity 每次占用都按当前生效上限校验并回写，不再信任陈旧快照；
--   4) 订单完成/评价时把尚未消费的未来夜间还给机构；
--   5) 软删进行中的订单时释放容量；
--   6) 修复历史账目：未覆盖行的容量重写为机构默认值，used 一律按有效订单重算。
--
-- 幂等：函数全部 CREATE OR REPLACE，对象带 IF NOT EXISTS，重算语句只改不一致的行。
-- 注意：历史行无法区分"机构真的设过按日覆盖"还是"快照"，这里统一按快照处理。
--      若机构此前手工调过某些日期的容量，需要在应用端重新设置一次。

-- ---------------------------------------------------------------- 1) 覆盖标记
ALTER TABLE sitter_daily_capacity
    ADD COLUMN IF NOT EXISTS is_override BOOLEAN NOT NULL DEFAULT FALSE;

COMMENT ON COLUMN sitter_daily_capacity.is_override IS
    'TRUE=机构显式设置的该日容量；FALSE=跟随机构默认容量，可由系统刷新';

-- ---------------------------------------------------------------- 2) 默认容量联动
CREATE OR REPLACE FUNCTION fn_sync_capacity_rows()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $fn$
BEGIN
    IF NEW.capacity IS DISTINCT FROM OLD.capacity THEN
        UPDATE sitter_daily_capacity
           SET capacity = NEW.capacity, updated_at = now()
         WHERE sitter_id = NEW.id
           AND NOT is_override
           AND capacity IS DISTINCT FROM NEW.capacity;
    END IF;
    RETURN NEW;
END;
$fn$;

DROP TRIGGER IF EXISTS trg_sitter_capacity_sync ON sitters;
CREATE TRIGGER trg_sitter_capacity_sync
    AFTER UPDATE OF capacity ON sitters
    FOR EACH ROW
    EXECUTE FUNCTION fn_sync_capacity_rows();

-- ---------------------------------------------------------------- 3) 占用：按当前上限校验
CREATE OR REPLACE FUNCTION fn_reserve_capacity(
    p_sitter_id  BIGINT,
    p_start_date DATE,
    p_end_date   DATE,
    p_pet_count  INT
) RETURNS VOID
LANGUAGE plpgsql
AS $fn$
DECLARE
    v_date     DATE;
    v_capacity INT;
    v_used     INT;
    v_max_cap  INT;
BEGIN
    SELECT capacity INTO v_max_cap FROM sitters WHERE id = p_sitter_id;
    IF v_max_cap IS NULL THEN
        RAISE EXCEPTION 'Sitter % not found', p_sitter_id;
    END IF;

    v_date := p_start_date;
    WHILE v_date < p_end_date LOOP
        -- 建行时记下当前默认容量；已有行只有在"未被机构覆盖"时才跟着刷新。
        -- ON CONFLICT DO UPDATE 会拿到该行的写锁，天然串行化同一天的并发占用。
        INSERT INTO sitter_daily_capacity (sitter_id, cap_date, used, capacity, is_override)
        VALUES (p_sitter_id, v_date, p_pet_count, v_max_cap, FALSE)
        ON CONFLICT (sitter_id, cap_date) DO UPDATE
           SET used     = sitter_daily_capacity.used + p_pet_count,
               capacity = CASE WHEN sitter_daily_capacity.is_override
                               THEN sitter_daily_capacity.capacity
                               ELSE v_max_cap END,
               updated_at = now()
        RETURNING sitter_daily_capacity.capacity, sitter_daily_capacity.used
           INTO v_capacity, v_used;

        IF v_used > v_capacity THEN
            RAISE EXCEPTION 'Capacity exceeded on %: used=%, max=%',
                to_char(v_date, 'YYYY-MM-DD'), v_used, v_capacity;
        END IF;

        v_date := v_date + INTERVAL '1 day';
    END LOOP;
END;
$fn$;

-- ------------------------------------------------- 4) 完成/评价 与 5) 软删：归还夜间
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
    END IF;
    RETURN NEW;
END;
$fn$;

CREATE OR REPLACE FUNCTION fn_order_deleted()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $fn$
BEGIN
    -- 后台删单是数据纠偏：资金与券仍按退款流程处理，但占用的夜间必须释放，
    -- 否则这笔订单从所有重算口径里消失，used 就成了谁也说不清的残留数。
    IF NEW.status IN ('pending_pay','confirmed','accepted','staying') THEN
        PERFORM fn_release_capacity(NEW.sitter_id, NEW.start_date, NEW.end_date, NEW.pet_count);
    END IF;
    RETURN NEW;
END;
$fn$;

DROP TRIGGER IF EXISTS trg_order_deleted_release ON orders;
CREATE TRIGGER trg_order_deleted_release
    AFTER UPDATE OF deleted_at ON orders
    FOR EACH ROW
    WHEN (OLD.deleted_at IS NULL AND NEW.deleted_at IS NOT NULL)
    EXECUTE FUNCTION fn_order_deleted();

-- ---------------------------------------------------------------- 6) 修复历史账目
-- 6.1 未覆盖的日容量行回到机构当前默认容量
UPDATE sitter_daily_capacity c
   SET capacity = s.capacity, updated_at = now()
  FROM sitters s
 WHERE s.id = c.sitter_id
   AND NOT c.is_override
   AND c.capacity IS DISTINCT FROM s.capacity;

-- 6.2 used 按有效订单重算：某晚被占用 <=> start_date <= 该日 < end_date
WITH truth AS (
    SELECT c.id, COALESCE(SUM(o.pet_count)::int, 0) AS real_used
    FROM sitter_daily_capacity c
    LEFT JOIN orders o ON o.sitter_id = c.sitter_id
         AND o.deleted_at IS NULL
         AND o.status IN ('pending_pay','confirmed','accepted','staying')
         AND o.start_date <= c.cap_date
         AND o.end_date > c.cap_date
    GROUP BY c.id
)
UPDATE sitter_daily_capacity c
   SET used = t.real_used, updated_at = now()
  FROM truth t
 WHERE t.id = c.id
   AND c.used IS DISTINCT FROM t.real_used;
