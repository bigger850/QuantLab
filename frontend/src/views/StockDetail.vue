<template>
  <div class="page">
    <div v-if="loading" class="empty"><span class="spin"></span> 加载中</div>

    <template v-else-if="kline">
      <div class="head">
        <div class="head-left">
          <div class="title">
            <span class="name">{{ kline.name }}</span>
            <span class="code num">{{ kline.code }}</span>
            <span class="tag">{{ kline.type }}</span>
          </div>
          <div class="price-row">
            <span class="price num" :class="pctClass(kline.pctChg)">{{ fmt(kline.latestPrice) }}</span>
            <span class="chg num" :class="pctClass(kline.pctChg)">{{ fmtPct(kline.pctChg) }}</span>
          </div>
        </div>

        <div class="head-right">
          <div class="range-switch">
            <button
              v-for="r in ranges"
              :key="r.value"
              :class="{ primary: limit === r.value }"
              @click="changeRange(r.value)"
            >{{ r.label }}</button>
          </div>
        </div>
      </div>

      <div class="metrics" v-if="metrics">
        <div class="metric" v-for="m in metrics" :key="m.label">
          <div class="metric-label">{{ m.label }}</div>
          <div class="metric-value num" :class="m.cls">{{ m.value }}</div>
        </div>
      </div>

      <div class="card chart-card">
        <KlineChart :kline="kline" :height="560" />
      </div>
    </template>

    <div v-else class="empty">{{ error || '无数据' }}</div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { api } from '../api'
import KlineChart from '../components/KlineChart.vue'

const route = useRoute()
const router = useRouter()
const kline = ref(null)
const loading = ref(true)
const error = ref('')
const limit = ref(250)

const ranges = [
  { label: '60日', value: 60 },
  { label: '120日', value: 120 },
  { label: '1年', value: 250 },
  { label: '2年', value: 500 },
  { label: '5年', value: 1200 }
]

const fmt = (v) => (v === null || v === undefined ? '—' : Number(v).toFixed(2))
const fmtPct = (v) => {
  if (v === null || v === undefined) return '—'
  const n = Number(v)
  return (n > 0 ? '+' : '') + n.toFixed(2) + '%'
}
const pctClass = (v) => (v > 0 ? 'up' : v < 0 ? 'down' : 'flat')

const last = (arr) => {
  if (!arr || !arr.length) return null
  for (let i = arr.length - 1; i >= 0; i--) {
    if (arr[i] !== null && arr[i] !== undefined) return arr[i]
  }
  return null
}

const metrics = computed(() => {
  const k = kline.value
  if (!k || !k.bars || !k.bars.length) return null
  const ind = k.indicators || {}
  const bars = k.bars
  const close = Number(bars[bars.length - 1].close)

  const ma20 = last(ind.ma20)
  const dev = ma20 ? (close - ma20) / ma20 * 100 : null
  const rsi14 = last(ind.rsi14)
  const dif = last(ind.dif)
  const dea = last(ind.dea)

  const pctOf = (n) => {
    if (bars.length <= n) return null
    const past = Number(bars[bars.length - 1 - n].close)
    return past > 0 ? (close - past) / past * 100 : null
  }
  const ret20 = pctOf(20)

  let peak = 0
  const start = Math.max(0, bars.length - 60)
  for (let i = start; i < bars.length; i++) {
    peak = Math.max(peak, Number(bars[i].close))
  }
  const dd = peak > 0 ? (peak - close) / peak * 100 : null

  return [
    {
      label: 'MA20 偏离',
      value: dev === null ? '—' : (dev > 0 ? '+' : '') + dev.toFixed(2) + '%',
      cls: dev === null ? '' : dev > 0 ? 'up' : 'down'
    },
    {
      label: 'RSI(14)',
      value: rsi14 === null ? '—' : rsi14.toFixed(1),
      cls: rsi14 === null ? '' : rsi14 > 70 ? 'up' : rsi14 < 30 ? 'down' : ''
    },
    {
      label: 'MACD',
      value: dif === null || dea === null ? '—' : (dif - dea >= 0 ? '多头' : '空头'),
      cls: dif === null || dea === null ? '' : dif - dea >= 0 ? 'up' : 'down'
    },
    {
      label: '20日涨幅',
      value: ret20 === null ? '—' : (ret20 > 0 ? '+' : '') + ret20.toFixed(2) + '%',
      cls: ret20 === null ? '' : ret20 > 0 ? 'up' : 'down'
    },
    {
      label: '距60日高点',
      value: dd === null ? '—' : '-' + dd.toFixed(2) + '%',
      cls: dd === null ? '' : dd > 10 ? 'down' : ''
    },
    {
      label: '样本',
      value: bars.length + ' 根',
      cls: 'muted'
    }
  ]
})

async function load() {
  loading.value = true
  error.value = ''
  try {
    kline.value = await api.kline(route.params.code, limit.value, true)
  } catch (e) {
    error.value = e.message
    kline.value = null
  } finally {
    loading.value = false
  }
}

function changeRange(v) {
  limit.value = v
  load()
}

watch(() => route.params.code, load)
onMounted(load)
</script>

<style scoped>
.head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  margin-bottom: 14px;
}

.title {
  display: flex;
  align-items: center;
  gap: 10px;
}

.name {
  font-size: 19px;
  font-weight: 600;
}

.code {
  color: var(--text-mute);
  font-size: 13px;
}

.price-row {
  display: flex;
  align-items: baseline;
  gap: 10px;
  margin-top: 6px;
}

.price {
  font-size: 30px;
  font-weight: 600;
  line-height: 1;
}

.chg {
  font-size: 14px;
}

.range-switch {
  display: flex;
  gap: 6px;
}

.range-switch button {
  padding: 5px 11px;
  font-size: 12px;
}

.metrics {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(140px, 1fr));
  gap: 10px;
  margin-bottom: 14px;
}

.metric {
  background: var(--panel);
  border: 1px solid var(--border-soft);
  border-radius: var(--radius-sm);
  padding: 10px 12px;
}

.metric-label {
  font-size: 11px;
  color: var(--text-mute);
  margin-bottom: 5px;
}

.metric-value {
  font-size: 16px;
  font-weight: 600;
}

.chart-card {
  padding: 12px 8px 8px;
}

@media (max-width: 1100px) {
  .metrics {
    grid-template-columns: repeat(3, 1fr);
  }
}
</style>
