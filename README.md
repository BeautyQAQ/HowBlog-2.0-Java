# HowBlog 2.0

一个基于 Spring Boot 的博客后端项目，采用 Maven 多模块结构，提供用户登录、文章管理、标签管理、评论管理和评论点赞等接口。

## 项目结构

```text
HowBlog-2.0-Java/
├── pom.xml                    # 父工程，统一管理版本和编译配置
├── how_common/                # 公共实体、返回结果和工具类
├── how_user/                  # 用户服务
├── how_base/                  # 基础数据服务，目前包含标签管理
└── how_article/               # 文章和评论服务
```

三个业务模块都是独立的 Spring Boot 应用，可以分别启动：

| 模块 | 启动类 | 默认端口 | 数据存储 |
| --- | --- | --- | --- |
| `how_user` | `com.liushao.user.UserApplication` | `9008` | MySQL `how-blog-smaill` |
| `how_base` | `com.liushao.base.BaseApplication` | `9001` | MySQL `how-blog-smaill` |
| `how_article` | `com.liushao.article.ArticleApplication` | `9004` | MySQL `how-blog-smaill`、Redis |

## 技术栈

- JDK 21
- Spring Boot 2.7.18
- Spring MVC
- Spring Data JPA
- MySQL Connector/J 8.0.33
- MySQL，用于用户、标签、文章和评论数据
- Redis，用于评论点赞记录
- Maven 多模块构建

## 环境要求

开始前请准备：

- JDK 21，并正确设置 `JAVA_HOME`
- Maven 3.8.1 或更高版本（本轮验证使用 3.8.1）
- MySQL 8.x
- Redis

当前工程没有提交 Maven Wrapper。如果系统没有配置 `mvn` 命令，可以直接使用本机 Maven，或在项目根目录执行 Maven Wrapper 生成命令后再使用 Wrapper。

启动服务前必须设置 JWT 密钥环境变量 `HOW_AUTH_JWT_SECRET`，值至少包含 32 个 UTF-8 字节；可选使用 `HOW_AUTH_JWT_ACCESS_TOKEN_TTL_SECONDS` 设置访问令牌有效期，默认 1800 秒。不要把密钥写入仓库或提交到配置文件。

## 配置数据库和中间件

各服务的配置文件位于：

- `how_user/src/main/resources/application.yml`
- `how_base/src/main/resources/application.yml`
- `how_article/src/main/resources/application.yml`

当前配置中的数据库地址、用户名和密码是已有环境配置，包含远程主机地址和明文密码。启动自己的环境前，请务必修改以下配置，不要直接用于生产环境：

- MySQL 地址、端口、数据库名、用户名和密码
- Redis 地址和端口
- 各服务的 HTTP 端口

三个服务共用的数据库名为：

```text
how-blog-smaill
```

文章服务还需要 Redis：

```text
Redis: 6379，逻辑数据库 3
```

三个服务的关键配置支持环境变量覆盖，未设置时使用 `application.yml` 中的默认值（默认值即当前测试环境配置）：

| 环境变量 | 默认值 | 用途 |
| --- | --- | --- |
| `HOW_USER_PORT` / `HOW_BASE_PORT` / `HOW_ARTICLE_PORT` | 9008 / 9001 / 9004 | 服务 HTTP 端口 |
| `HOW_USER_DB_URL` / `HOW_BASE_DB_URL` / `HOW_ARTICLE_DB_URL` | `application.yml` 中的连接串 | MySQL 连接串 |
| `HOW_USER_DB_USER` / `HOW_BASE_DB_USER` / `HOW_ARTICLE_DB_USER` | `root` | MySQL 用户名 |
| `HOW_USER_DB_PASSWORD` / `HOW_BASE_DB_PASSWORD` / `HOW_ARTICLE_DB_PASSWORD` | `application.yml` 中的密码 | MySQL 密码 |
| `HOW_ARTICLE_REDIS_HOST` / `HOW_ARTICLE_REDIS_PORT` / `HOW_ARTICLE_REDIS_PASSWORD` / `HOW_ARTICLE_REDIS_DATABASE` | `application.yml` 中的 Redis 配置 | 文章服务点赞缓存 |
| `HOW_CORS_ALLOWED_ORIGINS` | `*` | 三个服务与 WebSocket 允许的跨域来源，逗号分隔 |

用户服务的认证限流、会话清理和 Redis 变量见 [docs/admin-operations.md](docs/admin-operations.md)。

首次运行前，执行 [docs/mysql-init.sql](docs/mysql-init.sql) 创建 MySQL 数据库及所需业务表。

## 编译项目

在项目根目录执行：

```bash
mvn clean compile
```

跳过测试编译：

```bash
mvn clean -DskipTests compile
```

当前代码已验证可以在 JDK 21 下完成全模块编译：

```text
how_blog      SUCCESS
how_common    SUCCESS
how_article   SUCCESS
how_base      SUCCESS
how_user      SUCCESS
```

## 启动服务

在项目根目录先打包全部模块，再分别在三个终端运行可执行 JAR。三个服务使用相同的 `HOW_AUTH_JWT_SECRET`，并确认连接的是可安全使用的数据库和 Redis：

```bash
mvn package -DskipTests
```

```bash
java -jar how_user/target/how_user-1.0-SNAPSHOT.jar
java -jar how_base/target/how_base-1.0-SNAPSHOT.jar
java -jar how_article/target/how_article-1.0-SNAPSHOT.jar
```

开发时也可以先安装模块依赖，然后对单个业务模块执行 `spring-boot:run`：

```bash
mvn install -DskipTests
mvn -pl how_user spring-boot:run
```

## 主要接口

接口返回值统一使用 `com.liushao.entity.Result` 封装。以下路径默认不包含网关前缀。

### 用户服务 `http://localhost:9008`

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| `POST` | `/user/register` | 用户注册，成功不自动登录 |
| `POST` | `/user/login` | 用户登录，成功返回 Bearer JWT |
| `GET` | `/user/profile` | 查看本人资料，需要 Bearer JWT |
| `PUT` | `/user/profile` | 修改本人资料，需要 Bearer JWT |
| `POST` | `/user/password` | 修改密码，需要 Bearer JWT，成功撤销其他会话 |
| `POST` | `/user/refresh` | 刷新访问与刷新凭据 |
| `POST` | `/user/logout` | 退出当前会话 |
| `GET` | `/user/follow/{userId}` | 查询当前关注状态，需要 Bearer JWT |
| `PUT` | `/user/follow/{userId}` | 关注用户，需要 Bearer JWT |
| `DELETE` | `/user/follow/{userId}` | 取消关注，需要 Bearer JWT |
| `GET` | `/user/{userId}/fans/{page}/{size}` | 粉丝列表（公开） |
| `GET` | `/user/{userId}/follows/{page}/{size}` | 关注列表（公开） |

### 基础服务 `http://localhost:9001`

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| `GET` | `/label` | 查询全部标签 |
| `GET` | `/label/{id}` | 查询单个标签 |
| `POST` | `/label` | 新增标签，需要 Bearer JWT |
| `PUT` | `/label/{id}` | 修改标签，需要 Bearer JWT |
| `DELETE` | `/label/{id}` | 删除标签，需要 Bearer JWT |

### 文章服务 `http://localhost:9004`

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| `GET` | `/article` | 查询已发布文章 |
| `GET` | `/article/{articleId}` | 查询单篇文章（草稿仅作者可见） |
| `POST` | `/article` | 新增文章（草稿），需要 Bearer JWT |
| `PUT` | `/article/{articleId}` | 修改文章，需要作者 Bearer JWT |
| `PUT` | `/article/{articleId}/publish` | 发布文章，需要作者 Bearer JWT |
| `PUT` | `/article/{articleId}/unpublish` | 下架文章，需要作者 Bearer JWT |
| `PUT` | `/article/{articleId}/submit` | 提交审核，需要作者 Bearer JWT |
| `PUT` | `/article/{articleId}/approve` | 审核通过，需要管理员 Bearer JWT |
| `PUT` | `/article/{articleId}/reject` | 审核驳回，需要管理员 Bearer JWT |
| `PUT` | `/article/thumbup/{articleId}` | 文章点赞，需要 Bearer JWT |
| `DELETE` | `/article/{articleId}` | 删除文章，需要作者 Bearer JWT |
| `POST` | `/article/search/{page}/{size}` | 分页条件查询文章 |
| `GET` | `/comment` | 查询全部评论 |
| `GET` | `/comment/{id}` | 按 ID 查询评论 |
| `GET` | `/comment/article/{articleId}` | 按文章 ID 查询评论 |
| `POST` | `/comment` | 新增评论，需要 Bearer JWT |
| `PUT` | `/comment/{id}` | 修改评论，需要作者 Bearer JWT |
| `DELETE` | `/comment/{id}` | 删除评论，需要作者 Bearer JWT |
| `PUT` | `/comment/thumbup/{id}` | 评论点赞，需要 Bearer JWT |

评论 Controller 同时提供按评论 ID和按文章 ID查询评论：前者使用 `GET /comment/{id}`，后者使用 `GET /comment/article/{articleId}`。

文章请求和响应可使用 `labelIds` 字符串数组关联标签。新增时写入关系，编辑时省略保留、空数组清空；文章搜索可按 `labelIds` 筛选同时包含全部标签的文章。部署前需执行 `docs/mysql-article-label-migration.sql`，历史文章不会自动补标签关系。

## 即时通讯

项目已移除环信 IM、外部 SDK 和外部 CDN，`how_user` 内置基于 Spring WebSocket 的轻量 IM。启动用户服务后可以访问：

```text
http://localhost:9008/demo.html
http://localhost:9008/chatroom.html?token=<access-token>
```

WebSocket 地址为 `ws://localhost:9008/im?token=<access-token>`，HTTPS 环境自动使用 `wss`。先通过登录接口取得 token，再将其 URL 编码后连接。客户端发送 JSON：

```json
{"type":"join","room":"lobby"}
{"type":"message","room":"lobby","content":"你好"}
{"type":"message","to":"bob","content":"你好，Bob"}
```

当前支持在线状态、房间广播、全局广播和单聊。消息只保存在服务进程内存中，服务重启后丢失；生产环境建议将会话广播接入 Redis，并增加消息限流、消息持久化和离线消息；当前已包含握手与持续会话校验。音视频通话、文件传输、好友/群组管理不属于当前自建 IM 的范围。

## 开发说明

- 所有业务模块继承根目录 `pom.xml` 的依赖和 Java 21 编译配置。
- `how_common` 不单独启动，只作为公共依赖被其他模块引用。
- 数据访问使用 Spring Data JPA，当前 Spring Boot 2.7 使用 `javax.persistence`；升级 Spring Boot 3.x 时需要同步迁移至 `jakarta.persistence`。
- 配置文件中的 MySQL 驱动类使用 Connector/J 8 的 `com.mysql.cj.jdbc.Driver`。
- 当前访问令牌由 `how_user` 签发，其他服务使用同一 `HOW_AUTH_JWT_SECRET` 校验；刷新凭据单次轮换、退出撤销当前会话，修改密码撤销其他会话；三个服务和 IM 都检查数据库会话。
- 评论点赞已按 token 用户进行去重；MySQL 唯一关系表与原子计数负责最终一致性，Redis 仅保留提交后的短期兼容缓存。部署前需执行 `docs/mysql-thumbup-migration.sql`；IM 多实例广播仍未完成。
- 文章标签使用 `tb_article_label` 唯一关系表，关系变更与 `tb_label.count` 在同一事务内维护；部署前需执行 `docs/mysql-article-label-migration.sql`，不会重算历史标签计数。
- 当前 IM 使用 token 对应的用户 ID 作为身份，消息仍保存在进程内存中，仅适合开发测试。

## 构建验证

2026-10-27 已在 JDK 21、Maven 3.8.1 环境执行：

```bash
mvn clean test
mvn package -DskipTests
```

均为 `BUILD SUCCESS`。默认测试 325 项（315 通过、10 项外部验收跳过）；另显式执行 7 项真实 MySQL/Redis 验收全部通过。前端测试、生产构建和 Chromium 核心流程验收通过，临时数据已清理，原有记录数及统计值与验收前一致。具体范围见 [plan/progress.md](plan/progress.md)。

## 核心功能收尾（revision 23）

正文按转义纯文本保留换行显示；私密文章及其评论仅文章作者/管理员可读，发布不会解除私密限制。文章列表和分页由数据库统一按置顶、更新时间、发布时间、ID 倒序，前端沿用返回顺序。文章详情通过 `GET /user/follow/{userId}` 恢复关注状态，查询失败可重试。

当前契约三件套在 `docs/`，前端同目录有同步副本。验收记录见 `plan/progress.md`；本轮不需要新增 DDL，不批量转换历史正文或统计。评论树分页、通知、上传、密码找回和 IM 多实例留作后续增强。
