# HowBlog 2.0 推进进度

## 1. 当前状态

- 更新时间：2026-09-22
- 当前阶段：Phase 1 身份认证与安全边界进行中，AUTH-01 至 AUTH-07 已完成
- 当前接口契约：revision `5`，contract version `3.0.0`；仓库文档已同步，外部发布渠道待确认
- 总体判断：登录、REST 写接口和 WebSocket 已接入 JWT；刷新/撤销、角色授权、跨存储一致性和生产配置仍待推进
- 计划正文：[implementation-plan.md](implementation-plan.md)

状态标记：

- `[x]` 已完成并有验证依据
- `[>]` 当前进行中
- `[ ]` 尚未开始
- `[!]` 被外部决策或环境阻塞

## 2. 已完成基线

- [x] 完成 Maven 多模块结构和业务模块职责划分。
- [x] 完成从 MyBatis-Plus/MongoDB 相关实现到 Spring Data JPA 的迁移收敛。
- [x] 保持 Spring Boot 2.7.18、`javax.persistence` 和 Java 21 兼容约束。
- [x] 移除外部环信依赖，保留轻量 Spring WebSocket IM。
- [x] 修复评论按 ID 查询与按文章查询的路由冲突，当前文章评论路径为 `GET /comment/article/{articleId}`。
- [x] 建立前端接口契约、变更记录和机器可读状态文件。
- [x] 验证全模块 `clean compile` 和 `test` 命令可执行。
- [x] 已有 74 项自动化测试通过，其中 55 项为 MVC 切片测试；DAO/Redis 使用模拟依赖，真实存储联调仍未覆盖。

## 3. 阶段进度

| 阶段 | 状态 | 完成度 | 当前说明 |
| --- | --- | ---: | --- |
| Phase 0 基线整理 | 已完成 | 100% | 构建、模块、契约和已知限制已记录 |
| Phase 1 身份认证与安全边界 | 进行中 | 约 85% | AUTH-01 至 AUTH-07 已完成，剩余为角色模型、会话策略和生产安全收口 |
| Phase 2 测试与数据一致性 | 已有测试基础 | 待评估 | 74 项单元/MVC 测试通过；真实存储集成与一致性补偿尚未开展 |
| Phase 3 配置、部署与可观测性 | 未开始 | 0% | 需要先确定运行环境和服务编排方式 |
| Phase 4 博客核心产品能力 | 未开始 | 0% | 等安全和测试底座稳定后推进 |
| Phase 5 IM 生产化 | 未开始 | 0% | 当前 IM 仍为进程内开发实现 |

## 4. 当前迭代：Phase 1 身份认证与安全边界

### 待办顺序

- [x] `AUTH-01` 确定令牌方案、签发服务、刷新/退出策略和兼容期。
- [x] `AUTH-01` 确定现有密码数据格式及迁移方案。
- [x] `AUTH-01` 确定文章、评论、标签写接口的角色与资源归属规则的当前边界。
- [x] `AUTH-02` 设计登录请求 DTO、登录响应 DTO，并完成密码响应脱敏。
- [x] `AUTH-02` 登录成功时将匹配的存量明文密码升级为 BCrypt。
- [x] `AUTH-03` 实现令牌签发与校验，并补充未认证/认证测试。
- [x] `AUTH-04` 将评论点赞从固定用户 ID 改为当前认证用户，并增加失败回滚测试。
- [x] `AUTH-05` 将 WebSocket 身份从 URL 参数切换到认证上下文。
- [x] `AUTH-06` 同步接口契约至 revision `4`，并发布前端迁移说明。
- [x] `AUTH-07` 统一异常响应、基础输入校验和受保护接口 MVC 切片测试；同步 revision `5`。

### 当前明确风险

- [x] 登录不再按请求体所有非空字段精确匹配用户，只使用手机号查询并校验密码。
- [x] 登录响应不再暴露用户密码；存量明文密码只在成功登录时升级。
- [ ] 数据库中仍可能存在未登录用户的明文密码，尚未执行批量迁移。
- [x] 评论点赞已使用当前 token 用户 ID，并覆盖重复、评论不存在和数据库异常回滚路径。
- [x] WebSocket 已拒绝缺失/无效 token，不再信任 `?user=` 参数。
- [ ] JWT 尚无刷新、退出和服务端主动撤销机制。
- [ ] 标签写接口只有认证校验，尚无管理员角色模型。
- [x] 三个服务控制器异常已统一脱敏，坏 JSON/参数错误、缺失资源及未知异常返回明确 HTTP 状态。
- [ ] 最大文本长度、状态枚举、评论关联文章/父评论存在性校验仍未全面覆盖。
- [!] revision `5` 文档位于本仓库 `docs/`，前端共享发布渠道尚未确认。
- [ ] Redis 与 MySQL 点赞提交失败后的最终一致性补偿策略尚未完成。
- [ ] JWT secret、数据库和 Redis 配置仍需进一步完成环境变量化和敏感信息清理。

## 5. 任务验收记录

| 任务 | 开始时间 | 完成时间 | 验证命令/证据 | 结果 |
| --- | --- | --- | --- | --- |
| 基线编译 | 2026-09-22 | 2026-09-22 | `mvn clean compile` | 通过 |
| 基线测试命令 | 2026-09-22 | 2026-09-22 | `mvn test` | 当时通过但无测试源码；当前数量见 AUTH-07 |
| 评论路由修复 | 2026-09-22 | 2026-09-22 | API revision 2、变更记录 | 已发布 |
| AUTH-01 | 2026-09-22 | 2026-09-22 | 认证决策已写入实施方案 | 已完成 |
| AUTH-02 | 2026-09-22 | 2026-09-22 | `mvn -pl how_user -am test -Dtest=UserServiceTest`；3 tests passed | 已完成 |
| AUTH-03 | 2026-09-22 | 2026-09-22 | `mvn -pl how_user -am test -Dtest=UserServiceTest,JwtTokenServiceTest,JwtHandshakeInterceptorTest`；用户侧 5 tests passed，公共认证测试 8 项 | 已完成 |
| AUTH-04 | 2026-09-22 | 2026-09-22 | `mvn -pl how_article -am test -Dtest=CommentServiceTest`；6 tests passed，覆盖重复点赞、失败回滚、非作者操作和评论归属保护 | 已完成 |
| AUTH-05 | 2026-09-22 | 2026-09-22 | `JwtHandshakeInterceptorTest`；2 tests passed | 已完成 |
| AUTH-06 | 2026-09-22 | 2026-09-22 | API revision 4、破坏性认证协议、契约和 changelog 已同步 | 已完成 |
| AUTH-07 | 2026-09-22 | 2026-09-22 | JDK 21 下 `mvn clean test`；74 tests passed；源码诊断与 `git diff --check` 通过 | 已完成本轮范围；未连接真实 MySQL/Redis |

## 6. 变更记录

### 2026-09-22

- 建立实施方案和进度台账。
- 将 Phase 1 身份认证与安全边界设为下一阶段。
- 确认当前 API revision 为 `2`，认证改造预计发布 revision `3` 或更高。
- 确定登录边界的初始决策：手机号歧义拒绝登录，存量明文密码在成功登录时升级为 BCrypt，登录响应使用安全 DTO；JWT 令牌实现进入 AUTH-03。
- 完成 AUTH-02：新增登录请求/响应 DTO、手机号限定查询、密码校验和存量密码按登录升级；新增 3 个用户服务单元测试。
- 发布 API revision `3`：登录失败使用 `20002`，成功响应只返回脱敏用户资料，前端文档已同步。
- 完成 AUTH-03 至 AUTH-05：加入 HS256 JWT、REST Bearer 拦截器、文章/评论作者归属、用户级评论点赞和 WebSocket 握手鉴权；新增认证、JWT、点赞和握手测试。
- 发布 API revision `4`、contract version `2.0.0`：受保护写接口和 WebSocket 连接参数发生破坏性变化，前端契约已同步。
- 完成干净构建验证：`mvn clean test` 全模块通过；公共认证测试 8 项、文章模块测试 6 项、用户模块测试 5 项。
- 追加认证边界硬化：Bearer scheme 大小写兼容，评论编辑仅允许修改正文；窄测试新增通过，公共认证 8 项、文章/评论 6 项。
- 完成 AUTH-07：共享异常处理、基础 Bean Validation、分页限制和缺失资源处理；保留文章/评论作者权限与登录缺少凭据的原有响应。
- 新增 55 项 MVC 切片测试：文章/评论 33 项、标签 14 项、登录 8 项。使用真实 Controller、Service、JWT 和 MVC 配置，模拟 DAO/Redis；全量 74 项测试无失败、错误或跳过。
- 同步 API revision `5`、contract version `3.0.0`；前端需适配 400/404/500 和新校验边界，外部发布仍待共享渠道确认。

后续每次开发完成一个任务后，必须同步更新：

1. 上方阶段状态和任务勾选。
2. 任务验收表中的时间、命令、结果和未验证范围。
3. 当前风险、阻塞项和下一步任务。
4. 如接口可观察行为变化，同步更新 `docs/frontend-api.md`、`docs/frontend-api-changelog.md` 和 `docs/frontend-api-status.json`。

## 7. 下一次开发的最小入口

下一次开始编码时，按以下顺序执行：

1. 确认标签管理员权限模型与刷新/退出/撤销的上线要求，并建立 revision `5` 的前端共享发布渠道。
2. 在 Phase 2 设计 Redis/MySQL 点赞的一致性补偿，并补充 Testcontainers 或等价集成环境。
3. 在 Phase 3 完成数据库、Redis、JWT secret、CORS 和 Docker Compose 的环境化配置。
