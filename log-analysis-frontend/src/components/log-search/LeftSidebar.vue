<template>
  <div class="left-sidebar-container">
    <!-- Sidebar Header -->
    <div class="sidebar-header">
      <el-icon><Filter /></el-icon>
      <span class="header-title">字段过滤</span>
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
import { Filter, Setting, Check, Rank, Close, Plus } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import draggable from 'vuedraggable'
import SelectedFiltersSection from './SelectedFiltersSection.vue'
import FieldFacetPanel from './FieldFacetPanel.vue'
import { getFieldConfig, saveFieldConfig } from '@/api/field-config'

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
}

const props = withDefaults(defineProps<Props>(), {
  fieldStats: () => [],
  activeFilters: () => [],
  totalCount: 0,
  selectedFields: () => ['timestamp', 'level', 'hostname', 'service', 'message'],
  pinnedFields: () => []
})

const emit = defineEmits<{
  filter: [field: string, value: string, type: 'include' | 'exclude']
  'remove-filter': [filterId: string]
  'clear-filters': []
  'show-chart': [fieldName: string, fieldLabel: string]
  'pin-chart': [fieldName: string, fieldLabel: string]
  'fields-change': [fields: string[]]
}>()

// Field configurations
const fieldConfigs = [
  { name: 'severity', label: '日志级别' },
  { name: 'hostname', label: '主机' },
  { name: 'appname', label: '应用名' },
  { name: 'source_type', label: '来源类型' }
]

// All available fields for table columns
const ALL_FIELDS = [
  'timestamp', 'severity', 'hostname', 'appname', 'source_type',
  'message', 'facility', 'procid', 'source_ip', 'raw'
]

const FIELD_LABELS: Record<string, string> = {
  timestamp: '时间戳',
  severity: '日志级别',
  hostname: '主机',
  appname: '应用名',
  source_type: '来源类型',
  message: '消息',
  facility: '设施',
  procid: '进程ID',
  source_ip: '来源IP',
  raw: '原始日志'
}

// Field selector state
const fieldSelectorVisible = ref(false)
const localSelectedFields = ref<string[]>([...props.selectedFields])

// Available fields (not selected)
const availableFieldsList = computed(() => {
  return ALL_FIELDS.filter(f => !localSelectedFields.value.includes(f))
})

// Get field label
const getFieldLabel = (field: string): string => {
  return FIELD_LABELS[field] || field
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
    await saveFieldConfig('admin', {
      configType: 'log_list',
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
    const config = await getFieldConfig('admin', 'log_list')
    if (config.selectedFields && config.selectedFields.length > 0) {
      localSelectedFields.value = config.selectedFields
      emit('fields-change', [...config.selectedFields])
    }
  } catch (error) {
    console.error('加载字段配置失败:', error)
  }
}

// Watch for external changes to selectedFields
watch(() => props.selectedFields, (newFields) => {
  if (JSON.stringify(newFields) !== JSON.stringify(localSelectedFields.value)) {
    localSelectedFields.value = [...newFields]
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
  loadFieldConfig()
})
</script>

<style lang="scss">
// Global styles for popover (rendered outside component)
.field-selector-popper {
  .popover-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    font-weight: 600;
    font-size: 14px;
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
  }

  .field-list {
    max-height: 200px;
    overflow-y: auto;
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
    color: #909399;
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

  .el-icon {
    font-size: 16px;
    color: #409EFF;
  }
}

.header-title {
  font-weight: 600;
  font-size: 15px;
  font-weight: 600;
  font-size: 15px;
  color: var(--macos-text-primary);
  flex: 1;
}

.field-select-btn {
  color: #ffffff;
  font-size: 12px;
  padding: 4px 8px;
}

.facet-panels-container {
  flex: 1;
  min-height: 0;
}

</style>
