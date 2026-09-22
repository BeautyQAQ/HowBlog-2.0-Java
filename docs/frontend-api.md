# HowBlog 前端接口契约

> 当前契约版本：`1.1.0`
> 当前同步 revision：`2`
> 最后更新：`2026-09-22`
> 机器可读状态：[frontend-api-status.json](frontend-api-status.json)
> 变更记录：[frontend-api-changelog.md](frontend-api-changelog.md)

本文档是后端提供给前端的接口事实来源。前端实现应以本文档描述的当前行为为准；接口新增、修改、删除或 WebSocket 消息协议变化时，必须同时更新本文档、变更记录和状态文件。

## 前端同步流程

前端 AI agent 或前端开发者不需要访问后端仓库的 Git 历史，按以下顺序读取已发布的文档包即可：

1. 先读取 `frontend-api-status.json`，比较本地记录的 `revision` 与远端 `revision`。
2. revision 变大时，读取 `frontend-api-changelog.md` 中从上次 revision 到当前 revision 的条目，先了解新增、修改、删除和破坏性变更。
3. 再读取本文档的对应章节，以当前契约内容实现或调整前端。
4. 完成同步后，在前端项目中记录已处理的 revision，例如 `backendApiRevision: 1`。

这三个文件需要通过前端 agent 能访问的共享位置发布。可选方式包括：

- 将 `docs` 中的接口文档包同步到前端仓库；
- 由 CI 发布到前后端都能访问的内部文档站或构建产物地址；
- 将文档目录挂载到两个 agent 共用的工作区。

如果前端 agent 既看不到后端仓库，也没有共享文件、构建产物或 URL，那么它无法凭空知道接口发生了变化；必须先建立至少一个只读发布渠道。

## 服务地址

以下地址是本地开发默认地址。部署环境应替换主机名或端口，不要把部署地址硬编码到前端代码中。

| 服务 | 本地 Base URL | 端口 | 职责 |
| --- | --- | ---: | --- |
| 用户服务 | `http://localhost:9008` | 9008 | 登录、WebSocket 即时通讯 |
| 基础服务 | `http://localhost:9001` | 9001 | 标签管理 |
| 文章服务 | `http://localhost:9004` | 9004 | 文章、评论、评论点赞 |

REST 接口路径均相对于对应服务的 Base URL。例如，文章列表的本地地址是 `http://localhost:9004/article`。

## 通用 HTTP 约定

- 请求体和成功响应使用 JSON；发送请求时使用 `Content-Type: application/json`。
- 当前控制器启用了跨域访问，具体部署环境仍应根据网关和安全策略限制来源。
- 当前接口没有统一的登录令牌机制。登录接口返回用户对象，不返回 JWT 或其他 token；前端不能假设存在 `Authorization` 认证头。
- ID 均按字符串处理，避免在 JavaScript 中转成可能丢失精度的 number。
- 时间字段由 Java `Date` 序列化，前端应按可配置的日期解析逻辑处理，不要依赖某个未在契约中固定的显示格式。

### 统一响应体

REST 接口返回 `Result`：

```json
{
  "flag": true,
  "code": 20000,
  "message": "查询成功",
  "data": {}
}
```

字段说明：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `flag` | boolean | 业务是否成功 |
| `code` | integer | 业务状态码 |
| `message` | string | 面向调用方的结果说明 |
| `data` | object/array/null | 返回数据；无数据的写操作通常为 `null` |

当前已定义的业务状态码：

| code | 含义 |
| ---: | --- |
| 20000 | 成功 |
| 20001 | 通用失败 |
| 20002 | 用户名或密码错误（当前登录实现实际返回 20000，见登录接口说明） |
| 20003 | 权限不足，当前未有接口使用 |
| 20004 | 远程调用失败；评论重复点赞时也使用此码 |
| 20005 | 重复操作 |

前端应优先根据 `flag` 判断业务成功与否，再使用 `code` 和 `message` 展示或分流；同时处理非 2xx HTTP 响应和网络错误。

## 数据模型

以下字段来自当前接口实际接收或返回的 Java 对象。没有标记为必填的字段，当前后端没有 Bean Validation 强制校验；业务上是否必填仍应由前端界面保证。

### User

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `id` | string | 用户 ID |
| `mobile` | string | 手机号 |
| `password` | string | 密码；当前登录响应可能原样返回，请勿持久化 |
| `nickname` | string | 昵称 |
| `sex` | string | 性别 |
| `birthday` | datetime/null | 生日 |
| `avatar` | string | 头像地址 |
| `email` | string | 邮箱 |
| `regdate` | datetime/null | 注册时间 |
| `updatedate` | datetime/null | 更新时间 |
| `lastdate` | datetime/null | 最后登录时间 |
| `online` | integer/null | 在线时长，单位为分钟 |
| `interest` | string | 兴趣 |
| `personality` | string | 个性签名 |
| `fanscount` | integer/null | 粉丝数 |
| `followcount` | integer/null | 关注数 |

### Label

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `id` | string | 标签 ID；新增时由后端生成 |
| `labelname` | string | 标签名称 |
| `state` | string | 状态 |
| `count` | integer/null | 使用数量 |
| `fans` | integer/null | 关注数 |
| `recommend` | string | 是否推荐 |

### Article

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `id` | string | 文章 ID；新增时由后端生成 |
| `columnid` | string | 专栏 ID |
| `userid` | string | 作者用户 ID |
| `title` | string | 标题 |
| `content` | string | 正文 |
| `image` | string | 封面地址 |
| `createtime` | datetime/null | 发布时间 |
| `updatetime` | datetime/null | 更新时间 |
| `ispublic` | string | 是否公开 |
| `istop` | string | 是否置顶 |
| `visits` | integer/null | 浏览量 |
| `thumbup` | integer/null | 点赞数 |
| `comment` | integer/null | 评论数 |
| `state` | string | 审核状态 |
| `channelid` | string | 频道 ID |
| `url` | string | 文章 URL |
| `type` | string | 文章类型 |

### Comment

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `_id` | string | 评论 ID；新增时由后端生成 |
| `articleid` | string | 所属文章 ID |
| `content` | string | 评论内容 |
| `userid` | string | 用户 ID |
| `parentid` | string | 父评论 ID；顶级评论可为空 |
| `publishdate` | datetime/null | 发布时间；新增时由后端设置 |
| `thumbup` | integer/null | 点赞数；新增时初始化为 0 |

## 用户服务（9008）

### POST `/user/login`

用户登录。当前实现使用请求体中所有非空字段做精确匹配，因此前端通常发送 `mobile` 和 `password`；不要把未参与登录的用户字段随意带入请求。

请求示例：

```json
{
  "mobile": "13800000000",
  "password": "example-password"
}
```

成功响应的 `data` 为完整 `User` 对象：

```json
{
  "flag": true,
  "code": 20000,
  "message": "登录成功",
  "data": {
    "id": "10001",
    "mobile": "13800000000",
    "nickname": "Alice"
  }
}
```

用户不存在时当前实现返回：

```json
{
  "flag": false,
  "code": 20000,
  "message": "登录失败",
  "data": null
}
```

当前没有注册、退出登录、刷新 token 或用户资料修改接口。

## 基础服务（9001）

### GET `/label`

查询全部标签。

成功时 `data` 为 `Label[]`。

### GET `/label/{id}`

按 ID 查询标签。不存在时 `data` 为 `null`，当前仍返回 `flag: true`。

### POST `/label`

新增标签。后端会覆盖请求体中的 `id` 并生成新 ID。

请求体为 `Label`，通常至少包含：

```json
{
  "labelname": "Java",
  "state": "1",
  "recommend": "1"
}
```

成功响应：`flag: true`、`code: 20000`、`message: "添加成功"`，`data` 为 `null`。

### PUT `/label/{id}`

修改标签。路径中的 `id` 会覆盖请求体中的 `id`；当前只更新请求体中非空的字段。

请求体为需要修改的 `Label` 字段，例如：

```json
{
  "labelname": "Java 后端",
  "recommend": "0"
}
```

如果 ID 不存在，当前服务不抛出业务错误，仍可能返回修改成功。

### DELETE `/label/{id}`

删除标签。ID 不存在时当前服务仍可能返回删除成功。

## 文章服务（9004）

### GET `/article`

查询全部文章。成功时 `data` 为 `Article[]`。

### GET `/article/{articleId}`

按 ID 查询文章。不存在时 `data` 为 `null`，当前仍返回 `flag: true`。

### POST `/article`

新增文章。后端生成 `id`，请求体为 `Article`。当前没有强制字段校验。

### PUT `/article/{articleId}`

修改文章。路径 ID 会覆盖请求体中的 `id`；当前只更新请求体中非空的字段。不存在的文章可能被静默忽略，但接口仍返回修改成功。

### DELETE `/article/{articleId}`

删除文章。不存在的文章可能被静默忽略，但接口仍返回删除成功。

### POST `/article/search/{page}/{size}`

按条件分页查询文章。

- `page` 从 `1` 开始；小于 `1` 时按第 `1` 页处理。
- `size` 小于 `1` 时按 `1` 条处理。
- 请求体是 JSON 对象，后端对字段做精确相等匹配，不是模糊搜索。
- 当前允许的条件字段为：`id`、`columnid`、`userid`、`title`、`content`、`image`、`createtime`、`updatetime`、`ispublic`、`istop`、`visits`、`thumbup`、`comment`、`state`、`channelid`、`url`、`type`。
- 未知字段会被忽略；值为 `null` 的字段会被忽略。

请求示例：

```http
POST /article/search/1/10
Content-Type: application/json
```

```json
{
  "userid": "10001",
  "state": "1"
}
```

响应中的 `data` 为 `PageResult<Article>`：

```json
{
  "flag": true,
  "code": 20000,
  "message": "查询成功",
  "data": {
    "total": 1,
    "rows": [
      {
        "id": "20001",
        "title": "示例文章",
        "userid": "10001"
      }
    ]
  }
}
```

## 评论服务（9004）

### GET `/comment`

查询全部评论。成功时 `data` 为 `Comment[]`。

### GET `/comment/{id}`

按评论 ID 查询评论。不存在时 `data` 为 `null`，当前仍返回 `flag: true`。

### GET `/comment/article/{articleId}`

按文章 ID 查询评论列表，按发布时间倒序返回。成功时 `data` 为 `Comment[]`。

### POST `/comment`

新增评论。后端生成 `_id`、设置 `publishdate`，并把 `thumbup` 初始化为 `0`。请求体通常包含 `articleid`、`content`、`userid` 和可选的 `parentid`。

请求示例：

```json
{
  "articleid": "20001",
  "content": "这是一条评论",
  "userid": "10001",
  "parentid": null
}
```

### PUT `/comment/{id}`

修改评论。路径 ID 会写入请求体对象的 `_id`；当前只由持久层按 ID 更新，未提供字段校验。

### DELETE `/comment/{id}`

按 ID 删除评论。

### PUT `/comment/thumbup/{id}`

评论点赞。当前后端使用固定用户 ID `123` 判断重复点赞，并没有接入真实登录态。

- 首次点赞通常返回 `flag: true`、`code: 20000`、`message: "点赞成功"`。
- 同一服务进程中再次点赞通常返回 `flag: false`、`code: 20004`、`message: "不能重复点赞"`。
- 前端不能把当前固定用户 ID 逻辑当成真实鉴权。

按评论 ID和按文章 ID 查询使用不同的路径模板，前端应使用 `/comment/{id}` 查询单条评论，使用 `/comment/article/{articleId}` 查询文章评论列表；不要通过同一个路径推断查询语义。

## WebSocket 即时通讯（9008）

### 连接地址

```text
ws://localhost:9008/im?user=alice
```

HTTPS 页面使用 `wss://`。`user` 查询参数必填，目前只作为连接展示身份，不是登录凭证。缺少该参数时连接会被关闭。

### 客户端发送消息

加入房间：

```json
{
  "type": "join",
  "room": "lobby"
}
```

心跳：

```json
{
  "type": "ping"
}
```

房间广播：

```json
{
  "type": "message",
  "room": "lobby",
  "content": "大家好"
}
```

单聊：

```json
{
  "type": "message",
  "to": "bob",
  "content": "你好，Bob"
}
```

未填写 `to` 和 `room` 时为全局广播。`content` 去除首尾空白后不能为空。

### 服务端事件

连接建立后：

```json
{
  "type": "ready",
  "user": "alice"
}
```

在线状态：

```json
{
  "type": "presence",
  "event": "online",
  "user": "bob"
}
```

`event` 当前为 `online` 或 `offline`。

加入房间成功：

```json
{
  "type": "joined",
  "room": "lobby"
}
```

消息事件：

```json
{
  "type": "message",
  "from": "alice",
  "to": "",
  "room": "lobby",
  "content": "大家好",
  "timestamp": "2026-09-22T00:00:00Z"
}
```

心跳响应：

```json
{
  "type": "pong"
}
```

错误事件：

```json
{
  "type": "error",
  "message": "消息内容不能为空"
}
```

当前 WebSocket 会话和消息仅保存在服务进程内存中，服务重启后丢失；同一用户重复连接时，后建立的连接会覆盖内存中的旧连接。

## 当前集成限制

- REST 登录没有 token、刷新 token 或统一鉴权中间件。
- 评论点赞使用固定用户 ID `123`，不能代表真实用户级点赞。
- 评论文章查询存在路由冲突，见上文阻塞项。
- 新增、修改接口没有完整的 Bean Validation；前端应自行做基础输入校验，但不能把前端校验当成后端约束。
- 文章、标签的不存在资源操作当前可能返回成功消息，前端如需严格反馈应等待后端补充明确的错误契约。
- 文章服务需要 MySQL 和 Redis；所有业务服务的具体连接配置属于部署环境，不写入前端代码或文档。

## 契约维护规则

接口变更的同步规则写在根目录 [AGENTS.md](../AGENTS.md)。每次接口变更后，前端 agent 应以 `frontend-api-status.json` 的 `revision` 作为是否需要同步的唯一快速判断依据，再阅读变更日志和本文档。