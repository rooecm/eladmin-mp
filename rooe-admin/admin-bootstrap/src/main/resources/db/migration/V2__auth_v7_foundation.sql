-- v7 auth foundation
ALTER TABLE sys_user
    ADD COLUMN auth_version BIGINT NOT NULL DEFAULT 0 COMMENT '认证版本' AFTER enabled,
  ADD COLUMN must_change_password BIT(1) NOT NULL DEFAULT b'0' COMMENT '强制修改密码' AFTER auth_version,
  ADD COLUMN last_login_at DATETIME DEFAULT NULL COMMENT '最近一次登录时间' AFTER must_change_password,
  ADD COLUMN last_login_ip VARCHAR(64) DEFAULT NULL COMMENT '最近一次登录 IP' AFTER last_login_at;
