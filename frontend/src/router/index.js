import { createRouter, createWebHashHistory } from 'vue-router'

const routes = [
  { path: '/', redirect: '/dashboard' },
  { path: '/dashboard', name: 'dashboard', component: () => import('../views/Dashboard.vue'), meta: { title: '市场概览' } },
  { path: '/market', name: 'market', component: () => import('../views/Market.vue'), meta: { title: '行情列表' } },
  { path: '/stock/:code', name: 'stock', component: () => import('../views/StockDetail.vue'), meta: { title: '个股详情' } },
  { path: '/backtest', name: 'backtest', component: () => import('../views/Backtest.vue'), meta: { title: '策略回测' } },
  { path: '/screener', name: 'screener', component: () => import('../views/Screener.vue'), meta: { title: '选股筛选' } }
]

const router = createRouter({
  history: createWebHashHistory(),
  routes
})

router.afterEach((to) => {
  document.title = to.meta.title ? `${to.meta.title} · QuantLab` : 'QuantLab'
})

export default router
