# HostMonitor 数据库 ER 图

## 关系概览

```mermaid
erDiagram
    group {
        BIGINT id PK
        VARCHAR group_name UK
        VARCHAR description
        DATETIME create_time
        DATETIME update_time
        TINYINT is_del
    }

    user {
        BIGINT id PK
        VARCHAR username UK
        VARCHAR email UK
        VARCHAR password
        VARCHAR agent_key UK
        VARCHAR avatar
        TINYINT status
        BIGINT group_id FK
        DATETIME create_time
        DATETIME update_time
        TINYINT is_del
    }

    role {
        BIGINT id PK
        VARCHAR role_key UK
        VARCHAR role_name
        VARCHAR description
        DATETIME create_time
        DATETIME update_time
        TINYINT is_del
    }

    user_role {
        BIGINT id PK
        BIGINT user_id FK
        BIGINT role_id FK
        DATETIME create_time
        DATETIME update_time
        TINYINT is_del
    }

    permission {
        BIGINT id PK
        VARCHAR perm_key UK
        VARCHAR perm_name
        BIGINT parent_id FK
        TINYINT type
        INT sort_order
        DATETIME create_time
        DATETIME update_time
        TINYINT is_del
    }

    role_permission {
        BIGINT id PK
        BIGINT role_id FK
        BIGINT permission_id FK
        DATETIME create_time
        DATETIME update_time
        TINYINT is_del
    }

    host {
        BIGINT id PK
        BIGINT user_id FK_UK
        VARCHAR hostname
        VARCHAR ip_address
        VARCHAR cpu_model
        INT cpu_cores
        BIGINT memory_total
        BIGINT disk_total
        VARCHAR os_name
        VARCHAR os_version
        TINYINT status
        DATETIME last_heartbeat
        DATETIME create_time
        DATETIME update_time
        TINYINT is_del
    }

    host_metric {
        BIGINT id PK
        BIGINT host_id FK
        DECIMAL cpu_usage
        DECIMAL memory_usage
        DECIMAL disk_usage
        BIGINT memory_used
        BIGINT disk_used
        DATETIME collect_time
    }

    group ||--o{ user : "包含"
    user ||--o{ user_role : "拥有"
    role ||--o{ user_role : "分配给"
    role ||--o{ role_permission : "拥有"
    permission ||--o{ role_permission : "分配给"
    permission ||--o{ permission : "父子关系"
    user ||--|| host : "一对一绑定"
    host ||--o{ host_metric : "产生"
```

## 表结构说明

### 用户与组织

| 表名 | 说明 | 关键约束 |
|------|------|---------|
| `group` | 用户分组 | `group_name` 唯一 |
| `user` | 用户账号 | `username`、`email`、`agent_key` 唯一；`group_id` 外键关联 `group` |
| `host` | 主机信息 | `user_id` 唯一外键关联 `user`（一对一） |

### RBAC 权限模型

| 表名 | 说明 | 关键约束 |
|------|------|---------|
| `role` | 角色 | `role_key` 唯一 |
| `permission` | 权限 | `perm_key` 唯一；`parent_id` 自关联形成树形结构 |
| `user_role` | 用户-角色关联 | `(user_id, role_id)` 联合唯一 |
| `role_permission` | 角色-权限关联 | `(role_id, permission_id)` 联合唯一 |

### 监控数据

| 表名 | 说明 | 关键约束 |
|------|------|---------|
| `host_metric` | 主机性能指标 | `host_id` 外键关联 `host`；`collect_time` 索引 |

## 关系说明

- **user ↔ host**：一对一，一个用户只能绑定一台主机（`host.user_id` UNIQUE）
- **user ↔ group**：多对一，多个用户属于一个分组
- **user ↔ role**：多对多，通过 `user_role` 中间表关联
- **role ↔ permission**：多对多，通过 `role_permission` 中间表关联
- **permission ↔ permission**：自引用，`parent_id` 形成树形权限结构
- **host ↔ host_metric**：一对多，一台主机产生多条指标记录
