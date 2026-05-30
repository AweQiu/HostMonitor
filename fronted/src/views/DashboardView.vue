<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import {
  DesktopOutlined,
  CheckCircleOutlined,
  WarningOutlined,
  CloseCircleOutlined,
  AlertOutlined
} from '@ant-design/icons-vue'
import request from '@/utils/request'

const router = useRouter()

const hostTotal = ref(0)
const hostOnline = ref(0)
const alertUnresolved = ref(0)
const hostOffline = ref(0)

const recentAlerts = ref<any[]>([])

async function loadStats() {
  try {
    const hostData = await request.get('/hosts', { params: { page: 1, size: 1 } })
    hostTotal.value = hostData.data?.total || 0

    const onlineData = await request.get('/hosts', { params: { page: 1, size: 1, status: 1 } })
    hostOnline.value = onlineData.data?.total || 0

    hostOffline.value = hostTotal.value - hostOnline.value

    const alertData = await request.get('/alerts/unresolved-count')
    alertUnresolved.value = alertData.data || 0

    const recentData = await request.get('/alerts', { params: { page: 1, size: 5, status: 0 } })
    recentAlerts.value = (recentData.data?.records || []).map((r: any) => ({
      ...r,
      createTime: r.createTime ? r.createTime.replace('T', ' ').substring(0, 19) : '-'
    }))
  } catch {
    // ignore
  }
}

const stats = ref([
  { title: '主机总数', value: 0, icon: DesktopOutlined, color: '#1890ff', bg: '#e6f7ff' },
  { title: '运行中', value: 0, icon: CheckCircleOutlined, color: '#52c41a', bg: '#f6ffed' },
  { title: '告警', value: 0, icon: WarningOutlined, color: '#faad14', bg: '#fffbe6' },
  { title: '离线', value: 0, icon: CloseCircleOutlined, color: '#ff4d4f', bg: '#fff2f0' }
])

function updateStats() {
  stats.value[0].value = hostTotal.value
  stats.value[1].value = hostOnline.value
  stats.value[2].value = alertUnresolved.value
  stats.value[3].value = hostOffline.value
}

onMounted(async () => {
  await loadStats()
  updateStats()
})
</script>

<template>
  <div class="dashboard">
    <a-row :gutter="[16, 16]">
      <a-col :xs="24" :sm="12" :lg="6" v-for="item in stats" :key="item.title">
        <a-card :bordered="false" class="stat-card" @click="item.title === '告警' && router.push('/alerts')" style="cursor: pointer">
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

    <a-card :bordered="false" style="margin-top: 16px">
      <template #title>
        <span><AlertOutlined style="margin-right: 8px; color: #faad14" />最近告警</span>
      </template>
      <template #extra>
        <a-button type="link" size="small" @click="router.push('/alerts')">查看全部</a-button>
      </template>
      <a-table
        v-if="recentAlerts.length > 0"
        :columns="[
          { title: '主机', dataIndex: 'hostname', key: 'hostname', width: 140 },
          { title: '告警类型', dataIndex: 'alertTypeLabel', key: 'alertTypeLabel', width: 140 },
          { title: '进程名', dataIndex: 'processName', key: 'processName', ellipsis: true },
          { title: '时间', dataIndex: 'createTime', key: 'createTime', width: 180 }
        ]"
        :data-source="recentAlerts"
        :pagination="false"
        size="small"
        row-key="id"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'alertTypeLabel'">
            <a-tag :color="record.alertType === 'BLACKLIST_RUNNING' ? 'red' : 'orange'">
              {{ record.alertTypeLabel }}
            </a-tag>
          </template>
        </template>
      </a-table>
      <a-empty v-else description="暂无告警" />
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
