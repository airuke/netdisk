import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import { loginApi, currentUserApi } from '@/api/auth'
import { getToken, setToken, removeToken } from '@/utils/token'

export const useAuthStore = defineStore('auth', () => {
  const token = ref(getToken())
  const user = ref(null)

  const isLoggedIn = computed(() => Boolean(token.value))

  async function login(credentials) {
    const data = await loginApi(credentials)

    if (!data?.accessToken || !data?.user) {
      throw new Error('登录失败，请稍后重试')
    }

    setToken(data.accessToken)
    token.value = data.accessToken
    user.value = data.user
  }

  async function loadCurrentUser() {
    const requestedToken = token.value
    const data = await currentUserApi()

    // 请求期间退出或更换账号时，忽略旧请求返回的用户信息。
    if (token.value === requestedToken) {
      user.value = data
    }

    return data
  }

  function clearSession() {
    removeToken()
    token.value = ''
    user.value = null
  }

  return {
    token,
    user,
    isLoggedIn,
    login,
    loadCurrentUser,
    clearSession,
  }
})
