<script setup lang="ts">
import { ref } from 'vue'
import TheWelcome from '../components/TheWelcome.vue'

const count = ref(0)
</script>

<template>
  <main>
    <a-row :gutter="16">
      <a-col :span="8">
        <a-card title="欢迎使用" :bordered="false">
          <p>这是一个使用 Ant Design Vue 的项目</p>
          <a-button type="primary" @click="count++">点击次数: {{ count }}</a-button>
        </a-card>
      </a-col>
      <a-col :span="8">
        <a-card title="功能特性" :bordered="false">
          <ul>
            <li>Vue 3 + TypeScript</li>
            <li>Ant Design Vue</li>
            <li>Vite 构建工具</li>
            <li>Vue Router</li>
            <li>Pinia 状态管理</li>
          </ul>
        </a-card>
      </a-col>
      <a-col :span="8">
        <a-card title="快速开始" :bordered="false">
          <p>运行 npm install 安装依赖</p>
          <p>运行 npm run dev 启动开发服务器</p>
          <a-space>
            <a-button type="primary">开始使用</a-button>
            <a-button>了解更多</a-button>
          </a-space>
        </a-card>
      </a-col>
    </a-row>
    <div style="margin-top: 50px">
      <TheWelcome />
    </div>
  </main>
</template>
