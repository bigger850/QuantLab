<template>
  <div class="page">
    <div v-if="loading" class="empty"><span class="spin"></span> 加载中</div>

    <template v-else>
      <div class="stat-grid">
        <div class="stat-card">
          <div class="stat-label">标的总数</div>
          <div class="stat-value num">{{ data.symbolCount ?? 0 }}</div>
          <div class="stat-hint">个股 / ETF / 指数</div>
        </div>
        <div class="stat-card">
          <div class="stat-label">已入库标的</div>
          <div class="stat-value num">{{ data.withBars ?? 0 }}</div>
          <div class="stat-hint">最新交易日 {{ data.latestTradeDate || '—' }}</div>
        </div>
        <div class="stat-card">
          <div class="stat-label">上涨家数</div>
          <div class="stat-value num up">{{ data.upCount ?? 0 }}</div>
          <div class="stat-hint">
            下跌 <span class="down">{{ data.downCount ?? 0 }}</span> · 平盘 {{ data.flatCount ?? 0 }}
          </div>
        </div>
        <div class="stat-card">
          <div class="stat-label">同步状态</div>
          <div class="stat-value" :class="progressTextClass">{{ progressText }}</div>
          <div class="stat-hint">{{ progressHint }}</div>
        </div>
      </div>

      <div class="grid two-col">
        <div class="card">
          <div class="card-head">
            <h3 class="card-title">涨幅榜</h3>
            <RouterLink to="/market" class="more">全部 →</RouterLink>
          </div>
          <table>
            <thead>
              <tr><th>代码</th><th>名称</th><th class="right">最新价</th><th class="right">涨跌幅</th></tr>
            </thead>
            <tbody>
              <tr v-for="s in data.topGainers || []" :key="s.code" @click="go(s.code)" class="clickable">
                <td class="num muted">{{ s.code }}</td>
                <td>{{ s.name }}</td>
                <td class="right num">{{ fmt(s.latestPrice) }}</td>
                <td class="right num up">{{ fmtPct(s.pctChg) }}</td>
              </tr>
              <tr v-if="!(data.topGainers || []).length">
                <td colspan="4" class="empty">暂无数据，请先同步行情</td>
              </tr>
            </tbody>
          </table>
        </div>

        <div class="card">
          <div class="card-head">
            <h3 class="card-title">跌幅榜</h3>
          </div>
          <table>
            <thead>
              <tr><th>代码</th><th>名称</th><th class="right">最新价</th><th class="right">涨跌幅</th></tr>
            </thead>
            <tbody>
              <tr v-for="s in data.topLosers || []" :key="s.code" @click="go(s.code)" class="clickable">
                <td class="num muted">{{ s.code }}</td>
                <td>{{ s.name }}</td>
                <td class="right num">{{ fmt(s.latestPrice) }}</td>
                <td class="right num down">{{ fmtPct(s.pctChg) }}</td>
              </tr>
              <tr v-if="!(data.topLosers || []).length">
                <td colspan="4" class="empty">暂无数据，请先同步行情</td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>

      <div class="card" style="margin-top: 14px">
        <div class="card-head">
          <h3 class="card-title">成交额排行</h3>
          <div class="actions">
            <button @click="syncSymbols" :disabled="busy.symbols">
              <span v-if="busy.symbols" class="spin"></span>
              {{ busy.symbols ? '同步中' : '同步标的清单' }}
            </button>
            <button class="primary" @click="syncDaily" :disabled="busy.daily">
              <span v-if="busy.daily" class="spin"></span>
              {{ busy.daily ? '已提交' : '同步热门行情' }}
            </button>
          </div>
        </div>
        <table>
          <thead>
            <tr>
              <th>代码</th><th>名称</th><th class="right">最新价</th>
              <th class="right">涨跌幅</th><th class="right">成交额</th><th class="right">已入库</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="s in data.topTurnover || []" :key="s.code" @click="go(s.code)" class="clickable">
              <td class="num muted">{{ s.code }}</td>
              <td>{{ s.name }}</td>
              <td class="right num">{{ fmt(s.latestPrice) }}</td>
              <td class="right num" :class="pctClass(s.pctChg)">{{ fmtPct(s.pctChg) }}</td>
              <td class="right num">{{ fmtAmount(s.turnover) }}</td>
              <td class="right num muted">{{ s.barCount || 0 }} 根</td>
            </tr>
            <tr v-if="!(data.topTurnover || []).length">
              <td colspan="6" class="empty">暂无数据</td>
            </tr>
          </tbody>
        </table>
        <p v-if="msg" class="msg">{{ msg }}</p>
      </div>
    </template>
  </div>
</template>

<script setup>
import { ref, onMounted, onUnmounted, computed } from 'vue'
import { useRouter } from 'vue-router'
import { api } from '../api'

const router = useRouter()
const data = ref({})
const loading = ref(true)
const busy = ref({ symbols: false, daily: false })
const msg = ref('')
let timer = null

const progress = computed(() => data.value.syncProgress || {})
const progressText = computed(() => {
  const p = progress.value
  if (p.status === 'RUNNING') return `${p.success || 0} / ${p.total || 0}`
  if (p.status === 'SUCCESS') return '已完成'
  if (p.status === 'PARTIAL') return '部分失败'
  return '空闲'
})
const progressTextClass = computed(() => {
  const s = progress.value.status
  if (s === 'RUNNING') return 'warn'
  if (s === 'PARTIAL') return 'up'
  return 'ok'
})
const progressHint = computed(() => {
  const p = progress.value
  if (p.status === 'RUNNING') return p.message || '正在拉取日线'
  if (p.status === 'SUCCESS') return p.message || ''
  if (p.status === 'PARTIAL') return `失败 ${p.failed || 0} 只`
  return '点击右下按钮开始同步'
})

const fmt = (v) => (v === null || v === undefined ? '—' : Number(v).toFixed(2))
const fmtPct = (v) => {
  if (v === null || v === undefined) return '—'
  const n = Number(v)
  return (n > 0 ? '+' : '') + n.toFixed(2) + '%'
}
const pctClass = (v) => (v > 0 ? 'up' : v < 0 ? 'down' : 'flat')
const fmtAmount = (v) => {
  if (v === null || v === undefined) return '—'
  const n = Number(v)
  if (n >= 1e8) return (n / 1e8).toFixed(2) + ' 亿'
  if (n >= 1e4) return (n / 1e4).toFixed(1) + ' 万'
  return n.toFixed(0)
}

const go = (code) => router.push(`/stock/${code}`)

async function load() {
  try {
    data.value = await api.overview()
  } catch (e) {
    msg.value = '加载失败：' + e.message
  } finally {
    loading.value = false
  }
}

async function syncSymbols() {
  busy.value.symbols = true
  msg.value = '正在拉取标的清单，约 10-30 秒…'
  try {
    const r = await api.syncSymbols()
    msg.value = `标的清单同步完成，库内共 ${r.stockCount} 只`
    await load()
  } catch (e) {
    msg.value = '同步失败：' + e.message
  } finally {
    busy.value.symbols = false
  }
}

async function syncDaily() {
  busy.value.daily = true
  msg.value = ''
  try {
    const r = await api.syncDaily(true, 300)
    msg.value = r.message + '（成交额前 300 只，其余标的打开详情页时自动回源）'
    if (r.started) {
      poll()
    }
  } catch (e) {
    msg.value = '提交失败：' + e.message
  } finally {
    busy.value.daily = false
  }
}

function poll() {
  clearTimeout(timer)
  timer = setTimeout(async () => {
    await load()
    const st = progress.value.status
    if (st === 'RUNNING') {
      poll()
    } else {
      msg.value = '同步结束：成功 ' + (progress.value.success || 0) + ' 只'
    }
  }, 3000)
}

onMounted(load)
onUnmounted(() => clearTimeout(timer))
</script>

<style scoped>
.stat-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(190px, 1fr));
  gap: 14px;
  margin-bottom: 14px;
}

.stat-card {
  background: var(--panel);
  border: 1px solid var(--border-soft);
  border-radius: var(--radius);
  padding: 16px;
}

.stat-label {
  font-size: 12px;
  color: var(--text-mute);
  margin-bottom: 8px;
}

.stat-value {
  font-size: 26px;
  font-weight: 600;
  line-height: 1.1;
}

.stat-value.warn {
  color: var(--warn);
  font-size: 20px;
}

.stat-value.ok {
  color: var(--down);
  font-size: 20px;
}

.stat-hint {
  margin-top: 8px;
  font-size: 11px;
  color: var(--text-mute);
}

.two-col {
  grid-template-columns: repeat(auto-fit, minmax(400px, 1fr));
}

.card-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 10px;
}

.card-head .card-title {
  margin: 0;
}

.more {
  font-size: 12px;
  color: var(--accent);
}

.actions {
  display: flex;
  gap: 8px;
}

.right {
  text-align: right;
}

.clickable {
  cursor: pointer;
}

.msg {
  margin: 12px 0 0;
  font-size: 12px;
  color: var(--text-dim);
  padding: 8px 10px;
  background: var(--bg-soft);
  border-radius: var(--radius-sm);
  border-left: 2px solid var(--accent);
}

@media (max-width: 1100px) {
  .stat-grid {
    grid-template-columns: repeat(2, 1fr);
  }
  .two-col {
    grid-template-columns: 1fr;
  }
}
</style>
