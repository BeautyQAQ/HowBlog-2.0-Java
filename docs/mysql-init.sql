-- HowBlog 2.0 MySQL 初始化脚本
-- 适用于 MySQL 8.x。执行后会创建 how-blog-smaill 及其业务表。
-- 重复执行不会删除现有数据或表结构。

CREATE DATABASE IF NOT EXISTS `how-blog-smaill`
  DEFAULT CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;

USE `how-blog-smaill`;

CREATE TABLE IF NOT EXISTS `tb_user` (
  `id` VARCHAR(64) NOT NULL COMMENT '用户 ID，由应用生成',
  `mobile` VARCHAR(32) DEFAULT NULL COMMENT '手机号',
  `password` VARCHAR(255) DEFAULT NULL COMMENT '密码',
  `nickname` VARCHAR(100) DEFAULT NULL COMMENT '昵称',
  `sex` VARCHAR(16) DEFAULT NULL COMMENT '性别',
  `birthday` DATETIME DEFAULT NULL COMMENT '生日',
  `avatar` VARCHAR(500) DEFAULT NULL COMMENT '头像地址',
  `email` VARCHAR(255) DEFAULT NULL COMMENT '邮箱',
  `regdate` DATETIME DEFAULT NULL COMMENT '注册时间',
  `updatedate` DATETIME DEFAULT NULL COMMENT '更新时间',
  `lastdate` DATETIME DEFAULT NULL COMMENT '最后登录时间',
  `online` BIGINT DEFAULT 0 COMMENT '在线时长，单位分钟',
  `interest` VARCHAR(500) DEFAULT NULL COMMENT '兴趣',
  `personality` VARCHAR(500) DEFAULT NULL COMMENT '个性签名',
  `fanscount` INT DEFAULT 0 COMMENT '粉丝数',
  `followcount` INT DEFAULT 0 COMMENT '关注数',
  PRIMARY KEY (`id`),
  KEY `idx_tb_user_mobile` (`mobile`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户表';

CREATE TABLE IF NOT EXISTS `tb_label` (
  `id` VARCHAR(64) NOT NULL COMMENT '标签 ID，由应用生成',
  `labelname` VARCHAR(100) NOT NULL COMMENT '标签名称',
  `state` VARCHAR(16) DEFAULT NULL COMMENT '状态',
  `count` BIGINT DEFAULT 0 COMMENT '使用数量',
  `fans` BIGINT DEFAULT 0 COMMENT '关注数',
  `recommend` VARCHAR(16) DEFAULT NULL COMMENT '是否推荐',
  PRIMARY KEY (`id`),
  KEY `idx_tb_label_labelname` (`labelname`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='标签表';

CREATE TABLE IF NOT EXISTS `tb_article` (
  `id` VARCHAR(64) NOT NULL COMMENT '文章 ID，由应用生成',
  `columnid` VARCHAR(64) DEFAULT NULL COMMENT '专栏 ID',
  `userid` VARCHAR(64) DEFAULT NULL COMMENT '作者用户 ID',
  `title` VARCHAR(255) NOT NULL COMMENT '标题',
  `content` LONGTEXT COMMENT '正文',
  `image` VARCHAR(500) DEFAULT NULL COMMENT '封面地址',
  `createtime` DATETIME DEFAULT NULL COMMENT '发布时间',
  `updatetime` DATETIME DEFAULT NULL COMMENT '更新时间',
  `ispublic` VARCHAR(16) DEFAULT NULL COMMENT '是否公开',
  `istop` VARCHAR(16) DEFAULT NULL COMMENT '是否置顶',
  `visits` INT DEFAULT 0 COMMENT '浏览量',
  `thumbup` INT DEFAULT 0 COMMENT '点赞数',
  `comment` INT DEFAULT 0 COMMENT '评论数',
  `state` VARCHAR(16) DEFAULT NULL COMMENT '审核状态',
  `channelid` VARCHAR(64) DEFAULT NULL COMMENT '频道 ID',
  `url` VARCHAR(500) DEFAULT NULL COMMENT '文章 URL',
  `type` VARCHAR(32) DEFAULT NULL COMMENT '文章类型',
  PRIMARY KEY (`id`),
  KEY `idx_tb_article_userid` (`userid`),
  KEY `idx_tb_article_createtime` (`createtime`),
  KEY `idx_tb_article_state` (`state`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='文章表';

CREATE TABLE IF NOT EXISTS `tb_comment` (
  `id` VARCHAR(64) NOT NULL COMMENT '评论 ID，由应用生成',
  `articleid` VARCHAR(64) NOT NULL COMMENT '文章 ID',
  `content` TEXT NOT NULL COMMENT '评论内容',
  `userid` VARCHAR(64) DEFAULT NULL COMMENT '用户 ID',
  `parentid` VARCHAR(64) DEFAULT NULL COMMENT '父评论 ID',
  `publishdate` DATETIME DEFAULT NULL COMMENT '发布时间',
  `thumbup` INT NOT NULL DEFAULT 0 COMMENT '点赞数',
  PRIMARY KEY (`id`),
  KEY `idx_tb_comment_articleid` (`articleid`),
  KEY `idx_tb_comment_parentid` (`parentid`),
  KEY `idx_tb_comment_publishdate` (`publishdate`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='文章评论表';
