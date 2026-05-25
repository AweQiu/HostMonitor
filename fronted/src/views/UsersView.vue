<script setup lang="ts">
import { ref } from 'vue'
import { PlusOutlined } from '@ant-design/icons-vue'

const columns = [
  { title: '用户名', dataIndex: 'username', key: 'username' },
  { title: '邮箱', dataIndex: 'email', key: 'email' },
  { title: '角色', dataIndex: 'role', key: 'role' },
  { title: '状态', dataIndex: 'status', key: 'status' },
  { title: '创建时间', dataIndex: 'createTime', key: 'createTime' },
  { title: '操作', key: 'action', width: 180 }
]

const userData = ref([
  { key: '1', username: 'admin', email: 'admin@hostmonitor.com', role: '超级管理员', status: 1, createTime: '2024-01-01 10:00:00' },
  { key: '2', username: 'operator', email: 'operator@hostmonitor.com', role: '组管理员', status: 1, createTime: '2024-03-15 14:30:00' },
  { key: '3', username: 'user01', email: 'user01@hostmonitor.com', role: '普通用户', status: 1, createTime: '2024-06-20 09:15:00' },
  { key: '4', username: 'user02', email: 'user02@hostmonitor.com', role: '普通用户', status: 0, createTime: '2024-08-10 16:45:00' }
])
</script>

<template>
  <div class="users-page">
    <div class="page-header">
      <span></span>
      <a-button type="primary">
        <PlusOutlined />
        添加用户
      </a-button>
    </div>

    <a-table
      :columns="columns"
      :data-source="userData"
      :pagination="{ pageSize: 10, showTotal: (total: number) => `共 ${total} 个用户` }"
    >
      <template #bodyCell="{ column, record }">
        <template v-if="column.key === 'role'">
          <a-tag :color="record.role === '超级管理员' ? 'red' : record.role === '组管理员' ? 'orange' : 'blue'">
            {{ record.role }}
          </a-tag>
        </template>
        <template v-if="column.key === 'status'">
          <a-badge :status="record.status === 1 ? 'success' : 'default'" :text="record.status === 1 ? '正常' : '禁用'" />
        </template>
        <template v-if="column.key === 'action'">
          <a-space>
            <a-button type="link" size="small">编辑</a-button>
            <a-button type="link" size="small">分配角色</a-button>
            <a-popconfirm title="确定删除该用户？" ok-text="确定" cancel-text="取消">
              <a-button type="link" size="small" danger>删除</a-button>
            </a-popconfirm>
          </a-space>
        </template>
      </template>
    </a-table>
  </div>
</template>

<style scoped>
.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
}
</style>
