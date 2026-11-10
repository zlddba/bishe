-- 0009 逆地理缓存补街道/乡镇
--
-- 首页导航栏原来只显示到「市」，要做到区县/镇一级就得把高德返回的 township 也存下来，
-- 否则每次冷缓存都把这一级丢掉（缓存命中时和直连时返回的字段必须一致）。
--
-- 高德逆地理在 extensions=all（本项目的请求参数）下会返回 addressComponent.township，
-- 直辖市、部分开发区为空，此时前端用 district 兜底。

ALTER TABLE geocode_cache
    ADD COLUMN IF NOT EXISTS township VARCHAR(64) NOT NULL DEFAULT '';

COMMENT ON COLUMN geocode_cache.township IS
    '街道/乡镇，高德逆地理 extensions=all 返回；可能为空，调用方用 district 兜底（0009）';

-- 旧缓存里的逆地理结果没有 township，而逆地理缓存 TTL 是 30 天。
-- 不清掉的话，升级后最长一个月内首页仍然只显示到市。
-- geocode_cache 纯缓存，清空代价只是几十次高德调用。
DELETE FROM geocode_cache;
