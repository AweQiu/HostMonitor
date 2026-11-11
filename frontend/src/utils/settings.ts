export interface AppSettings {
  systemName: string
  sessionTimeout: number
  dataRetentionDays: number
  metricRefreshInterval: number
  defaultMetricRange: string
  hostListSortBy: string
  metricIntervalMs: number
  processIntervalMs: number
  screenFps: number
  screenJpegQuality: number
  processCpuThreshold: number
  heartbeatTimeout: number
  offlineThreshold: number
  notificationRetentionDays: number
  emailNotificationEnabled: boolean
  smtpServer: string
  smtpPort: number
  smtpSender: string
  smtpAuthCode: string
  processAlertEnabled: boolean
  alertDedupMinutes: number
}

const STORAGE_KEY = 'host_monitor_settings'

const defaultSettings: AppSettings = {
  systemName: 'HostMonitor',
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
  smtpAuthCode: '',
  processAlertEnabled: true,
  alertDedupMinutes: 30
}

export function getSettings(): AppSettings {
  try {
    const saved = localStorage.getItem(STORAGE_KEY)
    if (saved) {
      return { ...defaultSettings, ...JSON.parse(saved) }
    }
  } catch {
    // ignore
  }
  return { ...defaultSettings }
}

export function saveSettings(settings: Partial<AppSettings>): void {
  const current = getSettings()
  const merged = { ...current, ...settings }
  localStorage.setItem(STORAGE_KEY, JSON.stringify(merged))
  window.dispatchEvent(new CustomEvent('settings-changed', { detail: merged }))
}

export function resetSettings(): AppSettings {
  localStorage.removeItem(STORAGE_KEY)
  window.dispatchEvent(new CustomEvent('settings-changed', { detail: defaultSettings }))
  return { ...defaultSettings }
}

export function getSetting<K extends keyof AppSettings>(key: K): AppSettings[K] {
  return getSettings()[key]
}
