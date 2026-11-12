<template>
  <div ref="el" class="chart" :style="{ height: height + 'px' }"></div>
</template>

<script setup>
import { ref, watch, onMounted, onUnmounted, nextTick } from 'vue'
import * as echarts from 'echarts'

const props = defineProps({
  curve: { type: Array, default: () => [] },
  height: { type: Number, default: 320 }
})

const el = ref(null)
let chart = null
let ro = null

function buildOption(curve) {
  const dates = curve.map((p) => p.date)
  const strategy = curve.map((p) => Number(p.equity))
  const bench = curve.map((p) => Number(p.benchmark))
  const dd = curve.map((p) => -Number(p.drawdown || 0))

  return {
    animation: false,
    backgroundColor: 'transparent',
    tooltip: {
      trigger: 'axis',
      axisPointer: { type: 'cross', label: { backgroundColor: '#2a333f' } },
      backgroundColor: 'rgba(21,26,35,0.95)',
      borderColor: '#232a36',
      textStyle: { color: '#e6edf7', fontSize: 11 },
      valueFormatter: (v) => (v === null ? '—' : Number(v).toFixed(2))
    },
    legend: {
      data: ['策略净值', '买入持有', '回撤'],
      top: 0,
      left: 10,
      itemWidth: 14,
      itemHeight: 2,
      textStyle: { color: '#5d6b80', fontSize: 11 }
    },
    grid: { left: 62, right: 52, top: 30, bottom: 46 },
    xAxis: {
      type: 'category',
      data: dates,
      boundaryGap: false,
      axisLine: { lineStyle: { color: '#1c2330' } },
      axisLabel: { color: '#5d6b80', fontSize: 10 },
      axisTick: { show: false }
    },
    yAxis: [
      {
        type: 'value',
        scale: true,
        axisLine: { show: false },
        axisTick: { show: false },
        axisLabel: { color: '#5d6b80', fontSize: 10 },
        splitLine: { lineStyle: { color: '#1c2330', type: 'dashed' } }
      },
      {
        type: 'value',
        max: 0,
        axisLine: { show: false },
        axisTick: { show: false },
        axisLabel: { color: '#5d6b80', fontSize: 10, formatter: '{value}%' },
        splitLine: { show: false }
      }
    ],
    dataZoom: [
      { type: 'inside', start: 0, end: 100 },
      {
        type: 'slider',
        bottom: 2,
        height: 16,
        borderColor: '#1c2330',
        fillerColor: 'rgba(59,130,246,0.12)',
        handleStyle: { color: '#3b82f6' },
        textStyle: { color: '#5d6b80', fontSize: 10 }
      }
    ],
    series: [
      {
        name: '策略净值',
        type: 'line',
        data: strategy,
        symbol: 'none',
        lineStyle: { width: 1.8, color: '#3b82f6' },
        itemStyle: { color: '#3b82f6' },
        areaStyle: {
          color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
            { offset: 0, color: 'rgba(59,130,246,0.22)' },
            { offset: 1, color: 'rgba(59,130,246,0)' }
          ])
        }
      },
      {
        name: '买入持有',
        type: 'line',
        data: bench,
        symbol: 'none',
        lineStyle: { width: 1.2, color: '#8b98ab', type: 'dashed' },
        itemStyle: { color: '#8b98ab' }
      },
      {
        name: '回撤',
        type: 'line',
        data: dd,
        yAxisIndex: 1,
        symbol: 'none',
        lineStyle: { width: 0 },
        areaStyle: { color: 'rgba(245,69,92,0.16)' },
        itemStyle: { color: 'rgba(245,69,92,0.4)' }
      }
    ]
  }
}

function render() {
  if (!el.value) return
  if (!chart) {
    chart = echarts.init(el.value, null, { renderer: 'canvas' })
  }
  if (props.curve && props.curve.length) {
    chart.setOption(buildOption(props.curve), true)
  }
}

watch(() => props.curve, () => nextTick(render), { deep: false })

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
