<template>
  <AppLayout>
    <div class="log-search-container">
    <!-- 主搜索栏 -->
    <el-card class="search-toolbar" shadow="never">
      <!-- 数据源选择行 -->
      <div class="datasource-row">
        <span class="datasource-label">数据源:</span>
        <el-select
          v-model="selectedDatasource"
          placeholder="选择数据源"
          class="datasource-select"
          :multiple="isAiQuery"
          :collapse-tags="isAiQuery"
          :collapse-tags-tooltip="isAiQuery"
          :max-collapse-tags="2"
          @change="handleDatasourceChange"
          :loading="datasourceLoading"
        >
          <template #empty>
            <div class="datasource-empty">
              <p>暂无可查询的数据源</p>
              <p class="hint">请先在组件库中将 Sink 组件设为"可查询"</p>
            </div>
          </template>
          <el-option-group
            v-for="group in groupedDatasources"
            :key="group.type"
            :label="group.label"
          >
            <el-option
              v-for="ds in group.items"
              :key="ds.id"
              :label="ds.name"
              :value="ds.id"
            >
              <div class="datasource-option">
                <span>{{ ds.name }}</span>
                <span class="datasource-table">{{ ds.tableName }}</span>
              </div>
            </el-option>
          </el-option-group>
        </el-select>
        <el-tag v-if="currentDatasource && !isAiQuery" size="small" type="info" class="datasource-tag">
          {{ currentDatasource.vectorType }} / {{ currentDatasource.tableName || '-' }}
        </el-tag>
        <el-tag v-if="isAiQuery && Array.isArray(selectedDatasource) && selectedDatasource.length > 1"
                size="small" type="warning" class="datasource-tag">
          <el-icon><Connection /></el-icon>
          联合查询 {{ selectedDatasource.length }} 个数据源
        </el-tag>
        <router-link v-if="datasources.length === 0 && !datasourceLoading" to="/vector/components" class="datasource-link">
          去组件库配置 →
        </router-link>
      </div>

      <el-divider style="margin: 12px 0" />

      <div class="main-search-row">
        <!-- 统一搜索输入框 -->
        <el-input
          v-model="searchText"
          placeholder="搜索日志内容或输入自然语言查询... (支持普通搜索和AI查询)"
          class="main-search-input"
          clearable
          @keyup.enter="handleUnifiedSearch"
        >
          <template #prefix>
            <el-icon><Search /></el-icon>
          </template>
          <template #suffix>
            <el-tag v-if="isAiQuery" type="warning" size="small" style="margin-right: 8px">
              <el-icon><MagicStick /></el-icon>
              AI
            </el-tag>
          </template>
        </el-input>

        <el-select v-model="timeRange" class="time-range-select" @change="handleTimeRangeChange">
          <el-option label="最近15分钟" value="15m" />
          <el-option label="最近1小时" value="1h" />
          <el-option label="最近24小时" value="24h" />
          <el-option label="最近7天" value="7d" />
          <el-option label="自定义" value="custom" />
        </el-select>

        <el-button type="primary" @click="handleUnifiedSearch">
          <el-icon v-if="!loading"><Search /></el-icon>
          <el-icon v-else class="is-loading"><Loading /></el-icon>
          {{ loading ? '查询中...' : '查询' }}
        </el-button>

        <!-- AI查询切换按钮 -->
        <el-tooltip content="切换AI查询模式" placement="top">
          <el-button
            :type="isAiQuery ? 'warning' : ''"
            :class="{ 'ai-mode-active': isAiQuery }"
            @click="toggleAiQuery"
          >
            <el-icon><MagicStick /></el-icon>
            {{ isAiQuery ? 'AI模式' : '普通模式' }}
          </el-button>
        </el-tooltip>
      </div>

      <!-- 自定义时间范围 -->
      <div v-if="timeRange === 'custom'" class="custom-time-row">
        <el-date-picker
          v-model="customTimeRange"
          type="datetimerange"
          range-separator="至"
          start-placeholder="开始时间"
          end-placeholder="结束时间"
          value-format="YYYY-MM-DD HH:mm:ss"
          style="width: 100%"
          @change="handleSearch"
        />
      </div>

      <!-- 操作按钮行 -->
      <div class="action-buttons-row">
        <el-button @click="showAdvancedFilter = !showAdvancedFilter">
          <el-icon><Filter /></el-icon>
          高级筛选
        </el-button>
        <el-button @click="handleReset">重置</el-button>
        <el-button @click="handleExport">
          <el-icon><Download /></el-icon>
          导出
        </el-button>
      </div>

      <!-- 高级筛选面板 - 动态渲染数据源字段 -->
      <el-collapse-transition>
        <div v-show="showAdvancedFilter" class="advanced-filter">
          <el-divider />
          <el-form :inline="true">
            <el-form-item v-for="stat in fieldStats" :key="stat.name" :label="stat.label">
              <el-select
                v-model="advancedFilterValues[stat.name]"
                multiple
                clearable
                :placeholder="`选择${stat.label}`"
                style="width: 250px"
                @change="handleSearch"
              >
                <el-option
                  v-for="item in stat.topValues || []"
                  :key="item.value"
                  :label="`${item.value} (${item.count})`"
                  :value="item.value"
                />
              </el-select>
            </el-form-item>
          </el-form>
          <div v-if="fieldStats.length === 0" class="advanced-filter-empty">
            <el-text type="info">请先执行一次查询以加载可用的筛选字段</el-text>
          </div>
        </div>
      </el-collapse-transition>
    </el-card>

    <!-- 两栏布局 -->
    <TwoColumnLayout>
      <!-- 左侧边栏 - 字段过滤 -->
      <template #sidebar>
        <LeftSidebar
          :field-stats="fieldStats"
          :active-filters="activeFiltersForSidebar"
          :total-count="total"
          :time-range="getTimeRange()"
          :selected-fields="tableColumns"
          :pinned-fields="pinnedFieldNames"
          :available-fields="datasourceFields"
          :datasource-id="selectedDatasource"
          @filter="handleSidebarFilter"
          @remove-filter="handleRemoveFilter"
          @clear-filters="clearAllFilters"
          @show-chart="handleShowFieldChart"
          @pin-chart="handlePinFieldChart"
          @fields-change="handleFieldsChange"
          @stats-fields-change="handleStatsFieldsChange"
        />
      </template>

      <!-- 右侧主内容区 -->
      <template #content>
        <!-- 日志数量趋势图表 -->
        <LogVolumeTrendChart
          :series="timeSeriesData"
          :loading="chartLoading"
          v-model:auto-refresh="autoRefresh"
          @refresh="loadTimeSeries"
        />

        <!-- AI 查询结果展示 -->
        <!-- 调试信息 -->
        <el-alert v-if="aiQueryMetadata.sql" type="info" :closable="false" style="margin-bottom: 16px;">
          <template #title>
            调试信息：
            SQL存在: {{ !!aiQueryMetadata.sql }},
            结果类型: {{ aiQueryResultType }},
            显示条件: {{ aiQueryMetadata.sql && (aiQueryResultType === 'metric' || aiQueryResultType === 'category') }}
          </template>
        </el-alert>

        <AiQueryResultCard
          v-if="aiQueryMetadata.sql && (aiQueryResultType === 'metric' || aiQueryResultType === 'category' || aiQueryResultType === 'timeseries')"
          :result="logs"
          :sql="aiQueryMetadata.sql"
          :execution-time="aiQueryMetadata.executionTime"
          :result-type="aiQueryResultType"
        />

        <!-- 日志列表 -->
        <el-card class="log-list-card" shadow="never">
          <template #header>
            <div class="card-header">
              <span>日志列表 (共 {{ total }} 条)</span>
              <div class="list-controls">
                <el-switch
                  v-model="compactMode"
                  active-text="紧凑"
                  inactive-text="舒适"
                  size="small"
                />
                <el-radio-group v-model="viewMode" size="small">
                  <el-radio-button value="table">表格</el-radio-button>
                  <el-radio-button value="stats">统计</el-radio-button>
                  <el-radio-button value="raw">原始日志</el-radio-button>
                </el-radio-group>
              </div>
            </div>
          </template>

          <!-- 表格视图 -->
          <div v-if="viewMode === 'table'">
            <el-empty v-if="!loading && logs.length === 0" description="">
              <template #image>
                <el-icon :size="80" color="#909399"><Search /></el-icon>
              </template>
              <template #description>
                <div class="empty-state">
                  <h3>未找到匹配的日志</h3>
                  <p class="empty-tips">建议您尝试以下操作：</p>
                  <ul class="empty-suggestions">
                    <li>📅 扩大时间范围</li>
                    <li>🔍 检查搜索语法</li>
                    <li>🎯 清除部分过滤条件</li>
                  </ul>
                  <el-button type="primary" @click="handleReset" style="margin-top: 16px;">
                    重置所有条件
                  </el-button>
                </div>
              </template>
            </el-empty>

            <el-table
              v-else
              v-loading="loading"
              :data="logs"
              :class="{ 'compact-table': compactMode }"
              border
              stripe
              style="width: 100%"
              :expand-row-keys="expandedRowKeys"
              row-key="id"
              @expand-change="handleExpandChange"
            >
              <el-table-column type="expand">
                <template #default="{ row }">
                  <InlineRowExpansion
                    :log="row"
                    @filter="handleInlineFilter"
                  />
                </template>
              </el-table-column>

              <!-- Dynamic columns based on selected fields -->
              <template v-for="col in visibleColumns" :key="col.prop">
                <!-- Timestamp column -->
                <el-table-column
                  v-if="col.prop === 'timestamp'"
                  prop="timestamp"
                  :label="col.label"
                  :width="col.width"
                >
                  <template #default="{ row }">
                    {{ formatTimestamp(row.timestamp) }}
                  </template>
                </el-table-column>

                <!-- Level column with tag -->
                <el-table-column
                  v-else-if="col.prop === 'severity'"
                  prop="severity"
                  :label="col.label"
                  :width="col.width"
                >
                  <template #default="{ row }">
                    <el-tag
                      :type="getLevelType(row.severity)"
                      :style="getLevelStyle(row.severity)"
                      size="small"
                    >
                      {{ row.severity?.toUpperCase() }}
                    </el-tag>
                  </template>
                </el-table-column>

                <!-- Message column with monospace font -->
                <el-table-column
                  v-else-if="col.prop === 'message'"
                  prop="message"
                  :label="col.label"
                  :min-width="col.minWidth"
                  show-overflow-tooltip
                >
                  <template #default="{ row }">
                    <span class="log-message">{{ row.message }}</span>
                  </template>
                </el-table-column>

                <!-- Raw column with monospace font -->
                <el-table-column
                  v-else-if="col.prop === 'raw'"
                  prop="raw"
                  :label="col.label"
                  :min-width="col.minWidth"
                  show-overflow-tooltip
                >
                  <template #default="{ row }">
                    <span class="log-message">{{ row.raw }}</span>
                  </template>
                </el-table-column>

                <!-- Other columns -->
                <el-table-column
                  v-else
                  :prop="col.prop"
                  :label="col.label"
                  :width="col.width"
                  show-overflow-tooltip
                />
              </template>

              <el-table-column label="操作" width="140" fixed="right">
                <template #default="{ row }">
                  <div class="action-buttons">
                    <el-button type="primary" size="small" @click.stop="viewDetail(row)">
                      详情
                    </el-button>
                    <el-button type="info" size="small" @click.stop="viewContext(row)">
                      上下文
                    </el-button>
                  </div>
                </template>
              </el-table-column>
            </el-table>
          </div>

          <!-- 统计视图 -->
          <div v-else-if="viewMode === 'stats'" class="stats-view">
            <!-- 固定的图表 -->
            <el-row v-if="pinnedCharts.length > 0" :gutter="20" class="pinned-charts-row">
              <el-col :span="12" v-for="chart in pinnedCharts" :key="chart.id">
                <el-card class="pinned-chart-card" shadow="hover">
                  <template #header>
                    <div class="pinned-chart-header">
                      <span>{{ chart.label }} - 时序统计</span>
                      <div class="chart-actions">
                        <el-radio-group v-model="chart.type" size="small">
                          <el-radio-button value="bar">柱状图</el-radio-button>
                          <el-radio-button value="line">折线图</el-radio-button>
                        </el-radio-group>
                        <el-button size="small" type="danger" text @click="removePinnedChart(chart.id)">
                          <el-icon><Close /></el-icon>
                        </el-button>
                      </div>
                    </div>
                  </template>
                  <div :ref="el => setPinnedChartRef(chart.id, el)" :data-chart-id="chart.id" class="pinned-chart-container"></div>
                </el-card>
              </el-col>
            </el-row>
            <!-- 空状态提示 -->
            <el-empty v-if="pinnedCharts.length === 0" description="暂无固定的统计图表">
              <template #image>
                <el-icon :size="60" color="#909399"><DataAnalysis /></el-icon>
              </template>
              <template #description>
                <div class="stats-empty-tips">
                  <p>点击左侧字段面板的 <el-icon><DataAnalysis /></el-icon> 图标查看时序图表</p>
                  <p>点击 <el-icon><Star /></el-icon> 图标将图表固定到此视图</p>
                </div>
              </template>
            </el-empty>
          </div>

          <!-- 原始日志视图 -->
          <div v-else-if="viewMode === 'raw'" class="raw-log-view">
            <el-scrollbar max-height="600px">
              <div class="raw-log-container">
                <pre v-for="log in logs" :key="log.id" class="raw-log-line">{{ log.raw || log.message }}</pre>
              </div>
            </el-scrollbar>
          </div>

          <!-- 分页 - 统计视图不显示分页 -->
          <div v-if="viewMode !== 'stats'" class="pagination-wrapper">
            <el-pagination
              v-model:current-page="pagination.pageNum"
              v-model:page-size="pagination.pageSize"
              :page-sizes="[20, 50, 100, 200]"
              :total="total"
              layout="total, sizes, prev, pager, next, jumper"
              @size-change="handleSearch"
              @current-change="handleSearch"
            />
          </div>
        </el-card>
      </template>
    </TwoColumnLayout>

    <!-- 日志详情抽屉 -->
    <el-drawer
      v-model="detailVisible"
      title="日志详情"
      direction="rtl"
      size="65%"
    >
      <div v-if="currentLog" class="log-drawer-content">
        <InlineRowExpansion
          :log="currentLog"
          @filter="handleInlineFilter"
        />
      </div>
    </el-drawer>

    <!-- 上下文对话框 -->
    <el-dialog
      v-model="contextVisible"
      title="日志上下文"
      width="90%"
      top="5vh"
    >
      <div v-loading="contextLoading" class="context-dialog">
        <el-alert v-if="contextTargetLog" type="warning" :closable="false" show-icon class="target-log-alert">
          <template #title>
            <strong>目标日志:</strong> {{ contextTargetLog.timestamp }}
            <el-tag :style="getLevelStyle(contextTargetLog.severity)" size="small" style="margin: 0 8px;">
              {{ contextTargetLog.severity?.toUpperCase() }}
            </el-tag>
            {{ contextTargetLog.message?.slice(0, 100) }}...
          </template>
        </el-alert>
        <el-table 
          ref="contextTableRef"
          :data="contextLogs" 
          size="small" 
          stripe 
          height="60vh" 
          :row-class-name="getContextRowClass"
        >
          <el-table-column type="index" label="#" width="60" />
          <!-- Dynamic columns matching main table -->
          <template v-for="col in visibleColumns" :key="col.prop">
            <el-table-column
              v-if="col.prop === 'timestamp'"
              prop="timestamp"
              :label="col.label"
              width="180"
            >
              <template #default="{ row }">{{ formatTimestamp(row.timestamp) }}</template>
            </el-table-column>
            <el-table-column
              v-else-if="col.prop === 'severity'"
              prop="severity"
              :label="col.label"
              width="100"
            >
              <template #default="{ row }">
                <el-tag :style="getLevelStyle(row.severity)" size="small">{{ row.severity?.toUpperCase() }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column
              v-else-if="col.prop === 'message' || col.prop === 'raw'"
              :prop="col.prop"
              :label="col.label"
              show-overflow-tooltip
            />
            <el-table-column
              v-else
              :prop="col.prop"
              :label="col.label"
              :width="col.width"
              show-overflow-tooltip
            />
          </template>
        </el-table>
      </div>
    </el-dialog>

    <!-- 字段图表弹窗 -->
    <el-dialog
      v-model="fieldChartVisible"
      :title="`${currentFieldChart.label} - 时序统计`"
      width="900px"
    >
      <div class="field-chart-controls">
        <el-radio-group v-model="fieldChartType" size="small">
          <el-radio-button value="bar">柱状图</el-radio-button>
          <el-radio-button value="line">折线图</el-radio-button>
        </el-radio-group>
        <el-button type="primary" size="small" @click="pinCurrentChart">
          <el-icon><Star /></el-icon>
          固定到统计视图
        </el-button>
      </div>
      <div ref="fieldChartRef" class="field-chart-container"></div>
    </el-dialog>
    </div>
  </AppLayout>
</template>


<script setup lang="ts">
import { ref, reactive, onMounted, onUnmounted, watch, computed, nextTick } from 'vue'
import { useRoute } from 'vue-router'
import { useDark } from '@vueuse/core'
import { Search, Filter, Download, Loading, Close, Star, DataAnalysis, MagicStick, Connection } from '@element-plus/icons-vue'
import echarts from '@/utils/echarts'
import * as yaml from 'js-yaml'
import AppLayout from '@/components/layout/AppLayout.vue'
import { ElMessage } from 'element-plus'
import { queryLogs, queryTimeSeries, exportLogs, queryFieldStats, queryLogContext, queryFieldTimeSeries, getDatasourceSchema, aiQuery, type FieldInfo, type AiQueryResponse } from '@/api/log'
import { getPinnedChartsConfig, savePinnedChartsConfig, type PinnedChartConfig } from '@/api/field-config'
import { configComponentApi, type ConfigComponent } from '@/api/vector'
import type { LogEntry, TimeRangeType, FieldStats, FilterCondition, MessageCondition, FieldFilter } from '@/types/log'

// Import new components
import TwoColumnLayout from '@/components/log-search/TwoColumnLayout.vue'
import LeftSidebar from '@/components/log-search/LeftSidebar.vue'
import LogVolumeTrendChart from '@/components/log-search/LogVolumeTrendChart.vue'
import InlineRowExpansion from '@/components/log-search/InlineRowExpansion.vue'
import AiQueryResultCard from '@/components/ai-query-result/AiQueryResultCard.vue'

// Route
const route = useRoute()

// State
const loading = ref(false)
const chartLoading = ref(false)
const showAdvancedFilter = ref(false)
const autoRefresh = ref(false)
const viewMode = ref<'table' | 'stats' | 'raw'>('table')
const compactMode = ref(false)
const detailVisible = ref(false)
const timeRange = ref<TimeRangeType>('24h')
const customTimeRange = ref<[string, string]>()
const searchText = ref('') // 统一搜索文本
const isAiQuery = ref(false) // 是否使用AI查询模式
const isDark = useDark()

// 数据源相关
const datasources = ref<ConfigComponent[]>([])
const selectedDatasource = ref<string | string[]>('') // 支持单选和多选
const datasourceLoading = ref(false)

// 数据源字段信息（动态获取）
const datasourceFields = ref<FieldInfo[]>([])
const schemaLoading = ref(false)

// 计算可用的统计维度字段
const availableStatsDimensions = computed(() => {
  return datasourceFields.value.filter(f => f.isStatsDimension)
})

// 计算可用的内容字段（用于搜索）
const availableContentFields = computed(() => {
  return datasourceFields.value.filter(f => f.isContentField)
})

// 计算时间戳字段
const timestampField = computed(() => {
  return datasourceFields.value.find(f => f.isTimestamp)?.name || 'timestamp'
})

// 从 configYaml 中解析表名
const getTableNameFromYaml = (configYaml: string): string => {
  if (!configYaml) return ''
  try {
    const parsed = yaml.load(configYaml) as Record<string, any>
    return parsed?.table || parsed?.index || parsed?.topic || ''
  } catch {
    return ''
  }
}

// 按类型分组的数据源
const groupedDatasources = computed(() => {
  console.log('计算 groupedDatasources, datasources:', datasources.value)
  
  if (!datasources.value || datasources.value.length === 0) {
    return []
  }
  
  const groups: Record<string, { type: string; label: string; items: Array<{ id: string; name: string; vectorType: string; tableName: string }> }> = {}
  
  const typeLabels: Record<string, string> = {
    clickhouse: 'ClickHouse',
    elasticsearch: 'Elasticsearch',
    postgresql: 'PostgreSQL',
    mysql: 'MySQL',
    kafka: 'Kafka',
    loki: 'Loki'
  }
  
  datasources.value.forEach(ds => {
    const type = ds.vectorType
    if (!type) {
      console.warn('数据源缺少 vectorType:', ds)
      return
    }
    if (!groups[type]) {
      groups[type] = {
        type,
        label: typeLabels[type] || type,
        items: []
      }
    }
    groups[type].items.push({
      id: ds.id,
      name: ds.displayName || ds.name,
      vectorType: ds.vectorType,
      tableName: getTableNameFromYaml(ds.configYaml)
    })
  })
  
  const result = Object.values(groups)
  console.log('groupedDatasources 结果:', result)
  return result
})

// 当前选中的数据源详情
const currentDatasource = computed(() => {
  // 如果是多选模式，返回 null
  if (Array.isArray(selectedDatasource.value)) {
    return null
  }

  if (!selectedDatasource.value) return null
  const ds = datasources.value.find(d => d.id === selectedDatasource.value)
  if (!ds) return null
  return {
    ...ds,
    tableName: getTableNameFromYaml(ds.configYaml)
  }
})

// 加载可查询的数据源列表
const loadDatasources = async () => {
  datasourceLoading.value = true
  try {
    const res = await configComponentApi.getQueryableDataSources() as any
    // 处理后端返回的 Result 包装
    datasources.value = res.data || res || []

    console.log('加载数据源:', datasources.value)

    // 如果 URL 中有 datasource 参数，自动选中
    const dsId = route.query.datasource as string
    if (dsId && datasources.value.some(d => d.id === dsId)) {
      selectedDatasource.value = isAiQuery.value ? [dsId] : dsId
    } else if (datasources.value.length > 0 && !selectedDatasource.value) {
      // 默认选中第一个
      selectedDatasource.value = isAiQuery.value ? [datasources.value[0].id] : datasources.value[0].id
    }
  } catch (error) {
    console.error('加载数据源失败:', error)
  } finally {
    datasourceLoading.value = false
  }
}

// 加载数据源的字段信息
const loadDatasourceSchema = async () => {
  // 获取当前选中的数据源ID（单选或多选的第一个）
  let datasourceId = ''
  if (Array.isArray(selectedDatasource.value)) {
    datasourceId = selectedDatasource.value.length > 0 ? selectedDatasource.value[0] : ''
  } else {
    datasourceId = selectedDatasource.value
  }

  if (!datasourceId) {
    // 没有选择数据源时，使用默认字段
    datasourceFields.value = getDefaultFields()
    initializeTableColumns()
    initializeStatsFields()
    return
  }

  schemaLoading.value = true
  try {
    const res = await getDatasourceSchema(datasourceId) as any
    datasourceFields.value = res.data || res || []
    console.log('加载数据源字段:', datasourceFields.value)

    // 初始化表格列
    initializeTableColumns()

    // 初始化统计字段
    initializeStatsFields()

    // 更新 fieldStats 使用实际的统计维度字段（已废弃，由 initializeStatsFields 替代）
    // updateFieldStatsFromSchema()
  } catch (error) {
    console.error('加载数据源字段失败:', error)
    // 使用默认字段
    datasourceFields.value = getDefaultFields()
    initializeTableColumns()
    initializeStatsFields()
  } finally {
    schemaLoading.value = false
  }
}

// 获取默认字段（用于默认 ClickHouse syslog 表）
const getDefaultFields = (): FieldInfo[] => {
  return [
    { name: 'timestamp', type: 'DateTime', label: '时间戳', isTimestamp: true, isStatsDimension: false, isContentField: false },
    { name: 'severity', type: 'String', label: '日志级别', isTimestamp: false, isStatsDimension: true, isContentField: false },
    { name: 'source_type', type: 'String', label: '来源类型', isTimestamp: false, isStatsDimension: true, isContentField: false },
    { name: 'hostname', type: 'String', label: '主机', isTimestamp: false, isStatsDimension: true, isContentField: false },
    { name: 'appname', type: 'String', label: '应用名', isTimestamp: false, isStatsDimension: true, isContentField: false },
    { name: 'message', type: 'String', label: '消息', isTimestamp: false, isStatsDimension: false, isContentField: true },
    { name: 'raw', type: 'String', label: '原始日志', isTimestamp: false, isStatsDimension: false, isContentField: true },
  ]
}

// 根据字段信息更新 fieldStats（已废弃，由 initializeStatsFields 替代）
// const updateFieldStatsFromSchema = () => {
//   const dimensions = availableStatsDimensions.value
//   if (dimensions.length > 0) {
//     fieldStats.value = dimensions.slice(0, 6).map(f => ({
//       name: f.name,
//       label: f.label || f.name,
//       topValues: []
//     }))
//   }
// }

// 数据源切换处理
const handleDatasourceChange = async () => {
  // 只加载字段信息，不自动触发查询
  await loadDatasourceSchema()
  // 移除自动查询，由用户手动点击查询按钮
  // handleSearch()
}

// Expanded rows
const expandedRowKeys = ref<string[]>([])

// Context dialog
const contextVisible = ref(false)
const contextLoading = ref(false)
const contextTargetLog = ref<LogEntry | null>(null)
const contextLogs = ref<LogEntry[]>([])

// Field chart dialog
const fieldChartVisible = ref(false)
const fieldChartRef = ref<HTMLDivElement>()
const fieldChartType = ref<'bar' | 'line'>('bar')
const currentFieldChart = ref<{ name: string; label: string; data: any[] }>({ name: '', label: '', data: [] })
let fieldChartInstance: echarts.ECharts | null = null

// Pinned charts in stats view
interface PinnedChart {
  id: string
  name: string
  label: string
  type: 'bar' | 'line'
  data: any[]
}
const pinnedCharts = ref<PinnedChart[]>([])
const pinnedChartRefs = new Map<string, echarts.ECharts>()

// Computed pinned field names for sidebar
const pinnedFieldNames = computed(() => {
  return pinnedCharts.value.map(c => c.name)
})

// Data
const logs = ref<LogEntry[]>([])
const total = ref(0)
const pagination = reactive({
  pageNum: 1,
  pageSize: 50
})
const currentLog = ref<LogEntry | null>(null)

// Time series data
const timeSeriesData = ref<Array<{ timestamp: string; count: number }>>([])

// Field statistics (动态初始化)
const fieldStats = ref<FieldStats[]>([])

// Stats view fields (动态初始化)
const statsViewFields = ref<Array<{ name: string; label: string }>>([])

// 初始化统计字段（使用数据源的统计维度字段）
const initializeStatsFields = () => {
  const statsDimensions = datasourceFields.value.filter(f => f.isStatsDimension)

  if (statsDimensions.length > 0) {
    // 使用数据源的统计维度字段
    fieldStats.value = statsDimensions.map(f => ({
      name: f.name,
      label: f.label,
      topValues: []
    }))

    statsViewFields.value = statsDimensions.map(f => ({
      name: f.name,
      label: f.label
    }))
  } else {
    // 默认统计字段
    fieldStats.value = [
      { name: 'severity', label: '日志级别', topValues: [] },
      { name: 'source_type', label: '来源类型', topValues: [] },
      { name: 'hostname', label: '主机', topValues: [] },
      { name: 'appname', label: '应用名', topValues: [] }
    ]

    statsViewFields.value = [
      { name: 'severity', label: '日志级别' },
      { name: 'source_type', label: '来源类型' },
      { name: 'hostname', label: '主机名' },
      { name: 'appname', label: '应用名' }
    ]
  }
}

// Table columns configuration
const tableColumns = ref<string[]>([])

// 初始化表格列（根据数据源字段）
const initializeTableColumns = () => {
  if (datasourceFields.value.length > 0) {
    // 使用数据源的前5个字段
    tableColumns.value = datasourceFields.value.slice(0, 5).map(f => f.name)
  } else {
    // 默认字段
    tableColumns.value = ['timestamp', 'severity', 'hostname', 'appname', 'message']
  }

  // 同时初始化列定义
  initializeColumnDefs()
}

// Column definitions (动态生成，改为 ref 以支持运行时修改)
const COLUMN_DEFS = ref<Record<string, { label: string; width?: number; minWidth?: number }>>({})

// 初始化列定义
const initializeColumnDefs = () => {
  const defs: Record<string, { label: string; width?: number; minWidth?: number }> = {}

  // 从数据源字段生成列定义
  datasourceFields.value.forEach(field => {
    if (field.isTimestamp) {
      defs[field.name] = { label: field.label, width: 180 }
    } else if (field.isStatsDimension) {
      defs[field.name] = { label: field.label, width: 120 }
    } else if (field.isContentField) {
      defs[field.name] = { label: field.label, minWidth: 200 }
    } else {
      defs[field.name] = { label: field.label, width: 120 }
    }
  })

  // 如果没有数据源字段，使用默认定义
  if (Object.keys(defs).length === 0) {
    Object.assign(defs, {
      timestamp: { label: '时间戳', width: 180 },
      severity: { label: '级别', width: 100 },
      hostname: { label: '主机', width: 150 },
      appname: { label: '应用', width: 120 },
      source_type: { label: '来源', width: 120 },
      message: { label: '消息', minWidth: 200 },
      facility: { label: '设施', width: 100 },
      procid: { label: '进程ID', width: 100 },
      source_ip: { label: '来源IP', width: 140 },
      raw: { label: '原始日志', minWidth: 300 }
    })
  }

  COLUMN_DEFS.value = defs
}

// Computed visible columns based on selected fields
const visibleColumns = computed(() => {
  return tableColumns.value.map(field => ({
    prop: field,
    ...COLUMN_DEFS.value[field]
  }))
})

// Handle fields change from sidebar
const handleFieldsChange = (fields: string[]) => {
  tableColumns.value = fields
}

// Handle stats fields change from sidebar
const handleStatsFieldsChange = (fields: Array<{ name: string; label: string }>) => {
  // 更新 fieldStats 和 statsViewFields
  fieldStats.value = fields.map(f => ({
    name: f.name,
    label: f.label,
    topValues: []
  }))

  statsViewFields.value = fields.map(f => ({
    name: f.name,
    label: f.label
  }))

  // 重新加载统计数据
  loadFieldStats()
}

// Search form
interface SearchForm {
  filters: FilterCondition[]
  messageConditions: MessageCondition[]
  rawConditions: MessageCondition[]
  levels: string[]
  sources: string[]
  hosts: string[]
  services: string[]
}

const searchForm = reactive<SearchForm>({
  filters: [],
  messageConditions: [],
  rawConditions: [],
  levels: [],
  sources: [],
  hosts: [],
  services: []
})

// 高级筛选动态值：key 为字段名，value 为选中的值数组
const advancedFilterValues = reactive<Record<string, string[]>>({})

// Convert filters for sidebar display
const activeFiltersForSidebar = computed(() => {
  return searchForm.filters.map((f, index) => ({
    id: `filter-${index}`,
    field: f.field,
    fieldLabel: getFieldLabel(f.field),
    value: f.value,
    type: f.type
  }))
})

// Field label mapping
const getFieldLabel = (field: string): string => {
  const labelMap: Record<string, string> = {
    source_types: '来源类型',
    hostnames: '主机',
    appnames: '应用名',
    severity: '日志级别',
    source_type: '来源类型',
    hostname: '主机',
    appname: '应用名'
  }
  return labelMap[field] || field
}

// Convert filters to backend format
const convertFiltersToBackendFormat = (filters: FilterCondition[]): FieldFilter[] => {
  const groupedMap = new Map<string, FieldFilter>()
  filters.forEach(filter => {
    const key = `${filter.field}_${filter.type}`
    if (groupedMap.has(key)) {
      const existing = groupedMap.get(key)!
      if (!existing.values.includes(filter.value)) {
        existing.values.push(filter.value)
      }
    } else {
      groupedMap.set(key, {
        field: filter.field,
        type: filter.type,
        values: [filter.value]
      })
    }
  })
  return Array.from(groupedMap.values())
}

// 构建完整的字段筛选条件（侧边栏筛选 + 高级筛选）
const buildFieldFilters = (): FieldFilter[] | undefined => {
  const allFilters: FieldFilter[] = []

  // 1. 添加侧边栏筛选（已经是 FilterCondition[] 格式，需要转换）
  if (searchForm.filters.length > 0) {
    const sidebarFilters = convertFiltersToBackendFormat(searchForm.filters)
    allFilters.push(...sidebarFilters)
  }

  // 2. 添加高级筛选（动态字段，从 advancedFilterValues 获取）
  Object.entries(advancedFilterValues).forEach(([fieldName, values]) => {
    if (values && values.length > 0) {
      allFilters.push({ field: fieldName, type: 'include', values })
    }
  })

  // 兼容旧的硬编码字段（如果通过其他方式设置了值）
  if (searchForm.levels.length > 0) {
    allFilters.push({ field: 'severity', type: 'include', values: searchForm.levels })
  }
  if (searchForm.sources.length > 0) {
    allFilters.push({ field: 'source_type', type: 'include', values: searchForm.sources })
  }
  if (searchForm.hosts.length > 0) {
    allFilters.push({ field: 'hostname', type: 'include', values: searchForm.hosts })
  }
  if (searchForm.services.length > 0) {
    allFilters.push({ field: 'appname', type: 'include', values: searchForm.services })
  }

  return allFilters.length > 0 ? allFilters : undefined
}

// Time range calculation
const formatDateToLocal = (date: Date): string => {
  const year = date.getFullYear()
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')
  const hours = String(date.getHours()).padStart(2, '0')
  const minutes = String(date.getMinutes()).padStart(2, '0')
  const seconds = String(date.getSeconds()).padStart(2, '0')
  return `${year}-${month}-${day} ${hours}:${minutes}:${seconds}`
}

const getTimeRange = (): [string, string] => {
  if (timeRange.value === 'custom' && customTimeRange.value) {
    return customTimeRange.value
  }
  const now = new Date()
  const end = formatDateToLocal(now)
  let start: Date
  switch (timeRange.value) {
    case '15m': start = new Date(now.getTime() - 15 * 60 * 1000); break
    case '1h': start = new Date(now.getTime() - 60 * 60 * 1000); break
    case '24h': start = new Date(now.getTime() - 24 * 60 * 60 * 1000); break
    case '7d': start = new Date(now.getTime() - 7 * 24 * 60 * 60 * 1000); break
    default: start = new Date(now.getTime() - 24 * 60 * 60 * 1000)
  }
  return [formatDateToLocal(start), end]
}

// Search handlers
// 切换AI查询模式
const toggleAiQuery = () => {
  const previousMode = isAiQuery.value
  isAiQuery.value = !isAiQuery.value

  // 切换模式时重置数据源选择（不触发 change 事件）
  if (isAiQuery.value) {
    // 切换到AI模式：如果当前是单选，转换为数组；如果没选，清空
    if (typeof selectedDatasource.value === 'string' && selectedDatasource.value) {
      selectedDatasource.value = [selectedDatasource.value]
    } else {
      selectedDatasource.value = []
    }
    ElMessage.info('已切换到AI查询模式，支持多数据源联合查询')
  } else {
    // 切换到普通模式：如果是多选，取第一个；如果是空数组，清空
    if (Array.isArray(selectedDatasource.value)) {
      selectedDatasource.value = selectedDatasource.value.length > 0 ? selectedDatasource.value[0] : ''
    }
    ElMessage.info('已切换到普通查询模式')
  }

  // 清空搜索结果，避免显示不匹配的数据
  logs.value = []
  total.value = 0
  aiQueryMetadata.value = {}
  aiQueryResultType.value = 'list'
}

// 统一搜索处理
const handleUnifiedSearch = () => {
  if (isAiQuery.value) {
    // AI查询模式
    handleAiQuery()
  } else {
    // 普通查询模式
    handleMessageSearch()
  }
}

const handleMessageSearch = () => {
  if (searchText.value?.trim()) {
    searchForm.messageConditions = [{ operator: 'contains', value: searchText.value.trim() }]
    searchForm.rawConditions = [{ operator: 'contains', value: searchText.value.trim() }]
  } else {
    searchForm.messageConditions = []
    searchForm.rawConditions = []
  }
  handleSearch()
}

// AI查询结果类型
const aiQueryResultType = ref<'list' | 'metric' | 'category' | 'timeseries'>('list')
const aiQueryMetadata = ref<{ sql?: string; executionTime?: number }>({})

// AI查询处理
const handleAiQuery = async () => {
  if (!searchText.value?.trim()) {
    ElMessage.warning('请输入查询内容')
    return
  }

  loading.value = true
  try {
    // 构建请求参数
    const requestData: any = {
      query: searchText.value.trim()
    }

    // 判断是单数据源还是多数据源
    if (Array.isArray(selectedDatasource.value)) {
      if (selectedDatasource.value.length > 1) {
        // 多数据源联合查询
        requestData.datasourceIds = selectedDatasource.value
      } else if (selectedDatasource.value.length === 1) {
        // 单数据源
        requestData.datasourceId = selectedDatasource.value[0]
      }
      // 如果是空数组，不传数据源参数，使用默认
    } else if (selectedDatasource.value) {
      // 单数据源（字符串）
      requestData.datasourceId = selectedDatasource.value
    }

    const { data } = await aiQuery(requestData)

    if (data.success) {
      // 保存查询元数据
      aiQueryMetadata.value = {
        sql: data.sql || undefined,
        executionTime: data.totalExecutionTime || undefined
      }

      // 显示生成的SQL和执行时间
      ElMessage.success({
        message: `查询成功！SQL生成: ${data.sqlGenerationTime}秒, SQL执行: ${data.sqlExecutionTime}秒`,
        duration: 3000
      })

      // 显示SQL（可选）
      if (data.sql) {
        console.log('生成的SQL:', data.sql)
      }

      // 智能识别结果类型并处理
      if (data.result) {
        console.log('=== AI 查询结果调试 ===')
        console.log('原始结果:', data.result)
        console.log('结果类型:', typeof data.result)
        console.log('是否数组:', Array.isArray(data.result))

        handleAiQueryResult(data.result)

        console.log('识别的结果类型:', aiQueryResultType.value)
        console.log('SQL:', aiQueryMetadata.value.sql)
        console.log('logs.value:', logs.value)
        console.log('========================')
      }
    } else {
      ElMessage.error(`AI查询失败: ${data.error || '未知错误'}`)
    }
  } catch (error: any) {
    console.error('AI查询失败:', error)
    ElMessage.error(`AI查询失败: ${error.message || '网络错误'}`)
  } finally {
    loading.value = false
  }
}

// 智能识别并处理 AI 查询结果
const handleAiQueryResult = (result: any) => {
  // 1. 列表数据（数组）
  if (Array.isArray(result)) {
    if (result.length === 0) {
      ElMessage.info('查询结果为空')
      logs.value = []
      total.value = 0
      aiQueryResultType.value = 'list'
      return
    }

    const firstRow = result[0]

    // 检查是否是时序统计（优先级最高）
    if (isTimeSeriesResult(firstRow)) {
      aiQueryResultType.value = 'timeseries'
      logs.value = result
      total.value = result.length
      generateDynamicColumns(firstRow)
      return
    }

    // 检查是否是聚合结果（只有一行且包含聚合函数字段）
    if (result.length === 1 && isAggregateResult(firstRow)) {
      aiQueryResultType.value = 'metric'
      displayAggregateResult(firstRow)
    } else if (isAggregateResult(firstRow)) {
      // 多行聚合结果（分类统计）
      aiQueryResultType.value = 'category'
      logs.value = result
      total.value = result.length
      generateDynamicColumns(firstRow)
    } else {
      // 普通列表数据
      aiQueryResultType.value = 'list'
      logs.value = result
      total.value = result.length

      // 动态生成表头
      generateDynamicColumns(firstRow)
    }
  }
  // 2. 单个对象
  else if (typeof result === 'object' && result !== null) {
    // 检查是否是聚合结果
    if (isAggregateResult(result)) {
      aiQueryResultType.value = 'metric'
      displayAggregateResult(result)
    } else {
      // 单行数据，作为列表展示
      aiQueryResultType.value = 'list'
      logs.value = [result]
      total.value = 1
      generateDynamicColumns(result)
    }
  }
  // 3. 单值结果（数字、字符串等）
  else {
    aiQueryResultType.value = 'metric'
    // 将单值包装成对象
    logs.value = [{ value: result }]
    total.value = 1
    ElMessage.info({
      message: `查询结果: ${result}`,
      duration: 5000,
      showClose: true
    })
  }
}

// 判断是否是时序统计结果
const isTimeSeriesResult = (obj: any): boolean => {
  if (!obj || typeof obj !== 'object') return false

  const keys = Object.keys(obj)
  // 必须有时间字段
  const hasTimeField = keys.some(key =>
    /time|date|timestamp|hour|day|month/i.test(key)
  )

  // 必须有聚合字段
  const hasAggregateField = keys.some(key =>
    /count|sum|avg|max|min|total/i.test(key)
  )

  return hasTimeField && hasAggregateField
}

// 判断是否是聚合结果
const isAggregateResult = (obj: any): boolean => {
  if (!obj || typeof obj !== 'object') return false

  const keys = Object.keys(obj)
  // 常见的聚合函数字段名模式
  const aggregatePatterns = [
    /^count/i,
    /^sum/i,
    /^avg/i,
    /^max/i,
    /^min/i,
    /^total/i,
    /^average/i,
    /_count$/i,
    /_sum$/i,
    /_avg$/i
  ]

  return keys.some(key =>
    aggregatePatterns.some(pattern => pattern.test(key))
  )
}

// 展示聚合结果（使用卡片形式）
const displayAggregateResult = (result: any) => {
  // 将聚合结果转换为卡片数据
  const cards = Object.entries(result).map(([key, value]) => ({
    label: formatFieldLabel(key),
    value: value,
    key: key
  }))

  // 使用 ElMessage 展示（临时方案，后续可以改为专门的卡片组件）
  const message = cards.map(c => `${c.label}: ${c.value}`).join(', ')
  ElMessage.success({
    message: `统计结果 - ${message}`,
    duration: 8000,
    showClose: true
  })

  // 同时在表格中展示（作为单行数据）
  logs.value = [result]
  total.value = 1
  generateDynamicColumns(result)
}

// 动态生成表格列
const generateDynamicColumns = (sampleRow: any) => {
  if (!sampleRow || typeof sampleRow !== 'object') return

  const fields = Object.keys(sampleRow)

  // 更新表格列配置
  tableColumns.value = fields

  // 更新列定义（如果字段不在 COLUMN_DEFS 中）
  fields.forEach(field => {
    if (!COLUMN_DEFS.value[field]) {
      // 动态添加列定义
      COLUMN_DEFS.value[field] = {
        label: formatFieldLabel(field),
        width: 120
      }
    }
  })

  console.log('动态生成表头:', tableColumns.value)
}

// 格式化字段标签（将下划线转为空格，首字母大写）
const formatFieldLabel = (field: string): string => {
  return field
    .split('_')
    .map(word => word.charAt(0).toUpperCase() + word.slice(1))
    .join(' ')
}


const handleSearch = async () => {
  loading.value = true
  try {
    const [startTime, endTime] = getTimeRange()
    const params = {
      datasourceId: selectedDatasource.value || undefined, // 添加数据源ID
      startTime,
      endTime,
      fieldFilters: buildFieldFilters(), // 使用新函数合并侧边栏筛选和高级筛选
      messageConditions: searchForm.messageConditions.length > 0 ? searchForm.messageConditions : undefined,
      rawConditions: searchForm.rawConditions.length > 0 ? searchForm.rawConditions : undefined,
      pageNum: pagination.pageNum,
      pageSize: pagination.pageSize,
      useMcp: false
    }
    const { data } = await queryLogs(params)
    logs.value = data.data
    total.value = data.total
    await Promise.all([loadTimeSeries(), loadFieldStats()])
  } catch (error) {
    ElMessage.error('查询失败')
  } finally {
    loading.value = false
  }
}

const loadTimeSeries = async () => {
  chartLoading.value = true
  try {
    const [startTime, endTime] = getTimeRange()
    const timeRangeMs = new Date(endTime).getTime() - new Date(startTime).getTime()
    const hours = timeRangeMs / (1000 * 60 * 60)
    let granularity = '1h'
    if (hours <= 1) granularity = '1m'
    else if (hours <= 6) granularity = '5m'
    else if (hours <= 48) granularity = '1h'
    else granularity = '1d'

    const { data } = await queryTimeSeries({ 
      datasourceId: selectedDatasource.value || undefined,
      startTime, 
      endTime, 
      granularity,
      useMcp: false
    })
    timeSeriesData.value = data.series || []
  } catch (error) {
    console.error('加载时间序列数据失败:', error)
  } finally {
    chartLoading.value = false
  }
}

const loadFieldStats = async () => {
  try {
    const [startTime, endTime] = getTimeRange()
    
    // 使用动态获取的统计维度字段
    const dimensions = availableStatsDimensions.value.length > 0 
      ? availableStatsDimensions.value.slice(0, 6).map(f => f.name)
      : ['severity', 'source_type', 'hostname', 'appname'] // 默认字段
    
    const { data } = await queryFieldStats({
      datasourceId: selectedDatasource.value || undefined,
      startTime,
      endTime,
      dimensions,
      metrics: ['count'],
      useMcp: false
    })
    if (data.data) {
      fieldStats.value.forEach(stat => {
        const statsData = data.data[stat.name]
        if (statsData && Array.isArray(statsData)) {
          stat.topValues = statsData.slice(0, 10)
        }
      })
    }
  } catch (error) {
    console.error('加载字段统计失败:', error)
  }
}

// Filter handlers
const handleSidebarFilter = (field: string, value: string, type: 'include' | 'exclude') => {
  // 不再使用固定的字段映射，直接使用字段名
  searchForm.filters.push({ field, value, type })
  ElMessage.success(`已添加${type === 'include' ? '筛选' : '排除'}: ${field} = ${value}`)
  handleSearch()
}

const handleRemoveFilter = (filterId: string) => {
  const index = parseInt(filterId.replace('filter-', ''))
  if (!isNaN(index) && index >= 0 && index < searchForm.filters.length) {
    searchForm.filters.splice(index, 1)
    handleSearch()
  }
}

const clearAllFilters = () => {
  searchForm.filters = []
  searchForm.messageConditions = []
  searchForm.rawConditions = []
  searchForm.levels = []
  searchForm.sources = []
  searchForm.hosts = []
  searchForm.services = []
  // 清除动态高级筛选值
  Object.keys(advancedFilterValues).forEach(key => {
    advancedFilterValues[key] = []
  })
  searchText.value = ''
  ElMessage.success('已清除所有筛选条件')
  handleSearch()
}

const handleInlineFilter = (field: string, value: string, type: 'include' | 'exclude') => {
  handleSidebarFilter(field, value, type)
  detailVisible.value = false
}

// Row expansion
const handleExpandChange = (row: LogEntry, expandedRows: LogEntry[]) => {
  expandedRowKeys.value = expandedRows.map(r => r.id)
}

// Reset
const handleReset = () => {
  searchText.value = ''
  isAiQuery.value = false
  searchForm.filters = []
  searchForm.messageConditions = []
  searchForm.rawConditions = []
  searchForm.levels = []
  searchForm.sources = []
  searchForm.hosts = []
  searchForm.services = []
  // 清除动态高级筛选值
  Object.keys(advancedFilterValues).forEach(key => {
    advancedFilterValues[key] = []
  })
  timeRange.value = '24h'
  customTimeRange.value = undefined
  pagination.pageNum = 1
  expandedRowKeys.value = []
  handleSearch()
}

// Export
const handleExport = async () => {
  try {
    const [startTime, endTime] = getTimeRange()
    await exportLogs({
      datasourceId: selectedDatasource.value || undefined,
      startTime,
      endTime,
      fieldFilters: buildFieldFilters(), // 使用统一函数，包含侧边栏和高级筛选
      pageNum: 1,
      pageSize: 10000
    }, 'csv')
    ElMessage.success('导出成功')
  } catch (error) {
    ElMessage.error('导出失败')
  }
}

// View handlers
const viewDetail = (row: LogEntry) => {
  currentLog.value = row
  detailVisible.value = true
}

const contextTableRef = ref<any>(null)

const padNumber = (value: number, length = 2) => String(value).padStart(length, '0')
const parseTimestampPart = (value: unknown): number | null => {
  const parsed = typeof value === 'number' ? value : Number.parseInt(String(value), 10)
  return Number.isNaN(parsed) ? null : parsed
}

const normalizeRequestTimestamp = (value: unknown): string => {
  if (Array.isArray(value)) {
    const [yearRaw, monthRaw, dayRaw, hourRaw = 0, minuteRaw = 0, secondRaw = 0, nanoRaw = 0] = value
    const year = parseTimestampPart(yearRaw)
    const month = parseTimestampPart(monthRaw)
    const day = parseTimestampPart(dayRaw)
    const hour = parseTimestampPart(hourRaw) ?? 0
    const minute = parseTimestampPart(minuteRaw) ?? 0
    const second = parseTimestampPart(secondRaw) ?? 0
    const nano = parseTimestampPart(nanoRaw) ?? 0
    const millis = Math.floor(nano / 1_000_000)

    if ([year, month, day].some((part) => part == null)) {
      return ''
    }

    const base = `${year}-${padNumber(month)}-${padNumber(day)} ${padNumber(hour)}:${padNumber(minute)}:${padNumber(second)}`
    return millis > 0 ? `${base}.${padNumber(millis, 3)}` : base
  }

  if (value instanceof Date && !Number.isNaN(value.getTime())) {
    return [
      value.getFullYear(),
      padNumber(value.getMonth() + 1),
      padNumber(value.getDate())
    ].join('-') + ` ${padNumber(value.getHours())}:${padNumber(value.getMinutes())}:${padNumber(value.getSeconds())}`
  }

  return String(value ?? '').trim()
}

const viewContext = async (row: LogEntry) => {
  contextTargetLog.value = row
  contextVisible.value = true
  contextLoading.value = true

  const normalizedTimestamp = normalizeRequestTimestamp(row.timestamp)
  if (!normalizedTimestamp) {
    ElMessage.error('无法识别该日志的时间戳，无法查询上下文')
    contextLoading.value = false
    return
  }

  try {
    const { data } = await queryLogContext({
      datasourceId: selectedDatasource.value || undefined,
      logId: row.id,
      timestamp: normalizedTimestamp,
      beforeCount: 50,
      afterCount: 50,
      fieldFilters: buildFieldFilters(), // 使用统一函数，包含侧边栏和高级筛选
      useMcp: false
    })
    // Combine before + target + after logs and sort by timestamp
    const allLogs = [
      ...(data.beforeLogs || []),
      { ...row, isTarget: true },
      ...(data.afterLogs || [])
    ]
    // Sort by timestamp ascending
    contextLogs.value = allLogs.sort((a, b) => 
      new Date(a.timestamp).getTime() - new Date(b.timestamp).getTime()
    )
    
    // Scroll to target row after render
    nextTick(() => {
      const targetIndex = contextLogs.value.findIndex((log: any) => log.isTarget)
      if (targetIndex > -1 && contextTableRef.value) {
        // 使用更长的延迟确保表格完全渲染和样式应用
        setTimeout(() => {
          if (contextTableRef.value?.$el) {
            const tableBody = contextTableRef.value.$el.querySelector('.el-table__body-wrapper')
            if (tableBody) {
              // 方法1: 尝试使用 scrollIntoView（最可靠）
              const targetRow = tableBody.querySelector('.target-log-row')
              if (targetRow) {
                targetRow.scrollIntoView({
                  behavior: 'smooth',
                  block: 'center',  // 垂直方向居中
                  inline: 'nearest'
                })
              } else {
                // 方法2: 如果 targetRow 还未找到，使用计算方式
                const rowHeight = 48
                const viewportHeight = tableBody.clientHeight
                const targetRowTop = targetIndex * rowHeight
                const scrollTop = targetRowTop - (viewportHeight / 2) + (rowHeight / 2)
                tableBody.scrollTop = scrollTop
              }
            }
          }
        }, 300) // 增加到300ms确保DOM完全渲染
      }
    })
  } catch (error: any) {
    ElMessage.error(error.message || '查询上下文失败')
    contextLogs.value = []
  } finally {
    contextLoading.value = false
  }
}

const getContextRowClass = ({ row }: { row: any }) => {
  return row.isTarget ? 'target-log-row' : ''
}

// Field chart handlers
const handleShowFieldChart = async (fieldName: string, fieldLabel: string) => {
  currentFieldChart.value = { name: fieldName, label: fieldLabel, data: [] }
  fieldChartVisible.value = true
  
  try {
    const [startTime, endTime] = getTimeRange()
    const stat = fieldStats.value.find(s => s.name === fieldName)
    const topValues = stat?.topValues?.slice(0, 5) || []
    
    // Load time series for each top value
    const seriesPromises = topValues.map(item =>
      queryFieldTimeSeries({
        field: fieldName,
        value: item.value,
        startTime,
        endTime,
        granularity: calculateGranularity(startTime, endTime)
      })
    )
    
    const results = await Promise.all(seriesPromises)
    currentFieldChart.value.data = topValues.map((item, index) => ({
      name: item.value,
      series: results[index]?.data?.series || []
    }))
    
    renderFieldChart()
  } catch (error) {
    console.error('加载字段时序数据失败:', error)
  }
}

const calculateGranularity = (startTime: string, endTime: string): string => {
  const diffMs = new Date(endTime).getTime() - new Date(startTime).getTime()
  const hours = diffMs / (1000 * 60 * 60)
  if (hours <= 1) return '1m'
  if (hours <= 6) return '5m'
  if (hours <= 24) return '30m'
  if (hours <= 48) return '1h'
  return '1d'
}

const renderFieldChart = () => {
  if (!fieldChartRef.value || currentFieldChart.value.data.length === 0) return
  
  if (!fieldChartInstance) {
    fieldChartInstance = echarts.init(fieldChartRef.value, isDark.value ? 'dark' : undefined)
  }
  
  const colors = ['#409EFF', '#67C23A', '#E6A23C', '#F56C6C', '#909399']
  const allTimePoints = new Set<string>()
  
  currentFieldChart.value.data.forEach(item => {
    item.series?.forEach((point: any) => allTimePoints.add(point.timestamp))
  })
  
  const timePoints = Array.from(allTimePoints).sort()
  
  const series = currentFieldChart.value.data.map((item, index) => {
    const dataMap = new Map(item.series?.map((p: any) => [p.timestamp, p.count]) || [])
    return {
      name: item.name,
      type: fieldChartType.value,
      smooth: fieldChartType.value === 'line',
      data: timePoints.map(t => dataMap.get(t) || 0),
      itemStyle: { color: colors[index % colors.length] }
    }
  })
  
  const option = {
    backgroundColor: 'transparent',
    tooltip: {
      trigger: 'axis',
      backgroundColor: isDark.value ? 'rgba(30,30,30,0.9)' : '#fff',
      borderColor: isDark.value ? '#4C4D4F' : '#e4e7ed',
      textStyle: {
        color: isDark.value ? '#fff' : '#606266'
      },
      formatter: (params: any) => {
        let html = `<div style="font-weight:600;margin-bottom:8px;">${params[0]?.axisValue}</div>`
        params.forEach((p: any) => {
          html += `<div style="display:flex;align-items:center;gap:8px;">
            <span style="width:10px;height:10px;border-radius:50%;background:${p.color};"></span>
            <span>${p.seriesName}: <strong>${p.value}</strong></span>
          </div>`
        })
        return html
      }
    },
    legend: { data: series.map(s => s.name), bottom: 0 },
    grid: { left: '3%', right: '4%', bottom: '15%', containLabel: true },
    xAxis: {
      type: 'category',
      data: timePoints.map(t => new Date(t).toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' })),
      axisLabel: { rotate: 30 }
    },
    yAxis: { type: 'value', name: '数量' },
    series
  }
  
  fieldChartInstance.setOption(option, true)
}

const handlePinFieldChart = (fieldName: string, fieldLabel: string) => {
  handleShowFieldChart(fieldName, fieldLabel).then(() => {
    pinCurrentChart()
    fieldChartVisible.value = false
  })
}

const pinCurrentChart = async () => {
  if (currentFieldChart.value.data.length === 0) {
    ElMessage.warning('暂无数据可固定')
    return
  }
  
  const chartId = `${currentFieldChart.value.name}_${Date.now()}`
  pinnedCharts.value.push({
    id: chartId,
    name: currentFieldChart.value.name,
    label: currentFieldChart.value.label,
    type: fieldChartType.value,
    data: [...currentFieldChart.value.data]
  })
  
  // 持久化到后端
  await savePinnedChartsToBackend()
  
  viewMode.value = 'stats'
  ElMessage.success('已固定到统计视图')
  fieldChartVisible.value = false
}

const removePinnedChart = async (chartId: string) => {
  const index = pinnedCharts.value.findIndex(c => c.id === chartId)
  if (index > -1) {
    pinnedCharts.value.splice(index, 1)
    const instance = pinnedChartRefs.get(chartId)
    if (instance) {
      instance.dispose()
      pinnedChartRefs.delete(chartId)
    }
    
    // 持久化到后端
    await savePinnedChartsToBackend()
  }
}

// 保存固定图表配置到后端
const savePinnedChartsToBackend = async () => {
  try {
    const configs: PinnedChartConfig[] = pinnedCharts.value.map(chart => ({
      id: chart.id,
      name: chart.name,
      label: chart.label,
      type: chart.type
    }))
    await savePinnedChartsConfig('admin', configs)
  } catch (error) {
    console.error('保存固定图表配置失败:', error)
  }
}

// 从后端加载固定图表配置
const loadPinnedChartsFromBackend = async () => {
  try {
    const config = await getPinnedChartsConfig('admin')
    if (config.pinnedCharts && config.pinnedCharts.length > 0) {
      // 加载配置后，需要重新获取图表数据
      for (const chartConfig of config.pinnedCharts) {
        await loadPinnedChartData(chartConfig)
      }
    }
  } catch (error) {
    console.error('加载固定图表配置失败:', error)
  }
}

// 加载单个固定图表的数据
const loadPinnedChartData = async (chartConfig: PinnedChartConfig) => {
  try {
    const [startTime, endTime] = getTimeRange()
    const stat = fieldStats.value.find(s => s.name === chartConfig.name)
    const topValues = stat?.topValues?.slice(0, 5) || []
    
    if (topValues.length === 0) {
      // 如果没有统计数据，先等待加载
      return
    }
    
    const seriesPromises = topValues.map(item =>
      queryFieldTimeSeries({
        field: chartConfig.name,
        value: item.value,
        startTime,
        endTime,
        granularity: calculateGranularity(startTime, endTime)
      })
    )
    
    const results = await Promise.all(seriesPromises)
    const chartData = topValues.map((item, index) => ({
      name: item.value,
      series: results[index]?.data?.series || []
    }))
    
    pinnedCharts.value.push({
      id: chartConfig.id,
      name: chartConfig.name,
      label: chartConfig.label,
      type: chartConfig.type,
      data: chartData
    })
  } catch (error) {
    console.error(`加载图表 ${chartConfig.name} 数据失败:`, error)
  }
}

const setPinnedChartRef = (chartId: string, el: any) => {
  if (!el) return
  
  setTimeout(() => {
    const chart = pinnedCharts.value.find(c => c.id === chartId)
    if (!chart || pinnedChartRefs.has(chartId)) return
    
    const instance = echarts.init(el, isDark.value ? 'dark' : undefined)
    pinnedChartRefs.set(chartId, instance)
    renderPinnedChart(chartId, chart)
  }, 100)
}

const renderPinnedChart = (chartId: string, chart: PinnedChart) => {
  const instance = pinnedChartRefs.get(chartId)
  if (!instance || chart.data.length === 0) return
  
  const colors = ['#409EFF', '#67C23A', '#E6A23C', '#F56C6C', '#909399']
  const allTimePoints = new Set<string>()
  
  chart.data.forEach(item => {
    item.series?.forEach((point: any) => allTimePoints.add(point.timestamp))
  })
  
  const timePoints = Array.from(allTimePoints).sort()
  
  const series = chart.data.map((item, index) => {
    const dataMap = new Map(item.series?.map((p: any) => [p.timestamp, p.count]) || [])
    return {
      name: item.name,
      type: chart.type,
      smooth: chart.type === 'line',
      data: timePoints.map(t => dataMap.get(t) || 0),
      itemStyle: { color: colors[index % colors.length] }
    }
  })
  
  instance.setOption({
    backgroundColor: 'transparent',
    tooltip: { 
      trigger: 'axis',
      backgroundColor: isDark.value ? 'rgba(30,30,30,0.9)' : '#fff',
      borderColor: isDark.value ? '#4C4D4F' : '#e4e7ed',
      textStyle: {
        color: isDark.value ? '#fff' : '#606266'
      }
    },
    legend: { data: series.map(s => s.name), bottom: 0 },
    grid: { left: '3%', right: '4%', bottom: '15%', containLabel: true },
    xAxis: {
      type: 'category',
      data: timePoints.map(t => new Date(t).toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' })),
      axisLabel: { rotate: 30 }
    },
    yAxis: { type: 'value', name: '数量' },
    series
  }, true)
}

// Time range change
const handleTimeRangeChange = () => {
  if (timeRange.value !== 'custom') {
    handleSearch()
  }
}

// Utility functions
const formatTimestamp = (timestamp: string): string => {
  return new Date(timestamp).toLocaleString('zh-CN')
}

const getLevelType = (level: string) => {
  const normalizedLevel = level?.toLowerCase()
  const typeMap: Record<string, any> = {
    error: '', err: '', critical: 'danger',
    warn: 'warning', warning: 'warning',
    info: '', debug: 'info'
  }
  return typeMap[normalizedLevel] || ''
}

const getLevelStyle = (level: string) => {
  const normalizedLevel = level?.toLowerCase()
  const styleMap: Record<string, any> = {
    error: { backgroundColor: '#F56C6C', color: '#FFFFFF', borderColor: '#F56C6C' },
    err: { backgroundColor: '#F56C6C', color: '#FFFFFF', borderColor: '#F56C6C' },
    warn: { backgroundColor: '#FF9500', color: '#FFFFFF', borderColor: '#FF9500' },
    warning: { backgroundColor: '#FF9500', color: '#FFFFFF', borderColor: '#FF9500' },
    info: { backgroundColor: '#409EFF', color: '#FFFFFF', borderColor: '#409EFF' },
    debug: { backgroundColor: '#909399', color: '#FFFFFF', borderColor: '#909399' }
  }
  return styleMap[normalizedLevel] || {}
}

const getStatsFieldData = (fieldName: string) => {
  const stat = fieldStats.value.find(s => s.name === fieldName)
  return stat?.topValues?.slice(0, 10) || []
}

const calculatePercent = (count: number): number => {
  if (total.value === 0) return 0
  return Math.round((count / total.value) * 100)
}

// Lifecycle
onMounted(async () => {
  // 先加载数据源列表
  await loadDatasources()
  // 加载数据源字段信息
  await loadDatasourceSchema()
  // 然后执行搜索
  await handleSearch()
  // 搜索完成后加载固定图表配置（需要先有字段统计数据）
  await loadPinnedChartsFromBackend()
})

// Watch time range
watch(timeRange, (newVal) => {
  if (newVal === 'custom' && !customTimeRange.value) {
    const now = new Date()
    const end = formatDateToLocal(now)
    const start = formatDateToLocal(new Date(now.getTime() - 24 * 60 * 60 * 1000))
    customTimeRange.value = [start, end]
  }
})

// Watch field chart type
watch(fieldChartType, () => {
  if (fieldChartVisible.value) {
    renderFieldChart()
  }
})

// Watch pinned chart type changes
watch(pinnedCharts, (newVal, oldVal) => {
  // 只在统计视图时渲染图表
  if (viewMode.value === 'stats') {
    nextTick(() => {
      pinnedCharts.value.forEach(chart => {
        renderPinnedChart(chart.id, chart)
      })
    })
  }
  // 如果图表类型变化，保存到后端
  if (oldVal && newVal.length === oldVal.length) {
    const typeChanged = newVal.some((chart, index) =>
      oldVal[index] && chart.type !== oldVal[index].type
    )
    if (typeChanged) {
      savePinnedChartsToBackend()
    }
  }
}, { deep: true })

// Watch view mode to re-render charts when switching to stats view
watch(viewMode, (newVal) => {
  if (newVal === 'stats' && pinnedCharts.value.length > 0) {
    // 延迟渲染，确保 DOM 已更新
    nextTick(() => {
      // 清理旧实例
      pinnedChartRefs.forEach(instance => instance.dispose())
      pinnedChartRefs.clear()

      // 重新初始化所有图表
      setTimeout(() => {
        pinnedCharts.value.forEach(chart => {
          const el = document.querySelector(`[data-chart-id="${chart.id}"]`)
          if (el) {
            const instance = echarts.init(el as HTMLElement, isDark.value ? 'dark' : undefined)
            pinnedChartRefs.set(chart.id, instance)
            renderPinnedChart(chart.id, chart)
          }
        })
      }, 100)
    })

  }
})

// Watch dark mode
watch(isDark, () => {
  // Dispose and re-init field chart
  if (fieldChartInstance) {
    fieldChartInstance.dispose()
    fieldChartInstance = null
    renderFieldChart()
  }
  
  // Dispose and re-init pinned charts
  if (pinnedChartRefs.size > 0) {
    pinnedChartRefs.forEach(instance => instance.dispose())
    pinnedChartRefs.clear()
    
    nextTick(() => {
      pinnedCharts.value.forEach(chart => {
        const el = document.querySelector(`[data-chart-id="${chart.id}"]`)
        if (el) {
          const instance = echarts.init(el as HTMLElement, isDark.value ? 'dark' : undefined)
          pinnedChartRefs.set(chart.id, instance)
          renderPinnedChart(chart.id, chart)
        }
      })
    })
  }
})

// Cleanup
onUnmounted(() => {
  if (fieldChartInstance) {
    fieldChartInstance.dispose()
  }
  pinnedChartRefs.forEach(instance => instance.dispose())
  pinnedChartRefs.clear()
})
</script>


<style scoped lang="scss">
@use '@/assets/styles/macos.scss' as *;

.log-search-container {
  padding: 24px;
  background: var(--macos-bg-secondary);
  min-height: 100vh;
}

// Search toolbar
.search-toolbar {
  margin-bottom: 16px;
  border-radius: var(--macos-radius-lg);
  border: 1px solid var(--macos-border);
  background: var(--macos-glass-bg);
  backdrop-filter: blur(20px);
  -webkit-backdrop-filter: blur(20px);
  box-shadow: var(--macos-shadow-sm);

  :deep(.el-card__body) {
    padding: 16px;
  }

  .datasource-row {
    display: flex;
    align-items: center;
    gap: 12px;
    margin-bottom: 4px;

    .datasource-label {
      font-size: 14px;
      font-weight: 500;
      color: var(--macos-text-secondary);
      white-space: nowrap;
    }

    .datasource-select {
      width: 280px;

      :deep(.el-select__wrapper) {
        @include macos-input;
        box-shadow: none;
      }
    }

    .datasource-tag {
      font-family: 'Monaco', 'Consolas', monospace;
      font-size: 12px;
    }

    .datasource-link {
      font-size: 13px;
      color: var(--macos-blue);
      text-decoration: none;
      
      &:hover {
        text-decoration: underline;
      }
    }
  }

  .datasource-empty {
    padding: 16px;
    text-align: center;
    color: var(--macos-text-secondary);
    
    p {
      margin: 4px 0;
    }
    
    .hint {
      font-size: 12px;
      color: var(--macos-text-tertiary);
    }
  }

  .datasource-option {
    display: flex;
    justify-content: space-between;
    align-items: center;
    width: 100%;

    .datasource-table {
      font-size: 12px;
      color: var(--macos-text-tertiary);
      font-family: 'Monaco', 'Consolas', monospace;
    }
  }

  .query-mode-row {
    display: flex;
    align-items: center;
    margin-bottom: 12px;
    padding: 8px 0;

    :deep(.el-radio-group) {
      .el-radio-button__inner {
        padding: 8px 20px;
        font-size: 14px;
      }
    }

    .el-tag {
      display: flex;
      align-items: center;
      gap: 4px;
    }
  }

  .main-search-row {
    display: flex;
    gap: 12px;
    align-items: center;
    margin-bottom: 12px;

    .main-search-input {
      flex: 1;

      :deep(.el-input__wrapper) {
        height: 48px;
        font-size: 15px;
        border-radius: 8px;
        @include macos-input;
        box-shadow: none; // Override default
      }
    }

    .ai-query-input {
      flex: 1;

      :deep(.el-textarea__inner) {
        font-size: 14px;
        border-radius: 8px;
        @include macos-input;
        box-shadow: none;
        resize: none;
        line-height: 1.6;
      }
    }

    .time-range-select {
      width: 180px;

      :deep(.el-select__wrapper) {
        height: 48px;
        @include macos-input;
        box-shadow: none;
      }
    }

    .el-button {
      height: 48px;
      padding: 0 24px;
      font-size: 14px;
      @include macos-button-primary;
    }

    // AI 模式按钮激活状态
    .ai-mode-active {
      background: linear-gradient(135deg, #FF9500 0%, #FF6B00 100%) !important;
      border-color: #FF9500 !important;
      color: white !important;
      box-shadow: 0 4px 12px rgba(255, 149, 0, 0.3) !important;

      &:hover {
        background: linear-gradient(135deg, #FFa520 0%, #FF7B10 100%) !important;
        box-shadow: 0 6px 16px rgba(255, 149, 0, 0.4) !important;
      }
    }
  }

  .custom-time-row {
    margin-bottom: 12px;
  }

  .action-buttons-row {
    display: flex;
    gap: 8px;
    
    .el-button {
      @include macos-button-secondary;
    }
  }

  .advanced-filter {
    margin-top: 16px;
    padding-top: 16px;
    border-top: 1px solid var(--macos-border);
  }
}

// Log list card
.log-list-card {
  border-radius: var(--macos-radius-lg);
  border: 1px solid var(--macos-border);
  background: var(--macos-bg-primary);
  box-shadow: var(--macos-shadow-sm);

  :deep(.el-card__header) {
    padding: 12px 16px;
    border-bottom: 1px solid var(--macos-border);
    background: var(--macos-bg-secondary);
  }

  .card-header {
    display: flex;
    justify-content: space-between;
    align-items: center;

    span {
      font-weight: 600;
      font-size: 14px;
      color: var(--macos-text-primary);
    }

    .list-controls {
      display: flex;
      gap: 12px;
      align-items: center;
    }
  }

  .action-buttons {
    display: flex;
    gap: 8px;

    .el-button {
      padding: 4px 12px;
      font-size: 12px;
    }
  }

  .log-message {
    font-family: 'JetBrains Mono', 'SF Mono', monospace;
    font-size: 12px;
    color: var(--macos-text-primary);
  }

  .pagination-wrapper {
    margin-top: 16px;
    display: flex;
    justify-content: flex-end;
  }
}

// Empty state
.empty-state {
  text-align: left;
  max-width: 400px;
  margin: 0 auto;

  h3 {
    font-size: 18px;
    font-weight: 600;
    color: var(--macos-text-primary);
    margin: 16px 0 8px;
  }

  .empty-tips {
    color: var(--macos-text-secondary);
    font-size: 14px;
  }

  .empty-suggestions {
    list-style: none;
    padding: 0;
    margin: 12px 0;

    li {
      padding: 8px 12px;
      margin: 4px 0;
      background: var(--macos-blue-light);
      border-left: 3px solid var(--macos-blue);
      border-radius: 4px;
      font-size: 13px;
      color: var(--macos-text-primary);
    }
  }
}

// Stats view
.stats-view {
  padding: 16px 0;

  .stats-empty-tips {
    text-align: center;
    color: var(--macos-text-tertiary);
    font-size: 13px;
    line-height: 2;

    p {
      margin: 4px 0;
      display: flex;
      align-items: center;
      justify-content: center;
      gap: 4px;
    }

    .el-icon {
      color: var(--macos-blue);
    }
  }
}

// Raw log view
.raw-log-view {
  .raw-log-container {
    background: var(--macos-bg-primary);
  }

  .raw-log-line {
    margin: 0;
    padding: 4px 12px;
    font-size: 13px;
    font-family: 'JetBrains Mono', 'SF Mono', monospace;
    color: var(--macos-text-primary);
    white-space: pre-wrap;
    word-wrap: break-word;
    border-bottom: 1px solid var(--macos-border);

    &:hover {
      background: var(--macos-bg-secondary);
    }

    &:last-child {
      border-bottom: none;
    }
  }
}

// Compact table
.compact-table {
  :deep(th.el-table__cell),
  :deep(td.el-table__cell) {
    padding: 6px 0;
  }
}

// Drawer content
.log-drawer-content {
  padding: 0;
}

// Table styles
:deep(.el-table) {
  font-size: 13px;
  background: transparent;

  th.el-table__cell {
    background: var(--macos-bg-secondary);
    font-weight: 600;
    font-size: 12px;
    color: var(--macos-text-secondary);
    border-bottom: 1px solid var(--macos-border);
  }
  
  td.el-table__cell {
     border-bottom: 1px solid var(--macos-border);
  }

  .el-table__row:hover > td {
    background: var(--macos-blue-light) !important;
  }

  // Target log row highlight
  .target-log-row {
    background-color: #fff7e6 !important;

    td {
      background-color: #fff7e6 !important;
      border-left: 3px solid #fa8c16;
    }
  }
}

// Context dialog
.context-dialog {
  .target-log-alert {
    margin-bottom: 16px;
  }
}

// Field chart dialog
.field-chart-controls {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
}

.field-chart-container {
  width: 100%;
  height: 400px;
}

// Pinned charts
.pinned-charts-row {
  margin-bottom: 20px;
}

.pinned-chart-card {
  border-radius: var(--macos-radius-md);
  border: 1px solid var(--macos-border);
  box-shadow: var(--macos-shadow-sm);
  
  :deep(.el-card__header) {
    padding: 12px 16px;
    background: var(--macos-bg-secondary);
    border-bottom: 1px solid var(--macos-border);
  }
}

.pinned-chart-header {
  display: flex;
  justify-content: space-between;
  align-items: center;

  span {
    font-weight: 600;
    font-size: 14px;
    color: var(--macos-text-primary);
  }

  .chart-actions {
    display: flex;
    gap: 12px;
    align-items: center;
  }
}

.pinned-chart-container {
  width: 100%;
  height: 300px;
}
</style>
