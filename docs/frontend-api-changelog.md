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