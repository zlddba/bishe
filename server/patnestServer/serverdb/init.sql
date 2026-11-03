-- ==================================================================
-- PatNest Database Init Script (PostgreSQL 18)
-- Run: psql -U <user> -d <database> -f init.sql
-- ==================================================================

BEGIN;

SET timezone = 'Asia/Shanghai';
SET client_encoding = 'UTF8';

-- ==================================================================
-- 1. Extensions
-- ==================================================================
CREATE EXTENSION IF NOT EXISTS pg_trgm;
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- ==================================================================
-- 2. Custom Types (ENUM)
-- ==================================================================
DO $$ BEGIN
    CREATE TYPE user_role AS ENUM ('owner','sitter','admin');
EXCEPTION WHEN duplicate_object THEN NULL;
END $$;

DO $$ BEGIN
    CREATE TYPE pet_species AS ENUM ('cat','dog','other');
EXCEPTION WHEN duplicate_object THEN NULL;
END $$;

DO $$ BEGIN
    CREATE TYPE pet_size AS ENUM ('tiny','small','medium','large','xlarge');
EXCEPTION WHEN duplicate_object THEN NULL;
END $$;

DO $$ BEGIN
    CREATE TYPE sitter_type AS ENUM ('home','shop','visit','other');
EXCEPTION WHEN duplicate_object THEN NULL;
END $$;

DO $$ BEGIN
    CREATE TYPE sitter_status AS ENUM ('pending','active','inactive','banned','rejected');
EXCEPTION WHEN duplicate_object THEN NULL;
END $$;

DO $$ BEGIN
    CREATE TYPE order_status AS ENUM ('pending_pay','confirmed','accepted','staying','completed','evaluated','cancelled','rejected');
EXCEPTION WHEN duplicate_object THEN NULL;
END $$;

DO $$ BEGIN
    CREATE TYPE payment_status AS ENUM ('none','paid','refunded');
EXCEPTION WHEN duplicate_object THEN NULL;
END $$;

DO $$ BEGIN
    CREATE TYPE pay_method AS ENUM ('mock','alipay','wechat');
EXCEPTION WHEN duplicate_object THEN NULL;
END $$;

DO $$ BEGIN
    CREATE TYPE notification_type AS ENUM ('order_created','order_accepted','order_cancelled','order_rejected','order_completed','dynamics_published','review_received','system_announcement');
EXCEPTION WHEN duplicate_object THEN NULL;
END $$;

DO $$ BEGIN
    CREATE TYPE price_mode AS ENUM ('per_day','per_visit');
EXCEPTION WHEN duplicate_object THEN NULL;
END $$;
-- ==================================================================
-- 3. Tables
-- ==================================================================

-- 3.1 users
CREATE TABLE IF NOT EXISTS users (
    id              BIGSERIAL       PRIMARY KEY,
    username        VARCHAR(50)     NOT NULL,
    password_hash   VARCHAR(256)    NOT NULL,
    nickname        VARCHAR(64),
    phone           VARCHAR(20),
    email           VARCHAR(128),
    role            user_role       NOT NULL DEFAULT 'owner',
    avatar_url      TEXT,
    status          SMALLINT        NOT NULL DEFAULT 1,
    login_fail_count SMALLINT       NOT NULL DEFAULT 0,
    locked_until    TIMESTAMPTZ,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ     NOT NULL DEFAULT now(),
    deleted_at      TIMESTAMPTZ,
    CONSTRAINT uq_users_username UNIQUE (username),
    CONSTRAINT uq_users_phone    UNIQUE (phone)
);

COMMENT ON TABLE users IS 'Platform users: pet owners, sitters, admins';
COMMENT ON COLUMN users.status IS '1=active, 0=disabled';
COMMENT ON COLUMN users.locked_until IS 'Account lock expiry after repeated login failures';

-- 3.2 pets
CREATE TABLE IF NOT EXISTS pets (
    id              BIGSERIAL       PRIMARY KEY,
    owner_id        BIGINT          NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    name            VARCHAR(64)     NOT NULL,
    species         pet_species     NOT NULL,
    breed           VARCHAR(100),
    body_size       pet_size        DEFAULT 'medium',
    age_month       INT             CHECK (age_month >= 0 AND age_month <= 360),
    weight_kg       NUMERIC(6,2),
    vaccinated      BOOLEAN         NOT NULL DEFAULT FALSE,
    sterilized      BOOLEAN         NOT NULL DEFAULT FALSE,
    photo_urls      TEXT[]          DEFAULT '{}',
    avatar_url      TEXT,
    notes           TEXT,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ     NOT NULL DEFAULT now(),
    deleted_at      TIMESTAMPTZ
);

COMMENT ON TABLE pets IS 'Pet profiles owned by users (soft-delete via deleted_at)';

-- 3.3 sitters (boarding facilities)
CREATE TABLE IF NOT EXISTS sitters (
    id              BIGSERIAL       PRIMARY KEY,
    user_id         BIGINT          NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    name            VARCHAR(128)    NOT NULL,
    type            sitter_type     NOT NULL DEFAULT 'home',
    description     TEXT,
    daily_price     NUMERIC(10,2)   NOT NULL DEFAULT 0 CHECK (daily_price >= 0),
    visit_price     NUMERIC(10,2)   DEFAULT 0 CHECK (visit_price >= 0),
    price_mode      price_mode      NOT NULL DEFAULT 'per_day',
    capacity        INT             NOT NULL DEFAULT 1 CHECK (capacity >= 1),
    address         VARCHAR(255),
    latitude        NUMERIC(10,7),
    longitude       NUMERIC(10,7),
    avg_score       NUMERIC(3,2)    NOT NULL DEFAULT 0 CHECK (avg_score BETWEEN 0 AND 5),
    review_count    INT             NOT NULL DEFAULT 0,
    cover_photo_url TEXT,
    detail_photos   TEXT[]          DEFAULT '{}',
    contact_person  VARCHAR(64),
    license_photo_url TEXT,
    id_card_photo_url TEXT,
    review_note     VARCHAR(255),
    status          sitter_status   NOT NULL DEFAULT 'pending',
    banned_at       TIMESTAMPTZ,
    banned_reason   VARCHAR(255),
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ     NOT NULL DEFAULT now(),
    deleted_at      TIMESTAMPTZ
);

COMMENT ON TABLE sitters IS 'Boarding facilities / individual sitters';
COMMENT ON COLUMN sitters.avg_score IS 'Denormalized average, updated by trigger on review insert';

-- 3.4 sitter_daily_capacity
CREATE TABLE IF NOT EXISTS sitter_daily_capacity (
    id              BIGSERIAL       PRIMARY KEY,
    sitter_id       BIGINT          NOT NULL REFERENCES sitters(id) ON DELETE CASCADE,
    cap_date        DATE            NOT NULL,
    used            INT             NOT NULL DEFAULT 0 CHECK (used >= 0),
    capacity        INT             NOT NULL DEFAULT 1 CHECK (capacity >= 1),
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ     NOT NULL DEFAULT now(),
    CONSTRAINT uq_sitter_date UNIQUE (sitter_id, cap_date)
);

COMMENT ON TABLE sitter_daily_capacity IS 'Per-day capacity tracking for each sitter';

-- 3.5 orders
CREATE TABLE IF NOT EXISTS orders (
    id                BIGSERIAL       PRIMARY KEY,
    order_no          VARCHAR(64)     NOT NULL,
    owner_id          BIGINT          NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    sitter_id         BIGINT          NOT NULL REFERENCES sitters(id) ON DELETE RESTRICT,
    status            order_status    NOT NULL DEFAULT 'pending_pay',
    start_date        DATE            NOT NULL,
    end_date          DATE            NOT NULL,
    total_days        INT             NOT NULL DEFAULT 0,
    pet_count         SMALLINT        NOT NULL DEFAULT 1 CHECK (pet_count BETWEEN 1 AND 10),
    estimated_amount  NUMERIC(12,2)   NOT NULL DEFAULT 0,
    total_amount      NUMERIC(12,2)   NOT NULL DEFAULT 0,
    payment_status    payment_status  NOT NULL DEFAULT 'none',
    cancel_reason     VARCHAR(255),
    cancel_time       TIMESTAMPTZ,
    paid_at           TIMESTAMPTZ,
    accepted_at       TIMESTAMPTZ,
    completed_at      TIMESTAMPTZ,
    evaluated_at      TIMESTAMPTZ,
    remark            TEXT,
    created_at        TIMESTAMPTZ     NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ     NOT NULL DEFAULT now(),
    deleted_at        TIMESTAMPTZ,
    CONSTRAINT uq_orders_no UNIQUE (order_no),
    CONSTRAINT chk_orders_dates CHECK (end_date > start_date)
);

COMMENT ON TABLE orders IS 'Core booking orders';

-- 3.6 order_pet (many-to-many: order <-> pet)
CREATE TABLE IF NOT EXISTS order_pet (
    id              BIGSERIAL       PRIMARY KEY,
    order_id        BIGINT          NOT NULL REFERENCES orders(id) ON DELETE CASCADE,
    pet_id          BIGINT          NOT NULL REFERENCES pets(id) ON DELETE RESTRICT,
    stay_date       DATE            NOT NULL,
    checkin_time    TIME            DEFAULT '09:00:00',
    checkout_time   TIME,
    status          VARCHAR(20)     DEFAULT 'assigned' CHECK (status IN ('assigned','staying','picked_up','cancelled')),
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ     NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_order_pet ON order_pet (order_id, pet_id, stay_date);

-- 3.7 dynamics (boarding updates posted by sitter)
CREATE TABLE IF NOT EXISTS dynamics (
    id              BIGSERIAL       PRIMARY KEY,
    order_id        BIGINT          NOT NULL REFERENCES orders(id) ON DELETE CASCADE,
    sitter_id       BIGINT          NOT NULL REFERENCES sitters(id) ON DELETE RESTRICT,
    author_id       BIGINT          NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    content         TEXT            NOT NULL CHECK (length(content) <= 2000),
    photos          TEXT[]          DEFAULT '{}',
    tags            TEXT[]          DEFAULT '{}',
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ     NOT NULL DEFAULT now()
);

COMMENT ON TABLE dynamics IS 'Boarding progress updates with photos/tags';

-- 3.8 reviews
CREATE TABLE IF NOT EXISTS reviews (
    id                BIGSERIAL       PRIMARY KEY,
    order_id          BIGINT          NOT NULL REFERENCES orders(id) ON DELETE RESTRICT,
    sitter_id         BIGINT          NOT NULL REFERENCES sitters(id) ON DELETE RESTRICT,
    owner_id          BIGINT          NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    overall_score     SMALLINT        NOT NULL CHECK (overall_score BETWEEN 1 AND 5),
    service_score     SMALLINT        NOT NULL CHECK (service_score BETWEEN 1 AND 5),
    environment_score SMALLINT        NOT NULL CHECK (environment_score BETWEEN 1 AND 5),
    hygiene_score     SMALLINT        NOT NULL CHECK (hygiene_score BETWEEN 1 AND 5),
    owner_comment     TEXT            CHECK (length(owner_comment) <= 1000),
    owner_photos      TEXT[]          DEFAULT '{}',
    reply_text        TEXT,
    reply_at          TIMESTAMPTZ,
    is_visible        BOOLEAN         NOT NULL DEFAULT TRUE,
    created_at        TIMESTAMPTZ     NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ     NOT NULL DEFAULT now(),
    CONSTRAINT uq_reviews_order UNIQUE (order_id)
);

COMMENT ON TABLE reviews IS 'One review per completed order';

-- 3.9 payments
CREATE TABLE IF NOT EXISTS payments (
    id              BIGSERIAL       PRIMARY KEY,
    order_id        BIGINT          NOT NULL REFERENCES orders(id) ON DELETE RESTRICT,
    amount          NUMERIC(12,2)   NOT NULL CHECK (amount >= 0), -- 允许 0：全额抵扣/免费订单
    method          pay_method      NOT NULL DEFAULT 'mock',
    transaction_id  VARCHAR(128),
    status          VARCHAR(20)     NOT NULL DEFAULT 'created' CHECK (status IN ('created','paid','failed','refunded')),
    raw_response    JSONB,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT now(),
    paid_at         TIMESTAMPTZ,
    refunded_at     TIMESTAMPTZ
);

-- 3.10 notifications
CREATE TABLE IF NOT EXISTS notifications (
    id              BIGSERIAL       PRIMARY KEY,
    user_id         BIGINT          NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    type            notification_type NOT NULL,
    title           VARCHAR(128)    DEFAULT '',
    body            TEXT            NOT NULL,
    extra           JSONB           DEFAULT '{}',
    is_read         BOOLEAN         NOT NULL DEFAULT FALSE,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT now()
);

-- 3.11 audit_logs
CREATE TABLE IF NOT EXISTS audit_logs (
    id              BIGSERIAL       PRIMARY KEY,
    actor_id        BIGINT          REFERENCES users(id) ON DELETE SET NULL,
    action          VARCHAR(100)    NOT NULL,
    resource_type   VARCHAR(50)     NOT NULL,
    resource_id     BIGINT,
    detail          JSONB           DEFAULT '{}',
    ip_address      INET,
    user_agent      TEXT,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT now()
);

COMMENT ON TABLE audit_logs IS 'Platform audit trail for admin actions';

-- 3.12 refresh_tokens (JWT refresh token storage)
CREATE TABLE IF NOT EXISTS refresh_tokens (
    id              BIGSERIAL       PRIMARY KEY,
    user_id         BIGINT          NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token_hash      VARCHAR(128)    NOT NULL,
    expires_at      TIMESTAMPTZ     NOT NULL,
    revoked_at      TIMESTAMPTZ,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_refresh_token_hash ON refresh_tokens (token_hash);
-- ==================================================================
-- 4. Indexes
-- ==================================================================

-- users
CREATE INDEX IF NOT EXISTS idx_users_role       ON users (role) WHERE deleted_at IS NULL;
CREATE INDEX IF NOT EXISTS idx_users_created    ON users (created_at DESC);

-- pets
CREATE INDEX IF NOT EXISTS idx_pets_owner       ON pets (owner_id) WHERE deleted_at IS NULL;
CREATE INDEX IF NOT EXISTS idx_pets_species     ON pets (species) WHERE deleted_at IS NULL;
CREATE INDEX IF NOT EXISTS idx_pets_name_trgm   ON pets USING gin (name gin_trgm_ops);

-- sitters
CREATE INDEX IF NOT EXISTS idx_sitters_user     ON sitters (user_id) WHERE deleted_at IS NULL;
CREATE INDEX IF NOT EXISTS idx_sitters_status   ON sitters (status) WHERE deleted_at IS NULL;
CREATE INDEX IF NOT EXISTS idx_sitters_type     ON sitters (type) WHERE deleted_at IS NULL;
CREATE INDEX IF NOT EXISTS idx_sitters_score    ON sitters (avg_score DESC) WHERE deleted_at IS NULL;
CREATE INDEX IF NOT EXISTS idx_sitters_price    ON sitters (daily_price) WHERE deleted_at IS NULL;
CREATE INDEX IF NOT EXISTS idx_sitters_name_trgm ON sitters USING gin (name gin_trgm_ops);
CREATE INDEX IF NOT EXISTS idx_sitters_addr_trgm ON sitters USING gin (address gin_trgm_ops);

-- sitter_daily_capacity
CREATE INDEX IF NOT EXISTS idx_capacity_sitter_date ON sitter_daily_capacity (sitter_id, cap_date);

-- orders
CREATE INDEX IF NOT EXISTS idx_orders_owner     ON orders (owner_id, created_at DESC) WHERE deleted_at IS NULL;
CREATE INDEX IF NOT EXISTS idx_orders_sitter    ON orders (sitter_id, created_at DESC) WHERE deleted_at IS NULL;
CREATE INDEX IF NOT EXISTS idx_orders_status    ON orders (status) WHERE deleted_at IS NULL;
CREATE INDEX IF NOT EXISTS idx_orders_dates     ON orders (start_date, end_date) WHERE deleted_at IS NULL;

-- order_pet
CREATE INDEX IF NOT EXISTS idx_order_pet_pet    ON order_pet (pet_id);

-- dynamics
CREATE INDEX IF NOT EXISTS idx_dynamics_order   ON dynamics (order_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_dynamics_sitter  ON dynamics (sitter_id);

-- reviews
CREATE INDEX IF NOT EXISTS idx_reviews_sitter   ON reviews (sitter_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_reviews_owner    ON reviews (owner_id);

-- payments
CREATE INDEX IF NOT EXISTS idx_payments_order   ON payments (order_id);
CREATE INDEX IF NOT EXISTS idx_payments_status  ON payments (status);

-- notifications
CREATE INDEX IF NOT EXISTS idx_notifications_user ON notifications (user_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_notifications_unread ON notifications (user_id) WHERE is_read = FALSE;

-- audit_logs
CREATE INDEX IF NOT EXISTS idx_audit_actor      ON audit_logs (actor_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_audit_action     ON audit_logs (action);
CREATE INDEX IF NOT EXISTS idx_audit_resource   ON audit_logs (resource_type, resource_id);

-- refresh_tokens
CREATE INDEX IF NOT EXISTS idx_refresh_user     ON refresh_tokens (user_id) WHERE revoked_at IS NULL;
-- ==================================================================
-- 5. Functions
-- ==================================================================

-- 5.1 Auto-update updated_at timestamp
CREATE OR REPLACE FUNCTION fn_set_updated_at()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $fn$
BEGIN
    NEW.updated_at := now();
    RETURN NEW;
END;
$fn$;

-- 5.2 Generate order number: PATN-YYYYMMDD-SEQ
CREATE OR REPLACE FUNCTION fn_generate_order_no()
RETURNS VARCHAR(64)
LANGUAGE plpgsql
AS $fn$
DECLARE
    v_date_part TEXT;
    v_seq       BIGINT;
    v_order_no  VARCHAR(64);
BEGIN
    v_date_part := to_char(now(), 'YYYYMMDD');
    SELECT COALESCE(MAX(id), 0) + 1 INTO v_seq FROM orders;
    v_order_no := 'PATN-' || v_date_part || '-' || lpad(v_seq::text, 6, '0');
    RETURN v_order_no;
END;
$fn$;

-- 5.3 Calculate order cost
CREATE OR REPLACE FUNCTION fn_calculate_order_cost(
    p_price_mode    price_mode,
    p_daily_price   NUMERIC,
    p_visit_price   NUMERIC,
    p_start_date    DATE,
    p_end_date      DATE,
    p_pet_count     INT
) RETURNS TABLE (total_amount NUMERIC, total_days INT)
LANGUAGE plpgsql IMMUTABLE
AS $fn$
DECLARE
    v_days INT;
BEGIN
    v_days := (p_end_date - p_start_date);
    IF v_days <= 0 THEN
        RAISE EXCEPTION 'end_date must be after start_date';
    END IF;

    IF p_price_mode = 'per_day' THEN
        total_amount := v_days * p_daily_price * p_pet_count;
        total_days := v_days;
    ELSE
        total_amount := p_visit_price * p_pet_count;
        total_days := 0;
    END IF;

    RETURN NEXT;
END;
$fn$;

-- 5.4 Update sitter average score after review
CREATE OR REPLACE FUNCTION fn_update_sitter_score()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $fn$
DECLARE
    v_sitter_id BIGINT;
BEGIN
    IF TG_OP = 'DELETE' THEN
        v_sitter_id := OLD.sitter_id;
    ELSE
        v_sitter_id := NEW.sitter_id;
    END IF;

    UPDATE sitters
    SET avg_score = COALESCE(
            (SELECT ROUND(AVG(overall_score)::numeric, 2)
             FROM reviews
             WHERE sitter_id = v_sitter_id AND is_visible = TRUE), 0),
        review_count = (
            SELECT COUNT(*)
            FROM reviews
            WHERE sitter_id = v_sitter_id AND is_visible = TRUE
        )
    WHERE id = v_sitter_id;

    IF TG_OP = 'DELETE' THEN
        RETURN OLD;
    END IF;
    RETURN NEW;
END;
$fn$;

-- 5.5 Reserve capacity (call within transaction when creating order)
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
        INSERT INTO sitter_daily_capacity (sitter_id, cap_date, used, capacity)
        VALUES (p_sitter_id, v_date, p_pet_count, v_max_cap)
        ON CONFLICT (sitter_id, cap_date)
        DO UPDATE SET used = sitter_daily_capacity.used + p_pet_count;

        SELECT used, capacity INTO v_used, v_capacity
        FROM sitter_daily_capacity
        WHERE sitter_id = p_sitter_id AND cap_date = v_date
        FOR UPDATE;

        IF v_used > v_capacity THEN
            RAISE EXCEPTION 'Capacity exceeded on %: used=%, max=%', v_date, v_used, v_capacity;
        END IF;

        v_date := v_date + INTERVAL '1 day';
    END LOOP;
END;
$fn$;

-- 5.6 Release capacity on order cancellation
CREATE OR REPLACE FUNCTION fn_release_capacity(
    p_sitter_id  BIGINT,
    p_start_date DATE,
    p_end_date   DATE,
    p_pet_count  INT
) RETURNS VOID
LANGUAGE plpgsql
AS $fn$
DECLARE
    v_date DATE;
BEGIN
    v_date := p_start_date;
    WHILE v_date < p_end_date LOOP
        UPDATE sitter_daily_capacity
        SET used = GREATEST(used - p_pet_count, 0)
        WHERE sitter_id = p_sitter_id AND cap_date = v_date;

        v_date := v_date + INTERVAL '1 day';
    END LOOP;
END;
$fn$;

-- 5.7 Check pet availability for date range
CREATE OR REPLACE FUNCTION fn_check_pet_available(
    p_pet_id     BIGINT,
    p_start_date DATE,
    p_end_date   DATE
) RETURNS BOOLEAN
LANGUAGE plpgsql STABLE
AS $fn$
DECLARE
    v_conflict INT;
BEGIN
    SELECT COUNT(*) INTO v_conflict
    FROM order_pet op
    JOIN orders o ON o.id = op.order_id
    WHERE op.pet_id = p_pet_id
      AND o.status IN ('pending_pay','confirmed','accepted','staying')
      AND o.deleted_at IS NULL
      AND o.start_date < p_end_date
      AND o.end_date > p_start_date;
    RETURN v_conflict = 0;
END;
$fn$;

-- 5.8 Create notification helper
CREATE OR REPLACE FUNCTION fn_notify(
    p_user_id BIGINT,
    p_type    notification_type,
    p_title   VARCHAR,
    p_body    TEXT,
    p_extra   JSONB DEFAULT '{}'
) RETURNS BIGINT
LANGUAGE plpgsql
AS $fn$
DECLARE
    v_id BIGINT;
BEGIN
    INSERT INTO notifications (user_id, type, title, body, extra)
    VALUES (p_user_id, p_type, p_title, p_body, p_extra)
    RETURNING id INTO v_id;
    RETURN v_id;
END;
$fn$;

-- 5.9 Audit log helper
CREATE OR REPLACE FUNCTION fn_audit(
    p_actor_id      BIGINT,
    p_action        VARCHAR,
    p_resource_type VARCHAR,
    p_resource_id   BIGINT,
    p_detail        JSONB DEFAULT '{}',
    p_ip            INET DEFAULT NULL
) RETURNS VOID
LANGUAGE plpgsql
AS $fn$
BEGIN
    INSERT INTO audit_logs (actor_id, action, resource_type, resource_id, detail, ip_address)
    VALUES (p_actor_id, p_action, p_resource_type, p_resource_id, p_detail, p_ip);
END;
$fn$;

-- 5.10 Soft delete helper
CREATE OR REPLACE FUNCTION fn_soft_delete(p_table TEXT, p_id BIGINT)
RETURNS VOID
LANGUAGE plpgsql
AS $fn$
BEGIN
    EXECUTE format('UPDATE %I SET deleted_at = now() WHERE id = $1 AND deleted_at IS NULL', p_table)
    USING p_id;
END;
$fn$;
-- ==================================================================
-- 6. Triggers
-- ==================================================================

-- 6.1 updated_at triggers (auto-maintain timestamp on UPDATE)
CREATE TRIGGER trg_users_updated     BEFORE UPDATE ON users     FOR EACH ROW EXECUTE FUNCTION fn_set_updated_at();
CREATE TRIGGER trg_pets_updated      BEFORE UPDATE ON pets      FOR EACH ROW EXECUTE FUNCTION fn_set_updated_at();
CREATE TRIGGER trg_sitters_updated   BEFORE UPDATE ON sitters   FOR EACH ROW EXECUTE FUNCTION fn_set_updated_at();
CREATE TRIGGER trg_orders_updated    BEFORE UPDATE ON orders    FOR EACH ROW EXECUTE FUNCTION fn_set_updated_at();
CREATE TRIGGER trg_order_pet_updated BEFORE UPDATE ON order_pet FOR EACH ROW EXECUTE FUNCTION fn_set_updated_at();
CREATE TRIGGER trg_dynamics_updated  BEFORE UPDATE ON dynamics  FOR EACH ROW EXECUTE FUNCTION fn_set_updated_at();
CREATE TRIGGER trg_reviews_updated   BEFORE UPDATE ON reviews   FOR EACH ROW EXECUTE FUNCTION fn_set_updated_at();
CREATE TRIGGER trg_capacity_updated  BEFORE UPDATE ON sitter_daily_capacity FOR EACH ROW EXECUTE FUNCTION fn_set_updated_at();

-- 6.2 Review score aggregation triggers
CREATE TRIGGER trg_review_score_insert
    AFTER INSERT ON reviews
    FOR EACH ROW EXECUTE FUNCTION fn_update_sitter_score();

CREATE TRIGGER trg_review_score_update
    AFTER UPDATE OF is_visible, overall_score ON reviews
    FOR EACH ROW EXECUTE FUNCTION fn_update_sitter_score();

CREATE TRIGGER trg_review_score_delete
    AFTER DELETE ON reviews
    FOR EACH ROW EXECUTE FUNCTION fn_update_sitter_score();

-- 6.3 Auto-release capacity when order is cancelled or rejected
CREATE OR REPLACE FUNCTION fn_order_status_changed()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $fn$
BEGIN
    IF OLD.status IN ('pending_pay','confirmed','accepted')
       AND NEW.status IN ('cancelled','rejected') THEN
        PERFORM fn_release_capacity(NEW.sitter_id, NEW.start_date, NEW.end_date, NEW.pet_count);
    END IF;
    RETURN NEW;
END;
$fn$;

CREATE TRIGGER trg_order_cancel_release
    AFTER UPDATE OF status ON orders
    FOR EACH ROW
    WHEN (OLD.status IS DISTINCT FROM NEW.status)
    EXECUTE FUNCTION fn_order_status_changed();

-- ==================================================================
-- 7. Views
-- ==================================================================

-- 7.1 Active sitters (public listing)
CREATE OR REPLACE VIEW v_active_sitters AS
SELECT
    s.id, s.name, s.type, s.description,
    s.daily_price, s.visit_price, s.price_mode, s.capacity,
    s.address, s.latitude, s.longitude,
    s.avg_score, s.review_count,
    s.cover_photo_url, s.detail_photos,
    s.created_at
FROM sitters s
WHERE s.status = 'active' AND s.deleted_at IS NULL;

-- 7.2 Order detail with joins
CREATE OR REPLACE VIEW v_order_detail AS
SELECT
    o.id, o.order_no, o.status, o.payment_status,
    o.start_date, o.end_date, o.total_days, o.pet_count,
    o.estimated_amount, o.total_amount,
    o.created_at, o.updated_at,
    o.cancel_reason, o.cancel_time,
    u.id AS owner_user_id, u.username AS owner_username, u.nickname AS owner_nickname,
    s.id AS sitter_facility_id, s.name AS sitter_name, s.address AS sitter_address
FROM orders o
JOIN users u ON u.id = o.owner_id
JOIN sitters s ON s.id = o.sitter_id
WHERE o.deleted_at IS NULL;

-- 7.3 Pending orders for sitter dashboard
CREATE OR REPLACE VIEW v_pending_orders AS
SELECT
    o.id, o.order_no, o.owner_id, o.sitter_id,
    o.start_date, o.end_date, o.pet_count, o.estimated_amount,
    o.status, o.created_at,
    u.username AS owner_username, u.nickname AS owner_nickname
FROM orders o
JOIN users u ON u.id = o.owner_id
WHERE o.status = 'pending_pay' AND o.deleted_at IS NULL;

-- 7.4 Sitter review summary
CREATE OR REPLACE VIEW v_sitter_reviews AS
SELECT
    r.id, r.order_id, r.sitter_id, r.owner_id,
    r.overall_score, r.service_score, r.environment_score, r.hygiene_score,
    r.owner_comment, r.owner_photos, r.reply_text, r.reply_at,
    r.is_visible, r.created_at,
    u.username AS reviewer_name, u.nickname AS reviewer_nickname
FROM reviews r
JOIN users u ON u.id = r.owner_id
WHERE r.is_visible = TRUE;

-- 7.5 Unread notification count per user
CREATE OR REPLACE VIEW v_unread_notifications AS
SELECT user_id, COUNT(*) AS unread_count
FROM notifications
WHERE is_read = FALSE
GROUP BY user_id;

-- 7.6 Order with pet names (useful for listing)
CREATE OR REPLACE VIEW v_order_pets AS
SELECT
    o.id AS order_id,
    o.order_no,
    STRING_AGG(p.name, ', ' ORDER BY p.name) AS pet_names,
    COUNT(p.id) AS actual_pet_count
FROM orders o
JOIN order_pet op ON op.order_id = o.id
JOIN pets p ON p.id = op.pet_id
WHERE o.deleted_at IS NULL
GROUP BY o.id, o.order_no;

-- 7.7 Sitter capacity availability (next 30 days)
CREATE OR REPLACE VIEW v_sitter_availability AS
SELECT
    s.id AS sitter_id,
    s.name AS sitter_name,
    c.cap_date,
    c.capacity,
    c.used,
    c.capacity - c.used AS available
FROM sitters s
         CROSS JOIN LATERAL (
    SELECT generate_series(
                           CURRENT_DATE,
                           CURRENT_DATE + INTERVAL '30 days',
                           INTERVAL '1 day'
           )::date AS cap_date
    ) d
         LEFT JOIN sitter_daily_capacity c
                   ON c.sitter_id = s.id AND c.cap_date = d.cap_date
WHERE s.status = 'active' AND s.deleted_at IS NULL;
-- ==================================================================
-- 8. Seed Data
-- ==================================================================

-- 8.1 Admin account
-- Password: Admin@123456 (bcrypt cost=10)
-- Replace the hash below with a real bcrypt hash before production use
INSERT INTO users (username, password_hash, nickname, role, status) VALUES
('admin', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', 'System Admin', 'admin', 1);

-- 8.2 Demo pet owner
-- Password: Owner@123456
INSERT INTO users (username, password_hash, nickname, phone, role, status) VALUES
('petowner01', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', 'Zhang San', '13800001111', 'owner', 1);

-- 8.3 Demo sitter
-- Password: Sitter@123456
INSERT INTO users (username, password_hash, nickname, phone, role, status) VALUES
('sitter01', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', 'Li Si', '13800002222', 'sitter', 1);

-- 8.4 Demo sitter facilities
INSERT INTO sitters (user_id, name, type, description, daily_price, visit_price, price_mode, capacity, address, latitude, longitude, status) VALUES
(3, 'Anxin Pet Home', 'home', 'Warm home boarding with garden and indoor play area. 24h care.', 50.00, 30.00, 'per_day', 5, 'Beijing Chaoyang District XX Street No.8', 39.9042000, 116.4074000, 'active');

INSERT INTO sitters (user_id, name, type, description, daily_price, visit_price, price_mode, capacity, address, latitude, longitude, status) VALUES
(3, 'Happy Paws Shop', 'shop', 'Professional pet boarding shop with 24h video monitoring and vet on call.', 80.00, 0, 'per_day', 10, 'Beijing Haidian District YY Road No.15', 39.9590000, 116.2980000, 'active');

-- 8.5 Demo pets
INSERT INTO pets (owner_id, name, species, breed, body_size, age_month, weight_kg, vaccinated, sterilized, notes) VALUES
(2, 'Doudou', 'dog', 'Golden Retriever', 'large', 18, 28.5, TRUE, TRUE, 'Allergic to peanuts'),
(2, 'Mimi', 'cat', 'British Shorthair', 'small', 24, 4.2, TRUE, FALSE, NULL);

-- 8.6 Demo order (pending_pay)
INSERT INTO orders (order_no, owner_id, sitter_id, status, start_date, end_date, total_days, pet_count, estimated_amount, total_amount, payment_status) VALUES
('PATN-20261029-000001', 2, 1, 'pending_pay', '2026-11-01', '2026-11-04', 3, 1, 150.00, 150.00, 'none');

INSERT INTO order_pet (order_id, pet_id, stay_date) VALUES
(1, 1, '2026-11-01');

-- 8.7 Pre-fill capacity for demo dates
INSERT INTO sitter_daily_capacity (sitter_id, cap_date, used, capacity) VALUES
(1, '2026-11-01', 1, 5),
(1, '2026-11-02', 1, 5),
(1, '2026-11-03', 1, 5);

-- 8.8 Demo notification
INSERT INTO notifications (user_id, type, title, body, extra) VALUES
(3, 'order_created', 'New Order Received', 'Order PATN-20261029-000001 from Zhang San, 1 pet, Nov 1-4.', '{"order_id": 1}');

-- ==================================================================
-- 9. Permissions (uncomment and adjust for your app DB user)
-- ==================================================================
-- CREATE ROLE patnest_app LOGIN PASSWORD 'change_me_in_env';
-- GRANT CONNECT ON DATABASE patnest TO patnest_app;
-- GRANT USAGE ON SCHEMA public TO patnest_app;
-- GRANT ALL PRIVILEGES ON ALL TABLES IN SCHEMA public TO patnest_app;
-- GRANT ALL PRIVILEGES ON ALL SEQUENCES IN SCHEMA public TO patnest_app;
-- GRANT EXECUTE ON ALL FUNCTIONS IN SCHEMA public TO patnest_app;

COMMIT;

-- ==================================================================
-- PatNest database initialization complete.
-- Tables: users, pets, sitters, sitter_daily_capacity, orders,
--         order_pet, dynamics, reviews, payments, notifications,
--         audit_logs, refresh_tokens
-- Functions: fn_set_updated_at, fn_generate_order_no,
--            fn_calculate_order_cost, fn_update_sitter_score,
--            fn_reserve_capacity, fn_release_capacity,
--            fn_check_pet_available, fn_notify, fn_audit, fn_soft_delete
-- Triggers: updated_at auto-maintain, review score aggregation,
--           order cancel capacity release
-- Views: v_active_sitters, v_order_detail, v_pending_orders,
--        v_sitter_reviews, v_unread_notifications, v_order_pets,
--        v_sitter_availability
-- ==================================================================
