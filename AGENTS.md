# AGENTS.md - 智能体编码规范

## 项目概述

本项目是基于 Spring Boot 2.7.18 和 Java 21 的博客后端，采用 Maven 多模块结构，提供用户登录、标签、文章、评论、评论点赞及轻量 WebSocket 即时通讯能力。

### 模块职责

| 模块 | 职责 | 启动类 | 默认端口 | 数据存储 |
| --- | --- | --- | --- | --- |
| `how_common` | 公共实体、分页结果与工具类 | 不单独启动 | - | - |
| `how_user` | 用户登录与 WebSocket 即时通讯 | `com.liushao.user.UserApplication` | `9008` | MySQL `how_user` |
| `how_base` | 基础数据，当前包含标签管理 | `com.liushao.base.BaseApplication` | `9001` | MySQL `how_base` |
| `how_article` | 文章、评论和评论点赞 | `com.liushao.article.ArticleApplication` | `9004` | MySQL `how_article`、MongoDB、Redis |

所有模块使用 `com.liushao` 作为根包名。业务模块可依赖 `how_common`；避免在 `how_common` 中引入具体业务模块依赖，避免形成模块循环依赖。

## 构建与测试

项目要求 **JDK 21**。当前仓库未提交 Maven Wrapper，执行命令前先确认可用的 JDK 21 与 Maven，不假设它们位于固定路径，也不修改系统环境变量或 Shell 启动文件。

1. 优先检查 `JAVA_HOME`、`PATH` 中的 `java`、`javac` 与 `mvn`。
2. 若终端中没有 Maven，检查当前 IDE 的 Maven 配置或安装目录，使用已确认可用的 Maven 可执行文件。
3. 使用所选 Maven 入口执行 `mvn -v`，确认 Maven 实际运行在 Java 21 上，再执行构建或测试。

在项目根目录执行以下命令：

```bash
# 编译全部模块
mvn clean compile

# 执行全部测试
mvn test

# 编译指定模块及其依赖
mvn -pl how_article -am compile

# 运行指定模块测试及其依赖
mvn -pl how_article -am test

# 启动独立服务
mvn -pl how_user -am spring-boot:run
mvn -pl how_base -am spring-boot:run
mvn -pl how_article -am spring-boot:run

# 打包时跳过测试
mvn clean package -DskipTests
```

修改后至少执行与变更范围匹配的 Maven 编译或测试。新增或修改业务行为时，应补充 JUnit 5 测试；提交前运行 `mvn test`。当前仓库没有现有测试源码，无法用测试通过替代对 MySQL、MongoDB、Redis 等外部依赖的运行验证。

## 代码组织与风格

### 包结构

业务模块沿用现有职责划分：

```text
com.liushao.<module>/
├── config       # Spring、MyBatis-Plus、WebSocket 配置
├── controller   # REST 接口层
├── dao          # MyBatis-Plus Mapper 接口
├── exception    # 全局异常处理
├── pojo         # 持久化或传输对象
├── repository   # MongoDB Repository
├── service      # 业务服务
└── im           # 即时通讯实现（仅 how_user）
```

遵循当前代码的命名方式：

- 控制器使用 `{Entity}Controller`，服务使用 `{Entity}Service`，数据访问接口使用 `{Entity}Dao`。
- Java 类使用 PascalCase，方法与字段使用 camelCase，常量使用全大写下划线。
- 优先通过构造器注入依赖；避免新增字段注入。
- 导入顺序为 Java 标准库、第三方库、`com.liushao.*` 项目代码；每组内按字母排序。
- 只为不直观的业务规则添加简短注释；不要为显而易见的赋值或调用添加注释。

### Web 与异常处理

- 控制器使用 Spring MVC 注解，保持资源路径、HTTP 方法与现有 REST 风格一致。
- 接口返回统一使用 `com.liushao.entity.Result`；成功与失败状态使用 `StatusCode` 中既有约定。
- 可预期的参数或业务异常应交由模块现有的 `BaseExceptionHandler` 统一处理；避免在控制器中吞掉异常或返回未封装的错误对象。
- 参数新增或修改时，应使用 Bean Validation 或明确的业务校验，并覆盖空值、边界值和不存在的资源。

### 数据访问

- MySQL 持久化继续使用 MyBatis-Plus 2.x 既有 API 与 Mapper/DAO 模式，不要在单个模块中混入不兼容的新版 API。
- 评论数据使用 Spring Data MongoDB；评论点赞状态使用 Redis。变更这些逻辑时需考虑外部存储不可用、重复操作和并发更新。
- 修改实体字段、查询条件或持久化逻辑时，同步检查对应数据库或集合结构；本项目未提供完整建表脚本，不能臆造或执行破坏性 DDL。
- ID 和分页相关实现应复用 `how_common` 的既有实体与工具，避免各模块复制实现。

### 即时通讯

- `how_user` 的 WebSocket IM 仅适合开发测试：客户端 URL 中的 `user` 参数只是展示身份，消息仅保存在进程内存。
- 不应将该身份识别、消息可靠性或重启后的消息保留视为生产级能力。涉及生产化改造时，应先设计鉴权、持久化、分布式广播和限流方案。

## 配置与安全

- 配置文件位于各业务模块的 `src/main/resources/application.yml`。当前文件可能含真实或历史环境的主机地址和明文凭证，不要在日志、文档、测试输出、提交信息或新文件中复制这些值。
- 不要提交新的密钥、密码、令牌或生产连接信息。新增本地配置时，优先使用环境变量、未跟踪的本地覆盖文件或脱敏示例配置，并更新 `.gitignore`（如有必要）。
- 改动端口、数据库、MongoDB 或 Redis 配置前，确认影响模块；不要因本地开发需要直接覆盖其他环境配置。
- 启动依赖外部服务的模块前，确认目标服务和数据属于可安全使用的环境。除非用户明确要求，不执行 DDL、数据删除、批量更新或远程写入操作。

## 已知兼容性约束

- Spring Boot 固定为 `2.7.18`，仍使用 `javax` 命名空间。升级至 Spring Boot 3.x 时必须协调迁移至 `jakarta`，并升级 MyBatis-Plus 与相关配置；禁止只调整 Spring Boot 版本。
- MyBatis-Plus 当前为 2.x，升级或替换时必须覆盖所有模块及配置。
- MySQL Connector/J 8 使用 `com.mysql.cj.jdbc.Driver`。保持配置与实际驱动版本一致。
- 当前评论点赞逻辑使用固定用户 ID `123`，在接入真实登录态前不能将其视为用户级鉴权。
- `CommentController` 中按评论 ID 与按文章 ID 查询存在同形路径风险；修改评论接口时先明确路径与参数语义，避免引入冲突映射。

## 提交前检查

1. 确认变更限定在所需模块，不覆盖用户已有的本地修改。
2. 执行适用的 Maven 编译或测试；无法执行时记录原因与未验证范围。
3. 检查配置、日志、测试数据和 Git 差异，确保不包含凭证、内部地址或无关生成文件。
4. 对接口、实体、持久化或配置变更，核对对应模块的启动影响和外部依赖影响。