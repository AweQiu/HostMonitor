import { ref, computed } from 'vue'
import { defineStore } from 'pinia'
import { useRouter } from 'vue-router'

export interface TabItem {
  key: string
  title: string
  path: string
  closable: boolean
}

export const useTabsStore = defineStore('tabs', () => {
  const router = useRouter()

  const tabs = ref<TabItem[]>([
    { key: 'dashboard', title: '仪表盘', path: '/dashboard', closable: false }
  ])

  const activeKey = ref('dashboard')

  const activeTab = computed(() => tabs.value.find(t => t.key === activeKey.value))

  function addTab(tab: TabItem) {
    const exists = tabs.value.find(t => t.key === tab.key)
    if (!exists) {
      tabs.value.push(tab)
    }
    activeKey.value = tab.key
    router.push(tab.path)
  }

  function removeTab(key: string) {
    const index = tabs.value.findIndex(t => t.key === key)
    if (index === -1) return
    const tab = tabs.value[index]
    if (!tab || !tab.closable) return

    tabs.value.splice(index, 1)

    if (activeKey.value === key && tabs.value.length > 0) {
      const newIndex = Math.min(index, tabs.value.length - 1)
      const newTab = tabs.value[newIndex]
      if (newTab) {
        activeKey.value = newTab.key
        router.push(newTab.path)
      }
    }
  }

  function setActiveKey(key: string) {
    activeKey.value = key
    const tab = tabs.value.find(t => t.key === key)
    if (tab) {
      router.push(tab.path)
    }
  }

  function closeOtherTabs(key: string) {
    tabs.value = tabs.value.filter(t => !t.closable || t.key === key)
    const activeExists = tabs.value.find(t => t.key === activeKey.value)
    if (!activeExists && tabs.value.length > 0) {
      const firstTab = tabs.value[0]!
      activeKey.value = firstTab.key
      router.push(firstTab.path)
    }
  }

  function closeAllTabs() {
    tabs.value = tabs.value.filter(t => !t.closable)
    if (tabs.value.length > 0) {
      const firstTab = tabs.value[0]!
      activeKey.value = firstTab.key
      router.push(firstTab.path)
    }
  }

  return {
    tabs,
    activeKey,
    activeTab,
    addTab,
    removeTab,
    setActiveKey,
    closeOtherTabs,
    closeAllTabs
  }
})
