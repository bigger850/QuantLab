<template>
  <div class="shell">
    <aside class="sidebar">
      <div class="brand">
        <span class="logo">Q</span>
        <div>
          <div class="brand-name">QuantLab</div>
          <div class="brand-sub">A股量化研究</div>
        </div>
      </div>

      <nav class="nav">
        <RouterLink
          v-for="item in navs"
          :key="item.path"
          :to="item.path"
          class="nav-item"
          :class="{ active: isActive(item.path) }"
        >
          <span class="nav-icon" v-html="item.icon"></span>
          <span>{{ item.label }}</span>
        </RouterLink>
      </nav>

      <div class="sidebar-foot">
        <div class="stat-row">
          <span>标的</span>
          <span class="num">{{ stats.symbolCount ?? '—' }}</span>
        </div>
        <div class="stat-row">
          <span>已入库</span>
          <span class="num">{{ stats.withBars ?? '—' }}</span>
        </div>
        <div class="stat-row">
          <span>最新交易日</span>
          <span class="num">{{ stats.latestTradeDate ?? '—' }}</span>
        </div>
        <div class="disclaimer">仅供研究，不构成投资建议</div>
      </div>
    </aside>

    <main class="main">
      <header class="topbar">
        <div class="crumb">{{ $route.meta.title || 'QuantLab' }}</div>
        <div class="topbar-right">
          <div class="search-box">
            <input
              v-model="keyword"
              placeholder="代码或名称"
              @keyup.enter="doSearch"
              @focus="showResult = true"
            />
            <div v-if="showResult && results.length" class="search-drop">
              <div
                v-for="s in results"
                :key="s.code"
                class="search-item"
                @click="goStock(s.code)"
              >
                <span class="code num">{{ s.code }}</span>
                <span class="name">{{ s.name }}</span>
                <span class="num" :class="pctClass(s.pctChg)">{{ fmtPct(s.pctChg) }}</span>
              </div>
            </div>
          </div>
          <span class="sync-dot" :class="{ on: syncing }"></span>
          <span class="sync-text">{{ syncing ? '同步中' : '就绪' }}</span>
        </div>
      </header>

      <section class="content">
        <RouterView />
      </section>
    </main>
  </div>
</template>

<script setup>
import { ref, onMounted, onUnmounted, computed } from 'vue'
import { useRouter } from 'vue-router'
import { api } from './api'

const router = useRouter()
const keyword = ref('')
const results = ref([])
const showResult = ref(false)
const stats = ref({})
const syncing = ref(false)
let timer = null

const navs = [
  {
    path: '/dashboard',
    label: '市场概览',
    icon: '<svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="1.8"><rect x="3" y="3" width="7" height="9" rx="1.5"/><rect x="14" y="3" width="7" height="5" rx="1.5"/><rect x="14" y="12" width="7" height="9" rx="1.5"/><rect x="3" y="16" width="7" height="5" rx="1.5"/></svg>'
  },
  {
    path: '/market',
    label: '行情列表',
    icon: '<svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="1.8"><path d="M3 17l5-6 4 3 5-7"/><path d="M3 21h18"/></svg>'
  },
  {
    path: '/backtest',
    label: '策略回测',
    icon: '<svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="1.8"><circle cx="12" cy="12" r="9"/><path d="M12 7v5l3 2"/></svg>'
  },
  {
    path: '/screener',
    label: '选股筛选',
    icon: '<svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="1.8"><path d="M3 5h18l-7 8v6l-4 2v-8z"/></svg>'
  }
]

const isActive = (path) => {
  if (path === '/market') {
    return router.currentRoute.value.path === '/market' || router.currentRoute.value.path.startsWith('/stock')
  }
  return router.currentRoute.value.path === path
}

const pctClass = (v) => {
  if (v === null || v === undefined) return 'flat'
  return v > 0 ? 'up' : v < 0 ? 'down' : 'flat'
}
const fmtPct = (v) => {
  if (v === null || v === undefined) return '—'
  return (v > 0 ? '+' : '') + Number(v).toFixed(2) + '%'
}

async function doSearch() {
  if (!keyword.value.trim()) {
    results.value = []
    return
  }
  try {
    results.value = await api.search(keyword.value.trim())
  } catch (e) {
    results.value = []
  }
}

function goStock(code) {
  showResult.value = false
  keyword.value = ''
  results.value = []
  router.push(`/stock/${code}`)
}

async function loadStats() {
  try {
    const d = await api.overview()
    stats.value = d
    const p = d.syncProgress
    syncing.value = !!(p && p.status === 'RUNNING')
  } catch (e) {
    /* 后端未就绪时静默 */
  }
}

function onClickOutside(e) {
  if (!e.target.closest('.search-box')) {
    showResult.value = false
  }
}

onMounted(() => {
  loadStats()
  timer = setInterval(loadStats, 30000)
  document.addEventListener('click', onClickOutside)
})

onUnmounted(() => {
  clearInterval(timer)
  document.removeEventListener('click', onClickOutside)
})
</script>

<style scoped>
.shell {
  display: flex;
  height: 100%;
}

.sidebar {
  width: 208px;
  flex-shrink: 0;
  background: var(--bg-soft);
  border-right: 1px solid var(--border-soft);
  display: flex;
  flex-direction: column;
  padding: 18px 12px;
}

.brand {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 0 8px 20px;
}

.logo {
  width: 32px;
  height: 32px;
  border-radius: 8px;
  background: linear-gradient(135deg, #3b82f6, #6366f1);
  display: grid;
  place-items: center;
  font-weight: 700;
  font-size: 16px;
  color: #fff;
}

.brand-name {
  font-size: 15px;
  font-weight: 600;
  letter-spacing: 0.01em;
}

.brand-sub {
  font-size: 11px;
  color: var(--text-mute);
}

.nav {
  display: flex;
  flex-direction: column;
  gap: 3px;
}

.nav-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 9px 10px;
  border-radius: 7px;
  color: var(--text-dim);
  font-size: 13px;
  transition: all 0.15s ease;
}

.nav-item:hover {
  background: var(--panel);
  color: var(--text);
}

.nav-item.active {
  background: var(--accent-soft);
  color: #7fb0ff;
}

.nav-icon {
  display: grid;
  place-items: center;
}

.sidebar-foot {
  margin-top: auto;
  padding: 12px 8px 0;
  border-top: 1px solid var(--border-soft);
}

.stat-row {
  display: flex;
  justify-content: space-between;
  font-size: 11px;
  color: var(--text-mute);
  padding: 3px 0;
}

.disclaimer {
  margin-top: 12px;
  font-size: 10px;
  color: #3f4b5c;
  line-height: 1.4;
}

.main {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-width: 0;
}

.topbar {
  height: 52px;
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 20px;
  border-bottom: 1px solid var(--border-soft);
}

.crumb {
  font-size: 14px;
  font-weight: 500;
}

.topbar-right {
  display: flex;
  align-items: center;
  gap: 12px;
}

.search-box {
  position: relative;
}

.search-box input {
  width: 220px;
  background: var(--bg-soft);
}

.search-drop {
  position: absolute;
  top: 40px;
  right: 0;
  width: 300px;
  background: var(--panel);
  border: 1px solid var(--border);
  border-radius: var(--radius);
  box-shadow: 0 12px 32px rgba(0, 0, 0, 0.5);
  max-height: 320px;
  overflow-y: auto;
  z-index: 50;
  padding: 4px;
}

.search-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px 10px;
  border-radius: var(--radius-sm);
  cursor: pointer;
  font-size: 13px;
}

.search-item:hover {
  background: var(--panel-2);
}

.search-item .code {
  color: var(--text-dim);
  width: 58px;
}

.search-item .name {
  flex: 1;
}

.sync-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--down);
}

.sync-dot.on {
  background: var(--warn);
  animation: pulse 1.2s infinite;
}

.sync-text {
  font-size: 11px;
  color: var(--text-mute);
}

@keyframes pulse {
  50% {
    opacity: 0.35;
  }
}

.content {
  flex: 1;
  overflow-y: auto;
  padding: 18px 20px 40px;
}
</style>
