const BASE = '/api'

async function request(path, options = {}) {
  const res = await fetch(BASE + path, {
    headers: { 'Content-Type': 'application/json' },
    ...options
  })
  if (!res.ok) {
    throw new Error(`HTTP ${res.status}`)
  }
  const body = await res.json()
  if (body.code !== 0) {
    throw new Error(body.message || '请求失败')
  }
  return body.data
}

export const api = {
  overview: () => request('/dashboard/overview'),

  stocksPage: (params = {}) => {
    const q = new URLSearchParams(params).toString()
    return request(`/stocks/page?${q}`)
  },
  search: (keyword) => request(`/stocks/search?keyword=${encodeURIComponent(keyword)}`),
  stockDetail: (code) => request(`/stocks/${code}`),
  kline: (code, limit = 500, indicators = true) =>
    request(`/stocks/${code}/kline?limit=${limit}&indicators=${indicators}`),

  realtime: (codes) => request(`/quote/realtime?codes=${codes.join(',')}`),

  strategies: () => request('/backtest/strategies'),
  strategyMeta: () => request('/strategy/list'),
  runBacktest: (payload) => request('/backtest/run', {
    method: 'POST',
    body: JSON.stringify(payload)
  }),
  recentBacktests: (limit = 20) => request(`/backtest/recent?limit=${limit}`),

  runScreener: (payload) => request('/screener/run', {
    method: 'POST',
    body: JSON.stringify(payload)
  }),

  syncSymbols: () => request('/sync/symbols', { method: 'POST' }),
  syncDaily: (incremental = true, top = 300) =>
    request(`/sync/daily?incremental=${incremental}&top=${top}`, { method: 'POST' }),
  syncProgress: () => request('/sync/progress')
}
