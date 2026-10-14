-- HowBlog 2.0 用户关注关系表迁移
-- 适用于 MySQL 8.x；重复执行不会删除现有数据或表结构。
-- 关注关系（follower_id, followee_id 唯一）为权威；tb_user.fanscount/followcount 由应用端原子累加。
-- 历史 fanscount/followcount 计数保留，不重算、不补关系。

USE `how-blog-smaill`;

CREATE TABLE IF NOT EXISTS `tb_user_follow` (
  `follower_id` VARCHAR(64) NOT NULL COMMENT '关注者用户 ID',
  `followee_id` VARCHAR(64) NOT NULL COMMENT '被关注用户 ID',
  `created_at` DATETIME(6) NOT NULL COMMENT '关注时间',
  PRIMARY KEY (`follower_id`, `followee_id`),
  KEY `idx_tb_user_follow_followee` (`followee_id`),
  KEY `idx_tb_user_follow_created` (`created_at`),
  CONSTRAINT `fk_user_follow_follower` FOREIGN KEY (`follower_id`) REFERENCES `tb_user` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_user_follow_followee` FOREIGN KEY (`followee_id`) REFERENCES `tb_user` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户关注唯一关系';
