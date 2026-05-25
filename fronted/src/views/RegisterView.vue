<script setup lang="ts">
import { reactive, ref, computed } from 'vue'
import { useRouter } from 'vue-router'
import { message } from 'ant-design-vue'
import { UserOutlined, LockOutlined, MailOutlined } from '@ant-design/icons-vue'
import { useUserStore } from '@/stores/user'

const router = useRouter()
const userStore = useUserStore()
const loading = ref(false)

const formState = reactive({
  username: '',
  email: '',
  password: '',
  confirmPassword: '',
  agreement: false
})

const passwordValidateStatus = computed(() => {
  if (!formState.confirmPassword) return ''
  return formState.password === formState.confirmPassword ? 'success' : 'error'
})

const passwordHelp = computed(() => {
  if (!formState.confirmPassword) return ''
  return formState.password === formState.confirmPassword ? '' : '两次输入的密码不一致'
})

async function handleRegister() {
  if (!formState.username) {
    message.warning('请输入用户名')
    return
  }
  if (!formState.email) {
    message.warning('请输入邮箱')
    return
  }
  if (!formState.password) {
    message.warning('请输入密码')
    return
  }
  if (formState.password !== formState.confirmPassword) {
    message.warning('两次输入的密码不一致')
    return
  }
  if (!formState.agreement) {
    message.warning('请阅读并同意用户协议')
    return
  }

  loading.value = true
  try {
    await userStore.register(formState.username, formState.email, formState.password)
    message.success('注册成功，请登录')
    router.push('/login')
  } catch (error: any) {
    message.error(error.message || '注册失败')
  } finally {
    loading.value = false
  }
}

function goToLogin() {
  router.push('/login')
}
</script>

<template>
  <div class="register-container">
    <div class="register-background">
      <div class="bg-shape shape-1"></div>
      <div class="bg-shape shape-2"></div>
      <div class="bg-shape shape-3"></div>
    </div>

    <div class="register-card">
      <div class="register-header">
        <div class="logo-wrapper">
          <img src="@/assets/logo.svg" alt="Logo" class="logo" />
        </div>
        <h1 class="title">HostMonitor</h1>
        <p class="subtitle">创建新账号</p>
      </div>

      <a-form
        :model="formState"
        class="register-form"
        @finish="handleRegister"
      >
        <a-form-item
          name="username"
          :rules="[
            { required: true, message: '请输入用户名' },
            { min: 3, max: 20, message: '用户名长度为3-20个字符' },
            { pattern: /^[a-zA-Z0-9_]+$/, message: '用户名只能包含字母、数字和下划线' }
          ]"
        >
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

        <a-form-item
          name="email"
          :rules="[
            { required: true, message: '请输入邮箱' },
            { type: 'email', message: '请输入有效的邮箱地址' }
          ]"
        >
          <a-input
            v-model:value="formState.email"
            size="large"
            placeholder="邮箱"
            autocomplete="email"
          >
            <template #prefix>
              <MailOutlined style="color: rgba(0, 0, 0, 0.25)" />
            </template>
          </a-input>
        </a-form-item>

        <a-form-item
          name="password"
          :rules="[
            { required: true, message: '请输入密码' },
            { min: 6, max: 30, message: '密码长度为6-30个字符' }
          ]"
        >
          <a-input-password
            v-model:value="formState.password"
            size="large"
            placeholder="密码"
            autocomplete="new-password"
          >
            <template #prefix>
              <LockOutlined style="color: rgba(0, 0, 0, 0.25)" />
            </template>
          </a-input-password>
        </a-form-item>

        <a-form-item
          name="confirmPassword"
          :validate-status="passwordValidateStatus"
          :help="passwordHelp"
          :rules="[{ required: true, message: '请确认密码' }]"
        >
          <a-input-password
            v-model:value="formState.confirmPassword"
            size="large"
            placeholder="确认密码"
            autocomplete="new-password"
          >
            <template #prefix>
              <LockOutlined style="color: rgba(0, 0, 0, 0.25)" />
            </template>
          </a-input-password>
        </a-form-item>

        <a-form-item>
          <a-checkbox v-model:checked="formState.agreement">
            我已阅读并同意 <a>用户协议</a>
          </a-checkbox>
        </a-form-item>

        <a-form-item>
          <a-button
            type="primary"
            html-type="submit"
            size="large"
            :loading="loading"
            block
          >
            注册
          </a-button>
        </a-form-item>

        <div class="login-link">
          已有账号？<a @click="goToLogin">立即登录</a>
        </div>
      </a-form>
    </div>
  </div>
</template>

<style scoped>
.register-container {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  position: relative;
  overflow: hidden;
}

.register-background {
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
  left: -100px;
}

.shape-2 {
  width: 400px;
  height: 400px;
  bottom: -150px;
  right: -100px;
}

.shape-3 {
  width: 300px;
  height: 300px;
  top: 40%;
  right: 30%;
}

.register-card {
  width: 420px;
  background: #fff;
  border-radius: 16px;
  padding: 48px 40px;
  box-shadow: 0 20px 60px rgba(0, 0, 0, 0.15);
  position: relative;
  z-index: 1;
}

.register-header {
  text-align: center;
  margin-bottom: 36px;
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

.register-form {
  max-width: 100%;
}

.login-link {
  text-align: center;
  color: #8c8c8c;
  font-size: 14px;
}

.login-link a {
  color: #1890ff;
  cursor: pointer;
}

.login-link a:hover {
  color: #40a9ff;
}
</style>
