-- Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
-- 非商业学习版；发布检索与菜单权限约束。
CREATE INDEX ix_revision_effective ON document_revision(status,effective_date,review_date);
ALTER TABLE nav_menu ADD CONSTRAINT fk_menu_permission FOREIGN KEY(permission_code) REFERENCES permission(code);
