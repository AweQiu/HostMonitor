-- =============================================
-- HostMonitor 数据库初始化脚本
-- RBAC 三级权限 + 客户机信息
-- =============================================

-- 分组表
CREATE TABLE `group` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `group_name` VARCHAR(100) NOT NULL,
  `description` VARCHAR(255),
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `is_del` TINYINT NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 用户表
CREATE TABLE `user` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `username` VARCHAR(50) NOT NULL,
  `email` VARCHAR(100) NOT NULL,
  `password` VARCHAR(255) NOT NULL,
  `avatar` VARCHAR(255),
  `status` TINYINT NOT NULL DEFAULT 1,
  `group_id` BIGINT,
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `is_del` TINYINT NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_username` (`username`),
  UNIQUE KEY `uk_email` (`email`),
  KEY `idx_group_id` (`group_id`),
  CONSTRAINT `fk_user_group` FOREIGN KEY (`group_id`) REFERENCES `group` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 角色表
CREATE TABLE `role` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `role_key` VARCHAR(50) NOT NULL,
  `role_name` VARCHAR(100) NOT NULL,
  `description` VARCHAR(255),
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `is_del` TINYINT NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_role_key` (`role_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 用户角色关联表
CREATE TABLE `user_role` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `user_id` BIGINT NOT NULL,
  `role_id` BIGINT NOT NULL,
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `is_del` TINYINT NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_role` (`user_id`, `role_id`),
  KEY `idx_role_id` (`role_id`),
  CONSTRAINT `fk_ur_user` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`),
  CONSTRAINT `fk_ur_role` FOREIGN KEY (`role_id`) REFERENCES `role` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 权限表
CREATE TABLE `permission` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `perm_key` VARCHAR(100) NOT NULL,
  `perm_name` VARCHAR(100) NOT NULL,
  `parent_id` BIGINT DEFAULT NULL,
  `type` TINYINT NOT NULL,
  `sort_order` INT DEFAULT 0,
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `is_del` TINYINT NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_perm_key` (`perm_key`),
  KEY `idx_parent_id` (`parent_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 角色权限关联表
CREATE TABLE `role_permission` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `role_id` BIGINT NOT NULL,
  `permission_id` BIGINT NOT NULL,
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `is_del` TINYINT NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_role_perm` (`role_id`, `permission_id`),
  KEY `idx_permission_id` (`permission_id`),
  CONSTRAINT `fk_rp_role` FOREIGN KEY (`role_id`) REFERENCES `role` (`id`),
  CONSTRAINT `fk_rp_perm` FOREIGN KEY (`permission_id`) REFERENCES `permission` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 客户机信息表
CREATE TABLE `host` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `user_id` BIGINT NOT NULL,
  `hostname` VARCHAR(100) NOT NULL,
  `ip_address` VARCHAR(45) NOT NULL,
  `cpu_model` VARCHAR(255),
  `cpu_cores` INT,
  `memory_total` BIGINT,
  `disk_total` BIGINT,
  `os_name` VARCHAR(100),
  `os_version` VARCHAR(100),
  `status` TINYINT NOT NULL DEFAULT 1,
  `last_heartbeat` DATETIME,
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `is_del` TINYINT NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_id` (`user_id`),
  KEY `idx_ip_address` (`ip_address`),
  KEY `idx_hostname` (`hostname`),
  CONSTRAINT `fk_host_user` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 客户机性能指标表
CREATE TABLE `host_metric` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `host_id` BIGINT NOT NULL,
  `cpu_usage` DECIMAL(5,2),
  `memory_usage` DECIMAL(5,2),
  `disk_usage` DECIMAL(5,2),
  `memory_used` BIGINT,
  `disk_used` BIGINT,
  `collect_time` DATETIME NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_host_id` (`host_id`),
  KEY `idx_collect_time` (`collect_time`),
  CONSTRAINT `fk_metric_host` FOREIGN KEY (`host_id`) REFERENCES `host` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- =============================================
-- 初始化数据
-- =============================================

-- 初始化三个角色
INSERT INTO `role` (`role_key`, `role_name`, `description`) VALUES
('SUPER_ADMIN', '超级管理员', '拥有系统所有权限'),
('GROUP_ADMIN', '组管理员', '管理本组用户和资源'),
('USER', '普通用户', '基本操作权限');

-- 初始化管理员组（不可添加或删除成员的超级管理组）
INSERT INTO `group` (`group_name`, `description`) VALUES
('超级管理员组', '系统最高权限组，创建后不可添加或删除成员');

-- 初始化超管账号（密码 123456 的 BCrypt 加密）
INSERT INTO `user` (`username`, `email`, `password`, `status`, `group_id`) VALUES
('admin', '1@example.com', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iKTVKIUi', 1, LAST_INSERT_ID());

-- 为超管分配 SUPER_ADMIN 角色
INSERT INTO `user_role` (`user_id`, `role_id`) VALUES
(LAST_INSERT_ID(), 1);

-- 初始化系统权限
INSERT INTO `permission` (`perm_key`, `perm_name`, `parent_id`, `type`, `sort_order`) VALUES
('system',       '系统管理',     NULL, 1, 1),
('system:user',  '用户管理',     1,    2, 1),
('system:role',  '角色管理',     1,    2, 2),
('system:perm',  '权限管理',     1,    2, 3),
('system:group', '分组管理',     1,    2, 4),
('monitor',      '监控管理',     NULL, 1, 2),
('monitor:host', '主机管理',     6,    2, 1),
('monitor:metric','指标查看',    6,    2, 2),
('monitor:dashboard','仪表盘',   6,    2, 3);

-- 为 SUPER_ADMIN 角色分配所有权限
INSERT INTO `role_permission` (`role_id`, `permission_id`)
SELECT 1, id FROM `permission`;

-- 为 GROUP_ADMIN 角色分配监控相关权限
INSERT INTO `role_permission` (`role_id`, `permission_id`)
SELECT 2, id FROM `permission` WHERE `perm_key` IN ('monitor', 'monitor:host', 'monitor:metric', 'monitor:dashboard');

-- 为普通用户分配查看权限
INSERT INTO `role_permission` (`role_id`, `permission_id`)
SELECT 3, id FROM `permission` WHERE `perm_key` IN ('monitor:dashboard', 'monitor:metric');
