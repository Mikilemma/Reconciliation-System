import { createRouter, createWebHistory } from 'vue-router'
import Layout from '@/layouts/Layout.vue'
import { apiService } from '@/services/api'

interface AuthProfile {
  username: string
  role?: string
}

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    {
      path: '/login',
      name: 'login',
      component: () => import('../views/LoginView.vue'),
    },
    {
      path: '/',
      component: Layout,
      redirect: '/dashboard',
      children: [
        {
          path: 'dashboard',
          name: 'dashboard',
          component: () => import('../views/DashboardView.vue'),
        },
        {
          path: 'p2p/upload',
          name: 'p2p-upload',
          component: () => import('../views/p2p/FileUploadView.vue'),
        },
        {
          path: 'p2p/reconciliation',
          name: 'p2p-reconciliation',
          component: () => import('../views/p2p/ReconciliationView.vue'),
        },
        {
          path: 'p2p/reports',
          name: 'p2p-reports',
          component: () => import('../views/p2p/ReportsView.vue'),
        },
        {
          path: 'p2p/disputes',
          name: 'p2p-disputes',
          component: () => import('../views/p2p/DisputesView.vue'),
        },
        {
          path: 'p2p/transactions',
          name: 'p2p-transactions',
          component: () => import('../views/p2p/TransactionsView.vue'),
        },
        {
          path: 'ips/transactions',
          name: 'ips-transactions',
          component: () => import('../views/ips/IpsTransactionsView.vue'),
        },
        {
          path: 'ips/participants',
          name: 'ips-participants',
          component: () => import('../views/ips/IpsParticipantsView.vue'),
        },
        {
          path: 'settings',
          name: 'settings',
          component: () => import('../views/SettingsView.vue'),
        },
      ],
    },
  ],
})

const getAuthProfile = async (): Promise<AuthProfile | null> => {
  try {
    return await apiService.get<AuthProfile>('/api/auth/me')
  } catch {
    return null
  }
}

const isAdmin = (profile: AuthProfile | null) =>
  (profile?.role || '').toString().toUpperCase() === 'ADMIN'

router.beforeEach(async (to) => {
  const profile = await getAuthProfile()
  const authed = !!profile

  if (to.path === '/login') {
    if (authed) return '/dashboard'
    return true
  }

  if (!authed) {
    return '/login'
  }

  const requestedSection = (to.query.section || '').toString().toLowerCase()
  if (to.path === '/settings' && requestedSection === 'users' && !isAdmin(profile)) {
    return { path: '/settings', query: { section: 'security' } }
  }

  return true
})

export default router
