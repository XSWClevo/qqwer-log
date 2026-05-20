<template>
  <div class="left-sidebar-container">
    <!-- Sidebar Header -->
    <div class="sidebar-header">
      <el-icon><Filter /></el-icon>
      <span class="header-title">字段过滤</span>

      <!-- 统计字段选择按钮 -->
      <el-popover
        placement="right-start"
        :width="320"
        trigger="click"
        v-model:visible="statsFieldSelectorVisible"
        popper-class="stats-field-selector-popper"
      >
        <template #reference>
          <el-button size="small" type="success" plain class="stats-field-select-btn">
            <el-icon><DataAnalysis /></el-icon>
            统计字段
          </el-button>
        </template>
        <div class="field-selector-popover">
          <div class="popover-header">
            <span>选择统计字段</span>
            <el-button link type="primary" size="small" @click="handleSaveStatsFields">
              <el-icon><Check /></el-icon>
              保存
            </el-button>
          </div>
          <el-divider style="margin: 8px 0" />
          <div class="field-section">
            <div class="section-label">已选统计字段 ({{ selectedStatsFields.length }})</div>
            <draggable
              v-model="selectedStatsFields"
              group="statsFields"
              item-key="name"
              class="field-list selected-list"
              :animation="200"
            >
              <template #item="{ element }">
                <div class="field-item selected">
                  <el-icon class="drag-icon"><Rank /></el-icon>
                  <span class="field-name">{{ element.label }}</span>
                  <el-icon class="remove-icon" @click="removeStatsField(element)"><Close /></el-icon>
                </div>
              </template>
            </draggable>
          </div>
          <el-divider style="margin: 8px 0" />
          <div class="field-section">
            <div class="section-label">可用统计字段 ({{ availableStatsFields.length }})</div>
            <div class="field-list available-list">
              <div
                v-for="field in availableStatsFields"
                :key="field.name"
                class="field-item available"
                @click="addStatsField(field)"
              >
                <el-icon class="add-icon"><Plus /></el-icon>
                <span class="field-name">{{ field.label }}</span>
              </div>
            </div>
          </div>
        </div>
      </el-popover>

      <!-- 表格字段选择按钮 -->
      <el-popover
        placement="right-start"
        :width="320"
        trigger="click"
        v-model:visible="fieldSelectorVisible"
        popper-class="field-selector-popper"
      >
        <template #reference>
          <el-button size="small" type="primary" plain class="field-select-btn">
            <el-icon><Setting /></el-icon>
            字段选择
          </el-button>
        </template>
        <div class="field-selector-popover">
          <div class="popover-header">
            <span>选择显示字段</span>
            <el-button link type="primary" size="small" @click="handleSaveFields" plain class="field-select-btn">
              <el-icon><Check /></el-icon>
              保存
            </el-button>
          </div>
          <el-divider style="margin: 8px 0" />
          <div class="field-section">
            <div class="section-label">已选字段 (拖拽排序)</div>
            <draggable
              v-model="localSelectedFields"
              group="fields"
              item-key="field"
              class="field-list selected-list"
              :animation="200"
            >
              <template #item="{ element }">
                <div class="field-item selected">
                  <el-icon class="drag-icon"><Rank /></el-icon>
                  <span class="field-name">{{ getFieldLabel(element) }}</span>
                  <el-icon class="remove-icon" @click="removeField(element)"><Close /></el-icon>
                </div>
              </template>
            </draggable>
          </div>
          <el-divider style="margin: 8px 0" />
          <div class="field-section">
            <div class="section-label">可用字段</div>
            <div class="field-list available-list">
              <div
                v-for="field in availableFieldsList"
                :key="field"
                class="field-item available"
                @click="addField(field)"
              >
                <el-icon class="add-icon"><Plus /></el-icon>
                <span class="field-name">{{ getFieldLabel(field) }}</span>
              </div>
            </div>
          </div>
        </div>
      </el-popover>
    </div>

    <!-- Selected Filters Section -->
    <SelectedFiltersSection
      :filters="activeFilters"
      @remove="handleRemoveFilter"
      @clear-all="handleClearAllFilters"
    />

    <!-- Field Facet Panels -->
    <el-scrollbar class="facet-panels-container">
      <FieldFacetPanel
        v-for="field in fieldConfigs"
        :key="field.name"
        :field-name="field.name"
        :field-label="field.label"
        :values="getFieldValues(field.name)"
        :total-count="totalCount"
        :collapsed="collapsedPanels[field.name]"
        :is-pinned="pinnedFields.includes(field.name)"
        @filter="handleFieldFilter"
        @toggle="(collapsed) => handlePanelToggle(field.name, collapsed)"
        @show-chart="handleShowChart"
        @pin-chart="handlePinChart"
      />
    </el-scrollbar>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, watch, onMounted } from 'vue'
import { Filter, Setting, Check, Rank, Close, Plus, DataAnalysis } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import draggable from 'vuedraggable'
import SelectedFiltersSection from './SelectedFiltersSection.vue'
import FieldFacetPanel from './FieldFacetPanel.vue'
import { getFieldConfig, saveFieldConfig } from '@/api/field-config'

interface FieldInfo {
  name: string
  type: string
  label: string
  isTimestamp?: boolean
  isStatsDimension?: boolean
  isContentField?: boolean
}

interface FieldValue {
  value: string
  count: number
}

interface FieldStats {
  name: string
  label: string
  topValues: FieldValue[]
}

interface ActiveFilter {
  id: string
  field: string
  fieldLabel: string
  value: string
  type: 'include' | 'exclude'
}

interface Props {
  fieldStats: FieldStats[]
  activeFilters: ActiveFilter[]
  totalCount: number
  selectedFields?: string[]
  pinnedFields?: string[]
  availableFields?: FieldInfo[]  // 新增：可用字段列表
  datasourceId?: string           // 新增：数据源ID
}

const props = withDefaults(defineProps<Props>(), {
  fieldStats: () => [],
  activeFilters: () => [],
  totalCount: 0,
  selectedFields: () => ['timestamp', 'severity', 'hostname', 'appname', 'message'],
  pinnedFields: () => [],
  availableFields: () => [],
  datasourceId: ''
})

const emit = defineEmits<{
  filter: [field: string, value: string, type: 'include' | 'exclude']
  'remove-filter': [filterId: string]
  'clear-filters': []
  'show-chart': [fieldName: string, fieldLabel: string]
  'pin-chart': [fieldName: string, fieldLabel: string]
  'fields-change': [fields: string[]]
  'stats-fields-change': [fields: Array<{ name: string; label: string }>]  // 新增：统计字段变化事件
}>()

// Field configurations (动态生成，基于统计维度字段)
// 改为从用户选择的统计字段生成
const fieldConfigs = computed(() => {
  return selectedStatsFields.value
})

// 获取默认字段（兼容旧逻辑）
const getDefaultFields = (): FieldInfo[] => {
  return [
    { name: 'timestamp', type: 'DateTime', label: '时间戳', isTimestamp: true },
    { name: 'severity', type: 'String', label: '日志级别', isStatsDimension: true },
    { name: 'hostname', type: 'String', label: '主机', isStatsDimension: true },
    { name: 'appname', type: 'String', label: '应用名', isStatsDimension: true },
    { name: 'source_type', type: 'String', label: '来源类型', isStatsDimension: true },
    { name: 'message', type: 'String', label: '消息', isContentField: true },
    { name: 'facility', type: 'String', label: '设施' },
    { name: 'procid', type: 'String', label: '进程ID' },
    { name: 'source_ip', type: 'String', label: '来源IP' },
    { name: 'raw', type: 'String', label: '原始日志', isContentField: true }
  ]
}

// 所有可用字段（动态或默认）
const allFieldsInfo = computed(() => {
  if (props.availableFields && props.availableFields.length > 0) {
    return props.availableFields
  }
  return getDefaultFields()
})

// All available fields for table columns
const ALL_FIELDS = computed(() => {
  return allFieldsInfo.value.map(f => f.name)
})

const FIELD_LABELS = computed(() => {
  const labels: Record<string, string> = {}
  allFieldsInfo.value.forEach(field => {
    labels[field.name] = field.label || field.name
  })
  return labels
})

// Field selector state
const fieldSelectorVisible = ref(false)
const localSelectedFields = ref<string[]>([...props.selectedFields])

// Stats field selector state
const statsFieldSelectorVisible = ref(false)
const selectedStatsFields = ref<Array<{ name: string; label: string }>>([])

// 所有可用的统计维度字段
const allStatsDimensions = computed(() => {
  return allFieldsInfo.value.filter(f => f.isStatsDimension)
})

// 可用的统计字段（排除已选择的）
const availableStatsFields = computed(() => {
  const selectedNames = selectedStatsFields.value.map(f => f.name)
  return allStatsDimensions.value
    .filter(f => !selectedNames.includes(f.name))
    .map(f => ({ name: f.name, label: f.label }))
})

// 添加统计字段
const addStatsField = (field: { name: string; label: string }) => {
  if (!selectedStatsFields.value.find(f => f.name === field.name)) {
    selectedStatsFields.value.push(field)
  }
}

// 移除统计字段
const removeStatsField = (field: { name: string; label: string }) => {
  const index = selectedStatsFields.value.findIndex(f => f.name === field.name)
  if (index > -1) {
    selectedStatsFields.value.splice(index, 1)
  }
}

// 保存统计字段配置
const handleSaveStatsFields = async () => {
  try {
    const configType = props.datasourceId ? `stats_fields_${props.datasourceId}` : 'stats_fields'
    await saveFieldConfig('admin', {
      configType,
      selectedFields: selectedStatsFields.value.map(f => f.name),
      fieldOrder: selectedStatsFields.value.map(f => f.name)
    })
    emit('stats-fields-change', [...selectedStatsFields.value])
    statsFieldSelectorVisible.value = false
    ElMessage.success('统计字段配置已保存')
  } catch (error) {
    console.error('保存统计字段配置失败:', error)
    emit('stats-fields-change', [...selectedStatsFields.value])
    statsFieldSelectorVisible.value = false
  }
}

// 加载统计字段配置
const loadStatsFieldConfig = async () => {
  try {
    const configType = props.datasourceId ? `stats_fields_${props.datasourceId}` : 'stats_fields'
    const config = await getFieldConfig('admin', configType)

    if (config.selectedFields && config.selectedFields.length > 0) {
      // 过滤掉不存在的字段，保留存在的字段
      const validFields = config.selectedFields
        .map(name => {
          const field = allStatsDimensions.value.find(f => f.name === name)
          return field ? { name: field.name, label: field.label } : null
        })
        .filter(f => f !== null) as Array<{ name: string; label: string }>

      if (validFields.length > 0) {
        selectedStatsFields.value = validFields
        emit('stats-fields-change', [...validFields])
      } else {
        // 如果没有有效字段，使用默认配置（前4个统计维度字段）
        console.log('配置中的字段在当前数据源中不存在，使用默认字段')
        setDefaultStatsFields()
      }
    } else {
      // 默认配置：前4个统计维度字段
      setDefaultStatsFields()
    }
  } catch (error) {
    console.error('加载统计字段配置失败:', error)
    // 使用默认配置
    setDefaultStatsFields()
  }
}

// 设置默认统计字段（前4个）
const setDefaultStatsFields = () => {
  if (allStatsDimensions.value.length === 0) {
    console.warn('当前数据源没有统计维度字段')
    selectedStatsFields.value = []
    emit('stats-fields-change', [])
    return
  }

  const defaultFields = allStatsDimensions.value.slice(0, 4).map(f => ({
    name: f.name,
    label: f.label
  }))
  selectedStatsFields.value = defaultFields
  emit('stats-fields-change', [...defaultFields])
  console.log('使用默认统计字段:', defaultFields)
}

// Available fields (not selected)
const availableFieldsList = computed(() => {
  return ALL_FIELDS.value.filter(f => !localSelectedFields.value.includes(f))
})

// Get field label
const getFieldLabel = (field: string): string => {
  return FIELD_LABELS.value[field] || field
}

// Add field to selected
const addField = (field: string) => {
  if (!localSelectedFields.value.includes(field)) {
    localSelectedFields.value.push(field)
  }
}

// Remove field from selected
const removeField = (field: string) => {
  const index = localSelectedFields.value.indexOf(field)
  if (index > -1) {
    localSelectedFields.value.splice(index, 1)
  }
}

// Save field configuration
const handleSaveFields = async () => {
  try {
    const configType = props.datasourceId ? `log_list_${props.datasourceId}` : 'log_list'
    await saveFieldConfig('admin', {
      configType,
      selectedFields: localSelectedFields.value,
      fieldOrder: localSelectedFields.value
    })
    emit('fields-change', [...localSelectedFields.value])
    fieldSelectorVisible.value = false
    ElMessage.success('字段配置已保存')
  } catch (error) {
    console.error('保存字段配置失败:', error)
    // Still emit the change even if save fails
    emit('fields-change', [...localSelectedFields.value])
    fieldSelectorVisible.value = false
  }
}

// Load field configuration on mount
const loadFieldConfig = async () => {
  try {
    const configType = props.datasourceId ? `log_list_${props.datasourceId}` : 'log_list'
    const config = await getFieldConfig('admin', configType)
    if (config.selectedFields && config.selectedFields.length > 0) {
      // 过滤掉不存在的字段，保留存在的字段
      const validFields = config.selectedFields.filter(field =>
        ALL_FIELDS.value.includes(field)
      )

      if (validFields.length > 0) {
        localSelectedFields.value = validFields
        emit('fields-change', [...validFields])
      } else {
        // 如果没有有效字段，使用默认配置（前5个字段）
        const defaultFields = ALL_FIELDS.value.slice(0, 5)
        localSelectedFields.value = defaultFields
        emit('fields-change', [...defaultFields])
      }
    } else {
      // 默认配置：前5个字段
      const defaultFields = ALL_FIELDS.value.slice(0, 5)
      localSelectedFields.value = defaultFields
      emit('fields-change', [...defaultFields])
    }
  } catch (error) {
    console.error('加载字段配置失败:', error)
    // 使用默认配置
    const defaultFields = ALL_FIELDS.value.slice(0, 5)
    localSelectedFields.value = defaultFields
    emit('fields-change', [...defaultFields])
  }
}

// Watch for external changes to selectedFields
watch(() => props.selectedFields, (newFields) => {
  if (JSON.stringify(newFields) !== JSON.stringify(localSelectedFields.value)) {
    localSelectedFields.value = [...newFields]
  }
}, { deep: true })

// 监听数据源切换
watch(() => props.datasourceId, async (newId, oldId) => {
  if (newId !== oldId && oldId !== undefined) {
    // 数据源切换时，等待字段加载完成后再加载配置
    // 配置加载会在 availableFields 变化时触发
    console.log('数据源切换:', oldId, '->', newId)
  }
})

// 监听可用字段变化（数据源字段加载完成）
watch(() => props.availableFields, async (newFields, oldFields) => {
  // 只在字段从无到有，或者字段内容发生变化时重新加载配置
  const fieldsChanged = JSON.stringify(newFields) !== JSON.stringify(oldFields)

  if (newFields && newFields.length > 0 && fieldsChanged) {
    console.log('数据源字段已加载，重新加载配置')
    // 字段信息加载完成，重新加载配置
    await loadFieldConfig()
    await loadStatsFieldConfig()
  }
}, { deep: true })

// Track collapsed state for each panel
const collapsedPanels = ref<Record<string, boolean>>({})

// Get field values from stats
const getFieldValues = (fieldName: string): FieldValue[] => {
  const stat = props.fieldStats.find(s => s.name === fieldName)
  return stat?.topValues || []
}

// Handle filter from field panel
const handleFieldFilter = (field: string, value: string, type: 'include' | 'exclude') => {
  emit('filter', field, value, type)
}

// Handle remove single filter
const handleRemoveFilter = (filterId: string) => {
  emit('remove-filter', filterId)
}

// Handle clear all filters
const handleClearAllFilters = () => {
  emit('clear-filters')
}

// Handle panel toggle
const handlePanelToggle = (fieldName: string, collapsed: boolean) => {
  collapsedPanels.value[fieldName] = collapsed
}

// Handle show chart
const handleShowChart = (fieldName: string, fieldLabel: string) => {
  emit('show-chart', fieldName, fieldLabel)
}

// Handle pin chart
const handlePinChart = (fieldName: string, fieldLabel: string) => {
  emit('pin-chart', fieldName, fieldLabel)
}

// Load config on mount
onMounted(() => {
  // 只加载表格字段配置
  // 统计字段配置会在 availableFields 加载完成后自动加载
  loadFieldConfig()

  // 如果已经有可用字段，则立即加载统计字段配置
  if (props.availableFields && props.availableFields.length > 0) {
    loadStatsFieldConfig()
  }
})
</script>

<style lang="scss">
// Global styles for popover (rendered outside component)
.field-selector-popper,
.stats-field-selector-popper {
  .popover-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    font-weight: 600;
    font-size: 14px;
    color: var(--macos-text-primary);
  }

  .field-section {
    margin-bottom: 8px;
  }

  .section-label {
    font-size: 12px;
    color: var(--macos-text-secondary);
    margin-bottom: 8px;
    font-weight: 500;
  }

  .field-list {
    max-height: 200px;
    overflow-y: auto;

    &::-webkit-scrollbar {
      width: 6px;
    }

    &::-webkit-scrollbar-thumb {
      background: #dcdfe6;
      border-radius: 3px;
    }

    &::-webkit-scrollbar-track {
      background: transparent;
    }
  }

  .field-item {
    display: flex;
    align-items: center;
    gap: 8px;
    padding: 8px 12px;
    margin-bottom: 4px;
    border-radius: 4px;
    cursor: pointer;
    transition: all 0.2s;
    border: 1px solid transparent;

    &.selected {
      background: var(--macos-blue-light);
      border-color: var(--macos-blue);
      cursor: move;

      &:hover {
        background: var(--macos-blue-light);
        border-color: var(--macos-blue);
      }
    }

    &.available {
      background: var(--macos-bg-secondary);

      &:hover {
        background: var(--macos-bg-tertiary);
        border-color: var(--macos-border-hover);
      }
    }
  }

  .field-name {
    flex: 1;
    font-size: 13px;
    color: var(--macos-text-primary);
  }

  .drag-icon {
    color: var(--macos-text-tertiary);
    cursor: move;
    font-size: 14px;
  }

  .add-icon {
    color: #67c23a;
    font-size: 16px;
  }

  .remove-icon {
    color: #f56c6c;
    font-size: 14px;
    opacity: 0.7;
    transition: opacity 0.2s;

    &:hover {
      opacity: 1;
    }
  }
}
</style>

<style scoped lang="scss">
.left-sidebar-container {
  display: flex;
  flex-direction: column;
  height: 100%;
  background: var(--macos-bg-secondary);
  border-right: 1px solid var(--macos-border);
}

.sidebar-header {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 16px;
  border-bottom: 1px solid var(--macos-border);
  background: var(--macos-bg-secondary);
  flex-wrap: wrap;

  .el-icon {
    font-size: 16px;
    color: var(--macos-blue);
  }
}

.header-title {
  font-weight: 600;
  font-size: 15px;
  color: var(--macos-text-primary);
  flex: 1;
  min-width: 80px;
}

.stats-field-select-btn {
  font-size: 12px;
  padding: 4px 8px;
  height: 28px;

  :deep(.el-button__text) {
    display: flex;
    align-items: center;
    gap: 4px;
  }
}

.field-select-btn {
  font-size: 12px;
  padding: 4px 8px;
  height: 28px;

  :deep(.el-button__text) {
    display: flex;
    align-items: center;
    gap: 4px;
  }
}

.facet-panels-container {
  flex: 1;
  min-height: 0;
}

</style>
