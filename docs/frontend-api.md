# HowBlog 前端接口契约

> 当前契约版本：`6.0.1`
> 当前同步 revision：`9`
> 最后更新：`2026-09-24`
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
- 登录成功后返回短期 Bearer JWT。需要认证的写接口必须发送 `Authorization: Bearer <token>`；公开查询接口和登录接口不要求该请求头。
- 访问令牌默认最多有效 1800 秒，不超过所属会话的绝对期限。每次登录创建独立数据库会话，绝对有效期为 7 天；刷新不延长这 7 天。
- 访问 token 包含服务端会话 ID（`sid`），三个服务在使用 token 时都检查会话未撤销、未到期、归属用户匹配且用户仍存在。旧的不带会话 ID 的 token 不再可用，升级后需要重新登录。
- 数据库不可用时拒绝带 token 请求并返回脱敏 HTTP 500，不退回到仅校验 JWT 签名。未带 token 的公开查询保持公开；已通过验证的在途请求不保证因随后退出而被取消。
- 缺少或无法验证 token 的受保护请求返回 HTTP `401`，响应体仍使用 `Result`，业务码为 `20003`。
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
| 20002 | 手机号或密码错误 |
| 20003 | 未认证或权限不足 |
| 20004 | 远程调用失败；评论重复点赞时也使用此码 |
| 20005 | 重复操作 |

前端应优先根据 `flag` 判断业务成功与否，再使用 `code` 和 `message` 展示或分流；同时处理非 2xx HTTP 响应和网络错误。

### 错误响应与输入边界

三个服务的控制器异常统一返回 `Result`，不返回异常原文、SQL 或堆栈；错误响应的 `data` 为 `null`。

| 场景 | HTTP 状态 | code | message |
| --- | ---: | ---: | --- |
| 请求体缺失、JSON `null`、坏 JSON、字段类型错误、校验失败、分页越界 | 400 | 20001 | 请求格式或参数不正确 |
| 未认证或 token 无效 | 401 | 20003 | 请先登录 |
| 刷新/退出凭据未知、刷新会话过期/已撤销、旧刷新凭据重放 | 401 | 20003 | 请重新登录 |
| 已认证但没有标签管理员权限 | 403 | 20003 | 无权执行此操作 |
| 单条文章/评论/标签查询不存在，标签修改/删除不存在，评论点赞对象不存在 | 404 | 20001 | 资源不存在 |
| 控制器路由不支持的 HTTP 方法 | 405 | 20001 | 请求格式或参数不正确 |
| 请求体媒体类型不支持 | 415 | 20001 | 请求格式或参数不正确 |
| 登录、刷新、退出超过认证请求额度 | 429 | 20001 | 请求过于频繁，请稍后重试 |
| 认证限流 Redis 不可用或返回无效结果 | 503 | 20001 | 服务暂时不可用，请稍后重试 |
| 控制器执行中的未预期异常 | 500 | 20001 | 服务暂时不可用，请稍后重试 |

文章/评论修改和删除仍将“非作者或资源不存在”合并为 HTTP 200、`flag: false`、`code: 20003`，避免改变现有权限边界。登录请求为合法 JSON 对象但凭据缺失时，仍返回 HTTP 200、`code: 20002`；这与请求体无法解析的 HTTP 400 不同。

写操作输入约束：

- 新增文章：`title` 和 `content` 必填，不能是空字符串或纯空白。
- 新增评论：`articleid` 和 `content` 必填，不能是空字符串或纯空白。
- 新增标签：`labelname` 必填且非空白；`count`、`fans` 如提供则必须大于等于 0。
- 编辑文章的 `title`、`content`，评论的 `content`，标签的 `labelname`：未提供或为 `null` 时保留原值；提供字符串时不能为空白。标签编辑的 `count`、`fans` 如提供也必须大于等于 0。
- 本轮不新增最大文本长度、状态枚举、评论关联文章/父评论存在性约束；这些不属于已经实现的校验。

## 数据模型

以下字段来自当前接口实际接收或返回的 Java 对象。必填和编辑约束以“错误响应与输入边界”及具体接口说明为准；未明确声明的字段约束不代表后端已经实现。

### User

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `id` | string | 用户 ID |
| `mobile` | string | 手机号 |
| `password` | string | 密码；仅用于认证请求，服务端响应不会返回 |
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

### 认证限流

`POST /user/login`、`POST /user/refresh`、`POST /user/logout` 默认启用 Redis 共享限流。每个键从首次请求起使用 60 秒固定窗口，拒绝不延长窗口；成功、失败和无效请求均占用经过的检查点额度。默认额度：

| 维度 | 每窗口请求数 |
| --- | ---: |
| 登录来源 IP | 20 |
| 登录账号（不同 IP 共享） | 10 |
| 刷新来源 IP | 60 |
| 退出来源 IP | 30 |

- 三种端点的 IP 额度独立；账号检查发生在登录 JSON 解析后、密码查询前。IP 检查在 JWT 校验及请求体解析前，因此超限/限流存储故障优先返回 429/503，而非原本的 400/401。
- 来源使用服务端 `remoteAddr`，应用不读取客户端的 `X-Forwarded-For` 或 `Forwarded`。代理部署需由运维验证可信来源；共享 NAT/代理可能共用 IP 额度。
- 429/20001 响应带 `Retry-After`（剩余整秒，向上取整）、`Cache-Control: no-store`；跨域可读取 `Retry-After`。限流 Redis 故障时不放行，返回 503/20001、`Retry-After: 5`。具体阈值可由部署调整，前端不能硬编码倒计时。
- 429/503 不表示密码错误或会话失效，不应自动清除凭据或触发无限刷新。明确收到这些响应时，该次请求未进入认证业务，可在等待后由单飞协调器有限重试；这不改变网络超时/响应丢失时不能重试旧刷新凭据的规则。
- 退出收到 429/503 时尚未完成服务端撤销，不得显示服务端退出成功。429/503 响应均为 `flag: false`、`data: null`，不增加新的业务状态码。

### POST `/user/login`

用户登录。请求只使用 `mobile` 和 `password` 认证；其他 JSON 字段会被忽略，不参与用户匹配。成功后返回 Bearer JWT，前端应保存 token 并在后续受保护请求中发送 `Authorization` 请求头。

请求示例：

```json
{
  "mobile": "13800000000",
  "password": "example-password"
}
```

成功响应的 `data` 为脱敏用户资料和凭据，包含 `id`、`mobile`、`nickname`、`avatar`、`tokenType`、`token`、`expiresIn`、`refreshToken` 和 `refreshExpiresIn`。两个期限字段是响应时的剩余整秒数；`expiresIn` 不超过访问令牌配置期限，`refreshExpiresIn` 不超过 604800 秒。成功响应带 `Cache-Control: no-store`：

```json
{
  "flag": true,
  "code": 20000,
  "message": "登录成功",
  "data": {
    "id": "10001",
    "mobile": "13800000000",
    "nickname": "Alice",
    "avatar": "https://example.test/avatar.png",
    "tokenType": "Bearer",
    "token": "<access-token>",
    "expiresIn": 1800,
    "refreshToken": "<64-character-lowercase-hex-refresh-token>",
    "refreshExpiresIn": 604800
  }
}
```

手机号不存在、密码错误、手机号对应多条用户记录或请求缺少手机号/密码时，当前实现返回：

```json
{
  "flag": false,
  "code": 20002,
  "message": "手机号或密码错误",
  "data": null
}
```

存量明文密码在一次成功登录后会升级为 BCrypt 哈希；不会执行批量改密。密码升级、会话写入和访问 token 签发在同一事务内，失败不留下部分会话。当前仍没有注册或用户资料修改接口。

### POST `/user/refresh`

使用刷新凭据获取新的访问 token 和刷新凭据。只通过 JSON 请求体传入，不放入 URL；本接口不要求 `Authorization`，即使客户端附带过期的访问 token 也不参与鉴权：

```json
{
  "refreshToken": "<64-character-lowercase-hex-refresh-token>"
}
```

- `refreshToken` 必须为 64 位小写十六进制字符串，缺失、空白或格式不符返回 HTTP 400/20001。
- 成功返回 HTTP 200、`flag: true`、`code: 20000`、`message: "刷新成功"`，`data` 与登录成功结构相同；必须原子替换保存新的 `token`、`refreshToken` 和期限。
- 每个刷新凭据只能成功消费一次。已消费的旧凭据再次用于刷新时，返回 HTTP 401/20003，并撤销整个对应会话；同会话的新旧访问 token 和刷新能力都失效，不影响其他设备会话。
- 无此凭据、会话到期/已撤销或用户不存在时返回 HTTP 401/20003；随机伪造的凭据不会撤销其他会话。
- 前端必须单飞刷新并协调多个标签页，不能为多个失败请求并发调用刷新。并发请求中的第二次使用会被视为重放，导致第一次获得的新 token 同样失效。
- 网络超时导致结果不确定时，不自动重试旧刷新凭据，应清理本地凭据并重新登录。不要对刷新失败无限循环刷新。
- 响应带 `Cache-Control: no-store`；轮换与 JWT 签发在同一事务中，签发失败回滚消费标记。刷新成功本身不撤销同会话尚未过期的访问 token，重放或退出才撤销整个会话。

### POST `/user/logout`

请求体与 `/user/refresh` 相同，不要求可用访问 token，`Authorization` 头不参与身份判定。

- 已知的刷新凭据撤销它所属的当前会话，返回 HTTP 200、`flag: true`、`code: 20000`、`message: "退出成功"`、`data: null`。
- 同一会话的已消费刷新凭据也可用于撤销；摘要仍被保留时，重复退出保持成功，包括已经撤销或过期的已知会话。不退出该用户的其他会话。
- 有界清理默认关闭。启用后，绝对到期至少 24 小时（默认保留窗口，可由运维调整但不早于到期）的会话及摘要可逐批删除；已删除摘要的旧凭据视为未知，退出返回 401/20003，不再保证无限期幂等。清理不缩短 7 天会话期限，也不因提前撤销而提前删除重放检测摘要。
- 未知凭据返回 HTTP 401/20003，格式不符返回 HTTP 400/20001。数据库故障返回脱敏 HTTP 500，不得把它显示为服务端退出已完成。
- 响应带 `Cache-Control: no-store`。成功后清理本地访问/刷新凭据并关闭本地 WebSocket。撤销提交后新到达的 REST 鉴权与 IM 收发校验将拒绝该会话。

两个凭据都属于敏感信息，只经 HTTPS/WSS 传递，不写日志、埋点、错误报告或共享 URL。刷新凭据数据库仅存摘要，服务端不提供找回接口。前端不得把仅删除本地 token 等同于已完成服务端退出。

## 基础服务（9001）

### GET `/label`

查询全部标签。

成功时 `data` 为 `Label[]`。

### GET `/label/{id}`

按 ID 查询标签。不存在时返回 HTTP 404、`flag: false`、`code: 20001`、`message: "资源不存在"`。

### POST `/label`

新增标签。需要 `Authorization: Bearer <token>`，且 token 用户必须是数据库中存在的用户，并在 `tb_user_role` 中具有 `ADMIN` 角色。后端会覆盖请求体中的 `id` 并生成新 ID。

标签新增、修改、删除在输入校验通过后、读写标签数据前查询当前数据库角色；未授权返回 HTTP 403、`code: 20003`、`message: "无权执行此操作"`。角色查询失败返回 HTTP 500、`code: 20001` 和固定脱敏消息，不执行标签写入。无效请求体仍可能先返回 HTTP 400。

角色不从请求体或 JWT 角色字段采信，不缓存；降权提交后下一次角色查询生效，不承诺取消已通过授权的在途请求。登录响应不新增角色字段，前端不得把 HTTP 403 当成 token 过期或通过反复登录解决。公开标签查询不要求管理员权限。

请求体为 `Label`，`labelname` 必填且非空白，`count`、`fans` 如提供不能为负数，例如：

```json
{
  "labelname": "Java",
  "state": "1",
  "recommend": "1"
}
```

成功响应：`flag: true`、`code: 20000`、`message: "添加成功"`，`data` 为 `null`。

### PUT `/label/{id}`

修改标签。需要 `Authorization: Bearer <token>` 和数据库 `ADMIN` 角色。路径中的 `id` 会覆盖请求体中的 `id`；当前只更新请求体中非空的字段。

请求体为需要修改的 `Label` 字段，例如：

```json
{
  "labelname": "Java 后端",
  "recommend": "0"
}
```

如果 ID 不存在，返回 HTTP 404、`code: 20001`。`labelname` 如提供不能为空白，`count`、`fans` 如提供不能为负数。

### DELETE `/label/{id}`

删除标签。需要 `Authorization: Bearer <token>` 和数据库 `ADMIN` 角色。通过授权后 ID 不存在时返回 HTTP 404、`code: 20001`；非管理员返回 403，不查询标签是否存在。

## 文章服务（9004）

### GET `/article`

查询全部文章。成功时 `data` 为 `Article[]`。

### GET `/article/{articleId}`

按 ID 查询文章。不存在时返回 HTTP 404、`code: 20001`、`message: "资源不存在"`。

### POST `/article`

新增文章。需要 `Authorization: Bearer <token>`。后端生成 `id`，并使用 token 中的用户 ID 覆盖请求体中的 `userid`；`title` 和 `content` 必填且非空白，否则返回 HTTP 400、`code: 20001`。

### PUT `/article/{articleId}`

修改文章。需要 `Authorization: Bearer <token>`，且当前用户必须是文章作者。路径 ID 会覆盖请求体中的 `id`；作者只能修改内容字段，`userid`、统计字段、审核状态、置顶状态和发布时间由服务端维护。无权操作或文章不存在时返回 `flag: false`、`code: 20003`。

### DELETE `/article/{articleId}`

删除文章。需要 `Authorization: Bearer <token>`，且当前用户必须是文章作者。无权操作或文章不存在时返回 `flag: false`、`code: 20003`。

### POST `/article/search/{page}/{size}`

按条件分页查询文章。

- `page` 从 `1` 开始，必须大于等于 `1` 且在 Java int 范围内。
- `size` 必须在 `1` 到 `100` 之间，包含边界；越界或无法解析的分页参数返回 HTTP 400、`code: 20001`，不再自动纠正。
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

按评论 ID 查询评论。不存在时返回 HTTP 404、`code: 20001`、`message: "资源不存在"`。

### GET `/comment/article/{articleId}`

按文章 ID 查询评论列表，按发布时间倒序返回。成功时 `data` 为 `Comment[]`。

### POST `/comment`

新增评论。需要 `Authorization: Bearer <token>`。后端生成 `_id`、设置 `publishdate`，并把 `thumbup` 初始化为 `0`；`userid` 始终使用 token 中的用户 ID，忽略请求体中的值。请求体必须包含非空白的 `articleid` 和 `content`，`parentid` 可选。

请求示例：

```json
{
  "articleid": "20001",
  "content": "这是一条评论",
  "parentid": null
}
```

### PUT `/comment/{id}`

修改评论。需要 `Authorization: Bearer <token>`，且当前用户必须是评论作者。路径 ID 会写入请求体对象的 `_id`；作者只能修改 `content`，不能修改评论归属、父评论、`userid`、发布时间或点赞数。无权操作或评论不存在时返回 `flag: false`、`code: 20003`。

### DELETE `/comment/{id}`

按 ID 删除评论。需要 `Authorization: Bearer <token>`，且当前用户必须是评论作者。无权操作或评论不存在时返回 `flag: false`、`code: 20003`。

### PUT `/comment/thumbup/{id}`

评论点赞。需要 `Authorization: Bearer <token>`，后端使用 token 中的用户 ID 判断重复点赞。

- 首次点赞通常返回 `flag: true`、`code: 20000`、`message: "点赞成功"`。
- 同一用户再次点赞返回 `flag: false`、`code: 20004`、`message: "不能重复点赞"`。
- 评论不存在时返回 HTTP 404、`flag: false`、`code: 20001`、`message: "资源不存在"`。
- 点赞关系写入 MySQL 的 `tb_comment_thumbup` 唯一关系表，计数在同一事务中原子递增；Redis 仅作为提交后的短期兼容缓存，Redis 不可用不改变数据库结果。
- 旧版本 `thumbup_<userId>_<commentId>` Redis 记录会在首次请求时登记到关系表但不会再次增加计数；部署前必须先执行 [mysql-thumbup-migration.sql](mysql-thumbup-migration.sql)。
- 数据库事务失败不会写入新的 Redis 缓存；关系表唯一键负责并发去重，避免 Redis 残留导致永久误判或重复计数。

按评论 ID和按文章 ID 查询使用不同的路径模板，前端应使用 `/comment/{id}` 查询单条评论，使用 `/comment/article/{articleId}` 查询文章评论列表；不要通过同一个路径推断查询语义。

## WebSocket 即时通讯（9008）

### 连接地址

```text
ws://localhost:9008/im?token=<access-token>
```

HTTPS 页面使用 `wss://`。浏览器 WebSocket 握手当前通过 URL 查询参数携带 token；token 必须经过 URL 编码。`user` 查询参数不再作为身份来源，缺少或无效 token 时连接会被拒绝。连接建立后的 `ready.user` 是 token 对应的用户 ID。

- 握手验证 JWT 和持久化会话；失效返回 HTTP 401，数据库不可用返回 HTTP 503。浏览器不一定能取得握手的 HTTP 状态，应结合登录状态处理连接失败。
- 连接建立后，在入站消息、出站投递前再次检查访问 token 到期时间及数据库会话；失效关闭码为 `1008`，校验过程异常关闭码为 `1011`，不继续转发消息。
- 空闲连接每轮清理完成后间隔 30 秒检查；实际发现时间还受数据库响应与调度影响，不承诺严格 30 秒内关闭。在途消息不保证撤回。
- 刷新不会更新既有连接的访问 token 到期时间；应使用新访问 token 重连。相同用户建立新连接时，旧连接以 `1000` 关闭。

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

当前 WebSocket 连接和消息仅保存在服务进程内存中，服务重启后丢失；认证会话及撤销状态存储在 MySQL，不随服务重启清空。URL 访问 token 可能被代理或访问日志记录，必须在部署层脱敏，后续仍需改进凭据传递方式。刷新凭据不得用于 WebSocket URL。

## 当前集成限制

- 已支持持久化登录会话、刷新轮换和当前会话退出；没有全设备退出或管理员撤销接口。过期会话/历史刷新摘要的有界清理已实现但默认关闭，须经运维确认保留策略、数据库权限和删除授权后开启，见 [admin-operations.md](admin-operations.md)。
- 认证限流依赖用户服务 Redis，默认开启且故障关闭；当前自动化测试覆盖 MVC、模拟 Redis 和 H2 清理，真实 Redis Lua、多实例网络和 MySQL 清理验收尚未执行。固定窗口不是滑动窗口，窗口边界允许短时突发；不替代网关全局流量限制。
- 文章和评论继续要求作者本人；数据库 `ADMIN` 目前仅用于标签管理，不拥有代改文章或评论的权限。角色管理无公开接口，初始化流程见 [admin-operations.md](admin-operations.md)。
- 评论点赞使用 MySQL 唯一关系表和原子计数，Redis 仅用于提交后的短期兼容缓存；部署前需要执行 [mysql-thumbup-migration.sql](mysql-thumbup-migration.sql)。已在隔离临时数据上通过真实 MySQL、Redis 和 HTTP 旧键迁移验收；生产部署仍需按目标环境复核连接、密钥和数据备份策略。
- WebSocket token 暂通过 URL 查询参数传递，且消息仍只保存在进程内存中。
- 已有基础必填、非空白、非负计数和分页校验；最大文本长度、状态枚举、关联资源存在性仍未全面校验。
- 已通过 H2 完整登录/刷新/退出事务与 MVC 测试、IM 处理器撤销测试；此前会话核心的真实 MySQL 事务验收已通过。本轮未执行真实浏览器、多实例 IM 或全链路 MySQL/Redis 联调。
- 文章服务需要 MySQL 和 Redis；所有业务服务的具体连接配置属于部署环境，不写入前端代码或文档。
- 升级前在目标库审核并执行附加认证表迁移；三个服务必须使用相同的认证表/用户表、JWT 密钥及一致的时钟。协调部署三个服务，禁止混用仍仅验签的旧实例；部署后旧客户端必须重登。迁移不自动执行，也不自动授予管理员。

## 契约维护规则

接口变更的同步规则写在根目录 [AGENTS.md](../AGENTS.md)。每次接口变更后，前端 agent 应以 `frontend-api-status.json` 的 `revision` 作为是否需要同步的唯一快速判断依据，再阅读变更日志和本文档。