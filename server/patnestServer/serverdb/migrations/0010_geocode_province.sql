-- 0010 逆地理缓存补省级名称
--
-- 「选择地区」页要按省/市/区三级联动，默认选中项需要知道当前所在的省。
-- 逆地理响应里高德本来就有 province，但缓存表没有这一列，命中缓存时会丢；
-- 补上以保持「直连」与「缓存」返回的字段一致。

ALTER TABLE geocode_cache
    ADD COLUMN IF NOT EXISTS province VARCHAR(64) NOT NULL DEFAULT '';

COMMENT ON COLUMN geocode_cache.province IS
    '省/自治区/直辖市名称，高德逆地理 addressComponent.province（0010）';

-- 旧缓存的 province 为空，而逆地理 TTL 是 30 天；清空让它们重新解析。
-- geocode_cache 纯缓存，重建代价只是几十次高德调用。
DELETE FROM geocode_cache;
