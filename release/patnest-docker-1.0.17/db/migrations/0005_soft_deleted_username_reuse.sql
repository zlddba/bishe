-- 0005 用户名唯一改为"仅未删除行唯一"，修复软删除后无法重新注册同名账号。
--
-- 背景：注册查重只统计 deleted_at IS NULL 的行（auth_handler.Register），
--       而 uq_users_username 是全表唯一（含软删行）；管理员删除用户为软删除
--       （admin_users.AdminDeleteUser 置 deleted_at = now()），此后同名注册
--       查重通过、INSERT 却撞唯一约束，接口统一返回"创建用户失败"。
--
-- 变更：删除全表唯一约束，改为部分唯一索引，口径与登录/查重一致
--       （username = $1 AND deleted_at IS NULL），并发注册也被该索引兜住。

ALTER TABLE users DROP CONSTRAINT IF EXISTS uq_users_username;

CREATE UNIQUE INDEX IF NOT EXISTS uq_users_username
    ON users (username)
    WHERE deleted_at IS NULL;

COMMENT ON INDEX uq_users_username IS '用户名唯一（仅未删除行）：软删除后允许重新注册同名账号';
