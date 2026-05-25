<script setup lang="ts">
import { RouterLink, RouterView } from 'vue-router'
import HelloWorld from './components/HelloWorld.vue'
</script>

<template>
  <a-layout style="min-height: 100vh">
    <a-layout-header style="background: #fff; padding: 0 50px; display: flex; align-items: center">
      <img alt="Vue logo" class="logo" src="@/assets/logo.svg" width="40" height="40" />
      <h1 style="margin: 0 20px; color: #1890ff">HostMonitor</h1>
      <a-menu v-model:selectedKeys="selectedKeys" theme="light" mode="horizontal" style="flex: 1; min-width: 0">
        <a-menu-item key="1">
          <RouterLink to="/">Home</RouterLink>
        </a-menu-item>
        <a-menu-item key="2">
          <RouterLink to="/about">About</RouterLink>
        </a-menu-item>
      </a-menu>
    </a-layout-header>
    <a-layout-content style="padding: 50px">
      <RouterView />
    </a-layout-content>
  </a-layout>
</template>

<script lang="ts">
import { defineComponent, ref } from 'vue'

export default defineComponent({
  setup() {
    const selectedKeys = ref(['1'])
    return {
      selectedKeys
    }
  }
})
</script>

<style scoped>
.logo {
  height: 40px;
}

h1 {
  font-size: 20px;
  font-weight: bold;
}
</style>
