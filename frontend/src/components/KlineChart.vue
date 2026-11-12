<template>
  <div ref="el" class="chart" :style="{ height: height + 'px' }"></div>
</template>

<script setup>
import { ref, watch, onMounted, onUnmounted, nextTick } from 'vue'
import * as echarts from 'echarts'

const props = defineProps({
  kline: { type: Object, default: null },
  height: { type: Number, default: 560 }
})

const el = ref(null)
let chart = null
let ro = null

const UP = '#f5455c'
const DOWN = '#0ecb81'
const GRID = '#1c2330'
const AXIS = '#5d6b80'

function buildOption(k) {
  const bars = k.bars || []
  const ind = k.indicators || {}
  const dates = bars.map((b) => b.date)
  const candles = bars.map((b) => [
    Number(b.open), Number(b.close), Number(b.low), Number(b.high)
  ])
  const volumes = bars.map((b, i) => ({
    value: Number(b.volume || 0),
    itemStyle: {
      color: Number(b.close) >= Number(b.open) ? 'rgba(245,69,92,0.55)' : 'rgba(14,203,129,0.55)'
    }
  }))

  const line = (name, data, color, idx) => ({
    name,
    type: 'line',
    data,
    smooth: false,
    symbol: 'none',
    lineStyle: { width: 1, color },
    itemStyle: { color },
    xAxisIndex: idx,
    yAxisIndex: idx
  })

  const axisCommon = {
    type: 'category',
    data: dates,
    boundaryGap: false,
    axisLine: { lineStyle: { color: GRID } },
    axisLabel: { color: AXIS, fontSize: 10 },
    axisTick: { show: false },
    splitLine: { show: false }
  }

  return {
    animation: false,
    backgroundColor: 'transparent',
    tooltip: {
      trigger: 'axis',
      axisPointer: { type: 'cross', label: { backgroundColor: '#2a333f' } },
      backgroundColor: 'rgba(21,26,35,0.95)',
      borderColor: '#232a36',
      textStyle: { color: '#e6edf7', fontSize: 11 }
    },
    legend: {
      data: ['MA5', 'MA10', 'MA20', 'MA60'],
      top: 0,
      left: 50,
      itemWidth: 14,
      itemHeight: 2,
      textStyle: { color: AXIS, fontSize: 11 }
    },
    grid: [
      { left: 56, right: 16, top: 26, height: '46%' },
      { left: 56, right: 16, top: '56%', height: '12%' },
      { left: 56, right: 16, top: '72%', height: '15%' }
    ],
    xAxis: [
      { ...axisCommon, gridIndex: 0, axisLabel: { show: false } },
      { ...axisCommon, gridIndex: 1, axisLabel: { show: false } },
      { ...axisCommon, gridIndex: 2 }
    ],
    yAxis: [
      {
        gridIndex: 0, scale: true, position: 'left',
        axisLine: { show: false }, axisTick: { show: false },
        axisLabel: { color: AXIS, fontSize: 10 },
        splitLine: { lineStyle: { color: GRID, type: 'dashed' } }
      },
      {
        gridIndex: 1, scale: true, position: 'left',
        axisLine: { show: false }, axisTick: { show: false },
        axisLabel: { show: false },
        splitLine: { show: false }
      },
      {
        gridIndex: 2, scale: true, position: 'left',
        axisLine: { show: false }, axisTick: { show: false },
        axisLabel: { color: AXIS, fontSize: 10 },
        splitLine: { lineStyle: { color: GRID, type: 'dashed' } }
      }
    ],
    dataZoom: [
      { type: 'inside', xAxisIndex: [0, 1, 2], start: 60, end: 100 },
      {
        type: 'slider', xAxisIndex: [0, 1, 2],
        bottom: 4, height: 16,
        borderColor: GRID,
        fillerColor: 'rgba(59,130,246,0.12)',
        handleStyle: { color: '#3b82f6' },
        textStyle: { color: AXIS, fontSize: 10 }
      }
    ],
    series: [
      {
        name: 'K线',
        type: 'candlestick',
        data: candles,
        xAxisIndex: 0,
        yAxisIndex: 0,
        itemStyle: {
          color: UP, color0: DOWN, borderColor: UP, borderColor0: DOWN
        }
      },
      line('MA5', ind.ma5, '#f0b90b', 0),
      line('MA10', ind.ma10, '#3b82f6', 0),
      line('MA20', ind.ma20, '#c084fc', 0),
      line('MA60', ind.ma60, '#22d3ee', 0),
      {
        name: '成交量',
        type: 'bar',
        data: volumes,
        xAxisIndex: 1,
        yAxisIndex: 1
      },
      {
        name: 'MACD',
        type: 'bar',
        data: (ind.macd || []).map((v) => ({
          value: v,
          itemStyle: { color: v >= 0 ? 'rgba(245,69,92,0.7)' : 'rgba(14,203,129,0.7)' }
        })),
        xAxisIndex: 2,
        yAxisIndex: 2
      },
      line('DIF', ind.dif, '#f0b90b', 2),
      line('DEA', ind.dea, '#3b82f6', 2)
    ]
  }
}

function render() {
  if (!el.value) return
  if (!chart) {
    chart = echarts.init(el.value, null, { renderer: 'canvas' })
  }
  if (props.kline && props.kline.bars && props.kline.bars.length) {
    chart.setOption(buildOption(props.kline), true)
  }
}

watch(() => props.kline, () => nextTick(render), { deep: false })

onMounted(() => {
  render()
  ro = new ResizeObserver(() => chart && chart.resize())
  if (el.value) ro.observe(el.value)
})

onUnmounted(() => {
  if (ro) ro.disconnect()
  if (chart) {
    chart.dispose()
    chart = null
  }
})
</script>

<style scoped>
.chart {
  width: 100%;
}
</style>
