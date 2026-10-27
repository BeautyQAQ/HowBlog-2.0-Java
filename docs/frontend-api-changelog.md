# 前端接口契约变更记录

本文档采用追加方式维护，不删除或改写已发布的历史条目。最新一条记录的 `revision` 必须与 [frontend-api-status.json](frontend-api-status.json) 中的当前 revision 一致，历史条目保留各自的 revision。

## 记录格式

后续接口变更按以下格式追加：

```markdown
## Revision N - API-YYYYMMDD-NNN

- 日期：YYYY-MM-DD
- 类型：新增 / 修改 / 删除 / 修复 / 协议变更
- 破坏性变更：是 / 否
- 影响范围：服务和接口路径
- 变更内容：调用方式、请求体、响应体、错误码或行为变化
- 前端动作：需要修改的页面、状态管理、类型定义或请求封装
- 发布状态：已发布 / 待发布
```

## Revision 1 - API-20260922-001

- 日期：2026-09-22
- 类型：基线建立
- 破坏性变更：否
- 影响范围：用户服务、基础服务、文章服务和 WebSocket 即时通讯
- 变更内容：建立当前 REST 和 WebSocket 接口契约，记录统一响应体、数据模型、服务端口、分页搜索规则和已知限制。
- 前端动作：首次接入时读取 [frontend-api.md](frontend-api.md)，保存已处理 revision `1`。
- 已知阻塞项：`GET /comment/{id}` 与按文章查询评论的 `GET /comment/{articleId}` 是冲突映射，文章维度评论查询暂不可作为稳定接口使用。
- 发布状态：已发布

## Revision 2 - API-20260922-002

- 日期：2026-09-22
- 类型：修复
- 破坏性变更：否
- 影响范围：文章服务 `GET /comment/article/{articleId}`
- 变更内容：将按文章 ID 查询评论的路径从冲突的变量模板改为明确的 `/comment/article/{articleId}`；按评论 ID 查询继续使用 `GET /comment/{id}`。
- 前端动作：将文章评论列表请求切换为 `GET /comment/article/{articleId}`，并保存已处理 revision `2`。
- 发布状态：已发布。

## Revision 3 - API-20260922-003

- 日期：2026-09-22
- 类型：修复
- 破坏性变更：否
- 影响范围：用户服务 `POST /user/login`
- 变更内容：登录请求只使用 `mobile` 和 `password`；认证失败统一返回状态码 `20002`；成功响应改为脱敏资料，仅返回 `id`、`mobile`、`nickname` 和 `avatar`；存量明文密码在成功登录时升级为 BCrypt。当前仍不签发 token。
- 前端动作：登录请求不要依赖其他用户字段；按 `flag` 或 `code=20002` 处理认证失败；不要读取登录响应中的密码字段；保存已处理 revision `3`。
- 发布状态：已发布。

## Revision 4 - API-20260922-004

- 日期：2026-09-22
- 类型：协议变更
- 破坏性变更：是
- 影响范围：用户登录、标签写接口、文章写接口、评论写接口和 WebSocket IM
- 变更内容：登录成功响应新增 Bearer JWT、`tokenType` 和 `expiresIn`；文章、评论、标签的写接口需要 `Authorization: Bearer <token>`；文章和评论创建时使用 token 用户 ID，修改/删除时校验作者归属，评论编辑仅允许修改正文；评论点赞改为按真实 token 用户去重；WebSocket 连接从 `/im?user=...` 改为 `/im?token=...`，连接身份由 token 决定。缺少或无效 token 的 REST 受保护请求返回 HTTP 401 和业务码 20003。
- 前端动作：保存登录响应中的访问 token；为所有受保护写请求附加 `Authorization`；移除请求体中作为身份依据的 `userid`；将 WebSocket URL 切换为 `/im?token=<url-encoded-token>`；处理 HTTP 401 和业务码 20003；保存已处理 revision `4`。
- 已知限制：当前没有刷新/退出登录接口；标签暂未区分管理员角色；WebSocket token 暂通过 URL 传递；Redis 与 MySQL 跨存储补偿仍待实现。
- 发布状态：已发布。

## Revision 5 - API-20260922-005

- 日期：2026-09-22
- 类型：修复 / 协议变更
- 破坏性变更：是；contract version 升至 `3.0.0`。
- 影响范围：三个服务的 REST 控制器错误响应；`POST/PUT /article`、`POST /article/search/{page}/{size}`、`POST/PUT /comment`、`POST/PUT /label`；单条文章/评论/标签查询、标签修改/删除、`PUT /comment/thumbup/{id}`。PUT 的实际资源路径包含原有 ID 参数，路由不变。
- 变更内容：异常响应统一封装，坏 JSON、请求体缺失和参数校验失败返回 HTTP 400；未知异常返回 HTTP 500 和固定脱敏消息，不再回传底层异常原文。保留 MVC 的 405、415 等状态。新增文章必填非空白标题/正文，评论必填非空白文章 ID/正文，标签必填非空白名称；编辑保留部分更新语义，相关文本如提供不能为空白，标签计数不能为负数。文章分页限制为 `page >= 1`、`1 <= size <= 100`，不再自动纠正非法值。单条查询、标签修改/删除和评论点赞对象不存在返回 HTTP 404、业务码 20001、消息“资源不存在”。
- 保持不变：认证失败 HTTP 401/20003，文章/评论非作者或缺失资源修改/删除的 HTTP 200/20003 合并响应，合法登录 JSON 缺少凭据的 HTTP 200/20002，重复点赞业务码 20004；WebSocket 协议不变。
- 前端动作：提交前校验上述字段；限制分页输入；统一请求封装处理 HTTP 400/404/500 的 `Result`，不要再依赖不存在资源返回成功或底层异常文本；保留原有 401 登录失效和业务码判断；保存已处理 revision `5`。
- 验证：JDK 21 下全模块 `mvn clean test` 通过，共 74 项测试，其中新增 55 项 MVC 切片测试；未执行真实 MySQL/Redis 联调。
- 发布状态：仓库 `docs/` 三件套已同步；前端共享路径或发布 URL 尚未确认，外部发布待完成。

## Revision 6 - API-20260922-006

- 日期：2026-09-22
- 类型：协议变更
- 破坏性变更：是，contract version `4.0.0`。
- 影响范围：`POST /label`、`PUT /label/{id}`、`DELETE /label/{id}`。
- 变更内容：标签写操作新增数据库 `ADMIN` 角色校验，要求对应用户仍存在；输入校验通过后先授权再访问标签。未授权返回 HTTP 403、业务码 20003、消息“无权执行此操作”；数据库角色查询异常返回 HTTP 500/20001，不放行写入。每次写请求重新查库，角色不从请求体或 token 字段采信。
- 保持不变：公开标签查询、401 认证失败、400 输入校验、管理员操作缺失资源的 404、登录响应，以及文章/评论作者权限；刷新和退出接口尚未实现。
- 前端动作：将 403 作为权限不足展示，不触发重新登录循环；不要将登录成功视为标签管理授权；不得提交角色字段尝试提权；保存 revision `6`。
- 部署动作：先审核并执行附加认证表迁移，再部署基础服务；管理员由运维显式授权，见 `admin-operations.md`，没有默认管理员。
- 发布状态：工作区三件套已同步，待本切片提交推送后由前端读取后端 GitHub 仓库；revision 5 已在此前提交推送。

## Revision 7 - API-20260922-007

- 日期：2026-09-22
- 类型：协议变更 / 新增
- 破坏性变更：是，contract version `5.0.0`。
- 影响范围：`POST /user/login`、新增 `POST /user/refresh` 和 `POST /user/logout`；三个业务服务使用访问 token 的 REST 请求；`/im` 握手及连接后的收发。
- 变更内容：登录创建 7 天绝对期限的 MySQL 会话，响应新增 `refreshToken` 和 `refreshExpiresIn`；访问 JWT 绑定 `sid`，最多有效 1800 秒且受会话期限限制。刷新只接收 JSON 刷新凭据，单次轮换并返回全套新凭据；已消费凭据重放撤销该会话。退出用已知刷新凭据撤销当前会话，重复退出幂等，不影响其他设备。
- 认证变化：旧的无 `sid` token 不再有效；REST 每次查询会话和用户存在性，故障不放行。IM 握手、入站/出站和定期空闲检查均验证会话，到期或撤销关闭 1008，数据库异常关闭 1011；同用户新连接关闭旧连接 1000。刷新不更新旧连接 token，前端需重连。
- 前端动作：升级后强制重新登录；原子保存新访问/刷新凭据；刷新必须单飞并跨标签页协调，结果不确定时重登而非重试旧凭据；刷新/退出无需访问 token；成功退出清理本地凭据和连接，500 不表示退出完成。保存 revision `7`。
- 部署动作：先审核认证表迁移，确保三服务访问一致的用户/会话库，协调升级所有实例，避免旧实例绕过撤销检查；不得自动授予管理员。
- 验证：JDK 21 下全模块 `mvn clean test` 116 项通过、2 项外部数据库测试默认跳过；新增完整 H2 MVC/JPA 登录、刷新、退出、签发失败回滚及 IM 撤销测试。真实浏览器、多实例和完整 MySQL 请求链路未在本轮执行。
- 发布状态：工作区三件套已同步，待本切片提交推送到后端 GitHub 仓库供前端读取；revision 6 已发布。过期会话清理和全设备退出尚未实现。

## Revision 8 - API-20260922-008

- 日期：2026-09-22
- 类型：协议变更 / 安全防护
- 破坏性变更：是，contract version `6.0.0`；新增认证拒绝响应，旧凭据退出幂等性受清理保留期限限制。
- 影响范围：`POST /user/login`、`POST /user/refresh`、`POST /user/logout`，认证端点 CORS 暴露响应头；会话和刷新摘要保留行为。
- 变更内容：Redis 原子固定窗口限流默认开启，60 秒窗口下登录 IP 20 次、登录账号 10 次、刷新 IP 60 次、退出 IP 30 次，各维度独立。IP 限流先于 JWT 与请求体解析；账号计数先于密码查询。超额返回 429/20001，Redis 故障返回 503/20001，不进入认证业务；响应带 no-store 和跨域可读 Retry-After。
- 清理行为：默认关闭，启用后每 10 分钟最多扫描 100 个会话，每会话每事务最多删 100 条摘要，无摘要才删会话。使用数据库 UTC，以绝对到期加默认 24 小时为保留边界；提前撤销不提前清理。旧摘要被删除后退出返回 401/20003，不保证永久幂等。
- 前端动作：按 Retry-After 等待，429/503 不触发凭据失效或无限刷新，退出失败不得显示服务端撤销成功；仅明确收到拒绝响应后可单飞有限重试，网络结果不确定仍需重登。保存 revision `8`。
- 部署动作：为用户服务配置独立的 Redis 连接环境变量，各实例共享 Redis、键前缀、JWT secret 和阈值；验证可信代理来源。清理需单独授权、确认备份/保留策略和 DELETE 权限后开启，默认不删除现有数据。详见 `admin-operations.md`。
- 验证：JDK 21 全模块 `mvn clean test` 138 项通过、3 项外部测试跳过；新增 22 项默认测试，另新增显式本机 Redis 验收入口，默认跳过。未执行真实 Redis Lua、多实例网络或 MySQL 清理验收，无远程写入或 DDL。
- 发布状态：三件套已随后端提交 `79c4c61` 推送至 `origin/master`。2026-09-23 检查前端仓库的 `backend-api-sync.json` 仍为 revision `7`，尚未确认消费 revision `8`；前端工作区存在未提交改动，本次未修改前端文件。

## Revision 9 - API-20260924-009

- 日期：2026-09-24
- 类型：修复 / 数据一致性
- 破坏性变更：否；contract version `6.0.1`。
- 影响范围：文章服务 `PUT /comment/thumbup/{id}`，文章服务数据库迁移。
- 变更内容：评论点赞以 MySQL `tb_comment_thumbup` 的 `(commentid, userid)` 唯一关系为最终去重依据，并与评论计数更新处于同一事务；Redis 仅在事务提交后写入短期 `thumbup:v2:` 缓存。Redis 不可用、缓存写入失败或旧缓存残留不再改变点赞结果。旧版 `thumbup_<userId>_<commentId>` 键首次命中时只补登记关系，不重复增加计数。新增表使用评论外键级联删除。
- 前端动作：无需修改请求路径、请求体、认证头或成功/重复/不存在的响应处理；保存已处理 revision `9`。部署前执行 [mysql-thumbup-migration.sql](mysql-thumbup-migration.sql)，不要在未迁移关系表的旧实例上启用新文章服务。
- 验证：`how_article` 及 `how_common` 测试 43 项通过，覆盖正常、重复、缺失、Redis 不可用、数据库失败、旧键迁移和控制器路径；真实 MySQL/Redis 迁移与并发 HTTP 联调尚未执行。
- 发布状态：工作区已同步，待后端提交发布；前端仅需更新同步指针，无代码改动。

## Revision 10 - API-20261013-010

- 日期：2026-10-13
- 类型：配置能力新增
- 破坏性变更：否；contract version `6.1.0`。
- 影响范围：三个服务的跨域来源配置与服务端口配置；WebSocket 握手使用同一跨域来源配置，连接参数不变。
- 变更内容：跨域来源由环境变量 `HOW_CORS_ALLOWED_ORIGINS`（逗号分隔）配置，未设置时保持允许所有来源（`*`）；用户服务继续在响应中暴露 `Retry-After`。三个服务端口分别由 `HOW_USER_PORT`、`HOW_BASE_PORT`、`HOW_ARTICLE_PORT` 覆盖，默认 9008/9001/9004 不变；MySQL/Redis 连接新增 `${HOW_*:现值}` 环境变量覆盖，默认值不变。
- 前端动作：无需修改代码，默认行为与之前一致；部署收紧来源后，前端必须使用被允许的来源访问服务，否则预检请求和实际请求会被拒绝。
- 验证：新增 `CorsOriginsTest` 3 项、`CorsConfigurationTest` 3 项（显式来源的预检/实际请求与 `Retry-After` 暴露）、三个服务的默认全开断言、真实 WebSocket 跨源握手测试 1 项；全量 `mvn test` 155 项、0 失败、0 错误、4 项外部测试跳过；gated 点赞真实存储测试在配置包装后复跑通过（1 项）。
- 发布状态：待提交发布；前端仅需更新同步指针。

## Revision 11 - API-20261013-011

- 日期：2026-10-13
- 类型：可观测性与部署能力新增
- 破坏性变更：否；contract version `6.2.0`。
- 影响范围：三个服务的全部 HTTP 响应头与跨域暴露头；新增 `GET /actuator/health`；三个服务的容器化部署方式（接口路径、请求体、认证和业务行为不变）。
- 变更内容：每个 HTTP 响应新增 `X-Request-Id`，值为该请求的关联 ID，同一个 ID 也写入服务端日志（MDC `requestId`）；客户端可自带该请求头，服务端只接受 1 到 64 位的字母、数字、`-`、`_`、`.`，不符合要求或未提供时生成新 ID，控制字符会被过滤且不会被回显。三个服务的跨域响应新增暴露 `X-Request-Id`，用户服务继续暴露 `Retry-After`。三个服务新增无需认证的 `GET /actuator/health`，返回 `{"status":"UP"}`（依赖异常时为 `DOWN`），不使用 `Result` 响应体，不返回数据库、Redis 等依赖细节，也不受认证和业务限流影响。新增三个服务共用的镜像构建文件与 `docker-compose.yml`，可在空环境拉起隔离的 MySQL、Redis 和三个服务；服务端口、跨域来源和数据源仍由既有环境变量配置。
- 前端动作：无需修改现有请求逻辑，成功与失败响应结构不变；排查问题时可以读取响应头 `X-Request-Id` 并记录到自己的日志，也可以自带该请求头关联前后端日志，但这不是必需项。不要把 `/actuator/health` 当作业务接口或依赖其响应结构。保存 revision `11`。
- 部署动作：容器化编排使用 `.env` 中的本地口令，不连接任何既有环境的数据；MySQL 初始化脚本只在数据卷为空的首次启动时执行。会话清理保持默认关闭。
- 验证：JDK 21 全模块 `mvn test` 159 项、0 失败、0 错误、4 项外部存储测试默认跳过；新增 `RequestCorrelationFilterTest` 4 项（生成的 ID 在过滤器链中与日志上下文一致、伪造的含换行请求头不被回显）；`CorsConfigurationTest` 更新为断言 `X-Request-Id` 暴露；接口文档一致性与敏感信息扫描脚本通过。镜像构建、`docker compose up` 实际启动和 NAS 主机上的 Docker 验证尚未执行（本机 Docker 守护进程不可用，NAS 主机 SSH 认证未建立），真实浏览器与全链路联调也未执行。
- 发布状态：已随后端提交 `bb098e3` 推送至 `origin/master`；前端仅需更新同步指针，无代码改动。

## Revision 12 - API-20261014-012

- 日期：2026-10-14
- 类型：能力新增
- 破坏性变更：否；contract version `6.3.0`。
- 影响范围：用户服务新增 `POST /user/register`；认证限流新增注册来源 IP 维度；注册写入用户表。
- 变更内容：新增公开注册接口，请求体为 `mobile`（11 位、以 `1` 开头）、`password`（8 到 64 个字符）、可选 `nickname`（最长 100，去除首尾空白）。服务端生成用户 ID、立即保存 BCrypt 哈希、写入注册时间，在线/粉丝/关注计数初始化为 0；不自动登录。手机号已存在（含历史数据中同一手机号多条记录）返回 HTTP 200、`code: 20005`、`message: "手机号已注册"`；参数不符返回 HTTP 400/20001。注册入口纳入认证限流，新增来源 IP 维度，默认每 60 秒 10 次，可用 `HOW_AUTH_RATE_LIMIT_REGISTER_IP_LIMIT` 调整；429/503 行为与登录一致。
- 前端动作：可以接入注册表单：按 `flag`/`code` 分流，成功（20000）后调用登录接口获取凭据；`20005` 提示手机号已存在，引导登录或换号；`400/20001` 按参数错误提示；`429` 按 `Retry-After` 等待后重试。不要把注册成功当作已登录，也不要在本地日志或错误上报中保存密码。保存 revision `12`。
- 部署动作：无需新迁移；用户表没有手机号唯一索引，去重为应用层查询，如需数据库级唯一约束须先核对历史重复数据并单独评审 DDL。注册限流变量见 [admin-operations.md](admin-operations.md)。
- 验证：新增 `UserServiceTest` 3 项、`UserControllerTest` 8 项（成功、重复、5 组非法参数、注册限流先于业务解析）；gated `UserRegisterMySqlTest` 在测试环境真实 MySQL 上通过（BCrypt 与计数断言、重复与非法输入拒绝、临时用户按主键删除无残留）；全量 `mvn test` 171 项、0 失败、0 错误、5 项外部存储测试默认跳过。真实浏览器与前端联调未执行。
- 发布状态：已随后端提交 `1c03b70` 推送至 `origin/master`；前端可接入注册表单，无破坏性改动。

## Revision 13 - API-20261014-013

- 日期：2026-10-14
- 类型：能力新增
- 破坏性变更：否；contract version `6.4.0`。
- 影响范围：用户服务新增 `GET /user/profile` 与 `PUT /user/profile`；本人资料读取与修改。
- 变更内容：新增需认证的资料接口。`GET /user/profile` 返回本人资料安全视图（`id`、`mobile`、`nickname`、`sex`、`avatar`、`email`、`birthday`、`regdate`、`updatedate`、`lastdate`、`online`、`interest`、`personality`、`fanscount`、`followcount`，不含密码）。`PUT /user/profile` 支持部分更新 `nickname`、`avatar`、`sex`、`birthday`、`email`、`interest`、`personality`：未提供或为 `null` 的字段保留原值；字符串字段不能为纯空白并在保存前去除首尾空白；`email` 需为合法邮箱格式；`id`、`mobile`、`password`、计数与时间统计字段不可修改（被忽略）。成功返回 `data` 为修改后的完整资料；校验失败 400/20001；token 无效或用户不存在 401/20003。`updatedate` 由服务端在每次成功修改时更新。
- 前端动作：可以接入个人资料页：进入时用 `GET /user/profile` 加载，提交时用 `PUT /user/profile` 只发送变更字段，成功后用响应 `data` 覆盖本地资料。不要尝试通过该接口修改手机号或密码；头像字段是 URL 字符串，后端不提供上传。保存 revision `13`。
- 部署动作：无需迁移或新配置；资料接口不参与认证限流，仍受既有 token 会话校验保护。
- 验证：新增 `UserProfileTest` 6 项（认证、脱敏读取、部分更新、不可修改字段忽略、5 组非法输入 400、空更新）、`UserServiceTest` 3 项；gated `UserProfileMySqlTest` 在测试环境真实 MySQL 上通过（注册临时用户后读取与部分更新，按主键删除，DBX 复核 `profile-test` 残留 0）；全量 `mvn test` 181 项、0 失败、0 错误、6 项外部存储测试默认跳过。真实浏览器与前端联调未执行。
- 发布状态：已随后端提交 `e6a2447` 推送至 `origin/master`；前端可接入资料页，无破坏性改动。

## Revision 14 - API-20261014-014

- 日期：2026-10-14
- 类型：修复 / 数据一致性
- 破坏性变更：否；contract version `6.4.1`。
- 影响范围：`POST /user/register` 的并发去重保证；用户表新增手机号唯一约束。
- 变更内容：用户表新增唯一索引 `uk_tb_user_mobile`（取代原非唯一索引 `idx_tb_user_mobile`）。应用层查重逻辑不变；并发注册同一手机号时由数据库约束兜底，返回 HTTP 200、`code: 20005`、`message: "手机号已注册"`，不再返回 500，也不会产生重复记录。其它数据完整性异常仍返回脱敏 HTTP 500。
- 前端动作：无需修改；`20005` 的含义与处理方式不变。保存 revision `14`。
- 部署动作：部署新版本前对目标库执行 [mysql-user-mobile-unique.sql](mysql-user-mobile-unique.sql)（可重复执行，执行前先确认无重复手机号）；新库由初始化脚本直接创建唯一索引。2026-10-14 已在测试环境库执行。
- 验证：新增 `UserControllerTest` 2 项（数据库唯一约束冲突返回 20005、无关完整性异常保持脱敏 500）；全量 `mvn test` 183 项、0 失败、0 错误、6 项外部存储测试默认跳过。NAS Docker 端到端验证（2026-10-14）：五容器全部 healthy、三服务健康端点 UP；注册成功 20000、重复注册 20005、非法输入 400；20 路并发注册同一手机号恰好 1 次成功、19 次重复、0 次异常，库内该手机号仅 1 条记录；登录、资料读取（脱敏）、资料修改、退出全部通过；`X-Request-Id` 原样回显；CORS 预检暴露 `Retry-After` 与 `X-Request-Id`；库内确认 `uk_tb_user_mobile` 唯一索引与 BCrypt 密码落库。验证栈与测试数据已清理。
- 发布状态：已随后端提交 `86f887a` 推送至 `origin/master`；前端无需修改。

## Revision 15 - API-20261014-015

- 日期：2026-10-14
- 类型：能力新增
- 破坏性变更：否；contract version `6.5.0`。
- 影响范围：用户服务新增 `POST /user/password`；认证限流新增修改密码来源 IP 维度；成功后撤销该用户其他会话。
- 变更内容：新增需认证的修改密码接口，请求体为 `oldPassword` 与 `newPassword`（8 到 64 个字符，不能为纯空白）；旧密码错误返回 HTTP 200、`code: 20002`、`message: "旧密码不正确"`，参数不符返回 400/20001，未认证 401/20003。成功返回 HTTP 200、`code: 20000`、`message: "修改成功"`、`data: null`；服务端只保存 BCrypt 哈希，未强制新旧密码不同。成功后该用户在其他设备的所有会话立即撤销（访问 token 与刷新凭据同时失效，IM 连接随后以 `1008` 关闭），发起修改的当前会话保持有效。修改密码纳入认证限流，新增来源 IP 维度，默认每 60 秒 10 次，可用 `HOW_AUTH_RATE_LIMIT_PASSWORD_IP_LIMIT` 调整。
- 前端动作：可以接入修改密码表单（旧密码、新密码、确认密码一致性由前端校验）：成功后提示其他设备需要重新登录，当前设备无需重新登录、继续使用现有凭据；`20002` 表示旧密码错误；`429` 按 `Retry-After` 等待后重试。保存 revision `15`。
- 部署动作：无需迁移或新配置；修改密码限流变量见 [admin-operations.md](admin-operations.md)。
- 验证：新增 `UserPasswordTest` 4 项（认证、旧密码错误、非法新密码、成功撤销其他会话并保留当前会话）、`UserServiceTest` 3 项、限流参数化覆盖 `PASSWORD_IP`；gated `UserPasswordMySqlTest` 在测试环境真实 MySQL 上通过（双会话、改密后其他会话撤销、当前会话保留、旧密码失效、新密码可登录、临时数据按主键清理）；全量 `mvn test` 192 项、0 失败、0 错误、7 项外部存储测试默认跳过。NAS Docker 端到端验证（2026-10-14）：五容器 healthy、三健康端点 UP；双会话改密后其他会话访问与刷新均 401、当前会话保持 200；旧密码登录 20002、新密码登录 20000；库内会话撤销与 BCrypt 落库确认；`PASSWORD_IP` 限流 12 连发为 9 次 20002、3 次 429（带 Retry-After 与 no-store）、0 次 5xx。验证栈与测试数据已清理。
- 发布状态：已随后端提交 `4233698` 推送至 `origin/master`；前端可接入修改密码表单。

## Revision 16 - API-20261014-016

- 日期：2026-10-14
- 类型：能力新增 / 行为调整
- 破坏性变更：否；contract version `6.6.0`。
- 影响范围：文章服务新增 `PUT /article/{articleId}/publish` 与 `PUT /article/{articleId}/unpublish`；`POST /article` 改为创建草稿并由服务端维护生命周期字段；`GET /article`、`GET /article/{articleId}`、`POST /article/search` 增加可见性规则。
- 变更内容：引入文章状态 `state`：`0` 草稿、`1` 已发布。`POST /article` 创建的文章一律为草稿，服务端设置 `state`、`createtime`（草稿为 null）、`updatetime`、`istop`、`visits`、`thumbup`、`comment`，忽略请求体中的同名值。新增 `PUT /article/{articleId}/publish`（仅作者；置为已发布并在首次发布时写入 `createtime`）与 `PUT /article/{articleId}/unpublish`（仅作者；退回草稿并保留 `createtime`），非作者或文章不存在返回 `20003`。可见性：`GET /article` 仅返回已发布文章；`GET /article/{articleId}` 对草稿仅作者可见，其他人按 404 处理；`POST /article/search` 未带 token 时只返回已发布文章，带有效 token 时额外返回当前用户自己的文章。现有 `state` 为 `1` 的数据行为不变。
- 前端动作：可接入草稿/发布工作流：创建后得到草稿，调用 `publish` 上架、`unpublish` 下架；作者用带 token 的 `GET /article/{articleId}` 或 `POST /article/search`（`userid` 为自己）查看自己的草稿。注意 `GET /article` 与匿名 `POST /article/search` 不再返回草稿。保存 revision `16`。
- 部署动作：无需迁移；`tb_article.state` 为既有的 `VARCHAR(16)` 列，草稿/已发布复用该列，现有 `state` 为 `1` 的历史文章视为已发布，无需改写数据。
- 验证：新增 `ArticleServiceTest` 8 项（草稿创建字段、发布/重复发布/下架、非作者与缺失拒绝、草稿可见性、搜索可见性）、`ArticleControllerTest` 新增 8 项（含未认证发布/下架、草稿默认字段、作者发布/下架、非作者拒绝、草稿对匿名 404 对作者 200、列表只返回已发布）；gated `ArticleLifecycleMySqlTest` 在测试环境真实 MySQL 上通过（草稿落库 `state=0` 且 `createtime` 为空、发布写入 `state=1` 与发布时间、重复发布保留首次时间、下架保留发布时间、非作者拒绝；临时文章按主键删除，DBX 复核运行前后 `tb_article` 3 → 3、`article-lifecycle-test` 残留 0）；全量 `mvn test` 209 项、0 失败、0 错误、8 项外部存储测试默认跳过。真实浏览器与前端联调未执行。
- 发布状态：已随后端提交 `96995a3` 推送至 `origin/master`；前端可接入草稿/发布工作流。

## Revision 17 - API-20261014-017

- 日期：2026-10-14
- 类型：能力新增
- 破坏性变更：否；contract version `6.7.0`。
- 影响范围：文章服务新增 `PUT /article/{articleId}/submit`、`PUT /article/{articleId}/approve`、`PUT /article/{articleId}/reject`；文章状态新增「待审核」；`GET /article/{articleId}` 与 `POST /article/search` 增加管理员可见性。
- 变更内容：文章状态新增 `2` 待审核。作者可 `submit` 将草稿提交审核（仅草稿可提交）；管理员（数据库 `ADMIN` 角色）可 `approve`（待审核→已发布，首次写入 `createtime`）或 `reject`（待审核→草稿）。非管理员调用 `approve`/`reject` 返回 HTTP 403、`code: 20003`；文章不存在返回 HTTP 404；状态不符返回 HTTP 200、`code: 20001`、`message: "文章状态不允许该操作"`。可见性扩展：待审核文章对公开列表与匿名搜索不可见，仅作者与管理员可见；管理员在 `GET /article/{articleId}` 与 `POST /article/search` 中不受可见性限制。作者原有的 `publish`/`unpublish` 保持不变，审核为可选路径。
- 前端动作：可接入审核工作流：作者在草稿页调用 `submit`；管理端用 `POST /article/search`（`state: "2"`）列出待审核文章，调用 `approve`/`reject`。区分 403（非管理员）与 200/20001（状态不符）。保存 revision `17`。
- 部署动作：无需迁移；复用既有 `tb_article.state` 列。管理员需由运维在 `tb_user_role` 授予 `ADMIN`（与标签管理同一张表），应用不自动授权；未授予前审核接口对所有用户返回 403。
- 验证：新增 `ArticleServiceTest` 7 项（提交、非作者/错误状态/缺失、管理员校验、通过/驳回、错误状态、管理员可见性、管理员搜索不受限）、`ArticleControllerTest` 新增 11 项（含未认证三入口、作者提交、非作者提交拒绝、非管理员 403、管理员通过/驳回、错误状态、管理员读取草稿）；gated `ArticleLifecycleMySqlTest` 在测试环境真实 MySQL 上通过（草稿→发布→下架→提交→非管理员拒绝→管理员通过→再次提交→管理员驳回，含临时管理员与文章的精确清理；DBX 复核 `tb_article` 3 → 3、`tb_user` 2 → 2、`tb_user_role` 0 → 0、临时数据残留 0）；全量 `mvn test` 227 项、0 失败、0 错误、8 项外部存储测试默认跳过。真实浏览器与前端联调未执行。
- 发布状态：已随后端提交 `f35d3cb` 推送至 `origin/master`；前端可接入审核工作流。

## Revision 18 - API-20261014-018

- 日期：2026-10-14
- 类型：能力新增
- 破坏性变更：否；contract version `6.8.0`。
- 影响范围：`GET /article/{articleId}` 增加浏览量累加副作用；`Article.visits` 语义补充。
- 变更内容：读取已发布文章时，若访问者不是文章作者（包括未登录访问者），服务端对 `visits` 执行数据库端原子加 1，响应中的 `visits` 为累加后的值；作者读取自己的文章、读取草稿或待审核文章均不计入。该接口因此具有副作用，不再严格幂等。
- 前端动作：无需改动；文章详情页的浏览量会随阅读自动增长，展示时以接口返回的 `visits` 为准，不要在前端自行累加。注意同一用户重复打开会重复计数。保存 revision `18`。
- 部署动作：无需迁移；复用既有 `tb_article.visits` 列。
- 验证：新增 `ArticleServiceTest` 3 项（非作者/匿名读取计数、作者与草稿不计数）、`ArticleControllerTest` 2 项（公开读取 +1、作者读取不计数）；gated `ArticleLifecycleMySqlTest` 在测试环境真实 MySQL 上通过（发布后匿名读取计 1、作者读取不计、其他用户读取累计为 2；临时数据按主键清理，DBX 复核既有 3 篇文章 `visits` 1024/680/420 不变、残留 0）；全量 `mvn test` 232 项、0 失败、0 错误、8 项外部存储测试默认跳过。真实浏览器与前端联调未执行。
- 发布状态：已随后端提交 `52e7474` 推送至 `origin/master`。

## Revision 19 - API-20261014-019

- 日期：2026-10-14
- 类型：能力新增
- 破坏性变更：否；contract version `6.9.0`。
- 影响范围：文章服务新增 `PUT /article/thumbup/{articleId}`；新增唯一关系表 `tb_article_thumbup`；`Article.thumbup` 由点赞接口原子累加。
- 变更内容：新增文章点赞接口，仅已发布文章可点赞。服务端在 `tb_article_thumbup` 登记 `(articleId, userId)` 唯一关系并对 `tb_article.thumbup` 原子加 1；同一用户重复点赞返回 HTTP 200、`flag: false`、`code: 20004`、`message: "不能重复点赞"` 且不计数；文章不存在或非已发布返回 HTTP 404、`code: 20001`。并发同一用户点赞由唯一关系与行锁保证只成功一次。当前无取消点赞接口。
- 前端动作：可接入点赞按钮：调用 `PUT /article/thumbup/{articleId}`，成功（20000）后本地 `thumbup` 加 1；`20004` 表示已点赞过，按钮应置为已点赞态且不要重复计数；`404` 按文章不可见处理。保存 revision `19`。
- 部署动作：部署前对目标库执行 [mysql-article-thumbup-migration.sql](mysql-article-thumbup-migration.sql)（可重复执行）；新库由初始化脚本直接建表。历史 `tb_article.thumbup` 计数保留、不重算、不补关系。
- 验证：新增 `ArticleServiceTest` 4 项（首次计数、重复拒绝、缺失/非已发布拒绝、计数失败回滚关系）、`ArticleControllerTest` 2 项（未认证入口参数化 + 首次成功/重复 20004/缺失 404 用例）；gated `ArticleThumbupMySqlTest` 在测试环境真实 MySQL 上通过（首次/重复、同用户 20 路并发恰好 1 成功 19 重复、10 用户并发全成功、文章不存在与非已发布拒绝；临时数据按主键清理，DBX 复核既有 3 篇文章 `thumbup` 88/45/32 与 `visits` 不变、`tb_article_thumbup` 0 行、残留 0）；DDL 已获用户授权并在测试环境执行（`CREATE TABLE IF NOT EXISTS tb_article_thumbup`）；全量 `mvn test` 239 项、0 失败、0 错误、9 项外部存储测试默认跳过。真实浏览器与前端联调未执行。
- 发布状态：已随后端提交 `5a2f154` 推送至 `origin/master`。

## Revision 20 - API-20261014-020

- 日期：2026-10-14
- 类型：能力新增
- 破坏性变更：否；contract version `6.10.0`。
- 影响范围：用户服务新增 `PUT /user/follow/{userId}`、`DELETE /user/follow/{userId}`、`GET /user/{userId}/fans/{page}/{size}`、`GET /user/{userId}/follows/{page}/{size}`；新增唯一关系表 `tb_user_follow`；`tb_user.fanscount`/`followcount` 由关注接口原子增减。
- 变更内容：新增关注/取关与粉丝、关注列表。关注需认证：不能关注自己（HTTP 200、`code: 20001`、`message: "不能关注自己"`），目标不存在返回 HTTP 404，重复关注返回 HTTP 200、`code: 20005`、`message: "已关注"`；成功后服务端在 `tb_user_follow` 登记唯一关系并对被关注者 `fanscount`、关注者 `followcount` 原子加 1。取关：未关注返回 `20005`「未关注」，成功对被关注者 `fanscount`、关注者 `followcount` 原子减 1（不小于 0）。列表公开：粉丝 `GET /user/{userId}/fans/{page}/{size}`、关注 `GET /user/{userId}/follows/{page}/{size}`，`page` 从 1 开始、`size` 1–100，越界返回 400/20001，目标不存在返回 404；每项为公开摘要 `UserSummary`（`id`、`nickname`、`avatar`、`sex`、`interest`、`personality`、`fanscount`、`followcount`，不含手机号、邮箱、生日与密码）。
- 前端动作：可接入关注按钮与粉丝/关注列表：关注成功（20000）后本地更新计数；`20005` 表示已关注或未关注，按当前状态处理；`20001`「不能关注自己」时禁用对自己的关注。列表按 `PageResult` 读取 `rows`。保存 revision `20`。
- 部署动作：部署前对目标库执行 [mysql-user-follow-migration.sql](mysql-user-follow-migration.sql)（可重复执行）；新库由初始化脚本直接建表。历史 `fanscount`/`followcount` 保留、不重算、不补关系。
- 验证：新增 `UserFollowServiceTest` 7 项、`UserControllerTest` 新增 4 项（未认证、关注成功/自己/重复/缺失、取关、列表与分页）；gated `UserFollowMySqlTest` 在测试环境真实 MySQL 上通过（首次/重复/自己/缺失/取关、粉丝与关注列表、同一关系 20 路并发恰好 1 成功 19 重复；首次并发因锁竞争失败，写入前对目标用户行加 `FOR UPDATE` 后通过；临时用户与关系按主键清理，DBX 复核既有用户 `fanscount`/`followcount` 256/32 与 188/45 不变、`tb_user_follow` 0 行、残留 0）；DDL 已获用户授权并在测试环境执行（`CREATE TABLE IF NOT EXISTS tb_user_follow`）；全量 `mvn test` 251 项、0 失败、0 错误、10 项外部存储测试默认跳过。真实浏览器与前端联调未执行。
- 发布状态：已随后端提交 `a8b05df` 推送至 `origin/master`。

## Revision 21 - API-20261018-021

- 日期：2026-10-18
- 类型：能力新增
- 破坏性变更：否；contract version `6.11.0`。
- 影响范围：文章服务 `Article` 数据模型、`POST /article`、`PUT /article/{articleId}`、`DELETE /article/{articleId}`、`GET /article`、`GET /article/{articleId}`、`POST /article/search/{page}/{size}`；新增 `tb_article_label` 关系表。
- 变更内容：文章新增可选 `labelIds` 字符串数组。创建文章时写入标签关系；修改时省略表示保留、提供数组表示整体替换、空数组表示清空；删除文章级联清理关系。标签关系与 `tb_label.count` 在同一事务内原子增减。文章读取响应返回 `labelIds`。搜索请求可用 `labelIds` 筛选同时包含全部指定标签的文章；标签 ID 为空、格式错误或不存在返回 HTTP 400/20001。
- 前端动作：文章编辑表单提交标签 ID 数组，读取文章时使用 `labelIds` 并通过基础服务 `/label` 映射名称；编辑时区分省略字段与空数组；文章搜索可使用 `labelIds`，按 `PageResult` 读取结果。保存 revision `21`。
- 部署动作：部署前对目标库执行 [mysql-article-label-migration.sql](mysql-article-label-migration.sql)；新库由 `mysql-init.sql` 直接建表。迁移只创建关系表，不补历史关系或重算既有标签计数。
- 验证：新增 `ArticleServiceTest` 与 `ArticleControllerTest` 标签保存、替换、删除、筛选和非法输入覆盖；gated `ArticleLifecycleMySqlTest` 增加真实关系、计数和级联清理验收，默认未设置配置时跳过。窄测文章控制器 `61` 项、服务 `27` 项通过；真实 MySQL DDL 和 gated 验收待单独授权。
- 发布状态：工作区文档已同步，待本切片提交推送后由前端读取后端仓库。

## Revision 22 - API-20261027-022

- 日期：2026-10-27
- 类型：缺陷修复与评论规则完善
- 破坏性变更：是；contract version `7.0.0`。新密码输入增加 UTF-8 字节上限；评论资源校验、可见性及删除范围收紧。
- 影响范围：`POST /user/register`、`POST /user/login`、`POST /user/password`、`PUT /user/profile`；文章详情、编辑、发布/下架、提交/审核、删除；全部 `/comment` 读写及点赞接口。未新增路由、响应字段或数据库列。
- 变更内容：注册/新密码保留 8–64 字符并限制 UTF-8 ≤72 字节（非法输入 400/20001）；直接 BCrypt 的登录/旧密码候选超长时按原密码错误处理。历史超长明文完整匹配后升级为 `{bcrypt-sha256}` 标记哈希，客户端仍发送完整原密码。用户更新与文章写入使用事务行锁；资料不回写旧密码/旧计数，文章编辑或状态切换不覆盖并发统计，同状态审核交错重新检查当前状态。标签替换使用锁定当前读，校验实际插入数，并发删标签导致关系不完整时整体回滚。
- 评论规则：仅已发布文章允许新增与点赞，文章缺失/非已发布返回 404；父评论缺失或跨文章返回 400。评论读取遵守所属文章可见性，孤立评论不返回，按不存在文章查询从空数组改为 404。新增评论与文章计数 +1 同事务；作者删除自己的评论会删除整条后代回复分支（含他人回复）和点赞关系，计数按实际删除条数扣减且不低于 0；删除文章同事务清理全部评论及点赞关系。历史计数不重算。
- 前端动作：注册/改密增加 UTF-8 字节长度校验（例如 `TextEncoder`），继续发送原密码，不预哈希；对新增评论的 400 和文章缺失/下架的 404 给出提示；删除评论后刷新完整列表和文章统计，确认文案说明会删除回复分支；评论读取仍为平铺数组。保存 revision `22`。真实前后端联调按用户要求留待后续。
- 部署动作：无需 DDL 或历史数据批处理；已有关系表沿用。所有用户服务实例需支持新标记密码格式后再接收流量，不能回滚到无法识别该格式的旧实例；已被直接 BCrypt 截断的历史长密码无法恢复后缀，需运维受控重置。本次未连接真实 MySQL/Redis、未执行迁移或远程写入。
- 验证：真实内存 H2/JPA 先复现用户 3 类及文章 7 项并发缺陷，修复后通过；新增密码字符/字节边界、MVC 错误行为、评论创建/父评论校验/分支删除/可见性/并发计数及回滚测试。JDK 21 全量 `mvn clean test` 共 318 项，308 项通过、0 失败、0 错误、10 项外部验收跳过；契约一致性和敏感信息扫描通过。H2 结果不等同于真实 MySQL REPEATABLE READ 验收。
- 发布状态：代码与契约三件套随本切片提交同步；文档发布位置为后端仓库 `origin/master` 的 `docs/` 目录，前端同步指针仍为 revision `21`，待后续消费 revision `22`。

## Revision 23 - API-20261027-023

- 日期：2026-10-27
- 变更类型：fix
- 破坏性变更：是，contract version `8.0.0`。
- 影响路径：`/article`、`/article/{articleId}`、`/article/search/{page}/{size}`、`/article/thumbup/{articleId}`、全部评论查询及新增/点赞、`GET /user/follow/{userId}`，以及文章正文展示。
- 变更内容：私密文章（`ispublic: "0"`）即使已发布也仅作者和数据库 `ADMIN` 可见；关联评论的读取、新增及点赞遵守相同可见性，既有评论作者仍可修改/删除自己的评论。新增文章省略公开标记时默认 `"1"`，历史 null 保持公开兼容，非法标记返回 400。文章列表与分页在数据库中按置顶、更新时间、发布时间、ID 依次倒序。新增认证关注状态查询，返回 `data.following` 与 `Cache-Control: no-store`。文章正文以转义纯文本保留换行显示，HTML 标签不执行，无存量内容批量转换。
- 前端动作：同步正文渲染与输入提示；保留服务端分页顺序；进入文章详情/切换账号后查询真实关注状态，失败提供重试且不误发关注/取关；私密/不存在文章及评论按 404 处理；保存已处理 revision `23`。
- 数据影响：不新增数据库表或列，不执行 DDL、不回填历史公开标记或正文。目标环境需已有此前的认证、关注及文章标签关系表。
- 发布位置：后端仓库 `docs/` 三件套，并同步至前端仓库 `docs/`；测试与真实环境验收结果见 `plan/progress.md`。
- 验证：JDK 21 全量 `mvn clean test` 325 项（315 通过、10 项外部验收默认跳过），另显式运行 7 项真实 MySQL/Redis 测试全部通过；前端 `npm test`、`npm run build` 和 Chromium 核心流程验收通过。临时用户、文章、评论、关系、会话与专用 Redis 限流键已精确清理，原有表记录数和文章/评论/标签/用户统计与验收前一致；未执行 DDL 或正式环境部署。
