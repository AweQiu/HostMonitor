<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { message } from 'ant-design-vue'
import {
  ReloadOutlined,
  PlusOutlined,
  CheckOutlined,
  DeleteOutlined,
  BellOutlined
} from '@ant-design/icons-vue'
import request from '@/utils/request'
import { useUserStore } from '@/stores/user'

const userStore = useUserStore()

interface NotificationItem {
  id: number
  title: string
  content: string
  senderName: string
  sendType: number
  targetInfo: string
  isRead: number
  sendTime: string
}

const notifications = ref<NotificationItem[]>([])
const loading = ref(false)
const currentPage = ref(1)
const pageSize = ref(10)
const total = ref(0)
const sendModalVisible = ref(false)
const sendLoading = ref(false)

const sendForm = ref({
  title: '',
  content: '',
  sendType: 1,
  targetGroupId: null as number | null,
  targetUserIds: [] as number[]
})

const groups = ref<any[]>([])
const users = ref<any[]>([])

const isAdmin = () => {
  return userStore.userInfo?.roles?.includes('SUPER_ADMIN') ||
         userStore.userInfo?.roles?.includes('GROUP_ADMIN')
}

async function loadNotifications() {
  loading.value = true
  try {
    const data = await request.get('/notifications', {
      params: { page: currentPage.value, size: pageSize.value }
    })
    notifications.value = data.data.records
    total.value = data.data.total
  } catch {
    // ignore
  } finally {
    loading.value = false
  }
}

async function loadSendOptions() {
  try {
    const [groupsData, usersData] = await Promise.all([
      request.get('/users/groups'),
      request.get('/users', { params: { page: 1, size: 1000 } })
    ])
    groups.value = groupsData.data || []
    users.value = usersData.data?.records || []
  } catch {
    // ignore
  }
}

function handleTableChange(pagination: any) {
  currentPage.value = pagination.current
  pageSize.value = pagination.pageSize
  loadNotifications()
}

async function markAsRead(id: number) {
  try {
    await request.put(`/notifications/${id}/read`)
    const item = notifications.value.find(n => n.id === id)
    if (item) item.isRead = 1
  } catch {
    // ignore
  }
}

async function markAllAsRead() {
  try {
    await request.put('/notifications/read-all')
    notifications.value.forEach(n => n.isRead = 1)
    message.success('已全部标记为已读')
  } catch {
    // ignore
  }
}

async function deleteNotification(id: number) {
  try {
    await request.delete(`/notifications/${id}`)
    message.success('删除成功')
    loadNotifications()
  } catch {
    // ignore
  }
}

function openSendModal() {
  sendForm.value = {
    title: '',
    content: '',
    sendType: 1,
    targetGroupId: null,
    targetUserIds: []
  }
  sendModalVisible.value = true
  loadSendOptions()
}

async function handleSend() {
  if (!sendForm.value.title.trim()) {
    message.warning('请输入通知标题')
    return
  }
  if (!sendForm.value.content.trim()) {
    message.warning('请输入通知内容')
    return
  }

  sendLoading.value = true
  try {
    await request.post('/notifications', sendForm.value)
    message.success('发送成功')
    sendModalVisible.value = false
    loadNotifications()
  } catch {
    // ignore
  } finally {
    sendLoading.value = false
  }
}

function getSendTypeLabel(type: number) {
  switch (type) {
    case 1: return '全体'
    case 2: return '分组'
    case 3: return '指定用户'
    default: return '未知'
  }
}

function getSendTypeColor(type: number) {
  switch (type) {
    case 1: return 'red'
    case 2: return 'blue'
    case 3: return 'green'
    default: return 'default'
  }
}

onMounted(() => {
  loadNotifications()
})
</script>

<template>
  <div class="notifications-page">
    <div class="page-header">
      <div class="header-left">
        <h3>通知管理</h3>
      </div>
      <div class="header-right">
        <a-button @click="markAllAsRead" :disabled="!notifications.some(n => n.isRead === 0)">
          <CheckOutlined />
          全部已读
        </a-button>
        <a-button v-if="isAdmin()" type="primary" @click="openSendModal">
          <PlusOutlined />
          发送通知
        </a-button>
        <a-button @click="loadNotifications" :loading="loading">
          <ReloadOutlined />
          刷新
        </a-button>
      </div>
    </div>

    <a-table
      :columns="[
        { title: '标题', dataIndex: 'title', width: 200 },
        { title: '内容', dataIndex: 'content', ellipsis: true },
        { title: '发送者', dataIndex: 'senderName', width: 100 },
        { title: '范围', dataIndex: 'targetInfo', width: 100 },
        { title: '状态', dataIndex: 'isRead', width: 80, align: 'center' },
        { title: '发送时间', dataIndex: 'sendTime', width: 170 },
        { title: '操作', key: 'action', width: 140, align: 'center' }
      ]"
      :data-source="notifications"
      :loading="loading"
      :pagination="{
        current: currentPage,
        pageSize: pageSize,
        total: total,
        showTotal: (t: number) => `共 ${t} 条通知`,
        showSizeChanger: true
      }"
      row-key="id"
      :row-class-name="(record: NotificationItem) => record.isRead === 0 ? 'row-unread' : ''"
      @change="handleTableChange"
    >
      <template #bodyCell="{ column, record }">
        <template v-if="column.dataIndex === 'title'">
          <span :class="{ 'text-bold': record.isRead === 0 }">
            <BellOutlined v-if="record.isRead === 0" style="color: #faad14; margin-right: 4px" />
            {{ record.title }}
          </span>
        </template>
        <template v-if="column.dataIndex === 'targetInfo'">
          <a-tag :color="getSendTypeColor(record.sendType)">
            {{ getSendTypeLabel(record.sendType) }}
          </a-tag>
          {{ record.targetInfo }}
        </template>
        <template v-if="column.dataIndex === 'isRead'">
          <a-tag :color="record.isRead === 0 ? 'orange' : 'default'">
            {{ record.isRead === 0 ? '未读' : '已读' }}
          </a-tag>
        </template>
        <template v-if="column.dataIndex === 'sendTime'">
          {{ record.sendTime?.replace('T', ' ') }}
        </template>
        <template v-if="column.key === 'action'">
          <a-space>
            <a-button v-if="record.isRead === 0" type="link" size="small" @click="markAsRead(record.id)">
              <CheckOutlined /> 已读
            </a-button>
            <a-popconfirm title="确认删除？" @confirm="deleteNotification(record.id)">
              <a-button type="link" size="small" danger>
                <DeleteOutlined />
              </a-button>
            </a-popconfirm>
          </a-space>
        </template>
      </template>
    </a-table>

    <a-modal
      v-model:open="sendModalVisible"
      title="发送通知"
      :confirm-loading="sendLoading"
      @ok="handleSend"
      width="560px"
    >
      <a-form :label-col="{ span: 5 }" :wrapper-col="{ span: 18 }">
        <a-form-item label="通知标题" required>
          <a-input v-model:value="sendForm.title" placeholder="请输入通知标题" :maxlength="100" />
        </a-form-item>

        <a-form-item label="通知内容" required>
          <a-textarea v-model:value="sendForm.content" placeholder="请输入通知内容" :rows="4" :maxlength="2000" />
        </a-form-item>

        <a-form-item label="发送范围" required>
          <a-radio-group v-model:value="sendForm.sendType">
            <a-radio :value="1" v-if="userStore.userInfo?.roles?.includes('SUPER_ADMIN')">全体用户</a-radio>
            <a-radio :value="2">指定分组</a-radio>
            <a-radio :value="3">指定用户</a-radio>
          </a-radio-group>
        </a-form-item>

        <a-form-item v-if="sendForm.sendType === 2" label="选择分组">
          <a-select v-model:value="sendForm.targetGroupId" placeholder="请选择分组" style="width: 100%">
            <a-select-option v-for="g in groups" :key="g.id" :value="g.id">
              {{ g.groupName }}
            </a-select-option>
          </a-select>
        </a-form-item>

        <a-form-item v-if="sendForm.sendType === 3" label="选择用户">
          <a-select
            v-model:value="sendForm.targetUserIds"
            mode="multiple"
            placeholder="请选择用户"
            style="width: 100%"
            :filter-option="(input: string, option: any) => option.label?.toLowerCase().includes(input.toLowerCase())"
          >
            <a-select-option
              v-for="u in users"
              :key="u.id"
              :value="u.id"
              :label="u.username"
            >
              {{ u.username }} ({{ u.email }})
            </a-select-option>
          </a-select>
        </a-form-item>
      </a-form>
    </a-modal>
  </div>
</template>

<style scoped>
.notifications-page {
  height: 100%;
}

.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
}

.header-left h3 {
  margin: 0;
  font-size: 18px;
}

.header-right {
  display: flex;
  gap: 8px;
}

.text-bold {
  font-weight: 600;
}

:deep(.row-unread) {
  background: #fffbe6;
}

:deep(.row-unread:hover > td) {
  background: #fff1b8 !important;
}
</style>
