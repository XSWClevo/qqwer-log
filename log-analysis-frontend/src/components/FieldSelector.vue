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

const props = defineProps<{
  username?: string
}>()

const emit = defineEmits<{
  change: [fields: string[]]
}>()

// 所有可用字段定义
const ALL_FIELDS = [
  'timestamp', 'level', 'host', 'service', 'source',
  'message', 'facility', 'procid', 'sourceIp', 'raw'
]

// 字段标签映射
const FIELD_LABELS: Record<string, string> = {
  timestamp: '时间戳',
  level: '日志级别',
  host: '主机',
  service: '应用名',
  source: '来源类型',
  message: '消息',
  facility: '设施',
  procid: '进程ID',
  sourceIp: '来源IP',
  raw: '原始日志'
}

// 已选择的字段
const selectedFields = ref<string[]>([])

// 可用字段(排除已选择的)
const availableFields = computed(() => {
  return ALL_FIELDS.filter(field => !selectedFields.value.includes(field))
})

// 获取字段标签
const getFieldLabel = (field: string): string => {
  return FIELD_LABELS[field] || field
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
    const username = props.username || 'admin'
    await fetch(`/api/field-config/log_list?username=${username}`, {
      method: 'DELETE'
    })
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
    await saveFieldConfig(username, {
      configType: 'log_list',
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
    const config = await getFieldConfig(username, 'log_list')

    if (config.selectedFields && config.selectedFields.length > 0) {
      selectedFields.value = config.selectedFields
    } else {
      // 默认配置
      selectedFields.value = ['timestamp', 'level', 'host', 'service', 'message']
    }
  } catch (error) {
    console.error('加载配置失败:', error)
    // 使用默认配置
    selectedFields.value = ['timestamp', 'level', 'host', 'service', 'message']
  }
}

// 监听字段变化,通知父组件
watch(selectedFields, (newFields) => {
  emit('change', newFields)
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
  background: #f5f7fa;
  border-radius: 4px;

  :deep(.el-button.is-link) {
    &.el-button--primary {
      color: #409EFF !important;
      
      &:hover {
        color: #66b1ff !important;
      }
    }
    
    &.el-button--danger {
      color: #F56C6C !important;
      
      &:hover {
        color: #f78989 !important;
      }
    }
  }
}

.header-title {
  font-weight: 600;
  font-size: 14px;
  color: #303133;
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
  color: #606266;
  background: #fafafa;
  border-radius: 4px;
  margin-bottom: 8px;

  :deep(.el-button.is-link) {
    &.el-button--danger {
      color: #F56C6C !important;
      
      &:hover {
        color: #f78989 !important;
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
  background: #ecf5ff;
  border-color: #d9ecff;
  cursor: move;
}

.field-item.selected:hover {
  background: #d9ecff;
  border-color: #b3d8ff;
}

.field-item.available {
  background: #f5f7fa;
}

.field-item.available:hover {
  background: #e4e7ed;
  border-color: #dcdfe6;
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
