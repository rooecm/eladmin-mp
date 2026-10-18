-- v7 auth: stable role code used by the super-admin check and the AdminPrivilegeGuard (plan 8.6/14.1).
-- A role code is a stable, configurable identifier; the display name stays free-form.
ALTER TABLE sys_role
    ADD COLUMN role_code VARCHAR(64) DEFAULT NULL COMMENT '角色码' AFTER name,
  ADD UNIQUE KEY uniq_role_code (role_code);

-- The seeded administrator role is the super administrator; its code must match
-- auth.super-admin-role-code (default SUPER_ADMIN).
UPDATE sys_role
SET role_code = 'SUPER_ADMIN'
WHERE id = 1;
UPDATE sys_role
SET role_code = 'USER'
WHERE id = 2;
