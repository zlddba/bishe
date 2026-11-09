-- migrate:no-transaction
-- 0008 机构类型新增"其他"（other）：覆盖民宿、宠物酒店等未归类形态。
ALTER TYPE sitter_type ADD VALUE IF NOT EXISTS 'other';

COMMENT ON TYPE sitter_type IS '机构类型：home=家庭寄养 shop=门店寄养 visit=上门喂养 other=其他';
