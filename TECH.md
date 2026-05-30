# HostMonitor 技术文档

本文档面向开发者和运维人员，详细描述 HostMonitor 系统的技术架构、通信协议、数据库设计、API 接口、部署方式及核心流程实现。

***

## 技术栈

### 后端

Spring Boot 3.4.7 / Spring Security 6.4.x / MyBatis-Plus 3.5.9 / MySQL 8.0+ / JWT (jjwt 0.12.6) / WebSocket

### 前端

Vue 3.5.x / TypeScript 6.0.x / Ant Design Vue 4.2.x / Axios / ECharts 6.1.x / Pinia / Vite 8.0.x

### 客户端

Java 21 / OSHI 6.6.5 / Java AWT Robot / UDP/TCP Socket

***

## 项目结构

### 后端（backend）

```
src/main/java/com/ylh/backend/
├── BackendApplication.java          # Spring Boot 启动类
├── Common/                          # 通用工具
│   └── Result.java                  # 统一响应封装
├── Config/                          # 配置类
│   ├── GlobalExceptionHandler.java  # 全局异常处理
│   ├── JwtAuthenticationFilter.java # JWT 认证过滤器
│   ├── JwtUtil.java                 # JWT 工具类
│   ├── MyBatisMetaObjectHandler.java# MyBatis-Plus 自动填充
│   ├── MyBatisPlusConfig.java       # MyBatis-Plus 配置
│   ├── SecurityConfig.java          # Spring Security 配置
│   └── WebSocketConfig.java         # WebSocket 配置
├── Controller/                      # REST API 控制层
│   ├── AlertController.java         # 告警记录接口
│   ├── AlertSettingController.java  # 告警设置接口
│   ├── AuthController.java          # 认证接口
│   ├── GroupController.java         # 分组管理接口
│   ├── HostInfoController.java      # 主机信息接口
│   ├── HostProcessController.java   # 主机进程接口
│   ├── NotificationController.java  # 通知接口
│   ├── ProcessRuleController.java   # 进程规则接口
│   └── UserController.java          # 用户管理接口
├── Dto/                             # 数据传输对象
├── Entity/                          # 数据库实体类
├── Mapper/                          # MyBatis Mapper 接口
├── Service/                         # 业务逻辑层
│   ├── AlertService.java            # 告警服务
│   ├── AlertSettingService.java     # 告警设置服务
│   ├── AuthService.java             # 认证服务
│   ├── GroupService.java            # 分组服务
│   ├── HostInfoService.java         # 主机信息服务
│   ├── HostProcessService.java      # 主机进程服务
│   ├── NotificationService.java     # 通知服务
│   ├── ProcessRuleService.java      # 进程规则服务
│   └── UserService.java             # 用户服务
└── Socket/                          # Socket 通信模块
    ├── ClientHandler.java           # 客户端连接处理器
    ├── MessageConstants.java        # 消息类型常量
    ├── MessageParser.java           # 消息解析器
    ├── ScreenStreamManager.java     # 屏幕流管理器
    ├── ScreenWebSocketHandler.java  # 屏幕 WebSocket 转发
    ├── SocketMessage.java           # Socket 消息模型
    ├── SocketServer.java            # TCP Socket 服务端
    └── UdpScreenServer.java         # UDP 屏幕接收服务端
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
│   └── user.ts                      # 用户状态（登录态、权限）
├── utils/
│   ├── request.ts                   # Axios 封装（拦截器、Token 注入）
│   └── settings.ts                  # 应用设置工具
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
    ├── LoginView.vue                # 登录
    └── RegisterView.vue             # 注册
```

### 客户端（monitor-client）

```
src/main/java/com/ylh/client/
├── MonitorClient.java               # 客户端启动类
├── common/
│   ├── MessageConstants.java        # 消息类型常量
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
│   ├── NetworkClient.java           # TCP 网络客户端
│   └── UdpSender.java               # UDP 数据发送器
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
│   localhost:6000    │ <────────────────────── │   localhost:10800   │
│                     │     WebSocket           │                     │
└─────────────────────┘                        └───────────┬─────────┘
                                                           │
                                                           │ TCP (10801) 认证/指令/指标/进程
                                                           │ UDP (10802) 屏幕帧
                                                           ▼
                                               ┌─────────────────────┐
                                               │   客户端 (Java 21)  │
                                               │  屏幕截图 / 指标采集 │
                                               │  进程采集 / 增量上报  │
                                               └─────────────────────┘
```

通信协议说明：

- **HTTP REST API**：前端与后端之间的常规数据交互（用户管理、主机列表、告警、通知等）
- **WebSocket**：前端订阅屏幕监控实时画面，后端推送 JPEG 帧数据
- **TCP Socket**：客户端与后端之间的长连接，用于 Agent Key 认证、主机信息上报、性能指标上报、进程列表上报、指令下发
- **UDP Socket**：客户端向后端发送屏幕截图帧数据，低延迟传输

***

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

> 注意：init.sql 中包含 BCrypt 加密的密码哈希，请勿通过 shell `-e` 参数执行，避免 `$` 符号被变量展开。建议使用管道方式导入：`mysql ... < file.sql`

3. **配置数据库连接**

修改 `backend/src/main/resources/application.properties`:

```properties
spring.datasource.url=jdbc:mysql://localhost:3306/host_monitor?useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true
spring.datasource.username=root
spring.datasource.password=your_password
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

### 前端部署

```bash
cd fronted
npm install
npm run dev
```

前端默认端口：`6000`

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

运行客户端：

```bash
cd monitor-client
mvn spring-boot:run
```

***

## 通信协议

客户端与服务端之间通过 TCP 长连接通信，采用统一的 `TYPE|JSON_BODY\n` 文本协议格式：

- `TYPE`：消息类型标识（大写下划线风格）
- `|`：类型与消息体的分隔符
- `JSON_BODY`：JSON 编码的消息体
- `\n`：消息边界分隔符

### 消息类型

**客户端 → 服务端**：

| TYPE                 | Body 结构                                | 说明           |
| -------------------- | -------------------------------------- | ------------ |
| `AUTH`               | `{"agentKey":"xxx"}`                   | 认证请求         |
| `HOST_INFO`          | HostInfo 字段 JSON                       | 主机静态信息上报     |
| `HOST_METRIC`        | HostMetric 字段 JSON                     | 性能指标上报       |
| `PROCESS_LIST`       | `[{pid,name,cpuUsage,...}]`            | 全量进程列表（旧版）   |
| `PROCESS_LIST_FULL`  | `{seq,processes:[{...}]}`              | 全量进程列表（增量协议） |
| `PROCESS_LIST_DELTA` | `{seq,added:[],removed:[],changed:[]}` | 增量进程变化       |

**服务端 → 客户端**：

| TYPE             | Body 结构                      | 说明             |
| ---------------- | ---------------------------- | -------------- |
| `CONNECTED`      | `{"version":"1.0"}`          | 连接欢迎消息         |
| `OK`             | `{"message":"xxx","id":n}`   | 操作成功（id 为可选字段） |
| `ERROR`          | `{"message":"xxx"}`          | 操作失败           |
| `CMD`            | `{"command":"SCREEN_START"}` | 下发命令           |
| `PROCESS_RESYNC` | `{}`                         | 要求客户端重发全量进程列表  |

### 通信示例

```
客户端                              服务端
  |------- TCP 连接建立 -------------->|
  |<----- CONNECTED|{"version":"1.0"}-|  (欢迎消息)
  |                                     |
  |------- AUTH|{"agentKey":"xxx"} ---->|  (认证)
  |<----- OK|{"message":"Authenticated"}| (认证成功)
  |                                     |
  |------- HOST_INFO|{...} ------------>|  (主机信息上报)
  |<----- OK|{"message":"HostInfo received","id":1}| (确认)
  |                                     |
  |------- HOST_METRIC|{...} --------->|  (定时指标上报)
  |      ...                            |
  |                                     |
  |------- PROCESS_LIST_FULL|{...} --->|  (全量进程列表)
  |<----- OK|{"message":"...","seq":2} | (确认，期望下次seq=2)
  |------- PROCESS_LIST_DELTA|{...} -->|  (增量进程变化)
  |      ...                            |
  |                                     |
  |<----- CMD|{"command":"SCREEN_START"}| (有用户订阅截屏)
  |------- UDP帧(MAGIC+key+JPEG) ----->|  (截屏帧数据)
  |      ...                            |
  |<----- CMD|{"command":"SCREEN_STOP"}| (无用户订阅截屏)
```

### 协议扩展

所有消息类型通过 `MessageConstants` 常量类统一管理，新增消息类型只需：

1. 在 `MessageConstants` 中添加类型常量
2. 定义消息体的 DTO 类
3. 在 `ClientHandler.handleAuthenticatedMessage()` 中添加一个 `case` 分支

***

## 权限模型

系统采用 RBAC（基于角色的访问控制）模型，定义三种角色：

| 角色    | 角色标识         | 说明                             |
| ----- | ------------ | ------------------------------ |
| 超级管理员 | SUPER\_ADMIN | 拥有系统所有权限，不属于任何分组，可管理所有用户、分组、主机 |
| 组管理员  | GROUP\_ADMIN | 管理本组用户和主机，可向组内发送通知，可管理本组进程规则   |
| 普通用户  | USER         | 查看仪表盘、性能指标、接收告警通知              |

### 权限规则

- 超级管理员不属于任何分组（group\_id 为 NULL），不可被加入分组、不可被删除、不可被降级
- 每个分组最多只能有一个组管理员
- 组管理员只能管理本组内的用户和主机
- 组管理员被移出分组时自动降级为普通用户
- 超级管理员角色不可被创建或分配，仅通过系统初始化产生

### 菜单权限

| 菜单   | 超级管理员 |  组管理员 |  普通用户 |
| ---- | :---: | :---: | :---: |
| 仪表盘  |   ✅   |   ✅   |   ✅   |
| 主机管理 |   ✅   |   ✅   |   ✅   |
| 告警中心 |   ✅   | ✅（本组） | ✅（本人） |
| 进程管理 |   ✅   |   ✅   |   ❌   |
| 用户管理 |   ✅   | ✅（本组） |   ❌   |
| 组织管理 |   ✅   |   ❌   |   ❌   |
| 通知管理 |   ✅   | ✅（本组） | ✅（接收） |
| 系统设置 |   ✅   |   ❌   |   ❌   |

***

## 认证机制

### 用户认证（JWT）

1. 用户通过 `/api/auth/login` 登录，后端验证用户名密码
2. 验证成功后生成 JWT Token 返回前端
3. 前端在后续请求的 `Authorization: Bearer <token>` 头中携带 Token
4. Spring Security 过滤链校验 Token 有效性

### 客户端认证（Agent Key）

1. 用户注册时系统自动生成 UUID 格式的 Agent Key，存入 user 表
2. 客户端启动时使用配置的 Agent Key 连接服务端 TCP Socket（端口 10801）
3. 客户端发送 `AUTH|{"agentKey":"xxx"}` 消息进行认证
4. 服务端验证 Agent Key 对应的用户，建立用户-主机一对一绑定
5. 同一 Agent Key 仅允许一个客户端连接，重复连接将被拒绝
6. 超级管理员可在用户管理界面重置 Agent Key，重置后自动断开该用户的客户端连接

***

## 数据库设计

### 表结构总览

| 表名 | 说明 | 核心外键 |
|------|------|----------|
| `group` | 分组表 | 无 |
| `user` | 用户表 | `group_id` → `group.id` |
| `role` | 角色表 | 无 |
| `user_role` | 用户-角色关联表 | `user_id` → `user.id`, `role_id` → `role.id` |
| `permission` | 权限表（树形） | `parent_id` 自引用 |
| `role_permission` | 角色-权限关联表 | `role_id` → `role.id`, `permission_id` → `permission.id` |
| `host` | 主机信息表 | `user_id` → `user.id` |
| `host_metric` | 性能指标表 | `host_id` → `host.id` |
| `host_process` | 主机进程表 | `host_id` → `host.id` |
| `alert_setting` | 告警设置表 | 无 |
| `process_rule` | 进程规则表 | `group_id` → `group.id` |
| `alert_record` | 告警记录表 | 无外键约束 |
| `notification` | 通知表 | 无外键约束 |
| `user_notification` | 用户-通知关联表 | `user_id` → `user.id`, `notification_id` → `notification.id` |

### 表关系图

```
group ──1:N── user ──1:1── host ──1:N── host_metric
  │            │                  └──1:N── host_process
  │            └──N:M── role ──N:M── permission
  │                 │
  └──1:N── process_rule
  │
  └── (通过 user.group_id 关联)

notification ──N:M── user_notification ──N:1── user

alert_setting (独立配置表)
alert_record  (通过 host_id 关联 host)
```

### 详细表结构

#### group（分组表）

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT AUTO_INCREMENT | 主键 |
| group_name | VARCHAR(100) NOT NULL | 分组名称 |
| description | VARCHAR(255) | 分组描述 |
| create_time | DATETIME | 创建时间 |
| update_time | DATETIME | 更新时间 |
| is_del | TINYINT DEFAULT 0 | 逻辑删除标记 |

#### user（用户表）

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT AUTO_INCREMENT | 主键 |
| username | VARCHAR(50) NOT NULL | 用户名（唯一） |
| email | VARCHAR(100) NOT NULL | 邮箱（唯一） |
| password | VARCHAR(255) NOT NULL | BCrypt 加密密码 |
| agent_key | VARCHAR(128) NOT NULL | 客户端认证密钥（唯一） |
| avatar | VARCHAR(255) | 头像 |
| status | TINYINT DEFAULT 1 | 状态（1启用/0禁用） |
| group_id | BIGINT | 所属分组ID（外键→group.id） |
| create_time | DATETIME | 创建时间 |
| update_time | DATETIME | 更新时间 |
| is_del | TINYINT DEFAULT 0 | 逻辑删除标记 |

#### role（角色表）

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT AUTO_INCREMENT | 主键 |
| role_key | VARCHAR(50) NOT NULL | 角色标识（唯一，如 SUPER_ADMIN） |
| role_name | VARCHAR(100) NOT NULL | 角色名称 |
| description | VARCHAR(255) | 角色描述 |
| create_time | DATETIME | 创建时间 |
| update_time | DATETIME | 更新时间 |
| is_del | TINYINT DEFAULT 0 | 逻辑删除标记 |

#### user_role（用户-角色关联表）

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT AUTO_INCREMENT | 主键 |
| user_id | BIGINT NOT NULL | 用户ID（外键→user.id） |
| role_id | BIGINT NOT NULL | 角色ID（外键→role.id） |
| create_time | DATETIME | 创建时间 |

#### permission（权限表）

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT AUTO_INCREMENT | 主键 |
| perm_key | VARCHAR(100) NOT NULL | 权限标识（唯一） |
| perm_name | VARCHAR(100) NOT NULL | 权限名称 |
| parent_id | BIGINT | 父权限ID（树形结构） |
| type | TINYINT NOT NULL | 权限类型 |
| sort_order | INT DEFAULT 0 | 排序序号 |
| create_time | DATETIME | 创建时间 |
| update_time | DATETIME | 更新时间 |
| is_del | TINYINT DEFAULT 0 | 逻辑删除标记 |

#### role_permission（角色-权限关联表）

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT AUTO_INCREMENT | 主键 |
| role_id | BIGINT NOT NULL | 角色ID（外键→role.id） |
| permission_id | BIGINT NOT NULL | 权限ID（外键→permission.id） |
| create_time | DATETIME | 创建时间 |

#### host（主机信息表）

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT AUTO_INCREMENT | 主键 |
| user_id | BIGINT NOT NULL | 所属用户ID（外键→user.id，唯一） |
| hostname | VARCHAR(100) NOT NULL | 主机名 |
| ip_address | VARCHAR(45) DEFAULT '' | IP地址 |
| cpu_model | VARCHAR(255) | CPU型号 |
| cpu_cores | INT | CPU核心数 |
| memory_total | BIGINT | 总内存（字节） |
| disk_total | BIGINT | 总磁盘（字节） |
| os_name | VARCHAR(100) | 操作系统名称 |
| os_version | VARCHAR(100) | 操作系统版本 |
| status | TINYINT DEFAULT 0 | 状态（0离线/1在线） |
| last_heartbeat | DATETIME | 最后心跳时间 |
| create_time | DATETIME | 创建时间 |
| update_time | DATETIME | 更新时间 |
| is_del | TINYINT DEFAULT 0 | 逻辑删除标记 |

#### host_metric（性能指标表）

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT AUTO_INCREMENT | 主键 |
| host_id | BIGINT NOT NULL | 主机ID（外键→host.id） |
| cpu_usage | DECIMAL(5,2) | CPU使用率（%） |
| memory_usage | DECIMAL(5,2) | 内存使用率（%） |
| disk_usage | DECIMAL(5,2) | 磁盘使用率（%） |
| memory_used | BIGINT | 已用内存（字节） |
| disk_used | BIGINT | 已用磁盘（字节） |
| collect_time | DATETIME NOT NULL | 采集时间 |

#### host_process（主机进程表）

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT AUTO_INCREMENT | 主键 |
| host_id | BIGINT NOT NULL | 主机ID（外键→host.id） |
| pid | INT NOT NULL | 进程ID |
| ppid | INT | 父进程ID |
| name | VARCHAR(255) | 进程名 |
| user | VARCHAR(128) | 运行用户 |
| cpu_usage | DECIMAL(10,4) | CPU使用率（%） |
| memory_usage | DECIMAL(10,4) | 内存使用率（%） |
| memory_used | BIGINT | 已用内存（字节） |
| status | VARCHAR(32) | 进程状态 |
| start_time | DATETIME | 启动时间 |
| command_line | TEXT | 完整命令行 |
| collect_time | DATETIME NOT NULL | 采集时间 |

#### alert_setting（告警设置表）

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT AUTO_INCREMENT | 主键 |
| setting_key | VARCHAR(50) NOT NULL | 设置键（唯一） |
| setting_value | VARCHAR(255) NOT NULL | 设置值 |
| description | VARCHAR(255) | 设置描述 |
| update_time | DATETIME | 更新时间 |

#### process_rule（进程规则表）

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT AUTO_INCREMENT | 主键 |
| group_id | BIGINT NOT NULL | 分组ID（外键→group.id） |
| process_name | VARCHAR(255) NOT NULL | 进程名 |
| rule_type | VARCHAR(20) NOT NULL | 规则类型（WHITELIST/BLACKLIST） |
| description | VARCHAR(255) | 规则描述 |
| create_time | DATETIME | 创建时间 |
| update_time | DATETIME | 更新时间 |
| is_del | TINYINT DEFAULT 0 | 逻辑删除标记 |

#### alert_record（告警记录表）

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT AUTO_INCREMENT | 主键 |
| host_id | BIGINT NOT NULL | 主机ID |
| alert_type | VARCHAR(30) NOT NULL | 告警类型（BLACKLIST_RUNNING/WHITELIST_MISSING/PERFORMANCE_CPU/PERFORMANCE_MEMORY/PERFORMANCE_DISK） |
| process_name | VARCHAR(255) NOT NULL | 相关进程名 |
| rule_id | BIGINT | 关联规则ID |
| notification_id | BIGINT | 关联通知ID |
| status | TINYINT DEFAULT 0 | 状态（0未处理/1已处理） |
| create_time | DATETIME | 创建时间 |

#### notification（通知表）

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT AUTO_INCREMENT | 主键 |
| title | VARCHAR(100) NOT NULL | 通知标题 |
| content | TEXT NOT NULL | 通知内容 |
| sender_id | BIGINT | 发送者ID（NULL表示系统发送） |
| send_type | TINYINT NOT NULL | 发送类型（1全体/2分组/3指定用户） |
| target_group_id | BIGINT | 目标分组ID |
| target_user_ids | TEXT | 目标用户ID列表（JSON数组） |
| status | TINYINT DEFAULT 1 | 状态（0草稿/1已发送） |
| create_time | DATETIME | 创建时间 |
| send_time | DATETIME | 发送时间 |

#### user_notification（用户-通知关联表）

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT AUTO_INCREMENT | 主键 |
| user_id | BIGINT NOT NULL | 用户ID（外键→user.id） |
| notification_id | BIGINT NOT NULL | 通知ID（外键→notification.id） |
| is_read | TINYINT DEFAULT 0 | 已读状态（0未读/1已读） |
| read_time | DATETIME | 已读时间 |
| create_time | DATETIME | 创建时间 |

### 定时清理事件

| 事件名 | 执行周期 | 说明 |
|--------|---------|------|
| ev_clean_host_metric | 每天凌晨3:00 | 清理48小时前的 host_metric 数据 |
| ev_clean_host_process | 每48小时 | 清理48小时前的 host_process 数据 |

***

## API 接口

### 认证接口（AuthController）

基础路径：`/api/auth`

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/auth/login` | 用户登录，返回 JWT Token |
| POST | `/api/auth/register` | 用户注册（仅注册为普通用户） |
| GET | `/api/auth/agent-key` | 获取当前用户的 Agent Key |
| POST | `/api/auth/agent-key/reset` | 重置当前用户的 Agent Key |

### 用户管理接口（UserController）

基础路径：`/api/users`

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/users/me` | 获取当前登录用户信息 |
| GET | `/api/users/roles` | 获取角色列表 |
| GET | `/api/users/groups` | 获取分组列表 |
| GET | `/api/users` | 分页查询用户列表（支持关键词、角色、分组筛选） |
| POST | `/api/users` | 创建新用户 |
| PUT | `/api/users/{id}` | 更新用户信息 |
| DELETE | `/api/users/{id}` | 删除用户 |
| PUT | `/api/users/{id}/role` | 分配/变更用户角色 |
| POST | `/api/users/{id}/reset-agent-key` | 重置用户 Agent Key（超管，断开客户端连接） |

### 主机信息接口（HostInfoController）

基础路径：`/api/hosts`

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/hosts` | 分页查询主机列表（支持搜索、状态筛选、角色权限过滤） |
| GET | `/api/hosts/{id}` | 获取主机详情 |
| GET | `/api/hosts/{id}/metrics/latest` | 获取主机最新性能指标 |
| GET | `/api/hosts/{id}/metrics/history` | 获取主机指标历史数据（参数：range 时间范围） |
| DELETE | `/api/hosts/{id}` | 删除主机及关联指标数据 |

### 主机进程接口（HostProcessController）

基础路径：`/api/hosts`

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/hosts/{id}/processes` | 获取主机最新进程快照（含黑白名单标记） |
| GET | `/api/hosts/{id}/processes/history` | 获取主机进程历史数据 |

### 分组管理接口（GroupController）

基础路径：`/api/groups`

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/groups` | 获取分组列表 |
| GET | `/api/groups/{id}` | 获取分组详情 |
| POST | `/api/groups` | 创建分组 |
| PUT | `/api/groups/{id}` | 更新分组信息 |
| DELETE | `/api/groups/{id}` | 删除分组（清理成员和管理员） |
| GET | `/api/groups/{id}/members` | 获取分组成员列表 |
| POST | `/api/groups/{id}/members/{userId}` | 将用户加入分组 |
| DELETE | `/api/groups/{id}/members/{userId}` | 将用户移出分组 |
| POST | `/api/groups/{id}/admin/{userId}` | 指定用户为分组管理员 |
| DELETE | `/api/groups/{id}/admin/{userId}` | 撤销分组管理员身份 |
| GET | `/api/groups/{id}/process-rules` | 获取分组进程规则列表 |

### 进程规则接口（ProcessRuleController）

基础路径：`/api`

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/process-rules` | 获取所有进程规则（按角色过滤） |
| POST | `/api/groups/{groupId}/process-rules` | 添加进程规则 |
| DELETE | `/api/groups/{groupId}/process-rules/{ruleId}` | 删除进程规则 |

### 告警接口（AlertController）

基础路径：`/api/alerts`

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/alerts` | 分页查询告警列表（支持类型、状态筛选） |
| PUT | `/api/alerts/{id}/resolve` | 标记告警为已处理 |
| GET | `/api/alerts/unresolved-count` | 获取未处理告警数量 |

### 告警设置接口（AlertSettingController）

基础路径：`/api/alert-settings`

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/alert-settings` | 获取所有告警设置 |
| PUT | `/api/alert-settings` | 更新告警设置（仅超管） |

### 通知接口（NotificationController）

基础路径：`/api/notifications`

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/notifications` | 发送通知 |
| GET | `/api/notifications` | 分页查询当前用户通知列表 |
| PUT | `/api/notifications/{id}/read` | 标记通知为已读 |
| PUT | `/api/notifications/read-all` | 标记所有通知为已读 |
| GET | `/api/notifications/unread-count` | 获取未读通知数量 |
| DELETE | `/api/notifications/{id}` | 删除通知 |

***

## 核心流程

### 客户端连接与主机注册流程

1. 用户注册后，系统自动生成 Agent Key 并存入 user 表
2. 用户将 Agent Key 配置到客户端的 `application.properties`
3. 客户端启动后，通过 TCP Socket 连接服务端（端口 10801）
4. 客户端发送 `AUTH` 消息携带 Agent Key 进行认证
5. 服务端验证 Agent Key，查找对应用户
6. 认证成功后，客户端采集主机信息（CPU 型号、核心数、内存、磁盘、操作系统等）
7. 客户端通过 `HOST_INFO` 消息上报主机信息，服务端创建或更新 host 表记录（user\_id 关联），主机状态设为在线
8. 连接建立后，客户端定期通过 `HOST_METRIC` 消息上报性能指标和心跳
9. 客户端断开连接时，服务端自动将主机状态设为离线
10. 服务端重启时，自动将所有在线主机重置为离线状态

### 屏幕监控流程

1. 前端点击"监控"按钮，建立 WebSocket 连接
2. 后端检测到首个订阅者，通过 `CMD|{"command":"SCREEN_START"}` 通知客户端开始截屏
3. 客户端使用 AWT Robot 截屏，JPEG 压缩后通过 UDP 发送（端口 10802）
4. 后端接收 UDP 帧，通过 WebSocket 广播给所有订阅者
5. 前端收到帧数据，通过 Canvas 双缓冲渲染显示
6. 前端关闭页面，WebSocket 断开
7. 后端检测到最后一个订阅者离开，通过 `CMD|{"command":"SCREEN_STOP"}` 通知客户端停止截屏

### 性能指标监控流程

1. 客户端启动 MetricReporter 定时任务（默认每 5 秒执行一次）
2. 客户端使用 OSHI 库采集系统性能指标（CPU 使用率、内存使用率、磁盘使用率等）
3. 客户端通过 `HOST_METRIC` 消息将性能指标发送到服务端
4. 服务端解析 JSON 消息体，存入 host\_metric 表
5. 服务端每 30 秒更新主机的最后心跳时间
6. 服务端每 30 秒检测性能指标是否超过告警阈值，超阈值则创建告警并自动发送通知
7. 前端通过 HTTP 请求获取主机状态和历史指标数据
8. MySQL 定时事件每天凌晨 3 点清理 48 小时前的历史指标数据

### 进程监控流程

1. 客户端启动 ProcessReporter 定时任务，采集系统进程列表
2. 首次上报发送全量进程列表（`PROCESS_LIST_FULL`），后续发送增量变化（`PROCESS_LIST_DELTA`）
3. 增量数据包含序列号，服务端校验序列号，不匹配时请求客户端重发全量列表（`PROCESS_RESYNC`）
4. 服务端维护内存中的进程快照，合并增量数据后批量入库
5. 入库后触发进程告警检测：检查黑名单进程是否运行、白名单进程是否缺失
6. 前端按需刷新进程列表，展示每个进程的黑白名单标注

### 告警流程

1. 进程数据入库或性能指标上报时，触发告警检测
2. 检查对应告警类型的开关是否启用
3. 进程告警：匹配主机所属分组的黑白名单规则
4. 性能告警：比较指标值与配置的阈值
5. 去重检查：同一主机同一类型告警在设定间隔内不重复创建
6. 创建告警记录（alert\_record 表），自动发送系统通知给主机用户和组管理员
7. 前端告警中心展示告警列表，支持按类型/状态筛选和标记已处理

***

## 主机在线状态管理

- 客户端连接并发送 `HOST_INFO` 时，主机状态设为在线（status=1）
- 客户端断开连接时，服务端自动将主机状态设为离线（status=0）
- 服务端心跳检测线程每 30 秒检查一次，60 秒无数据则关闭连接并标记离线
- 服务端启动时，自动将所有在线主机重置为离线（防止服务重启后状态残留）
- 主机列表 API 支持按在线状态筛选

***
