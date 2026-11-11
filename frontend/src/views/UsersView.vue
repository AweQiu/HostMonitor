<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { message } from 'ant-design-vue'
import { PlusOutlined, ReloadOutlined, CopyOutlined, RetweetOutlined } from '@ant-design/icons-vue'
import { useUserStore } from '@/stores/user'
import request from '@/utils/request'

const userStore = useUserStore()

const columns = [
  { title: '用户名', dataIndex: 'username', key: 'username' },
  { title: '邮箱', dataIndex: 'email', key: 'email' },
  { title: '角色', dataIndex: 'roles', key: 'roles' },
  { title: '所属组', dataIndex: 'groupName', key: 'groupName' },
  { title: '状态', dataIndex: 'status', key: 'status' },
  { title: '创建时间', dataIndex: 'createTime', key: 'createTime' },
  { title: '操作', key: 'action', width: 160 }
]

interface UserRecord {
  id: number
  username: string
  email: string
  avatar: string | null
  status: number
  groupId: number | null
  groupName: string | null
  roles: string[]
  agentKey: string
  createTime: string
}

const loading = ref(false)
const userData = ref<UserRecord[]>([])
const total = ref(0)
const currentPage = ref(1)
const pageSize = ref(10)
const keyword = ref('')
const filterRole = ref<string | undefined>(undefined)
const filterGroup = ref<number | undefined>(undefined)

const roleFilterOptions = ref<{ value: string; label: string }[]>([])
const groupFilterOptions = ref<{ value: number; label: string }[]>([])

const isSuperAdmin = ref(false)
const isGroupAdmin = ref(false)

const createModalVisible = ref(false)
const editModalVisible = ref(false)
const resetKeyLoading = ref(false)

const createForm = ref({
  username: '',
  email: '',
  password: '',
  status: 1,
  groupId: undefined as number | undefined,
  roleKey: 'USER'
})

const editForm = ref({
  id: 0,
  email: '',
  password: '',
  status: 1,
  groupId: undefined as number | undefined,
  roleKey: 'USER',
  agentKey: '',
  username: ''
})

const roleOptions = [
  { value: 'SUPER_ADMIN', label: '超级管理员' },
  { value: 'GROUP_ADMIN', label: '组管理员' },
  { value: 'USER', label: '普通用户' }
]

const roleColorMap: Record<string, string> = {
  SUPER_ADMIN: 'red',
  GROUP_ADMIN: 'orange',
  USER: 'blue'
}

const roleNameMap: Record<string, string> = {
  SUPER_ADMIN: '超级管理员',
  GROUP_ADMIN: '组管理员',
  USER: '普通用户'
}

function maskAgentKey(key: string): string {
  if (!key || key.length <= 8) return key
  return key.substring(0, 4) + '*'.repeat(8) + key.substring(key.length - 4)
}

async function fetchUsers() {
  loading.value = true
  try {
    const params: Record<string, string> = {
      page: String(currentPage.value),
      size: String(pageSize.value)
    }
    if (keyword.value) params.keyword = keyword.value
    if (filterRole.value) params.roleKey = filterRole.value
    if (filterGroup.value != null) params.groupId = String(filterGroup.value)
    const data = await request.get('/users', { params })
    userData.value = data.data.records
    total.value = data.data.total
  } catch {
    // error handled by interceptor
  } finally {
    loading.value = false
  }
}

async function loadCurrentUserRoles() {
  try {
    const data = await request.get('/users/me')
    isSuperAdmin.value = data.data.roles.includes('SUPER_ADMIN')
    isGroupAdmin.value = data.data.roles.includes('GROUP_ADMIN')
  } catch {
    // ignore
  }
}

async function loadFilterOptions() {
  try {
    const [rolesData, groupsData] = await Promise.all([
      request.get('/users/roles'),
      request.get('/users/groups')
    ])
    roleFilterOptions.value = (rolesData.data || []).map((r: any) => ({
      value: r.roleKey, label: r.roleName
    }))
    groupFilterOptions.value = (groupsData.data || []).map((g: any) => ({
      value: g.id, label: g.groupName
    }))
  } catch {
    // ignore
  }
}

function handleFilterChange() {
  currentPage.value = 1
  fetchUsers()
}

function handleSearch() {
  currentPage.value = 1
  fetchUsers()
}

function handleTableChange(pagination: { current: number; pageSize: number }) {
  currentPage.value = pagination.current
  pageSize.value = pagination.pageSize
  fetchUsers()
}

function showCreateModal() {
  createForm.value = {
    username: '', email: '', password: '',
    status: 1, groupId: undefined, roleKey: 'USER'
  }
  createModalVisible.value = true
}

async function handleCreate() {
  if (!createForm.value.username || !createForm.value.email || !createForm.value.password) {
    message.warning('请填写完整信息')
    return
  }
  try {
    await request.post('/users', createForm.value)
    message.success('创建成功')
    createModalVisible.value = false
    fetchUsers()
  } catch {
    // error handled by interceptor
  }
}

function showEditModal(record: UserRecord) {
  editForm.value = {
    id: record.id,
    email: record.email,
    password: '',
    status: record.status,
    groupId: record.groupId ?? 0,
    roleKey: record.roles[0] || 'USER',
    agentKey: record.agentKey || '',
    username: record.username
  }
  editModalVisible.value = true
}

async function handleEdit() {
  try {
    const body: Record<string, unknown> = {
      email: editForm.value.email,
      status: editForm.value.status
    }
    if (editForm.value.password) {
      body.password = editForm.value.password
    }
    if (isSuperAdmin.value) {
      if (editForm.value.groupId === 0) {
        body.clearGroupId = true
      } else if (editForm.value.groupId !== undefined) {
        body.groupId = editForm.value.groupId
      }
    }
    await request.put(`/users/${editForm.value.id}`, body)

    if (isSuperAdmin.value) {
      await request.put(`/users/${editForm.value.id}/role`, { roleKey: editForm.value.roleKey })
    }

    message.success('更新成功')
    editModalVisible.value = false
    fetchUsers()
  } catch {
    // error handled by interceptor
  }
}

async function handleCopyKey() {
  try {
    await navigator.clipboard.writeText(editForm.value.agentKey)
    message.success('已复制到剪贴板')
  } catch {
    message.error('复制失败，请手动复制')
  }
}

function handleResetKey() {
  const username = editForm.value.username
  const m = `确定要重置用户「${username}」的密钥吗？重置后该用户的客户端将无法连接服务端，需要使用新密钥重新配置。`
  Modal.confirm({
    title: '重置密钥',
    content: m,
    okText: '确定重置',
    okType: 'danger',
    cancelText: '取消',
    onOk: doResetKey
  })
}

async function doResetKey() {
  resetKeyLoading.value = true
  try {
    const data = await request.post(`/users/${editForm.value.id}/reset-agent-key`)
    editForm.value.agentKey = data.data
    message.success('密钥已重置')
    fetchUsers()
  } catch {
    // error handled by interceptor
  } finally {
    resetKeyLoading.value = false
  }
}

async function handleDelete(id: number) {
  try {
    await request.delete(`/users/${id}`)
    message.success('删除成功')
    fetchUsers()
  } catch {
    // error handled by interceptor
  }
}

function canManageUser(record: UserRecord): boolean {
  if (isSuperAdmin.value) return true
  if (isGroupAdmin.value && !record.roles.includes('SUPER_ADMIN')) return true
  return false
}

import { Modal } from 'ant-design-vue'

onMounted(() => {
  loadCurrentUserRoles()
  loadFilterOptions()
  fetchUsers()
})
</script>

<template>
  <div class="users-page">
    <div class="page-header">
      <a-space>
        <a-input-search
          v-model:value="keyword"
          placeholder="搜索用户名或邮箱"
          style="width: 220px"
          @search="handleSearch"
          allow-clear
        />
        <a-select
          v-model:value="filterRole"
          placeholder="按角色筛选"
          style="width: 140px"
          allow-clear
          @change="handleFilterChange"
        >
          <a-select-option v-for="opt in roleFilterOptions" :key="opt.value" :value="opt.value">
            {{ opt.label }}
          </a-select-option>
        </a-select>
        <a-select
          v-if="isSuperAdmin"
          v-model:value="filterGroup"
          placeholder="按分组筛选"
          style="width: 140px"
          allow-clear
          @change="handleFilterChange"
        >
          <a-select-option v-for="opt in groupFilterOptions" :key="opt.value" :value="opt.value">
            {{ opt.label }}
          </a-select-option>
        </a-select>
      </a-space>
      <a-space>
        <a-button @click="fetchUsers">
          <ReloadOutlined />
          刷新
        </a-button>
        <a-button type="primary" @click="showCreateModal" v-if="isSuperAdmin || isGroupAdmin">
          <PlusOutlined />
          添加用户
        </a-button>
      </a-space>
    </div>

    <a-table
      :columns="columns"
      :data-source="userData"
      :loading="loading"
      :pagination="{
        current: currentPage,
        pageSize: pageSize,
        total: total,
        showTotal: (t: number) => `共 ${t} 个用户`,
        showSizeChanger: true
      }"
      row-key="id"
      @change="handleTableChange"
    >
      <template #bodyCell="{ column, record }">
        <template v-if="column.key === 'roles'">
          <a-tag
            v-for="role in record.roles"
            :key="role"
            :color="roleColorMap[role] || 'default'"
          >
            {{ roleNameMap[role] || role }}
          </a-tag>
        </template>
        <template v-if="column.key === 'groupName'">
          {{ record.groupName || '未分组' }}
        </template>
        <template v-if="column.key === 'status'">
          <a-badge :status="record.status === 1 ? 'success' : 'default'" :text="record.status === 1 ? '正常' : '禁用'" />
        </template>
        <template v-if="column.key === 'createTime'">
          {{ record.createTime ? record.createTime.replace('T', ' ').substring(0, 19) : '' }}
        </template>
        <template v-if="column.key === 'action'">
          <a-space v-if="canManageUser(record)">
            <a-button type="link" size="small" @click="showEditModal(record)">编辑</a-button>
            <a-popconfirm title="确定删除该用户？" ok-text="确定" cancel-text="取消" @confirm="handleDelete(record.id)">
              <a-button type="link" size="small" danger>删除</a-button>
            </a-popconfirm>
          </a-space>
          <span v-else style="color: #999">-</span>
        </template>
      </template>
    </a-table>

    <a-modal v-model:open="createModalVisible" title="添加用户" @ok="handleCreate" destroy-on-close>
      <a-form :label-col="{ span: 6 }" :wrapper-col="{ span: 16 }">
        <a-form-item label="用户名" required>
          <a-input v-model:value="createForm.username" placeholder="请输入用户名" />
        </a-form-item>
        <a-form-item label="邮箱" required>
          <a-input v-model:value="createForm.email" placeholder="请输入邮箱" />
        </a-form-item>
        <a-form-item label="密码" required>
          <a-input-password v-model:value="createForm.password" placeholder="请输入密码" />
        </a-form-item>
        <a-form-item label="角色">
          <a-select v-model:value="createForm.roleKey">
            <a-select-option v-for="opt in roleOptions" :key="opt.value" :value="opt.value" :disabled="!isSuperAdmin && opt.value === 'SUPER_ADMIN'">
              {{ opt.label }}
            </a-select-option>
          </a-select>
        </a-form-item>
        <a-form-item label="状态">
          <a-select v-model:value="createForm.status">
            <a-select-option :value="1">正常</a-select-option>
            <a-select-option :value="0">禁用</a-select-option>
          </a-select>
        </a-form-item>
      </a-form>
    </a-modal>

    <a-modal v-model:open="editModalVisible" title="编辑用户" @ok="handleEdit" destroy-on-close width="560px">
      <a-form :label-col="{ span: 6 }" :wrapper-col="{ span: 16 }">
        <a-form-item label="用户名">
          <a-input :value="editForm.username" disabled />
        </a-form-item>
        <a-form-item label="邮箱">
          <a-input v-model:value="editForm.email" placeholder="请输入邮箱" />
        </a-form-item>
        <a-form-item label="新密码">
          <a-input-password v-model:value="editForm.password" placeholder="留空则不修改" />
        </a-form-item>
        <a-form-item label="角色" v-if="isSuperAdmin">
          <a-select v-model:value="editForm.roleKey">
            <a-select-option v-for="opt in roleOptions" :key="opt.value" :value="opt.value" :disabled="opt.value === 'SUPER_ADMIN'">
              {{ opt.label }}
            </a-select-option>
          </a-select>
        </a-form-item>
        <a-form-item label="所属分组" v-if="isSuperAdmin">
          <a-select v-model:value="editForm.groupId" placeholder="请选择分组">
            <a-select-option :value="0">无分组</a-select-option>
            <a-select-option v-for="opt in groupFilterOptions" :key="opt.value" :value="opt.value">
              {{ opt.label }}
            </a-select-option>
          </a-select>
        </a-form-item>
        <a-form-item label="状态">
          <a-select v-model:value="editForm.status">
            <a-select-option :value="1">正常</a-select-option>
            <a-select-option :value="0">禁用</a-select-option>
          </a-select>
        </a-form-item>
        <a-form-item label="Agent Key">
          <div class="agent-key-row">
            <a-input
              :value="maskAgentKey(editForm.agentKey)"
              readonly
              class="agent-key-input"
            />
            <a-tooltip title="复制密钥">
              <a-button @click="handleCopyKey">
                <CopyOutlined />
              </a-button>
            </a-tooltip>
            <a-tooltip title="重置密钥" v-if="isSuperAdmin">
              <a-button danger @click="handleResetKey" :loading="resetKeyLoading">
                <RetweetOutlined />
              </a-button>
            </a-tooltip>
          </div>
        </a-form-item>
      </a-form>
    </a-modal>
  </div>
</template>

<style scoped>
.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
}

.agent-key-row {
  display: flex;
  gap: 8px;
  width: 100%;
}

.agent-key-input {
  flex: 1;
  font-family: 'Courier New', monospace;
  letter-spacing: 0.5px;
}
</style>
