-- 文章标签关系表迁移
-- 适用于已执行 mysql-init.sql 的 MySQL 8.x 数据库。
-- 只创建关系表，不改写既有文章、标签或文章计数。

CREATE TABLE IF NOT EXISTS `tb_article_label` (
  `articleid` VARCHAR(64) NOT NULL COMMENT '文章 ID',
  `labelid` VARCHAR(64) NOT NULL COMMENT '标签 ID',
  `created_at` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '关联时间',
  PRIMARY KEY (`articleid`, `labelid`),
  KEY `idx_tb_article_label_labelid` (`labelid`),
  CONSTRAINT `fk_article_label_article` FOREIGN KEY (`articleid`)
    REFERENCES `tb_article` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_article_label_label` FOREIGN KEY (`labelid`)
    REFERENCES `tb_label` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='文章标签唯一关系';