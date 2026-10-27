# HowBlog 前端接口契约

> 当前契约版本：`8.0.0`
> 当前同步 revision：`23`
> 最后更新：`2026-10-27`
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

以下地址是本地开发默认地址。部署环境应替换主机名或端口，不要把部署地址硬编码到前端代码中。三个服务的端口可用 `HOW_USER_PORT`、`HOW_BASE_PORT`、`HOW_ARTICLE_PORT` 覆盖，默认值不变。

| 服务 | 本地 Base URL | 端口 | 职责 |
| --- | --- | ---: | --- |
| 用户服务 | `http://localhost:9008` | 9008 | 登录、WebSocket 即时通讯 |
| 基础服务 | `http://localhost:9001` | 9001 | 标签管理 |
| 文章服务 | `http://localhost:9004` | 9004 | 文章、评论、评论点赞 |

REST 接口路径均相对于对应服务的 Base URL。例如，文章列表的本地地址是 `http://localhost:9004/article`。

## 通用 HTTP 约定

- 请求体和成功响应使用 JSON；发送请求时使用 `Content-Type: application/json`。
- 三个服务默认允许所有跨域来源（`Access-Control-Allow-Origin: *`），可用环境变量 `HOW_CORS_ALLOWED_ORIGINS` 配置逗号分隔的来源列表，WebSocket 握手使用同一配置；部署环境仍应根据网关和安全策略限制来源。
- 每个 HTTP 响应都带 `X-Request-Id` 响应头，值为该请求的关联 ID，同一个 ID 也写入服务端日志；跨域响应已暴露该响应头。客户端可以自带 `X-Request-Id` 传入自己的追踪 ID，但只接受 1 到 64 位的字母、数字、`-`、`_`、`.`；不符合要求或未提供时，服务端生成新的 ID。
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
| 注册时手机号已存在 | 200 | 20005 | 手机号已注册 |
| 修改密码时旧密码不正确 | 200 | 20002 | 旧密码不正确 |
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

- 注册：`mobile` 必填且为 11 位以 `1` 开头的数字；`password` 必填、8 到 64 个字符且 UTF-8 编码不超过 72 字节；`nickname` 可选、最长 100 个字符。
- 修改资料：`nickname` 最长 100、`avatar` 最长 500、`sex` 最长 16、`email` 最长 255 且需为合法邮箱、`interest` 与 `personality` 最长 500；提供的字符串不能为纯空白，未提供的字段保留原值。
- 修改密码：`oldPassword` 必填且需与当前密码一致；`newPassword` 必填、8 到 64 个字符、UTF-8 编码不超过 72 字节且不能为纯空白。
- 新增文章：`title` 和 `content` 必填，不能是空字符串或纯空白。
- 文章 `content` 按纯文本处理，前端必须转义后显示并保留换行，不直接渲染为 HTML。存量 HTML 字符串不会批量转换，标签按文字显示。
- 文章 `ispublic` 如提供只能为字符串 `"0"`（私密）或 `"1"`（公开）；新增时省略/null 默认 `"1"`，编辑时省略/null 保留原值，其他值返回 HTTP 400。历史 null 按公开处理，但仍受发布状态限制。
- 文章 `labelIds` 如提供必须是标签 ID 字符串数组；元素会去除首尾空白并去重，空字符串或不存在的标签返回 HTTP 400。
- 新增评论：`articleid` 和 `content` 必填且非空白；文章必须存在且已发布；可选 `parentid` 必须指向同篇文章内存在的评论。
- 新增标签：`labelname` 必填且非空白；`count`、`fans` 如提供则必须大于等于 0。
- 编辑文章的 `title`、`content`，评论的 `content`，标签的 `labelname`：未提供或为 `null` 时保留原值；提供字符串时不能为空白。标签编辑的 `count`、`fans` 如提供也必须大于等于 0。
- 文章与评论正文尚未增加最大文本长度约束；评论已校验关联文章及父评论存在性，不能通过编辑更换归属。

## 服务健康检查

三个服务都提供无需认证的 `GET /actuator/health`，供部署探活和容器健康检查使用：

```json
{"status":"UP"}
```

该接口不使用 `Result` 统一响应体，只返回 `status`（依赖异常时为 `DOWN`），不包含数据库、Redis 等依赖细节，也不受认证和业务限流影响。它不是业务接口，前端不应依赖它的响应结构。

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

### UserSummary

粉丝/关注列表返回的公开摘要，不含手机号、邮箱、生日与密码。

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `id` | string | 用户 ID |
| `nickname` | string | 昵称 |
| `avatar` | string | 头像地址 |
| `sex` | string | 性别 |
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
| `createtime` | datetime/null | 首次发布时间；草稿为 null，首次发布时由服务端写入 |
| `updatetime` | datetime/null | 更新时间；由服务端维护 |
| `ispublic` | string | 是否公开 |
| `istop` | string | 是否置顶；新增时服务端置为 `0` |
| `visits` | integer/null | 浏览量；新增时服务端初始化为 `0`，非作者读取已发布文章时服务端累加 |
| `thumbup` | integer/null | 点赞数；新增时服务端初始化为 `0`，文章点赞接口原子累加 |
| `comment` | integer/null | 评论总数（包含回复）；新增文章时为 `0`，新增评论原子加 1，删除分支按实际删除数原子扣减且不小于 0；不自动校准历史偏差 |
| `state` | string | 文章状态：`0` 草稿、`1` 已发布、`2` 待审核；由服务端维护 |
| `channelid` | string | 频道 ID |
| `url` | string | 文章 URL |
| `type` | string | 文章类型 |
| `labelIds` | string[] | 文章关联的标签 ID；读取文章时返回，标签名称需通过基础服务查询 |

### Comment

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `_id` | string | 评论 ID；新增时由后端生成 |
| `articleid` | string | 所属文章 ID |
| `content` | string | 评论内容 |
| `userid` | string | 用户 ID |
| `parentid` | string/null | 父评论 ID，必须存在且属于同篇文章；省略、null 或纯空白表示顶级评论，保存为 null |
| `publishdate` | datetime/null | 发布时间；新增时由后端设置 |
| `thumbup` | integer/null | 点赞数；新增时初始化为 0 |

## 用户服务（9008）

### 认证限流

`POST /user/login`、`POST /user/register`、`POST /user/password`、`POST /user/refresh`、`POST /user/logout` 默认启用 Redis 共享限流。每个键从首次请求起使用 60 秒固定窗口，拒绝不延长窗口；成功、失败和无效请求均占用经过的检查点额度。默认额度：

| 维度 | 每窗口请求数 |
| --- | ---: |
| 登录来源 IP | 20 |
| 登录账号（不同 IP 共享） | 10 |
| 注册来源 IP | 10 |
| 修改密码来源 IP | 10 |
| 刷新来源 IP | 60 |
| 退出来源 IP | 30 |

- 五种端点的 IP 额度独立（登录、注册、修改密码、刷新、退出）；账号检查发生在登录 JSON 解析后、密码查询前。IP 检查在 JWT 校验及请求体解析前，因此超限/限流存储故障优先返回 429/503，而非原本的 400/401。
- 来源使用服务端 `remoteAddr`，应用不读取客户端的 `X-Forwarded-For` 或 `Forwarded`。代理部署需由运维验证可信来源；共享 NAT/代理可能共用 IP 额度。
- 429/20001 响应带 `Retry-After`（剩余整秒，向上取整）、`Cache-Control: no-store`；跨域可读取 `Retry-After`。限流 Redis 故障时不放行，返回 503/20001、`Retry-After: 5`。具体阈值可由部署调整，前端不能硬编码倒计时。
- 429/503 不表示密码错误或会话失效，不应自动清除凭据或触发无限刷新。明确收到这些响应时，该次请求未进入认证业务，可在等待后由单飞协调器有限重试；这不改变网络超时/响应丢失时不能重试旧刷新凭据的规则。
- 退出收到 429/503 时尚未完成服务端撤销，不得显示服务端退出成功。429/503 响应均为 `flag: false`、`data: null`，不增加新的业务状态码。

### POST `/user/register`

用户注册。公开接口，不需要 `Authorization`；成功只创建账号，不自动登录，注册成功后应使用 `POST /user/login` 获取凭据。

请求体：

```json
{
  "mobile": "13900000000",
  "password": "example-password",
  "nickname": "Bob"
}
```

- `mobile` 必填，必须为 11 位数字且以 `1` 开头。
- `password` 必填，长度 8 到 64 个字符（按 Java 字符串长度计），不能为纯空白，且 UTF-8 编码不超过 72 字节；服务端只保存 BCrypt 哈希，从不回显或返回密码。超过字节上限返回 HTTP 400/20001，即使字符数未超限。
- `nickname` 可选，最长 100 个字符；首尾空白会被去除，去除后为空时按未提供处理。
- 未知 JSON 字段会被忽略。请求体缺失、坏 JSON 或字段不符合上述规则返回 HTTP 400、`code: 20001`、`message: "请求格式或参数不正确"`。

成功响应：

```json
{
  "flag": true,
  "code": 20000,
  "message": "注册成功",
  "data": null
}
```

手机号已存在（包括历史数据中同一手机号对应多条用户记录的情况）返回 HTTP 200：

```json
{
  "flag": false,
  "code": 20005,
  "message": "手机号已注册",
  "data": null
}
```

注册同样受来源 IP 限流（默认每 60 秒 10 次），429/503 行为与登录一致。手机号在数据库层有唯一约束（`uk_tb_user_mobile`）：应用层查重给出常规的 `20005`，并发竞争由唯一约束兜底，同样返回 `20005` 而不是 500，因此同一手机号最多只会存在一条注册记录。后端不提供注册后的邮箱或短信验证。

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

存量明文密码只在完整匹配后的成功登录中升级，不执行批量改密。UTF-8 长度不超过 72 字节的存量密码升级为直接 BCrypt；超过 72 字节的历史明文升级为带 `{bcrypt-sha256}` 标记的 BCrypt(SHA-256 UTF-8 完整密码的十六进制摘要)，继续校验完整输入。标记格式由服务端识别，客户端不预哈希。密码升级、会话写入和访问 token 签发在同一事务内，失败不留下部分会话。

直接 BCrypt 账号的登录候选密码超过 72 UTF-8 字节时，返回原有 HTTP 200/20002 密码错误，避免忽略尾部字符；历史标记格式允许输入完整的原密码。若历史账号已被直接 BCrypt 截断，原后缀无法恢复，需运维受控重置，不自动迁移或猜测原密码。登录与改密读取最新用户行；并发使用同一旧密码改成不同新密码时，后提交者重新验证并返回旧密码错误。

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

### GET `/user/profile`

查看当前登录用户的完整资料。需要 `Authorization: Bearer <token>`。响应 `data` 为本人资料的安全视图，包含 `id`、`mobile`、`nickname`、`sex`、`avatar`、`email`、`birthday`、`regdate`、`updatedate`、`lastdate`、`online`、`interest`、`personality`、`fanscount`、`followcount`；不包含密码。时间字段按 Java `Date` 序列化，与其它接口一致：

```json
{
  "flag": true,
  "code": 20000,
  "message": "查询成功",
  "data": {
    "id": "10001",
    "mobile": "13800000000",
    "nickname": "Alice",
    "sex": "1",
    "avatar": "https://example.test/avatar.png",
    "email": "alice@example.test",
    "birthday": "1995-06-17T00:00:00.000+00:00",
    "regdate": "2026-10-14T00:00:00.000+00:00",
    "fanscount": 0,
    "followcount": 0
  }
}
```

未携带或携带无效 token 返回 HTTP 401、`code: 20003`；token 有效但用户已不存在时同样返回 HTTP 401、`message: "请先登录"`。

### PUT `/user/profile`

修改当前登录用户的资料。需要 `Authorization: Bearer <token>`。请求体只需提供要修改的字段；未提供或为 `null` 的字段保留原值，未知字段以及不可修改字段（`id`、`mobile`、`password`、`regdate`、`updatedate`、`lastdate`、`online`、`fanscount`、`followcount`）会被忽略。

```json
{
  "nickname": "Bob",
  "avatar": "https://example.test/new.png",
  "sex": "1",
  "birthday": "1995-06-17T00:00:00.000Z",
  "email": "bob@example.test",
  "interest": "Java",
  "personality": "安静"
}
```

- 字符串字段不能为纯空白，首尾空白会被去除；`nickname` 最长 100，`avatar` 最长 500，`sex` 最长 16，`email` 最长 255 且需为合法邮箱格式，`interest`、`personality` 最长 500。
- `birthday` 接受与响应一致的日期字符串或毫秒时间戳；不能通过该接口清空已有生日。
- 校验失败返回 HTTP 400、`code: 20001`；认证失败返回 HTTP 401、`code: 20003`。
- 成功返回 HTTP 200、`code: 20000`、`message: "修改成功"`，`data` 为修改后的完整资料（结构同 `GET /user/profile`）。

修改手机号不在本接口范围，密码修改使用 `POST /user/password`；后端不提供头像文件上传，`avatar` 只是 URL 字符串。

### POST `/user/password`

修改当前登录用户的密码。需要 `Authorization: Bearer <token>`。请求体：

```json
{
  "oldPassword": "example-password",
  "newPassword": "new-example-password"
}
```

- `oldPassword` 必填，必须与当前密码一致，否则返回 HTTP 200、`flag: false`、`code: 20002`、`message: "旧密码不正确"`。
- `newPassword` 必填，长度 8 到 64 个字符（按 Java 字符串长度计），不能为纯空白，且 UTF-8 编码不超过 72 字节；服务端只保存 BCrypt 哈希，未强制新密码与旧密码不同。
- `oldPassword` 对直接 BCrypt 账号也拒绝超过 72 UTF-8 字节的候选（HTTP 200/20002）；历史 `{bcrypt-sha256}` 标记账号校验完整原密码。
- 请求体缺失、坏 JSON 或字段不符合规则返回 HTTP 400、`code: 20001`；未认证返回 HTTP 401、`code: 20003`。
- 成功返回 HTTP 200、`code: 20000`、`message: "修改成功"`、`data: null`。

成功修改后，该用户在其他设备上的所有会话立即被撤销：这些设备的访问 token 再使用时返回 401、刷新凭据也失效（IM 连接在下一次消息或空闲检查时以 `1008` 关闭），需要重新登录。发起修改的当前会话保持有效，其访问 token 和刷新凭据继续可用。修改密码受来源 IP 限流（默认每 60 秒 10 次），429/503 行为与登录一致。

### GET `/user/follow/{userId}`

查询当前登录用户是否已关注目标用户，需要 `Authorization: Bearer <token>`。成功返回 `Result`，`code: 20000`、`message: "查询成功"`、`data: {"following": true}`（未关注为 `false`），响应带 `Cache-Control: no-store`。目标用户不存在返回 HTTP 404/20001，未认证返回 HTTP 401/20003；查询不创建关系或修改计数。前端进入文章详情或切换账号时读取状态，失败时显示重试入口，状态未知时不发起关注/取关。

### PUT `/user/follow/{userId}`

关注指定用户。需要 `Authorization: Bearer <token>`。不能关注自己：返回 HTTP 200、`flag: false`、`code: 20001`、`message: "不能关注自己"`。目标用户不存在返回 HTTP 404、`code: 20001`、`message: "资源不存在"`。重复关注返回 HTTP 200、`flag: false`、`code: 20005`、`message: "已关注"`（计数不变）。成功后服务端在 `tb_user_follow` 登记唯一关系，并对被关注者 `fanscount`、关注者 `followcount` 原子加 1，返回 `code: 20000`、`message: "关注成功"`。

### DELETE `/user/follow/{userId}`

取消关注。需要 `Authorization: Bearer <token>`。目标用户不存在返回 HTTP 404、`code: 20001`。未关注时返回 HTTP 200、`flag: false`、`code: 20005`、`message: "未关注"`（计数不变）。成功后删除关系并对被关注者 `fanscount`、关注者 `followcount` 原子减 1（不小于 0），返回 `code: 20000`、`message: "已取消关注"`。

### GET `/user/{userId}/fans/{page}/{size}`

查询指定用户的粉丝列表（关注该用户的人）。公开接口，无需 token。

### GET `/user/{userId}/follows/{page}/{size}`

查询指定用户关注的用户列表。公开接口，无需 token。

- 两个列表接口的 `page` 从 `1` 开始、`size` 在 `1` 到 `100` 之间（含边界）；越界或无法解析返回 HTTP 400、`code: 20001`。
- 目标用户不存在返回 HTTP 404、`code: 20001`、`message: "资源不存在"`。
- 成功返回 `data` 为 `PageResult<UserSummary>`（`total` 与 `rows`），每项为公开摘要，不含手机号、邮箱、生日与密码。

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

查询公开文章列表。仅返回已发布（`state` 为 `1`）且公开（`ispublic` 为 `"1"` 或历史 null）的文章；私密文章、草稿、待审核不出现在该列表，即使携带作者 token。成功时 `data` 为 `Article[]`，顺序为 `istop`、`updatetime`、`createtime`、`id` 依次倒序（置顶优先、最近更新优先、ID 打破时间相同的平局）。

### GET `/article/{articleId}`

按 ID 查询文章。只有已发布且公开（`ispublic` 为 `"1"` 或历史 null）的文章对所有人可见；私密文章（包括已发布）、草稿与待审核文章只有作者本人或管理员（携带其 `Authorization: Bearer <token>`）可见。文章不存在、或文章非公开而访问者既不是作者也不是管理员时，返回 HTTP 404、`code: 20001`、`message: "资源不存在"`。响应中的 `labelIds` 为该文章当前关联的标签 ID。读取已发布文章时，若访问者不是作者（包括未登录访问者），服务端对 `visits` 执行数据库端原子加 1，响应中的 `visits` 为累加后的值；作者读取自己的文章、读取草稿或待审核文章均不计入。该接口因此具有副作用，不保证幂等。

### POST `/article`

新增文章，创建为草稿。需要 `Authorization: Bearer <token>`。后端生成 `id`，并使用 token 中的用户 ID 覆盖请求体中的 `userid`；`title` 和 `content` 必填且非空白，否则返回 HTTP 400、`code: 20001`。文章以草稿创建，`ispublic` 未提供时默认为 `"1"`：`state` 固定为 `0`、`createtime` 为 `null`、`updatetime` 为服务端当前时间、`istop` 为 `0`、`visits`/`thumbup`/`comment` 均为 `0`，请求体中的这些字段一律被忽略；`columnid`、`image`、`ispublic`、`channelid`、`url`、`type` 由请求体提供。可选的 `labelIds` 会在同一事务中写入文章标签关系，并使每个标签的 `count` 原子加 1；标签不存在时整个请求返回 HTTP 400 且不保存文章。成功返回 `flag: true`、`code: 20000`、`message: "添加成功"`。

### PUT `/article/{articleId}`

修改文章。需要 `Authorization: Bearer <token>`，且当前用户必须是文章作者。路径 ID 会覆盖请求体中的 `id`；作者只能修改内容字段（`columnid`、`title`、`content`、`image`、`ispublic`、`channelid`、`url`、`type`）。`labelIds` 如省略则保留原关联，如提供则整体替换，空数组表示清空；标签关联变化与每个标签 `count` 的原子增减在同一事务中完成。未知标签返回 HTTP 400 且不保存文章。`userid`、`state`、`createtime`、`updatetime`、统计字段和置顶状态由服务端维护。编辑不会改变文章的草稿/已发布状态。无权操作或文章不存在时返回 `flag: false`、`code: 20003`。

### PUT `/article/{articleId}/publish`

发布文章。需要 `Authorization: Bearer <token>`，且当前用户必须是文章作者。将文章状态置为已发布（`state` 为 `1`）；若尚未设置过 `createtime`，服务端写入当前时间作为首次发布时间，之后重复发布不会覆盖该时间；`updatetime` 更新为服务端当前时间。无权操作或文章不存在时返回 `flag: false`、`code: 20003`；成功返回 `code: 20000`、`message: "发布成功"`。

### PUT `/article/{articleId}/unpublish`

下架文章。需要 `Authorization: Bearer <token>`，且当前用户必须是文章作者。将文章状态退回草稿（`state` 为 `0`），保留 `createtime`，并更新 `updatetime`。无权操作或文章不存在时返回 `flag: false`、`code: 20003`；成功返回 `code: 20000`、`message: "已下架"`。

### PUT `/article/{articleId}/submit`

提交审核。需要 `Authorization: Bearer <token>`，且当前用户必须是文章作者。仅允许从草稿提交：将状态置为待审核（`state` 为 `2`）。非作者或文章不存在返回 `flag: false`、`code: 20003`；文章不是草稿时返回 HTTP 200、`flag: false`、`code: 20001`、`message: "文章状态不允许该操作"`；成功返回 `code: 20000`、`message: "已提交审核"`。

### PUT `/article/{articleId}/approve`

审核通过（仅管理员）。需要 `Authorization: Bearer <token>`，且当前用户必须具有数据库 `ADMIN` 角色，否则返回 HTTP 403、`code: 20003`、`message: "无权执行此操作"`。仅待审核（`state` 为 `2`）的文章可审核：置为已发布（`state` 为 `1`），若尚未设置 `createtime` 则写入当前时间。文章不存在返回 HTTP 404、`code: 20001`；状态不符返回 HTTP 200、`flag: false`、`code: 20001`、`message: "文章状态不允许该操作"`；成功返回 `code: 20000`、`message: "审核通过"`。

### PUT `/article/{articleId}/reject`

审核驳回（仅管理员）。权限与存在性规则同 `approve`。将待审核文章退回草稿（`state` 为 `0`），保留 `createtime`。成功返回 `code: 20000`、`message: "已驳回"`。

### PUT `/article/thumbup/{articleId}`

文章点赞。需要 `Authorization: Bearer <token>`。仅已发布（`state` 为 `1`）且对当前用户可见的文章可点赞：服务端在唯一关系表 `tb_article_thumbup` 登记 `(articleId, userId)`，并对 `tb_article.thumbup` 执行数据库端原子加 1，同一用户对同一文章只能成功点赞一次。首次成功返回 `code: 20000`、`message: "点赞成功"`；重复点赞返回 HTTP 200、`flag: false`、`code: 20004`、`message: "不能重复点赞"`（计数不变）；文章不存在、非已发布或对当前用户不可见返回 HTTP 404、`code: 20001`、`message: "资源不存在"`。当前不提供取消点赞接口。

### DELETE `/article/{articleId}`

删除文章。需要 `Authorization: Bearer <token>`，且当前用户必须是文章作者。删除时在同一事务内清理文章标签关系、全部评论及回复、评论点赞关系和文章点赞关系，并扣减对应标签使用数；任何步骤失败整体回滚；无权操作或文章不存在时返回 `flag: false`、`code: 20003`。

### POST `/article/search/{page}/{size}`

按条件分页查询文章。

- `page` 从 `1` 开始，必须大于等于 `1` 且在 Java int 范围内。
- `size` 必须在 `1` 到 `100` 之间，包含边界；越界或无法解析的分页参数返回 HTTP 400、`code: 20001`，不再自动纠正。
- 请求体是 JSON 对象，后端对字段做精确相等匹配，不是模糊搜索。
- 数据库在分页前统一按 `istop`、`updatetime`、`createtime`、`id` 依次倒序；前端保持返回顺序，不再仅对当前页另行排序。固定数据集翻页顺序稳定；这是偏移分页，有并发新增/编辑时不提供快照语义。
- 当前允许的条件字段为：`id`、`columnid`、`userid`、`title`、`content`、`image`、`createtime`、`updatetime`、`ispublic`、`istop`、`visits`、`thumbup`、`comment`、`state`、`channelid`、`url`、`type`；`labelIds` 是特殊筛选字段，不做文章表精确匹配。
- `labelIds` 如提供必须是标签 ID 字符串数组；文章必须同时关联数组中的全部标签才会返回。数组为空等同于未提供；标签不存在返回 HTTP 400、`code: 20001`。
- 未知字段会被忽略；值为 `null` 的字段会被忽略。
- 可见性：未携带 token 时只返回已发布且公开的文章；携带有效 token 时，除公开已发布文章外还可返回当前用户自己的文章（含私密、草稿、待审核）；管理员（数据库 `ADMIN` 角色）不受该限制，可查询任意状态的文章（例如用 `state: "2"` 列出待审核队列）。

请求示例：

```http
POST /article/search/1/10
Content-Type: application/json
```

```json
{
  "labelIds": ["java", "spring"],
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

查询当前访问者可见的评论。成功时 `data` 为 `Comment[]`，按发布时间、ID 倒序；匿名访问仅包含已发布且公开文章的评论，带有效 token 时额外包含本人文章的评论，数据库 `ADMIN` 可见所有存在文章的评论。无对应文章的历史孤立评论不返回。

### GET `/comment/{id}`

按评论 ID 查询评论。评论及所属文章均需存在；文章已发布且公开时公开可读，私密/草稿/待审核文章的评论仅文章作者或数据库 `ADMIN` 可读，其他人按资源不存在处理。不存在或不可见时返回 HTTP 404、`code: 20001`、`message: "资源不存在"`。

### GET `/comment/article/{articleId}`

按文章 ID 查询评论平铺列表，按发布时间倒序返回。成功时 `data` 为 `Comment[]`，仍通过 `parentid` 表示回复关系。所属文章必须存在且对访问者可见，可见性同单条评论；不存在或不可见时返回 HTTP 404/20001（不再返回空数组），存在且无评论时返回空数组。

### POST `/comment`

新增评论。需要 `Authorization: Bearer <token>`。后端生成 `_id`、设置 `publishdate`，并把 `thumbup` 初始化为 `0`；`userid` 始终使用 token 中的用户 ID，忽略请求体中的值。请求体必须包含非空白的 `articleid` 和 `content`，`parentid` 可选。

- 所属文章必须存在、已发布（`state: "1"`）且对当前用户可见，否则返回 HTTP 404/20001，包括作者自己的草稿/待审核文章。私密已发布文章仅文章作者和数据库 `ADMIN` 可新增评论或回复。
- `parentid` 省略、null 或纯空白时保存为 null；非空值会去除首尾空白，父评论不存在或属于另一篇文章返回 HTTP 400/20001。
- 评论保存与所属文章 `comment` 原子加 1 在同一事务内完成，失败不留下部分评论或计数；计数包含所有层级回复。

请求示例：

```json
{
  "articleid": "20001",
  "content": "这是一条评论",
  "parentid": null
}
```

### PUT `/comment/{id}`

修改评论。需要 `Authorization: Bearer <token>`，且当前用户必须是评论作者。路径 ID 会写入请求体对象的 `_id`；作者只能修改 `content`，不能修改评论归属、父评论、`userid`、发布时间或点赞数。无权操作、评论不存在或所属文章不存在时返回 `flag: false`、`code: 20003`。

### DELETE `/comment/{id}`

按 ID 删除评论。需要 `Authorization: Bearer <token>`，且当前用户必须是该评论作者（管理员和文章作者没有代删权限）。成功时物理删除该评论及其全部后代回复，即使回复由其他用户发表；仅影响同篇文章中的这个分支，保留其他分支。评论点赞关系一起清理，文章 `comment` 按实际删除条数原子扣减且不低于 0，整个操作同事务提交。文章下架后，评论作者仍可修改或删除本人评论。无权操作、评论不存在或所属文章不存在时返回 `flag: false`、`code: 20003`。客户端应在删除后刷新整篇评论列表和文章统计。

### PUT `/comment/thumbup/{id}`

评论点赞。需要 `Authorization: Bearer <token>`，后端使用 token 中的用户 ID 判断重复点赞。

- 首次点赞通常返回 `flag: true`、`code: 20000`、`message: "点赞成功"`。
- 同一用户再次点赞返回 `flag: false`、`code: 20004`、`message: "不能重复点赞"`。
- 评论不存在、所属文章不存在、尚未发布或对当前用户不可见时返回 HTTP 404、`flag: false`、`code: 20001`、`message: "资源不存在"`。
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
- 认证限流依赖用户服务 Redis，默认开启且故障关闭；已验证 MVC、模拟 Redis、H2 清理和隔离 Redis Lua 固定窗口行为，本轮真实浏览器登录/刷新/退出也经过 Redis。多实例网络和真实 MySQL 清理验收尚未执行。固定窗口不是滑动窗口，窗口边界允许短时突发；不替代网关全局流量限制。
- 文章和评论的内容修改/删除继续要求作者本人；数据库 `ADMIN` 用于标签管理、文章审核及读取非公开文章/其评论，不拥有代改文章内容或评论的权限。角色管理无公开接口，初始化流程见 [admin-operations.md](admin-operations.md)。
- 评论点赞使用 MySQL 唯一关系表和原子计数，Redis 仅用于提交后的短期兼容缓存；部署前需要执行 [mysql-thumbup-migration.sql](mysql-thumbup-migration.sql)。已在隔离临时数据上通过真实 MySQL、Redis 和 HTTP 旧键迁移验收；生产部署仍需按目标环境复核连接、密钥和数据备份策略。
- WebSocket token 暂通过 URL 查询参数传递，且消息仍只保存在进程内存中。
- 已有基础必填、非空白、非负计数和分页校验；文章正文按转义纯文本显示，暂不提供 HTML 富文本渲染；评论已增加文章与父评论存在性和同文章归属校验。评论仍返回平铺数组，未增加评论树分页、审核或通知接口，正文最大长度尚未全面限制。
- 评论计数从本版本起对新增/删除操作维护，不重算历史计数、不批量清理孤立评论。历史偏差须在另行核对和授权后处理。
- 用户与文章写操作在事务内锁定目标行；资料更新不回写未修改的密码和统计字段，文章编辑/状态变更不覆盖浏览、点赞、评论计数。同一文章的详情读取、写入和评论操作可能因行锁等待。
- 文章已有草稿/已发布/待审核状态与作者权限：新增即为草稿，`publish`/`unpublish`/`submit` 仅作者可调用，`approve`/`reject` 仅数据库 `ADMIN` 可调用，公开列表只返回已发布且公开文章，私密、草稿与待审核仅作者或管理员可见。作者仍可自行 `publish`，审核（`submit` → `approve`/`reject`）为可选路径；未授予任何真实账号管理员权限，未授权时审核接口对所有用户返回 403。
- 文章浏览量在 `GET /article/{articleId}` 读取已发布文章时累加（排除作者本人），没有按用户或 IP 去重，重复刷新、预取或爬虫会重复计数；草稿与待审核文章不计入。
- 文章点赞使用唯一关系表 `tb_article_thumbup`（部署前执行 [mysql-article-thumbup-migration.sql](mysql-article-thumbup-migration.sql)，可重复执行）；历史 `tb_article.thumbup` 计数保留、不重算、不补关系，历史点赞用户仍可再次点赞。暂不支持取消点赞或点赞用户列表。
- 关注使用唯一关系表 `tb_user_follow`（部署前执行 [mysql-user-follow-migration.sql](mysql-user-follow-migration.sql)，可重复执行）；`fanscount`/`followcount` 历史计数保留、不重算、不补关系。粉丝/关注列表只返回公开摘要（`id`、`nickname`、`avatar`、`sex`、`interest`、`personality`、`fanscount`、`followcount`），不含手机号、邮箱与生日。
- 用户表已有手机号唯一约束（`uk_tb_user_mobile`，迁移见 [mysql-user-mobile-unique.sql](mysql-user-mobile-unique.sql)）：并发注册由数据库约束兜底并返回 `20005`。历史数据中已存在的重复手机号仍会让该手机号注册被拒绝，需要人工处理。后端尚未提供邮箱/短信验证与密码找回。
- 资料接口只允许修改 `nickname`、`avatar`、`sex`、`birthday`、`email`、`interest`、`personality`；`mobile`、密码、计数与统计字段不可通过该接口修改。头像目前只是 URL 字符串，后端不提供文件上传。
- 修改密码会撤销该用户的其他所有会话（其他设备需要重新登录，IM 连接随后关闭），发起修改的当前会话保持有效；仍没有全设备退出或管理员撤销接口。
- 已通过 H2 完整登录/刷新/退出事务与 MVC 测试、IM 处理器撤销测试；此前会话核心的真实 MySQL 事务验收已通过。2026-10-27 另通过 7 项真实 MySQL/Redis 测试及 Chromium 核心博客流程验收；本轮未验证多实例 IM、外部存储宕机或正式环境部署，详细范围由后端 `plan/progress.md` 记录。
- 文章服务需要 MySQL 和 Redis；所有业务服务的具体连接配置属于部署环境，不写入前端代码或文档。
- 升级前在目标库审核并执行附加认证表迁移；三个服务必须使用相同的认证表/用户表、JWT 密钥及一致的时钟。协调部署三个服务，禁止混用仍仅验签的旧实例；部署后旧客户端必须重登。迁移不自动执行，也不自动授予管理员。

## 契约维护规则

接口变更的同步规则写在根目录 [AGENTS.md](../AGENTS.md)。每次接口变更后，前端 agent 应以 `frontend-api-status.json` 的 `revision` 作为是否需要同步的唯一快速判断依据，再阅读变更日志和本文档。
