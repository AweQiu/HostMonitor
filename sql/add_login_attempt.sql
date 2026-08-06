-- 登录尝试记录表（防爆破功能）
CREATE TABLE IF NOT EXISTS `login_attempt` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `username` VARCHAR(50) NOT NULL,
  `ip_address` VARCHAR(45) NOT NULL,
  `success` TINYINT NOT NULL DEFAULT 0,
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_username_time` (`username`, `create_time`),
  KEY `idx_ip_time` (`ip_address`, `create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 定时清理24小时前的登录尝试记录
CREATE EVENT IF NOT EXISTS `ev_clean_login_attempt`
ON SCHEDULE EVERY 1 DAY STARTS CONCAT(CURDATE() + INTERVAL 1 DAY, ' 03:30:00')
ON COMPLETION PRESERVE ENABLE
COMMENT '每天凌晨3:30清理24小时前的login_attempt数据'
DO
  DELETE FROM `login_attempt` WHERE `create_time` < DATE_SUB(NOW(), INTERVAL 24 HOUR);
