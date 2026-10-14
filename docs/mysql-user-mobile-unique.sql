-- HowBlog 2.0 用户手机号唯一约束迁移（新增 uk_tb_user_mobile）
-- 适用于 MySQL 8.x。可重复执行：索引已存在时只输出提示，不重复执行 DDL。
--
-- 执行前先核对历史重复数据（应返回 0 行）：
--   SELECT mobile, COUNT(*) FROM tb_user
--   WHERE mobile IS NOT NULL GROUP BY mobile HAVING COUNT(*) > 1;
-- 若存在重复手机号，先与业务确认处理方式，不要直接删除用户数据。
-- 唯一索引允许多行 mobile 为 NULL；应用注册流程只写入合法手机号。

USE `how-blog-smaill`;

SET @unique_exists := (SELECT COUNT(*) FROM information_schema.statistics
  WHERE table_schema = DATABASE() AND table_name = 'tb_user' AND index_name = 'uk_tb_user_mobile');
SET @legacy_exists := (SELECT COUNT(*) FROM information_schema.statistics
  WHERE table_schema = DATABASE() AND table_name = 'tb_user' AND index_name = 'idx_tb_user_mobile');

-- 唯一索引取代原有非唯一索引；两条变更在同一条 ALTER 中原子提交，重复数据会让本语句整体失败。
SET @ddl := IF(@unique_exists = 0,
  CONCAT('ALTER TABLE `tb_user`',
         IF(@legacy_exists > 0, ' DROP INDEX `idx_tb_user_mobile`,', ''),
         ' ADD UNIQUE KEY `uk_tb_user_mobile` (`mobile`)'),
  'SELECT ''uk_tb_user_mobile already exists'' AS info');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
