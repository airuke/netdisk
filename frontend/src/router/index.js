import { createRouter, createWebHistory } from 'vue-router'
import { pinia } from '@/stores'
import { useAuthStore } from '@/stores/auth'

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),

  routes: [
    {
      path: '/',
      redirect: '/drive',
    },
    {
      path: '/login',
      name: 'login',
      component: () => import('@/views/AuthView.vue'),
    },
    {
      path: '/register',
      name: 'register',
      component: () => import('@/views/AuthView.vue'),
    },
    {
      path: '/drive',
      name: 'drive',
      component: () => import('@/views/DriveView.vue'),
      meta: {
        requiresAuth: true,
      },
    },
    {
      path: '/drive/shares', name: 'shares',
      component: () => import('@/views/SharesView.vue'), meta: { requiresAuth: true },
    },
    {
      path: '/s/:code', name: 'public-share',
      component: () => import('@/views/PublicShareView.vue'),
    },
    {
      path: '/:pathMatch(.*)*',
      redirect: '/drive',
    },
  ],
})

router.beforeEach((to) => {
  const auth = useAuthStore(pinia)

  if (to.meta.requiresAuth && !auth.isLoggedIn) {
    return {
      name: 'login',
      query: {
        redirect: to.fullPath,
      },
    }
  }

  if (['login', 'register'].includes(to.name) && auth.isLoggedIn) {
    return { name: 'drive' }
  }
})

router.afterEach((to) => {
  const title = { login: '登录', register: '创建账号', drive: '我的云盘', shares: '我的分享', 'public-share': '文件分享' }[to.name] || '个人云盘'
  document.title = `${title} · 个人云盘`
})

export default router
