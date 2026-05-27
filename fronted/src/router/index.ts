import { createRouter, createWebHistory } from 'vue-router'
import MainLayout from '../layouts/MainLayout.vue'

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    {
      path: '/login',
      name: 'login',
      component: () => import('../views/LoginView.vue'),
      meta: { requiresAuth: false }
    },
    {
      path: '/register',
      name: 'register',
      component: () => import('../views/RegisterView.vue'),
      meta: { requiresAuth: false }
    },
    {
      path: '/',
      component: MainLayout,
      meta: { requiresAuth: true },
      children: [
        {
          path: '',
          redirect: '/dashboard'
        },
        {
          path: 'dashboard',
          name: 'dashboard',
          component: () => import('../views/DashboardView.vue'),
          meta: { requiresAuth: true, title: '仪表盘' }
        },
        {
          path: 'hosts',
          name: 'hosts',
          component: () => import('../views/HostsView.vue'),
          meta: { requiresAuth: true, title: '主机管理' }
        },
        {
          path: 'hosts/:hostId/screen',
          name: 'screen',
          component: () => import('../views/ScreenView.vue'),
          meta: { requiresAuth: true, title: '屏幕监控' }
        },
        {
          path: 'users',
          name: 'users',
          component: () => import('../views/UsersView.vue'),
          meta: { requiresAuth: true, title: '用户管理' }
        },
        {
          path: 'groups',
          name: 'groups',
          component: () => import('../views/GroupsView.vue'),
          meta: { requiresAuth: true, title: '组织管理' }
        },
        {
          path: 'notifications',
          name: 'notifications',
          component: () => import('../views/NotificationsView.vue'),
          meta: { requiresAuth: true, title: '通知管理' }
        },
        {
          path: 'settings',
          name: 'settings',
          component: () => import('../views/SettingsView.vue'),
          meta: { requiresAuth: true, title: '系统设置' }
        }
      ]
    }
  ],
})

router.beforeEach((to, _from, next) => {
  const token = localStorage.getItem('token')
  if (to.meta.requiresAuth && !token) {
    next({ name: 'login' })
  } else if ((to.name === 'login' || to.name === 'register') && token) {
    next({ path: '/dashboard' })
  } else {
    next()
  }
})

export default router
