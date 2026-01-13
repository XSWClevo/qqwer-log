<template>
  <AppLayout>
    <div class="settings-container">
      <!-- 顶部标题栏 -->
      <div class="page-header">
        <div class="header-left">
          <h1 class="page-title">系统设置</h1>
          <p class="subtitle">系统配置与版本信息</p>
        </div>
      </div>

      <div class="content-area">
        <el-row :gutter="20">
        <!-- 数据库配置 -->
        <el-col :span="12">
          <el-card shadow="never" class="settings-card clickable" @click="goToDatabaseConfig">
            <template #header>
              <div class="card-header">
                <el-icon><Setting /></el-icon>
                <span>数据库配置</span>
              </div>
            </template>

            <div class="card-content">
              <p class="description">管理 ClickHouse、PostgreSQL、Elasticsearch 的 DDL 默认配置</p>
              <el-button type="primary" link>
                前往配置
                <el-icon class="el-icon--right"><ArrowRight /></el-icon>
              </el-button>
            </div>
          </el-card>
        </el-col>

        <!-- 日志保留策略 -->
        <el-col :span="12">
          <el-card shadow="never" class="settings-card">
            <template #header>
              <div class="card-header">
                <el-icon><Timer /></el-icon>
                <span>日志保留策略</span>
              </div>
            </template>
            
            <el-form label-width="120px" :model="retentionForm">
              <el-form-item label="保留天数">
                <el-input-number 
                  v-model="retentionForm.days" 
                  :min="1" 
                  :max="365"
                  :step="1"
                />
                <span class="form-tip">天</span>
              </el-form-item>
              <el-form-item label="TTL 表达式">
                <el-input 
                  v-model="ttlExpression" 
                  readonly
                  class="ttl-input"
                >
                  <template #append>
                    <el-button @click="copyTTL">
                      <el-icon><CopyDocument /></el-icon>
                    </el-button>
                  </template>
                </el-input>
              </el-form-item>
              <el-form-item>
                <el-alert type="info" :closable="false">
                  <template #title>ClickHouse TTL 说明</template>
                  <p>在 ClickHouse 表中添加 TTL 表达式可自动清理过期日志：</p>
                  <pre class="code-block">ALTER TABLE logs
MODIFY TTL timestamp + INTERVAL {{ retentionForm.days }} DAY;</pre>
                </el-alert>
              </el-form-item>
            </el-form>
          </el-card>
        </el-col>

        <!-- 关于/版本 -->
        <el-col :span="12">
          <el-card shadow="never" class="settings-card">
            <template #header>
              <div class="card-header">
                <el-icon><InfoFilled /></el-icon>
                <span>关于系统</span>
              </div>
            </template>
            
            <el-descriptions :column="1" border>
              <el-descriptions-item label="系统名称">日志分析平台</el-descriptions-item>
              <el-descriptions-item label="版本号">{{ systemInfo.version }}</el-descriptions-item>
              <el-descriptions-item label="构建时间">{{ systemInfo.buildTime }}</el-descriptions-item>
              <el-descriptions-item label="Vector 版本">{{ systemInfo.vectorVersion }}</el-descriptions-item>
              <el-descriptions-item label="Agent 版本">{{ systemInfo.agentVersion }}</el-descriptions-item>
            </el-descriptions>

            <el-divider />

            <div class="tech-stack">
              <h4>技术栈</h4>
              <el-space wrap>
                <el-tag>Vue 3</el-tag>
                <el-tag>Spring Boot</el-tag>
                <el-tag>PostgreSQL</el-tag>
                <el-tag>ClickHouse</el-tag>
                <el-tag>Vector</el-tag>
                <el-tag>Go</el-tag>
              </el-space>
            </div>
          </el-card>
        </el-col>
      </el-row>
      </div>
    </div>
  </AppLayout>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Timer, InfoFilled, CopyDocument, Setting, ArrowRight } from '@element-plus/icons-vue'
import axios from 'axios'
import AppLayout from '@/components/layout/AppLayout.vue'

const router = useRouter()

const retentionForm = ref({
  days: 30
})

const systemInfo = ref({
  version: '1.0.0',
  buildTime: '-',
  vectorVersion: '-',
  agentVersion: '-'
})

const ttlExpression = computed(() => {
  return `TTL timestamp + INTERVAL ${retentionForm.value.days} DAY`
})

const copyTTL = () => {
  const sql = `ALTER TABLE logs MODIFY TTL timestamp + INTERVAL ${retentionForm.value.days} DAY;`
  navigator.clipboard.writeText(sql)
  ElMessage.success('TTL SQL 已复制')
}

const goToDatabaseConfig = () => {
  router.push('/settings/database-config')
}

const fetchSystemInfo = async () => {
  try {
    // 获取最新的 Bundle 版本作为系统版本
    const { data } = await axios.get('/api/vector/packages/list', {
      params: { packageType: 'vector-agent-bundle' }
    })
    if (data.code === 200 && data.data?.length > 0) {
      const latest = data.data.find((p: any) => p.isLatest) || data.data[0]
      systemInfo.value.agentVersion = latest.version
      systemInfo.value.buildTime = latest.createdAt?.split('T')[0] || '-'
    }
  } catch (error) {
    console.error('获取系统信息失败:', error)
  }
  
  // 设置固定版本信息
  systemInfo.value.version = '1.0.0'
  systemInfo.value.vectorVersion = '0.52.0'
}

onMounted(() => {
  fetchSystemInfo()
})
</script>

<style scoped lang="scss">
@use '@/assets/styles/macos.scss' as macos;

.settings-container {
  @include macos.macos-page-container;
}

.page-header {
  @include macos.macos-page-header;

  .header-left {
    .page-title {
      @include macos.macos-page-title;
      margin-bottom: 2px;
    }

    .subtitle {
      margin: 0;
      font-size: 13px;
      color: var(--macos-text-secondary);
    }
  }
}

.content-area {
  padding: 20px;
}

.settings-card {
  height: 100%;
  @include macos.macos-card;

  .card-header {
    display: flex;
    align-items: center;
    gap: 8px;
    font-weight: 600;
  }

  &.clickable {
    cursor: pointer;
    transition: all 0.2s ease;

    &:hover {
      transform: translateY(-2px);
      box-shadow: 0 4px 12px rgba(0, 0, 0, 0.1);
    }
  }
}

.card-content {
  .description {
    margin: 0 0 16px 0;
    color: var(--macos-text-secondary);
    line-height: 1.6;
  }
}

.form-tip {
  margin-left: 8px;
  color: var(--macos-text-secondary);
}

.ttl-input {
  font-family: 'Monaco', 'Consolas', monospace;
}

.code-block {
  background: var(--macos-bg-tertiary);
  padding: 12px;
  border-radius: 4px;
  font-family: 'Monaco', 'Consolas', monospace;
  font-size: 12px;
  margin: 8px 0 0 0;
  overflow-x: auto;
  color: var(--macos-text-primary);
}

.tech-stack {
  h4 {
    margin: 0 0 12px 0;
    font-size: 14px;
    color: var(--macos-text-secondary);
  }
}
</style>
