<template>
  <div class="log-source-table">
    <el-table
      v-loading="loading"
      :data="data"
      border
      stripe
      style="width: 100%"
    >
      <el-table-column prop="sourceIp" label="IP地址" width="150" />
      <el-table-column prop="hostname" label="主机名" width="180" show-overflow-tooltip />
      <el-table-column prop="description" label="描述" min-width="200" show-overflow-tooltip />
      <el-table-column prop="logCount" label="日志数量" width="120" align="right">
        <template #default="{ row }">
          {{ formatNumber(row.logCount) }}
        </template>
      </el-table-column>
      <el-table-column prop="firstSeenAt" label="首次发现" width="180">
        <template #default="{ row }">
          {{ formatTime(row.firstSeenAt) }}
        </template>
      </el-table-column>
      <el-table-column prop="lastSeenAt" label="最后活跃" width="180">
        <template #default="{ row }">
          {{ formatTime(row.lastSeenAt) }}
        </template>
      </el-table-column>
      <el-table-column v-if="status === 'trusted'" prop="trustedBy" label="操作人" width="120" />
      <el-table-column label="操作" width="200" fixed="right">
        <template #default="{ row }">
          <div class="action-buttons">
            <el-button
              v-if="status !== 'trusted'"
              type="success"
              size="small"
              @click="$emit('trust', row)"
            >
              <el-icon><Select /></el-icon>
              信任
            </el-button>
            <el-button
              v-if="status !== 'blocked'"
              type="warning"
              size="small"
              @click="$emit('block', row)"
            >
              <el-icon><CircleClose /></el-icon>
              拉黑
            </el-button>
            <el-button
              type="danger"
              size="small"
              @click="$emit('delete', row)"
            >
              <el-icon><Delete /></el-icon>
              删除
            </el-button>
          </div>
        </template>
      </el-table-column>
    </el-table>

    <el-empty v-if="!loading && data.length === 0" description="暂无数据" />
  </div>
</template>

<script setup lang="ts">
import { Select, CircleClose, Delete } from '@element-plus/icons-vue'
import type { LogSourceDTO } from '@/api/log-source'

defineProps<{
  data: LogSourceDTO[]
  loading: boolean
  status: 'trusted' | 'pending' | 'blocked'
}>()

defineEmits<{
  trust: [source: LogSourceDTO]
  block: [source: LogSourceDTO]
  delete: [source: LogSourceDTO]
}>()

// 格式化数字
const formatNumber = (num?: number) => {
  if (num === undefined || num === null) return '-'
  return num.toLocaleString()
}

// 格式化时间
const formatTime = (time?: string) => {
  if (!time) return '-'
  return new Date(time).toLocaleString('zh-CN')
}
</script>

<style scoped lang="scss">
.log-source-table {
  .action-buttons {
    display: flex;
    gap: 8px;
    flex-wrap: wrap;

    .el-button {
      padding: 4px 8px;
      font-size: 12px;
    }
  }
}
</style>
