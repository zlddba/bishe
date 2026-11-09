-- 0007 机构入驻资质审核：申请时上传营业执照 / 身份证等证明材料，
-- 管理员审核通过（active）后账号才升级为 sitter 角色；驳回（rejected）可修改材料后重新申请。

ALTER TYPE sitter_status ADD VALUE IF NOT EXISTS 'rejected';

ALTER TABLE sitters ADD COLUMN IF NOT EXISTS contact_person    VARCHAR(64);
ALTER TABLE sitters ADD COLUMN IF NOT EXISTS license_photo_url TEXT;
ALTER TABLE sitters ADD COLUMN IF NOT EXISTS id_card_photo_url TEXT;
ALTER TABLE sitters ADD COLUMN IF NOT EXISTS review_note       VARCHAR(255);

COMMENT ON COLUMN sitters.contact_person IS '入驻申请联系人姓名';
COMMENT ON COLUMN sitters.license_photo_url IS '营业执照照片 URL（入驻资质材料）';
COMMENT ON COLUMN sitters.id_card_photo_url IS '联系人身份证照片 URL（入驻资质材料）';
COMMENT ON COLUMN sitters.review_note IS '入驻审核驳回原因';
