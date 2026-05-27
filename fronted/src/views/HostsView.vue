<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { message } from 'ant-design-vue'
import { ReloadOutlined } from '@ant-design/icons-vue'
import request from '@/utils/request'
import { useUserStore } from '@/stores/user'

interface HostRecord {
  id: number
  hostname: string
  ipAddress: string
  cpuModel: string | null
  cpuCores: number | null
  memoryTotal: number | null
  diskTotal: number | null
  osName: string | null
  osVersion: string | null
  status: number
  lastHeartbeat: string | null
}

const userStore = useUserStore()
const router = useRouter()
const searchText = ref('')
const loading = ref(false)
const hostData = ref<HostRecord[]>([])
const total = ref(0)
const currentPage = ref(1)
const pageSize = ref(10)

const isAdmin = computed(() => {
  const roles = userStore.userInfo?.roles || []
  return roles.includes('SUPER_ADMIN') || roles.includes('GROUP_ADMIN')
})

const columns = [
  { title: '主机名', dataIndex: 'hostname', key: 'hostname', width: 140 },
  { title: 'IP 地址', dataIndex: 'ipAddress', key: 'ipAddress', width: 140 },
  { title: '操作系统', key: 'os', width: 180 },
  { title: 'CPU', key: 'cpu', width: 200, ellipsis: true },
  { title: '内存总量', dataIndex: 'memoryTotal', key: 'memoryTotal', width: 110, align: 'right' as const },
  { title: '硬盘空间', dataIndex: 'diskTotal', key: 'diskTotal', width: 110, align: 'right' as const },
  { title: '状态', dataIndex: 'status', key: 'status', width: 80, align: 'center' as const },
  { title: '操作', key: 'action', width: 180, fixed: 'right' as const }
]

function formatBytes(bytes: number | null): string {
  if (bytes === null || bytes === undefined) return '-'
  if (bytes === 0) return '0 B'
  const units = ['B', 'KB', 'MB', 'GB', 'TB']
  const i = Math.floor(Math.log(bytes) / Math.log(1024))
  return (bytes / Math.pow(1024, i)).toFixed(1) + ' ' + units[i]
}

async function fetchHosts() {
  loading.value = true
  try {
    const params: Record<string, string> = {
      page: String(currentPage.value),
      size: String(pageSize.value)
    }
    if (searchText.value) {
      params.keyword = searchText.value
    }
    const data = await request.get('/hosts', { params })
    hostData.value = data.data.records
    total.value = data.data.total
  } catch {
    // error handled by interceptor
  } finally {
    loading.value = false
  }
}

function handleSearch() {
  currentPage.value = 1
  fetchHosts()
}

function handleTableChange(pagination: { current: number; pageSize: number }) {
  currentPage.value = pagination.current
  pageSize.value = pagination.pageSize
  fetchHosts()
}

function handleRefresh() {
  fetchHosts()
}

async function handleDelete(id: number) {
  try {
    await request.delete(`/hosts/${id}`)
    message.success('删除成功')
    fetchHosts()
  } catch {
    // error handled by interceptor
  }
}

function handleScreen(record: HostRecord) {
  router.push({
    name: 'screen',
    params: { hostId: record.id },
    query: { name: record.hostname }
  })
}

function handleDetail(record: HostRecord) {
  router.push({
    name: 'hostDetail',
    params: { hostId: record.id },
    query: { name: record.hostname }
  })
}

onMounted(() => {
  fetchHosts()
})
</script>

<template>
  <div class="hosts-page">
    <div class="page-header">
      <a-input-search
        v-model:value="searchText"
        placeholder="搜索主机名或 IP 地址"
        style="width: 300px"
        @search="handleSearch"
        allow-clear
      />
      <a-button @click="handleRefresh" :loading="loading">
        <ReloadOutlined />
        刷新
      </a-button>
    </div>

    <a-table
      :columns="columns"
      :data-source="hostData"
      :loading="loading"
      :pagination="{
        current: currentPage,
        pageSize: pageSize,
        total: total,
        showTotal: (t: number) => `共 ${t} 台主机`,
        showSizeChanger: true
      }"
      row-key="id"
      :scroll="{ x: 1200 }"
      @change="handleTableChange"
    >
      <template #bodyCell="{ column, record }">
        <template v-if="column.key === 'cpu'">
          {{ record.cpuModel || '-' }}{{ record.cpuCores ? ' ' + record.cpuCores + '核' : '' }}
        </template>
        <template v-if="column.key === 'memoryTotal'">
          {{ formatBytes(record.memoryTotal) }}
        </template>
        <template v-if="column.key === 'diskTotal'">
          {{ formatBytes(record.diskTotal) }}
        </template>
        <template v-if="column.key === 'os'">
          {{ record.osName || '-' }}{{ record.osVersion ? ' ' + record.osVersion : '' }}
        </template>
        <template v-if="column.key === 'status'">
          <a-badge
            :status="record.status === 1 ? 'success' : 'default'"
            :text="record.status === 1 ? '在线' : '离线'"
          />
        </template>
        <template v-if="column.key === 'action'">
          <a-space>
            <a-button type="link" size="small" @click="handleDetail(record)">详情</a-button>
            <a-button type="link" size="small" @click="handleScreen(record)">监控</a-button>
            <a-popconfirm v-if="isAdmin" title="确定删除该主机？" ok-text="确定" cancel-text="取消" @confirm="handleDelete(record.id)">
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
