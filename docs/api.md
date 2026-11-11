# HostMonitor 接口文档

> 适用代码：HostMonitor（backend 0.0.1-SNAPSHOT，Spring Boot 3.4.7）
> 端口：HTTP 10800（REST + WebSocket）
> 关联文档：`architecture.md`（系统架构、TCP/UDP 协议、加密与屏幕链路）

---

## 1. 阅读说明

| 项目 | 说明 |
| --- | --- |
| 接口前缀 | 全部为 `/api`（WebSocket 为 `/ws`） |
| 服务地址 | `http://{server}:10800`；前端开发环境通过 Vite 代理 `/api` → `localhost:10800` |
| 传输格式 | `application/json; charset=UTF-8` |
| 时间格式 | ISO-8601 本地时间字符串，如 `2026-11-12T01:30:00`（无时区后缀，统一按 `Asia/Shanghai`） |
| 鉴权方式 | 请求头 `Authorization: Bearer <token>` |
| 分页参数 | `page` 从 1 开始，`size` 为每页条数（各接口有默认值） |
| 逻辑删除 | 所有业务表 `is_del` 字段（0 正常，1 已删除），接口返回的数据均已过滤 |

> TCP（10801）/ UDP（10802）是客户端 Agent 的二进制通信链路，不属于本文档范围，见 `architecture.md` 第 6～9 章。

---

## 2. 认证与鉴权

### 2.1 登录流程

```
POST /api/auth/login  →  返回 token
后续所有请求          →  Header: Authorization: Bearer <token>
```

- Token 为 JWT（HS256），载荷：`sub`=userId、`username`、`roles`（逗号分隔字符串）、`iat`、`exp`。
- 默认有效期 `jwt.expiration = 86400000` 毫秒（24 小时）。
- 签名密钥 `jwt.secret` 必须 ≥ 32 字节（HS256 要求 256 位密钥），否则启动/签发 Token 时抛 `WeakKeyException`。

### 2.2 公开接口（无需登录）

| 方法 | 路径 |
| --- | --- |
| POST | `/api/auth/login` |
| POST | `/api/auth/register` |
| GET | `/api/auth/captcha` |
| GET | `/api/auth/login-status` |
| WS | `/ws/screen/{hostId}?token=<JWT>`（握手阶段自行校验 token） |

其余全部接口（含 `/actuator/**`）都需要合法 Token。

### 2.3 角色与数据范围

| 角色 `roleKey` | 名称 | 数据范围 |
| --- | --- | --- |
| `SUPER_ADMIN` | 超级管理员 | 全部主机、用户、分组、设置 |
| `GROUP_ADMIN` | 组管理员 | 本组内成员及其主机、本组设置、本组进程规则 |
| `USER` | 普通用户 | 仅自己的主机与相关数据 |

- 一个用户只能绑定一台主机（`host.user_id` 唯一）。
- 角色以 JWT 中的 `roles` 为准，权限校验统一在服务端 `AccessChecker` 完成；前端只做展示层隐藏，不作为安全边界。

### 2.4 登录防护（三层）

| 触发条件 | 结果 |
| --- | --- |
| 同 IP 每分钟 ≥ 10 次登录请求 | `code=429`，`请求过于频繁，请稍后再试` |
| 同 IP 15 分钟内 ≥ 3 次失败 | 后续登录必须携带验证码，缺失返回 `code=403`，`需要验证码，请刷新页面后重试` |
| 同账号 15 分钟内 ≥ 5 次失败 | 锁定 15 分钟，`code=423`，`账号已被临时锁定，请15分钟后再试` |

验证码为算术题图片，缓存 5 分钟、全局最多 1000 个，一次性使用。

---

## 3. 通用响应格式

### 3.1 统一响应 `Result<T>`

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `code` | int | 业务状态码，`200` 表示成功 |
| `message` | string | 提示信息 |
| `data` | T | 业务数据，可能为 `null` |

```json
{ "code": 200, "message": "操作成功", "data": null }
```

### 3.2 分页响应 `PageResult<T>`

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `records` | T[] | 当前页数据 |
| `total` | long | 总记录数 |
| `current` | long | 当前页码 |
| `size` | long | 每页条数 |
| `pages` | long | 总页数（`ceil(total/size)`） |

```json
{
  "code": 200,
  "message": "操作成功",
  "data": { "records": [], "total": 0, "current": 1, "size": 10, "pages": 0 }
}
```

### 3.3 状态码约定

| code | 含义 | 典型场景 |
| --- | --- | --- |
| 200 | 成功 | 所有正常返回 |
| 400 | 参数错误 | `@Valid` 校验失败、验证码错误、业务参数非法 |
| 401 | 未认证 | 未带 Token / Token 过期 / 签名错误 |
| 403 | 无权限 | 越权访问、需要验证码、角色不足 |
| 404 | 资源不存在 | 主机/用户/分组/告警不存在 |
| 423 | 账号锁定 | 登录失败次数过多 |
| 429 | 请求过频 | IP 限速 |
| 500 | 服务器错误 | 未捕获异常、业务异常默认码 |

- `@Valid` 校验失败时 `message` 为 `字段: 校验消息; ...` 拼接，例如 `用户名: 用户名不能为空`。
- **除 Security 入口返回真实 HTTP 401 外，业务异常统一由全局异常处理器包装为 HTTP 200 + body.code**，调用方必须以 `body.code` 判断成败。

### 3.4 常用枚举

| 枚举 | 取值 |
| --- | --- |
| 用户状态 `status` | `1` 启用，`0` 禁用 |
| 主机状态 `status` | `1` 在线，`0` 离线 |
| 角色 `roleKey` | `SUPER_ADMIN` / `GROUP_ADMIN` / `USER` |
| 进程规则 `ruleType` | `WHITELIST`（白名单：缺失告警）、`BLACKLIST`（黑名单：运行告警） |
| 告警类型 `alertType` | `BLACKLIST_RUNNING`、`WHITELIST_MISSING` |
| 告警状态 `status` | `0` 未处理，`1` 已处理 |
| 通知发送范围 `sendType` | `1` 全体，`2` 指定分组，`3` 指定用户 |
| 通知已读 `isRead` | `0` 未读，`1` 已读 |

---

## 4. 接口索引

| ID | 模块 | 方法 | 路径 | 说明 |
| --- | --- | --- | --- | --- |
| A-01 | 认证 | POST | `/api/auth/login` | 登录 |
| A-02 | 认证 | POST | `/api/auth/register` | 注册 |
| A-03 | 认证 | GET | `/api/auth/captcha` | 获取验证码 |
| A-04 | 认证 | GET | `/api/auth/login-status` | 查询是否需要验证码 |
| U-01 | 用户 | GET | `/api/users/me` | 当前登录用户 |
| U-02 | 用户 | GET | `/api/users/roles` | 角色列表 |
| U-03 | 用户 | GET | `/api/users/groups` | 分组列表（下拉用） |
| U-04 | 用户 | GET | `/api/users` | 用户分页 |
| U-05 | 用户 | POST | `/api/users` | 创建用户 |
| U-06 | 用户 | PUT | `/api/users/{id}` | 更新用户 |
| U-07 | 用户 | DELETE | `/api/users/{id}` | 删除用户 |
| U-08 | 用户 | PUT | `/api/users/{id}/role` | 分配角色 |
| U-09 | 用户 | POST | `/api/users/{id}/reset-agent-key` | 重置 Agent Key |
| G-01 | 分组 | GET | `/api/groups` | 分组列表 |
| G-02 | 分组 | POST | `/api/groups` | 创建分组 |
| G-03 | 分组 | PUT | `/api/groups/{id}` | 更新分组 |
| G-04 | 分组 | DELETE | `/api/groups/{id}` | 删除分组 |
| G-05 | 分组 | GET | `/api/groups/{id}/members` | 分组成员 |
| G-06 | 分组 | POST | `/api/groups/{id}/members/{userId}` | 添加成员 |
| G-07 | 分组 | DELETE | `/api/groups/{id}/members/{userId}` | 移出成员 |
| G-08 | 分组 | POST | `/api/groups/{id}/admin/{userId}` | 指定组管理员 |
| G-09 | 分组 | DELETE | `/api/groups/{id}/admin/{userId}` | 撤销组管理员 |
| G-10 | 分组 | GET | `/api/groups/{id}/process-rules` | 分组进程规则 |
| H-01 | 主机 | GET | `/api/hosts` | 主机分页 |
| H-02 | 主机 | GET | `/api/hosts/{id}` | 主机详情 |
| H-03 | 主机 | GET | `/api/hosts/{id}/metrics/latest` | 最新性能指标 |
| H-04 | 主机 | GET | `/api/hosts/{id}/metrics/history` | 历史性能指标 |
| H-05 | 主机 | DELETE | `/api/hosts/{id}` | 删除主机 |
| P-01 | 进程 | GET | `/api/hosts/{id}/processes` | 主机进程快照 |
| AL-01 | 告警 | GET | `/api/alerts` | 告警分页 |
| AL-02 | 告警 | PUT | `/api/alerts/{id}/resolve` | 处理告警 |
| AL-03 | 告警 | GET | `/api/alerts/unresolved-count` | 未处理数量 |
| AS-01 | 告警设置 | GET | `/api/alert-settings` | 查询告警设置 |
| AS-02 | 告警设置 | PUT | `/api/alert-settings` | 更新告警设置 |
| GS-01 | 组设置 | GET | `/api/group-settings/{groupId}` | 分组生效设置 |
| GS-02 | 组设置 | PUT | `/api/group-settings/{groupId}` | 更新分组设置 |
| SS-01 | 系统设置 | GET | `/api/system-settings` | 查询系统设置 |
| SS-02 | 系统设置 | PUT | `/api/system-settings` | 更新系统设置 |
| SS-03 | 系统设置 | POST | `/api/system-settings/email/test` | 发送测试邮件 |
| PR-01 | 进程规则 | GET | `/api/process-rules` | 进程规则列表 |
| PR-02 | 进程规则 | POST | `/api/groups/{groupId}/process-rules` | 新增进程规则 |
| PR-03 | 进程规则 | DELETE | `/api/groups/{groupId}/process-rules/{ruleId}` | 删除进程规则 |
| N-01 | 通知 | POST | `/api/notifications` | 发送通知 |
| N-02 | 通知 | GET | `/api/notifications` | 我的通知分页 |
| N-03 | 通知 | PUT | `/api/notifications/{id}/read` | 单条已读 |
| N-04 | 通知 | PUT | `/api/notifications/read-all` | 全部已读 |
| N-05 | 通知 | GET | `/api/notifications/unread-count` | 未读数量 |
| N-06 | 通知 | DELETE | `/api/notifications/{id}` | 删除通知 |
| WS-01 | 屏幕 | WS | `/ws/screen/{hostId}?token=<JWT>` | 实时屏幕流订阅 |

---

## 5. 认证模块 `/api/auth`

### A-01 登录

- **方法/路径**：`POST /api/auth/login`
- **权限**：公开
- **请求体** `LoginRequest`

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `username` | string | 是 | 用户名，非空 |
| `password` | string | 是 | 明文密码，服务端 BCrypt 校验，非空 |
| `captchaKey` | string | 否 | 验证码标识；被要求验证码时必填 |
| `captchaAnswer` | string | 否 | 验证码答案；被要求验证码时必填 |

- **响应** `AuthResponse`

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `token` | string | JWT，后续请求放入 `Authorization: Bearer <token>` |
| `user.id` | long | 用户 ID |
| `user.username` | string | 用户名 |
| `user.email` | string | 邮箱 |
| `user.avatar` | string | 头像 |
| `user.status` | int | 1 启用 / 0 禁用 |
| `user.groupId` | long | 所属分组 ID，可为 null |
| `user.agentKey` | string | 该用户的 Agent Key（客户端认证用） |
| `user.roles` | string[] | 角色列表，如 `["SUPER_ADMIN"]` |

```json
{
  "code": 200,
  "message": "登录成功",
  "data": {
    "token": "eyJhbGciOiJIUzI1NiJ9...",
    "user": {
      "id": 1, "username": "admin", "email": "1@example.com",
      "avatar": null, "status": 1, "groupId": null,
      "agentKey": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
      "roles": ["SUPER_ADMIN"]
    }
  }
}
```

- **业务错误**：`400 验证码错误`、`401 用户名或密码错误`、`401 账号已被禁用`、`403 需要验证码，请刷新页面后重试`、`423 账号已被临时锁定，请15分钟后再试`、`429 请求过于频繁，请稍后再试`

### A-02 注册

- **方法/路径**：`POST /api/auth/register`
- **权限**：公开
- **请求体** `RegisterRequest`

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `username` | string | 是 | 2～50 字符，全局唯一 |
| `email` | string | 是 | 邮箱格式，全局唯一 |
| `password` | string | 是 | 6～100 字符 |

- **响应**：`Result<Void>`，`message="注册成功"`
- **业务错误**：`400 字段校验失败`、`用户名已存在`、`邮箱已被注册`
- **说明**：新用户默认角色 `USER`，状态启用，服务端自动生成 `agentKey`。

### A-03 获取验证码

- **方法/路径**：`GET /api/auth/captcha`
- **权限**：公开
- **响应** `Map<String,String>`

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `captchaKey` | string | 验证码标识，登录时回传 |
| `captchaImage` | string | 算术验证码图片的 Base64 字符串（前端拼接 `data:image/png;base64,` 展示） |

### A-04 查询是否需要验证码

- **方法/路径**：`GET /api/auth/login-status`
- **权限**：公开
- **响应** `Map<String,Object>`

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `captchaRequired` | boolean | 当前 IP 是否已被要求输入验证码 |

---

## 6. 用户模块 `/api/users`

> `requireAdmin`：`SUPER_ADMIN` 或 `GROUP_ADMIN` 可调用；普通用户 403。

### U-01 当前登录用户

- `GET /api/users/me`
- **权限**：已登录
- **响应** `UserVO`（见下表）

`UserVO` 字段：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `id` | long | 用户 ID |
| `username` | string | 用户名 |
| `email` | string | 邮箱 |
| `avatar` | string | 头像 |
| `status` | int | 1 启用 / 0 禁用 |
| `groupId` | long | 分组 ID |
| `groupName` | string | 分组名称 |
| `roles` | string[] | 角色列表 |
| `agentKey` | string | 客户端认证 Key |
| `createTime` | datetime | 创建时间 |

### U-02 角色列表

- `GET /api/users/roles`；**权限**：`requireAdmin`
- **响应**：`Role[]`，字段 `id`、`roleKey`、`roleName`、`description`、`createTime`、`updateTime`

### U-03 分组列表（下拉用）

- `GET /api/users/groups`；**权限**：`requireAdmin`
- **响应**：`Group[]`，字段 `id`、`groupName`、`description`、`createTime`、`updateTime`

### U-04 用户分页

- `GET /api/users?page=1&size=10&keyword=&roleKey=&groupId=`
- **权限**：超管 / 组管（普通用户 403 `无权访问用户管理`）
- **Query 参数**

| 参数 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `page` | int | 否 | 默认 1 |
| `size` | int | 否 | 默认 10 |
| `keyword` | string | 否 | 用户名/邮箱模糊匹配 |
| `roleKey` | string | 否 | 按角色过滤 |
| `groupId` | long | 否 | 按分组过滤；组管会被强制限制为本组 |

- **响应**：`PageResult<UserVO>`
- **业务错误**：组管未分配分组时 403 `您未分配到任何组，无法管理用户`

### U-05 创建用户

- `POST /api/users`；**权限**：`requireAdmin`
- **请求体** `CreateUserRequest`

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `username` | string | 是 | 唯一，重复返回 `用户名已存在` |
| `email` | string | 是 | 唯一，重复返回 `邮箱已被注册` |
| `password` | string | 是 | 初始密码，BCrypt 存储 |
| `status` | int | 否 | 默认 1 |
| `groupId` | long | 否 | 组管会被强制改为自己所在组 |
| `roleKey` | string | 否 | 默认 `USER`；不允许创建 `SUPER_ADMIN` |

- **响应**：`message="创建成功"`；服务端生成该用户的 `agentKey`

### U-06 更新用户

- `PUT /api/users/{id}`；**权限**：超管 / 组管（组管不能操作超管）
- **请求体** `UpdateUserRequest`（全部可选，只更新非 null 字段）

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `email` | string | 新邮箱（唯一性校验） |
| `password` | string | 新密码 |
| `status` | int | 1 启用 / 0 禁用 |
| `groupId` | long | 新分组 |
| `clearGroupId` | boolean | 为 true 时清空分组（仅超管） |

- **响应**：`message="更新成功"`；组管将用户换组时，该用户的 `GROUP_ADMIN` 角色会降级为 `USER`

### U-07 删除用户

- `DELETE /api/users/{id}`；**权限**：超管 / 组管
- **响应**：`message="删除成功"`（同时删除 `user_role` 关联）
- **业务错误**：`不能删除自己`、`不能删除超级管理员`

### U-08 分配角色

- `PUT /api/users/{id}/role`；**权限**：`requireAdmin`
- **请求体** `AssignRoleRequest`：`roleKey`（string，必填）
- **响应**：`message="角色分配成功"`
- **业务错误**：禁止分配 `SUPER_ADMIN`；指定 `GROUP_ADMIN` 需用户已有分组且该组没有其他组管理员

### U-09 重置 Agent Key

- `POST /api/users/{id}/reset-agent-key`；**权限**：仅超管
- **行为**：先向该用户在线客户端下发 `DISCONNECT` 命令、等待 500ms、断开旧连接，再生成新 key
- **响应** `Result<String>`：`message="密钥已重置"`，`data` 为新 `agentKey`
- **注意**：旧 key 立即失效，客户端需更新配置后重连

---

## 7. 分组模块 `/api/groups`

> 本模块所有接口 **仅超管**（`requireSuperAdmin`），组管/普通用户 403。

| ID | 说明 | 请求/响应要点 |
| --- | --- | --- |
| G-01 | `GET /api/groups` 分组列表 | 响应 `GroupVO[]`，按 `createTime` 倒序 |
| G-02 | `POST /api/groups` 创建分组 | 请求 `CreateGroupRequest`：`groupName`（必填，非空，唯一）、`description`、`adminId`（可选，创建时指定组管理员）；响应 `创建成功` |
| G-03 | `PUT /api/groups/{id}` 更新分组 | 请求 `UpdateGroupRequest`：`groupName`（必填，非空，唯一）、`description`；响应 `更新成功` |
| G-04 | `DELETE /api/groups/{id}` 删除分组 | 先撤销组管理员、成员 `groupId` 置空、删除分组；响应 `删除成功` |
| G-05 | `GET /api/groups/{id}/members` 成员列表 | 响应 `UserVO[]` |
| G-06 | `POST /api/groups/{id}/members/{userId}` 添加成员 | 响应 `添加成功`；错误：`超级管理员不能加入分组`、`用户已在该分组中` |
| G-07 | `DELETE /api/groups/{id}/members/{userId}` 移出成员 | 响应 `移出成功`；若该用户是组管则同时撤销；超管不可移出 |
| G-08 | `POST /api/groups/{id}/admin/{userId}` 指定组管 | 响应 `指定组管理员成功`；先撤销原组管，目标用户 `USER` → `GROUP_ADMIN` |
| G-09 | `DELETE /api/groups/{id}/admin/{userId}` 撤销组管 | 响应 `撤销组管理员成功`；`GROUP_ADMIN` → `USER` |
| G-10 | `GET /api/groups/{id}/process-rules` 分组规则 | 响应 `ProcessRuleVO[]` |

`GroupVO` 字段：`id`、`groupName`、`description`、`memberCount`、`adminId`、`adminName`、`createTime`。

---

## 8. 主机模块 `/api/hosts`

### H-01 主机分页

- `GET /api/hosts?page=1&size=10&keyword=&status=`
- **权限**：已登录；数据范围自动过滤

| 参数 | 类型 | 说明 |
| --- | --- | --- |
| `page` / `size` | int | 默认 1 / 10 |
| `keyword` | string | 主机名/IP 模糊匹配 |
| `status` | int | 1 在线 / 0 离线 |

- **响应**：`PageResult<HostVO>`；组管所在组无成员时返回空页

`HostVO` 字段：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `id` | long | 主机 ID |
| `hostname` | string | 主机名 |
| `ipAddress` | string | IP |
| `cpuModel` | string | CPU 型号 |
| `cpuCores` | int | 核心数 |
| `memoryTotal` | long | 内存总量（字节） |
| `diskTotal` | long | 磁盘总量（字节） |
| `osName` / `osVersion` | string | 操作系统 |
| `status` | int | 1 在线 / 0 离线 |
| `lastHeartbeat` | datetime | 最后心跳时间 |
| `userId` / `username` | long / string | 绑定用户 |
| `groupId` / `groupName` | long / string | 所属分组 |
| `createTime` | datetime | 首次上报时间 |

### H-02 主机详情

- `GET /api/hosts/{id}` → `HostVO`
- **业务错误**：403 `无权查看该主机`

### H-03 最新性能指标

- `GET /api/hosts/{id}/metrics/latest` → `HostVO.MetricVO`

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `cpuUsage` | decimal | CPU 使用率（%） |
| `memoryUsage` | decimal | 内存使用率（%） |
| `diskUsage` | decimal | 磁盘使用率（%） |
| `memoryUsed` | long | 已用内存（字节） |
| `diskUsed` | long | 已用磁盘（字节） |
| `collectTime` | datetime | 采集时间 |

- 无数据时返回各字段为 null 的空对象，不报错。

### H-04 历史性能指标

- `GET /api/hosts/{id}/metrics/history?range=1h` → `HostVO.MetricHistoryVO[]`
- `range` 格式：`{n}m`（分钟）或 `{n}h`（小时，最多 48），默认 `1h`
- 字段：`collectTime`、`cpuUsage`、`memoryUsage`、`diskUsage`

### H-05 删除主机

- `DELETE /api/hosts/{id}` → `message="删除成功"`，同时删除该主机的 `host_metric` 数据

### P-01 主机进程快照

- `GET /api/hosts/{id}/processes` → `ProcessVO[]`
- **响应字段**

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `id` | long | 记录 ID |
| `hostId` | long | 主机 ID |
| `pid` / `ppid` | int | 进程 PID / 父 PID |
| `name` | string | 进程名 |
| `user` | string | 运行用户 |
| `cpuUsage` | decimal | CPU 占用（%） |
| `memoryUsage` | decimal | 内存占用（%） |
| `memoryUsed` | long | 内存占用（字节） |
| `status` | string | 进程状态 |
| `startTime` | datetime | 启动时间 |
| `commandLine` | string | 命令行 |
| `collectTime` | datetime | 采集时间 |
| `isWhitelisted` | boolean | 是否命中该主机所属分组的白名单 |
| `isBlacklisted` | boolean | 是否命中黑名单 |

- 越权返回 403 `无权查看该主机`

---

## 9. 告警模块 `/api/alerts`

| ID | 接口 | 说明 |
| --- | --- | --- |
| AL-01 | `GET /api/alerts?page=1&size=20&alertType=&status=` | 分页查询，按当前用户角色过滤（超管全部、组管本组、用户自己）；响应 `PageResult<AlertVO>` |
| AL-02 | `PUT /api/alerts/{id}/resolve` | 标记已处理，响应 `告警已处理` |
| AL-03 | `GET /api/alerts/unresolved-count` | 未处理数量，响应 `Result<Long>` |

`AlertVO` 字段：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `id` | long | 告警 ID |
| `hostId` / `hostname` | long / string | 主机 |
| `alertType` | string | `BLACKLIST_RUNNING` / `WHITELIST_MISSING` |
| `alertTypeLabel` | string | 类型中文名 |
| `processName` | string | 触发进程名 |
| `ruleId` | long | 命中的进程规则 ID |
| `notificationId` | long | 关联通知 ID |
| `status` / `statusLabel` | int / string | 0 未处理 / 1 已处理 |
| `createTime` | datetime | 告警时间 |

---

## 10. 告警设置 `/api/alert-settings`

### AS-01 查询

- `GET /api/alert-settings` → `Result<Map<String,String>>`
- **权限**：超管返回全部；组管仅返回 6 个键；普通用户 500 `无权访问告警设置`

组管可见键：`process_alert_enabled`、`performance_alert_enabled`、`cpu_threshold`、`memory_threshold`、`disk_threshold`、`alert_dedup_minutes`

### AS-02 更新

- `PUT /api/alert-settings`，请求体 `Map<String,String>`（键值对）
- 组管只接受上述 6 个键，全部被过滤时返回 `无权修改这些设置`
- **响应**：`message="告警设置已更新"`

---

## 11. 组设置 `/api/group-settings`

### GS-01 查询分组生效设置

- `GET /api/group-settings/{groupId}` → `Result<Map<String,String>>`
- 返回**组级覆盖全局后的生效值**（仅包含组内显式配置的键）
- **权限**：超管任意组；组管仅本组，否则 `只能查看本组设置`；普通用户 `无权访问组设置`

### GS-02 更新分组设置

- `PUT /api/group-settings/{groupId}`，请求体 `Map<String,String>`
- **响应**：`message="组设置已更新"`；错误：`只能修改本组设置` / `无权修改组设置`

---

## 12. 系统设置 `/api/system-settings`

### SS-01 查询

- `GET /api/system-settings` → `Result<Map<String,String>>`
- 超管返回全部；组管仅返回 13 个客户端/告警相关键

组管可见键：`client_metric_interval_ms`、`client_process_interval_ms`、`client_screen_fps`、`client_screen_jpeg_quality`、`client_process_cpu_threshold`、`client_heartbeat_timeout_ms`、`client_offline_threshold_ms`、`performance_alert_enabled`、`cpu_threshold`、`memory_threshold`、`disk_threshold`、`alert_dedup_minutes`、`process_alert_enabled`

### SS-02 更新

- `PUT /api/system-settings`，请求体 `Map<String,String>`
- **响应**：超管 `message="系统设置已更新"`；组管 `message="设置已更新"`（只写允许的键）

### SS-03 发送测试邮件

- `POST /api/system-settings/email/test`；**权限**：仅超管
- **请求体**：`{ "to": "someone@example.com" }`；为空返回 `请输入收件人邮箱`
- **响应**：`message="测试邮件已发送"`
- **前置条件**：数据库配置项 `email_enabled=true` 且 SMTP 参数已配置（见 `deployment.md` 配置表）

---

## 13. 进程规则 `/api`

| ID | 接口 | 说明 |
| --- | --- | --- |
| PR-01 | `GET /api/process-rules` | 超管返回全部；组管返回本组；无组返回空数组；其他角色 403 `无权查看进程规则`；响应 `ProcessRuleVO[]` |
| PR-02 | `POST /api/groups/{groupId}/process-rules` | 新增规则，见下 |
| PR-03 | `DELETE /api/groups/{groupId}/process-rules/{ruleId}` | 删除规则，响应 `删除成功` |

PR-02 请求体：

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `processName` | string | 是 | 进程名（同组内唯一，重复返回 `该进程规则已存在`） |
| `ruleType` | string | 是 | `WHITELIST` / `BLACKLIST` |
| `description` | string | 否 | 说明 |

- **权限**：超管任意组；组管仅本组（403 `只能管理本分组的规则`）；分组不存在时报错
- **响应**：`message="添加成功"`

`ProcessRuleVO` 字段：`id`、`groupId`、`groupName`、`processName`、`ruleType`、`description`、`createTime`。

---

## 14. 通知模块 `/api/notifications`

### N-01 发送通知

- `POST /api/notifications`；**权限**：已登录（发送范围受角色限制）
- **请求体** `CreateNotificationRequest`

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `title` | string | 是 | 标题，非空 |
| `content` | string | 是 | 内容，非空 |
| `sendType` | int | 是 | 1 全体 / 2 指定分组 / 3 指定用户 |
| `targetGroupId` | long | 否 | `sendType=2` 时使用；组管固定为本组 |
| `targetUserIds` | long[] | 否 | `sendType=3` 时使用 |

- **响应**：`message="发送成功"`；服务端会为每个目标用户写入 `user_notification` 关系

### N-02 我的通知分页

- `GET /api/notifications?page=1&size=10` → `PageResult<NotificationVO>`

`NotificationVO` 字段：`id`、`title`、`content`、`senderName`、`sendType`、`targetInfo`、`isRead`（0/1）、`sendTime`

| ID | 接口 | 说明 |
| --- | --- | --- |
| N-03 | `PUT /api/notifications/{id}/read` | 单条已读，响应 `已读` |
| N-04 | `PUT /api/notifications/read-all` | 全部已读，响应 `全部已读` |
| N-05 | `GET /api/notifications/unread-count` | 未读数，`Result<Long>` |
| N-06 | `DELETE /api/notifications/{id}` | 删除我的通知，响应 `删除成功` |

---

## 15. 屏幕流 WebSocket

### WS-01 订阅实时屏幕

- **地址**：`ws://{server}:10800/ws/screen/{hostId}?token=<JWT>`
  （生产环境经 Nginx 反代时用 `wss://`）
- **路径参数**：`hostId` 为主机 ID
- **Query 参数**：`token` 为登录返回的 JWT（握手时由拦截器写入会话属性，处理器自行校验）
- **权限规则**：

| 角色 | 可见范围 |
| --- | --- |
| `SUPER_ADMIN` | 任意主机 |
| `GROUP_ADMIN` | 与主机所属用户同组的主机 |
| 普通用户 | 仅自己绑定的主机 |

- **协议行为**

| 方向 | 内容 |
| --- | --- |
| 客户端 → 服务端 | 无业务消息（连接即订阅，关闭即取消） |
| 服务端 → 客户端 | 二进制帧（`BinaryMessage`），内容为 JPEG 图片字节，直接解码绘制 |

- **生命周期**

1. 前端连接成功后，服务端 `ScreenStreamManager` 记录订阅者；
2. 首个订阅者出现时，服务端通过 TCP 长连接向 Agent 下发 `SCREEN_START`；
3. Agent 按配置的帧率/画质截屏并推 UDP 帧，服务端解密后经 WebSocket 广播最新帧给所有订阅者；
4. 最后一个订阅者断开时，服务端下发 `SCREEN_STOP`，Agent 停止截屏；
5. 新订阅者会立即收到最近一帧缓存，首帧无需等待。

- **前端建议**：断线 3 秒自动重连；使用 `requestAnimationFrame` + 只保留最新帧，避免解码积压。
- **注意**：JWT 放在 Query 上，可能被 Nginx access log 记录，生产环境应做日志脱敏或改用短期票据。

---

## 16. 附录

### 16.1 初始化数据

| 项 | 值 |
| --- | --- |
| 默认管理员 | 用户名 `admin`，密码 `123456`（BCrypt 存储，首次登录后务必修改） |
| 默认管理员 Agent Key | `a1b2c3d4-e5f6-7890-abcd-ef1234567890`（与 `monitor-client` 默认配置一致） |
| 角色 | `SUPER_ADMIN` / `GROUP_ADMIN` / `USER` |
| 权限 | `system:user`、`system:role`、`system:perm`、`system:group`、`monitor:host`、`monitor:metric`、`monitor:dashboard` 等 |

### 16.2 数据库配置键（`alert_setting` 表，接口可改）

| 键 | 默认值 | 说明 |
| --- | --- | --- |
| `process_alert_enabled` | true | 进程告警开关 |
| `performance_alert_enabled` | true | 性能告警开关 |
| `cpu_threshold` | 90 | CPU 使用率阈值（%） |
| `memory_threshold` | 90 | 内存使用率阈值（%） |
| `disk_threshold` | 90 | 磁盘使用率阈值（%） |
| `alert_dedup_minutes` | 30 | 告警去重窗口（分钟） |
| `session_timeout_hours` | 24 | 会话超时（小时） |
| `data_retention_days` | 2 | 指标/进程数据保留天数 |
| `notification_retention_days` | 30 | 通知保留天数 |
| `email_enabled` | false | 邮件通知开关 |
| `smtp_host` | - | SMTP 服务器 |
| `smtp_port` | 465 | SMTP 端口（SSL） |
| `smtp_username` | - | 发件人地址 |
| `smtp_password` | - | SMTP 授权码 |
| `client_metric_interval_ms` | 5000 | 客户端指标采集间隔 |
| `client_process_interval_ms` | 15000 | 客户端进程采集间隔 |
| `client_screen_fps` | 5 | 屏幕推流帧率 |
| `client_screen_jpeg_quality` | 0.5 | 屏幕 JPEG 质量（0～1） |
| `client_process_cpu_threshold` | 1.0 | 进程 CPU 变化上报阈值（%） |
| `client_heartbeat_timeout_ms` | 60000 | 心跳超时（毫秒） |
| `client_offline_threshold_ms` | 60000 | 离线判定阈值（毫秒） |

### 16.3 curl 调用示例

```bash
# 1) 登录拿 token
curl -X POST http://127.0.0.1:10800/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"123456"}'

# 2) 带 token 查询主机列表
curl "http://127.0.0.1:10800/api/hosts?page=1&size=10" \
  -H "Authorization: Bearer <token>"

# 3) 更新告警阈值
curl -X PUT http://127.0.0.1:10800/api/alert-settings \
  -H "Authorization: Bearer <token>" \
  -H "Content-Type: application/json" \
  -d '{"cpu_threshold":"85","memory_threshold":"85"}'
```

### 16.4 接口与前端页面映射

| 前端页面 | 主要接口 |
| --- | --- |
| 登录 / 注册 | A-01 ~ A-04 |
| 仪表盘 `DashboardView` | H-01、AL-03、N-05 |
| 主机管理 `HostsView` / 主机详情 `HostDetailView` | H-01 ~ H-04、P-01 |
| 屏幕监控 `ScreenView` | WS-01 |
| 告警中心 `AlertsView` | AL-01 ~ AL-03 |
| 用户管理 `UsersView` | U-01 ~ U-09 |
| 组织管理 `GroupsView` | G-01 ~ G-10、PR-01 ~ PR-03 |
| 通知管理 `NotificationsView` | N-01 ~ N-06 |
| 系统设置 `SettingsView` | AS-01/02、GS-01/02、SS-01 ~ SS-03 |
