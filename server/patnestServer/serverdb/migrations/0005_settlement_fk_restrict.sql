-- 0005 结算凭证不随机构删除：sitter_settlements.sitter_id 由 CASCADE 改 RESTRICT
--
-- 结算单是财务凭证。原来外键是 ON DELETE CASCADE，机构行被物理删除时，
-- 它的全部结算记录会跟着一起消失（包括已打款的转账凭证），事后无法对账。
--
-- 改成 RESTRICT 之后：只要还有结算记录，机构就不能被物理删除，
-- 注销流程会退化为软删除（account_cancel.go 里 purgeUserAccount 失败 →
-- 降级软删的用户进后台「已注销」列表），记录保留，审计链完整。
--
-- 注：软删除（deleted_at）不受影响，仍可正常注销机构。

ALTER TABLE sitter_settlements
    DROP CONSTRAINT IF EXISTS sitter_settlements_sitter_id_fkey;

ALTER TABLE sitter_settlements
    ADD CONSTRAINT sitter_settlements_sitter_id_fkey
    FOREIGN KEY (sitter_id) REFERENCES sitters(id) ON DELETE RESTRICT;

COMMENT ON CONSTRAINT sitter_settlements_sitter_id_fkey ON sitter_settlements
    IS '结算单为财务凭证，机构存在结算记录时禁止物理删除（0005）';
