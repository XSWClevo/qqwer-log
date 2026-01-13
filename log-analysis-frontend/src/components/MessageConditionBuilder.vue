<template>
  <div class="message-condition-builder">
    <el-form-item :label="label">
      <div class="conditions-list">
        <div v-for="(condition, index) in modelValue" :key="index" class="condition-row">
          <el-select
            v-model="condition.operator"
            placeholder="操作"
            style="width: 120px"
            @change="emitUpdate"
          >
            <el-option label="包含" value="contains" />
            <el-option label="不包含" value="notContains" />
            <el-option label="等于" value="equals" />
            <el-option label="不等于" value="notEquals" />
          </el-select>

          <el-input
            v-model="condition.value"
            :placeholder="`输入${label}的值`"
            clearable
            class="condition-input"
            @input="emitUpdate"
          />

          <el-button
            :icon="Close"
            circle
            size="small"
            @click="removeCondition(index)"
          />
        </div>

        <el-button
          :icon="Plus"
          size="small"
          class="add-button"
          @click="addCondition"
        >
          添加{{ label }}条件
        </el-button>
      </div>
    </el-form-item>
  </div>
</template>

<script setup lang="ts">
import { Plus, Close } from '@element-plus/icons-vue'

export interface MessageCondition {
  operator: 'contains' | 'notContains' | 'equals' | 'notEquals'
  value: string
}

interface Props {
  modelValue: MessageCondition[]
  label?: string
}

const props = withDefaults(defineProps<Props>(), {
  label: 'Message'
})

const emit = defineEmits(['update:modelValue'])

const addCondition = () => {
  const newConditions = [...props.modelValue, { operator: 'contains' as const, value: '' }]
  emit('update:modelValue', newConditions)
}

const removeCondition = (index: number) => {
  const newConditions = props.modelValue.filter((_, i) => i !== index)
  emit('update:modelValue', newConditions)
}

const emitUpdate = () => {
  // 触发更新，确保父组件响应
  emit('update:modelValue', [...props.modelValue])
}
</script>

<style scoped>
.message-condition-builder {
  width: 100%;
}

.conditions-list {
  width: 100%;
}

.condition-row {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
}

.condition-input {
  flex: 1;
}

.add-button {
  width: 100%;
  margin-top: 4px;
}
</style>
