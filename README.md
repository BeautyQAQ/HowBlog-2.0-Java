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
- Maven 3.9 或更高版本
- MySQL 8.x
- Redis

当前工程没有提交 Maven Wrapper。如果系统没有配置 `mvn` 命令，可以直接使用本机 Maven，或在项目根目录执行 Maven Wrapper 生成命令后再使用 Wrapper。

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

可以分别进入模块目录启动：

```bash
mvn -pl how_user spring-boot:run
mvn -pl how_base spring-boot:run
mvn -pl how_article spring-boot:run
```

也可以直接运行对应启动类：

```bash
java -cp how_user/target/classes com.liushao.user.UserApplication
java -cp how_base/target/classes com.liushao.base.BaseApplication
java -cp how_article/target/classes com.liushao.article.ArticleApplication
```

实际运行时推荐使用 Maven 的 `spring-boot:run`，因为它会自动处理模块依赖和运行时 classpath：

```bash
mvn -pl how_user spring-boot:run
```

## 主要接口

接口返回值统一使用 `com.liushao.entity.Result` 封装。以下路径默认不包含网关前缀。

### 用户服务 `http://localhost:9008`

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| `POST` | `/user/login` | 用户登录，请求体为用户 JSON |

### 基础服务 `http://localhost:9001`

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| `GET` | `/label` | 查询全部标签 |
| `GET` | `/label/{id}` | 查询单个标签 |
| `POST` | `/label` | 新增标签 |
| `PUT` | `/label/{id}` | 修改标签 |
| `DELETE` | `/label/{id}` | 删除标签 |

### 文章服务 `http://localhost:9004`

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| `GET` | `/article` | 查询全部文章 |
| `GET` | `/article/{articleId}` | 查询单篇文章 |
| `POST` | `/article` | 新增文章 |
| `PUT` | `/article/{articleId}` | 修改文章 |
| `DELETE` | `/article/{articleId}` | 删除文章 |
| `POST` | `/article/search/{page}/{size}` | 分页条件查询文章 |
| `GET` | `/comment` | 查询全部评论 |
| `GET` | `/comment/{id}` | 按 ID 查询评论 |
| `GET` | `/comment/article/{articleId}` | 按文章 ID 查询评论 |
| `POST` | `/comment` | 新增评论 |
| `PUT` | `/comment/{id}` | 修改评论 |
| `DELETE` | `/comment/{id}` | 删除评论 |
| `PUT` | `/comment/thumbup/{id}` | 评论点赞 |

评论 Controller 同时提供按评论 ID和按文章 ID查询评论：前者使用 `GET /comment/{id}`，后者使用 `GET /comment/article/{articleId}`。

## 即时通讯

项目已移除环信 IM、外部 SDK 和外部 CDN，`how_user` 内置基于 Spring WebSocket 的轻量 IM。启动用户服务后可以访问：

```text
http://localhost:9008/demo.html
http://localhost:9008/chatroom.html?user=alice
```

WebSocket 地址为 `ws://localhost:9008/im?user=alice`，HTTPS 环境自动使用 `wss`。客户端发送 JSON：

```json
{"type":"join","room":"lobby"}
{"type":"message","room":"lobby","content":"你好"}
{"type":"message","to":"bob","content":"你好，Bob"}
```

当前支持在线状态、房间广播、全局广播和单聊。消息只保存在服务进程内存中，服务重启后丢失；生产环境建议将会话广播接入 Redis，并增加登录态校验、限流、消息持久化和离线消息。音视频通话、文件传输、好友/群组管理不属于当前自建 IM 的范围。

## 开发说明

- 所有业务模块继承根目录 `pom.xml` 的依赖和 Java 21 编译配置。
- `how_common` 不单独启动，只作为公共依赖被其他模块引用。
- 数据访问使用 Spring Data JPA，当前 Spring Boot 2.7 使用 `javax.persistence`；升级 Spring Boot 3.x 时需要同步迁移至 `jakarta.persistence`。
- 配置文件中的 MySQL 驱动类使用 Connector/J 8 的 `com.mysql.cj.jdbc.Driver`。
- 当前评论点赞逻辑使用固定用户 ID `123`，实际接入登录态前不能视为完整的用户级鉴权方案。
- 当前 IM 使用连接 URL 中的用户名作为展示身份，仅适合开发测试，不能替代登录认证。

## 构建验证

本项目已在 JDK 21、Maven 3.9.6 环境执行以下命令验证：

```bash
mvn clean -DskipTests compile
```

结果为 `BUILD SUCCESS`。项目当前未提供完整的自动化测试用例，编译通过不代表外部 MySQL 和 Redis 配置已经可用。