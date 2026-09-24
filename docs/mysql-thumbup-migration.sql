-- 评论点赞一致性迁移。仅新增表，不删除或改写现有业务数据。
USE `how-blog-smaill`;

CREATE TABLE IF NOT EXISTS `tb_comment_thumbup` (
  `commentid` VARCHAR(64) NOT NULL,
  `userid` VARCHAR(64) NOT NULL,
  PRIMARY KEY (`commentid`, `userid`),
  KEY `idx_tb_comment_thumbup_userid` (`userid`),
  CONSTRAINT `fk_comment_thumbup_comment` FOREIGN KEY (`commentid`)
    REFERENCES `tb_comment` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='评论点赞唯一关系';