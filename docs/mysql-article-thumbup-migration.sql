-- HowBlog 2.0 文章点赞关系表迁移
-- 适用于 MySQL 8.x；重复执行不会删除现有数据或表结构。
-- 与评论点赞一致：关系表（articleid, userid 唯一）为点赞状态权威，tb_article.thumbup 由应用端原子累加。
-- 历史 tb_article.thumbup 计数保留，不重算、不补关系。

USE `how-blog-smaill`;

CREATE TABLE IF NOT EXISTS `tb_article_thumbup` (
  `articleid` VARCHAR(64) NOT NULL,
  `userid` VARCHAR(64) NOT NULL,
  PRIMARY KEY (`articleid`, `userid`),
  KEY `idx_tb_article_thumbup_userid` (`userid`),
  CONSTRAINT `fk_article_thumbup_article` FOREIGN KEY (`articleid`)
    REFERENCES `tb_article` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='文章点赞唯一关系';
