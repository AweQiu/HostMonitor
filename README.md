# HostMonitor - 主机监控系统

一个基于 Spring Boot + Vue 3 的主机监控系统，支持实时屏幕监控、系统指标采集、用户管理和组织管理功能。

***

## 技术栈

### 后端技术栈

Java 21 / Spring Boot 3.4.7 / Spring Security 6.4.x / MyBatis-Plus 3.5.5 / MySQL 8.0+ / JWT / WebSocket

### 前端技术栈

Vue 3.4.x / TypeScript 5.4.x / Vite 6.5.x / Pinia 2.1.x / Ant Design Vue 4.1.x

### 客户端技术栈

Java 21 / OSHI 6.5.x / Java AWT Robot / UDP/TCP Socket

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
CREATE DATABASE hostmonitor DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

1. **导入初始化脚本**

```bash
mysql -u username -p hostmonitor < sql/init.sql
```

1. **配置数据库连接**

修改 `backend/src/main/resources/application.properties`:

```properties
spring.datasource.url=jdbc:mysql://localhost:3306/hostmonitor?useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true
spring.datasource.username=root
spring.datasource.password=your_password
```

1. **启动服务**

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
┌─────────────────────┐     HTTP/WebSocket     ┌─────────────────────┐
│      前端 (Vue 3)   │ ──────────────────────> │      后端 (Spring)  │
│   http://localhost:5173 │                    │    http://localhost:8080 │
└─────────────────────┘                        └───────────┬─────────┘
                                                          │
                                                          │ TCP/UDP
                                                          ▼
┌─────────────────────┐     TCP/UDP Socket     ┌─────────────────────┐
│     客户端 (Java)   │ <─────────────────────> │      SocketServer   │
│   屏幕截图/指标采集  │                        │   认证/数据接收      │
└─────────────────────┘                        └─────────────────────┘
```

***

## 功能模块

### 1. 用户管理

- 用户注册（仅普通用户）
- 用户登录（JWT认证）
- 用户列表查询（分页、筛选）
- 用户编辑、删除、角色分配
- 权限控制：超管管理所有用户，组织管理员管理组织内用户

### 2. 组织管理

- 分组创建、编辑、删除
- 分组管理员指定与撤销
- 成员管理（添加/移除）
- 权限控制：管理员组（ID=1）不可删除，超级管理员不可移出分组

### 3. 主机管理

- 主机列表查询（分页、搜索）
- 主机状态监控（在线/离线）
- 主机删除（需管理员权限）
- 屏幕监控入口

### 4. 屏幕监控

- 实时屏幕推送（5fps）
- WebSocket 实时传输
- 按需截屏（无人观看时停止）
- 16:9 画面比例自适应

### 5. 系统指标采集

- CPU 使用率
- 内存使用量
- 磁盘使用量
- 网络流量
- 默认每 5 秒上报一次

***

## 监控流程

### 屏幕监控流程

1. 前端点击"监控"按钮，建立 WebSocket 连接
2. 后端检测到首个订阅者，通过 TCP 通知客户端开始截屏
3. 客户端使用 AWT Robot 截屏，JPEG 压缩后通过 UDP 发送
4. 后端接收 UDP 帧，通过 WebSocket 广播给所有订阅者
5. 前端收到帧数据，通过 Canvas 渲染显示
6. 前端关闭页面，WebSocket 断开
7. 后端检测到最后一个订阅者离开，通知客户端停止截屏

### 主机信息监控流程

1. 客户端启动后，通过 TCP Socket 连接到服务端
2. 客户端使用 Agent Key 进行认证
3. 认证成功后，客户端采集主机信息（CPU型号、核心数、内存大小、磁盘大小、操作系统信息等）
4. 客户端通过 TCP Socket 将主机信息发送到服务端
5. 服务端解析主机信息，更新或创建主机记录
6. 前端通过 HTTP 请求获取主机列表和详情

### 性能指标监控流程

1. 客户端启动 MetricReporter 定时任务（默认每 5 秒执行一次）
2. 客户端使用 OSHI 库采集系统性能指标（CPU使用率、内存使用率、磁盘使用率、网络流量等）
3. 客户端通过 TCP Socket 将性能指标发送到服务端
4. 服务端解析指标数据，存入 host\_metric 表
5. 服务端更新主机的最后心跳时间和在线状态
6. 前端通过 HTTP 请求获取主机状态和历史指标数据

***

