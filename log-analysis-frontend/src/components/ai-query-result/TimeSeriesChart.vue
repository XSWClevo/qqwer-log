<template>
  <div class="timeseries-chart">
    <!-- 图表类型切换 -->
    <div class="chart-controls">
      <el-radio-group v-model="chartType" size="small">
        <el-radio-button value="line">
          <el-icon><TrendCharts /></el-icon>
          折线图
        </el-radio-button>
        <el-radio-button value="bar">
          <el-icon><Histogram /></el-icon>
          柱状图
        </el-radio-button>
        <el-radio-button value="table">
          <el-icon><Grid /></el-icon>
          表格
        </el-radio-button>
      </el-radio-group>
    </div>

    <!-- 图表展示 -->
    <div v-show="chartType !== 'table'" ref="chartRef" class="chart-container"></div>

    <!-- 表格展示 -->
    <el-table v-show="chartType === 'table'" :data="data" border stripe max-height="500">
      <el-table-column
        v-for="col in columns"
        :key="col.prop"
        :prop="col.prop"
        :label="col.label"
        :width="col.width"
      >
        <template #default="{ row }">
          <span v-if="col.isTime">{{ formatTime(row[col.prop]) }}</span>
          <span v-else-if="col.isNumber">{{ formatNumber(row[col.prop]) }}</span>
          <span v-else>{{ row[col.prop] }}</span>
        </template>
      </el-table-column>
    </el-table>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, watch, onMounted, onUnmounted, nextTick } from 'vue'
import { useDark } from '@vueuse/core'
import { TrendCharts, Histogram, Grid } from '@element-plus/icons-vue'
import echarts from '@/utils/echarts'

interface Props {
  data: any[]
}

const props = defineProps<Props>()

const chartRef = ref<HTMLDivElement>()
const chartType = ref<'line' | 'bar' | 'table'>('line')
const isDark = useDark()
let chartInstance: echarts.ECharts | null = null

// 解析数据结构
const parsedData = computed(() => {
  if (!props.data || props.data.length === 0) return null

  const firstRow = props.data[0]
  const keys = Object.keys(firstRow)

  // 找出时间字段
  const timeField = keys.find(key =>
    /time|date|timestamp|hour|day|month/i.test(key)
  ) || keys[0]

  // 找出聚合字段（数值字段）
  const aggregateField = keys.find(key =>
    /count|sum|avg|max|min|total|value/i.test(key)
  )

  // 找出维度字段（分组字段）
  const dimensionField = keys.find(key =>
    key !== timeField && key !== aggregateField
  )

  if (!timeField || !aggregateField) return null

  // 按维度分组数据
  const seriesMap = new Map<string, Array<{ time: string; value: number }>>()
  const timeSet = new Set<string>()

  props.data.forEach(row => {
    const time = row[timeField]
    const value = row[aggregateField]
    const dimension = dimensionField ? row[dimensionField] : 'default'

    timeSet.add(time)

    if (!seriesMap.has(dimension)) {
      seriesMap.set(dimension, [])
    }

    seriesMap.get(dimension)!.push({ time, value })
  })

  // 排序时间点
  const timePoints = Array.from(timeSet).sort()

  // 为每个维度创建完整的时间序列
  const series: Array<{ name: string; data: number[] }> = []

  seriesMap.forEach((points, dimension) => {
    const dataMap = new Map(points.map(p => [p.time, p.value]))
    const data = timePoints.map(t => dataMap.get(t) || 0)
    series.push({ name: dimension, data })
  })

  return {
    timeField,
    aggregateField,
    dimensionField,
    timePoints,
    series
  }
})

// 表格列配置
const columns = computed(() => {
  if (!props.data || props.data.length === 0) return []

  const firstRow = props.data[0]
  return Object.keys(firstRow).map(key => {
    const isTime = /time|date|timestamp/i.test(key)
    const isNumber = typeof firstRow[key] === 'number'

    return {
      prop: key,
      label: formatFieldLabel(key),
      width: isTime ? 180 : undefined,
      isTime,
      isNumber
    }
  })
})

// 格式化字段标签
const formatFieldLabel = (field: string): string => {
  return field
    .split('_')
    .map(word => word.charAt(0).toUpperCase() + word.slice(1))
    .join(' ')
}

// 格式化时间
const formatTime = (time: string): string => {
  try {
    return new Date(time).toLocaleString('zh-CN', {
      year: 'numeric',
      month: '2-digit',
      day: '2-digit',
      hour: '2-digit',
      minute: '2-digit'
    })
  } catch {
    return time
  }
}

// 格式化数字
const formatNumber = (num: number): string => {
  return num.toLocaleString()
}

// 渲染图表
const renderChart = () => {
  if (!chartRef.value || !parsedData.value || chartType.value === 'table') return

  if (!chartInstance) {
    chartInstance = echarts.init(chartRef.value, isDark.value ? 'dark' : undefined)
  }

  const { timePoints, series, aggregateField, dimensionField } = parsedData.value

  // 格式化时间标签
  const timeLabels = timePoints.map(t => {
    try {
      const date = new Date(t)
      return date.toLocaleString('zh-CN', {
        month: '2-digit',
        day: '2-digit',
        hour: '2-digit',
        minute: '2-digit'
      })
    } catch {
      return t
    }
  })

  // 颜色方案
  const colors = [
    '#5470c6', '#91cc75', '#fac858', '#ee6666', '#73c0de',
    '#3ba272', '#fc8452', '#9a60b4', '#ea7ccc', '#d14a61'
  ]

  const option: any = {
    backgroundColor: 'transparent',
    tooltip: {
      trigger: 'axis',
      axisPointer: {
        type: 'cross',
        label: {
          backgroundColor: '#6a7985'
        }
      },
      formatter: (params: any) => {
        let html = `<div style="font-weight:600;margin-bottom:8px;">${params[0].axisValue}</div>`
        params.forEach((p: any) => {
          html += `<div style="display:flex;align-items:center;gap:8px;margin:4px 0;">
            <span style="width:10px;height:10px;border-radius:50%;background:${p.color};"></span>
            <span style="flex:1;">${p.seriesName}:</span>
            <strong>${p.value.toLocaleString()}</strong>
          </div>`
        })
        return html
      }
    },
    legend: {
      data: series.map(s => s.name),
      top: 10,
      type: series.length > 10 ? 'scroll' : 'plain'
    },
    grid: {
      left: '3%',
      right: '4%',
      bottom: '10%',
      top: series.length > 10 ? 60 : 50,
      containLabel: true
    },
    xAxis: {
      type: 'category',
      boundaryGap: chartType.value === 'bar',
      data: timeLabels,
      axisLabel: {
        rotate: timeLabels.length > 20 ? 45 : 0,
        fontSize: 11
      }
    },
    yAxis: {
      type: 'value',
      name: formatFieldLabel(aggregateField),
      axisLabel: {
        formatter: (value: number) => {
          if (value >= 1000000) return (value / 1000000).toFixed(1) + 'M'
          if (value >= 1000) return (value / 1000).toFixed(1) + 'K'
          return value.toString()
        }
      }
    },
    series: series.map((s, index) => ({
      name: s.name,
      type: chartType.value === 'bar' ? 'bar' : 'line',
      data: s.data,
      smooth: chartType.value === 'line',
      itemStyle: {
        color: colors[index % colors.length]
      },
      emphasis: {
        focus: 'series'
      },
      ...(chartType.value === 'bar' ? {
        stack: series.length > 5 ? 'total' : undefined
      } : {})
    }))
  }

  if (option) {
    chartInstance.setOption(option, true)
  }
}

// 监听图表类型变化
watch(chartType, () => {
  if (chartType.value === 'table') {
    if (chartInstance) {
      chartInstance.dispose()
      chartInstance = null
    }
  } else {
    nextTick(() => {
      renderChart()
    })
  }
})

// 监听数据变化
watch(() => props.data, () => {
  if (chartType.value !== 'table') {
    nextTick(() => {
      renderChart()
    })
  }
}, { deep: true })

// 监听暗黑模式
watch(isDark, () => {
  if (chartInstance) {
    chartInstance.dispose()
    chartInstance = null
  }
  if (chartType.value !== 'table') {
    nextTick(() => {
      renderChart()
    })
  }
})

// 生命周期
onMounted(() => {
  if (chartType.value !== 'table') {
    nextTick(() => {
      try {
        renderChart()
      } catch (error) {
        console.error('时序图表渲染失败:', error)
      }
    })
  }

  window.addEventListener('resize', handleResize)
})

onUnmounted(() => {
  if (chartInstance) {
    try {
      chartInstance.dispose()
      chartInstance = null
    } catch (error) {
      console.error('时序图表销毁失败:', error)
    }
  }
  window.removeEventListener('resize', handleResize)
})

const handleResize = () => {
  if (chartInstance && chartType.value !== 'table') {
    try {
      chartInstance.resize()
    } catch (error) {
      console.error('时序图表调整大小失败:', error)
    }
  }
}
</script>

<style scoped lang="scss">
.timeseries-chart {
  .chart-controls {
    margin-bottom: 16px;
    display: flex;
    justify-content: center;
  }

  .chart-container {
    width: 100%;
    height: 400px;
  }
}
</style>
