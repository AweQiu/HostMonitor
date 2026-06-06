# HostMonitor 技术文档

> 最后更新：2026 年 6 月 — Socket 通信加密（ECDH + AES-GCM）、登录防爆破（三层防护 + 验证码）

本文档面向开发者和运维人员，详细描述 HostMonitor 系统的技术架构、通信协议、数据库设计、API 接口、部署方式及核心流程实现。

***

## 技术栈

### 后端

Spring Boot 3.4.7 / Spring Security 6.4.x / MyBatis-Plus 3.5.9 / MySQL 8.0+ / JWT (jjwt 0.12.6) / WebSocket / JavaMail (Spring Mail) / X25519 ECDH / AES-256-GCM

### 前端

Vue 3.5.x / TypeScript / Ant Design Vue 4.2.x / Axios / ECharts 6.1.x / Pinia / Vite 8.0.x

### 客户端

Java 21 / OSHI 6.6.5 / Java AWT Robot / UDP/TCP Socket / X25519 ECDH / AES-256-GCM

***

## 项目结构

### 后端（backend）

```
src/main/java/com/ylh/backend/
├── BackendApplication.java          # Spring Boot 启动类
├── Common/                          # 通用工具层
│   ├── Result.java                  # 统一响应封装
│   ├── PageResult.java              # 统一分页响应封装
│   ├── BusinessException.java       # 业务异常基类（含错误码）
│   ├── ForbiddenException.java      # 403 权限不足异常
│   ├── NotFoundException.java       # 404 资源不存在异常
│   └── UnauthorizedException.java   # 401 未认证异常
├── Config/                          # 配置类
│   ├── AccessChecker.java           # 权限校验统一入口
│   ├── DataCleanupTask.java         # 定时数据清理
│   ├── GlobalExceptionHandler.java  # 全局异常处理
│   ├── JwtAuthenticationFilter.java # JWT 认证过滤器
│   ├── JwtUtil.java                 # JWT 工具类
│   ├── MyBatisMetaObjectHandler.java# MyBatis-Plus 自动填充
│   ├── MyBatisPlusConfig.java       # MyBatis-Plus 配置
│   ├── SecurityConfig.java          # Spring Security 配置
│   └── WebSocketConfig.java         # WebSocket 配置
├── Controller/                      # REST API 控制层
│   ├── AlertController.java         # 告警记录接口
│   ├── AlertSettingController.java  # 全局告警设置接口
│   ├── AuthController.java          # 认证接口（含验证码、登录状态）
│   ├── GroupController.java         # 分组管理接口
│   ├── GroupSettingController.java  # 组级别设置接口
│   ├── HostInfoController.java      # 主机信息接口
│   ├── HostProcessController.java   # 主机进程接口
│   ├── NotificationController.java  # 通知接口
│   ├── ProcessRuleController.java   # 进程规则接口
│   ├── SystemSettingController.java # 系统设置接口
│   └── UserController.java          # 用户管理接口
├── Dto/                             # 数据传输对象
│   ├── AlertVO.java                 # 告警视图对象
│   ├── AssignRoleRequest.java       # 角色分配请求
│   ├── AuthResponse.java            # 认证响应
│   ├── CreateGroupRequest.java      # 创建分组请求
│   ├── CreateNotificationRequest.java # 创建通知请求
│   ├── CreateUserRequest.java       # 创建用户请求
│   ├── GroupVO.java                 # 分组视图对象
│   ├── HostVO.java                  # 主机视图对象
│   ├── LoginRequest.java            # 登录请求（含验证码字段）
│   ├── NotificationVO.java          # 通知视图对象
│   ├── ProcessRuleVO.java           # 进程规则视图对象
│   ├── ProcessVO.java               # 进程视图对象
│   ├── RegisterRequest.java         # 注册请求
│   ├── UpdateGroupRequest.java      # 更新分组请求
│   ├── UpdateUserRequest.java       # 更新用户请求
│   └── UserVO.java                  # 用户视图对象
├── Entity/                          # 数据库实体类
│   ├── AlertRecord.java             # 告警记录
│   ├── AlertSetting.java            # 全局设置项
│   ├── Group.java                   # 分组
│   ├── GroupSetting.java            # 组级别设置覆盖
│   ├── HostInfo.java                # 主机信息
│   ├── HostMetric.java              # 性能指标
│   ├── HostProcess.java             # 主机进程
│   ├── LoginAttempt.java            # 登录尝试记录
│   ├── Notification.java            # 通知
│   ├── Permission.java              # 权限
│   ├── ProcessRule.java             # 进程规则
│   ├── Role.java                    # 角色
│   ├── RolePermission.java          # 角色-权限关联
│   ├── User.java                    # 用户
│   ├── UserNotification.java        # 用户-通知关联
│   └── UserRole.java                # 用户-角色关联
├── Mapper/                          # MyBatis Mapper 接口
│   ├── AlertRecordMapper.java
│   ├── AlertSettingMapper.java
│   ├── GroupMapper.java
│   ├── GroupSettingMapper.java
│   ├── HostInfoMapper.java
│   ├── HostMetricMapper.java
│   ├── HostProcessMapper.java
│   ├── LoginAttemptMapper.java
│   ├── NotificationMapper.java
│   ├── ProcessRuleMapper.java
│   ├── RoleMapper.java
│   ├── UserMapper.java
│   ├── UserNotificationMapper.java
│   └── UserRoleMapper.java
├── Service/                         # 业务逻辑层
│   ├── AlertService.java            # 告警服务
│   ├── AlertSettingService.java     # 全局设置服务（含缓存+推送）
│   ├── AuthService.java             # 认证服务（含防爆破检查）
│   ├── CaptchaService.java          # 验证码服务（数学运算+AWT绘图）
│   ├── EmailService.java            # 邮件发送服务（异步）
│   ├── GroupService.java            # 分组服务
│   ├── GroupSettingService.java     # 组设置服务（含覆盖机制）
│   ├── HostInfoService.java         # 主机信息服务
│   ├── HostProcessService.java      # 主机进程服务
│   ├── LoginAttemptService.java     # 登录尝试服务（三层防护）
│   ├── NotificationService.java     # 通知服务
│   ├── ProcessRuleService.java      # 进程规则服务
│   └── UserService.java             # 用户服务
└── Socket/                          # Socket 通信模块
    ├── ClientHandler.java           # 客户端连接处理器（含ECDH+加解密）
    ├── CryptoService.java           # 加密服务（X25519 ECDH + AES-256-GCM）
    ├── MessageConstants.java        # 消息类型常量
    ├── MessageParser.java           # 消息解析器
    ├── ScreenStreamManager.java     # 屏幕流管理器
    ├── ScreenWebSocketHandler.java  # 屏幕 WebSocket 转发
    ├── SocketMessage.java           # Socket 消息模型
    ├── SocketServer.java            # TCP Socket 服务端
    └── UdpScreenServer.java         # UDP 屏幕接收服务端（含帧解密）
```

### 前端（fronted）

```
src/
├── App.vue                          # 根组件
├── main.ts                          # 应用入口
├── assets/                          # 静态资源
├── components/                      # 公共组件
├── layouts/
│   └── MainLayout.vue               # 主布局（侧边栏+顶栏+内容区）
├── router/
│   └── index.ts                     # 路由定义与守卫
├── stores/                          # Pinia 状态管理
│   ├── tabs.ts                      # 标签页状态
│   └── user.ts                      # 用户状态（登录态、权限、验证码）
├── utils/
│   ├── request.ts                   # Axios 封装（拦截器、Token 注入）
│   └── settings.ts                  # 应用设置工具（浏览器本地存储）
└── views/                           # 页面视图
    ├── DashboardView.vue            # 仪表盘
    ├── HostsView.vue                # 主机列表
    ├── HostDetailView.vue           # 主机详情
    ├── ScreenView.vue               # 屏幕监控
    ├── AlertsView.vue               # 告警中心
    ├── ProcessManagementView.vue    # 进程管理
    ├── UsersView.vue                # 用户管理
    ├── GroupsView.vue               # 组织管理
    ├── NotificationsView.vue        # 通知管理
    ├── SettingsView.vue             # 系统设置
    ├── LoginView.vue                # 登录（含验证码）
    └── RegisterView.vue             # 注册
```

### 客户端（monitor-client）

```
src/main/java/com/ylh/client/
├── MonitorClient.java               # 客户端启动类
├── common/
│   ├── CryptoService.java           # 加密服务（X25519 ECDH + AES-256-GCM）
│   ├── MessageConstants.java        # 消息类型常量
│   ├── RuntimeConfig.java           # 运行时配置（支持热更新）
│   └── SocketMessage.java           # Socket 消息模型
├── core/
│   ├── MetricReporter.java          # 系统指标采集上报
│   ├── ProcessReporter.java         # 进程信息采集上报
│   └── ScreenStreamer.java          # 屏幕画面采集推送
├── models/
│   ├── HostInfo.java                # 主机基础信息
│   ├── HostMetric.java              # 主机性能指标
│   ├── ProcessInfo.java             # 进程信息
│   └── ProcessListDelta.java        # 进程增量数据
├── network/
│   ├── NetworkClient.java           # TCP 网络客户端（含ECDH+加解密）
│   └── UdpSender.java               # UDP 数据发送器（含帧加密）
└── utils/
    ├── CollectionUtil.java          # 集合工具
    ├── ConfigUtil.java              # 配置读取
    ├── ProcessCollector.java        # 系统进程采集器
    ├── ProcessDeltaCalculator.java  # 进程增量计算器
    └── ScreenCapture.java           # 屏幕截图工具
```

***

## 系统架构

```
┌─────────────────────┐     HTTP/REST API      ┌─────────────────────┐
│      前端 (Vue 3)   │ ──────────────────────> │      后端 (Spring)  │
│   localhost:5173    │ <────────────────────── │   localhost:10800   │
│                     │     WebSocket           │                     │
└─────────────────────┘                        └───────────┬─────────┘
                                                           │
                                                           │ TCP (10801) 认证/指令/指标/进程/配置
                                                           │     ECDH密钥协商 → AES-256-GCM加密
                                                           │ UDP (10802) 屏幕帧
                                                           │     AES-256-GCM加密
                                                           ▼
                                               ┌─────────────────────┐
                                               │   客户端 (Java 21)  │
                                               │  屏幕截图 / 指标采集 │
                                               │  进程采集 / 增量上报  │
                                               │  配置拉取 / 热更新    │
                                               │  ECDH协商 / 加密传输  │
                                               └─────────────────────┘
```

通信协议说明：

- **HTTP REST API**：前端与后端之间的常规数据交互（用户管理、主机列表、告警、通知、系统设置等）
- **WebSocket**：前端订阅屏幕监控实时画面，后端推送 JPEG 帧数据
- **TCP Socket**：客户端与后端之间的长连接，用于 Agent Key 认证、主机信息上报、性能指标上报、进程列表上报、指令下发、配置推送。认证后所有消息通过 AES-256-GCM 加密传输
- **UDP Socket**：客户端向后端发送屏幕截图帧数据，低延迟传输。帧数据通过 AES-256-GCM 加密

***

## 核心设计

### 异常处理体系

系统定义了分层异常体系，替代原来的 `RuntimeException`：

```
BusinessException (code, message)          # 基类，默认 400
├── UnauthorizedException (401)            # 未认证
├── ForbiddenException (403)              # 无权限
└── NotFoundException (404)               # 资源不存在
```

`GlobalExceptionHandler` 按异常类型返回对应的 HTTP 状态码：

- `BusinessException` → 取其 `code` 字段（200/400/401/403/404/423/429 等）
- `MethodArgumentNotValidException` → 400 + 字段校验信息
- `RuntimeException`（未知）→ 500
- `Exception`（兜底）→ 500

### 权限校验体系

`AccessChecker` 是权限校验的统一入口，Controller 层不再各自实现权限逻辑：

```java
// AccessChecker 核心方法
Long getCurrentUserId()               // 从 SecurityContext 获取当前用户 ID
List<String> getCurrentUserRoles()    // 优先从 Authentication.details 取，回退到 DB 查
boolean isSuperAdmin()                // 是否超管
boolean isGroupAdmin()                // 是否组管
void requireSuperAdmin()              // 不是超管抛 ForbiddenException
void requireAdmin()                   // 不是超管也不是组管抛 ForbiddenException
void requireUserManagePermission(Long targetUserId)  // 校验用户管理权限
User getCurrentUser()                 // 获取当前用户实体
```

### JWT 认证机制

与初版相比的改进：

1. **角色写入 Token**：JWT `claims` 中携带 `roles` 字段（逗号分隔），`JwtAuthenticationFilter` 验证后放入 `Authentication.details`，后续 Controller 无需再查 DB
2. **可配置过期时间**：通过 `session_timeout_hours` 系统设置项控制 Token 有效期，登录时动态使用
3. **Configurable secret**：通过 `jwt.secret` 配置项设置签名密钥

### 登录防爆破机制

系统实现了三层递进的登录防护：

| 层级 | 机制 | 触发条件 | 错误码 | 持续时间 |
|------|------|---------|--------|---------|
| 第一层 | IP 速率限制 | 同一 IP 每分钟 ≥10 次登录请求 | 429 | 1 分钟滑动窗口 |
| 第二层 | 验证码 | 同一 IP 15分钟内 ≥3 次失败 | 403/400 | 15 分钟滑动窗口 |
| 第三层 | 账号临时锁定 | 同一账号 15分钟内 ≥5 次失败 | 423 | 锁定 15 分钟 |

**验证码方案**：

- 使用数学运算验证码（加/减/乘），无需第三方依赖
- 验证码答案存储在内存 `ConcurrentHashMap` 中（TTL 5 分钟，最大 1000 条），无需 Redis
- 验证码图片使用 Java AWT 绘制（120×40），带干扰线和噪点
- 每次验证码校验后立即从缓存中移除（一次性使用）

**前端交互流程**：

1. 页面加载时调用 `GET /api/auth/login-status` 检查是否需要验证码
2. 登录失败后自动标记需要验证码，并刷新验证码图片
3. 验证码图片支持点击刷新

### 分页响应统一

`PageResult<T>` 统一了所有分页接口的响应格式：

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "records": [...],
    "total": 100,
    "current": 1,
    "size": 10,
    "pages": 10
  }
}
```

提供两个静态工厂方法：
- `PageResult.of(MyBatisPlusPage)` — 直接转换
- `PageResult.of(MyBatisPlusPage, records)` — 映射 VO 后转换

### 全局设置缓存与配置推送

`AlertSettingService` 维护 30 秒 TTL 的内存缓存 (`ConcurrentHashMap`)，避免高频读 DB。更新设置时写穿缓存 + 更新 DB。

当更新的设置项属于 `CLIENT_CONFIG_KEYS`（客户端相关配置）时，自动调用 `SocketServer.pushConfigToAllClients()` 将变更推送给所有在线客户端——客户端无需重启即可生效。

### 组级别设置覆盖

`group_setting` 表允许分组独立覆盖部分全局设置。`GroupSettingService.getEffective(groupId, key)` 先查组设置（有则返回），再回退到全局设置。

可被覆盖的设置项由 `OVERRIDABLE_KEYS` 常量控制：性能告警阈值、告警开关、去重间隔、客户端采集参数等。

### 客户端配置生命周期

```
客户端启动 → TCP 连接 → AUTH 认证成功
    ↓
服务端 sendClientConfig()（查 groupSettingService.getEffective）
    ↓
客户端应用配置到各 Reporter
    ↓
管理员修改设置 → AlertSettingService.updateSettings()
    ↓ 检测到属于 CLIENT_CONFIG_KEYS
    ↓
SocketServer.pushConfigToAllClients() → CONFIG|{json}\n
    ↓
客户端收到 → 更新采集间隔/帧率等参数
```

### 数据清理

`DataCleanupTask` 通过 `@Scheduled(cron = "0 0 3 * * ?")` 每天凌晨 3 点执行：

| 清理项 | 可配置参数 | 默认值 |
|--------|-----------|--------|
| `host_metric` | `data_retention_days` | 2 天 |
| `host_process` | `data_retention_days` | 2 天 |
| `notification` + `user_notification` | `notification_retention_days` | 30 天 |
| `login_attempt` | 固定 | 24 小时 |

### 邮件通知

`EmailService` 提供两个核心方法：

- `sendTestEmail(to)` — 同步发送，用于 SMTP 配置验证
- `sendAlertEmailAsync(toList, subject, content)` — `@Async` 异步发送，告警触发时调用，不阻塞主流程

SMTP 参数从 `alert_setting` 表中动态读取（`smtp_host`、`smtp_port`、`smtp_username`、`smtp_password`），每次发送时构建 `JavaMailSender` 实例，支持 SSL。

***

## 通信协议

### TCP 消息类型

客户端与服务端之间通过 TCP 长连接通信，采用统一的 `TYPE|JSON_BODY\n` 文本协议格式。

**客户端 → 服务端**：

| TYPE                 | Body 结构                                             | 说明           |
| -------------------- | --------------------------------------------------- | ------------ |
| `AUTH`               | `{"agentKey":"xxx","clientPubKey":"base64..."}`     | 认证请求（含ECDH公钥） |
| `HOST_INFO`          | HostInfo 字段 JSON                                    | 主机静态信息上报     |
| `HOST_METRIC`        | HostMetric 字段 JSON                                  | 性能指标上报       |
| `PROCESS_LIST`       | `[{pid,name,cpuUsage,...}]`                         | 全量进程列表（旧版）   |
| `PROCESS_LIST_FULL`  | `{seq,processes:[{...}]}`                           | 全量进程列表（增量协议） |
| `PROCESS_LIST_DELTA` | `{seq,added:[],removed:[],changed:[]}`              | 增量进程变化       |
| `ENCRYPTED`          | `{"ciphertext":"base64..."}`                        | 加密消息包装层      |

**服务端 → 客户端**：

| TYPE             | Body 结构                                       | 说明             |
| ---------------- | --------------------------------------------- | -------------- |
| `CONNECTED`      | `{"version":"1.0"}`                           | 连接欢迎消息         |
| `OK`             | `{"message":"xxx","serverPubKey":"base64..."}` | 操作成功（含ECDH公钥）  |
| `ERROR`          | `{"message":"xxx"}`                           | 操作失败           |
| `CMD`            | `{"command":"SCREEN_START"}`                  | 下发命令           |
| `CONFIG`         | `{"key":"value",...}`                         | 认证后下发配置（全量）   |
| `CONFIG_UPDATE`  | `{"key":"value",...}`                         | 配置热更新（增量）     |
| `PROCESS_RESYNC` | `{}`                                          | 要求客户端重发全量进程列表  |
| `ENCRYPTED`      | `{"ciphertext":"base64..."}`                  | 加密消息包装层       |

### 通信加密

客户端与服务端之间的 TCP 和 UDP 通信支持基于 ECDH 密钥协商的 AES-256-GCM 加密。

**加密方案**：

| 组件 | 算法 | 说明 |
|------|------|------|
| 密钥交换 | X25519 (ECDH) | 椭圆曲线 Diffie-Hellman，生成 256 位共享密钥 |
| 密钥推导 | SHA-256 + HKDF Salt | 从共享密钥推导 AES-256 密钥 |
| 对称加密 | AES-256-GCM | 提供加密和完整性校验（128 位 Tag） |
| IV 生成 | SecureRandom | 每条消息独立 12 字节随机 IV |

**密钥协商流程**：

```
客户端                                    服务端
  |                                         |
  |<---------- CONNECTED ------------------|  (明文)
  |                                         |
  |--- AUTH{agentKey, clientPubKey} ------->|  (明文，含客户端公钥)
  |                                         |
  |<-- OK{message, serverPubKey} ----------|  (明文，含服务端公钥)
  |                                         |
  |  双方各自: deriveAesKey(peerPubKey)      |
  |  → 生成相同的 AES-256 密钥              |
  |                                         |
  |--- ENCRYPTED{ciphertext} ------------->|  (后续所有TCP消息加密)
  |<-- ENCRYPTED{ciphertext} --------------|
  |                                         |
  |--- UDP[header][encrypted_jpeg] -------->|  (UDP帧加密)
```

**关键设计**：

- **向后兼容**：如果客户端未提供公钥，或 ECDH 密钥交换失败，自动降级为明文通信
- **TCP/UDP 共享密钥**：UdpSender 使用与 NetworkClient 相同的 CryptoService 实例，AES 密钥一致
- **每条消息独立 IV**：AES-GCM 每次加密生成随机 12 字节 IV，防止重放攻击
- **无额外依赖**：使用 JDK 21 内置的 X25519 和 AES-GCM 实现，无需引入第三方加密库
- **加密消息格式**：`ENCRYPTED|{"ciphertext":"base64(iv+encrypted+tag)"}\n`，ciphertext 中前 12 字节为 IV

### 配置下发协议

```json
// 认证后全量下发
CONFIG|{"client_metric_interval_ms":"5000","client_process_interval_ms":"15000","client_screen_fps":"5","client_screen_jpeg_quality":"0.5","client_process_cpu_threshold":"1.0","client_heartbeat_timeout_ms":"60000","client_offline_threshold_ms":"60000"}

// 运行时增量热更新
CONFIG_UPDATE|{"client_metric_interval_ms":"10000"}
```

### 通信示例

```
客户端                              服务端
  |------- TCP 连接建立 -------------->|
  |<----- CONNECTED|{"version":"1.0"}-|  (欢迎消息)
  |                                     |
  |------- AUTH|{agentKey,clientPubKey}>|  (认证+ECDH公钥)
  |<----- OK|{message,serverPubKey} ---|  (认证成功+服务端公钥)
  |<----- CONFIG|{采集参数配置} --------|  (认证后即时下发配置)
  |                                     |
  |--- ENCRYPTED|{ciphertext} --------->|  (加密: HOST_INFO)
  |<----- ENCRYPTED|{ciphertext} -------|  (加密: OK)
  |                                     |
  |--- ENCRYPTED|{ciphertext} --------->|  (加密: HOST_METRIC)
  |      ...                            |
  |                                     |
  |--- ENCRYPTED|{ciphertext} --------->|  (加密: PROCESS_LIST_FULL)
  |<----- ENCRYPTED|{ciphertext} -------|  (加密: OK)
  |--- ENCRYPTED|{ciphertext} --------->|  (加密: PROCESS_LIST_DELTA)
  |      ...                            |
  |                                     |
  |<----- ENCRYPTED|{ciphertext} -------|  (加密: CONFIG_UPDATE)
  |                                     |
  |<----- ENCRYPTED|{ciphertext} -------|  (加密: CMD SCREEN_START)
  |------- UDP帧(MAGIC+key+enc_jpeg) ->|  (加密屏幕帧)
  |      ...                            |
  |<----- ENCRYPTED|{ciphertext} -------|  (加密: CMD SCREEN_STOP)
```

### 协议扩展

所有消息类型通过 `MessageConstants` 常量类统一管理，新增消息类型只需：

1. 在 `MessageConstants` 中添加类型常量
2. 定义消息体的 DTO 类
3. 在 `ClientHandler.handleAuthenticatedMessage()` 中添加一个 `case` 分支

加密消息对上层逻辑透明——`ClientHandler` 在 `handleMessage()` 中自动解密 `ENCRYPTED` 消息后，将内部消息交给现有处理逻辑；发送时 `sendMessage()` 自动判断是否加密。

***

## 权限模型

系统采用 RBAC（基于角色的访问控制）模型，定义三种角色：

| 角色    | 角色标识         | 说明                             |
| ----- | ------------ | ------------------------------ |
| 超级管理员 | SUPER\_ADMIN | 拥有系统所有权限，不属于任何分组，可管理所有用户、分组、主机 |
| 组管理员  | GROUP\_ADMIN | 管理本组用户和主机，可管理本组进程规则和部分设置    |
| 普通用户  | USER         | 查看仪表盘、性能指标、接收告警通知              |

### 设置权限

组管理员对本组设置页面可访问：客户端设置（可读写）、告警设置（可读写），不可访问常规设置和通知设置。

### 权限校验流程

```
Controller 进入
    ↓
AccessChecker.isSuperAdmin() / requireSuperAdmin() / 特定权限方法
    ↓
从 SecurityContext.getAuthentication().getDetails() 获取 roles（JWT Filter 已写入）
    ↓
判断 → 放行或抛 ForbiddenException(403) / UnauthorizedException(401)
```

不再需要每个 Controller 自己查 `userService.getUserRoleKeys()`。

***

## 数据库设计

### 表结构总览

| 表名 | 说明 | 核心外键 |
|------|------|----------|
| `group` | 分组表 | 无 |
| `user` | 用户表 | `group_id` → `group.id` |
| `role` | 角色表 | 无 |
| `user_role` | 用户-角色关联表 | `user_id` → `user.id`, `role_id` → `role.id` |
| `permission` | 权限表（树形，预留） | `parent_id` 自引用 |
| `role_permission` | 角色-权限关联表（预留） | `role_id` → `role.id`, `permission_id` → `permission.id` |
| `host` | 主机信息表 | `user_id` → `user.id` |
| `host_metric` | 性能指标表 | `host_id` → `host.id` |
| `host_process` | 主机进程表 | `host_id` → `host.id` |
| `alert_setting` | 全局设置表 | 无 |
| `group_setting` | 组级别设置覆盖表 | `group_id` → `group.id` |
| `process_rule` | 进程规则表 | `group_id` → `group.id` |
| `alert_record` | 告警记录表 | 无外键约束 |
| `notification` | 通知表 | 无外键约束 |
| `user_notification` | 用户-通知关联表 | `user_id` → `user.id`, `notification_id` → `notification.id` |
| `login_attempt` | 登录尝试记录表 | 无外键约束 |

### 表关系图

```
group ──1:N── user ──1:1── host ──1:N── host_metric
  │            │                  └──1:N── host_process
  │            └──N:M── role ──N:M── permission
  │                 │
  ├──1:N── process_rule
  └──1:N── group_setting (覆盖 alert_setting 的组级配置)

notification ──N:M── user_notification ──N:1── user

alert_setting  (独立配置表，全局默认值)
alert_record   (通过 host_id 关联 host)
login_attempt  (独立记录表，用于防爆破)
```

### 新增表：login_attempt（登录尝试记录表）

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT AUTO_INCREMENT | 主键 |
| username | VARCHAR(50) NOT NULL | 登录用户名 |
| ip_address | VARCHAR(45) NOT NULL | 客户端 IP 地址 |
| success | TINYINT NOT NULL DEFAULT 0 | 是否成功（0=失败, 1=成功） |
| create_time | DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP | 创建时间 |

索引：
- `idx_username_time` (`username`, `create_time`) — 账号锁定查询
- `idx_ip_time` (`ip_address`, `create_time`) — IP 限速和验证码触发查询

### 组级别设置覆盖表（group_setting）

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT AUTO_INCREMENT | 主键 |
| group_id | BIGINT NOT NULL | 分组ID（外键→group.id） |
| setting_key | VARCHAR(100) NOT NULL | 设置键 |
| setting_value | VARCHAR(500) | 设置值 |
| description | VARCHAR(200) | 描述 |
| update_time | DATETIME | 更新时间 |

唯一约束：`(group_id, setting_key)`

可覆盖的设置键（由 `GroupSettingService.OVERRIDABLE_KEYS` 控制）：
`performance_alert_enabled`, `cpu_threshold`, `memory_threshold`, `disk_threshold`, `alert_dedup_minutes`, `process_alert_enabled`, `client_metric_interval_ms`, `client_process_interval_ms`, `client_screen_fps`, `client_screen_jpeg_quality`, `client_process_cpu_threshold`

### 全局设置项（alert_setting 表完整列表）

| setting_key | 默认值 | 说明 |
|-------------|--------|------|
| `session_timeout_hours` | `24` | 会话超时（小时） |
| `data_retention_days` | `2` | 数据保留天数 |
| `notification_retention_days` | `30` | 通知保留天数 |
| `email_enabled` | `false` | 邮件通知开关 |
| `smtp_host` | — | SMTP 服务器地址 |
| `smtp_port` | `465` | SMTP 端口 |
| `smtp_username` | — | SMTP 发件人地址 |
| `smtp_password` | — | SMTP 授权码 |
| `process_alert_enabled` | `true` | 进程告警开关 |
| `performance_alert_enabled` | `true` | 性能告警开关 |
| `cpu_threshold` | `90` | CPU 告警阈值 (%) |
| `memory_threshold` | `90` | 内存告警阈值 (%) |
| `disk_threshold` | `90` | 磁盘告警阈值 (%) |
| `alert_dedup_minutes` | `30` | 告警去重间隔（分钟） |
| `client_metric_interval_ms` | `5000` | 客户端指标采集间隔 |
| `client_process_interval_ms` | `15000` | 客户端进程采集间隔 |
| `client_screen_fps` | `5` | 屏幕推流帧率 |
| `client_screen_jpeg_quality` | `0.5` | 屏幕 JPEG 质量 |
| `client_process_cpu_threshold` | `1.0` | 进程 CPU 变化上报阈值 (%) |
| `client_heartbeat_timeout_ms` | `60000` | 心跳超时（毫秒） |
| `client_offline_threshold_ms` | `60000` | 离线判定阈值（毫秒） |

### 定时清理

`DataCleanupTask` 替代了原本的 MySQL Event，在应用层通过 `@Scheduled` 执行，清理周期可通过 `data_retention_days` 和 `notification_retention_days` 配置。`login_attempt` 固定清理 24 小时前的记录。

***

## API 接口

### 认证接口（AuthController）

基础路径：`/api/auth`

| 方法 | 路径 | 权限 | 说明 |
|------|------|------|------|
| POST | `/api/auth/login` | 公开 | 登录（支持验证码参数） |
| POST | `/api/auth/register` | 公开 | 注册 |
| GET | `/api/auth/captcha` | 公开 | 获取验证码图片 |
| GET | `/api/auth/login-status` | 公开 | 查询是否需要验证码 |

**登录请求体**：

```json
{
  "username": "admin",
  "password": "123456",
  "captchaKey": "uuid-of-captcha",      // 可选，需要验证码时必填
  "captchaAnswer": "42"                  // 可选，需要验证码时必填
}
```

**验证码响应**：

```json
{
  "code": 200,
  "data": {
    "captchaKey": "a1b2c3d4e5f6...",
    "captchaImage": "data:image/png;base64,iVBOR..."
  }
}
```

**登录状态响应**：

```json
{
  "code": 200,
  "data": {
    "captchaRequired": true
  }
}
```

### 系统设置接口（SystemSettingController）

基础路径：`/api/system-settings`

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/system-settings` | 获取系统设置（超管全量，组管过滤） |
| PUT | `/api/system-settings` | 更新系统设置（支持配置热推送） |
| POST | `/api/system-settings/email/test` | 发送测试邮件 |

### 组设置接口（GroupSettingController）

基础路径：`/api/group-settings`

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/group-settings/{groupId}` | 获取组设置（含全局默认值合并） |
| PUT | `/api/group-settings/{groupId}` | 更新组设置覆盖项 |

### 全局告警设置接口（AlertSettingController）

基础路径：`/api/alert-settings`

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/alert-settings` | 获取全局告警设置 |
| PUT | `/api/alert-settings` | 更新全局告警设置 |

### 其他接口

基础路径前缀统一为 `/api`，各 Controller 详见项目源码。

---

## 部署指南

### 环境要求

- JDK 21+
- MySQL 8.0+
- Maven 3.8+
- Node.js 20+

### 服务端部署

1. **创建数据库**

```sql
CREATE DATABASE host_monitor DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

2. **导入初始化脚本**

```bash
mysql -u username -p host_monitor < sql/init.sql
```

> 注意：init.sql 中包含 BCrypt 加密的密码哈希，请勿通过 shell `-e` 参数执行。建议使用管道方式导入。

3. **配置数据库连接**

修改 `backend/src/main/resources/application.properties`:

```properties
spring.datasource.url=jdbc:mysql://localhost:3306/host_monitor?useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true
spring.datasource.username=root
spring.datasource.password=your_password

# JWT 配置（可选，有默认值）
jwt.secret=your-secret-key-at-least-256-bits
```

4. **启动服务**

```bash
cd backend
mvn spring-boot:run
```

服务端默认端口：

- HTTP: `10800`
- TCP Socket: `10801`
- UDP Screen: `10802`

5. **配置邮件通知（可选）**

登录系统后进入系统设置 → 通知设置 → 填写 SMTP 信息 → 发送测试邮件验证。

### 前端部署

```bash
cd fronted
npm install
npm run dev
```

前端默认端口：`5173`

Vite 代理配置已包含 `/api`、`/ws` 路径的转发，无需额外配置 Nginx。

### 客户端配置

修改 `monitor-client/src/main/resources/application.properties`:

```properties
socket.host=your_server_ip
socket.port=10801
agent.key=your_agent_key_here
screen.enabled=true
screen.udp.port=10802
screen.fps=5
screen.jpeg.quality=0.5
metric.interval.ms=5000
```

> 注意：`metric.interval.ms`、`screen.fps` 等本地配置仅作为初始值。客户端连接认证后，服务端会下发配置覆盖本地设置。

运行客户端：

```bash
cd monitor-client
mvn spring-boot:run
```

---

## 告警流程

### 进程告警

1. 进程数据入库（全量或增量）时，触发进程告警检测
2. 检查 `process_alert_enabled` 开关（先查组覆盖，再查全局）
3. 获取主机所属分组的黑白名单规则
4. 匹配：黑名单进程在运行中 → BLACKLIST_RUNNING；白名单进程未运行 → WHITELIST_MISSING
5. 去重检查：同一主机同一类型同一进程在 `alert_dedup_minutes` 内不重复创建
6. 创建告警记录 + 发送系统通知 + 异步发送邮件

### 性能告警

1. 性能指标入库时，触发性能告警检测（每 30 秒最多一次）
2. 检查 `performance_alert_enabled` 开关（先查组覆盖，再查全局）
3. 比较指标值与阈值（CPU/Memory/Disk，先查组覆盖，再查全局）
4. 去重检查 → 创建告警记录 → 通知 + 邮件

---

## 主机在线状态管理

- 客户端连接并发送 `HOST_INFO` 时，主机状态设为在线
- 客户端断开连接时，服务端自动将主机状态设为离线
- 服务端心跳检测线程每 30 秒检查一次，超时时间通过 `client_heartbeat_timeout_ms` 配置（默认 60 秒）
- 服务端启动时，自动将所有在线主机重置为离线
- 主机列表 API 支持按在线状态筛选
