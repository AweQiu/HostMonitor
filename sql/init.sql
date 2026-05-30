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
  `agent_key` VARCHAR(128) NOT NULL,
  `avatar` VARCHAR(255),
  `status` TINYINT NOT NULL DEFAULT 1,
  `group_id` BIGINT,
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `is_del` TINYINT NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_username` (`username`),
  UNIQUE KEY `uk_email` (`email`),
  UNIQUE KEY `uk_agent_key` (`agent_key`),
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
  `ip_address` VARCHAR(45) NOT NULL DEFAULT '',
  `cpu_model` VARCHAR(255),
  `cpu_cores` INT,
  `memory_total` BIGINT,
  `disk_total` BIGINT,
  `os_name` VARCHAR(100),
  `os_version` VARCHAR(100),
  `status` TINYINT NOT NULL DEFAULT 0,
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

-- 主机进程表
CREATE TABLE `host_process` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `host_id` BIGINT NOT NULL,
  `pid` INT NOT NULL,
  `ppid` INT DEFAULT NULL,
  `name` VARCHAR(255) DEFAULT NULL,
  `user` VARCHAR(128) DEFAULT NULL,
  `cpu_usage` DECIMAL(10,4) DEFAULT NULL,
  `memory_usage` DECIMAL(10,4) DEFAULT NULL,
  `memory_used` BIGINT DEFAULT NULL,
  `status` VARCHAR(32) DEFAULT NULL,
  `start_time` DATETIME DEFAULT NULL,
  `command_line` TEXT DEFAULT NULL,
  `collect_time` DATETIME NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_host_collect` (`host_id`, `collect_time`),
  KEY `idx_collect_time` (`collect_time`),
  CONSTRAINT `fk_process_host` FOREIGN KEY (`host_id`) REFERENCES `host` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 进程规则表
CREATE TABLE `process_rule` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `group_id` BIGINT NOT NULL,
  `process_name` VARCHAR(255) NOT NULL,
  `rule_type` VARCHAR(20) NOT NULL COMMENT 'WHITELIST或BLACKLIST',
  `description` VARCHAR(255),
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `is_del` TINYINT NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `idx_group_id` (`group_id`),
  UNIQUE KEY `uk_group_process` (`group_id`, `process_name`),
  CONSTRAINT `fk_rule_group` FOREIGN KEY (`group_id`) REFERENCES `group` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 告警记录表
CREATE TABLE `alert_record` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `host_id` BIGINT NOT NULL,
  `alert_type` VARCHAR(30) NOT NULL COMMENT 'BLACKLIST_RUNNING或WHITELIST_MISSING',
  `process_name` VARCHAR(255) NOT NULL,
  `rule_id` BIGINT DEFAULT NULL,
  `notification_id` BIGINT DEFAULT NULL,
  `status` TINYINT NOT NULL DEFAULT 0 COMMENT '0-未处理 1-已处理',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_host_id` (`host_id`),
  KEY `idx_status` (`status`),
  KEY `idx_create_time` (`create_time`),
  KEY `idx_host_type_process` (`host_id`, `alert_type`, `process_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 通知表
CREATE TABLE `notification` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `title` VARCHAR(100) NOT NULL,
  `content` TEXT NOT NULL,
  `sender_id` BIGINT DEFAULT NULL COMMENT 'NULL表示系统自动发送',
  `send_type` TINYINT NOT NULL COMMENT '1-全体 2-分组 3-指定用户',
  `target_group_id` BIGINT DEFAULT NULL,
  `target_user_ids` TEXT DEFAULT NULL COMMENT 'JSON数组，如[1,2,3]',
  `status` TINYINT NOT NULL DEFAULT 1 COMMENT '0-草稿 1-已发送',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `send_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_sender_id` (`sender_id`),
  KEY `idx_send_time` (`send_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 用户通知关系表
CREATE TABLE `user_notification` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `user_id` BIGINT NOT NULL,
  `notification_id` BIGINT NOT NULL,
  `is_read` TINYINT NOT NULL DEFAULT 0 COMMENT '0-未读 1-已读',
  `read_time` DATETIME DEFAULT NULL,
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_notif` (`user_id`, `notification_id`),
  KEY `idx_user_read` (`user_id`, `is_read`),
  KEY `idx_notif_id` (`notification_id`),
  CONSTRAINT `fk_un_user` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`),
  CONSTRAINT `fk_un_notif` FOREIGN KEY (`notification_id`) REFERENCES `notification` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- =============================================
-- 初始化数据
-- =============================================

-- 初始化三个角色
INSERT INTO `role` (`role_key`, `role_name`, `description`) VALUES
('SUPER_ADMIN', '超级管理员', '拥有系统所有权限'),
('GROUP_ADMIN', '组管理员', '管理本组用户和资源'),
('USER', '普通用户', '基本操作权限');

-- 初始化超管账号（密码 123456 的 BCrypt 加密）
INSERT INTO `user` (`username`, `email`, `password`, `agent_key`, `status`) VALUES
('admin', '1@example.com', '$2a$10$wV0PoqPUK0etpdEUn11UI.NMJfLWSUc8ZwhwNkIhi5PpJw2DgEIwm', 'a1b2c3d4-e5f6-7890-abcd-ef1234567890', 1);

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

-- 开启事件调度器
SET GLOBAL event_scheduler = ON;

-- 定时清理48小时前的性能指标数据（每天凌晨3点执行）
CREATE EVENT IF NOT EXISTS `ev_clean_host_metric`
ON SCHEDULE EVERY 1 DAY STARTS CONCAT(CURDATE() + INTERVAL 1 DAY, ' 03:00:00')
ON COMPLETION PRESERVE ENABLE
COMMENT '每天凌晨3点清理48小时前的host_metric数据'
DO
  DELETE FROM `host_metric` WHERE `collect_time` < DATE_SUB(NOW(), INTERVAL 48 HOUR);

-- 定时清理48小时前的进程数据（每48小时凌晨4点执行）
CREATE EVENT IF NOT EXISTS `ev_clean_host_process`
ON SCHEDULE EVERY 48 HOUR STARTS CONCAT(CURDATE() + INTERVAL 1 DAY, ' 04:00:00')
ON COMPLETION PRESERVE ENABLE
COMMENT '每48小时清理48小时前的host_process数据'
DO
  DELETE FROM `host_process` WHERE `collect_time` < DATE_SUB(NOW(), INTERVAL 48 HOUR);
