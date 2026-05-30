<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { message } from 'ant-design-vue'
import {
  ReloadOutlined,
  WarningOutlined,
  CheckCircleOutlined
} from '@ant-design/icons-vue'
import request from '@/utils/request'

interface AlertRecord {
  id: number
  hostId: number
  hostname: string
  alertType: string
  alertTypeLabel: string
  processName: string
  ruleId: number | null
  notificationId: number | null
  status: number
  statusLabel: string
  createTime: string
}

const alerts = ref<AlertRecord[]>([])
const loading = ref(false)
const pagination = ref({ current: 1, pageSize: 20, total: 0 })
const filterAlertType = ref<string | undefined>(undefined)
const filterStatus = ref<number | undefined>(undefined)

const columns = [
  { title: '主机', dataIndex: 'hostname', key: 'hostname', width: 140 },
  { title: '告警类型', dataIndex: 'alertTypeLabel', key: 'alertTypeLabel', width: 140 },
  { title: '进程名', dataIndex: 'processName', key: 'processName', width: 180, ellipsis: true },
  { title: '状态', dataIndex: 'statusLabel', key: 'statusLabel', width: 100, align: 'center' as const },
  { title: '时间', dataIndex: 'createTime', key: 'createTime', width: 180 },
  { title: '操作', key: 'action', width: 100, align: 'center' as const }
]

async function fetchAlerts() {
  loading.value = true
  try {
    const params: Record<string, any> = {
      page: pagination.value.current,
      size: pagination.value.pageSize
    }
    if (filterAlertType.value) params.alertType = filterAlertType.value
    if (filterStatus.value !== undefined) params.status = filterStatus.value

    const data = await request.get('/alerts', { params })
    alerts.value = data.data.records || []
    pagination.value.total = data.data.total || 0
  } catch {
    // handled by interceptor
  } finally {
    loading.value = false
  }
}

function handleTableChange(pag: any) {
  pagination.value.current = pag.current
  pagination.value.pageSize = pag.pageSize
  fetchAlerts()
}

async function handleResolve(record: AlertRecord) {
  try {
    await request.put(`/alerts/${record.id}/resolve`)
    message.success('告警已处理')
    fetchAlerts()
  } catch {
    // handled by interceptor
  }
}

function formatTime(time: string | null): string {
  if (!time) return '-'
  return time.replace('T', ' ').substring(0, 19)
}

onMounted(() => {
  fetchAlerts()
})
</script>

<template>
  <div class="alerts-page">
    <div class="alerts-toolbar">
      <a-space>
        <a-select
          v-model:value="filterAlertType"
          placeholder="告警类型"
          allow-clear
          style="width: 160px"
          @change="fetchAlerts"
        >
          <a-select-option value="BLACKLIST_RUNNING">黑名单进程运行</a-select-option>
          <a-select-option value="WHITELIST_MISSING">白名单进程缺失</a-select-option>
          <a-select-option value="CPU_HIGH">CPU使用率过高</a-select-option>
          <a-select-option value="MEMORY_HIGH">内存使用率过高</a-select-option>
          <a-select-option value="DISK_HIGH">磁盘使用率过高</a-select-option>
        </a-select>
        <a-select
          v-model:value="filterStatus"
          placeholder="处理状态"
          allow-clear
          style="width: 120px"
          @change="fetchAlerts"
        >
          <a-select-option :value="0">未处理</a-select-option>
          <a-select-option :value="1">已处理</a-select-option>
        </a-select>
      </a-space>
      <a-button @click="fetchAlerts" :loading="loading">
        <ReloadOutlined />
        刷新
      </a-button>
    </div>

    <a-table
      :columns="columns"
      :data-source="alerts"
      :loading="loading"
      :pagination="{
        current: pagination.current,
        pageSize: pagination.pageSize,
        total: pagination.total,
        showTotal: (t: number) => `共 ${t} 条`,
        showSizeChanger: true
      }"
      row-key="id"
      size="middle"
      @change="handleTableChange"
    >
      <template #bodyCell="{ column, record }">
        <template v-if="column.key === 'alertTypeLabel'">
          <a-tag :color="record.alertType === 'BLACKLIST_RUNNING' ? 'red' : record.alertType === 'WHITELIST_MISSING' ? 'orange' : 'volcano'">
            <WarningOutlined v-if="record.alertType === 'BLACKLIST_RUNNING' || record.alertType === 'CPU_HIGH'" />
            {{ record.alertTypeLabel }}
          </a-tag>
        </template>
        <template v-if="column.key === 'statusLabel'">
          <a-badge
            :status="record.status === 0 ? 'error' : 'success'"
            :text="record.status === 0 ? '未处理' : '已处理'"
          />
        </template>
        <template v-if="column.key === 'createTime'">
          {{ formatTime(record.createTime) }}
        </template>
        <template v-if="column.key === 'action'">
          <a-button
            v-if="record.status === 0"
            type="link"
            size="small"
            @click="handleResolve(record)"
          >
            <CheckCircleOutlined /> 处理
          </a-button>
          <span v-else style="color: #999">-</span>
        </template>
      </template>
    </a-table>
  </div>
</template>

<style scoped>
.alerts-page {
  height: 100%;
}

.alerts-toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
}
</style>
