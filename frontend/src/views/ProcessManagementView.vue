<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { message } from 'ant-design-vue'
import { PlusOutlined, ReloadOutlined, DeleteOutlined } from '@ant-design/icons-vue'
import request from '@/utils/request'
import { useUserStore } from '@/stores/user'

const userStore = useUserStore()

const isAdmin = computed(() => {
  const roles = userStore.userInfo?.roles || []
  return roles.includes('SUPER_ADMIN') || roles.includes('GROUP_ADMIN')
})

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

const loading = ref(false)
const ruleData = ref<ProcessRule[]>([])
const groups = ref<GroupOption[]>([])
const filterGroup = ref<number | undefined>(undefined)
const filterType = ref<string | undefined>(undefined)
const searchText = ref('')

const addModalVisible = ref(false)
const addForm = ref({
  groupId: undefined as number | undefined,
  processName: '',
  ruleType: 'BLACKLIST' as 'WHITELIST' | 'BLACKLIST',
  description: ''
})

const columns = [
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
  if (searchText.value) {
    const keyword = searchText.value.toLowerCase()
    data = data.filter(r =>
      r.processName.toLowerCase().includes(keyword) ||
      (r.groupName || '').toLowerCase().includes(keyword)
    )
  }
  return data
})

async function fetchRules() {
  loading.value = true
  try {
    const data = await request.get('/process-rules')
    ruleData.value = data.data
  } catch {
    // error handled by interceptor
  } finally {
    loading.value = false
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

async function handleAdd() {
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

async function handleDelete(id: number, groupId: number) {
  try {
    await request.delete(`/groups/${groupId}/process-rules/${id}`)
    message.success('规则已删除')
    fetchRules()
  } catch {
    // error handled by interceptor
  }
}

onMounted(() => {
  fetchRules()
  fetchGroups()
})
</script>

<template>
  <div class="process-management">
    <div class="page-header">
      <a-space>
        <a-input-search
          v-model:value="searchText"
          placeholder="搜索进程名或分组"
          style="width: 220px"
          allow-clear
        />
        <a-select
          v-model:value="filterGroup"
          placeholder="按分组筛选"
          style="width: 160px"
          allow-clear
        >
          <a-select-option v-for="g in groups" :key="g.id" :value="g.id">
            {{ g.groupName }}
          </a-select-option>
        </a-select>
        <a-select
          v-model:value="filterType"
          placeholder="按类型筛选"
          style="width: 140px"
          allow-clear
        >
          <a-select-option value="BLACKLIST">黑名单</a-select-option>
          <a-select-option value="WHITELIST">白名单</a-select-option>
        </a-select>
      </a-space>
      <a-space>
        <a-button @click="fetchRules" :loading="loading">
          <ReloadOutlined />
          刷新
        </a-button>
        <a-button v-if="isAdmin" type="primary" @click="showAddModal">
          <PlusOutlined />
          添加规则
        </a-button>
      </a-space>
    </div>

    <a-table
      :columns="columns"
      :data-source="filteredRuleData"
      :loading="loading"
      :pagination="{ pageSize: 20, showTotal: (t: number) => `共 ${t} 条规则` }"
      row-key="id"
      size="small"
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
          <a-popconfirm title="确定删除该规则？" ok-text="确定" cancel-text="取消" @confirm="handleDelete(record.id, record.groupId)">
            <a-button type="link" size="small" danger>
              <DeleteOutlined />
            </a-button>
          </a-popconfirm>
        </template>
      </template>
    </a-table>

    <a-modal v-model:open="addModalVisible" title="添加进程规则" @ok="handleAdd" destroy-on-close>
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
.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
}
</style>
