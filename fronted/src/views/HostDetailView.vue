<script setup lang="ts">
import { ref, computed, onMounted, onBeforeUnmount, nextTick, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { message } from 'ant-design-vue'
import {
  ArrowLeftOutlined,
  DesktopOutlined,
  MonitorOutlined,
  DeleteOutlined,
  CheckCircleOutlined,
  CloseCircleOutlined,
  ReloadOutlined
} from '@ant-design/icons-vue'
import * as echarts from 'echarts'
import request from '@/utils/request'
import { useUserStore } from '@/stores/user'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const hostId = computed(() => route.params.hostId as string)

const isAdmin = computed(() => {
  const roles = userStore.userInfo?.roles || []
  return roles.includes('SUPER_ADMIN') || roles.includes('GROUP_ADMIN')
})

interface HostInfo {
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
  userId: number
  username: string | null
  groupId: number | null
  groupName: string | null
  createTime: string
}

interface MetricInfo {
  cpuUsage: number | null
  memoryUsage: number | null
  diskUsage: number | null
  memoryUsed: number | null
  diskUsed: number | null
  collectTime: string | null
}

interface MetricHistory {
  collectTime: string
  cpuUsage: number
  memoryUsage: number
  diskUsage: number
}

interface ProcessRecord {
  pid: number
  name: string
  user: string
  cpuUsage: number
  memoryUsage: number
  memoryUsed: number
  status: string
  commandLine: string | null
  startTime: string | null
  isWhitelisted: boolean | null
  isBlacklisted: boolean | null
}

interface ProcessRule {
  id: number
  groupId: number
  processName: string
  ruleType: 'WHITELIST' | 'BLACKLIST'
  description: string | null
  createTime: string
}

const hostInfo = ref<HostInfo | null>(null)
const metric = ref<MetricInfo | null>(null)
const processList = ref<ProcessRecord[]>([])
const processRules = ref<ProcessRule[]>([])
const loading = ref(false)
const processLoading = ref(false)
const activeTab = ref('overview')
const metricRange = ref('1h')
const processSearch = ref('')

const filteredProcessList = computed(() => {
  if (!processSearch.value) return processList.value
  const keyword = processSearch.value.toLowerCase()
  return processList.value.filter(p =>
    p.name.toLowerCase().includes(keyword) ||
    String(p.pid).includes(keyword)
  )
})

const chartRef = ref<HTMLDivElement | null>(null)
let chartInstance: echarts.ECharts | null = null

let refreshTimer: ReturnType<typeof setInterval> | null = null

const metricQueue = ref<MetricHistory[]>([])
let lastMetricTime: string | null = null
let chartInitialized = false

const rangeOptions = [
  { label: '1 小时', value: '1h' },
  { label: '6 小时', value: '6h' },
  { label: '24 小时', value: '24h' },
  { label: '7 天', value: '7d' }
]

const rangeMs: Record<string, number> = {
  '1h': 3600_000,
  '6h': 6 * 3600_000,
  '24h': 24 * 3600_000,
  '7d': 7 * 24 * 3600_000
}

function formatBytes(bytes: number | null): string {
  if (bytes === null || bytes === undefined) return '-'
  if (bytes === 0) return '0 B'
  const units = ['B', 'KB', 'MB', 'GB', 'TB']
  const i = Math.floor(Math.log(bytes) / Math.log(1024))
  return (bytes / Math.pow(1024, i)).toFixed(1) + ' ' + units[i]
}

function timeAgo(dateStr: string | null): string {
  if (!dateStr) return '-'
  const diff = Date.now() - new Date(dateStr).getTime()
  const seconds = Math.floor(diff / 1000)
  if (seconds < 60) return `${seconds} 秒前`
  const minutes = Math.floor(seconds / 60)
  if (minutes < 60) return `${minutes} 分钟前`
  const hours = Math.floor(minutes / 60)
  if (hours < 24) return `${hours} 小时前`
  return `${Math.floor(hours / 24)} 天前`
}

async function fetchHostDetail() {
  loading.value = true
  try {
    const data = await request.get(`/hosts/${hostId.value}`)
    hostInfo.value = data.data
  } catch {
    // error handled by interceptor
  } finally {
    loading.value = false
  }
}

async function fetchMetric() {
  try {
    const data = await request.get(`/hosts/${hostId.value}/metrics/latest`)
    metric.value = data.data
    if (chartInitialized && data.data?.collectTime) {
      appendToQueue(data.data)
    }
  } catch (e) {
    console.error('[HostDetail] fetchMetric error:', e)
  }
}

async function fetchMetricHistory() {
  try {
    const data = await request.get(`/hosts/${hostId.value}/metrics/history`, {
      params: { range: metricRange.value }
    })
    metricQueue.value = data.data || []
    if (metricQueue.value.length > 0) {
      lastMetricTime = metricQueue.value[metricQueue.value.length - 1]?.collectTime ?? null
    }
    renderChart()
  } catch {
    // ignore
  }
}

function appendToQueue(m: MetricInfo) {
  if (!m.collectTime) return
  if (lastMetricTime && new Date(m.collectTime).getTime() <= new Date(lastMetricTime).getTime()) return

  const gapMs = lastMetricTime
    ? new Date(m.collectTime).getTime() - new Date(lastMetricTime).getTime()
    : 0
  if (gapMs > 60000) {
    fetchMetricHistory()
    return
  }

  metricQueue.value.push({
    collectTime: m.collectTime,
    cpuUsage: m.cpuUsage ?? 0,
    memoryUsage: m.memoryUsage ?? 0,
    diskUsage: m.diskUsage ?? 0
  })
  lastMetricTime = m.collectTime
  trimQueue()
  renderChart()
}

function trimQueue() {
  const maxMs = rangeMs[metricRange.value] || rangeMs['1h']
  const cutoff = Date.now() - (maxMs ?? rangeMs['1h'] ?? 3600_000)
  while (metricQueue.value.length > 0 && metricQueue.value[0]?.collectTime && new Date(metricQueue.value[0].collectTime).getTime() < cutoff) {
    metricQueue.value.shift()
  }
}

async function fetchProcesses() {
  processLoading.value = true
  try {
    const data = await request.get(`/hosts/${hostId.value}/processes`)
    processList.value = data.data
  } catch {
    // error handled by interceptor
  } finally {
    processLoading.value = false
  }
}

async function fetchProcessRules() {
  if (!hostInfo.value?.groupId) return
  try {
    const data = await request.get(`/groups/${hostInfo.value.groupId}/process-rules`)
    processRules.value = data.data
  } catch {
    // ignore
  }
}

function initChart() {
  if (!chartRef.value) return
  chartInstance = echarts.init(chartRef.value)
  chartInstance.setOption({
    tooltip: {
      trigger: 'axis',
      backgroundColor: 'rgba(255,255,255,0.95)',
      borderColor: '#eee',
      borderWidth: 1,
      textStyle: { color: '#333', fontSize: 12 },
      formatter(params: any) {
        const time = params[0]?.axisValue || ''
        let html = `<div style="font-weight:600;margin-bottom:4px">${time}</div>`
        params.forEach((p: any) => {
          html += `<div style="display:flex;align-items:center;gap:6px">
            <span style="display:inline-block;width:8px;height:8px;border-radius:50%;background:${p.color}"></span>
            ${p.seriesName}: <b>${p.value}%</b>
          </div>`
        })
        return html
      }
    },
    legend: {
      data: ['CPU 使用率', '内存使用率', '硬盘使用率'],
      bottom: 0,
      itemWidth: 16,
      itemHeight: 8,
      textStyle: { fontSize: 12, color: '#666' }
    },
    grid: {
      top: 16,
      left: 48,
      right: 24,
      bottom: 36
    },
    xAxis: {
      type: 'category',
      boundaryGap: false,
      data: [],
      axisLine: { lineStyle: { color: '#ddd' } },
      axisTick: { show: false },
      axisLabel: { color: '#999', fontSize: 11, formatter: (val: string) => val.substring(val.length - 5) }
    },
    yAxis: {
      type: 'value',
      min: 0,
      max: 100,
      axisLabel: { color: '#999', fontSize: 11, formatter: '{value}%' },
      splitLine: { lineStyle: { color: '#f5f5f5' } },
      axisLine: { show: false },
      axisTick: { show: false }
    },
    series: [
      {
        name: 'CPU 使用率',
        type: 'line',
        smooth: true,
        symbol: 'none',
        lineStyle: { width: 2, color: '#1890ff' },
        areaStyle: { color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
          { offset: 0, color: 'rgba(24,144,255,0.25)' },
          { offset: 1, color: 'rgba(24,144,255,0.02)' }
        ]) },
        data: []
      },
      {
        name: '内存使用率',
        type: 'line',
        smooth: true,
        symbol: 'none',
        lineStyle: { width: 2, color: '#52c41a' },
        areaStyle: { color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
          { offset: 0, color: 'rgba(82,196,26,0.25)' },
          { offset: 1, color: 'rgba(82,196,26,0.02)' }
        ]) },
        data: []
      },
      {
        name: '硬盘使用率',
        type: 'line',
        smooth: true,
        symbol: 'none',
        lineStyle: { width: 2, color: '#faad14' },
        areaStyle: { color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
          { offset: 0, color: 'rgba(250,173,20,0.25)' },
          { offset: 1, color: 'rgba(250,173,20,0.02)' }
        ]) },
        data: []
      }
    ]
  })
  chartInitialized = true
  fetchMetricHistory()
}

function renderChart() {
  if (!chartInstance) return
  const q = metricQueue.value
  const times = q.map(m => m.collectTime.replace('T', ' ').substring(0, 16))
  const cpuData = q.map(m => m.cpuUsage)
  const memData = q.map(m => m.memoryUsage)
  const diskData = q.map(m => m.diskUsage)
  chartInstance.setOption({
    xAxis: { data: times },
    series: [
      { data: cpuData },
      { data: memData },
      { data: diskData }
    ]
  })
}

function handleResize() {
  chartInstance?.resize()
}

watch(metricRange, (newRange, oldRange) => {
  const newMs = rangeMs[newRange] || rangeMs['1h']
  const oldMs = rangeMs[oldRange] || rangeMs['1h']
  if (newMs! > oldMs!) {
    fetchMetricHistory()
  } else {
    trimQueue()
    renderChart()
  }
})

function handleRefresh() {
  fetchHostDetail()
  fetchMetric()
  fetchMetricHistory()
  if (activeTab.value === 'process') {
    fetchProcesses()
  }
  if (activeTab.value === 'rules') {
    fetchProcessRules()
  }
}

async function handleDeleteHost() {
  try {
    await request.delete(`/hosts/${hostId.value}`)
    message.success('主机已删除')
    router.push('/hosts')
  } catch {
    // error handled by interceptor
  }
}

function handleScreen() {
  router.push({
    name: 'screen',
    params: { hostId: hostId.value },
    query: { name: hostInfo.value?.hostname }
  })
}

function goBack() {
  router.push('/hosts')
}

const processColumns = [
  { title: 'PID', dataIndex: 'pid', key: 'pid', width: 80, sorter: (a: ProcessRecord, b: ProcessRecord) => a.pid - b.pid },
  { title: '进程名', dataIndex: 'name', key: 'name', width: 180, ellipsis: true },
  { title: '用户', dataIndex: 'user', key: 'user', width: 100 },
  { title: 'CPU %', dataIndex: 'cpuUsage', key: 'cpuUsage', width: 100, align: 'right' as const, sorter: (a: ProcessRecord, b: ProcessRecord) => a.cpuUsage - b.cpuUsage, defaultSortOrder: 'descend' as const },
  { title: '内存 %', dataIndex: 'memoryUsage', key: 'memoryUsage', width: 100, align: 'right' as const, sorter: (a: ProcessRecord, b: ProcessRecord) => a.memoryUsage - b.memoryUsage },
  { title: '内存', dataIndex: 'memoryUsed', key: 'memoryUsed', width: 100, align: 'right' as const },
  { title: '状态', dataIndex: 'status', key: 'status', width: 80 },
  { title: '规则', key: 'rule', width: 100, align: 'center' as const }
]

const ruleColumns = [
  { title: '进程名', dataIndex: 'processName', key: 'processName' },
  { title: '规则类型', dataIndex: 'ruleType', key: 'ruleType', width: 120 },
  { title: '说明', dataIndex: 'description', key: 'description', ellipsis: true },
  { title: '创建时间', dataIndex: 'createTime', key: 'createTime', width: 180 }
]

const cpuPercent = computed(() => {
  const v = metric.value?.cpuUsage
  return v != null ? Number(Number(v).toFixed(1)) : 0
})

const memPercent = computed(() => {
  const v = metric.value?.memoryUsage
  return v != null ? Number(Number(v).toFixed(1)) : 0
})

const diskPercent = computed(() => {
  const v = metric.value?.diskUsage
  return v != null ? Number(Number(v).toFixed(1)) : 0
})

function formatPercent(percent: number | undefined) {
  const v = percent ?? 0
  return `${v.toFixed(1)}%`
}

function getUsageColor(usage: number | null): string {
  if (usage === null) return '#1890ff'
  if (usage >= 90) return '#ff4d4f'
  if (usage >= 70) return '#faad14'
  return '#52c41a'
}

function getProcessRuleTag(record: ProcessRecord) {
  if (record.isBlacklisted) return { text: '黑名单', color: 'red' }
  if (record.isWhitelisted) return { text: '白名单', color: 'green' }
  return null
}

function handleVisibilityChange() {
  if (document.visibilityState === 'visible' && chartInitialized) {
    fetchMetricHistory()
  }
}

onMounted(async () => {
  await fetchHostDetail()
  fetchMetric()
  fetchProcesses()
  await nextTick()
  initChart()
  window.addEventListener('resize', handleResize)
  document.addEventListener('visibilitychange', handleVisibilityChange)
  refreshTimer = setInterval(() => {
    fetchMetric()
  }, 10000)
})

onBeforeUnmount(() => {
  if (refreshTimer) {
    clearInterval(refreshTimer)
    refreshTimer = null
  }
  document.removeEventListener('visibilitychange', handleVisibilityChange)
  window.removeEventListener('resize', handleResize)
  if (chartInstance) {
    chartInstance.dispose()
    chartInstance = null
  }
  chartInitialized = false
})
</script>

<template>
  <div class="host-detail" v-if="hostInfo">
    <div class="detail-header">
      <div class="header-left">
        <a-button @click="goBack">
          <ArrowLeftOutlined />
          返回
        </a-button>
        <div class="host-title">
          <DesktopOutlined style="font-size: 20px; color: #1890ff" />
          <h2>{{ hostInfo.hostname }}</h2>
          <a-badge
            :status="hostInfo.status === 1 ? 'success' : 'default'"
            :text="hostInfo.status === 1 ? '在线' : '离线'"
          />
        </div>
        <span class="host-ip">{{ hostInfo.ipAddress }}</span>
      </div>
      <div class="header-right">
        <a-button @click="handleRefresh" :loading="loading">
          <ReloadOutlined />
          刷新
        </a-button>
        <a-button @click="handleScreen">
          <MonitorOutlined />
          屏幕监控
        </a-button>
        <a-popconfirm v-if="isAdmin" title="确定删除该主机？" ok-text="确定" cancel-text="取消" @confirm="handleDeleteHost">
          <a-button danger>
            <DeleteOutlined />
            删除
          </a-button>
        </a-popconfirm>
      </div>
    </div>

    <a-tabs v-model:activeKey="activeTab" class="detail-tabs" @change="(key: string) => { if (key === 'rules') fetchProcessRules() }">
      <a-tab-pane key="overview" tab="概览">
        <a-row :gutter="[24, 24]">
          <a-col :span="24" :lg="14">
            <a-card title="性能趋势" :bordered="false" class="section-card">
              <template #extra>
                <a-radio-group v-model:value="metricRange" size="small" button-style="solid">
                  <a-radio-button v-for="opt in rangeOptions" :key="opt.value" :value="opt.value">
                    {{ opt.label }}
                  </a-radio-button>
                </a-radio-group>
              </template>
              <div ref="chartRef" class="metric-chart"></div>
            </a-card>
          </a-col>

          <a-col :span="24" :lg="10">
            <a-card title="硬件信息" :bordered="false" class="section-card">
              <a-descriptions :column="1" size="small" bordered>
                <a-descriptions-item label="CPU 型号">{{ hostInfo.cpuModel || '-' }}</a-descriptions-item>
                <a-descriptions-item label="CPU 核心">{{ hostInfo.cpuCores ? hostInfo.cpuCores + ' 核' : '-' }}</a-descriptions-item>
                <a-descriptions-item label="内存总量">{{ formatBytes(hostInfo.memoryTotal) }}</a-descriptions-item>
                <a-descriptions-item label="硬盘总量">{{ formatBytes(hostInfo.diskTotal) }}</a-descriptions-item>
                <a-descriptions-item label="操作系统">{{ (hostInfo.osName || '-') + (hostInfo.osVersion ? ' ' + hostInfo.osVersion : '') }}</a-descriptions-item>
              </a-descriptions>
            </a-card>
          </a-col>

          <a-col :span="24" :lg="14">
            <a-card title="实时性能" :bordered="false" class="section-card">
              <a-row :gutter="[24, 20]">
                <a-col :span="8">
                  <div class="gauge-item">
                    <a-progress
                      type="dashboard"
                      :percent="cpuPercent"
                      :stroke-color="getUsageColor(metric?.cpuUsage ?? null)"
                      :width="120"
                      :format="formatPercent"
                    />
                    <div class="gauge-label">CPU 使用率</div>
                  </div>
                </a-col>
                <a-col :span="8">
                  <div class="gauge-item">
                    <a-progress
                      type="dashboard"
                      :percent="memPercent"
                      :stroke-color="getUsageColor(metric?.memoryUsage ?? null)"
                      :width="120"
                      :format="formatPercent"
                    />
                    <div class="gauge-label">内存使用率</div>
                    <div class="gauge-detail">
                      {{ formatBytes(metric?.memoryUsed ?? null) }} / {{ formatBytes(hostInfo.memoryTotal) }}
                    </div>
                  </div>
                </a-col>
                <a-col :span="8">
                  <div class="gauge-item">
                    <a-progress
                      type="dashboard"
                      :percent="diskPercent"
                      :stroke-color="getUsageColor(metric?.diskUsage ?? null)"
                      :width="120"
                      :format="formatPercent"
                    />
                    <div class="gauge-label">硬盘使用率</div>
                    <div class="gauge-detail">
                      {{ formatBytes(metric?.diskUsed ?? null) }} / {{ formatBytes(hostInfo.diskTotal) }}
                    </div>
                  </div>
                </a-col>
              </a-row>
              <div class="metric-time" v-if="metric?.collectTime">
                数据采集时间：{{ metric.collectTime.replace('T', ' ').substring(0, 19) }}
              </div>
            </a-card>
          </a-col>

          <a-col :span="24" :lg="10">
            <a-card title="基本信息" :bordered="false" class="section-card">
              <a-descriptions :column="1" size="small" bordered>
                <a-descriptions-item label="主机名">{{ hostInfo.hostname }}</a-descriptions-item>
                <a-descriptions-item label="IP 地址">{{ hostInfo.ipAddress }}</a-descriptions-item>
                <a-descriptions-item label="所属用户">{{ hostInfo.username || '-' }}</a-descriptions-item>
                <a-descriptions-item label="所属分组">{{ hostInfo.groupName || '未分组' }}</a-descriptions-item>
                <a-descriptions-item label="最后心跳">{{ timeAgo(hostInfo.lastHeartbeat) }}</a-descriptions-item>
                <a-descriptions-item label="注册时间">{{ hostInfo.createTime ? hostInfo.createTime.replace('T', ' ').substring(0, 19) : '-' }}</a-descriptions-item>
              </a-descriptions>
            </a-card>
          </a-col>
        </a-row>
      </a-tab-pane>

      <a-tab-pane key="process" tab="进程监控">
        <div class="process-toolbar">
          <a-input-search
            v-model:value="processSearch"
            placeholder="搜索进程名或 PID"
            style="width: 240px"
            allow-clear
          />
          <a-space>
            <span class="process-count">共 {{ filteredProcessList.length }} 个进程</span>
            <a-button size="small" @click="fetchProcesses" :loading="processLoading">
              <ReloadOutlined />
              刷新
            </a-button>
          </a-space>
        </div>
        <a-table
          :columns="processColumns"
          :data-source="filteredProcessList"
          :loading="processLoading"
          :pagination="{ pageSize: 20, showTotal: (t: number) => `共 ${t} 个进程` }"
          row-key="pid"
          size="small"
          :scroll="{ x: 900 }"
          :expandable="{ expandedRowRender: undefined }"
        >
          <template #expandedRowRender="{ record }">
            <div class="process-detail">
              <a-descriptions :column="2" size="small">
                <a-descriptions-item label="命令行" :span="2">
                  <code class="cmd-text">{{ record.commandLine || '-' }}</code>
                </a-descriptions-item>
                <a-descriptions-item label="启动时间">
                  {{ record.startTime ? record.startTime.replace('T', ' ').substring(0, 19) : '-' }}
                </a-descriptions-item>
                <a-descriptions-item label="PID">
                  {{ record.pid }}
                </a-descriptions-item>
              </a-descriptions>
            </div>
          </template>
          <template #bodyCell="{ column, record }">
            <template v-if="column.key === 'cpuUsage'">
              <span :style="{ color: record.cpuUsage > 80 ? '#ff4d4f' : record.cpuUsage > 50 ? '#faad14' : '' }">
                {{ record.cpuUsage.toFixed(1) }}%
              </span>
            </template>
            <template v-if="column.key === 'memoryUsage'">
              <span :style="{ color: record.memoryUsage > 80 ? '#ff4d4f' : record.memoryUsage > 50 ? '#faad14' : '' }">
                {{ record.memoryUsage.toFixed(1) }}%
              </span>
            </template>
            <template v-if="column.key === 'memoryUsed'">
              {{ (record.memoryUsed / 1024 / 1024).toFixed(1) }} MB
            </template>
            <template v-if="column.key === 'rule'">
              <template v-if="getProcessRuleTag(record)">
                <a-tag :color="getProcessRuleTag(record)!.color">
                  {{ getProcessRuleTag(record)!.text }}
                </a-tag>
              </template>
              <template v-else>
                <span style="color: #999">-</span>
              </template>
            </template>
          </template>
        </a-table>
      </a-tab-pane>

      <a-tab-pane key="rules" tab="进程规则">
        <div class="rules-toolbar">
          <a-alert
            v-if="!hostInfo.groupId"
            message="该主机未分配分组，无法设置进程规则"
            type="warning"
            show-icon
            style="margin-bottom: 16px"
          />
        </div>

        <a-table
          :columns="ruleColumns"
          :data-source="processRules"
          :pagination="false"
          row-key="id"
          size="small"
        >
          <template #bodyCell="{ column, record }">
            <template v-if="column.key === 'ruleType'">
              <a-tag :color="record.ruleType === 'BLACKLIST' ? 'red' : 'green'">
                <template v-if="record.ruleType === 'BLACKLIST'">
                  <CloseCircleOutlined /> 黑名单
                </template>
                <template v-else>
                  <CheckCircleOutlined /> 白名单
                </template>
              </a-tag>
            </template>
            <template v-if="column.key === 'createTime'">
              {{ record.createTime ? record.createTime.replace('T', ' ').substring(0, 19) : '-' }}
            </template>
          </template>
        </a-table>

        <a-empty v-if="processRules.length === 0 && hostInfo.groupId" description="暂无进程规则" style="margin-top: 40px" />
      </a-tab-pane>
    </a-tabs>
  </div>

  <div v-else class="loading-wrapper">
    <a-spin size="large" />
  </div>
</template>

<style scoped>
.host-detail {
  height: 100%;
}

.detail-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
  flex-wrap: wrap;
  gap: 12px;
}

.header-left {
  display: flex;
  align-items: center;
  gap: 16px;
}

.host-title {
  display: flex;
  align-items: center;
  gap: 10px;
}

.host-title h2 {
  margin: 0;
  font-size: 20px;
}

.host-ip {
  color: #8c8c8c;
  font-size: 14px;
}

.header-right {
  display: flex;
  gap: 8px;
}

.section-card {
  border-radius: 8px;
}

.gauge-item {
  text-align: center;
}

.gauge-value {
  font-size: 18px;
  font-weight: 600;
}

.gauge-label {
  margin-top: 8px;
  font-size: 14px;
  color: #595959;
}

.gauge-detail {
  margin-top: 4px;
  font-size: 12px;
  color: #8c8c8c;
}

.metric-time {
  margin-top: 16px;
  text-align: center;
  font-size: 12px;
  color: #bfbfbf;
}

.metric-chart {
  width: 100%;
  height: 300px;
}

.process-toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
}

.process-count {
  color: #8c8c8c;
  font-size: 14px;
}

.process-detail {
  padding: 8px 16px;
}

.cmd-text {
  font-family: 'Courier New', monospace;
  font-size: 12px;
  color: #595959;
  word-break: break-all;
  background: #f5f5f5;
  padding: 4px 8px;
  border-radius: 4px;
  display: inline-block;
  max-width: 100%;
}

.loading-wrapper {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 300px;
}
</style>
