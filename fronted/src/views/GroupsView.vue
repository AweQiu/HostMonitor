<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { message } from 'ant-design-vue'
import { PlusOutlined, ReloadOutlined } from '@ant-design/icons-vue'
import request from '@/utils/request'

interface GroupRecord {
  id: number
  groupName: string
  description: string | null
  memberCount: number
  adminName: string | null
  adminId: number | null
  createTime: string
}

interface UserRecord {
  id: number
  username: string
  email: string
  status: number
  roles: string[]
  groupId: number | null
  groupName: string | null
}

const columns = [
  { title: '分组名称', dataIndex: 'groupName', key: 'groupName' },
  { title: '描述', dataIndex: 'description', key: 'description', ellipsis: true },
  { title: '成员数', dataIndex: 'memberCount', key: 'memberCount', width: 80 },
  { title: '组管理员', dataIndex: 'adminName', key: 'adminName', width: 120 },
  { title: '创建时间', dataIndex: 'createTime', key: 'createTime', width: 180 },
  { title: '操作', key: 'action', width: 280 }
]

const memberColumns = [
  { title: '用户名', dataIndex: 'username', key: 'username' },
  { title: '邮箱', dataIndex: 'email', key: 'email' },
  { title: '角色', dataIndex: 'roles', key: 'roles' },
  { title: '操作', key: 'action', width: 200 }
]

const loading = ref(false)
const groupData = ref<GroupRecord[]>([])

const createModalVisible = ref(false)
const editModalVisible = ref(false)
const memberModalVisible = ref(false)
const assignAdminModalVisible = ref(false)

const createForm = ref({ groupName: '', description: '', adminId: undefined as number | undefined })
const editForm = ref({ id: 0, groupName: '', description: '' })
const currentGroupId = ref(0)
const members = ref<UserRecord[]>([])
const allUsers = ref<UserRecord[]>([])

async function fetchGroups() {
  loading.value = true
  try {
    const data = await request.get('/groups')
    groupData.value = data.data
  } catch {
    // error handled by interceptor
  } finally {
    loading.value = false
  }
}

async function fetchAllUsers() {
  try {
    const data = await request.get('/users', { params: { page: 1, size: 1000 } })
    allUsers.value = data.data.records
  } catch {
    // ignore
  }
}

function showCreateModal() {
  createForm.value = { groupName: '', description: '', adminId: undefined }
  fetchAllUsers()
  createModalVisible.value = true
}

async function handleCreate() {
  if (!createForm.value.groupName) {
    message.warning('请输入分组名称')
    return
  }
  try {
    await request.post('/groups', createForm.value)
    message.success('创建成功')
    createModalVisible.value = false
    fetchGroups()
  } catch {
    // error handled by interceptor
  }
}

function showEditModal(record: GroupRecord) {
  editForm.value = { id: record.id, groupName: record.groupName, description: record.description || '' }
  editModalVisible.value = true
}

async function handleEdit() {
  if (!editForm.value.groupName) {
    message.warning('请输入分组名称')
    return
  }
  try {
    await request.put(`/groups/${editForm.value.id}`, {
      groupName: editForm.value.groupName,
      description: editForm.value.description
    })
    message.success('更新成功')
    editModalVisible.value = false
    fetchGroups()
  } catch {
    // error handled by interceptor
  }
}

async function handleDelete(id: number) {
  try {
    await request.delete(`/groups/${id}`)
    message.success('删除成功')
    fetchGroups()
  } catch {
    // error handled by interceptor
  }
}

async function showMemberModal(record: GroupRecord) {
  currentGroupId.value = record.id
  await fetchMembers(record.id)
  await fetchAllUsers()
  memberModalVisible.value = true
}

async function fetchMembers(groupId: number) {
  try {
    const data = await request.get(`/groups/${groupId}/members`)
    members.value = data.data
  } catch {
    // ignore
  }
}

async function addMember(userId: number) {
  try {
    await request.post(`/groups/${currentGroupId.value}/members/${userId}`)
    message.success('添加成功')
    fetchMembers(currentGroupId.value)
    fetchAllUsers()
    fetchGroups()
  } catch {
    // error handled by interceptor
  }
}

async function removeMember(userId: number) {
  try {
    await request.delete(`/groups/${currentGroupId.value}/members/${userId}`)
    message.success('移出成功')
    fetchMembers(currentGroupId.value)
    fetchAllUsers()
    fetchGroups()
  } catch {
    // error handled by interceptor
  }
}

function showAssignAdminModal(record: GroupRecord) {
  currentGroupId.value = record.id
  fetchAllUsers()
  assignAdminModalVisible.value = true
}

async function assignAdmin(userId: number) {
  try {
    await request.post(`/groups/${currentGroupId.value}/admin/${userId}`)
    message.success('指定组管理员成功')
    assignAdminModalVisible.value = false
    fetchGroups()
  } catch {
    // error handled by interceptor
  }
}

async function revokeAdmin(groupId: number, userId: number) {
  try {
    await request.delete(`/groups/${groupId}/admin/${userId}`)
    message.success('撤销组管理员成功')
    fetchGroups()
  } catch {
    // error handled by interceptor
  }
}

const roleNameMap: Record<string, string> = {
  SUPER_ADMIN: '超级管理员',
  GROUP_ADMIN: '组管理员',
  USER: '普通用户'
}

const roleColorMap: Record<string, string> = {
  SUPER_ADMIN: 'red',
  GROUP_ADMIN: 'orange',
  USER: 'blue'
}

const availableUsers = ref<UserRecord[]>([])

function filterAvailableUsers() {
  const memberIds = new Set(members.value.map(m => m.id))
  availableUsers.value = allUsers.value.filter(u =>
    !memberIds.has(u.id) && !u.roles.includes('SUPER_ADMIN') && u.groupId === null
  )
}

onMounted(() => {
  fetchGroups()
})
</script>

<template>
  <div class="groups-page">
    <div class="page-header">
      <span></span>
      <a-space>
        <a-button @click="fetchGroups">
          <ReloadOutlined />
          刷新
        </a-button>
        <a-button type="primary" @click="showCreateModal">
          <PlusOutlined />
          创建分组
        </a-button>
      </a-space>
    </div>

    <a-table
      :columns="columns"
      :data-source="groupData"
      :loading="loading"
      :pagination="false"
      row-key="id"
    >
      <template #bodyCell="{ column, record }">
        <template v-if="column.key === 'createTime'">
          {{ record.createTime ? record.createTime.replace('T', ' ').substring(0, 19) : '' }}
        </template>
        <template v-if="column.key === 'adminName'">
          <span v-if="record.adminName">
            {{ record.adminName }}
            <a-button type="link" size="small" danger @click="revokeAdmin(record.id, record.adminId)">撤销</a-button>
          </span>
          <span v-else style="color: #999">
            未指定
            <a-button type="link" size="small" @click="showAssignAdminModal(record)">指定</a-button>
          </span>
        </template>
        <template v-if="column.key === 'action'">
          <a-space>
            <a-button type="link" size="small" @click="showMemberModal(record)">成员管理</a-button>
            <a-button type="link" size="small" @click="showEditModal(record)">编辑</a-button>
            <a-popconfirm title="删除分组将移出所有成员，确定删除？" ok-text="确定" cancel-text="取消" @confirm="handleDelete(record.id)">
              <a-button type="link" size="small" danger>删除</a-button>
            </a-popconfirm>
          </a-space>
        </template>
      </template>
    </a-table>

    <a-modal v-model:open="createModalVisible" title="创建分组" @ok="handleCreate" destroy-on-close>
      <a-form :label-col="{ span: 6 }" :wrapper-col="{ span: 16 }">
        <a-form-item label="分组名称" required>
          <a-input v-model:value="createForm.groupName" placeholder="请输入分组名称" />
        </a-form-item>
        <a-form-item label="描述">
          <a-textarea v-model:value="createForm.description" placeholder="请输入描述" :rows="3" />
        </a-form-item>
        <a-form-item label="组管理员">
          <a-select v-model:value="createForm.adminId" placeholder="可选，创建后指定" allow-clear>
            <a-select-option v-for="u in allUsers.filter(u => !u.roles.includes('SUPER_ADMIN') && !u.roles.includes('GROUP_ADMIN'))" :key="u.id" :value="u.id">
              {{ u.username }} ({{ u.email }})
            </a-select-option>
          </a-select>
        </a-form-item>
      </a-form>
    </a-modal>

    <a-modal v-model:open="editModalVisible" title="编辑分组" @ok="handleEdit" destroy-on-close>
      <a-form :label-col="{ span: 6 }" :wrapper-col="{ span: 16 }">
        <a-form-item label="分组名称" required>
          <a-input v-model:value="editForm.groupName" placeholder="请输入分组名称" />
        </a-form-item>
        <a-form-item label="描述">
          <a-textarea v-model:value="editForm.description" placeholder="请输入描述" :rows="3" />
        </a-form-item>
      </a-form>
    </a-modal>

    <a-modal v-model:open="memberModalVisible" title="成员管理" :footer="null" width="700px" destroy-on-close @cancel="fetchGroups">
      <div style="margin-bottom: 16px">
        <a-select
          placeholder="选择用户添加到分组"
          style="width: 300px"
          @change="(val: number) => addMember(val)"
          :options="allUsers.filter(u => !u.roles.includes('SUPER_ADMIN') && u.groupId === null && !members.some(m => m.id === u.id)).map(u => ({ value: u.id, label: `${u.username} (${u.email})` }))"
        />
      </div>
      <a-table
        :columns="memberColumns"
        :data-source="members"
        :pagination="false"
        row-key="id"
        size="small"
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
          <template v-if="column.key === 'action'">
            <a-popconfirm title="确定将该用户移出分组？" ok-text="确定" cancel-text="取消" @confirm="removeMember(record.id)">
              <a-button type="link" size="small" danger>移出分组</a-button>
            </a-popconfirm>
          </template>
        </template>
      </a-table>
    </a-modal>

    <a-modal v-model:open="assignAdminModalVisible" title="指定组管理员" :footer="null" destroy-on-close>
      <p style="margin-bottom: 16px; color: #666">选择一个用户指定为该分组的管理员（用户需先加入分组）：</p>
      <a-list :data-source="members" size="small">
        <template #renderItem="{ item }">
          <a-list-item>
            <a-list-item-meta :title="item.username" :description="item.email" />
            <template #actions>
              <a-button
                v-if="!item.roles.includes('GROUP_ADMIN')"
                type="link"
                size="small"
                @click="assignAdmin(item.id)"
              >
                指定为管理员
              </a-button>
              <a-tag v-else color="orange">当前管理员</a-tag>
            </template>
          </a-list-item>
        </template>
      </a-list>
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
</style>
