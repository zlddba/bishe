import { createRouter, createWebHistory } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

const routes = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/LoginView.vue'),
    meta: { public: true },
  },
  {
    path: '/',
    component: () => import('@/layout/AdminLayout.vue'),
    redirect: '/dashboard',
    children: [
      { path: 'dashboard', name: 'Dashboard', component: () => import('@/views/DashboardView.vue'), meta: { title: '经营看板' } },
      { path: 'sitters', name: 'Sitters', component: () => import('@/views/SittersView.vue'), meta: { title: '机构管理' } },
      { path: 'orders', name: 'Orders', component: () => import('@/views/OrdersView.vue'), meta: { title: '订单管理' } },
      { path: 'pets', name: 'Pets', component: () => import('@/views/PetsView.vue'), meta: { title: '宠物管理' } },
      { path: 'dynamics', name: 'Dynamics', component: () => import('@/views/DynamicsView.vue'), meta: { title: '寄养动态' } },
      { path: 'reviews', name: 'Reviews', component: () => import('@/views/ReviewsView.vue'), meta: { title: '评价管理' } },
      { path: 'coupons', name: 'Coupons', component: () => import('@/views/CouponsView.vue'), meta: { title: '优惠券' } },
      { path: 'notifications', name: 'Notifications', component: () => import('@/views/NotificationsView.vue'), meta: { title: '通知公告' } },
      { path: 'templates', name: 'Templates', component: () => import('@/views/TemplatesView.vue'), meta: { title: '消息模板' } },
      { path: 'push-tasks', name: 'PushTasks', component: () => import('@/views/PushTasksView.vue'), meta: { title: '推送任务' } },
      { path: 'aftersales', name: 'AfterSales', component: () => import('@/views/AfterSalesView.vue'), meta: { title: '售后服务' } },
      { path: 'settlements', name: 'Settlements', component: () => import('@/views/SettlementsView.vue'), meta: { title: '机构结算' } },
      { path: 'users', name: 'Users', component: () => import('@/views/UsersView.vue'), meta: { title: '用户管理' } },
      { path: 'permissions', name: 'Permissions', component: () => import('@/views/PermissionsView.vue'), meta: { title: '权限配置' } },
      { path: 'audit', name: 'Audit', component: () => import('@/views/AuditView.vue'), meta: { title: '审计日志' } },
      { path: 'updates', name: 'Updates', component: () => import('@/views/UpdatesView.vue'), meta: { title: '系统更新' } },
    ],
  },
]

const router = createRouter({ history: createWebHistory(), routes })

router.beforeEach((to) => {
  const auth = useAuthStore()
  if (!to.meta.public && !auth.isLoggedIn) return '/login'
  if (to.path === '/login' && auth.isLoggedIn) return '/dashboard'
})

export default router
