<script setup lang="ts">
import { ref, onMounted, onBeforeUnmount, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ArrowLeftOutlined, ExpandOutlined, CompressOutlined } from '@ant-design/icons-vue'

const route = useRoute()
const router = useRouter()

const hostId = computed(() => route.params.hostId as string)
const hostName = computed(() => (route.query.name as string) || '未知主机')

const wsState = ref<'disconnected' | 'connecting' | 'connected'>('disconnected')
const hasFrame = ref(false)
const fps = ref(0)
const isFullscreen = ref(false)
const isLive = ref(false)

const canvasRef = ref<HTMLCanvasElement | null>(null)

let ws: WebSocket | null = null
let frameCount = 0
let fpsTimer: ReturnType<typeof setInterval> | null = null
let reconnectTimer: ReturnType<typeof setTimeout> | null = null
let liveCheckTimer: ReturnType<typeof setInterval> | null = null
let lastFrameTime = 0
let pendingData: ArrayBuffer | null = null
let renderRAF: number | null = null
let destroyed = false

function getWsUrl() {
  const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:'
  const host = window.location.host
  const token = localStorage.getItem('token')
  return `${protocol}//${host}/ws/screen/${hostId.value}?token=${token}`
}

function connect() {
  if (destroyed) return
  if (ws && ws.readyState === WebSocket.OPEN) return

  wsState.value = 'connecting'
  const url = getWsUrl()
  ws = new WebSocket(url)
  ws.binaryType = 'arraybuffer'

  ws.onopen = () => {
    if (destroyed) {
      ws?.close()
      return
    }
    wsState.value = 'connected'
    frameCount = 0
    lastFrameTime = Date.now()
    startFpsCounter()
    startLiveChecker()
  }

  ws.onmessage = (event) => {
    if (destroyed) return
    if (event.data instanceof ArrayBuffer) {
      lastFrameTime = Date.now()
      isLive.value = true
      frameCount++
      pendingData = event.data
      if (!renderRAF) {
        renderRAF = requestAnimationFrame(doRender)
      }
    }
  }

  ws.onclose = () => {
    wsState.value = 'disconnected'
    isLive.value = false
    stopFpsCounter()
    stopLiveChecker()
    if (!destroyed) {
      reconnectTimer = setTimeout(() => {
        if (!destroyed) {
          connect()
        }
      }, 3000)
    }
  }

  ws.onerror = () => {
  }
}

function doRender() {
  renderRAF = null
  if (destroyed) return
  const data = pendingData
  pendingData = null
  if (!data) return

  const blob = new Blob([data], { type: 'image/jpeg' })
  const url = URL.createObjectURL(blob)
  const img = new Image()
  img.onload = () => {
    if (destroyed) {
      URL.revokeObjectURL(url)
      return
    }
    const canvas = canvasRef.value
    if (canvas) {
      if (canvas.width !== img.naturalWidth || canvas.height !== img.naturalHeight) {
        canvas.width = img.naturalWidth
        canvas.height = img.naturalHeight
      }
      const ctx = canvas.getContext('2d')
      if (ctx) {
        ctx.drawImage(img, 0, 0)
      }
    }
    hasFrame.value = true
    URL.revokeObjectURL(url)

    if (pendingData && !destroyed) {
      renderRAF = requestAnimationFrame(doRender)
    }
  }
  img.onerror = () => {
    URL.revokeObjectURL(url)
  }
  img.src = url
}

function disconnect() {
  destroyed = true

  if (reconnectTimer) {
    clearTimeout(reconnectTimer)
    reconnectTimer = null
  }
  if (ws) {
    ws.onclose = null
    ws.onopen = null
    ws.onmessage = null
    ws.onerror = null
    ws.close()
    ws = null
  }
  if (renderRAF) {
    cancelAnimationFrame(renderRAF)
    renderRAF = null
  }
  pendingData = null
  wsState.value = 'disconnected'
  isLive.value = false
  stopFpsCounter()
  stopLiveChecker()
}

function startFpsCounter() {
  stopFpsCounter()
  fpsTimer = setInterval(() => {
    fps.value = frameCount
    frameCount = 0
  }, 1000)
}

function stopFpsCounter() {
  if (fpsTimer) {
    clearInterval(fpsTimer)
    fpsTimer = null
  }
  fps.value = 0
}

function startLiveChecker() {
  stopLiveChecker()
  liveCheckTimer = setInterval(() => {
    if (Date.now() - lastFrameTime > 10000) {
      isLive.value = false
    }
  }, 3000)
}

function stopLiveChecker() {
  if (liveCheckTimer) {
    clearInterval(liveCheckTimer)
    liveCheckTimer = null
  }
}

function goBack() {
  router.push('/hosts')
}

function toggleFullscreen() {
  const container = document.querySelector('.screen-container')
  if (!container) return

  if (!document.fullscreenElement) {
    container.requestFullscreen().then(() => {
      isFullscreen.value = true
    }).catch(() => {})
  } else {
    document.exitFullscreen().then(() => {
      isFullscreen.value = false
    }).catch(() => {})
  }
}

const statusTag = computed(() => {
  if (wsState.value === 'disconnected') return { text: '已断开', color: 'red' }
  if (wsState.value === 'connecting') return { text: '连接中...', color: 'orange' }
  if (!isLive.value) return { text: '等待数据', color: 'orange' }
  return { text: '接收中', color: 'green' }
})

onMounted(() => {
  connect()
  document.addEventListener('fullscreenchange', () => {
    isFullscreen.value = !!document.fullscreenElement
  })
})

onBeforeUnmount(() => {
  disconnect()
})
</script>

<template>
  <div class="screen-page">
    <div class="screen-toolbar">
      <div class="toolbar-left">
        <a-button @click="goBack">
          <ArrowLeftOutlined />
          返回主机列表
        </a-button>
        <span class="host-name">{{ hostName }}</span>
      </div>
      <div class="toolbar-right">
        <a-tag :color="statusTag.color">{{ statusTag.text }}</a-tag>
        <a-tag v-if="isLive" color="blue">{{ fps }} FPS</a-tag>
        <a-button
          size="small"
          @click="toggleFullscreen"
          :title="isFullscreen ? '退出全屏' : '全屏'"
        >
          <CompressOutlined v-if="isFullscreen" />
          <ExpandOutlined v-else />
        </a-button>
      </div>
    </div>

    <div class="screen-container">
      <div v-if="wsState === 'disconnected'" class="screen-placeholder">
        <p>连接已断开</p>
        <a-button type="primary" @click="connect">重新连接</a-button>
      </div>
      <div v-else-if="wsState === 'connecting'" class="screen-placeholder">
        <a-spin size="large" />
        <p>正在连接...</p>
      </div>
      <div v-else-if="!hasFrame" class="screen-placeholder">
        <a-spin size="large" />
        <p>等待画面数据...</p>
      </div>
      <canvas
        v-show="hasFrame"
        ref="canvasRef"
        class="screen-canvas"
      />
      <div v-if="hasFrame && !isLive" class="screen-overlay">
        <span>等待数据中...</span>
      </div>
    </div>
  </div>
</template>

<style scoped>
.screen-page {
  display: flex;
  flex-direction: column;
  height: 100%;
  min-height: 0;
  overflow: hidden;
}

.screen-toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 8px 0;
  margin-bottom: 12px;
  border-bottom: 1px solid #f0f0f0;
  flex-shrink: 0;
}

.toolbar-left {
  display: flex;
  align-items: center;
  gap: 12px;
}

.toolbar-right {
  display: flex;
  align-items: center;
  gap: 8px;
}

.host-name {
  font-size: 16px;
  font-weight: 500;
}

.screen-container {
  flex: 1;
  aspect-ratio: 16 / 9;
  max-width: 100%;
  margin: 0 auto;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #000;
  border-radius: 8px;
  overflow: hidden;
  min-height: 0;
  position: relative;
}

.screen-container:fullscreen {
  border-radius: 0;
}

.screen-placeholder {
  position: absolute;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 12px;
  color: #999;
  font-size: 16px;
}

.screen-overlay {
  position: absolute;
  top: 12px;
  left: 50%;
  transform: translateX(-50%);
  background: rgba(0, 0, 0, 0.6);
  color: #faad14;
  padding: 4px 16px;
  border-radius: 4px;
  font-size: 13px;
  pointer-events: none;
}

.screen-canvas {
  position: absolute;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
  object-fit: contain;
}
</style>
