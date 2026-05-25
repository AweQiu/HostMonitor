import { ref, computed } from 'vue'
import { defineStore } from 'pinia'

export interface UserInfo {
  id: number
  username: string
  email: string
  avatar: string | null
  status: number
  groupId: number | null
  roles: string[]
}

export const useUserStore = defineStore('user', () => {
  const token = ref<string>(localStorage.getItem('token') || '')
  const userInfo = ref<UserInfo | null>(null)

  const isLoggedIn = computed(() => !!token.value)

  function setToken(newToken: string) {
    token.value = newToken
    localStorage.setItem('token', newToken)
  }

  function setUserInfo(info: UserInfo) {
    userInfo.value = info
  }

  function logout() {
    token.value = ''
    userInfo.value = null
    localStorage.removeItem('token')
  }

  async function login(username: string, password: string): Promise<boolean> {
    try {
      const response = await fetch('/api/auth/login', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ username, password })
      })
      const data = await response.json()
      if (data.code === 200) {
        setToken(data.data.token)
        setUserInfo(data.data.user)
        return true
      }
      throw new Error(data.message || '登录失败')
    } catch (error: any) {
      throw new Error(error.message || '网络错误，请稍后重试')
    }
  }

  async function register(username: string, email: string, password: string): Promise<boolean> {
    try {
      const response = await fetch('/api/auth/register', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ username, email, password })
      })
      const data = await response.json()
      if (data.code === 200) {
        return true
      }
      throw new Error(data.message || '注册失败')
    } catch (error: any) {
      throw new Error(error.message || '网络错误，请稍后重试')
    }
  }

  return {
    token,
    userInfo,
    isLoggedIn,
    setToken,
    setUserInfo,
    logout,
    login,
    register
  }
})
