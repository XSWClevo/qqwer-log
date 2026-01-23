<template>
  <div class="category-chart">
    <!-- 图表类型切换 -->
    <div class="chart-controls">
      <el-radio-group v-model="chartType" size="small">
        <el-radio-button value="bar">
          <el-icon><Histogram /></el-icon>
          柱状图
        </el-radio-button>
        <el-radio-button value="pie">
          <el-icon><PieChart /></el-icon>
          饼图
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
    <el-table v-show="chartType === 'table'" :data="data" border stripe>
      <el-table-column
        v-for="col in columns"
        :key="col.prop"
        :prop="col.prop"
        :label="col.label"
        :width="col.width"
      >
        <template #default="{ row }">
          <span v-if="col.isNumber">{{ formatNumber(row[col.prop]) }}</span>
          <span v-else>{{ row[col.prop] }}</span>
        </template>
      </el-table-column>
    </el-table>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, watch, onMounted, onUnmounted, nextTick } from 'vue'
import { useDark } from '@vueuse/core'
import { Histogram, PieChart, Grid } from '@element-plus/icons-vue'
import * as echarts from 'echarts'

interface Props {
  data: any[]
}

const props = defineProps<Props>()

const chartRef = ref<HTMLDivElement>()
const chartType = ref<'bar' | 'pie' | 'table'>('bar')
const isDark = useDark()
let chartInstance: echarts.ECharts | null = null

// 解析数据结构
const parsedData = computed(() => {
  if (!props.data || props.data.length === 0) return null

  const firstRow = props.data[0]
  const keys = Object.keys(firstRow)

  // 找出维度字段和聚合字段
  const aggregateField = keys.find(key =>
    /count|sum|avg|max|min|total|value/i.test(key)
  ) || keys[keys.length - 1]

  const dimensionField = keys.find(key => key !== aggregateField) || keys[0]

  if (!aggregateField || !dimensionField) return null

  return {
    dimensionField,
    aggregateField,
    categories: props.data.map(row => row[dimensionField]),
    values: props.data.map(row => row[aggregateField])
  }
})

// 表格列配置
const columns = computed(() => {
  if (!props.data || props.data.length === 0) return []

  const firstRow = props.data[0]
  return Object.keys(firstRow).map(key => ({
    prop: key,
    label: formatFieldLabel(key),
    width: undefined,
    isNumber: typeof firstRow[key] === 'number'
  }))
})

// 格式化字段标签
const formatFieldLabel = (field: string): string => {
  return field
    .split('_')
    .map(word => word.charAt(0).toUpperCase() + word.slice(1))
    .join(' ')
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

  const { categories, values, dimensionField, aggregateField } = parsedData.value

  let option: any

  if (chartType.value === 'bar') {
    // 柱状图
    option = {
      backgroundColor: 'transparent',
      tooltip: {
        trigger: 'axis',
        axisPointer: {
          type: 'shadow'
        }
      },
      grid: {
        left: '3%',
        right: '4%',
        bottom: '3%',
        containLabel: true
      },
      xAxis: {
        type: 'category',
        data: categories,
        axisLabel: {
          rotate: categories.length > 10 ? 45 : 0
        }
      },
      yAxis: {
        type: 'value',
        name: formatFieldLabel(aggregateField)
      },
      series: [
        {
          name: formatFieldLabel(aggregateField),
          type: 'bar',
          data: values,
          itemStyle: {
            color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
              { offset: 0, color: '#83bff6' },
              { offset: 0.5, color: '#188df0' },
              { offset: 1, color: '#188df0' }
            ])
          },
          emphasis: {
            itemStyle: {
              color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
                { offset: 0, color: '#2378f7' },
                { offset: 0.7, color: '#2378f7' },
                { offset: 1, color: '#83bff6' }
              ])
            }
          }
        }
      ]
    }
  } else if (chartType.value === 'pie') {
    // 饼图
    const pieData = categories.map((name, index) => ({
      name,
      value: values[index]
    }))

    option = {
      backgroundColor: 'transparent',
      tooltip: {
        trigger: 'item',
        formatter: '{a} <br/>{b}: {c} ({d}%)'
      },
      legend: {
        orient: 'vertical',
        right: '10%',
        top: 'center',
        data: categories
      },
      series: [
        {
          name: formatFieldLabel(dimensionField),
          type: 'pie',
          radius: ['40%', '70%'],
          center: ['40%', '50%'],
          avoidLabelOverlap: false,
          itemStyle: {
            borderRadius: 10,
            borderColor: '#fff',
            borderWidth: 2
          },
          label: {
            show: false,
            position: 'center'
          },
          emphasis: {
            label: {
              show: true,
              fontSize: 20,
              fontWeight: 'bold'
            }
          },
          labelLine: {
            show: false
          },
          data: pieData
        }
      ]
    }
  }

  if (option) {
    chartInstance.setOption(option, true)
  }
}

// 监听图表类型变化
watch(chartType, () => {
  if (chartType.value === 'table') {
    // 切换到表格时，销毁图表实例
    if (chartInstance) {
      chartInstance.dispose()
      chartInstance = null
    }
  } else {
    // 切换到图表时，重新渲染
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
        console.error('图表渲染失败:', error)
      }
    })
  }

  // 监听窗口大小变化
  window.addEventListener('resize', handleResize)
})

onUnmounted(() => {
  if (chartInstance) {
    try {
      chartInstance.dispose()
      chartInstance = null
    } catch (error) {
      console.error('图表销毁失败:', error)
    }
  }
  window.removeEventListener('resize', handleResize)
})

const handleResize = () => {
  if (chartInstance && chartType.value !== 'table') {
    try {
      chartInstance.resize()
    } catch (error) {
      console.error('图表调整大小失败:', error)
    }
  }
}
</script>

<style scoped lang="scss">
.category-chart {
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
