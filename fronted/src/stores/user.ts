import { ref, computed } from 'vue'
import { defineStore } from 'pinia'
import request from '@/utils/request'

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

  async function login(username: string, password: string, captchaKey?: string, captchaAnswer?: string): Promise<boolean> {
    const data: any = { username, password }
    if (captchaKey && captchaAnswer) {
      data.captchaKey = captchaKey
      data.captchaAnswer = captchaAnswer
    }
    const res = await request.post('/auth/login', data)
    setToken(res.data.token)
    setUserInfo(res.data.user)
    return true
  }

  async function register(username: string, email: string, password: string): Promise<boolean> {
    await request.post('/auth/register', { username, email, password })
    return true
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
