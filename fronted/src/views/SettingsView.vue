<script setup lang="ts">
import { reactive, ref } from 'vue'
import { message } from 'ant-design-vue'

const loading = ref(false)

const basicSetting = reactive({
  siteName: 'HostMonitor',
  siteDescription: '主机监控系统',
  heartbeatInterval: 30,
  dataRetention: 30
})

const emailSetting = reactive({
  smtpHost: '',
  smtpPort: 465,
  senderEmail: '',
  enableNotification: false
})

function handleSave() {
  loading.value = true
  setTimeout(() => {
    loading.value = false
    message.success('设置已保存')
  }, 800)
}
</script>

<template>
  <div class="settings-page">
    <a-row :gutter="24">
      <a-col :span="24" :lg="12">
        <a-card title="基本设置" :bordered="false" style="margin-bottom: 24px">
          <a-form :model="basicSetting" layout="vertical">
            <a-form-item label="系统名称">
              <a-input v-model:value="basicSetting.siteName" />
            </a-form-item>
            <a-form-item label="系统描述">
              <a-input v-model:value="basicSetting.siteDescription" />
            </a-form-item>
            <a-form-item label="心跳间隔（秒）">
              <a-input-number v-model:value="basicSetting.heartbeatInterval" :min="5" :max="300" style="width: 100%" />
            </a-form-item>
            <a-form-item label="数据保留天数">
              <a-input-number v-model:value="basicSetting.dataRetention" :min="1" :max="365" style="width: 100%" />
            </a-form-item>
            <a-form-item>
              <a-button type="primary" :loading="loading" @click="handleSave">保存设置</a-button>
            </a-form-item>
          </a-form>
        </a-card>
      </a-col>
      <a-col :span="24" :lg="12">
        <a-card title="邮件通知" :bordered="false" style="margin-bottom: 24px">
          <a-form :model="emailSetting" layout="vertical">
            <a-form-item label="SMTP 服务器">
              <a-input v-model:value="emailSetting.smtpHost" placeholder="smtp.example.com" />
            </a-form-item>
            <a-form-item label="SMTP 端口">
              <a-input-number v-model:value="emailSetting.smtpPort" :min="1" :max="65535" style="width: 100%" />
            </a-form-item>
            <a-form-item label="发件人邮箱">
              <a-input v-model:value="emailSetting.senderEmail" placeholder="noreply@example.com" />
            </a-form-item>
            <a-form-item label="启用邮件通知">
              <a-switch v-model:checked="emailSetting.enableNotification" />
            </a-form-item>
            <a-form-item>
              <a-button type="primary" :loading="loading" @click="handleSave">保存设置</a-button>
            </a-form-item>
          </a-form>
        </a-card>
      </a-col>
    </a-row>
  </div>
</template>
