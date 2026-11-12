<template>
  <div class="page">
    <div class="card">
      <h3 class="card-title">回测配置</h3>
      <div class="form-row">
        <div class="field">
          <label>标的代码</label>
          <input v-model="form.code" placeholder="如 600519" style="width: 130px" />
        </div>
        <div class="field">
          <label>策略</label>
          <select v-model="form.strategy" @change="onStrategyChange" style="width: 150px">
            <option v-for="s in strategies" :key="s.code" :value="s.code">{{ s.name }}</option>
          </select>
        </div>
        <div class="field" v-for="p in currentParams" :key="p.key">
          <label>{{ p.label }}</label>
          <input
            v-model.number="form.params[p.key]"
            type="number"
            :min="p.min"
            :max="p.max"
            :step="p.type === 'double' ? 0.5 : 1"
            style="width: 96px"
          />
        </div>
        <div class="field">
          <label>开始日期</label>
          <input v-model="form.startDate" type="date" style="width: 150px" />
        </div>
        <div class="field">
          <label>结束日期</label>
          <input v-model="form.endDate" type="date" style="width: 150px" />
        </div>
        <div class="field">
          <label>初始资金</label>
          <input v-model.number="form.initCash" type="number" step="10000" style="width: 130px" />
        </div>
        <button class="primary" @click="run" :disabled="running">
          <span v-if="running" class="spin"></span>
          {{ running ? '计算中' : '开始回测' }}
        </button>
      </div>
      <p v-if="strategyDesc" class="desc">{{ strategyDesc }}</p>
      <p v-if="error" class="err">{{ error }}</p>
    </div>

    <template v-if="result">
      <div class="metrics">
        <div class="metric" v-for="m in metricCards" :key="m.label">
          <div class="metric-label">{{ m.label }}</div>
          <div class="metric-value num" :class="m.cls">{{ m.value }}</div>
          <div class="metric-sub" v-if="m.sub">{{ m.sub }}</div>
        </div>
      </div>

      <div class="card" style="margin-top: 14px">
        <div class="card-head">
          <h3 class="card-title">净值曲线</h3>
          <span class="muted" style="font-size: 12px">
            {{ result.startDate }} → {{ result.endDate }} · {{ result.barCount }} 个交易日
          </span>
        </div>
        <EquityChart :curve="result.equityCurve || []" :height="340" />
      </div>

      <div class="card" style="margin-top: 14px">
        <div class="card-head">
          <h3 class="card-title">成交记录（{{ (result.trades || []).length }} 笔）</h3>
        </div>
        <div class="table-wrap">
          <table>
            <thead>
              <tr>
                <th>日期</th><th>方向</th><th class="right">价格</th>
                <th class="right">数量</th><th class="right">金额</th>
                <th class="right">费用</th><th class="right">盈亏</th><th class="right">收益率</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="(t, i) in result.trades || []" :key="i">
                <td class="num muted">{{ t.date }}</td>
                <td><span class="tag" :class="t.side === 'BUY' ? 'buy' : 'sell'">{{ t.side === 'BUY' ? '买入' : '卖出' }}</span></td>
                <td class="right num">{{ fmt(t.price) }}</td>
                <td class="right num">{{ fmt(t.qty, 0) }}</td>
                <td class="right num">{{ fmt(t.amount) }}</td>
                <td class="right num muted">{{ fmt(t.fee) }}</td>
                <td class="right num" :class="pnlClass(t.pnl)">{{ t.pnl === null || t.pnl === undefined ? '—' : fmt(t.pnl) }}</td>
                <td class="right num" :class="pnlClass(t.pnlPct)">{{ t.pnlPct === null || t.pnlPct === undefined ? '—' : fmtPct(t.pnlPct) }}</td>
              </tr>
              <tr v-if="!(result.trades || []).length">
                <td colspan="8" class="empty">区间内没有产生成交</td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>
    </template>

    <div v-else-if="!running" class="card" style="margin-top: 14px">
      <div class="empty">
        填写标的与策略后点击「开始回测」。<br />
        回测按次日开盘价成交，含佣金与印花税，结果仅供参考。
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { api } from '../api'
import EquityChart from '../components/EquityChart.vue'

const strategies = ref([])
const metas = ref({})
const result = ref(null)
const running = ref(false)
const error = ref('')

const today = new Date()
const oneYearAgo = new Date(today.getTime() - 365 * 24 * 3600 * 1000)

const form = ref({
  code: '600519',
  strategy: 'ma_cross',
  params: {},
  startDate: oneYearAgo.toISOString().slice(0, 10),
  endDate: today.toISOString().slice(0, 10),
  initCash: 1000000
})

const currentParams = computed(() => {
  const meta = metas.value[form.value.strategy]
  if (!meta || !meta.paramSchema) return []
  try {
    return JSON.parse(meta.paramSchema)
  } catch (e) {
    return []
  }
})

const strategyDesc = computed(() => metas.value[form.value.strategy]?.description || '')

const fmt = (v, digits = 2) => (v === null || v === undefined ? '—' : Number(v).toFixed(digits))
const fmtPct = (v) => {
  if (v === null || v === undefined) return '—'
  const n = Number(v)
  return (n > 0 ? '+' : '') + n.toFixed(2) + '%'
}
const pnlClass = (v) => {
  if (v === null || v === undefined) return ''
  return v > 0 ? 'up' : v < 0 ? 'down' : ''
}

const metricCards = computed(() => {
  const r = result.value
  if (!r) return []
  return [
    {
      label: '总收益率',
      value: fmtPct(r.totalReturn),
      cls: pnlClass(r.totalReturn),
      sub: `期末权益 ${fmt(r.finalEquity, 0)}`
    },
    {
      label: '年化收益',
      value: fmtPct(r.annualReturn),
      cls: pnlClass(r.annualReturn),
      sub: `基准 ${fmtPct(r.benchmarkReturn)}`
    },
    {
      label: '最大回撤',
      value: '-' + fmt(r.maxDrawdown) + '%',
      cls: 'down',
      sub: '区间峰值回落'
    },
    {
      label: '夏普比率',
      value: fmt(r.sharpe),
      cls: Number(r.sharpe) > 1 ? 'up' : '',
      sub: '无风险利率 2%'
    },
    {
      label: '胜率',
      value: fmt(r.winRate) + '%',
      cls: Number(r.winRate) > 50 ? 'up' : '',
      sub: `${r.winCount} 胜 / ${r.loseCount} 负`
    },
    {
      label: '盈亏比',
      value: fmt(r.profitFactor),
      cls: Number(r.profitFactor) > 1 ? 'up' : 'down',
      sub: `共 ${r.tradeCount} 笔`
    }
  ]
})

async function loadMeta() {
  try {
    const list = await api.strategyMeta()
    strategies.value = list
    const map = {}
    list.forEach((s) => { map[s.code] = s })
    metas.value = map
    onStrategyChange()
  } catch (e) {
    error.value = '策略列表加载失败：' + e.message
  }
}

function onStrategyChange() {
  const meta = metas.value[form.value.strategy]
  if (!meta || !meta.paramSchema) {
    form.value.params = {}
    return
  }
  try {
    const schema = JSON.parse(meta.paramSchema)
    const p = {}
    schema.forEach((s) => { p[s.key] = s.default })
    form.value.params = p
  } catch (e) {
    form.value.params = {}
  }
}

async function run() {
  if (!form.value.code) {
    error.value = '请填写标的代码'
    return
  }
  running.value = true
  error.value = ''
  try {
    result.value = await api.runBacktest({
      code: form.value.code.trim(),
      strategy: form.value.strategy,
      params: form.value.params,
      startDate: form.value.startDate || null,
      endDate: form.value.endDate || null,
      initCash: form.value.initCash,
      save: true
    })
  } catch (e) {
    error.value = e.message
    result.value = null
  } finally {
    running.value = false
  }
}

onMounted(loadMeta)
</script>

<style scoped>
.desc {
  margin: 12px 0 0;
  font-size: 12px;
  color: var(--text-mute);
}

.err {
  margin: 12px 0 0;
  font-size: 12px;
  color: var(--up);
  padding: 8px 10px;
  background: rgba(245, 69, 92, 0.08);
  border-radius: var(--radius-sm);
  border-left: 2px solid var(--up);
}

.metrics {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(150px, 1fr));
  gap: 10px;
  margin-top: 14px;
}

.metric {
  background: var(--panel);
  border: 1px solid var(--border-soft);
  border-radius: var(--radius);
  padding: 13px;
}

.metric-label {
  font-size: 11px;
  color: var(--text-mute);
  margin-bottom: 6px;
}

.metric-value {
  font-size: 20px;
  font-weight: 600;
  line-height: 1.1;
}

.metric-sub {
  margin-top: 6px;
  font-size: 10px;
  color: var(--text-mute);
}

.card-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 8px;
}

.card-head .card-title {
  margin: 0;
}

.right {
  text-align: right;
}

.table-wrap {
  max-height: 420px;
  overflow-y: auto;
}

.tag.buy {
  background: rgba(245, 69, 92, 0.14);
  color: var(--up);
  border-color: rgba(245, 69, 92, 0.3);
}

.tag.sell {
  background: rgba(14, 203, 129, 0.14);
  color: var(--down);
  border-color: rgba(14, 203, 129, 0.3);
}

@media (max-width: 1200px) {
  .metrics {
    grid-template-columns: repeat(3, 1fr);
  }
}
</style>
