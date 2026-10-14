# 认证与标签管理员运维

## 部署顺序

1. 确认目标库、备份和变更窗口；检查 `tb_user` 存量结构及用户 ID 语义。
2. 执行经过审核的 `mysql-auth-migration.sql`；新库还需先执行 `mysql-init.sql`。脚本不由应用自动执行，`IF NOT EXISTS` 不会修复同名异构表。
3. 基础服务需拥有 `tb_user_role` 和 `tb_user` 的读取权限，以及原有标签操作权限；不需要应用账号拥有角色写入权限。
4. 部署 AUTH-08B。没有管理员记录时所有账号的标签写入均拒绝，公开查询保持原状。缺表或角色查询故障返回脱敏 500，不降级放行。

## 初始授权

- 没有默认管理员，不使用首个登录账号或手机号猜测身份；先由负责人确认目标用户 ID，并只查询必要的 ID/昵称信息，不导出密码等字段。
- 运维使用独立权限账号，在事务内检查用户存在，再向 `tb_user_role(user_id, role)` 写入该 ID 和精确大写的 `ADMIN`。重复授权前检查已有记录，避免盲目忽略数据库错误。
- 使用参数化语句绑定目标 ID；核对影响行数和最终结果后提交，否则回滚。授权操作保留工单和操作人记录，不在应用日志记录连接凭据。
- 本轮开发没有执行任何真实账号管理员授权；`auth-test-*` 等集成测试标识不是可授权的真实账号。
- 应用没有角色设置接口；登录请求、标签请求或自行构造的 JWT 角色字段都不能赋予管理员权限。

## 撤回与验收

- 经授权的运维操作只撤回指定用户的 `ADMIN` 记录，不删除用户、不清空角色表；操作前核对目标，提交后用同一个有效 token 验证下一次标签写入返回 403。
- 管理员测试有效的新增/修改/删除应成功；无角色或已删除用户的遗留角色记录必须拒绝；普通用户不应获知目标标签是否存在。
- HTTP 401 表示认证失败，HTTP 403 表示权限不足，HTTP 500 可能为角色查询失败；500 不代表角色已被撤回，不能通过放宽权限排障。
- 当前角色验证位于标签服务写入口，每次重新查库；已通过校验的在途操作不保证被取消。文章/评论权限和 JWT 会话生命周期不受本切片影响。

## 验证范围

- MVC 测试覆盖管理员、普通用户、伪造请求字段、降权及数据库故障关闭；H2 JPA 切片验证实体映射、用户存在性关联和原生角色查询。
- AUTH-08A 已验证独立 MySQL 8 的认证表创建与基础事务；本切片未对真实账号执行授权/降权，也未在实际基础服务数据库上运行完整 JPA 启动验收。上线前应核对基础服务确实指向已迁移且包含用户数据的目标库。

## 认证限流部署（AUTH-10）

用户服务新增 Redis 依赖，默认开启，故障返回 503 而非放行。上线前必须配置并验证 Redis，不能把本地 H2/MVC 通过当作部署验收。既有 MySQL 配置未改，本轮没有连接或修改业务数据。

| 环境变量 | 默认值 | 含义 |
| --- | --- | --- |
| `HOW_AUTH_REDIS_HOST` / `HOW_AUTH_REDIS_PORT` | localhost / 6379 | 用户服务限流 Redis |
| `HOW_AUTH_REDIS_PASSWORD` | 空 | 通过部署密钥注入，不写入仓库或命令历史 |
| `HOW_AUTH_REDIS_DATABASE` / `HOW_AUTH_REDIS_SSL` | 0 / false | 数据库编号及 TLS；Redis Cluster 需使用数据库 0 并配置对应集群连接 |
| `HOW_AUTH_RATE_LIMIT_ENABLED` | true | 显式 false 仅供隔离测试或经审批的回退，不是故障自动降级 |
| `HOW_AUTH_RATE_LIMIT_KEY_PREFIX` | howblog:auth:rate:v1: | 同环境各实例相同，不同环境隔离 |
| `HOW_AUTH_RATE_LIMIT_WINDOW_SECONDS` | 60 | 1..3600，首次请求起固定窗口 |
| `HOW_AUTH_RATE_LIMIT_LOGIN_IP_LIMIT` | 20 | 每来源登录额度 |
| `HOW_AUTH_RATE_LIMIT_LOGIN_ACCOUNT_LIMIT` | 10 | 每账号登录额度，跨来源共享 |
| `HOW_AUTH_RATE_LIMIT_REGISTER_IP_LIMIT` | 10 | 每来源注册额度 |
| `HOW_AUTH_RATE_LIMIT_PASSWORD_IP_LIMIT` | 10 | 每来源修改密码额度 |
| `HOW_AUTH_RATE_LIMIT_REFRESH_IP_LIMIT` | 60 | 每来源刷新额度 |
| `HOW_AUTH_RATE_LIMIT_LOGOUT_IP_LIMIT` | 30 | 每来源退出额度，与登录/刷新独立 |

- 额度范围为 1..100000；Redis 连接和命令超时均为 1 秒。Lua 在一个键上原子计数并设置 TTL，拒绝不增加计数、不续期；固定窗口边界仍允许突发。
- 键仅包含维度和 HMAC-SHA256 摘要，密钥复用 JWT secret，账号作 NFKC、首尾空白及小写规范化；不把原始手机号、IP、密码或 token 写入 Redis 键/值及应用日志。所有用户实例必须使用相同的 secret、Redis、命名空间、阈值及窗口；改 secret/前缀、清空/淘汰 Redis 键会重置额度，配置变更需协调发布。
- 权限至少需要脚本执行/缓存及 GET、INCR、PTTL、PEXPIRE。容量需按窗口内不同来源/账号数评估，建议使用隔离的限流 Redis 和 noeviction，监控内存、拒绝数、503 及延迟；内存耗尽会关闭认证入口，不应自动绕过限流。
- 应用使用 `request.getRemoteAddr()`，不直接采信转发头。默认不启用转发头解析；需要真实代理来源时，先在可信入口覆盖外部头、限制直连来源，再配置和验证容器代理处理。否则反向代理/NAT 下会共享来源额度。
- 只保护五个认证 POST 入口（登录、注册、修改密码、刷新、退出），不限制 OPTIONS、WebSocket 或其他 REST。仍需网关请求体大小、全局/连接限流和告警；被盗凭据分布式滥用、不受控代理链及大规模拒绝服务不能靠本功能解决。
- 验收 429 的 Retry-After/CORS/no-store、账号跨 IP 共享、独立退出额度、Redis 断连后的 503，以及恢复后的请求。不要把 429/503 当作服务端退出成功。

## 用户注册部署

- `POST /user/register` 为公开接口，成功即写入用户表（雪花 ID、BCrypt 密码、注册时间，在线/粉丝/关注计数初始化为 0），不自动登录；注册额度由 `HOW_AUTH_RATE_LIMIT_REGISTER_IP_LIMIT`（默认 10）控制，429/503 与登录一致。
- 手机号唯一约束：部署前对目标库执行 [mysql-user-mobile-unique.sql](mysql-user-mobile-unique.sql)（可重复执行；执行前先确认无重复手机号，有重复需人工处理，不要直接删数据）。冲突由应用兜底返回 `20005`，同一手机号不会再产生重复记录；2026-10-14 已在测试环境库执行。
- 注册验收使用 gated `UserRegisterMySqlTest`（`HOW_AUTH_SERVICE_TEST_CONFIG` 指向用户服务配置，需绝对路径）：临时用户按主键清理；2026-10-14 在测试环境真实 MySQL 通过。未做浏览器/前端联调。

## 过期会话清理部署

| 环境变量 | 默认值 | 含义 |
| --- | --- | --- |
| `HOW_AUTH_CLEANUP_ENABLED` | false | 仅明确授权后开启 |
| `HOW_AUTH_CLEANUP_RETENTION_HOURS` | 24 | 绝对到期后的额外保留小时数，0..8760 |
| `HOW_AUTH_CLEANUP_SESSION_BATCH_SIZE` | 100 | 每轮候选会话上限，1..1000 |
| `HOW_AUTH_CLEANUP_TOKEN_BATCH_SIZE` | 100 | 每会话事务删除摘要上限，1..1000 |
| `HOW_AUTH_CLEANUP_INTERVAL_MS` | 600000 | 首次启动延迟及每轮结束后的等待间隔，须为正整数 |

1. 先确认目标库、备份、审计/保留要求和删除授权，核对原迁移中的到期索引、摘要 session_id 索引与外键；本轮无 DDL、无自动迁移，不执行既有测试或业务数据删除。
2. 清理资格以数据库 `UTC_TIMESTAMP(6)` 减保留窗口确定，不依赖 JVM 时区。会话绝对到期前保留全部历史摘要，即使已撤销；到期后的保留窗口只用于历史数据，不延长认证有效期。
3. 建议先在一个用户实例显式开启。每候选独立 15 秒事务，先锁会话行并复查期限，最多删除指定摘要数，剩余摘要后续轮次继续，无摘要才删会话；单事务失败回滚，停止本轮，下一轮重试。多个实例使用同一锁顺序避免重复删除，但每实例都有批次额度，不是集群全局删除上限。
4. 应用账号需在两个认证表拥有 DELETE 及原有读写权限。默认每轮最多 10000 条摘要、100 条会话，不是无限全表删除；如遇锁等待或积压，观察日志/数据库指标后调整。实际 MySQL 锁超时语义仍待隔离验收。
5. 关闭开关后重启实例可停调度，但不能恢复已删除记录；需要恢复时按备份流程处理。已删旧凭据的退出返回 401，须先同步 API revision 8。

调度池设为两个线程，减少清理与现有 IM 空闲检查互相阻塞；仍需监控连接池和数据库负载。日志仅输出聚合摘要删除数和固定失败提示，不记录会话 ID/凭据。

## 本轮验收边界

- H2 真实事务覆盖摘要分批、会话最终删除、保留边界、活跃/撤销会话保护、删除失败回滚和并发清理；调度测试覆盖默认关闭、数据库时间和故障重试。MVC 覆盖限流早于 JWT/JSON/密码查询、429/503、跨域重试头和伪造转发头。
- 真实 Redis 测试默认跳过，只在显式设置 `HOW_AUTH_REDIS_TEST_PORT` 后连接 `127.0.0.1` 指定端口。准备隔离的无密码本机 Redis 后运行 `mvn -pl how_user -am test -Dtest=AuthRateLimiterRedisTest -Dsurefire.failIfNoSpecifiedTests=false`；测试使用随机前缀、最多 30 秒 TTL，不执行 FLUSHDB，也不删除其他键。覆盖两实例并发额度、TTL 不续期及到期恢复。
- 本轮未找到可用 Redis 服务，未运行真实 Lua 或 MySQL 清理；旧 MySQL 测试授权不自动扩展为清理授权。正式启用前需补真实 Redis、MySQL 行锁/外键/回滚、多实例和浏览器验收。

## 评论点赞真实存储集成测试

评论点赞关系表、原子计数和 Redis 兼容缓存提供一条真实存储集成测试，默认跳过，只有显式设置 `HOW_ARTICLE_TEST_CONFIG` 指向配置文件才运行：

```bash
HOW_ARTICLE_TEST_CONFIG=/path/to/integration-test-config.yml \
  mvn -pl how_article -am test -Dtest=CommentThumbupMySqlRedisTest -Dsurefire.failIfNoSpecifiedTests=false
```

- 配置文件结构见 [integration-test-config.example.yml](integration-test-config.example.yml)：一个已执行 [mysql-thumbup-migration.sql](mysql-thumbup-migration.sql) 的隔离测试库，以及一个隔离 Redis 逻辑库。测试按应用默认的 JDK 序列化访问 Redis；值支持 `${ENV_VAR:default}` 占位符，避免把凭证写入文件。
- 覆盖场景：首次点赞恰好一次原子计数、同用户重复点赞拒绝、旧 `thumbup_` 键迁移只登记关系不重复计数、同一评论同用户 20 路并发恰好一次成功、10 个用户并发全部成功、评论不存在时不写入；同时断言关系表唯一键、计数和 `thumbup:v2:` 缓存。
- 数据边界：只创建 `thumbup-test-` 前缀的临时评论、随机用户 ID 和对应的 `thumbup_`/`thumbup:v2:` 键，成功和失败路径结束时都精确删除；不执行 DDL、不清库、不删除其他键，也不触碰既有评论和点赞计数。
- 跳过只表示当前环境没有提供隔离配置，不表示行为已验证；运行前确认目标库和 Redis 属于可安全写入的测试环境。

## 文章点赞迁移与真实存储集成测试

- 文章点赞使用唯一关系表 `tb_article_thumbup`；部署前对目标库执行 [mysql-article-thumbup-migration.sql](mysql-article-thumbup-migration.sql)（可重复执行），新库由 [mysql-init.sql](mysql-init.sql) 直接建表。2026-10-14 经用户授权在测试环境库执行。
- 历史 `tb_article.thumbup` 计数保留、不重算、不补关系；迁移脚本不写入或修改既有文章与计数。文章点赞仅对已发布文章生效，无取消点赞接口。
- gated `ArticleThumbupMySqlTest` 在真实 MySQL 上覆盖首次/重复、同用户 20 路并发恰好一次成功、10 用户并发全部成功、文章不存在与非已发布拒绝；临时文章与关系按主键精确清理，不执行 DDL、不清库。

```bash
HOW_ARTICLE_TEST_CONFIG=/absolute/path/to/how_article/src/main/resources/application.yml \
  mvn -pl how_article -am test -Dtest=ArticleThumbupMySqlTest -Dsurefire.failIfNoSpecifiedTests=false
```

## 服务配置环境变量（Phase 3）

三个服务的端口、MySQL 与跨域来源支持环境变量覆盖；未设置时使用 `application.yml` 中的默认值，默认值保持不变。

| 环境变量 | 默认值 | 含义 |
| --- | --- | --- |
| `HOW_USER_PORT` / `HOW_BASE_PORT` / `HOW_ARTICLE_PORT` | 9008 / 9001 / 9004 | 服务 HTTP 端口 |
| `HOW_USER_DB_URL` / `HOW_BASE_DB_URL` / `HOW_ARTICLE_DB_URL` | 当前连接串 | MySQL 连接串，含库名与连接参数 |
| `HOW_USER_DB_USER` / `HOW_BASE_DB_USER` / `HOW_ARTICLE_DB_USER` | `root` | MySQL 用户名 |
| `HOW_USER_DB_PASSWORD` / `HOW_BASE_DB_PASSWORD` / `HOW_ARTICLE_DB_PASSWORD` | 当前密码 | MySQL 密码 |
| `HOW_ARTICLE_REDIS_HOST` / `HOW_ARTICLE_REDIS_PORT` / `HOW_ARTICLE_REDIS_PASSWORD` / `HOW_ARTICLE_REDIS_DATABASE` | 当前 Redis 配置 | 文章服务点赞缓存 Redis |
| `HOW_CORS_ALLOWED_ORIGINS` | `*` | REST 与 WebSocket 允许的跨域来源，逗号分隔；空白按 `*` 处理 |

- 用户服务的 `HOW_AUTH_REDIS_*`、认证限流与清理变量见上文各节。
- 跨域默认保持允许所有来源；收紧来源时 REST 与 WebSocket 共用同一变量，部署后需验证预检请求和浏览器直连。
- 使用环境变量或未跟踪的本地覆盖文件调整配置，不要为本地开发直接覆盖仓库中的其他环境配置。