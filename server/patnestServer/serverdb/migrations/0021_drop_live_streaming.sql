-- 0021 下线商家直播：清掉 0020 建的表、字段与权限项。
-- 0020 保留在迁移历史里，保证已经执行过它的数据库记录不会错位；本文件负责真正回收。
-- 注意：此迁移会删除直播间的历史记录与弹幕数据，执行前请确认无需留存。

DROP TABLE IF EXISTS live_messages;
DROP TABLE IF EXISTS live_viewers;
DROP TABLE IF EXISTS live_rooms;

ALTER TABLE sitters DROP COLUMN IF EXISTS live_banned_at;
ALTER TABLE sitters DROP COLUMN IF EXISTS live_ban_reason;

DELETE FROM role_permissions WHERE permission = 'lives.manage';
