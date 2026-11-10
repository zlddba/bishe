-- 0002 机构角色回填
--
-- 背景：机构资质审核通过时才会把账号从 owner 升成 sitter。但下面几种情况会让
-- users.role 一直停在 owner，而机构记录其实已经存在：
--   1. 后台"机构管理"里直接新建机构（AdminCreateSitter 之前不同步角色）；
--   2. 更早版本的数据、或审核时角色同步语句失败；
--   3. 手工改库导入的机构。
-- 结果就是账号登录后进机构端，/sitter/* 全被 RequireRole("sitter") 拦成 403，
-- 表现为"机构修改机构信息提示权限不足"。
--
-- 服务端已经从"只看令牌里的角色"改成"以数据库角色 + 名下是否有在营机构为准"，
-- 这里把历史数据的角色补齐，让 /auth/me 回显的角色也和真实能力一致。
UPDATE users u
SET role = 'sitter', updated_at = now()
WHERE u.role = 'owner'
  AND u.deleted_at IS NULL
  AND EXISTS (
      SELECT 1
      FROM sitters s
      WHERE s.user_id = u.id
        AND s.deleted_at IS NULL
        AND s.status IN ('active', 'inactive')
  );
