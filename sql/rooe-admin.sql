/*
 Navicat Premium Dump SQL

 Source Server         : localhost
 Source Server Type    : MariaDB
 Source Server Version : 110206 (11.2.6-MariaDB)
 Source Host           : localhost:3306
 Source Schema         : eladmin-mp

 Target Server Type    : MariaDB
 Target Server Version : 110206 (11.2.6-MariaDB)
 File Encoding         : 65001

 Date: 19/06/2025 16:56:01
*/

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;
-- ----------------------------
-- Table structure for sys_dept
-- ----------------------------
DROP TABLE IF EXISTS `sys_dept`;
CREATE TABLE `sys_dept` (
                            `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT 'ID',
                            `pid` bigint(20) DEFAULT NULL COMMENT '上级部门',
                            `sub_count` int(5) DEFAULT 0 COMMENT '子部门数目',
                            `name` varchar(255) NOT NULL COMMENT '名称',
                            `dept_sort` int(5) DEFAULT 999 COMMENT '排序',
                            `enabled` bit(1) NOT NULL COMMENT '状态',
                            `create_by` varchar(255) DEFAULT NULL COMMENT '创建者',
                            `update_by` varchar(255) DEFAULT NULL COMMENT '更新者',
                            `create_time` datetime DEFAULT NULL COMMENT '创建日期',
                            `update_time` datetime DEFAULT NULL COMMENT '更新时间',
                            PRIMARY KEY (`id`) USING BTREE,
                            KEY `idx_pid` (`pid`),
                            KEY `idx_enabled` (`enabled`)
) ENGINE=InnoDB AUTO_INCREMENT=18 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci ROW_FORMAT=COMPACT COMMENT='部门';

-- ----------------------------
-- Records of sys_dept
-- ----------------------------
BEGIN;
INSERT INTO `sys_dept` (`id`, `pid`, `sub_count`, `name`, `dept_sort`, `enabled`, `create_by`, `update_by`, `create_time`, `update_time`) VALUES (2, 7, 1, '研发部', 3, b'1', 'admin', 'admin', '2019-03-25 09:15:32', '2020-08-02 14:48:47');
INSERT INTO `sys_dept` (`id`, `pid`, `sub_count`, `name`, `dept_sort`, `enabled`, `create_by`, `update_by`, `create_time`, `update_time`) VALUES (5, 7, 0, '运维部', 4, b'1', 'admin', 'admin', '2019-03-25 09:20:44', '2020-05-17 14:27:27');
INSERT INTO `sys_dept` (`id`, `pid`, `sub_count`, `name`, `dept_sort`, `enabled`, `create_by`, `update_by`, `create_time`, `update_time`) VALUES (6, 8, 0, '测试部', 6, b'1', 'admin', 'admin', '2019-03-25 09:52:18', '2020-06-08 11:59:21');
INSERT INTO `sys_dept` (`id`, `pid`, `sub_count`, `name`, `dept_sort`, `enabled`, `create_by`, `update_by`, `create_time`, `update_time`) VALUES (7, NULL, 2, '华南分部', 0, b'1', 'admin', 'admin', '2019-03-25 11:04:50', '2020-06-08 12:08:56');
INSERT INTO `sys_dept` (`id`, `pid`, `sub_count`, `name`, `dept_sort`, `enabled`, `create_by`, `update_by`, `create_time`, `update_time`) VALUES (8, NULL, 2, '华北分部', 1, b'1', 'admin', 'admin', '2019-03-25 11:04:53', '2020-05-14 12:54:00');
INSERT INTO `sys_dept` (`id`, `pid`, `sub_count`, `name`, `dept_sort`, `enabled`, `create_by`, `update_by`, `create_time`, `update_time`) VALUES (15, 8, 0, 'UI部门', 7, b'1', 'admin', 'admin', '2020-05-13 22:56:53', '2020-05-14 12:54:13');
INSERT INTO `sys_dept` (`id`, `pid`, `sub_count`, `name`, `dept_sort`, `enabled`, `create_by`, `update_by`, `create_time`, `update_time`) VALUES (17, 2, 0, '研发一组', 999, b'1', 'admin', 'admin', '2020-08-02 14:49:07', '2020-08-02 14:49:07');
COMMIT;

-- ----------------------------
-- Table structure for sys_dict
-- ----------------------------
DROP TABLE IF EXISTS `sys_dict`;
CREATE TABLE `sys_dict` (
                            `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT 'ID',
                            `name` varchar(255) NOT NULL COMMENT '字典名称',
                            `description` varchar(255) DEFAULT NULL COMMENT '描述',
                            `create_by` varchar(255) DEFAULT NULL COMMENT '创建者',
                            `update_by` varchar(255) DEFAULT NULL COMMENT '更新者',
                            `create_time` datetime DEFAULT NULL COMMENT '创建日期',
                            `update_time` datetime DEFAULT NULL COMMENT '更新时间',
                            PRIMARY KEY (`id`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=7 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci ROW_FORMAT=COMPACT COMMENT='数据字典';

-- ----------------------------
-- Records of sys_dict
-- ----------------------------
BEGIN;
INSERT INTO `sys_dict` (`id`, `name`, `description`, `create_by`, `update_by`, `create_time`, `update_time`) VALUES (1, 'user_status', '用户状态', NULL, NULL, '2019-10-27 20:31:36', NULL);
INSERT INTO `sys_dict` (`id`, `name`, `description`, `create_by`, `update_by`, `create_time`, `update_time`) VALUES (4, 'dept_status', '部门状态', NULL, NULL, '2019-10-27 20:31:36', NULL);
INSERT INTO `sys_dict` (`id`, `name`, `description`, `create_by`, `update_by`, `create_time`, `update_time`) VALUES (5, 'job_status', '岗位状态', NULL, 'admin', '2019-10-27 20:31:36', '2025-01-14 15:48:29');
COMMIT;

-- ----------------------------
-- Table structure for sys_dict_detail
-- ----------------------------
DROP TABLE IF EXISTS `sys_dict_detail`;
CREATE TABLE `sys_dict_detail` (
                                   `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT 'ID',
                                   `dict_id` bigint(11) DEFAULT NULL COMMENT '字典id',
                                   `label` varchar(255) NOT NULL COMMENT '字典标签',
                                   `value` varchar(255) NOT NULL COMMENT '字典值',
                                   `dict_sort` int(5) DEFAULT NULL COMMENT '排序',
                                   `create_by` varchar(255) DEFAULT NULL COMMENT '创建者',
                                   `update_by` varchar(255) DEFAULT NULL COMMENT '更新者',
                                   `create_time` datetime DEFAULT NULL COMMENT '创建日期',
                                   `update_time` datetime DEFAULT NULL COMMENT '更新时间',
                                   PRIMARY KEY (`id`) USING BTREE,
                                   KEY `idx_dict_id` (`dict_id`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=7 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci ROW_FORMAT=COMPACT COMMENT='数据字典详情';

-- ----------------------------
-- Records of sys_dict_detail
-- ----------------------------
BEGIN;
INSERT INTO `sys_dict_detail` (`id`, `dict_id`, `label`, `value`, `dict_sort`, `create_by`, `update_by`, `create_time`, `update_time`) VALUES (1, 1, '激活', 'true', 1, NULL, NULL, '2019-10-27 20:31:36', NULL);
INSERT INTO `sys_dict_detail` (`id`, `dict_id`, `label`, `value`, `dict_sort`, `create_by`, `update_by`, `create_time`, `update_time`) VALUES (2, 1, '禁用', 'false', 2, NULL, NULL, NULL, NULL);
INSERT INTO `sys_dict_detail` (`id`, `dict_id`, `label`, `value`, `dict_sort`, `create_by`, `update_by`, `create_time`, `update_time`) VALUES (3, 4, '启用', 'true', 1, NULL, NULL, NULL, NULL);
INSERT INTO `sys_dict_detail` (`id`, `dict_id`, `label`, `value`, `dict_sort`, `create_by`, `update_by`, `create_time`, `update_time`) VALUES (4, 4, '停用', 'false', 2, NULL, NULL, '2019-10-27 20:31:36', NULL);
INSERT INTO `sys_dict_detail` (`id`, `dict_id`, `label`, `value`, `dict_sort`, `create_by`, `update_by`, `create_time`, `update_time`) VALUES (5, 5, '启用', 'true', 1, NULL, NULL, NULL, NULL);
INSERT INTO `sys_dict_detail` (`id`, `dict_id`, `label`, `value`, `dict_sort`, `create_by`, `update_by`, `create_time`, `update_time`) VALUES (6, 5, '停用', 'false', 2, NULL, NULL, '2019-10-27 20:31:36', NULL);
COMMIT;

-- ----------------------------
-- Table structure for sys_job
-- ----------------------------
DROP TABLE IF EXISTS `sys_job`;
CREATE TABLE `sys_job` (
                           `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT 'ID',
                           `name` varchar(180) NOT NULL COMMENT '岗位名称',
                           `enabled` bit(1) NOT NULL COMMENT '岗位状态',
                           `job_sort` int(5) DEFAULT NULL COMMENT '排序',
                           `create_by` varchar(255) DEFAULT NULL COMMENT '创建者',
                           `update_by` varchar(255) DEFAULT NULL COMMENT '更新者',
                           `create_time` datetime DEFAULT NULL COMMENT '创建日期',
                           `update_time` datetime DEFAULT NULL COMMENT '更新时间',
                           PRIMARY KEY (`id`) USING BTREE,
                           UNIQUE KEY `uniq_name` (`name`),
                           KEY `idx_enabled` (`enabled`)
) ENGINE=InnoDB AUTO_INCREMENT=17 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci ROW_FORMAT=COMPACT COMMENT='岗位';

-- ----------------------------
-- Records of sys_job
-- ----------------------------
BEGIN;
INSERT INTO `sys_job` (`id`, `name`, `enabled`, `job_sort`, `create_by`, `update_by`, `create_time`, `update_time`) VALUES (8, '人事专员', b'1', 3, NULL, NULL, '2019-03-29 14:52:28', NULL);
INSERT INTO `sys_job` (`id`, `name`, `enabled`, `job_sort`, `create_by`, `update_by`, `create_time`, `update_time`) VALUES (10, '产品经理', b'1', 4, NULL, NULL, '2019-03-29 14:55:51', NULL);
INSERT INTO `sys_job` (`id`, `name`, `enabled`, `job_sort`, `create_by`, `update_by`, `create_time`, `update_time`) VALUES (11, '全栈开发', b'1', 2, NULL, 'admin', '2019-03-31 13:39:30', '2020-05-05 11:33:43');
INSERT INTO `sys_job` (`id`, `name`, `enabled`, `job_sort`, `create_by`, `update_by`, `create_time`, `update_time`) VALUES (12, '软件测试', b'1', 5, NULL, 'admin', '2019-03-31 13:39:43', '2020-05-10 19:56:26');
COMMIT;

-- ----------------------------
-- Table structure for sys_log
-- ----------------------------
DROP TABLE IF EXISTS `sys_log`;
CREATE TABLE `sys_log` (
                           `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT 'ID',
                           `description` varchar(255) DEFAULT NULL COMMENT '描述',
                           `log_type` varchar(10) NOT NULL COMMENT '日志类型：INFI/ERROR',
                           `method` varchar(255) DEFAULT NULL COMMENT '方法名',
                           `params` text DEFAULT NULL COMMENT '参数',
                           `request_ip` varchar(255) DEFAULT NULL COMMENT '请求IP',
                           `time` bigint(20) DEFAULT NULL COMMENT '执行时间',
                           `username` varchar(255) DEFAULT NULL COMMENT '用户名',
                           `address` varchar(255) DEFAULT NULL COMMENT '地址',
                           `browser` varchar(255) DEFAULT NULL COMMENT '浏览器',
                           `exception_detail` text DEFAULT NULL COMMENT '异常',
                           `create_time` datetime NOT NULL COMMENT '创建时间',
                           PRIMARY KEY (`id`) USING BTREE,
                           KEY `idx_create_time_index` (`create_time`),
                           KEY `idx_log_type` (`log_type`)
) ENGINE=InnoDB AUTO_INCREMENT=13754 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci ROW_FORMAT=COMPACT COMMENT='系统日志';

-- ----------------------------
-- Records of sys_log
-- ----------------------------
BEGIN;
COMMIT;

-- ----------------------------
-- Table structure for sys_menu
-- ----------------------------
DROP TABLE IF EXISTS `sys_menu`;
CREATE TABLE `sys_menu` (
                            `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT 'ID',
                            `pid` bigint(20) DEFAULT NULL COMMENT '上级菜单ID',
                            `sub_count` int(5) DEFAULT 0 COMMENT '子菜单数目',
                            `type` int(11) DEFAULT NULL COMMENT '菜单类型',
                            `title` varchar(100) DEFAULT NULL COMMENT '菜单标题',
                            `name` varchar(100) DEFAULT NULL COMMENT '组件名称',
                            `component` varchar(255) DEFAULT NULL COMMENT '组件',
                            `menu_sort` int(5) DEFAULT NULL COMMENT '排序',
                            `icon` varchar(255) DEFAULT NULL COMMENT '图标',
                            `path` varchar(255) DEFAULT NULL COMMENT '链接地址',
                            `i_frame` bit(1) DEFAULT NULL COMMENT '是否外链',
                            `cache` bit(1) DEFAULT b'0' COMMENT '缓存',
                            `hidden` bit(1) DEFAULT b'0' COMMENT '隐藏',
                            `permission` varchar(255) DEFAULT NULL COMMENT '权限',
                            `create_by` varchar(255) DEFAULT NULL COMMENT '创建者',
                            `update_by` varchar(255) DEFAULT NULL COMMENT '更新者',
                            `create_time` datetime DEFAULT NULL COMMENT '创建日期',
                            `update_time` datetime DEFAULT NULL COMMENT '更新时间',
                            PRIMARY KEY (`id`) USING BTREE,
                            UNIQUE KEY `uniq_name` (`name`),
                            UNIQUE KEY `uniq_title` (`title`),
                            KEY `idx_pid` (`pid`)
) ENGINE=InnoDB AUTO_INCREMENT=117 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci ROW_FORMAT=COMPACT COMMENT='系统菜单';

-- ----------------------------
-- Records of sys_menu
-- ----------------------------
BEGIN;
INSERT INTO `sys_menu` (`id`, `pid`, `sub_count`, `type`, `title`, `name`, `component`, `menu_sort`, `icon`, `path`,
                        `i_frame`, `cache`, `hidden`, `permission`, `create_by`, `update_by`, `create_time`,
                        `update_time`)
VALUES (1, NULL, 6, 0, '系统管理', NULL, NULL, 1, 'system', 'system', b'0', b'0', b'0', NULL, NULL, 'admin',
        '2018-12-18 15:11:29', '2025-01-14 15:48:18');
INSERT INTO `sys_menu` (`id`, `pid`, `sub_count`, `type`, `title`, `name`, `component`, `menu_sort`, `icon`, `path`, `i_frame`, `cache`, `hidden`, `permission`, `create_by`, `update_by`, `create_time`, `update_time`) VALUES (2, 1, 3, 1, '用户管理', 'User', 'system/user/index', 2, 'peoples', 'user', b'0', b'0', b'0', 'user:list', NULL, NULL, '2018-12-18 15:14:44', NULL);
INSERT INTO `sys_menu` (`id`, `pid`, `sub_count`, `type`, `title`, `name`, `component`, `menu_sort`, `icon`, `path`, `i_frame`, `cache`, `hidden`, `permission`, `create_by`, `update_by`, `create_time`, `update_time`) VALUES (3, 1, 3, 1, '角色管理', 'Role', 'system/role/index', 3, 'role', 'role', b'0', b'0', b'0', 'roles:list', NULL, NULL, '2018-12-18 15:16:07', NULL);
INSERT INTO `sys_menu` (`id`, `pid`, `sub_count`, `type`, `title`, `name`, `component`, `menu_sort`, `icon`, `path`, `i_frame`, `cache`, `hidden`, `permission`, `create_by`, `update_by`, `create_time`, `update_time`) VALUES (5, 1, 3, 1, '菜单管理', 'Menu', 'system/menu/index', 5, 'menu', 'menu', b'0', b'0', b'0', 'menu:list', NULL, NULL, '2018-12-18 15:17:28', NULL);
INSERT INTO `sys_menu` (`id`, `pid`, `sub_count`, `type`, `title`, `name`, `component`, `menu_sort`, `icon`, `path`, `i_frame`, `cache`, `hidden`, `permission`, `create_by`, `update_by`, `create_time`, `update_time`) VALUES (6, NULL, 4, 0, '系统监控', NULL, NULL, 10, 'monitor', 'monitor', b'0', b'0', b'0', NULL, NULL, NULL, '2018-12-18 15:17:48', NULL);
INSERT INTO `sys_menu` (`id`, `pid`, `sub_count`, `type`, `title`, `name`, `component`, `menu_sort`, `icon`, `path`, `i_frame`, `cache`, `hidden`, `permission`, `create_by`, `update_by`, `create_time`, `update_time`) VALUES (7, 6, 0, 1, '操作日志', 'Log', 'monitor/log/index', 11, 'log', 'logs', b'0', b'1', b'0', NULL, NULL, 'admin', '2018-12-18 15:18:26', '2020-06-06 13:11:57');
INSERT INTO `sys_menu` (`id`, `pid`, `sub_count`, `type`, `title`, `name`, `component`, `menu_sort`, `icon`, `path`, `i_frame`, `cache`, `hidden`, `permission`, `create_by`, `update_by`, `create_time`, `update_time`) VALUES (9, 6, 0, 1, 'SQL监控', 'Sql', 'monitor/sql/index', 18, 'sqlMonitor', 'druid', b'0', b'0', b'0', NULL, NULL, NULL, '2018-12-18 15:19:34', NULL);
INSERT INTO `sys_menu` (`id`, `pid`, `sub_count`, `type`, `title`, `name`, `component`, `menu_sort`, `icon`, `path`, `i_frame`, `cache`, `hidden`, `permission`, `create_by`, `update_by`, `create_time`, `update_time`) VALUES (32, 6, 0, 1, '异常日志', 'ErrorLog', 'monitor/log/errorLog', 12, 'error', 'errorLog', b'0', b'0', b'0', NULL, NULL, NULL, '2019-01-13 13:49:03', NULL);
INSERT INTO `sys_menu` (`id`, `pid`, `sub_count`, `type`, `title`, `name`, `component`, `menu_sort`, `icon`, `path`, `i_frame`, `cache`, `hidden`, `permission`, `create_by`, `update_by`, `create_time`, `update_time`) VALUES (35, 1, 3, 1, '部门管理', 'Dept', 'system/dept/index', 6, 'dept', 'dept', b'0', b'0', b'0', 'dept:list', NULL, NULL, '2019-03-25 09:46:00', NULL);
INSERT INTO `sys_menu` (`id`, `pid`, `sub_count`, `type`, `title`, `name`, `component`, `menu_sort`, `icon`, `path`,
                        `i_frame`, `cache`, `hidden`, `permission`, `create_by`, `update_by`, `create_time`,
                        `update_time`)
VALUES (36, NULL, 0, 0, '系统工具', NULL, '', 30, 'sys-tools', 'sys-tools', b'0', b'0', b'0', NULL, NULL, NULL,
        '2019-03-29 10:57:35', NULL);
INSERT INTO `sys_menu` (`id`, `pid`, `sub_count`, `type`, `title`, `name`, `component`, `menu_sort`, `icon`, `path`, `i_frame`, `cache`, `hidden`, `permission`, `create_by`, `update_by`, `create_time`, `update_time`) VALUES (37, 1, 3, 1, '岗位管理', 'Job', 'system/job/index', 7, 'Steve-Jobs', 'job', b'0', b'0', b'0', 'job:list', NULL, NULL, '2019-03-29 13:51:18', NULL);
INSERT INTO `sys_menu` (`id`, `pid`, `sub_count`, `type`, `title`, `name`, `component`, `menu_sort`, `icon`, `path`, `i_frame`, `cache`, `hidden`, `permission`, `create_by`, `update_by`, `create_time`, `update_time`) VALUES (39, 1, 3, 1, '字典管理', 'Dict', 'system/dict/index', 8, 'dictionary', 'dict', b'0', b'0', b'0', 'dict:list', NULL, NULL, '2019-04-10 11:49:04', NULL);
INSERT INTO `sys_menu` (`id`, `pid`, `sub_count`, `type`, `title`, `name`, `component`, `menu_sort`, `icon`, `path`, `i_frame`, `cache`, `hidden`, `permission`, `create_by`, `update_by`, `create_time`, `update_time`) VALUES (41, 6, 0, 1, '在线用户', 'OnlineUser', 'monitor/online/index', 10, 'Steve-Jobs', 'online', b'0', b'0', b'0', NULL, NULL, NULL, '2019-10-26 22:08:43', NULL);
INSERT INTO `sys_menu` (`id`, `pid`, `sub_count`, `type`, `title`, `name`, `component`, `menu_sort`, `icon`, `path`, `i_frame`, `cache`, `hidden`, `permission`, `create_by`, `update_by`, `create_time`, `update_time`) VALUES (44, 2, 0, 2, '用户新增', NULL, '', 2, '', '', b'0', b'0', b'0', 'user:add', NULL, NULL, '2019-10-29 10:59:46', NULL);
INSERT INTO `sys_menu` (`id`, `pid`, `sub_count`, `type`, `title`, `name`, `component`, `menu_sort`, `icon`, `path`, `i_frame`, `cache`, `hidden`, `permission`, `create_by`, `update_by`, `create_time`, `update_time`) VALUES (45, 2, 0, 2, '用户编辑', NULL, '', 3, '', '', b'0', b'0', b'0', 'user:edit', NULL, NULL, '2019-10-29 11:00:08', NULL);
INSERT INTO `sys_menu` (`id`, `pid`, `sub_count`, `type`, `title`, `name`, `component`, `menu_sort`, `icon`, `path`, `i_frame`, `cache`, `hidden`, `permission`, `create_by`, `update_by`, `create_time`, `update_time`) VALUES (46, 2, 0, 2, '用户删除', NULL, '', 4, '', '', b'0', b'0', b'0', 'user:del', NULL, NULL, '2019-10-29 11:00:23', NULL);
INSERT INTO `sys_menu` (`id`, `pid`, `sub_count`, `type`, `title`, `name`, `component`, `menu_sort`, `icon`, `path`, `i_frame`, `cache`, `hidden`, `permission`, `create_by`, `update_by`, `create_time`, `update_time`) VALUES (48, 3, 0, 2, '角色创建', NULL, '', 2, '', '', b'0', b'0', b'0', 'roles:add', NULL, NULL, '2019-10-29 12:45:34', NULL);
INSERT INTO `sys_menu` (`id`, `pid`, `sub_count`, `type`, `title`, `name`, `component`, `menu_sort`, `icon`, `path`, `i_frame`, `cache`, `hidden`, `permission`, `create_by`, `update_by`, `create_time`, `update_time`) VALUES (49, 3, 0, 2, '角色修改', NULL, '', 3, '', '', b'0', b'0', b'0', 'roles:edit', NULL, NULL, '2019-10-29 12:46:16', NULL);
INSERT INTO `sys_menu` (`id`, `pid`, `sub_count`, `type`, `title`, `name`, `component`, `menu_sort`, `icon`, `path`, `i_frame`, `cache`, `hidden`, `permission`, `create_by`, `update_by`, `create_time`, `update_time`) VALUES (50, 3, 0, 2, '角色删除', NULL, '', 4, '', '', b'0', b'0', b'0', 'roles:del', NULL, NULL, '2019-10-29 12:46:51', NULL);
INSERT INTO `sys_menu` (`id`, `pid`, `sub_count`, `type`, `title`, `name`, `component`, `menu_sort`, `icon`, `path`, `i_frame`, `cache`, `hidden`, `permission`, `create_by`, `update_by`, `create_time`, `update_time`) VALUES (52, 5, 0, 2, '菜单新增', NULL, '', 2, '', '', b'0', b'0', b'0', 'menu:add', NULL, NULL, '2019-10-29 12:55:07', NULL);
INSERT INTO `sys_menu` (`id`, `pid`, `sub_count`, `type`, `title`, `name`, `component`, `menu_sort`, `icon`, `path`, `i_frame`, `cache`, `hidden`, `permission`, `create_by`, `update_by`, `create_time`, `update_time`) VALUES (53, 5, 0, 2, '菜单编辑', NULL, '', 3, '', '', b'0', b'0', b'0', 'menu:edit', NULL, NULL, '2019-10-29 12:55:40', NULL);
INSERT INTO `sys_menu` (`id`, `pid`, `sub_count`, `type`, `title`, `name`, `component`, `menu_sort`, `icon`, `path`, `i_frame`, `cache`, `hidden`, `permission`, `create_by`, `update_by`, `create_time`, `update_time`) VALUES (54, 5, 0, 2, '菜单删除', NULL, '', 4, '', '', b'0', b'0', b'0', 'menu:del', NULL, NULL, '2019-10-29 12:56:00', NULL);
INSERT INTO `sys_menu` (`id`, `pid`, `sub_count`, `type`, `title`, `name`, `component`, `menu_sort`, `icon`, `path`, `i_frame`, `cache`, `hidden`, `permission`, `create_by`, `update_by`, `create_time`, `update_time`) VALUES (56, 35, 0, 2, '部门新增', NULL, '', 2, '', '', b'0', b'0', b'0', 'dept:add', NULL, NULL, '2019-10-29 12:57:09', NULL);
INSERT INTO `sys_menu` (`id`, `pid`, `sub_count`, `type`, `title`, `name`, `component`, `menu_sort`, `icon`, `path`, `i_frame`, `cache`, `hidden`, `permission`, `create_by`, `update_by`, `create_time`, `update_time`) VALUES (57, 35, 0, 2, '部门编辑', NULL, '', 3, '', '', b'0', b'0', b'0', 'dept:edit', NULL, NULL, '2019-10-29 12:57:27', NULL);
INSERT INTO `sys_menu` (`id`, `pid`, `sub_count`, `type`, `title`, `name`, `component`, `menu_sort`, `icon`, `path`, `i_frame`, `cache`, `hidden`, `permission`, `create_by`, `update_by`, `create_time`, `update_time`) VALUES (58, 35, 0, 2, '部门删除', NULL, '', 4, '', '', b'0', b'0', b'0', 'dept:del', NULL, NULL, '2019-10-29 12:57:41', NULL);
INSERT INTO `sys_menu` (`id`, `pid`, `sub_count`, `type`, `title`, `name`, `component`, `menu_sort`, `icon`, `path`, `i_frame`, `cache`, `hidden`, `permission`, `create_by`, `update_by`, `create_time`, `update_time`) VALUES (60, 37, 0, 2, '岗位新增', NULL, '', 2, '', '', b'0', b'0', b'0', 'job:add', NULL, NULL, '2019-10-29 12:58:27', NULL);
INSERT INTO `sys_menu` (`id`, `pid`, `sub_count`, `type`, `title`, `name`, `component`, `menu_sort`, `icon`, `path`, `i_frame`, `cache`, `hidden`, `permission`, `create_by`, `update_by`, `create_time`, `update_time`) VALUES (61, 37, 0, 2, '岗位编辑', NULL, '', 3, '', '', b'0', b'0', b'0', 'job:edit', NULL, NULL, '2019-10-29 12:58:45', NULL);
INSERT INTO `sys_menu` (`id`, `pid`, `sub_count`, `type`, `title`, `name`, `component`, `menu_sort`, `icon`, `path`, `i_frame`, `cache`, `hidden`, `permission`, `create_by`, `update_by`, `create_time`, `update_time`) VALUES (62, 37, 0, 2, '岗位删除', NULL, '', 4, '', '', b'0', b'0', b'0', 'job:del', NULL, NULL, '2019-10-29 12:59:04', NULL);
INSERT INTO `sys_menu` (`id`, `pid`, `sub_count`, `type`, `title`, `name`, `component`, `menu_sort`, `icon`, `path`, `i_frame`, `cache`, `hidden`, `permission`, `create_by`, `update_by`, `create_time`, `update_time`) VALUES (64, 39, 0, 2, '字典新增', NULL, '', 2, '', '', b'0', b'0', b'0', 'dict:add', NULL, NULL, '2019-10-29 13:00:17', NULL);
INSERT INTO `sys_menu` (`id`, `pid`, `sub_count`, `type`, `title`, `name`, `component`, `menu_sort`, `icon`, `path`, `i_frame`, `cache`, `hidden`, `permission`, `create_by`, `update_by`, `create_time`, `update_time`) VALUES (65, 39, 0, 2, '字典编辑', NULL, '', 3, '', '', b'0', b'0', b'0', 'dict:edit', NULL, NULL, '2019-10-29 13:00:42', NULL);
INSERT INTO `sys_menu` (`id`, `pid`, `sub_count`, `type`, `title`, `name`, `component`, `menu_sort`, `icon`, `path`, `i_frame`, `cache`, `hidden`, `permission`, `create_by`, `update_by`, `create_time`, `update_time`) VALUES (66, 39, 0, 2, '字典删除', NULL, '', 4, '', '', b'0', b'0', b'0', 'dict:del', NULL, NULL, '2019-10-29 13:00:59', NULL);
COMMIT;

-- ----------------------------
-- Table structure for sys_role
-- ----------------------------
DROP TABLE IF EXISTS `sys_role`;
CREATE TABLE `sys_role` (
                            `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT 'ID',
                            `name` varchar(100) NOT NULL COMMENT '名称',
                            `level` int(50) DEFAULT NULL COMMENT '角色级别',
                            `data_scope` varchar(255) DEFAULT NULL COMMENT '数据权限',
                            `description` varchar(255) DEFAULT NULL COMMENT '角色描述',
                            `create_by` varchar(255) DEFAULT NULL COMMENT '创建者',
                            `update_by` varchar(255) DEFAULT NULL COMMENT '更新者',
                            `create_time` datetime DEFAULT NULL COMMENT '创建日期',
                            `update_time` datetime DEFAULT NULL COMMENT '更新时间',
                            PRIMARY KEY (`id`) USING BTREE,
                            UNIQUE KEY `uniq_name` (`name`),
                            KEY `idx_level` (`level`)
) ENGINE=InnoDB AUTO_INCREMENT=6 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci ROW_FORMAT=COMPACT COMMENT='角色表';

-- ----------------------------
-- Records of sys_role
-- ----------------------------
BEGIN;
INSERT INTO `sys_role` (`id`, `name`, `level`, `data_scope`, `description`, `create_by`, `update_by`, `create_time`,
                        `update_time`)
VALUES (1, '管理员', 1, 'ALL', NULL, NULL, 'admin', '2018-11-23 11:04:37', '2020-08-06 16:10:24');
INSERT INTO `sys_role` (`id`, `name`, `level`, `data_scope`, `description`, `create_by`, `update_by`, `create_time`,
                        `update_time`)
VALUES (2, '普通用户', 2, 'DEPT', NULL, NULL, 'admin', '2018-11-23 13:09:06', '2020-09-05 10:45:12');
COMMIT;

-- ----------------------------
-- Table structure for sys_role_dept
-- ----------------------------
DROP TABLE IF EXISTS `sys_role_dept`;
CREATE TABLE `sys_role_dept`
(
    `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT 'ID',
                                   `role_id` bigint(20) NOT NULL COMMENT '角色ID',
                                   `dept_id` bigint(20) NOT NULL COMMENT '部门ID',
    PRIMARY KEY (`id`) USING BTREE,
    UNIQUE KEY `uniq_role_dept` (`role_id`,`dept_id`) USING BTREE,
                                   KEY `idx_dept_id` (`dept_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci ROW_FORMAT=COMPACT COMMENT='角色部门关联';

-- ----------------------------
-- Records of sys_role_dept
-- ----------------------------
BEGIN;
COMMIT;

-- ----------------------------
-- Table structure for sys_role_menu
-- ----------------------------
DROP TABLE IF EXISTS `sys_role_menu`;
CREATE TABLE `sys_role_menu`
(
    `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT 'ID',
                                   `menu_id` bigint(20) NOT NULL COMMENT '菜单ID',
                                   `role_id` bigint(20) NOT NULL COMMENT '角色ID',
    PRIMARY KEY (`id`) USING BTREE,
    UNIQUE KEY `uniq_menu_role` (`menu_id`,`role_id`) USING BTREE,
                                   KEY `idx_role_id` (`role_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci ROW_FORMAT=COMPACT COMMENT='角色菜单关联';

-- ----------------------------
-- Records of sys_role_menu
-- ----------------------------
BEGIN;
INSERT INTO `sys_role_menu` (`menu_id`, `role_id`)
VALUES (1, 1);
INSERT INTO `sys_role_menu` (`menu_id`, `role_id`)
VALUES (1, 2);
INSERT INTO `sys_role_menu` (`menu_id`, `role_id`)
VALUES (2, 1);
INSERT INTO `sys_role_menu` (`menu_id`, `role_id`)
VALUES (2, 2);
INSERT INTO `sys_role_menu` (`menu_id`, `role_id`)
VALUES (3, 1);
INSERT INTO `sys_role_menu` (`menu_id`, `role_id`)
VALUES (5, 1);
INSERT INTO `sys_role_menu` (`menu_id`, `role_id`)
VALUES (6, 1);
INSERT INTO `sys_role_menu` (`menu_id`, `role_id`)
VALUES (6, 2);
INSERT INTO `sys_role_menu` (`menu_id`, `role_id`)
VALUES (7, 1);
INSERT INTO `sys_role_menu` (`menu_id`, `role_id`)
VALUES (7, 2);
INSERT INTO `sys_role_menu` (`menu_id`, `role_id`)
VALUES (9, 1);
INSERT INTO `sys_role_menu` (`menu_id`, `role_id`)
VALUES (9, 2);
INSERT INTO `sys_role_menu` (`menu_id`, `role_id`)
VALUES (32, 1);
INSERT INTO `sys_role_menu` (`menu_id`, `role_id`)
VALUES (32, 2);
INSERT INTO `sys_role_menu` (`menu_id`, `role_id`)
VALUES (35, 1);
INSERT INTO `sys_role_menu` (`menu_id`, `role_id`)
VALUES (36, 1);
INSERT INTO `sys_role_menu` (`menu_id`, `role_id`)
VALUES (36, 2);
INSERT INTO `sys_role_menu` (`menu_id`, `role_id`)
VALUES (37, 1);
INSERT INTO `sys_role_menu` (`menu_id`, `role_id`)
VALUES (39, 1);
INSERT INTO `sys_role_menu` (`menu_id`, `role_id`)
VALUES (41, 1);
INSERT INTO `sys_role_menu` (`menu_id`, `role_id`)
VALUES (44, 1);
INSERT INTO `sys_role_menu` (`menu_id`, `role_id`)
VALUES (45, 1);
INSERT INTO `sys_role_menu` (`menu_id`, `role_id`)
VALUES (46, 1);
INSERT INTO `sys_role_menu` (`menu_id`, `role_id`)
VALUES (48, 1);
INSERT INTO `sys_role_menu` (`menu_id`, `role_id`)
VALUES (49, 1);
INSERT INTO `sys_role_menu` (`menu_id`, `role_id`)
VALUES (50, 1);
INSERT INTO `sys_role_menu` (`menu_id`, `role_id`)
VALUES (52, 1);
INSERT INTO `sys_role_menu` (`menu_id`, `role_id`)
VALUES (53, 1);
INSERT INTO `sys_role_menu` (`menu_id`, `role_id`)
VALUES (54, 1);
INSERT INTO `sys_role_menu` (`menu_id`, `role_id`)
VALUES (56, 1);
INSERT INTO `sys_role_menu` (`menu_id`, `role_id`)
VALUES (57, 1);
INSERT INTO `sys_role_menu` (`menu_id`, `role_id`)
VALUES (58, 1);
INSERT INTO `sys_role_menu` (`menu_id`, `role_id`)
VALUES (60, 1);
INSERT INTO `sys_role_menu` (`menu_id`, `role_id`)
VALUES (61, 1);
INSERT INTO `sys_role_menu` (`menu_id`, `role_id`)
VALUES (62, 1);
INSERT INTO `sys_role_menu` (`menu_id`, `role_id`)
VALUES (64, 1);
INSERT INTO `sys_role_menu` (`menu_id`, `role_id`)
VALUES (65, 1);
INSERT INTO `sys_role_menu` (`menu_id`, `role_id`)
VALUES (66, 1);
COMMIT;

-- ----------------------------
-- Table structure for sys_user
-- ----------------------------
DROP TABLE IF EXISTS `sys_user`;
CREATE TABLE `sys_user` (
                            `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT 'ID',
                            `dept_id` bigint(20) DEFAULT NULL COMMENT '部门名称',
                            `username` varchar(180) DEFAULT NULL COMMENT '用户名',
                            `nick_name` varchar(255) DEFAULT NULL COMMENT '昵称',
                            `gender` varchar(2) DEFAULT NULL COMMENT '性别',
                            `phone` varchar(255) DEFAULT NULL COMMENT '手机号码',
                            `email` varchar(180) DEFAULT NULL COMMENT '邮箱',
                            `avatar_name` varchar(255) DEFAULT NULL COMMENT '头像地址',
                            `avatar_path` varchar(255) DEFAULT NULL COMMENT '头像真实路径',
                            `password` varchar(255) DEFAULT NULL COMMENT '密码',
                            `is_admin` bit(1) DEFAULT b'0' COMMENT '是否为admin账号',
                            `enabled` bit(1) DEFAULT NULL COMMENT '状态：1启用、0禁用',
                            `create_by` varchar(255) DEFAULT NULL COMMENT '创建者',
                            `update_by` varchar(255) DEFAULT NULL COMMENT '更新者',
                            `pwd_reset_time` datetime DEFAULT NULL COMMENT '修改密码的时间',
                            `create_time` datetime DEFAULT NULL COMMENT '创建日期',
                            `update_time` datetime DEFAULT NULL COMMENT '更新时间',
                            PRIMARY KEY (`id`) USING BTREE,
                            UNIQUE KEY `uniq_email` (`email`) USING BTREE,
                            UNIQUE KEY `uniq_username` (`username`) USING BTREE,
                            KEY `idx_dept_id` (`dept_id`) USING BTREE,
                            KEY `idx_enabled` (`enabled`)
) ENGINE=InnoDB AUTO_INCREMENT=3 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci ROW_FORMAT=COMPACT COMMENT='系统用户';

-- ----------------------------
-- Records of sys_user
-- ----------------------------
BEGIN;
INSERT INTO `sys_user` (`id`, `dept_id`, `username`, `nick_name`, `gender`, `phone`, `email`, `avatar_name`, `avatar_path`, `password`, `is_admin`, `enabled`, `create_by`, `update_by`, `pwd_reset_time`, `create_time`, `update_time`) VALUES (1, 2, 'admin', '管理员', '男', '18888888888', '201507802@qq.com', 'avatar-20250121112710866.png', '/Users/jie/Documents/work/private/eladmin-mp/~/avatar/avatar-20250121112710866.png', '$2a$10$Egp1/gvFlt7zhlXVfEFw4OfWQCGPw0ClmMcc6FjTnvXNRVf9zdMRa', b'1', b'1', NULL, 'admin', '2020-05-03 16:38:31', '2018-08-23 09:11:56', '2020-09-05 10:43:31');
INSERT INTO `sys_user` (`id`, `dept_id`, `username`, `nick_name`, `gender`, `phone`, `email`, `avatar_name`, `avatar_path`, `password`, `is_admin`, `enabled`, `create_by`, `update_by`, `pwd_reset_time`, `create_time`, `update_time`) VALUES (2, 2, 'test', '测试', '男', '19999999999', '231@qq.com', NULL, NULL, '$2a$10$BSR9oUNtzWhnqs8NmZk5Zu3zfsNop3KxZO0xGEzy01cumf9k/AW6.', b'0', b'1', 'admin', 'admin', '2025-01-21 15:25:12', '2020-05-05 11:15:49', '2020-09-05 10:43:38');
COMMIT;

-- ----------------------------
-- Table structure for sys_user_job
-- ----------------------------
DROP TABLE IF EXISTS `sys_user_job`;
CREATE TABLE `sys_user_job`
(
    `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT 'ID',
                                  `user_id` bigint(20) NOT NULL COMMENT '用户ID',
                                  `job_id` bigint(20) NOT NULL COMMENT '岗位ID',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uniq_user_job` (`user_id`,`job_id`),
                                  KEY `idx_job_id` (`job_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3 COLLATE=utf8mb3_general_ci COMMENT='用户与岗位关联表';

-- ----------------------------
-- Records of sys_user_job
-- ----------------------------
BEGIN;
INSERT INTO `sys_user_job` (`user_id`, `job_id`)
VALUES (1, 11);
INSERT INTO `sys_user_job` (`user_id`, `job_id`)
VALUES (2, 11);
COMMIT;

-- ----------------------------
-- Table structure for sys_user_role
-- ----------------------------
DROP TABLE IF EXISTS `sys_user_role`;
CREATE TABLE `sys_user_role`
(
    `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT 'ID',
                                   `user_id` bigint(20) NOT NULL COMMENT '用户ID',
                                   `role_id` bigint(20) NOT NULL COMMENT '角色ID',
    PRIMARY KEY (`id`) USING BTREE,
    UNIQUE KEY `uniq_user_role` (`user_id`,`role_id`) USING BTREE,
                                   KEY `idx_role_id` (`role_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci ROW_FORMAT=COMPACT COMMENT='用户角色关联';

-- ----------------------------
-- Records of sys_user_role
-- ----------------------------
BEGIN;
INSERT INTO `sys_user_role` (`user_id`, `role_id`)
VALUES (1, 1);
INSERT INTO `sys_user_role` (`user_id`, `role_id`)
VALUES (2, 2);
COMMIT;

SET FOREIGN_KEY_CHECKS = 1;

