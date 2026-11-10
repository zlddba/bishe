-- 0008 文件链接改为相对路径
--
-- 背景：2026-11-10 之前上传接口返回的是完整地址（对象存储直链或统一域名），
-- 客户端原样保存进库，于是数据库里写死了域名和端口：
--   http://zdz1.linkzdsada.dpdns.org:9000/patnest-uploads/uploads/2026/11/xxx.jpg
-- 结果是换域名、上 HTTPS、切 CDN 都要回头改数据，而且历史数据里 http/https 混杂。
--
-- 现在统一存 /uploads/2026/11/xxx.jpg，绝对地址在出站时按 PUBLIC_FILE_BASE 拼接
-- （见 internal/middleware/fileurl.go），域名不再进入数据库。
--
-- 本迁移只做存量数据的一次性转换，可重复执行：相对路径与其他值原样返回。

CREATE OR REPLACE FUNCTION fn_rel_file(p TEXT) RETURNS TEXT
LANGUAGE sql IMMUTABLE AS $$
    SELECT CASE
        WHEN p IS NULL OR p = '' THEN p
        WHEN p ~ '^https?://[^/]+/(files|patnest-uploads)/'
            THEN '/' || regexp_replace(p, '^https?://[^/]+/(files|patnest-uploads)/', '')
        ELSE p
    END
$$;

COMMENT ON FUNCTION fn_rel_file(TEXT) IS
    '把历史绝对文件地址还原成相对路径（0008 引入）；相对路径与其他值原样返回';

-- 单个字段
UPDATE users
   SET avatar_url = fn_rel_file(avatar_url)
 WHERE avatar_url ~ '^https?://';

UPDATE pets
   SET avatar_url = fn_rel_file(avatar_url)
 WHERE avatar_url ~ '^https?://';

UPDATE sitters
   SET cover_photo_url = fn_rel_file(cover_photo_url)
 WHERE cover_photo_url ~ '^https?://';

UPDATE sitters
   SET license_photo_url = fn_rel_file(license_photo_url)
 WHERE license_photo_url ~ '^https?://';

UPDATE sitters
   SET id_card_photo_url = fn_rel_file(id_card_photo_url)
 WHERE id_card_photo_url ~ '^https?://';

-- 数组字段：只处理确实含绝对地址的行，避免把 NULL 变成空数组
UPDATE pets
   SET photo_urls = ARRAY(SELECT fn_rel_file(x) FROM unnest(photo_urls) x)
 WHERE EXISTS (SELECT 1 FROM unnest(photo_urls) x WHERE x ~ '^https?://');

UPDATE pets
   SET vaccine_urls = ARRAY(SELECT fn_rel_file(x) FROM unnest(vaccine_urls) x)
 WHERE EXISTS (SELECT 1 FROM unnest(vaccine_urls) x WHERE x ~ '^https?://');

UPDATE sitters
   SET detail_photos = ARRAY(SELECT fn_rel_file(x) FROM unnest(detail_photos) x)
 WHERE EXISTS (SELECT 1 FROM unnest(detail_photos) x WHERE x ~ '^https?://');

UPDATE dynamics
   SET photos = ARRAY(SELECT fn_rel_file(x) FROM unnest(photos) x)
 WHERE EXISTS (SELECT 1 FROM unnest(photos) x WHERE x ~ '^https?://');

UPDATE reviews
   SET owner_photos = ARRAY(SELECT fn_rel_file(x) FROM unnest(owner_photos) x)
 WHERE EXISTS (SELECT 1 FROM unnest(owner_photos) x WHERE x ~ '^https?://');
