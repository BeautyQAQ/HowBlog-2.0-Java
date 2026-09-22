# AGENTS.md - 智能体编码规范

## 项目概述

本项目是基于 Spring Boot 2.7.18 和 Java 21 的博客后端，采用 Maven 多模块结构，提供用户登录、标签、文章、评论、评论点赞及轻量 WebSocket 即时通讯能力。

### 模块职责

| 模块 | 职责 | 启动类 | 默认端口 | 数据存储 |
| --- | --- | --- | --- | --- |
| `how_common` | 公共实体、分页结果与工具类 | 不单独启动 | - | - |
| `how_user` | 用户登录与 WebSocket 即时通讯 | `com.liushao.user.UserApplication` | `9008` | MySQL `how-blog-smaill` |
| `how_base` | 基础数据，当前包含标签管理 | `com.liushao.base.BaseApplication` | `9001` | MySQL `how-blog-smaill` |
| `how_article` | 文章、评论和评论点赞 | `com.liushao.article.ArticleApplication` | `9004` | MySQL `how-blog-smaill`、Redis |

所有模块使用 `com.liushao` 作为根包名。业务模块可依赖 `how_common`；避免在 `how_common` 中引入具体业务模块依赖，避免形成模块循环依赖。

## 构建与测试

项目要求 **JDK 21**。Java 与 Maven 的安装位置以执行设备为准；构建或测试前必须先确认可用工具，不假设固定路径，不修改系统环境变量或 Shell 启动文件，也不将设备绝对路径写入共享项目配置。

### Java 环境选择

1. 优先检查已有的 `JAVA_HOME` 以及 `PATH` 中的 `java`、`javac`，通过版本信息确认其为可用的 JDK 21。
2. 若未配置、不可用或版本不符，检查当前 IDE 的 Java 配置和本机常见安装目录。`/opt/java/jdk21`、`/usr/local/jdk21` 仅是候选示例；使用前必须确认目录存在且实际包含 JDK 21。
3. 需要切换 JDK 时，仅为当前命令设置 `JAVA_HOME`，必要时临时调整 `PATH`。不得修改系统环境变量或 Shell 启动文件。

### Maven 执行入口选择

1. 优先使用项目中的 Maven Wrapper，例如 `./mvnw` 或 Windows 下的 `mvnw.cmd`。当前仓库未提交 Wrapper，因此通常继续查找其他入口。
2. 没有 Wrapper 时，检查 `PATH` 中的 `mvn` 或 `maven`。若不可用，检查当前 IDE 或插件的 Maven 配置、安装目录及执行日志，定位其实际使用的 Maven 可执行文件。可参考 VS Code 用户或工作区设置中的 `maven.executable.path`。
3. 查找 VS Code 插件时，以当前实际运行的 VS Code、Remote SSH 或 code-server 环境的配置、扩展目录和用户数据目录为准；不得只检查默认的 `~/.vscode/extensions`。插件已安装不代表终端的 `PATH` 中存在 `mvn`，也不代表插件自带 Maven 可执行文件。
4. IntelliJ IDEA 可使用其 Maven 设置中确认的 Bundled Maven，定位对应的 `bin/mvn` 或 Windows 下的 `bin/mvn.cmd`，无需另行安装全局 Maven。
5. VS Code Red Hat Java 插件可能仅通过 M2E 内嵌 Maven 运行库支持项目，只有 JAR 而没有 `bin/mvn`。IDE 可以启动项目并不代表终端存在 Maven 命令，也不代表已执行过 `clean compile`。
6. 若只找到内嵌运行库，可在确认依赖完整且版本兼容后，于临时目录组装 classpath，再使用选定 JDK 的 `java` 调用 `org.apache.maven.cli.MavenCli`。该入口必须设置 `-Dmaven.multiModuleProjectDirectory` 为项目根目录，并先执行 `-v` 验证；不得固化插件版本、缓存编号或临时路径到项目中，也不得修改插件文件。
7. 无法找到可用的 JDK 或 Maven 时，说明已检查的位置和缺失项后，再向用户询问实际路径。不得仅因 `mvn` 不在 `PATH` 中就认定无法构建，也不得直接安装工具或修改全局配置。

### 执行与验证

以下命令中的 `mvn` 均表示已确认的 Maven 执行入口，可替换为 Wrapper、`PATH` 中的命令或 Maven 可执行文件的绝对路径。构建前先以该入口执行 `mvn -v`，确认 Maven 实际使用 Java 21；仅检查 `java -version` 不足以完成此验证。在 IDE 的 Maven 目标输入框中，仅填写命令中的目标和参数，例如 `clean compile` 或 `test -Dtest=ClassName`。

下面是 Bash 下临时指定 JDK 和 Maven 的示例。路径均为占位符，必须替换为当前设备已确认的实际路径；Windows 下使用对应的环境变量语法和 `mvn.cmd`。

```bash
JAVA_HOME="/actual/path/to/jdk21" "/actual/path/to/maven/bin/mvn" -v
JAVA_HOME="/actual/path/to/jdk21" "/actual/path/to/maven/bin/mvn" clean compile
```

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
├── config       # Spring、JPA、WebSocket 配置
├── controller   # REST 接口层
├── dao          # Spring Data JPA Repository 接口
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

- MySQL 持久化使用 Spring Data JPA；实体使用 `javax.persistence`，Repository 继续沿用当前 DAO 命名。
- 评论数据使用 JPA；评论点赞状态使用 Redis。点赞计数必须使用数据库端原子更新，并考虑外部存储不可用、重复操作和并发更新。
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

- Spring Boot 固定为 `2.7.18`，JPA 使用 `javax.persistence` 命名空间。升级至 Spring Boot 3.x 时必须整体迁移至 `jakarta.persistence`；禁止只调整 Spring Boot 版本。
- MySQL Connector/J 8 使用 `com.mysql.cj.jdbc.Driver`。保持配置与实际驱动版本一致。
- 当前评论点赞逻辑使用固定用户 ID `123`，在接入真实登录态前不能将其视为用户级鉴权。
- `CommentController` 中按评论 ID 与按文章 ID 查询存在同形路径风险；修改评论接口时先明确路径与参数语义，避免引入冲突映射。

## 提交前检查

1. 确认变更限定在所需模块，不覆盖用户已有的本地修改。
2. 执行适用的 Maven 编译或测试；无法执行时记录原因与未验证范围。
3. 检查配置、日志、测试数据和 Git 差异，确保不包含凭证、内部地址或无关生成文件。
4. 对接口、实体、持久化或配置变更，核对对应模块的启动影响和外部依赖影响。