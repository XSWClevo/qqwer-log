import { createRouter, createWebHistory } from 'vue-router'
import type { RouteRecordRaw } from 'vue-router'
import { useAuthStore } from '@/store/auth'

const routes: RouteRecordRaw[] = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/login/Index.vue'),
    meta: { requiresAuth: false }
  },
  {
    path: '/',
    name: 'Dashboard',
    component: () => import('@/views/dashboard/Index.vue'),
    meta: { requiresAuth: true }
  },
  {
    path: '/log-search',
    name: 'LogSearch',
    component: () => import('@/views/log-search/Index.vue'),
    meta: { requiresAuth: true }
  },
  {
    path: '/agent',
    name: 'Agent',
    component: () => import('@/views/agent/Index.vue'),
    meta: { requiresAuth: true }
  },
  {
    path: '/trend-analysis',
    name: 'TrendAnalysis',
    component: () => import('@/views/trend-analysis/Index.vue'),
    meta: { requiresAuth: true }
  },
  {
    path: '/trace-analysis',
    name: 'TraceAnalysis',
    component: () => import('@/views/trace-analysis/Index.vue'),
    meta: { requiresAuth: true }
  },
  {
    path: '/attack-classification',
    name: 'AttackClassification',
    component: () => import('@/views/attack/Index.vue'),
    meta: { requiresAuth: true }
  },
  {
    path: '/alert/rules',
    name: 'AlertRules',
    component: () => import('@/views/alert/rules/Index.vue'),
    meta: { requiresAuth: true }
  },
  {
    path: '/alert/history',
    name: 'AlertHistory',
    component: () => import('@/views/alert/history/Index.vue'),
    meta: { requiresAuth: true }
  },
  // Vector 管理路由
  {
    path: '/vector/machines',
    name: 'VectorMachines',
    component: () => import('@/views/vector/MachineList.vue'),
    meta: { requiresAuth: true }
  },
  {
    path: '/vector/configs',
    name: 'VectorConfigs',
    component: () => import('@/views/vector/ConfigList.vue'),
    meta: { requiresAuth: true }
  },
  {
    path: '/vector/visual-configs',
    name: 'VectorVisualConfigs',
    component: () => import('@/views/vector/VisualConfigList.vue'),
    meta: { requiresAuth: true }
  },
  {
    path: '/vector/visual-config/:id',
    name: 'VectorVisualConfigEditor',
    component: () => import('@/views/vector/VisualConfigEditor.vue'),
    meta: { requiresAuth: true }
  },
  {
    path: '/vector/components',
    name: 'VectorComponents',
    component: () => import('@/views/vector/ComponentLibrary.vue'),
    meta: { requiresAuth: true }
  },
  {
    path: '/vector/packages',
    name: 'VectorPackages',
    component: () => import('@/views/vector/PackageManager.vue'),
    meta: { requiresAuth: true }
  },
  {
    path: '/vector/logs',
    name: 'VectorLogs',
    component: () => import('@/views/vector/LogMonitor.vue'),
    meta: { requiresAuth: true }
  },
  {
    path: '/datasources',
    name: 'Datasources',
    component: () => import('@/views/datasource/Index.vue'),
    meta: { requiresAuth: true }
  },
  {
    path: '/log-source',
    name: 'LogSource',
    component: () => import('@/views/log-source/Index.vue'),
    meta: { requiresAuth: true }
  },
  {
    path: '/todos',
    name: 'Todos',
    component: () => import('@/views/todo/Index.vue'),
    meta: { requiresAuth: true }
  },
  {
    path: '/settings',
    name: 'Settings',
    component: () => import('@/views/settings/Index.vue'),
    meta: { requiresAuth: true }
  },
  {
    path: '/settings/database-config',
    name: 'DatabaseConfig',
    component: () => import('@/views/settings/DatabaseConfig.vue'),
    meta: { requiresAuth: true }
  },
  // 404 兜底路由
  {
    path: '/:pathMatch(.*)*',
    name: 'NotFound',
    component: () => import('@/views/error/NotFound.vue'),
    meta: { requiresAuth: false }
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

// 路由守卫
router.beforeEach((to, _from, next) => {
  const authStore = useAuthStore()

  // 需要认证的路由
  if (to.meta.requiresAuth) {
    if (authStore.isAuthenticated) {
      next()
    } else {
      next('/login')
    }
  } else {
    // 已登录用户访问登录页，跳转到首页
    if (to.path === '/login' && authStore.isAuthenticated) {
      next('/')
    } else {
      next()
    }
  }
})

export default router
