<script setup lang="ts">
import { ref, watch, h, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { useTabsStore, type TabItem } from '@/stores/tabs'
import request from '@/utils/request'
import {
  DashboardOutlined,
  DesktopOutlined,
  TeamOutlined,
  SettingOutlined,
  LogoutOutlined,
  UserOutlined,
  MenuFoldOutlined,
  MenuUnfoldOutlined,
  ReloadOutlined,
  CloseOutlined,
  GroupOutlined
} from '@ant-design/icons-vue'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const tabsStore = useTabsStore()

const collapsed = ref(false)
const selectedKeys = ref<string[]>(['dashboard'])

const menuItems = [
  { key: 'dashboard', icon: () => h(DashboardOutlined), label: '仪表盘', path: '/dashboard' },
  { key: 'hosts', icon: () => h(DesktopOutlined), label: '主机管理', path: '/hosts' },
  { key: 'users', icon: () => h(TeamOutlined), label: '用户管理', path: '/users' },
  { key: 'groups', icon: () => h(GroupOutlined), label: '组织管理', path: '/groups' },
  { key: 'settings', icon: () => h(SettingOutlined), label: '系统设置', path: '/settings' }
]

const tabKeyMap: Record<string, string> = {
  '/dashboard': 'dashboard',
  '/hosts': 'hosts',
  '/users': 'users',
  '/groups': 'groups',
  '/settings': 'settings'
}

const tabTitleMap: Record<string, string> = {
  dashboard: '仪表盘',
  hosts: '主机管理',
  users: '用户管理',
  groups: '组织管理',
  settings: '系统设置'
}

watch(
  () => route.path,
  (path) => {
    const key = tabKeyMap[path]
    if (key) {
      selectedKeys.value = [key]
      const tab: TabItem = {
        key,
        title: tabTitleMap[key] || key,
        path,
        closable: key !== 'dashboard'
      }
      tabsStore.addTab(tab)
    }
  },
  { immediate: true }
)

async function loadUserInfo() {
  if (userStore.token && !userStore.userInfo) {
    try {
      const data = await request.get('/users/me')
      userStore.setUserInfo({
        id: data.data.id,
        username: data.data.username,
        email: data.data.email,
        avatar: data.data.avatar,
        status: data.data.status,
        groupId: data.data.groupId,
        roles: data.data.roles
      })
    } catch {
      // ignore
    }
  }
}

onMounted(() => {
  loadUserInfo()
})

function handleMenuClick({ key }: { key: string }) {
  const item = menuItems.find(m => m.key === key)
  if (item) {
    tabsStore.addTab({
      key: item.key,
      title: tabTitleMap[item.key] || item.label,
      path: item.path,
      closable: item.key !== 'dashboard'
    })
  }
}

function handleTabChange(key: string) {
  tabsStore.setActiveKey(key)
}

function handleTabEdit(targetKey: string | undefined, action: 'add' | 'remove') {
  if (action === 'remove' && targetKey) {
    tabsStore.removeTab(targetKey)
  }
}

function handleLogout() {
  userStore.logout()
  router.push('/login')
}

function handleReload() {
  router.replace({ path: '/redirect' + route.path })
}
</script>

<template>
  <a-layout class="main-layout">
    <a-layout-header class="top-bar">
      <div class="top-bar-left">
        <span class="trigger" @click="collapsed = !collapsed">
          <MenuUnfoldOutlined v-if="collapsed" />
          <MenuFoldOutlined v-else />
        </span>
        <div class="brand">
          <img src="@/assets/logo.svg" alt="Logo" class="brand-logo" />
          <span class="brand-name" v-show="!collapsed">HostMonitor</span>
        </div>
      </div>
      <div class="top-bar-right">
        <a-tooltip title="刷新当前页">
          <span class="top-bar-action" @click="handleReload">
            <ReloadOutlined />
          </span>
        </a-tooltip>
        <a-dropdown>
          <span class="user-info">
            <a-avatar size="small" style="background-color: #1890ff">
              <template #icon><UserOutlined /></template>
            </a-avatar>
            <span class="username">{{ userStore.userInfo?.username || '用户' }}</span>
          </span>
          <template #overlay>
            <a-menu>
              <a-menu-item key="profile">
                <UserOutlined style="margin-right: 8px" />
                个人中心
              </a-menu-item>
              <a-menu-divider />
              <a-menu-item key="logout" @click="handleLogout">
                <LogoutOutlined style="margin-right: 8px" />
                退出登录
              </a-menu-item>
            </a-menu>
          </template>
        </a-dropdown>
      </div>
    </a-layout-header>

    <a-layout class="body-layout">
      <a-layout-sider
        v-model:collapsed="collapsed"
        :trigger="null"
        collapsible
        :width="220"
        :collapsed-width="64"
        class="sidebar"
        theme="dark"
      >
        <a-menu
          v-model:selectedKeys="selectedKeys"
          theme="dark"
          mode="inline"
          @click="handleMenuClick"
        >
          <a-menu-item v-for="item in menuItems" :key="item.key">
            <component :is="item.icon" />
            <span>{{ item.label }}</span>
          </a-menu-item>
        </a-menu>
      </a-layout-sider>

      <a-layout class="content-layout">
        <div class="tab-bar">
          <a-tabs
            v-model:activeKey="tabsStore.activeKey"
            type="editable-card"
            hide-add
            size="small"
            @change="handleTabChange"
            @edit="handleTabEdit"
          >
            <a-tab-pane
              v-for="tab in tabsStore.tabs"
              :key="tab.key"
              :tab="tab.title"
              :closable="tab.closable"
            />
          </a-tabs>
          <div class="tab-actions" v-if="tabsStore.tabs.length > 1">
            <a-dropdown>
              <span class="tab-action-btn">
                <CloseOutlined style="font-size: 12px" />
              </span>
              <template #overlay>
                <a-menu>
                  <a-menu-item key="closeOther" @click="tabsStore.closeOtherTabs(tabsStore.activeKey)">
                    关闭其他
                  </a-menu-item>
                  <a-menu-item key="closeAll" @click="tabsStore.closeAllTabs()">
                    关闭所有
                  </a-menu-item>
                </a-menu>
              </template>
            </a-dropdown>
          </div>
        </div>

        <a-layout-content class="tab-content">
          <RouterView />
        </a-layout-content>
      </a-layout>
    </a-layout>
  </a-layout>
</template>

<style scoped>
.main-layout {
  min-height: 100vh;
}

.top-bar {
  background: #001529;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 24px;
  height: 56px;
  line-height: 56px;
  box-shadow: 0 1px 4px rgba(0, 21, 41, 0.12);
  z-index: 10;
}

.top-bar-left {
  display: flex;
  align-items: center;
  gap: 16px;
}

.trigger {
  font-size: 18px;
  cursor: pointer;
  color: #fff;
  transition: color 0.3s;
  padding: 0 8px;
}

.trigger:hover {
  color: #1890ff;
}

.brand {
  display: flex;
  align-items: center;
  gap: 10px;
}

.brand-logo {
  width: 32px;
  height: 32px;
}

.brand-name {
  color: #fff;
  font-size: 18px;
  font-weight: 600;
  white-space: nowrap;
}

.top-bar-right {
  display: flex;
  align-items: center;
  gap: 16px;
}

.top-bar-action {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  border-radius: 4px;
  cursor: pointer;
  color: #fff;
  transition: background 0.3s;
}

.top-bar-action:hover {
  background: rgba(255, 255, 255, 0.1);
}

.user-info {
  display: flex;
  align-items: center;
  gap: 8px;
  cursor: pointer;
  color: #fff;
  padding: 4px 12px;
  border-radius: 4px;
  transition: background 0.3s;
}

.user-info:hover {
  background: rgba(255, 255, 255, 0.1);
}

.username {
  font-size: 14px;
}

.body-layout {
  flex: 1;
}

.sidebar {
  background: #001529;
  overflow-y: auto;
  overflow-x: hidden;
}

.sidebar :deep(.ant-menu) {
  border-right: none;
}

.content-layout {
  display: flex;
  flex-direction: column;
  background: #f0f2f5;
}

.tab-bar {
  background: #fff;
  padding: 4px 16px 0;
  display: flex;
  align-items: center;
  border-bottom: 1px solid #f0f0f0;
  min-height: 40px;
}

.tab-bar :deep(.ant-tabs) {
  flex: 1;
  min-width: 0;
}

.tab-bar :deep(.ant-tabs-nav) {
  margin-bottom: 0;
}

.tab-bar :deep(.ant-tabs-tab) {
  border-radius: 6px 6px 0 0 !important;
  border-bottom: none !important;
}

.tab-actions {
  margin-left: 8px;
  flex-shrink: 0;
}

.tab-action-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  border-radius: 4px;
  cursor: pointer;
  color: #666;
  transition: all 0.3s;
}

.tab-action-btn:hover {
  background: #f0f0f0;
  color: #1890ff;
}

.tab-content {
  flex: 1;
  margin: 16px;
  padding: 24px;
  background: #fff;
  border-radius: 8px;
  overflow: auto;
}
</style>
