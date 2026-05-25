<script setup lang="ts">
import { ref } from 'vue'
import {
  DesktopOutlined,
  CheckCircleOutlined,
  WarningOutlined,
  CloseCircleOutlined
} from '@ant-design/icons-vue'

const stats = ref([
  { title: '主机总数', value: 12, icon: DesktopOutlined, color: '#1890ff', bg: '#e6f7ff' },
  { title: '运行中', value: 8, icon: CheckCircleOutlined, color: '#52c41a', bg: '#f6ffed' },
  { title: '告警', value: 3, icon: WarningOutlined, color: '#faad14', bg: '#fffbe6' },
  { title: '离线', value: 1, icon: CloseCircleOutlined, color: '#ff4d4f', bg: '#fff2f0' }
])

const columns = [
  { title: '主机名', dataIndex: 'hostname', key: 'hostname' },
  { title: 'IP 地址', dataIndex: 'ip', key: 'ip' },
  { title: 'CPU 使用率', dataIndex: 'cpu', key: 'cpu' },
  { title: '内存使用率', dataIndex: 'memory', key: 'memory' },
  { title: '状态', dataIndex: 'status', key: 'status' }
]

const hostData = ref([
  { key: '1', hostname: 'server-01', ip: '192.168.1.101', cpu: '23%', memory: '45%', status: 'online' },
  { key: '2', hostname: 'server-02', ip: '192.168.1.102', cpu: '67%', memory: '78%', status: 'warning' },
  { key: '3', hostname: 'server-03', ip: '192.168.1.103', cpu: '12%', memory: '32%', status: 'online' },
  { key: '4', hostname: 'server-04', ip: '192.168.1.104', cpu: '-', memory: '-', status: 'offline' },
  { key: '5', hostname: 'server-05', ip: '192.168.1.105', cpu: '89%', memory: '91%', status: 'warning' }
])
</script>

<template>
  <div class="dashboard">
    <a-row :gutter="[16, 16]">
      <a-col :xs="24" :sm="12" :lg="6" v-for="item in stats" :key="item.title">
        <a-card :bordered="false" class="stat-card">
          <div class="stat-content">
            <div class="stat-info">
              <div class="stat-title">{{ item.title }}</div>
              <div class="stat-value">{{ item.value }}</div>
            </div>
            <div class="stat-icon" :style="{ background: item.bg }">
              <component :is="item.icon" :style="{ color: item.color, fontSize: '24px' }" />
            </div>
          </div>
        </a-card>
      </a-col>
    </a-row>

    <a-card title="主机概览" :bordered="false" style="margin-top: 16px">
      <a-table :columns="columns" :data-source="hostData" :pagination="false" size="middle">
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'cpu' || column.key === 'memory'">
            <a-progress
              :percent="record[column.key] === '-' ? 0 : parseInt(record[column.key])"
              :status="record[column.key] === '-' ? 'normal' : parseInt(record[column.key]) > 80 ? 'exception' : 'active'"
              size="small"
            />
          </template>
          <template v-if="column.key === 'status'">
            <a-badge
              :status="record.status === 'online' ? 'success' : record.status === 'warning' ? 'warning' : 'default'"
              :text="record.status === 'online' ? '在线' : record.status === 'warning' ? '告警' : '离线'"
            />
          </template>
        </template>
      </a-table>
    </a-card>
  </div>
</template>

<style scoped>
.stat-card {
  border-radius: 8px;
}

.stat-content {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.stat-title {
  font-size: 14px;
  color: #8c8c8c;
  margin-bottom: 8px;
}

.stat-value {
  font-size: 28px;
  font-weight: 600;
  color: #1a1a2e;
}

.stat-icon {
  width: 56px;
  height: 56px;
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
}
</style>
