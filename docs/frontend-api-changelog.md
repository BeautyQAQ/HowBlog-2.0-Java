# 前端接口契约变更记录

本文档采用追加方式维护，不删除或改写已发布的历史条目。每一条记录的 `revision` 必须与 [frontend-api-status.json](frontend-api-status.json) 中的当前 revision 一致。

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