-- V2 and V3 are already published; extend them without rewriting their checksums.
ALTER TABLE sys_role
    ADD COLUMN enabled BIT(1) NOT NULL DEFAULT b'1' COMMENT '角色启用状态';
ALTER TABLE sys_menu
    ADD COLUMN enabled BIT(1) NOT NULL DEFAULT b'1' COMMENT '权限启用状态';

-- Disable only historical demo credentials. Customized accounts are preserved.
-- The optional one-time initializer provisions admin from AUTH_INITIAL_PASSWORD.
UPDATE sys_user
SET enabled              = b'0',
    must_change_password = b'1',
    auth_version         = auth_version + 1
WHERE (username = 'admin' AND password = '$2a$10$Egp1/gvFlt7zhlXVfEFw4OfWQCGPw0ClmMcc6FjTnvXNRVf9zdMRa')
   OR (username = 'test' AND password = '$2a$10$BSR9oUNtzWhnqs8NmZk5Zu3zfsNop3KxZO0xGEzy01cumf9k/AW6.');

-- The legacy online-session list is intentionally retired by v7.
UPDATE sys_menu
SET enabled = b'0',
    hidden  = b'1'
WHERE component = 'monitor/online/index';

-- Preserve the project's existing permission codes. Add codes for formerly admin-only log endpoints.
INSERT INTO sys_menu (pid, type, title, menu_sort, i_frame, cache, hidden, permission, create_time, update_time)
SELECT NULL,
       2,
       '系统日志查询',
       999,
       b'0',
       b'0',
       b'1',
       'logs:list',
       NOW(),
       NOW() WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE permission = 'logs:list');
INSERT INTO sys_menu (pid, type, title, menu_sort, i_frame, cache, hidden, permission, create_time, update_time)
SELECT NULL,
       2,
       '系统日志删除',
       999,
       b'0',
       b'0',
       b'1',
       'logs:del',
       NOW(),
       NOW() WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE permission = 'logs:del');

INSERT INTO sys_menu (pid, type, title, menu_sort, i_frame, cache, hidden, permission, create_time, update_time)
SELECT NULL,
       2,
       'email:list',
       999,
       b'0',
       b'0',
       b'1',
       'email:list',
       NOW(),
       NOW() WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE permission = 'email:list');

INSERT INTO sys_menu (pid, type, title, menu_sort, i_frame, cache, hidden, permission, create_time, update_time)
SELECT NULL,
       2,
       'email:config',
       999,
       b'0',
       b'0',
       b'1',
       'email:config',
       NOW(),
       NOW() WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE permission = 'email:config');

INSERT INTO sys_menu (pid, type, title, menu_sort, i_frame, cache, hidden, permission, create_time, update_time)
SELECT NULL,
       2,
       'email:send',
       999,
       b'0',
       b'0',
       b'1',
       'email:send',
       NOW(),
       NOW() WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE permission = 'email:send');

INSERT INTO sys_menu (pid, type, title, menu_sort, i_frame, cache, hidden, permission, create_time, update_time)
SELECT NULL,
       2,
       'storage:add',
       999,
       b'0',
       b'0',
       b'1',
       'storage:add',
       NOW(),
       NOW() WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE permission = 'storage:add');

INSERT INTO sys_menu (pid, type, title, menu_sort, i_frame, cache, hidden, permission, create_time, update_time)
SELECT NULL,
       2,
       'storage:edit',
       999,
       b'0',
       b'0',
       b'1',
       'storage:edit',
       NOW(),
       NOW() WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE permission = 'storage:edit');
