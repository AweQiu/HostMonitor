<script setup lang="ts">
import { ref } from 'vue'
import { SearchOutlined, PlusOutlined, ReloadOutlined } from '@ant-design/icons-vue'

const searchText = ref('')
const loading = ref(false)

const columns = [
  { title: '主机名', dataIndex: 'hostname', key: 'hostname' },
  { title: 'IP 地址', dataIndex: 'ip', key: 'ip' },
  { title: '操作系统', dataIndex: 'os', key: 'os' },
  { title: 'CPU 使用率', dataIndex: 'cpu', key: 'cpu' },
  { title: '内存使用率', dataIndex: 'memory', key: 'memory' },
  { title: '磁盘使用率', dataIndex: 'disk', key: 'disk' },
  { title: '状态', dataIndex: 'status', key: 'status' },
  { title: '操作', key: 'action', width: 180 }
]

const hostData = ref([
  { key: '1', hostname: 'server-01', ip: '192.168.1.101', os: 'CentOS 7.9', cpu: 23, memory: 45, disk: 56, status: 'online' },
  { key: '2', hostname: 'server-02', ip: '192.168.1.102', os: 'Ubuntu 22.04', cpu: 67, memory: 78, disk: 82, status: 'warning' },
  { key: '3', hostname: 'server-03', ip: '192.168.1.103', os: 'Debian 12', cpu: 12, memory: 32, disk: 41, status: 'online' },
  { key: '4', hostname: 'server-04', ip: '192.168.1.104', os: 'Windows Server 2022', cpu: 0, memory: 0, disk: 0, status: 'offline' },
  { key: '5', hostname: 'server-05', ip: '192.168.1.105', os: 'CentOS 8', cpu: 89, memory: 91, disk: 73, status: 'warning' },
  { key: '6', hostname: 'server-06', ip: '192.168.1.106', os: 'Ubuntu 20.04', cpu: 34, memory: 52, disk: 38, status: 'online' }
])

function handleRefresh() {
  loading.value = true
  setTimeout(() => {
    loading.value = false
  }, 1000)
}
</script>

<template>
  <div class="hosts-page">
    <div class="page-header">
      <a-input-search
        v-model:value="searchText"
        placeholder="搜索主机名或 IP 地址"
        style="width: 300px"
      >
        <template #prefix>
          <SearchOutlined style="color: #bfbfbf" />
        </template>
      </a-input-search>
      <a-space>
        <a-button @click="handleRefresh" :loading="loading">
          <ReloadOutlined />
          刷新
        </a-button>
        <a-button type="primary">
          <PlusOutlined />
          添加主机
        </a-button>
      </a-space>
    </div>

    <a-table
      :columns="columns"
      :data-source="hostData"
      :loading="loading"
      :pagination="{ pageSize: 10, showTotal: (total: number) => `共 ${total} 台主机` }"
    >
      <template #bodyCell="{ column, record }">
        <template v-if="column.key === 'cpu' || column.key === 'memory' || column.key === 'disk'">
          <a-progress
            :percent="record[column.key]"
            :status="record[column.key] > 80 ? 'exception' : 'active'"
            size="small"
          />
        </template>
        <template v-if="column.key === 'status'">
          <a-badge
            :status="record.status === 'online' ? 'success' : record.status === 'warning' ? 'warning' : 'default'"
            :text="record.status === 'online' ? '在线' : record.status === 'warning' ? '告警' : '离线'"
          />
        </template>
        <template v-if="column.key === 'action'">
          <a-space>
            <a-button type="link" size="small">详情</a-button>
            <a-button type="link" size="small">监控</a-button>
            <a-popconfirm title="确定删除该主机？" ok-text="确定" cancel-text="取消">
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
