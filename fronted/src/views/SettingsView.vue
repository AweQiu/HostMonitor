<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { message } from 'ant-design-vue'
import {
  BgColorsOutlined,
  SendOutlined
} from '@ant-design/icons-vue'

const activeTab = ref('general')

const STORAGE_KEY = 'host_monitor_settings'

const defaultSettings = {
  systemName: 'HostMonitor',
  themeColor: '#1890ff',
  sessionTimeout: 4,
  dataRetentionDays: 30,
  metricRefreshInterval: 10,
  defaultMetricRange: '1h',
  hostListSortBy: 'status',
  metricIntervalMs: 5000,
  processIntervalMs: 15000,
  screenFps: 5,
  screenJpegQuality: 0.5,
  processCpuThreshold: 1.0,
  heartbeatTimeout: 60,
  offlineThreshold: 60,
  notificationRetentionDays: 30,
  emailNotificationEnabled: false,
  smtpServer: '',
  smtpPort: 465,
  smtpSender: '',
  smtpAuthCode: ''
}

const settings = reactive({ ...defaultSettings })

function loadSettings() {
  try {
    const saved = localStorage.getItem(STORAGE_KEY)
    if (saved) {
      Object.assign(settings, JSON.parse(saved))
    }
  } catch {
    // ignore
  }
}

function saveSettings() {
  localStorage.setItem(STORAGE_KEY, JSON.stringify(settings))
  message.success('设置已保存')
}

function resetSettings() {
  Object.assign(settings, defaultSettings)
  localStorage.removeItem(STORAGE_KEY)
  message.success('设置已重置为默认值')
}

const themeColors = [
  { color: '#1890ff', label: '默认蓝' },
  { color: '#0960bd', label: '深蓝' },
  { color: '#52c41a', label: '自然绿' },
  { color: '#13c2c2', label: '青色' },
  { color: '#722ed1', label: '紫色' },
  { color: '#eb2f96', label: '洋红' },
  { color: '#fa541c', label: '橙色' },
  { color: '#faad14', label: '金色' }
]

const emailTestLoading = ref(false)

function sendTestEmail() {
  if (!settings.smtpServer) {
    message.warning('请先填写 SMTP 服务器')
    return
  }
  if (!settings.smtpSender) {
    message.warning('请先填写发件人地址')
    return
  }
  if (!settings.smtpAuthCode) {
    message.warning('请先填写授权码')
    return
  }
  emailTestLoading.value = true
  setTimeout(() => {
    emailTestLoading.value = false
    message.info('邮件发送功能尚未接入后端，当前仅保存配置')
  }, 1500)
}

onMounted(() => {
  loadSettings()
})
</script>

<template>
  <div class="settings-page">
    <a-tabs v-model:activeKey="activeTab" class="settings-tabs">
      <a-tab-pane key="general" tab="常规设置">
        <div class="settings-section">
          <a-form layout="vertical" class="settings-form">
            <a-form-item label="系统名称">
              <a-input v-model:value="settings.systemName" placeholder="输入系统名称" style="max-width: 360px" />
              <div class="form-hint">显示在侧边栏顶部和浏览器标题栏</div>
            </a-form-item>

            <a-form-item label="主题色">
              <div class="theme-color-picker">
                <div
                  v-for="item in themeColors"
                  :key="item.color"
                  class="theme-color-item"
                  :class="{ active: settings.themeColor === item.color }"
                  :style="{ backgroundColor: item.color }"
                  :title="item.label"
                  @click="settings.themeColor = item.color"
                >
                  <span v-if="settings.themeColor === item.color" class="check-mark">✓</span>
                </div>
              </div>
            </a-form-item>

            <a-form-item label="会话超时时间">
              <a-select v-model:value="settings.sessionTimeout" style="max-width: 200px">
                <a-select-option :value="1">1 小时</a-select-option>
                <a-select-option :value="2">2 小时</a-select-option>
                <a-select-option :value="4">4 小时</a-select-option>
                <a-select-option :value="8">8 小时</a-select-option>
                <a-select-option :value="24">24 小时</a-select-option>
              </a-select>
              <div class="form-hint">超过该时间未操作将自动退出登录</div>
            </a-form-item>

            <a-form-item label="数据保留天数">
              <a-input-number v-model:value="settings.dataRetentionDays" :min="1" :max="365" style="max-width: 200px" />
              <div class="form-hint">超过保留期的性能指标数据将被自动清理</div>
            </a-form-item>

            <a-form-item>
              <a-space>
                <a-button type="primary" @click="saveSettings">保存设置</a-button>
                <a-button @click="resetSettings">恢复默认</a-button>
              </a-space>
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
                <a-button type="primary" @click="saveSettings">保存设置</a-button>
                <a-button @click="resetSettings">恢复默认</a-button>
              </a-space>
            </a-form-item>
          </a-form>
        </div>
      </a-tab-pane>

      <a-tab-pane key="client" tab="客户端设置">
        <div class="settings-section">
          <div class="section-notice">
            <BgColorsOutlined style="margin-right: 6px" />
            以下配置修改后需在客户端 application.properties 中同步配置并重启生效
          </div>

          <a-form layout="vertical" class="settings-form">
            <a-form-item label="指标采集间隔">
              <a-input-number v-model:value="settings.metricIntervalMs" :min="1000" :max="60000" :step="1000" style="max-width: 200px" />
              <span class="form-unit">ms</span>
              <div class="form-hint">客户端上报 HOST_METRIC 的间隔时间</div>
            </a-form-item>

            <a-form-item label="进程采集间隔">
              <a-input-number v-model:value="settings.processIntervalMs" :min="5000" :max="300000" :step="5000" style="max-width: 200px" />
              <span class="form-unit">ms</span>
              <div class="form-hint">客户端上报 PROCESS_LIST 的间隔时间</div>
            </a-form-item>

            <a-form-item label="屏幕推流帧率">
              <a-input-number v-model:value="settings.screenFps" :min="1" :max="30" style="max-width: 200px" />
              <span class="form-unit">FPS</span>
            </a-form-item>

            <a-form-item label="屏幕推流画质">
              <a-input-number v-model:value="settings.screenJpegQuality" :min="0.1" :max="1.0" :step="0.1" style="max-width: 200px" />
              <div class="form-hint">JPEG 压缩质量，0.1 最低画质最小体积，1.0 最高画质最大体积</div>
            </a-form-item>

            <a-form-item label="进程 CPU 变化阈值">
              <a-input-number v-model:value="settings.processCpuThreshold" :min="0.1" :max="50" :step="0.5" style="max-width: 200px" />
              <span class="form-unit">%</span>
              <div class="form-hint">增量上报时 CPU 使用率变化超过此阈值才上报</div>
            </a-form-item>

            <a-form-item label="心跳超时时间">
              <a-input-number v-model:value="settings.heartbeatTimeout" :min="10" :max="600" style="max-width: 200px" />
              <span class="form-unit">秒</span>
              <div class="form-hint">服务端多久未收到心跳则断开客户端连接</div>
            </a-form-item>

            <a-form-item label="离线判定阈值">
              <a-input-number v-model:value="settings.offlineThreshold" :min="10" :max="600" style="max-width: 200px" />
              <span class="form-unit">秒</span>
              <div class="form-hint">超过该时间未收到心跳，则判定主机离线</div>
            </a-form-item>

            <a-form-item>
              <a-space>
                <a-button type="primary" @click="saveSettings">保存设置</a-button>
                <a-button @click="resetSettings">恢复默认</a-button>
              </a-space>
            </a-form-item>
          </a-form>
        </div>
      </a-tab-pane>

      <a-tab-pane key="notification" tab="通知设置">
        <div class="settings-section">
          <a-form layout="vertical" class="settings-form">
            <a-form-item label="通知保留天数">
              <a-input-number v-model:value="settings.notificationRetentionDays" :min="1" :max="365" style="max-width: 200px" />
              <div class="form-hint">超过保留期的通知将被自动清理</div>
            </a-form-item>

            <a-divider orientation="left">邮件通知</a-divider>

            <a-form-item label="启用邮件通知">
              <a-switch v-model:checked="settings.emailNotificationEnabled" />
            </a-form-item>

            <template v-if="settings.emailNotificationEnabled">
              <a-form-item label="SMTP 服务器">
                <a-input v-model:value="settings.smtpServer" placeholder="如 smtp.example.com" style="max-width: 360px" />
              </a-form-item>

              <a-form-item label="SMTP 端口">
                <a-input-number v-model:value="settings.smtpPort" :min="1" :max="65535" style="max-width: 200px" />
              </a-form-item>

              <a-form-item label="发件人地址">
                <a-input v-model:value="settings.smtpSender" placeholder="如 noreply@example.com" style="max-width: 360px" />
              </a-form-item>

              <a-form-item label="授权码">
                <a-input-password v-model:value="settings.smtpAuthCode" placeholder="SMTP 授权码" style="max-width: 360px" />
                <div class="form-hint">邮箱服务商提供的 SMTP 授权码，非登录密码</div>
              </a-form-item>

              <a-form-item>
                <a-button :loading="emailTestLoading" @click="sendTestEmail">
                  <SendOutlined /> 发送测试邮件
                </a-button>
              </a-form-item>
            </template>

            <a-form-item>
              <a-space>
                <a-button type="primary" @click="saveSettings">保存设置</a-button>
                <a-button @click="resetSettings">恢复默认</a-button>
              </a-space>
            </a-form-item>
          </a-form>
        </div>
      </a-tab-pane>

      <a-tab-pane key="alert" tab="告警设置">
        <a-empty description="暂无设置项" style="margin-top: 60px" />
      </a-tab-pane>
    </a-tabs>
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

.theme-color-picker {
  display: flex;
  gap: 12px;
  flex-wrap: wrap;
}

.theme-color-item {
  width: 36px;
  height: 36px;
  border-radius: 6px;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: transform 0.15s, box-shadow 0.15s;
  border: 2px solid transparent;
}

.theme-color-item:hover {
  transform: scale(1.12);
}

.theme-color-item.active {
  border-color: #333;
  box-shadow: 0 0 0 2px rgba(0, 0, 0, 0.1);
}

.check-mark {
  color: #fff;
  font-size: 16px;
  font-weight: bold;
  text-shadow: 0 1px 2px rgba(0, 0, 0, 0.3);
}
</style>
