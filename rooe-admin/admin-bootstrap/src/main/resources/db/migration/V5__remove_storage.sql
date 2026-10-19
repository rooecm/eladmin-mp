-- 移除存储管理功能：清理存储菜单、角色菜单关联与存储数据表。
-- V1 已发布，不再修改，因此这里同时覆盖空库（V1 建表后立即清理）与存量库。
-- 菜单 permission 以 storage: 开头的按钮权限，以及存储管理父菜单（id = 18）一并删除。
DELETE
FROM sys_role_menu
WHERE menu_id IN (SELECT id FROM sys_menu WHERE permission LIKE 'storage:%' OR id = 18);

DELETE
FROM sys_menu
WHERE permission LIKE 'storage:%'
   OR id = 18;

-- 存储管理移除后同步“系统工具”父菜单的子菜单数量。
UPDATE sys_menu
SET sub_count = (SELECT COUNT(*) FROM (SELECT id FROM sys_menu WHERE pid = 36) AS children)
WHERE id = 36;

DROP TABLE IF EXISTS tool_local_storage;
DROP TABLE IF EXISTS tool_s3_storage;
