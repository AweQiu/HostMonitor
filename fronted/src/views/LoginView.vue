<script setup lang="ts">
import { reactive, ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { message } from 'ant-design-vue'
import { UserOutlined, LockOutlined, SafetyOutlined, ReloadOutlined } from '@ant-design/icons-vue'
import { useUserStore } from '@/stores/user'
import request from '@/utils/request'

const router = useRouter()
const userStore = useUserStore()
const loading = ref(false)

const captchaRequired = ref(false)
const captchaKey = ref('')
const captchaImage = ref('')
const captchaAnswer = ref('')

const formState = reactive({
  username: '',
  password: '',
  remember: true
})

async function checkLoginStatus() {
  try {
    const res = await request.get('/auth/login-status')
    captchaRequired.value = res.data.captchaRequired
    if (captchaRequired.value) {
      await refreshCaptcha()
    }
  } catch {
    // ignore
  }
}

async function refreshCaptcha() {
  try {
    const res = await request.get('/auth/captcha')
    captchaKey.value = res.data.captchaKey
    captchaImage.value = res.data.captchaImage
    captchaAnswer.value = ''
  } catch {
    message.error('获取验证码失败')
  }
}

async function handleLogin() {
  if (!formState.username) {
    message.warning('请输入用户名')
    return
  }
  if (!formState.password) {
    message.warning('请输入密码')
    return
  }
  if (captchaRequired.value && !captchaAnswer.value) {
    message.warning('请输入验证码')
    return
  }

  loading.value = true
  try {
    await userStore.login(formState.username, formState.password,
      captchaRequired.value ? captchaKey.value : undefined,
      captchaRequired.value ? captchaAnswer.value : undefined
    )
    message.success('登录成功')
    router.push('/')
  } catch (error: any) {
    captchaRequired.value = true
    await refreshCaptcha()
  } finally {
    loading.value = false
  }
}

function goToRegister() {
  router.push('/register')
}

onMounted(() => {
  checkLoginStatus()
})
</script>

<template>
  <div class="login-container">
    <div class="login-background">
      <div class="bg-shape shape-1"></div>
      <div class="bg-shape shape-2"></div>
      <div class="bg-shape shape-3"></div>
    </div>

    <div class="login-card">
      <div class="login-header">
        <div class="logo-wrapper">
          <img src="@/assets/logo.svg" alt="Logo" class="logo" />
        </div>
        <h1 class="title">HostMonitor</h1>
        <p class="subtitle">主机监控系统</p>
      </div>

      <a-form
        :model="formState"
        class="login-form"
        @finish="handleLogin"
      >
        <a-form-item name="username" :rules="[{ required: true, message: '请输入用户名' }]">
          <a-input
            v-model:value="formState.username"
            size="large"
            placeholder="用户名"
            autocomplete="username"
          >
            <template #prefix>
              <UserOutlined style="color: rgba(0, 0, 0, 0.25)" />
            </template>
          </a-input>
        </a-form-item>

        <a-form-item name="password" :rules="[{ required: true, message: '请输入密码' }]">
          <a-input-password
            v-model:value="formState.password"
            size="large"
            placeholder="密码"
            autocomplete="current-password"
          >
            <template #prefix>
              <LockOutlined style="color: rgba(0, 0, 0, 0.25)" />
            </template>
          </a-input-password>
        </a-form-item>

        <a-form-item v-if="captchaRequired" name="captcha">
          <div class="captcha-row">
            <a-input
              v-model:value="captchaAnswer"
              size="large"
              placeholder="验证码"
              style="flex: 1"
            >
              <template #prefix>
                <SafetyOutlined style="color: rgba(0, 0, 0, 0.25)" />
              </template>
            </a-input>
            <div class="captcha-image-wrapper" @click="refreshCaptcha" title="点击刷新验证码">
              <img v-if="captchaImage" :src="captchaImage" alt="captcha" class="captcha-img" />
              <a-button v-else size="large" @click.stop="refreshCaptcha">
                <ReloadOutlined />
              </a-button>
            </div>
          </div>
        </a-form-item>

        <a-form-item>
          <div class="form-actions">
            <a-checkbox v-model:checked="formState.remember">记住我</a-checkbox>
            <a class="forgot-link">忘记密码？</a>
          </div>
        </a-form-item>

        <a-form-item>
          <a-button
            type="primary"
            html-type="submit"
            size="large"
            :loading="loading"
            block
          >
            登录
          </a-button>
        </a-form-item>

        <div class="register-link">
          还没有账号？<a @click="goToRegister">立即注册</a>
        </div>
      </a-form>
    </div>
  </div>
</template>

<style scoped>
.login-container {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  position: relative;
  overflow: hidden;
}

.login-background {
  position: absolute;
  inset: 0;
  overflow: hidden;
}

.bg-shape {
  position: absolute;
  border-radius: 50%;
  opacity: 0.1;
  background: #fff;
}

.shape-1 {
  width: 600px;
  height: 600px;
  top: -200px;
  right: -100px;
}

.shape-2 {
  width: 400px;
  height: 400px;
  bottom: -150px;
  left: -100px;
}

.shape-3 {
  width: 300px;
  height: 300px;
  top: 50%;
  left: 50%;
  transform: translate(-50%, -50%);
}

.login-card {
  width: 420px;
  background: #fff;
  border-radius: 16px;
  padding: 48px 40px;
  box-shadow: 0 20px 60px rgba(0, 0, 0, 0.15);
  position: relative;
  z-index: 1;
}

.login-header {
  text-align: center;
  margin-bottom: 40px;
}

.logo-wrapper {
  display: flex;
  justify-content: center;
  margin-bottom: 16px;
}

.logo {
  width: 56px;
  height: 56px;
}

.title {
  font-size: 28px;
  font-weight: 700;
  color: #1a1a2e;
  margin: 0 0 8px;
}

.subtitle {
  font-size: 14px;
  color: #8c8c8c;
  margin: 0;
}

.login-form {
  max-width: 100%;
}

.captcha-row {
  display: flex;
  gap: 8px;
  align-items: center;
}

.captcha-image-wrapper {
  cursor: pointer;
  flex-shrink: 0;
  height: 40px;
  display: flex;
  align-items: center;
  border: 1px solid #d9d9d9;
  border-radius: 6px;
  overflow: hidden;
  transition: border-color 0.3s;
}

.captcha-image-wrapper:hover {
  border-color: #1890ff;
}

.captcha-img {
  height: 40px;
  display: block;
}

.form-actions {
  display: flex;
  justify-content: space-between;
  align-items: center;
  width: 100%;
}

.forgot-link {
  color: #1890ff;
  cursor: pointer;
  font-size: 14px;
}

.forgot-link:hover {
  color: #40a9ff;
}

.register-link {
  text-align: center;
  color: #8c8c8c;
  font-size: 14px;
}

.register-link a {
  color: #1890ff;
  cursor: pointer;
}

.register-link a:hover {
  color: #40a9ff;
}
</style>
