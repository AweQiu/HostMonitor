<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import { message } from 'ant-design-vue'
import {
  BgColorsOutlined,
  SendOutlined,
  PlusOutlined,
  ReloadOutlined,
  DeleteOutlined
} from '@ant-design/icons-vue'
import { getSettings, saveSettings as saveToStore, resetSettings as resetStore, type AppSettings } from '@/utils/settings'
import request from '@/utils/request'
import { useUserStore } from '@/stores/user'

const userStore = useUserStore()

const isAdmin = computed(() => {
  const roles = userStore.userInfo?.roles || []
  return roles.includes('SUPER_ADMIN') || roles.includes('GROUP_ADMIN')
})

const isSuperAdmin = computed(() => {
  const roles = userStore.userInfo?.roles || []
  return roles.includes('SUPER_ADMIN')
})

const activeTab = ref('general')

const settings = reactive<AppSettings>(getSettings())

const alertSettings = reactive<Record<string, string>>({})
const alertLoading = ref(false)

const systemSettings = reactive<Record<string, string>>({})
const systemLoading = ref(false)

const clientSettings = reactive<Record<string, string>>({})
const clientLoading = ref(false)

async function loadAlertSettings() {
  try {
    if (isSuperAdmin.value) {
      const data = await request.get('/alert-settings')
      Object.assign(alertSettings, data.data || {})
    } else {
      const groupId = userStore.userInfo?.groupId
      if (groupId) {
        const data = await request.get(`/group-settings/${groupId}`)
        Object.assign(alertSettings, data.data || {})
      }
    }
  } catch {
    // ignore
  }
}

async function saveAlertSettings() {
  alertLoading.value = true
  try {
    if (isSuperAdmin.value) {
      await request.put('/alert-settings', alertSettings)
    } else {
      const groupId = userStore.userInfo?.groupId
      if (groupId) {
        await request.put(`/group-settings/${groupId}`, alertSettings)
      }
    }
    message.success('告警设置已保存')
  } catch {
    // handled by interceptor
  } finally {
    alertLoading.value = false
  }
}

async function loadSystemSettings() {
  try {
    const data = await request.get('/system-settings')
    Object.assign(systemSettings, data.data || {})
    Object.assign(clientSettings, data.data || {})
  } catch {
    // ignore
  }
}

async function saveSystemSettings() {
  systemLoading.value = true
  try {
    await request.put('/system-settings', {
      session_timeout_hours: systemSettings.session_timeout_hours,
      data_retention_days: systemSettings.data_retention_days,
      notification_retention_days: systemSettings.notification_retention_days,
      email_enabled: systemSettings.email_enabled,
      smtp_host: systemSettings.smtp_host,
      smtp_port: systemSettings.smtp_port,
      smtp_username: systemSettings.smtp_username,
      smtp_password: systemSettings.smtp_password
    })
    message.success('系统设置已保存')
  } catch {
    // handled by interceptor
  } finally {
    systemLoading.value = false
  }
}

async function saveClientSettings() {
  clientLoading.value = true
  try {
    const payload = {
      client_metric_interval_ms: String(clientSettings.client_metric_interval_ms || '5000'),
      client_process_interval_ms: String(clientSettings.client_process_interval_ms || '15000'),
      client_screen_fps: String(clientSettings.client_screen_fps || '5'),
      client_screen_jpeg_quality: String(clientSettings.client_screen_jpeg_quality || '0.5'),
      client_process_cpu_threshold: String(clientSettings.client_process_cpu_threshold || '1.0'),
      client_heartbeat_timeout_ms: String(clientSettings.client_heartbeat_timeout_ms || '60000'),
      client_offline_threshold_ms: String(clientSettings.client_offline_threshold_ms || '60000')
    }
    if (isSuperAdmin.value) {
      await request.put('/system-settings', payload)
    } else {
      const groupId = userStore.userInfo?.groupId
      if (groupId) {
        await request.put(`/group-settings/${groupId}`, payload)
      }
    }
    message.success('客户端设置已保存，在线客户端将自动更新配置')
  } catch {
    // handled by interceptor
  } finally {
    clientLoading.value = false
  }
}

function handleSave() {
  saveToStore(settings)
  message.success('设置已保存')
}

function handleReset() {
  const defaults = resetStore()
  Object.assign(settings, defaults)
  message.success('设置已重置为默认值')
}

const emailTestLoading = ref(false)
const emailTestTo = ref('')

async function sendTestEmail() {
  if (!systemSettings.smtp_host) {
    message.warning('请先填写 SMTP 服务器')
    return
  }
  if (!systemSettings.smtp_username) {
    message.warning('请先填写发件人地址')
    return
  }
  if (!systemSettings.smtp_password) {
    message.warning('请先填写授权码')
    return
  }
  if (!emailTestTo.value) {
    message.warning('请输入收件人邮箱')
    return
  }
  emailTestLoading.value = true
  try {
    await request.post('/system-settings/email/test', { to: emailTestTo.value })
    message.success('测试邮件已发送，请查收')
  } catch {
    // handled by interceptor
  } finally {
    emailTestLoading.value = false
  }
}

interface ProcessRule {
  id: number
  groupId: number
  groupName: string
  processName: string
  ruleType: 'WHITELIST' | 'BLACKLIST'
  description: string | null
  createTime: string
}

interface GroupOption {
  id: number
  groupName: string
}

const ruleLoading = ref(false)
const ruleData = ref<ProcessRule[]>([])
const groups = ref<GroupOption[]>([])
const filterGroup = ref<number | undefined>(undefined)
const filterType = ref<string | undefined>(undefined)
const ruleSearch = ref('')

const addModalVisible = ref(false)
const addForm = ref({
  groupId: undefined as number | undefined,
  processName: '',
  ruleType: 'BLACKLIST' as 'WHITELIST' | 'BLACKLIST',
  description: ''
})

const ruleColumns = [
  { title: '进程名', dataIndex: 'processName', key: 'processName', width: 180 },
  { title: '规则类型', dataIndex: 'ruleType', key: 'ruleType', width: 120 },
  { title: '所属分组', dataIndex: 'groupName', key: 'groupName', width: 140 },
  { title: '说明', dataIndex: 'description', key: 'description', ellipsis: true },
  { title: '创建时间', dataIndex: 'createTime', key: 'createTime', width: 180 },
  { title: '操作', key: 'action', width: 80 }
]

const filteredRuleData = computed(() => {
  let data = ruleData.value
  if (filterGroup.value) {
    data = data.filter(r => r.groupId === filterGroup.value)
  }
  if (filterType.value) {
    data = data.filter(r => r.ruleType === filterType.value)
  }
  if (ruleSearch.value) {
    const keyword = ruleSearch.value.toLowerCase()
    data = data.filter(r =>
      r.processName.toLowerCase().includes(keyword) ||
      (r.groupName || '').toLowerCase().includes(keyword)
    )
  }
  return data
})

async function fetchRules() {
  ruleLoading.value = true
  try {
    const data = await request.get('/process-rules')
    ruleData.value = data.data
  } catch {
    // error handled by interceptor
  } finally {
    ruleLoading.value = false
  }
}

async function fetchGroups() {
  try {
    const data = await request.get('/groups')
    groups.value = data.data
  } catch {
    // ignore
  }
}

function showAddModal() {
  addForm.value = {
    groupId: undefined,
    processName: '',
    ruleType: 'BLACKLIST',
    description: ''
  }
  addModalVisible.value = true
}

async function handleAddRule() {
  if (!addForm.value.groupId) {
    message.warning('请选择所属分组')
    return
  }
  if (!addForm.value.processName) {
    message.warning('请输入进程名')
    return
  }
  try {
    await request.post(`/groups/${addForm.value.groupId}/process-rules`, {
      processName: addForm.value.processName,
      ruleType: addForm.value.ruleType,
      description: addForm.value.description
    })
    message.success('规则添加成功')
    addModalVisible.value = false
    fetchRules()
  } catch {
    // error handled by interceptor
  }
}

async function handleDeleteRule(id: number, groupId: number) {
  try {
    await request.delete(`/groups/${groupId}/process-rules/${id}`)
    message.success('规则已删除')
    fetchRules()
  } catch {
    // error handled by interceptor
  }
}

onMounted(() => {
  Object.assign(settings, getSettings())
  loadAlertSettings()
  loadSystemSettings()
  fetchRules()
  fetchGroups()
})
</script>

<template>
  <div class="settings-page">
    <a-tabs v-model:activeKey="activeTab" class="settings-tabs">
      <a-tab-pane v-if="isSuperAdmin" key="general" tab="常规设置">
        <div class="settings-section">
          <a-form layout="vertical" class="settings-form">
            <a-form-item label="系统名称">
              <a-input v-model:value="settings.systemName" placeholder="输入系统名称" style="max-width: 360px" />
              <div class="form-hint">显示在侧边栏顶部和浏览器标题栏</div>
            </a-form-item>

            <a-form-item label="会话超时时间">
              <a-select v-model:value="systemSettings.session_timeout_hours" style="max-width: 200px">
                <a-select-option value="1">1 小时</a-select-option>
                <a-select-option value="2">2 小时</a-select-option>
                <a-select-option value="4">4 小时</a-select-option>
                <a-select-option value="8">8 小时</a-select-option>
                <a-select-option value="24">24 小时</a-select-option>
              </a-select>
              <div class="form-hint">超过该时间未操作将自动退出登录</div>
            </a-form-item>

            <a-form-item label="数据保留天数">
              <a-input-number :value="Number(systemSettings.data_retention_days || 2)" :min="1" :max="365" style="max-width: 200px" @change="(v: number) => systemSettings.data_retention_days = String(v)" />
              <div class="form-hint">超过保留期的性能指标数据将被自动清理</div>
            </a-form-item>

            <a-form-item>
              <a-button type="primary" :loading="systemLoading" @click="saveSystemSettings">保存设置</a-button>
            </a-form-item>
          </a-form>
        </div>
      </a-tab-pane>

      <a-tab-pane key="display" tab="展示设置">
        <div class="settings-section">
          <a-form layout="vertical" class="settings-form">
            <a-form-item label="指标刷新间隔">
              <a-select v-model:value="settings.metricRefreshInterval" style="max-width: 200px">
                <a-select-option :value="5">5 秒</a-select-option>
                <a-select-option :value="10">10 秒</a-select-option>
                <a-select-option :value="30">30 秒</a-select-option>
                <a-select-option :value="0">关闭自动刷新</a-select-option>
              </a-select>
              <div class="form-hint">主机详情页性能指标自动刷新频率</div>
            </a-form-item>

            <a-form-item label="默认指标时间范围">
              <a-select v-model:value="settings.defaultMetricRange" style="max-width: 200px">
                <a-select-option value="1h">1 小时</a-select-option>
                <a-select-option value="6h">6 小时</a-select-option>
                <a-select-option value="24h">24 小时</a-select-option>
                <a-select-option value="7d">7 天</a-select-option>
              </a-select>
              <div class="form-hint">进入主机详情时折线图默认展示的时间范围</div>
            </a-form-item>

            <a-form-item label="主机列表默认排序">
              <a-select v-model:value="settings.hostListSortBy" style="max-width: 200px">
                <a-select-option value="status">按状态（在线优先）</a-select-option>
                <a-select-option value="hostname">按主机名</a-select-option>
                <a-select-option value="cpuUsage">按 CPU 使用率</a-select-option>
                <a-select-option value="memoryUsage">按内存使用率</a-select-option>
              </a-select>
            </a-form-item>

            <a-form-item>
              <a-space>
                <a-button type="primary" @click="handleSave">保存设置</a-button>
                <a-button @click="handleReset">恢复默认</a-button>
              </a-space>
            </a-form-item>
          </a-form>
        </div>
      </a-tab-pane>

      <a-tab-pane key="client" tab="客户端设置">
        <div class="settings-section">
          <div class="section-notice success-notice">
            <BgColorsOutlined style="margin-right: 6px" />
            以下配置保存后将自动推送给在线客户端，无需重启即可生效；离线客户端下次连接时自动拉取最新配置
          </div>

          <a-form layout="vertical" class="settings-form">
            <a-form-item label="指标采集间隔">
              <a-input-number :value="Number(clientSettings.client_metric_interval_ms || 5000)" :min="1000" :max="60000" :step="1000" style="max-width: 200px" @change="(v: number) => clientSettings.client_metric_interval_ms = String(v)" />
              <span class="form-unit">ms</span>
              <div class="form-hint">客户端上报 HOST_METRIC 的间隔时间</div>
            </a-form-item>

            <a-form-item label="进程采集间隔">
              <a-input-number :value="Number(clientSettings.client_process_interval_ms || 15000)" :min="5000" :max="300000" :step="5000" style="max-width: 200px" @change="(v: number) => clientSettings.client_process_interval_ms = String(v)" />
              <span class="form-unit">ms</span>
              <div class="form-hint">客户端上报 PROCESS_LIST 的间隔时间</div>
            </a-form-item>

            <a-form-item label="屏幕推流帧率">
              <a-input-number :value="Number(clientSettings.client_screen_fps || 5)" :min="1" :max="30" style="max-width: 200px" @change="(v: number) => clientSettings.client_screen_fps = String(v)" />
              <span class="form-unit">FPS</span>
            </a-form-item>

            <a-form-item label="屏幕推流画质">
              <a-input-number :value="Number(clientSettings.client_screen_jpeg_quality || 0.5)" :min="0.1" :max="1.0" :step="0.1" style="max-width: 200px" @change="(v: number) => clientSettings.client_screen_jpeg_quality = String(v)" />
              <div class="form-hint">JPEG 压缩质量，0.1 最低画质最小体积，1.0 最高画质最大体积</div>
            </a-form-item>

            <a-form-item label="进程 CPU 变化阈值">
              <a-input-number :value="Number(clientSettings.client_process_cpu_threshold || 1.0)" :min="0.1" :max="50" :step="0.5" style="max-width: 200px" @change="(v: number) => clientSettings.client_process_cpu_threshold = String(v)" />
              <span class="form-unit">%</span>
              <div class="form-hint">增量上报时 CPU 使用率变化超过此阈值才上报</div>
            </a-form-item>

            <a-form-item label="心跳超时时间">
              <a-input-number :value="Number(clientSettings.client_heartbeat_timeout_ms || 60000) / 1000" :min="10" :max="600" style="max-width: 200px" @change="(v: number) => clientSettings.client_heartbeat_timeout_ms = String(v * 1000)" />
              <span class="form-unit">秒</span>
              <div class="form-hint">服务端多久未收到心跳则断开客户端连接</div>
            </a-form-item>

            <a-form-item label="离线判定阈值">
              <a-input-number :value="Number(clientSettings.client_offline_threshold_ms || 60000) / 1000" :min="10" :max="600" style="max-width: 200px" @change="(v: number) => clientSettings.client_offline_threshold_ms = String(v * 1000)" />
              <span class="form-unit">秒</span>
              <div class="form-hint">超过该时间未收到心跳，则判定主机离线</div>
            </a-form-item>

            <a-form-item>
              <a-button type="primary" :loading="clientLoading" @click="saveClientSettings">保存设置</a-button>
            </a-form-item>
          </a-form>
        </div>
      </a-tab-pane>

      <a-tab-pane v-if="isSuperAdmin" key="notification" tab="通知设置">
        <div class="settings-section">
          <a-form layout="vertical" class="settings-form">
            <a-form-item label="通知保留天数">
              <a-input-number :value="Number(systemSettings.notification_retention_days || 30)" :min="1" :max="365" style="max-width: 200px" @change="(v: number) => systemSettings.notification_retention_days = String(v)" />
              <div class="form-hint">超过保留期的通知将被自动清理</div>
            </a-form-item>

            <a-divider orientation="left">邮件通知</a-divider>

            <a-form-item label="启用邮件通知">
              <a-switch :checked="systemSettings.email_enabled === 'true'" @change="(v: boolean) => systemSettings.email_enabled = v ? 'true' : 'false'" />
            </a-form-item>

            <template v-if="systemSettings.email_enabled === 'true'">
              <a-form-item label="SMTP 服务器">
                <a-input v-model:value="systemSettings.smtp_host" placeholder="如 smtp.example.com" style="max-width: 360px" />
              </a-form-item>

              <a-form-item label="SMTP 端口">
                <a-input-number :value="Number(systemSettings.smtp_port || 465)" :min="1" :max="65535" style="max-width: 200px" @change="(v: number) => systemSettings.smtp_port = String(v)" />
              </a-form-item>

              <a-form-item label="发件人地址">
                <a-input v-model:value="systemSettings.smtp_username" placeholder="如 noreply@example.com" style="max-width: 360px" />
              </a-form-item>

              <a-form-item label="授权码">
                <a-input-password v-model:value="systemSettings.smtp_password" placeholder="SMTP 授权码" style="max-width: 360px" />
                <div class="form-hint">邮箱服务商提供的 SMTP 授权码，非登录密码</div>
              </a-form-item>

              <a-form-item label="发送测试邮件">
                <a-input-group compact>
                  <a-input v-model:value="emailTestTo" placeholder="收件人邮箱" style="width: calc(100% - 140px); max-width: 280px" />
                  <a-button :loading="emailTestLoading" @click="sendTestEmail">
                    <SendOutlined /> 发送
                  </a-button>
                </a-input-group>
              </a-form-item>
            </template>

            <a-form-item>
              <a-button type="primary" :loading="systemLoading" @click="saveSystemSettings">保存设置</a-button>
            </a-form-item>
          </a-form>
        </div>
      </a-tab-pane>

      <a-tab-pane key="alert" tab="告警设置">
        <div class="alert-section">
          <a-row :gutter="[24, 24]">
            <a-col :span="24" :lg="14">
              <a-card title="进程告警" :bordered="false" class="alert-card">
                <a-form layout="vertical">
                  <a-form-item label="启用进程告警">
                    <a-switch :checked="alertSettings.process_alert_enabled === 'true'" @change="(v: boolean) => alertSettings.process_alert_enabled = v ? 'true' : 'false'" />
                    <div class="form-hint">检测到黑名单进程运行或白名单进程未运行时自动发送告警通知</div>
                  </a-form-item>

                  <template v-if="alertSettings.process_alert_enabled === 'true'">
                    <a-divider orientation="left" style="margin: 12px 0 16px">进程规则设置</a-divider>

                    <div class="rule-toolbar">
                      <a-space>
                        <a-input-search
                          v-model:value="ruleSearch"
                          placeholder="搜索进程名或分组"
                          style="width: 180px"
                          allow-clear
                          size="small"
                        />
                        <a-select
                          v-model:value="filterGroup"
                          placeholder="按分组筛选"
                          style="width: 120px"
                          allow-clear
                          size="small"
                        >
                          <a-select-option v-for="g in groups" :key="g.id" :value="g.id">
                            {{ g.groupName }}
                          </a-select-option>
                        </a-select>
                        <a-select
                          v-model:value="filterType"
                          placeholder="按类型筛选"
                          style="width: 110px"
                          allow-clear
                          size="small"
                        >
                          <a-select-option value="BLACKLIST">黑名单</a-select-option>
                          <a-select-option value="WHITELIST">白名单</a-select-option>
                        </a-select>
                      </a-space>
                      <a-space>
                        <a-button size="small" @click="fetchRules" :loading="ruleLoading">
                          <ReloadOutlined />
                        </a-button>
                        <a-button v-if="isAdmin" size="small" type="primary" @click="showAddModal">
                          <PlusOutlined /> 添加规则
                        </a-button>
                      </a-space>
                    </div>

                    <a-table
                      :columns="ruleColumns"
                      :data-source="filteredRuleData"
                      :loading="ruleLoading"
                      :pagination="{ pageSize: 10, size: 'small', showTotal: (t: number) => `共 ${t} 条规则` }"
                      row-key="id"
                      size="small"
                      :scroll="{ x: 600 }"
                      style="margin-top: 12px"
                    >
                      <template #bodyCell="{ column, record }">
                        <template v-if="column.key === 'ruleType'">
                          <a-tag :color="record.ruleType === 'BLACKLIST' ? 'red' : 'green'">
                            {{ record.ruleType === 'BLACKLIST' ? '黑名单' : '白名单' }}
                          </a-tag>
                        </template>
                        <template v-if="column.key === 'createTime'">
                          {{ record.createTime ? record.createTime.replace('T', ' ').substring(0, 19) : '-' }}
                        </template>
                        <template v-if="column.key === 'action'">
                          <a-popconfirm title="确定删除该规则？" ok-text="确定" cancel-text="取消" @confirm="handleDeleteRule(record.id, record.groupId)">
                            <a-button type="link" size="small" danger>
                              <DeleteOutlined />
                            </a-button>
                          </a-popconfirm>
                        </template>
                      </template>
                    </a-table>
                  </template>
                </a-form>
              </a-card>
            </a-col>

            <a-col :span="24" :lg="10">
              <a-card title="性能告警" :bordered="false" class="alert-card">
                <a-form layout="vertical">
                  <a-form-item label="启用性能告警">
                    <a-switch :checked="alertSettings.performance_alert_enabled === 'true'" @change="(v: boolean) => alertSettings.performance_alert_enabled = v ? 'true' : 'false'" />
                    <div class="form-hint">CPU、内存、磁盘使用率超过阈值时自动发送告警通知</div>
                  </a-form-item>

                  <template v-if="alertSettings.performance_alert_enabled === 'true'">
                    <a-form-item label="CPU 使用率阈值">
                      <a-input-number :value="Number(alertSettings.cpu_threshold || 90)" :min="1" :max="100" style="max-width: 200px" @change="(v: number) => alertSettings.cpu_threshold = String(v)" />
                      <span class="form-unit">%</span>
                      <div class="form-hint">CPU 使用率超过此值时触发告警</div>
                    </a-form-item>

                    <a-form-item label="内存使用率阈值">
                      <a-input-number :value="Number(alertSettings.memory_threshold || 90)" :min="1" :max="100" style="max-width: 200px" @change="(v: number) => alertSettings.memory_threshold = String(v)" />
                      <span class="form-unit">%</span>
                      <div class="form-hint">内存使用率超过此值时触发告警</div>
                    </a-form-item>

                    <a-form-item label="磁盘使用率阈值">
                      <a-input-number :value="Number(alertSettings.disk_threshold || 90)" :min="1" :max="100" style="max-width: 200px" @change="(v: number) => alertSettings.disk_threshold = String(v)" />
                      <span class="form-unit">%</span>
                      <div class="form-hint">磁盘使用率超过此值时触发告警</div>
                    </a-form-item>
                  </template>
                </a-form>
              </a-card>
            </a-col>
          </a-row>

          <a-card title="通用设置" :bordered="false" class="alert-card" style="margin-top: 24px">
            <a-form layout="vertical">
              <a-form-item label="告警去重间隔">
                <a-input-number :value="Number(alertSettings.alert_dedup_minutes || 30)" :min="5" :max="1440" :step="5" style="max-width: 200px" @change="(v: number) => alertSettings.alert_dedup_minutes = String(v)" />
                <span class="form-unit">分钟</span>
                <div class="form-hint">同一主机同一类型的告警，在间隔时间内不重复发送</div>
              </a-form-item>

              <a-form-item>
                <a-button type="primary" :loading="alertLoading" @click="saveAlertSettings">保存告警设置</a-button>
              </a-form-item>
            </a-form>
          </a-card>
        </div>
      </a-tab-pane>
    </a-tabs>

    <a-modal v-model:open="addModalVisible" title="添加进程规则" @ok="handleAddRule" destroy-on-close>
      <a-form :label-col="{ span: 6 }" :wrapper-col="{ span: 16 }">
        <a-form-item label="所属分组" required>
          <a-select v-model:value="addForm.groupId" placeholder="请选择分组">
            <a-select-option v-for="g in groups" :key="g.id" :value="g.id">
              {{ g.groupName }}
            </a-select-option>
          </a-select>
        </a-form-item>
        <a-form-item label="进程名" required>
          <a-input v-model:value="addForm.processName" placeholder="如 nginx, python3, java" />
        </a-form-item>
        <a-form-item label="规则类型" required>
          <a-radio-group v-model:value="addForm.ruleType">
            <a-radio value="BLACKLIST">黑名单（禁止运行）</a-radio>
            <a-radio value="WHITELIST">白名单（允许运行）</a-radio>
          </a-radio-group>
        </a-form-item>
        <a-form-item label="说明">
          <a-textarea v-model:value="addForm.description" placeholder="规则说明（可选）" :rows="2" />
        </a-form-item>
      </a-form>
    </a-modal>
  </div>
</template>

<style scoped>
.settings-page {
  height: 100%;
  padding: 0 4px;
}

.settings-tabs {
  height: 100%;
}

.settings-section {
  max-width: 680px;
  padding-top: 8px;
}

.settings-form {
  margin-top: 4px;
}

.form-hint {
  font-size: 12px;
  color: #999;
  margin-top: 4px;
}

.form-unit {
  margin-left: 8px;
  color: #666;
  font-size: 13px;
}

.section-notice {
  background: #e6f7ff;
  border: 1px solid #91d5ff;
  border-radius: 6px;
  padding: 10px 16px;
  margin-bottom: 20px;
  font-size: 13px;
  color: #0960bd;
  display: flex;
  align-items: center;
}

.success-notice {
  background: #f6ffed;
  border-color: #b7eb8f;
  color: #52c41a;
}

.client-config-table {
  margin-bottom: 8px;
}

.rule-toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 4px;
}

.alert-section {
  padding-top: 8px;
}

.alert-card {
  border-radius: 8px;
}
</style>
