# HostMonitor - 主机监控系统

一个基于 Spring Boot + Vue 3 的主机监控系统，支持实时屏幕监控、系统指标采集、用户管理、组织管理和通知功能。

***

## 技术栈

### 后端技术栈

Java 21 / Spring Boot 3.4.7 / Spring Security 6.4.x / MyBatis-Plus 3.5.9 / MySQL 8.0+ / JWT (jjwt 0.12.6) / WebSocket / Lombok

### 前端技术栈

Vue 3.5.x / TypeScript 6.0.x / Vite 8.0.x / Pinia 3.0.x / Ant Design Vue 4.2.x / Vue Router 5.0.x / Axios

### 客户端技术栈

Java 21 / OSHI 6.6.5 / Java AWT Robot / UDP/TCP Socket / Lombok

***

## 部署环境

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

- HTTP: `8080`
- TCP Socket: `12345`
- UDP Screen: `12346`

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
socket.host=127.0.0.1
socket.port=12345
agent.key=your_agent_key_here
screen.enabled=true
screen.udp.port=12346
screen.fps=5
screen.jpeg.quality=0.5
metric.interval.ms=5000
```

运行客户端：

```bash
cd monitor-client
mvn compile exec:java
```

***

## 系统架构

```
┌─────────────────────┐     HTTP/REST API      ┌─────────────────────┐
│      前端 (Vue 3)   │ ──────────────────────> │      后端 (Spring)  │
│   localhost:5173    │ <────────────────────── │    localhost:8080   │
│                     │     WebSocket / SSE     │                     │
└─────────────────────┘                        └───────────┬─────────┘
                                                           │
                                                           │ TCP (12345) 认证/指令/指标
                                                           │ UDP (12346) 屏幕帧
                                                           ▼
                                               ┌─────────────────────┐
                                               │   客户端 (Java 21)  │
                                               │  屏幕截图 / 指标采集 │
                                               └─────────────────────┘
```

通信协议说明：

- **HTTP REST API**：前端与后端之间的常规数据交互（用户管理、主机列表、通知等）
- **WebSocket**：前端订阅屏幕监控实时画面，后端推送 JPEG 帧数据
- **TCP Socket**：客户端与后端之间的长连接，用于 Agent Key 认证、主机信息上报、性能指标上报、指令下发
- **UDP Socket**：客户端向后端发送屏幕截图帧数据，低延迟传输

***

## 权限模型

系统采用 RBAC（基于角色的访问控制）模型，定义三种角色：

| 角色 | 角色标识 | 说明 |
|------|---------|------|
| 超级管理员 | SUPER_ADMIN | 拥有系统所有权限，不属于任何分组，可管理所有用户、分组、主机 |
| 组管理员 | GROUP_ADMIN | 管理本组用户和主机，可向组内发送通知 |
| 普通用户 | USER | 查看仪表盘和性能指标 |

### 权限规则

- 超级管理员不属于任何分组（group_id 为 NULL），不可被加入分组、不可被删除、不可被降级
- 每个分组最多只能有一个组管理员
- 组管理员只能管理本组内的用户和主机
- 组管理员被移出分组时自动降级为普通用户
- 超级管理员角色不可被创建或分配，仅通过系统初始化产生

### 菜单权限

| 菜单 | 超级管理员 | 组管理员 | 普通用户 |
|------|:---------:|:-------:|:-------:|
| 仪表盘 | ✅ | ✅ | ✅ |
| 主机管理 | ✅ | ✅ | ✅ |
| 用户管理 | ✅ | ✅（本组） | ❌ |
| 组织管理 | ✅ | ❌ | ❌ |
| 通知管理 | ✅ | ✅（本组） | ✅（接收） |
| 系统设置 | ✅ | ❌ | ❌ |

***

## 功能模块

### 1. 用户管理

- 用户注册（仅注册为普通用户）
- 用户登录（JWT 认证）
- 用户列表查询（分页、按角色筛选、按分组筛选、关键词搜索）
- 用户编辑（邮箱、密码、状态、所属分组、角色分配）
- Agent Key 管理（脱敏展示、一键复制、重置并断开客户端连接）
- 用户删除（不可删除超级管理员）
- 权限控制：超管管理所有用户，组管理员仅管理本组用户

### 2. 组织管理

- 分组创建、编辑、删除
- 分组管理员指定与撤销（每组仅一名管理员）
- 成员管理（添加/移除，超级管理员不可加入分组）
- 删除分组时自动移出所有成员并撤销管理员身份

### 3. 主机管理

- 主机列表查询（分页、搜索）
- 主机状态监控（在线/离线）
- 主机删除（需管理员权限）
- 屏幕监控入口
- 权限控制：超管查看所有主机，组管理员仅查看本组主机

### 4. 屏幕监控

- 实时屏幕推送（默认 5fps）
- WebSocket 实时传输，Canvas 双缓冲渲染
- 按需截屏（无人观看时客户端自动停止截屏）
- 16:9 画面比例自适应
- 连接状态管理（等待连接/接收数据/等待数据/超时）

### 5. 系统指标采集

- CPU 使用率
- 内存使用量/使用率
- 磁盘使用量/使用率
- 默认每 5 秒上报一次

### 6. 通知管理

- 超级管理员可发送全体通知、指定分组通知、指定用户通知
- 组管理员可向本组发送通知
- 通知已读/未读状态管理
- 顶部铃铛实时显示未读数量
- 通知列表分页查看

***

## 认证机制

### 用户认证（JWT）

1. 用户通过 `/api/auth/login` 登录，后端验证用户名密码
2. 验证成功后生成 JWT Token 返回前端
3. 前端在后续请求的 `Authorization: Bearer <token>` 头中携带 Token
4. Spring Security 过滤链校验 Token 有效性

### 客户端认证（Agent Key）

1. 用户注册时系统自动生成 UUID 格式的 Agent Key，存入 user 表
2. 客户端启动时使用配置的 Agent Key 连接服务端 TCP Socket（端口 12345）
3. 服务端验证 Agent Key 对应的用户，建立用户-主机一对一绑定
4. 同一 Agent Key 仅允许一个客户端连接，重复连接将被拒绝
5. 超级管理员可在用户管理界面重置 Agent Key，重置后自动断开该用户的客户端连接

***

## 监控流程

### 客户端连接与主机注册流程

1. 用户注册后，系统自动生成 Agent Key 并存入 user 表
2. 用户将 Agent Key 配置到客户端的 `application.properties`
3. 客户端启动后，通过 TCP Socket 连接服务端（端口 12345）
4. 客户端发送 Agent Key 进行认证
5. 服务端验证 Agent Key，查找对应用户
6. 认证成功后，客户端采集主机信息（CPU 型号、核心数、内存、磁盘、操作系统等）
7. 客户端通过 TCP 上报主机信息，服务端创建或更新 host 表记录（user_id 关联）
8. 连接建立后，客户端定期上报性能指标和心跳

### 屏幕监控流程

1. 前端点击"监控"按钮，建立 WebSocket 连接
2. 后端检测到首个订阅者，通过 TCP 通知客户端开始截屏
3. 客户端使用 AWT Robot 截屏，JPEG 压缩后通过 UDP 发送（端口 12346）
4. 后端接收 UDP 帧，通过 WebSocket 广播给所有订阅者
5. 前端收到帧数据，通过 Canvas 双缓冲渲染显示
6. 前端关闭页面，WebSocket 断开
7. 后端检测到最后一个订阅者离开，通知客户端停止截屏

### 性能指标监控流程

1. 客户端启动 MetricReporter 定时任务（默认每 5 秒执行一次）
2. 客户端使用 OSHI 库采集系统性能指标（CPU 使用率、内存使用率、磁盘使用率等）
3. 客户端通过 TCP Socket 将性能指标发送到服务端
4. 服务端解析指标数据，存入 host\_metric 表
5. 服务端更新主机的最后心跳时间和在线状态
6. 前端通过 HTTP 请求获取主机状态和历史指标数据

***

## 数据库设计

系统共 9 张表，采用 RBAC 权限模型：

| 表名 | 说明 |
|------|------|
| `user` | 用户表（id, username, email, password, agent\_key, avatar, status, group\_id） |
| `role` | 角色表（id, role\_key, role\_name, description） |
| `user_role` | 用户-角色关联表（user\_id, role\_id） |
| `permission` | 权限表（id, perm\_key, perm\_name, parent\_id, type, sort\_order） |
| `role_permission` | 角色-权限关联表（role\_id, permission\_id） |
| `group` | 分组表（id, group\_name, description） |
| `host` | 主机信息表（id, user\_id, hostname, ip\_address, cpu\_model, cpu\_cores, memory\_total, disk\_total, os\_name, os\_version, status, last\_heartbeat） |
| `host_metric` | 性能指标表（id, host\_id, cpu\_usage, memory\_usage, disk\_usage, memory\_used, disk\_used, collect\_time） |
| `notification` | 通知表（id, title, content, sender\_id, send\_type, target\_group\_id, target\_user\_ids, status） |
| `user_notification` | 用户通知关联表（user\_id, notification\_id, is\_read, read\_time） |

关键设计：

- 用户与分组为多对一关系（user.group\_id → group.id），超级管理员 group\_id 为 NULL
- 用户与主机为一对一关系（host.user\_id → user.id），通过 Agent Key 认证绑定
- 用户与角色为多对多关系（通过 user\_role 关联表）
- 通知支持三种发送类型：全体（send\_type=1）、分组（send\_type=2）、指定用户（send\_type=3）
