-- =======================================================
-- HowBlog 2.0 初始测试数据脚本
-- 适用于 MySQL 8.x 数据库: how-blog-smaill
-- =======================================================

USE `how-blog-smaill`;

-- 1. 初始化测试用户数据 (密码均为 123456)
DELETE FROM `tb_user` WHERE `id` IN ('10001', '10002');
INSERT INTO `tb_user` (
  `id`, `mobile`, `password`, `nickname`, `sex`, `birthday`,
  `avatar`, `email`, `regdate`, `updatedate`, `lastdate`,
  `online`, `interest`, `personality`, `fanscount`, `followcount`
) VALUES
(
  '10001', '13800000000', '123456', 'Alice', '女', '1995-06-15 00:00:00',
  'https://cube.elemecdn.com/0/88/03b0d39583f48206768a7534e55bcpng.png',
  'alice@howblog.com', NOW(), NOW(), NOW(), 120, '技术写作, 架构设计',
  '热爱开源与微服务技术', 256, 32
),
(
  '10002', '13900000000', '123456', 'Bob', '男', '1993-11-20 00:00:00',
  'https://cube.elemecdn.com/3/7c/3ea6beec64369c2642b92c6726f1epng.png',
  'bob@howblog.com', NOW(), NOW(), NOW(), 90, '全栈开发, 即时通讯',
  'Code for fun!', 188, 45
);

-- 2. 初始化标签数据
DELETE FROM `tb_label` WHERE `id` IN ('1', '2', '3', '4', '5');
INSERT INTO `tb_label` (`id`, `labelname`, `state`, `count`, `fans`, `recommend`) VALUES
('1', 'Java', '1', 28, 350, '1'),
('2', 'Spring Boot', '1', 22, 280, '1'),
('3', 'Vue.js', '1', 18, 195, '1'),
('4', '微服务', '1', 15, 160, '1'),
('5', 'MySQL', '1', 12, 140, '0');

-- 3. 初始化文章数据
DELETE FROM `tb_article` WHERE `id` IN ('20001', '20002', '20003');
INSERT INTO `tb_article` (
  `id`, `columnid`, `userid`, `title`, `content`, `image`,
  `createtime`, `updatetime`, `ispublic`, `istop`, `visits`,
  `thumbup`, `comment`, `state`, `channelid`, `url`, `type`
) VALUES
(
  '20001', '1', '10001', 'HowBlog 2.0 微服务架构拆分与实践',
  '<p>HowBlog 2.0 采用了微服务架构，将核心业务拆分为三个独立的服务模块：</p><ul><li><strong>how_base (9001)</strong>：基础数据服务，负责标签的管理。</li><li><strong>how_article (9004)</strong>：核心业务服务，负责文章内容存储、分页查询与评论交互。</li><li><strong>how_user (9008)</strong>：用户与通讯服务，负责用户登录与 WebSocket 即时聊天。</li></ul><p>通过独立部署与清晰的契约规范，系统获得了更强的扩展性和灵活性。</p>',
  'https://images.unsplash.com/photo-1517694712202-14dd9538aa97?w=600',
  NOW() - INTERVAL 2 DAY, NOW() - INTERVAL 2 DAY, '1', '1', 1024, 88, 2, '1', '1', '', '1'
),
(
  '20002', '3', '10001', 'Vue 2 + Element UI 构建现代化技术博客',
  '<p>在本项目中，前端基于经典的 Vue 2.6 与 Element UI 2.15 技术栈构建。通过封装统一的 Axios 请求拦截器、Vuex 状态持久化、Vue Router 路由守卫以及 DevServer 多服务反向代理，实现了与微服务后端的高效无缝对接。</p><p>界面设计强调清爽排版、清晰的层级关系以及极简优雅的用户交互。</p>',
  'https://images.unsplash.com/photo-1555066931-4365d14bab8c?w=600',
  NOW() - INTERVAL 1 DAY, NOW() - INTERVAL 1 DAY, '1', '0', 680, 45, 1, '1', '3', '', '1'
),
(
  '20003', '4', '10002', '基于 Spring Boot WebSocket 实现聊天室与单聊',
  '<p>在微服务架构中，即时通讯是一个典型的长连接业务。我们在 <code>how_user</code> 模块中集成了 Spring WebSocket，使用 <code>ChatWebSocketHandler</code> 进行连接会话管理，实现了房间广播（Lobby）与点对点私聊功能，支持心跳保活与用户上下线状态监听。</p>',
  'https://images.unsplash.com/photo-1577563908411-5077b6dc7624?w=600',
  NOW(), NOW(), '1', '0', 420, 32, 0, '1', '4', '', '1'
);

-- 4. 初始化评论数据
DELETE FROM `tb_comment` WHERE `id` IN ('30001', '30002', '30003');
INSERT INTO `tb_comment` (`id`, `articleid`, `content`, `userid`, `parentid`, `publishdate`, `thumbup`) VALUES
('30001', '20001', '微服务拆分很清晰，端口规划也很合理！期待后续关于网关的分享。', '10002', NULL, NOW() - INTERVAL 1 DAY, 15),
('30002', '20001', '感谢认可，后续会进一步完善服务注册与发现机制。', '10001', '30001', NOW() - INTERVAL 12 HOUR, 6),
('30003', '20002', 'Element UI 配上现代化阴影与间距，效果非常耐看！', '10002', NULL, NOW() - INTERVAL 6 HOUR, 8);
