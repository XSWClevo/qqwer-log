<template>
  <div class="field-selector">
    <div class="field-selector-header">
      <span class="header-title">字段选择</span>
      <el-button link type="primary" size="small" @click="saveConfig">
        <el-icon><Check /></el-icon>
        保存
      </el-button>
    </div>

    <el-divider style="margin: 8px 0" />

    <div class="field-section">
      <div class="section-title">
        <el-icon><Select /></el-icon>
        <span>已选择字段 ({{ selectedFields.length }})</span>
        <el-button link type="danger" size="small" @click="resetFields">
          重置
        </el-button>
      </div>

      <el-scrollbar max-height="300px">
        <draggable
          v-model="selectedFields"
          group="fields"
          item-key="field"
          class="field-list"
          :animation="200"
        >
          <template #item="{ element }">
            <div class="field-item selected">
              <el-icon class="drag-icon"><Rank /></el-icon>
              <span class="field-name">{{ getFieldLabel(element) }}</span>
              <el-icon class="remove-icon" @click="removeField(element)">
                <Close />
              </el-icon>
            </div>
          </template>
        </draggable>
      </el-scrollbar>
    </div>

    <el-divider style="margin: 12px 0" />

    <div class="field-section">
      <div class="section-title">
        <el-icon><InfoFilled /></el-icon>
        <span>可用字段 ({{ availableFields.length }})</span>
      </div>

      <el-scrollbar max-height="300px">
        <div class="field-list">
          <div
            v-for="field in availableFields"
            :key="field"
            class="field-item available"
            @click="addField(field)"
          >
            <el-icon class="add-icon"><Plus /></el-icon>
            <span class="field-name">{{ getFieldLabel(field) }}</span>
          </div>
        </div>
      </el-scrollbar>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, watch, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { Check, Select, InfoFilled, Rank, Close, Plus } from '@element-plus/icons-vue'
import draggable from 'vuedraggable'
import { getFieldConfig, saveFieldConfig } from '@/api/field-config'
import request from '@/utils/request'

interface FieldInfo {
  name: string
  type: string
  label: string
  isTimestamp?: boolean
  isStatsDimension?: boolean
  isContentField?: boolean
}

const props = defineProps<{
  username?: string
  availableFields?: FieldInfo[]  // 可用字段列表（从父组件传入）
  datasourceId?: string           // 数据源ID（用于检测切换和配置存储）
}>()

const emit = defineEmits<{
  change: [fields: string[]]
}>()

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
const allFields = computed(() => {
  if (props.availableFields && props.availableFields.length > 0) {
    return props.availableFields
  }
  return getDefaultFields()
})

// 所有可用字段名列表
const ALL_FIELDS = computed(() => {
  return allFields.value.map(f => f.name)
})

// 字段标签映射（动态生成）
const FIELD_LABELS = computed(() => {
  const labels: Record<string, string> = {}
  allFields.value.forEach(field => {
    labels[field.name] = field.label || field.name
  })
  return labels
})

// 已选择的字段
const selectedFields = ref<string[]>([])

// 可用字段(排除已选择的)
const availableFields = computed(() => {
  return ALL_FIELDS.value.filter(field => !selectedFields.value.includes(field))
})

// 获取字段标签
const getFieldLabel = (field: string): string => {
  return FIELD_LABELS.value[field] || field
}

// 添加字段
const addField = (field: string) => {
  if (!selectedFields.value.includes(field)) {
    selectedFields.value.push(field)
  }
}

// 移除字段
const removeField = (field: string) => {
  const index = selectedFields.value.indexOf(field)
  if (index > -1) {
    selectedFields.value.splice(index, 1)
  }
}

// 重置字段为默认配置
const resetFields = async () => {
  try {
    const configType = props.datasourceId ? `log_list_${props.datasourceId}` : 'log_list'
    const username = props.username || 'admin'
    await request.delete(`/api/field-config/${configType}`, { params: { username } })
    // 重新加载默认配置
    await loadConfig()
    ElMessage.success('已重置为默认配置')
  } catch (error) {
    console.error('重置失败:', error)
    ElMessage.error('重置失败')
  }
}

// 保存配置
const saveConfig = async () => {
  try {
    const username = props.username || 'admin'
    const configType = props.datasourceId ? `log_list_${props.datasourceId}` : 'log_list'
    await saveFieldConfig(username, {
      configType,
      selectedFields: selectedFields.value,
      fieldOrder: selectedFields.value
    })
    ElMessage.success('配置已保存')
  } catch (error) {
    console.error('保存失败:', error)
    ElMessage.error('保存失败')
  }
}

// 加载配置
const loadConfig = async () => {
  try {
    const username = props.username || 'admin'
    const configType = props.datasourceId ? `log_list_${props.datasourceId}` : 'log_list'
    const config = await getFieldConfig(username, configType)

    if (config.selectedFields && config.selectedFields.length > 0) {
      // 过滤掉不存在的字段，保留存在的字段
      const validFields = config.selectedFields.filter(field =>
        ALL_FIELDS.value.includes(field)
      )

      if (validFields.length > 0) {
        selectedFields.value = validFields
      } else {
        // 如果没有有效字段，使用默认配置
        setDefaultFields()
      }
    } else {
      // 默认配置：前5个字段
      setDefaultFields()
    }
  } catch (error) {
    console.error('加载配置失败:', error)
    // 使用默认配置
    setDefaultFields()
  }
}

// 设置默认字段（前5个）
const setDefaultFields = () => {
  selectedFields.value = ALL_FIELDS.value.slice(0, 5)
}

// 监听字段变化,通知父组件
watch(selectedFields, (newFields) => {
  emit('change', newFields)
}, { deep: true })

// 监听数据源切换
watch(() => props.datasourceId, async (newId, oldId) => {
  if (newId !== oldId && oldId !== undefined) {
    // 数据源切换时，重新加载配置
    await loadConfig()
  }
})

// 监听可用字段变化（数据源字段加载完成）
watch(() => props.availableFields, async (newFields, oldFields) => {
  if (newFields && newFields.length > 0 && (!oldFields || oldFields.length === 0)) {
    // 字段信息首次加载完成，重新加载配置
    await loadConfig()
  }
}, { deep: true })

// 组件挂载时加载配置
onMounted(() => {
  loadConfig()
})

// 暴露方法供父组件使用
defineExpose({
  selectedFields,
  loadConfig
})
</script>

<style scoped>
.field-selector {
  height: 100%;
  display: flex;
  flex-direction: column;
}

.field-selector-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 12px 16px;
  background: var(--macos-fill-secondary);
  border-radius: 4px;

  :deep(.el-button.is-link) {
    &.el-button--primary {
      color: var(--macos-blue) !important;
      
      &:hover {
        color: var(--macos-blue-hover) !important;
      }
    }
    
    &.el-button--danger {
      color: var(--macos-danger) !important;
      
      &:hover {
        opacity: 0.8;
      }
    }
  }
}

.header-title {
  font-weight: 600;
  font-size: 14px;
  color: var(--macos-text-primary);
}

.field-section {
  flex: 1;
  min-height: 0;
}

.section-title {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 8px 16px;
  font-size: 13px;
  font-weight: 500;
  color: var(--macos-text-secondary);
  background: var(--macos-fill-tertiary);
  border-radius: 4px;
  margin-bottom: 8px;

  :deep(.el-button.is-link) {
    &.el-button--danger {
      color: var(--macos-danger) !important;
      
      &:hover {
        opacity: 0.8;
      }
    }
  }
}

.section-title span {
  flex: 1;
}

.field-list {
  padding: 0 8px;
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
}

.field-item.selected {
  background: var(--macos-blue-light);
  border-color: var(--macos-blue-light);
  cursor: move;
}

.field-item.selected:hover {
  background: var(--macos-info-bg);
  border-color: var(--macos-info-border);
}

.field-item.available {
  background: var(--macos-fill-secondary);
}

.field-item.available:hover {
  background: var(--macos-bg-tertiary);
  border-color: var(--macos-border-hover);
}

.field-name {
  flex: 1;
  font-size: 13px;
  color: #303133;
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
}

.remove-icon:hover {
  opacity: 1;
}

:deep(.el-scrollbar__view) {
  padding: 4px 0;
}
</style>
