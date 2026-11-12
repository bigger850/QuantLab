<template>
  <div class="page">
    <div class="card">
      <h3 class="card-title">筛选条件</h3>
      <div class="form-row">
        <div class="field">
          <label>类型</label>
          <select v-model="form.type" style="width: 110px">
            <option value="STOCK">股票</option>
            <option value="ETF">ETF</option>
          </select>
        </div>
        <div class="field">
          <label>候选池</label>
          <input v-model.number="form.poolSize" type="number" step="50" style="width: 100px" />
        </div>
        <div class="field">
          <label>成交额下限（万）</label>
          <input v-model.number="minAmountWan" type="number" step="1000" style="width: 120px" />
        </div>
        <div class="field">
          <label>20日涨幅 ≥ (%)</label>
          <input v-model.number="form.minReturn20" type="number" step="1" style="width: 110px" />
        </div>
        <div class="field">
          <label>20日涨幅 ≤ (%)</label>
          <input v-model.number="form.maxReturn20" type="number" step="1" style="width: 110px" />
        </div>
        <div class="field">
          <label>波动率上限 (%)</label>
          <input v-model.number="form.maxVolatility" type="number" step="5" style="width: 110px" />
        </div>
        <div class="field">
          <label>60日回撤上限 (%)</label>
          <input v-model.number="form.maxDrawdown60" type="number" step="5" style="width: 110px" />
        </div>
        <div class="field">
          <label>站上20日线</label>
          <select v-model="form.aboveMa20" style="width: 100px">
            <option :value="null">不限</option>
            <option :value="true">是</option>
            <option :value="false">否</option>
          </select>
        </div>
        <button class="primary" @click="run" :disabled="loading">
          <span v-if="loading" class="spin"></span>
          {{ loading ? '筛选中' : '开始筛选' }}
        </button>
      </div>
      <p v-if="error" class="err">{{ error }}</p>
      <p v-else-if="elapsed !== null" class="hint">
        命中 {{ rows.length }} 只 · 耗时 {{ elapsed }} ms
      </p>
    </div>

    <div class="card" style="margin-top: 14px">
      <div class="table-wrap">
        <table>
        <thead>
          <tr>
            <th>代码</th><th>名称</th>
            <th class="right">最新价</th><th class="right">涨跌幅</th>
            <th class="right">5日</th><th class="right">20日</th><th class="right">60日</th>
            <th class="right">波动率</th><th class="right">60日回撤</th><th class="right">MA20偏离</th>
            <th class="right">成交额</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="s in rows" :key="s.code" class="clickable" @click="go(s.code)">
            <td class="num muted">{{ s.code }}</td>
            <td>{{ s.name }}</td>
            <td class="right num">{{ fmt(s.price) }}</td>
            <td class="right num" :class="cls(s.pctChg)">{{ fmtPct(s.pctChg) }}</td>
            <td class="right num" :class="cls(s.return5)">{{ fmtPct(s.return5) }}</td>
            <td class="right num" :class="cls(s.return20)">{{ fmtPct(s.return20) }}</td>
            <td class="right num" :class="cls(s.return60)">{{ fmtPct(s.return60) }}</td>
            <td class="right num">{{ fmt(s.volatility20) }}%</td>
            <td class="right num down">-{{ fmt(s.drawdown60) }}%</td>
            <td class="right num" :class="cls(s.ma20Deviation)">{{ fmtPct(s.ma20Deviation) }}</td>
            <td class="right num">{{ fmtAmount(s.amount) }}</td>
          </tr>
          <tr v-if="!rows.length">
            <td colspan="11" class="empty">
              {{ elapsed === null ? '设置条件后点击「开始筛选」' : '没有符合条件的标的，可适当放宽条件' }}
            </td>
          </tr>
        </tbody>
        </table>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { api } from '../api'

const router = useRouter()
const rows = ref([])
const loading = ref(false)
const error = ref('')
const elapsed = ref(null)
const minAmountWan = ref(5000)

const form = ref({
  type: 'STOCK',
  poolSize: 300,
  minReturn20: null,
  maxReturn20: null,
  maxVolatility: null,
  maxDrawdown60: null,
  aboveMa20: null,
  limit: 50
})

const fmt = (v) => (v === null || v === undefined ? '—' : Number(v).toFixed(2))
const fmtPct = (v) => {
  if (v === null || v === undefined) return '—'
  const n = Number(v)
  return (n > 0 ? '+' : '') + n.toFixed(2) + '%'
}
const cls = (v) => (v > 0 ? 'up' : v < 0 ? 'down' : 'flat')
const fmtAmount = (v) => {
  if (v === null || v === undefined) return '—'
  const n = Number(v)
  if (n >= 1e8) return (n / 1e8).toFixed(2) + ' 亿'
  if (n >= 1e4) return (n / 1e4).toFixed(0) + ' 万'
  return n.toFixed(0)
}

async function run() {
  loading.value = true
  error.value = ''
  const begin = performance.now()
  try {
    const payload = { ...form.value }
    payload.minAmount = minAmountWan.value ? minAmountWan.value * 10000 : null
    rows.value = await api.runScreener(payload)
    elapsed.value = Math.round(performance.now() - begin)
  } catch (e) {
    error.value = e.message
    rows.value = []
  } finally {
    loading.value = false
  }
}

const go = (code) => router.push(`/stock/${code}`)
</script>

<style scoped>
.right {
  text-align: right;
}

.table-wrap {
  overflow-x: auto;
}

.clickable {
  cursor: pointer;
}

.err {
  margin: 12px 0 0;
  font-size: 12px;
  color: var(--up);
}

.hint {
  margin: 12px 0 0;
  font-size: 12px;
  color: var(--text-mute);
}
</style>
