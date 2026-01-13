<template>
  <div class="route-config">
    <el-alert type="info" :closable="false" style="margin-bottom: 16px;">
      Route 组件用于将事件流分流到多个子流。每个路由条件对应一个输出端口。
    </el-alert>
    
    <div class="routes-header">
      <span>路由条件</span>
      <el-button type="primary" size="small" @click="addRoute">
        <el-icon><Plus /></el-icon>添加路由
      </el-button>
    </div>
    
    <div class="routes-list">
      <div v-for="(route, index) in routes" :key="index" class="route-item">
        <div class="route-header">
          <el-input 
            v-model="route.name" 
            placeholder="路由名称 (如: errors, warnings)"
            size="small"
            style="width: 150px;"
            @change="emitChange"
          />
          <el-select 
            v-model="route.type" 
            size="small" 
            style="width: 100px;"
            @change="emitChange"
          >
            <el-option value="vrl" label="VRL" />
            <el-option value="datadog_search" label="Datadog" />
            <el-option value="is_log" label="is_log" />
            <el-option value="is_metric" label="is_metric" />
            <el-option value="is_trace" label="is_trace" />
          </el-select>
          <el-button 
            type="danger" 
            size="small" 
            text 
            @click="removeRoute(index)"
            :disabled="routes.length <= 1"
          >
            <el-icon><Delete /></el-icon>
          </el-button>
        </div>
        
        <el-input
          v-if="route.type === 'vrl' || route.type === 'datadog_search'"
          v-model="route.condition"
          type="textarea"
          :rows="2"
          :placeholder="getPlaceholder(route.type)"
          @change="emitChange"
        />
      </div>
    </div>
    
    <el-divider />
    
    <el-form-item label="未匹配事件处理">
      <el-switch 
        v-model="reroute_unmatched" 
        active-text="输出到 _unmatched" 
        inactive-text="丢弃"
        @change="emitChange"
      />
    </el-form-item>
    
    <el-alert v-if="routes.length > 0" type="warning" :closable="false" style="margin-top: 12px;">
      <template #title>
        <span>输出端口说明</span>
      </template>
      <div style="font-size: 12px; margin-top: 4px;">
        <div v-for="route in routes" :key="route.name">
          • <code>{{ route.name || '(未命名)' }}</code> - 匹配条件的事件
        </div>
        <div v-if="reroute_unmatched">
          • <code>_unmatched</code> - 未匹配任何条件的事件
        </div>
      </div>
    </el-alert>
  </div>
</template>

<script setup lang="ts">
import { ref, watch, onMounted } from 'vue'
import { Plus, Delete } from '@element-plus/icons-vue'

interface RouteCondition {
  name: string
  type: 'vrl' | 'datadog_search' | 'is_log' | 'is_metric' | 'is_trace'
  condition?: string
}

const props = defineProps<{
  modelValue: Record<string, any>
}>()

const emit = defineEmits(['update:modelValue', 'change', 'routes-changed'])

const routes = ref<RouteCondition[]>([
  { name: 'default', type: 'vrl', condition: '' }
])
const reroute_unmatched = ref(true)

// 获取 placeholder 文本
const getPlaceholder = (type: string) => {
  if (type === 'vrl') {
    return '.level == "error"'
  }
  return '*error*'
}

// 从 modelValue 初始化
onMounted(() => {
  if (props.modelValue?.route) {
    const routeConfig = props.modelValue.route
    routes.value = Object.entries(routeConfig).map(([name, config]: [string, any]) => {
      if (typeof config === 'string') {
        return { name, type: 'vrl' as const, condition: config }
      }
      return {
        name,
        type: config.type || 'vrl',
        condition: config.condition || config.source || ''
      }
    })
    if (routes.value.length === 0) {
      routes.value = [{ name: 'default', type: 'vrl', condition: '' }]
    }
  }
  if (props.modelValue?.reroute_unmatched !== undefined) {
    reroute_unmatched.value = props.modelValue.reroute_unmatched
  }
})

watch(() => props.modelValue, (newVal) => {
  if (newVal?.route) {
    const routeConfig = newVal.route
    routes.value = Object.entries(routeConfig).map(([name, config]: [string, any]) => {
      if (typeof config === 'string') {
        return { name, type: 'vrl' as const, condition: config }
      }
      return {
        name,
        type: config.type || 'vrl',
        condition: config.condition || config.source || ''
      }
    })
  }
}, { deep: true })

const addRoute = () => {
  routes.value.push({ name: `route_${routes.value.length + 1}`, type: 'vrl', condition: '' })
  emitChange()
}

const removeRoute = (index: number) => {
  routes.value.splice(index, 1)
  emitChange()
}

const emitChange = () => {
  const routeConfig: Record<string, any> = {}
  routes.value.forEach(r => {
    if (r.name) {
      if (r.type === 'vrl' || r.type === 'datadog_search') {
        routeConfig[r.name] = {
          type: r.type,
          condition: r.condition || ''
        }
      } else {
        // is_log, is_metric, is_trace
        routeConfig[r.name] = {
          type: r.type
        }
      }
    }
  })
  
  const config = {
    route: routeConfig,
    reroute_unmatched: reroute_unmatched.value
  }
  
  emit('update:modelValue', config)
  emit('change', config)
  
  // 通知父组件路由条件变化，用于更新输出端口
  const routeNames = routes.value.map(r => r.name).filter(Boolean)
  if (reroute_unmatched.value) {
    routeNames.push('_unmatched')
  }
  emit('routes-changed', routeNames)
}
</script>

<style scoped lang="scss">
.route-config {
  .routes-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    margin-bottom: 12px;
    font-weight: 500;
  }
  
  .routes-list {
    .route-item {
      background: #f5f7fa;
      border-radius: 4px;
      padding: 12px;
      margin-bottom: 8px;
      
      .route-header {
        display: flex;
        gap: 8px;
        align-items: center;
        margin-bottom: 8px;
      }
    }
  }
  
  code {
    background: #e8e8e8;
    padding: 2px 6px;
    border-radius: 3px;
    font-family: monospace;
    font-size: 12px;
  }
}
</style>
