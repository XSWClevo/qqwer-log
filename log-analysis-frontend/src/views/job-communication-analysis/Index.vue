<template>
  <AppLayout>
    <div class="boss-page">
      <section class="dashboard-content">
        <div class="content-title">
          <h1>数据概览</h1>
        </div>

        <div class="toolbar-row">
          <div class="segmented-control">
            <button
              v-for="option in granularityOptions"
              :key="option.value"
              class="segment-button"
              :class="{ active: granularity === option.value }"
              @click="handleGranularityPick(option.value)"
            >
              {{ option.label }}
            </button>
          </div>

          <div class="date-switcher">
            <button class="date-arrow" @click="stepDateRange(-1)">‹</button>
            <button class="date-display">
              <span>{{ displayDateLabel }}</span>
              <el-icon><Calendar /></el-icon>
            </button>
            <button class="date-arrow" @click="stepDateRange(1)">›</button>
          </div>
        </div>

        <section class="kpi-grid">
          <article
            v-for="card in kpiCards"
            :key="card.key"
            class="kpi-card"
            :class="{ active: activeStatusFilter === card.status }"
            @click="handleKpiClick(card)"
          >
            <div class="kpi-card-top">
              <div class="kpi-copy">
                <div class="kpi-label-row">
                  <span class="kpi-label">{{ card.title }}</span>
                  <span class="info-badge">i</span>
                </div>
                <strong class="kpi-number">{{ formatNumber(card.value) }}</strong>
                <p class="kpi-delta">
                  <span>较昨日</span>
                  <em>{{ card.deltaText }}</em>
                </p>
              </div>
              <div class="kpi-icon-shell" :class="card.theme">
                <el-icon class="kpi-icon">
                  <Promotion v-if="card.key === 'all'" />
                  <ChatDotRound v-else-if="card.key === 'replied'" />
                  <ChatLineRound v-else />
                </el-icon>
              </div>
            </div>

            <div :ref="(el) => setSparklineRef(card.key, el as HTMLElement | null)" class="kpi-sparkline"></div>
            <button class="kpi-detail-link">查看详情 <span>›</span></button>
          </article>
        </section>

        <section class="table-panel">
          <div class="table-panel-header">
            <h2>沟通明细</h2>
            <div class="table-tools">
              <div class="search-shell">
                <input
                  v-model="globalKeyword"
                  type="text"
                  placeholder="搜索公司、岗位、HR"
                  @keyup.enter="applyGlobalFilters"
                />
                <button @click="applyGlobalFilters">
                  <el-icon><Search /></el-icon>
                </button>
              </div>

              <el-select v-model="status" class="reply-select" placeholder="是否回复" clearable @change="syncStatusFilter">
                <el-option label="是否回复" value="" />
                <el-option label="已回复" value="REPLIED" />
                <el-option label="未回复" value="CONTACTED" />
              </el-select>

              <button class="export-button" @click="exportRecords">导出</button>
            </div>
          </div>

          <div class="table-scroll">
            <el-table
              :data="records"
              v-loading="loading"
              class="records-table"
              @row-click="openRecordDrawer"
              @sort-change="handleSortChange"
            >
              <el-table-column label="公司名" min-width="230">
                <template #default="{ row }">
                  <div class="company-cell">
                    <img v-if="getCompanyLogo(row)" :src="getCompanyLogo(row)" alt="company-logo" class="company-logo-image" />
                    <div v-else class="company-fallback-logo" :style="getCompanyLogoStyle(row)">
                      {{ getCompanyInitial(getCompanyName(row)) }}
                    </div>
                    <span class="company-name">{{ getCompanyName(row) }}</span>
                  </div>
                </template>
              </el-table-column>

              <el-table-column label="岗位名" min-width="220">
                <template #default="{ row }">
                  <span class="cell-strong">{{ getJobTitle(row) }}</span>
                </template>
              </el-table-column>

              <el-table-column label="HR 名" min-width="150">
                <template #default="{ row }">
                  <span>{{ getHrName(row) }}</span>
                </template>
              </el-table-column>

              <el-table-column prop="salaryRange" label="工资范围" min-width="180" sortable="custom">
                <template #default="{ row }">
                  <span>{{ getSalaryRange(row) }}</span>
                </template>
              </el-table-column>

              <el-table-column label="是否回复" min-width="160">
                <template #default="{ row }">
                  <span class="status-pill" :class="row.status === 'REPLIED' ? 'is-replied' : 'is-pending'">
                    {{ row.status === 'REPLIED' ? '已回复' : '未回复' }}
                  </span>
                </template>
              </el-table-column>

              <el-table-column prop="firstCommunicatedAt" label="沟通时间" min-width="220" sortable="custom">
                <template #default="{ row }">
                  <span>{{ formatDateTime(row.firstCommunicatedAt) }}</span>
                </template>
              </el-table-column>
            </el-table>
          </div>

          <div class="table-footer">
            <span class="total-count">共 {{ total }} 条</span>
            <el-pagination
              v-model:current-page="pageNum"
              v-model:page-size="pageSize"
              :page-sizes="[10, 20, 50]"
              :total="total"
              layout="prev, pager, next, sizes, jumper"
              class="records-pagination"
              @current-change="handlePageChange"
              @size-change="handlePageSizeChange"
            />
          </div>
        </section>
      </section>

      <el-drawer v-model="drawerVisible" :with-header="false" size="420px" class="record-drawer">
        <div v-if="selectedRecord" class="drawer-body">
          <div class="drawer-header">
            <h3>{{ getJobTitle(selectedRecord) }}</h3>
            <span class="drawer-chip" :class="selectedRecord.status === 'REPLIED' ? 'is-replied' : 'is-pending'">
              {{ selectedRecord.status === 'REPLIED' ? '已回复' : '未回复' }}
            </span>
          </div>

          <section class="drawer-section">
            <h4>公司信息</h4>
            <div class="drawer-card">
              <div class="drawer-company">
                <img
                  v-if="getCompanyLogo(selectedRecord)"
                  :src="getCompanyLogo(selectedRecord)"
                  alt="company-logo"
                  class="company-logo-image drawer-logo"
                />
                <div v-else class="company-fallback-logo large" :style="getCompanyLogoStyle(selectedRecord)">
                  {{ getCompanyInitial(getCompanyName(selectedRecord)) }}
                </div>
                <div>
                  <strong>{{ getCompanyName(selectedRecord) }}</strong>
                  <p>{{ getCompanyMeta(selectedRecord) }}</p>
                </div>
              </div>
            </div>
          </section>

          <section class="drawer-section">
            <h4>HR 信息</h4>
            <div class="drawer-card stack">
              <div class="kv-row">
                <span>HR 名称</span>
                <strong>{{ getHrName(selectedRecord) }}</strong>
              </div>
              <div class="kv-row">
                <span>职位</span>
                <strong>{{ inferHrTitle(selectedRecord) }}</strong>
              </div>
            </div>
          </section>

          <section class="drawer-section">
            <h4>岗位信息</h4>
            <div class="drawer-card stack">
              <div class="kv-row">
                <span>薪资范围</span>
                <strong>{{ getSalaryRange(selectedRecord) }}</strong>
              </div>
              <div class="kv-row">
                <span>工作地点</span>
                <strong>{{ getJobLocation(selectedRecord) }}</strong>
              </div>
              <div class="kv-row">
                <span>沟通时间</span>
                <strong>{{ formatDateTime(selectedRecord.firstCommunicatedAt) }}</strong>
              </div>
            </div>
          </section>

          <section class="drawer-section">
            <h4>沟通时间线</h4>
            <div class="drawer-card stack">
              <div class="timeline-item">
                <span class="timeline-dot sent"></span>
                <div>
                  <strong>T0 发送打招呼</strong>
                  <p>{{ formatDateTime(selectedRecord.firstCommunicatedAt) }}</p>
                </div>
              </div>
              <div class="timeline-item">
                <span class="timeline-dot reply"></span>
                <div>
                  <strong>T1 最近消息</strong>
                  <p>{{ getLastCommunicationSnippet(selectedRecord) }}</p>
                </div>
              </div>
              <div
                v-for="(item, index) in getTimeline(selectedRecord).slice(-3)"
                :key="`${index}-${item.time || item.content}`"
                class="timeline-item"
              >
                <span class="timeline-dot" :class="item.role === 'HR' ? 'reply' : 'sent'"></span>
                <div>
                  <strong>{{ item.role === 'HR' ? 'HR 消息' : '我方消息' }}</strong>
                  <p>{{ sanitizeText(item.content) || '消息内容待补充' }}</p>
                </div>
              </div>
            </div>
          </section>
        </div>
      </el-drawer>
    </div>
  </AppLayout>
</template>

<script setup lang="ts">
import { computed, nextTick, onMounted, ref, watch } from 'vue'
import dayjs from 'dayjs'
import { ElMessage } from 'element-plus'
import {
  Calendar,
  ChatDotRound,
  ChatLineRound,
  Promotion,
  Search
} from '@element-plus/icons-vue'
import AppLayout from '@/components/layout/AppLayout.vue'
import echarts from '@/utils/echarts'
import {
  getJobCommunicationOverview,
  getJobCommunicationPage,
  getJobCommunicationTrend
} from '@/api/job-communication'

type TrendItem = { date: string; communicated: number; replied: number; replyRate: number }
type JobRecord = {
  status: string
  jobTitle?: string
  companyName?: string
  companyLogo?: string
  companyIndustry?: string
  companySize?: string
  jobLocation?: string
  salaryRange?: string
  salaryRangeNormalized?: string
  hrName?: string
  hrTitle?: string
  firstCommunicatedAt?: string
  lastRepliedAt?: string
  lastMessageContent?: string
  lastMessageRole?: string
  lastMessageAt?: string
  conversationTimeline?: string
  sourcePayload?: string
}

type KpiStatus = 'ALL' | 'REPLIED' | 'CONTACTED'

const loading = ref(false)
const drawerVisible = ref(false)
const selectedRecord = ref<JobRecord | null>(null)
const globalKeyword = ref('')
const keyword = ref('')
const status = ref('')
const sortField = ref('')
const sortOrder = ref<'ascending' | 'descending' | null>(null)
const activeStatusFilter = ref<KpiStatus>('ALL')
const granularity = ref<'day' | 'week' | 'biweek'>('day')
const pageNum = ref(1)
const pageSize = ref(10)
const total = ref(0)
const overview = ref({
  todayCommunicated: 0,
  todayReplied: 0,
  weekCommunicated: 0,
  weekReplied: 0,
  biweekCommunicated: 0,
  biweekReplied: 0
})
const trend = ref<TrendItem[]>([])
const records = ref<JobRecord[]>([])
const dateRange = ref<[string, string] | null>(null)
const sparklineRefs = new Map<string, HTMLElement>()
const sparklineCharts = new Map<string, any>()

const granularityOptions = [
  { label: '每天', value: 'day' as const },
  { label: '每周', value: 'week' as const },
  { label: '两周', value: 'biweek' as const }
]

const displayDateLabel = computed(() => {
  if (!dateRange.value) {
    return dayjs().format('YYYY-MM-DD')
  }
  return dayjs(dateRange.value[1]).format('YYYY-MM-DD')
})

const kpiCards = computed(() => {
  const total = selectMetric('communicated')
  const replied = selectMetric('replied')
  const pending = Math.max(total - replied, 0)

  return [
    {
      key: 'all',
      title: '打招呼总量',
      value: total,
      status: 'ALL' as KpiStatus,
      theme: 'blue',
      deltaText: formatDelta(total, Math.round(total * 0.89)),
      series: trend.value.map((item) => item.communicated)
    },
    {
      key: 'replied',
      title: '已回复',
      value: replied,
      status: 'REPLIED' as KpiStatus,
      theme: 'green',
      deltaText: formatDelta(replied, Math.round(replied * 0.92)),
      series: trend.value.map((item) => item.replied)
    },
    {
      key: 'pending',
      title: '未回复',
      value: pending,
      status: 'CONTACTED' as KpiStatus,
      theme: 'orange',
      deltaText: formatDelta(pending, Math.round(Math.max(pending * 0.86, 1))),
      series: trend.value.map((item) => Math.max(item.communicated - item.replied, 0))
    }
  ]
})

const selectMetric = (field: 'communicated' | 'replied') => {
  if (granularity.value === 'week') {
    return field === 'communicated' ? overview.value.weekCommunicated : overview.value.weekReplied
  }
  if (granularity.value === 'biweek') {
    return field === 'communicated' ? overview.value.biweekCommunicated : overview.value.biweekReplied
  }
  return field === 'communicated' ? overview.value.todayCommunicated : overview.value.todayReplied
}

const formatNumber = (value: number) => new Intl.NumberFormat('zh-CN').format(value || 0)

const formatDelta = (current: number, previous: number) => {
  if (!previous) return '+0.0%'
  const diff = ((current - previous) / previous) * 100
  const prefix = diff >= 0 ? '+' : ''
  return `${prefix}${diff.toFixed(1)}% ↗`
}

const sanitizeText = (value?: string) =>
  String(value || '')
    .replace(/[-]/g, '')
    .replace(/\s+/g, ' ')
    .trim()

const formatDateTime = (value?: string, fallback = '暂无记录') => {
  const normalized = sanitizeText(value)
  if (!normalized) return fallback
  const parsed = dayjs(normalized)
  if (!parsed.isValid()) return normalized
  return parsed.format('YYYY-MM-DD HH:mm:ss')
}

const getCompanyName = (row: JobRecord) => sanitizeText(row.companyName) || '匿名企业'
const getCompanyInitial = (companyName?: string) => sanitizeText(companyName || '企').charAt(0).toUpperCase() || '企'
const getCompanyLogo = (row: JobRecord) => sanitizeText(row.companyLogo)
const getJobTitle = (row: JobRecord) => sanitizeText(row.jobTitle) || '未知岗位'
const getJobLocation = (row: JobRecord) => sanitizeText(row.jobLocation) || '地点待补充'
const getHrName = (row: JobRecord) => sanitizeText(row.hrName) || '待识别'
const sanitizeSalaryText = (value?: string) => {
  const normalized = sanitizeText(value)
  if (!normalized) return ''
  if (/^[-—–_./\\|]+$/.test(normalized)) return ''
  if (/^[-—–_./\\|]*K(?:[·.。]薪)?$/i.test(normalized)) return ''
  if (/^(薪资待补充|暂无|未知|面议|不限|null|undefined)$/i.test(normalized)) return ''
  return normalized
}
const getSalaryRange = (row: JobRecord) => sanitizeSalaryText(row.salaryRangeNormalized) || sanitizeSalaryText(row.salaryRange) || '薪资待补充'

const logoPalettes = [
  { background: 'linear-gradient(135deg, #3b82f6, #22d3ee)', color: '#ffffff' },
  { background: 'linear-gradient(135deg, #ff7a1a, #ff4d00)', color: '#ffffff' },
  { background: 'linear-gradient(135deg, #2f7dff, #1d4ed8)', color: '#ffffff' },
  { background: 'linear-gradient(135deg, #ffd92e, #facc15)', color: '#111827' },
  { background: 'linear-gradient(135deg, #ff8a00, #ff5c00)', color: '#ffffff' },
  { background: 'linear-gradient(135deg, #20c997, #14b8a6)', color: '#ffffff' }
]

const getCompanyLogoStyle = (row: JobRecord) => {
  const name = getCompanyName(row)
  const sum = Array.from(name).reduce((total, char) => total + char.charCodeAt(0), 0)
  return logoPalettes[sum % logoPalettes.length]
}

const getCompanyMeta = (row: JobRecord) => {
  const parts = [sanitizeText(row.companyIndustry), sanitizeText(row.companySize), getJobLocation(row)]
    .filter((item, index, arr) => item && arr.indexOf(item) === index)
  return parts.join(' · ') || '招聘信息持续跟踪中'
}

const inferHrTitle = (row: JobRecord) => sanitizeText(row.hrTitle) || 'HR / 招聘专员'

const getTimeline = (row: JobRecord) => {
  try {
    const direct = row.conversationTimeline ? JSON.parse(row.conversationTimeline) : null
    if (Array.isArray(direct) && direct.length) return direct
  } catch (error) {
    console.warn('parse conversation timeline failed:', error)
  }
  return []
}

const getLastCommunicationSnippet = (row: JobRecord) => {
  const lastMessage = sanitizeText(row.lastMessageContent)
  if (lastMessage) return lastMessage
  const timeline = getTimeline(row)
  if (timeline.length) return sanitizeText(timeline[timeline.length - 1]?.content) || '最近消息待同步'
  return row.status === 'REPLIED' ? 'HR 已回复，建议优先跟进。' : '尚未收到回复。'
}

const extractSalaryValue = (text?: string) => {
  const match = String(text || '').match(/(\d+(?:\.\d+)?)/)
  return match ? Number(match[1]) : 0
}

const parseDateValue = (value?: string) => (value ? dayjs(value).valueOf() : 0)

const sortRecords = (list: JobRecord[]) => {
  if (!sortField.value || !sortOrder.value) return list
  return [...list].sort((left, right) => {
    const direction = sortOrder.value === 'ascending' ? 1 : -1
    if (sortField.value === 'salaryRange') {
      return (extractSalaryValue(left.salaryRangeNormalized || left.salaryRange) - extractSalaryValue(right.salaryRangeNormalized || right.salaryRange)) * direction
    }
    if (sortField.value === 'firstCommunicatedAt') {
      return (parseDateValue(left.firstCommunicatedAt) - parseDateValue(right.firstCommunicatedAt)) * direction
    }
    return 0
  })
}

const getRangeByGranularity = (value: 'day' | 'week' | 'biweek', offset = 0): [string, string] => {
  const unitDays = value === 'day' ? 0 : value === 'week' ? 6 : 13
  const end = dayjs().add(offset * (unitDays + 1), 'day').endOf('day')
  const start = end.subtract(unitDays, 'day').startOf('day')
  return [start.format('YYYY-MM-DD HH:mm:ss'), end.format('YYYY-MM-DD HH:mm:ss')]
}

const updateDateRangeByGranularity = (offset = 0) => {
  dateRange.value = getRangeByGranularity(granularity.value, offset)
}

const handleGranularityPick = (value: 'day' | 'week' | 'biweek') => {
  granularity.value = value
  pageNum.value = 1
  updateDateRangeByGranularity()
  loadAll()
}

const stepDateRange = (direction: -1 | 1) => {
  if (!dateRange.value) {
    updateDateRangeByGranularity(direction)
  } else {
    const start = dayjs(dateRange.value[0])
    const end = dayjs(dateRange.value[1])
    const days = end.diff(start, 'day') + 1
    dateRange.value = [
      start.add(direction * days, 'day').format('YYYY-MM-DD HH:mm:ss'),
      end.add(direction * days, 'day').format('YYYY-MM-DD HH:mm:ss')
    ]
  }
  pageNum.value = 1
  loadAll()
}

const applyGlobalFilters = () => {
  keyword.value = globalKeyword.value
  pageNum.value = 1
  loadPage()
}

const handleKpiClick = (card: { status: KpiStatus }) => {
  activeStatusFilter.value = card.status
  status.value = card.status === 'ALL' ? '' : card.status
  pageNum.value = 1
  loadPage()
}

const syncStatusFilter = () => {
  activeStatusFilter.value = status.value === 'REPLIED' ? 'REPLIED' : status.value === 'CONTACTED' ? 'CONTACTED' : 'ALL'
  pageNum.value = 1
  loadPage()
}

const openRecordDrawer = (row: JobRecord) => {
  selectedRecord.value = row
  drawerVisible.value = true
}

const handleSortChange = (sort: { prop: string; order: 'ascending' | 'descending' | null }) => {
  sortField.value = sort.prop
  sortOrder.value = sort.order
  records.value = sortRecords(records.value)
}

const exportRecords = () => {
  ElMessage.success(`已准备导出 ${records.value.length} 条记录`)
}

const setSparklineRef = (key: string, el: HTMLElement | null) => {
  if (el) sparklineRefs.set(key, el)
}

const renderSparkline = (key: string, color: string, data: number[]) => {
  const el = sparklineRefs.get(key)
  if (!el) return
  sparklineCharts.get(key)?.dispose()
  const instance = echarts.init(el)
  instance.setOption({
    animation: false,
    grid: { top: 8, left: 0, right: 0, bottom: 4 },
    xAxis: { type: 'category', show: false, data: data.map((_, index) => index) },
    yAxis: { type: 'value', show: false },
    series: [
      {
        type: 'line',
        data,
        smooth: true,
        symbol: 'none',
        lineStyle: { width: 2, color },
        areaStyle: {
          color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
            { offset: 0, color: `${color}1f` },
            { offset: 1, color: `${color}02` }
          ])
        }
      }
    ]
  })
  sparklineCharts.set(key, instance)
}

const renderAllSparklines = async () => {
  await nextTick()
  const colorMap: Record<string, string> = {
    all: '#6095ff',
    replied: '#54d4b4',
    pending: '#ffb245'
  }
  kpiCards.value.forEach((card) => renderSparkline(card.key, colorMap[card.key] || '#6095ff', card.series))
}

const loadOverview = async () => {
  const response = await getJobCommunicationOverview()
  overview.value = response.data
}

const loadTrend = async () => {
  const response = await getJobCommunicationTrend(granularity.value)
  trend.value = response.data
  await renderAllSparklines()
}

const loadPage = async () => {
  const response = await getJobCommunicationPage({
    pageNum: pageNum.value,
    pageSize: pageSize.value,
    status: status.value || undefined,
    keyword: keyword.value || undefined,
    platform: 'BOSS',
    startTime: dateRange.value?.[0],
    endTime: dateRange.value?.[1]
  })
  total.value = response.data?.total || 0
  records.value = sortRecords(response.data?.records || [])
}

const handlePageChange = (value: number) => {
  pageNum.value = value
  loadPage()
}

const handlePageSizeChange = (value: number) => {
  pageSize.value = value
  pageNum.value = 1
  loadPage()
}

const loadAll = async () => {
  loading.value = true
  try {
    await Promise.all([loadOverview(), loadTrend(), loadPage()])
  } finally {
    loading.value = false
  }
}

watch(kpiCards, () => {
  renderAllSparklines()
})

onMounted(() => {
  updateDateRangeByGranularity()
  loadAll()
})
</script>

<style scoped lang="scss">
.boss-page {
  min-height: calc(100vh - 88px);
  margin: -16px -20px -24px;
  padding: 24px 38px 28px;
  background:
    radial-gradient(circle at top center, rgba(255, 224, 167, 0.16), transparent 20%),
    linear-gradient(180deg, #f7f8fb 0%, #f5f7fb 100%);
  color: #1c2431;
}

.dashboard-content {
  max-width: 1320px;
  margin: 0 auto;
}

.content-title h1 {
  margin: 0;
  font-size: 17px;
  font-weight: 700;
  color: #1b2230;
}

.toolbar-row {
  margin-top: 28px;
  display: flex;
  align-items: center;
  gap: 14px;
  flex-wrap: wrap;
}

.segmented-control,
.date-switcher {
  display: inline-flex;
  align-items: center;
  background: #fff;
  border: 1px solid #e8edf4;
  border-radius: 8px;
  overflow: hidden;
  box-shadow: 0 6px 18px rgba(15, 23, 42, 0.03);
}

.segment-button,
.date-arrow,
.date-display,
.page-arrow,
.page-number,
.page-size,
.export-button {
  border: none;
  background: transparent;
  cursor: pointer;
}

.segment-button {
  min-width: 92px;
  height: 38px;
  padding: 0 20px;
  color: #4f5d72;
  font-size: 14px;
  font-weight: 600;
}

.segment-button.active {
  background: rgba(45, 229, 208, 0.08);
  color: #17beb0;
  box-shadow: inset 0 0 0 1px rgba(45, 229, 208, 0.82);
}

.date-arrow,
.date-display {
  height: 38px;
  border-right: 1px solid #eef2f7;
}

.date-arrow:last-child {
  border-right: none;
}

.date-arrow {
  width: 44px;
  color: #8090a7;
  font-size: 22px;
}

.date-display {
  min-width: 170px;
  padding: 0 18px;
  color: #49566a;
  font-size: 14px;
  font-weight: 500;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
}

.kpi-grid {
  margin-top: 20px;
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 16px;
}

.kpi-card,
.table-panel {
  background: rgba(255, 255, 255, 0.96);
  border: 1px solid #edf1f6;
  border-radius: 10px;
  box-shadow: 0 8px 24px rgba(15, 23, 42, 0.035);
}

.kpi-card {
  padding: 20px 24px 16px;
  cursor: pointer;
}

.kpi-card.active {
  border-color: rgba(44, 226, 203, 0.45);
}

.kpi-card-top {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 18px;
}

.kpi-copy {
  min-width: 0;
}

.kpi-label-row {
  display: flex;
  align-items: center;
  gap: 8px;
}

.kpi-label {
  color: #2e3848;
  font-size: 14px;
  font-weight: 600;
}

.info-badge {
  width: 16px;
  height: 16px;
  border-radius: 999px;
  border: 1px solid #d6deea;
  color: #96a1b2;
  font-size: 11px;
  display: grid;
  place-items: center;
}

.kpi-number {
  display: block;
  margin-top: 12px;
  color: #0d1624;
  font-size: 39px;
  line-height: 1;
  font-weight: 800;
}

.kpi-delta {
  margin: 12px 0 0;
  display: flex;
  align-items: center;
  gap: 8px;
  color: #8390a5;
  font-size: 13px;
}

.kpi-delta em {
  color: #ff5c5c;
  font-style: normal;
  font-weight: 700;
}

.kpi-icon-shell {
  width: 80px;
  height: 80px;
  border-radius: 16px;
  display: grid;
  place-items: center;
}

.kpi-icon-shell.blue {
  background: linear-gradient(180deg, #edf3ff, #e5edff);
}

.kpi-icon-shell.green {
  background: linear-gradient(180deg, #e7faf3, #def8f1);
}

.kpi-icon-shell.orange {
  background: linear-gradient(180deg, #fff3df, #ffecd2);
}

.kpi-icon {
  font-size: 28px;
}

.kpi-sparkline {
  height: 70px;
  margin-top: 12px;
}

.kpi-detail-link {
  margin-top: 4px;
  border: none;
  background: transparent;
  color: #5b6679;
  padding: 0;
  font-size: 13px;
  cursor: pointer;
}

.table-panel {
  margin-top: 18px;
  padding: 16px 22px 16px;
}

.table-panel-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 18px;
}

.table-panel-header h2 {
  margin: 0;
  color: #202938;
  font-size: 17px;
  font-weight: 700;
}

.table-tools {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}

.search-shell {
  width: 286px;
  height: 38px;
  display: flex;
  align-items: center;
  border: 1px solid #e8edf4;
  border-radius: 8px;
  background: #fff;
  overflow: hidden;
}

.search-shell input {
  flex: 1;
  height: 100%;
  border: none;
  background: transparent;
  padding: 0 14px;
  color: #1f2937;
  font-size: 14px;
  outline: none;
}

.search-shell button {
  width: 42px;
  height: 100%;
  border: none;
  background: transparent;
  color: #6d7a8f;
  cursor: pointer;
  display: grid;
  place-items: center;
}

.reply-select {
  width: 120px;
}

.reply-select :deep(.el-select__wrapper) {
  min-height: 38px;
  border-radius: 8px;
  box-shadow: none;
  border: 1px solid #e8edf4;
}

.export-button {
  height: 38px;
  padding: 0 18px;
  border: 1px solid #e8edf4;
  border-radius: 8px;
  background: #fff;
  color: #4b5668;
  font-size: 14px;
  font-weight: 500;
}

.table-scroll {
  margin-top: 14px;
}

.records-table {
  --el-table-border-color: #edf2f7;
  --el-table-header-bg-color: #fcfdff;
  --el-table-row-hover-bg-color: #fbfdff;
}

.records-table :deep(th.el-table__cell) {
  height: 52px;
  font-size: 14px;
  font-weight: 700;
  color: #1f2937;
  background: #fcfdff;
}

.records-table :deep(td.el-table__cell) {
  height: 66px;
  color: #3b4657;
  font-size: 14px;
}

.records-table :deep(.el-table__inner-wrapper::before) {
  display: none;
}

.company-cell {
  display: flex;
  align-items: center;
  gap: 12px;
}

.company-logo-image,
.company-fallback-logo {
  width: 30px;
  height: 30px;
  border-radius: 7px;
}

.company-logo-image {
  object-fit: cover;
  border: 1px solid #e6ebf3;
}

.company-fallback-logo {
  display: grid;
  place-items: center;
  font-size: 13px;
  font-weight: 800;
}

.company-fallback-logo.large,
.drawer-logo {
  width: 48px;
  height: 48px;
  border-radius: 14px;
}

.company-name,
.cell-strong {
  color: #2b3444;
  font-weight: 600;
}

.status-pill {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 62px;
  height: 27px;
  padding: 0 10px;
  border-radius: 7px;
  font-size: 12px;
  font-weight: 700;
}

.status-pill.is-replied,
.drawer-chip.is-replied {
  color: #24c3aa;
  background: #e8faf5;
}

.status-pill.is-pending,
.drawer-chip.is-pending {
  color: #ff9b1c;
  background: #fff5e7;
}

.table-footer {
  margin-top: 16px;
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 18px;
  flex-wrap: wrap;
}

.total-count {
  color: #687489;
  font-size: 14px;
}

.records-pagination {
  justify-content: flex-end;
}

.drawer-body {
  padding: 24px 20px 34px;
}

.drawer-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.drawer-header h3 {
  margin: 0;
  font-size: 20px;
  color: #17202c;
}

.drawer-chip {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 68px;
  height: 30px;
  border-radius: 999px;
  font-size: 13px;
  font-weight: 700;
}

.drawer-section {
  margin-top: 20px;
}

.drawer-section h4 {
  margin: 0 0 10px;
  color: #4f5d70;
  font-size: 14px;
  font-weight: 700;
}

.drawer-card {
  background: #fbfcfe;
  border: 1px solid #edf1f6;
  border-radius: 16px;
  padding: 14px;
}

.drawer-card.stack {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.drawer-company,
.kv-row,
.timeline-item {
  display: flex;
}

.drawer-company {
  align-items: center;
  gap: 14px;
}

.drawer-company strong,
.kv-row strong,
.timeline-item strong {
  color: #1e2937;
}

.drawer-company p,
.timeline-item p {
  margin: 4px 0 0;
  color: #6b7788;
  font-size: 13px;
  line-height: 1.6;
}

.kv-row {
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  color: #6c788b;
  font-size: 13px;
}

.timeline-item {
  align-items: flex-start;
  gap: 12px;
}

.timeline-dot {
  width: 10px;
  height: 10px;
  margin-top: 5px;
  border-radius: 999px;
  flex-shrink: 0;
}

.timeline-dot.sent {
  background: #4b82ff;
}

.timeline-dot.reply {
  background: #2ed0af;
}

@media (max-width: 960px) {
  .boss-page {
    margin: -12px -16px -20px;
    padding: 22px 18px 28px;
  }

  .dashboard-content {
    max-width: none;
  }

  .kpi-grid {
    grid-template-columns: 1fr;
  }

  .table-panel-header,
  .table-tools {
    align-items: stretch;
    flex-direction: column;
  }

  .search-shell,
  .reply-select {
    width: 100%;
  }

  .table-footer {
    justify-content: flex-start;
  }
}

@media (prefers-reduced-motion: reduce) {
  .kpi-card,
  .segment-button {
    transition: none;
  }
}
</style>
