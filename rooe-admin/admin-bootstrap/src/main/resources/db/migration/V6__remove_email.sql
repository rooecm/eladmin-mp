-- 移除邮件功能：清理邮件工具菜单、邮件权限、角色菜单关联与邮件配置表。
-- V1/V4 已发布，不再修改，因此这里同时覆盖空库（V1/V4 建表与种子后立即清理）与存量库。
-- 菜单 permission 以 email: 开头的按钮权限，以及邮件工具菜单（id = 14）一并删除。
DELETE
FROM sys_role_menu
WHERE menu_id IN (SELECT id FROM sys_menu WHERE permission LIKE 'email:%' OR id = 14);

DELETE
FROM sys_menu
WHERE permission LIKE 'email:%'
   OR id = 14;

-- 邮件工具移除后同步“系统工具”父菜单的子菜单数量。
UPDATE sys_menu
SET sub_count = (SELECT COUNT(*) FROM (SELECT id FROM sys_menu WHERE pid = 36) AS children)
WHERE id = 36;

DROP TABLE IF EXISTS tool_email_config;
