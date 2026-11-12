<template>
  <div class="page">
    <div class="card">
      <div class="form-row" style="margin-bottom: 14px">
        <div class="field">
          <label>类型</label>
          <select v-model="query.type" @change="reload">
            <option value="STOCK">股票</option>
            <option value="ETF">ETF</option>
            <option value="INDEX">指数</option>
          </select>
        </div>
        <div class="field">
          <label>关键词</label>
          <input v-model="query.keyword" placeholder="代码 / 名称" @keyup.enter="reload" />
        </div>
        <button class="primary" @click="reload">查询</button>
        <button @click="reset">重置</button>
        <span class="muted" style="margin-left: auto; font-size: 12px">
          共 {{ total }} 条
        </span>
      </div>

      <div v-if="loading" class="empty"><span class="spin"></span> 加载中</div>
      <div v-else class="table-wrap">
        <table>
        <thead>
          <tr>
            <th>代码</th><th>名称</th><th>类型</th>
            <th class="right">最新价</th><th class="right">涨跌幅</th>
            <th class="right">成交额</th><th class="right">K线根数</th><th class="right">最新日期</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="s in rows" :key="s.code" class="clickable" @click="go(s.code)">
            <td class="num muted">{{ s.code }}</td>
            <td>{{ s.name }}</td>
            <td><span class="tag">{{ s.type }}</span></td>
            <td class="right num">{{ fmt(s.latestPrice) }}</td>
            <td class="right num" :class="pctClass(s.pctChg)">{{ fmtPct(s.pctChg) }}</td>
            <td class="right num">{{ fmtAmount(s.turnover) }}</td>
            <td class="right num muted">{{ s.barCount || 0 }}</td>
            <td class="right num muted">{{ s.lastBarDate || '—' }}</td>
          </tr>
          <tr v-if="!rows.length">
            <td colspan="8" class="empty">没有匹配的标的</td>
          </tr>
        </tbody>
        </table>
      </div>

      <div class="pager" v-if="total > pageSize">
        <button :disabled="query.page <= 1" @click="turn(-1)">上一页</button>
        <span class="muted num">{{ query.page }} / {{ totalPages }}</span>
        <button :disabled="query.page >= totalPages" @click="turn(1)">下一页</button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { api } from '../api'

const router = useRouter()
const rows = ref([])
const total = ref(0)
const loading = ref(true)
const pageSize = 20
const query = ref({ type: 'STOCK', keyword: '', page: 1 })

const totalPages = computed(() => Math.max(1, Math.ceil(total.value / pageSize)))

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

async function reload() {
  loading.value = true
  query.value.page = 1
  await load()
}

async function load() {
  loading.value = true
  try {
    const d = await api.stocksPage({
      type: query.value.type,
      keyword: query.value.keyword,
      page: query.value.page,
      size: pageSize
    })
    rows.value = d.records || []
    total.value = d.total || 0
  } catch (e) {
    rows.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

function reset() {
  query.value = { type: 'STOCK', keyword: '', page: 1 }
  load()
}

function turn(delta) {
  query.value.page += delta
  load()
}

const go = (code) => router.push(`/stock/${code}`)

onMounted(load)
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

.pager {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 14px;
  margin-top: 16px;
  font-size: 12px;
}
</style>
