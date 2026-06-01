<template>
  <AppLayout>
    <div class="database-config-container">
      <!-- 顶部标题栏 -->
      <div class="page-header">
        <div class="header-left">
          <h1 class="page-title">数据库配置</h1>
          <p class="subtitle">管理数据库 DDL 默认配置</p>
        </div>
        <div class="header-right">
          <el-button type="primary" @click="saveAllConfigs" :loading="saving">
            <el-icon><Select /></el-icon>
            保存所有配置
          </el-button>
        </div>
      </div>

      <div class="content-area">
        <el-tabs v-model="activeTab" type="border-card">
          <!-- ClickHouse 配置 -->
          <el-tab-pane label="ClickHouse" name="clickhouse">
            <el-form :model="clickhouseConfig" label-width="140px" class="config-form">
              <el-form-item label="表引擎">
                <el-select v-model="clickhouseConfig['ddl.engine']" placeholder="选择表引擎">
                  <el-option label="MergeTree" value="MergeTree" />
                  <el-option label="ReplicatedMergeTree" value="ReplicatedMergeTree" />
                  <el-option label="ReplacingMergeTree" value="ReplacingMergeTree" />
                </el-select>
                <span class="form-tip">默认表引擎类型</span>
              </el-form-item>

              <el-form-item label="分区策略">
                <el-input v-model="clickhouseConfig['ddl.partition_by']" placeholder="例如: toYYYYMM(timestamp)" />
                <span class="form-tip">按时间分区，提高查询性能</span>
              </el-form-item>

              <el-form-item label="排序键">
                <el-input v-model="clickhouseConfig['ddl.order_by']" placeholder="例如: timestamp,hostname" />
                <span class="form-tip">逗号分隔的字段列表</span>
              </el-form-item>

              <el-form-item label="数据保留期">
                <el-input-number 
                  v-model.number="ttlDays" 
                  :min="1" 
                  :max="3650"
                  :step="1"
                />
                <span class="form-tip">天（TTL 自动清理）</span>
              </el-form-item>

              <el-form-item label="压缩编码">
                <el-select v-model="clickhouseConfig['ddl.compression']" placeholder="选择压缩算法">
                  <el-option label="LZ4" value="LZ4" />
                  <el-option label="ZSTD" value="ZSTD" />
                  <el-option label="None" value="None" />
                </el-select>
                <span class="form-tip">数据压缩算法</span>
              </el-form-item>

              <el-form-item label="保留原始日志">
                <el-switch v-model="keepRaw" />
                <span class="form-tip">是否保留 raw 字段</span>
              </el-form-item>

              <el-form-item label="索引配置">
                <el-input 
                  v-model="clickhouseConfig['ddl.indexes']" 
                  type="textarea"
                  :rows="3"
                  placeholder="例如: timestamp:minmax,hostname:set"
                />
                <span class="form-tip">格式: 字段名:索引类型，逗号分隔</span>
              </el-form-item>
            </el-form>
          </el-tab-pane>

          <!-- PostgreSQL 配置 -->
          <el-tab-pane label="PostgreSQL" name="postgresql">
            <el-form :model="postgresqlConfig" label-width="140px" class="config-form">
              <el-form-item label="主键类型">
                <el-select v-model="postgresqlConfig['ddl.primary_key_type']" placeholder="选择主键类型">
                  <el-option label="UUID" value="UUID" />
                  <el-option label="SERIAL" value="SERIAL" />
                  <el-option label="BIGSERIAL" value="BIGSERIAL" />
                </el-select>
                <span class="form-tip">主键生成策略</span>
              </el-form-item>

              <el-form-item label="索引配置">
                <el-input 
                  v-model="postgresqlConfig['ddl.indexes']" 
                  type="textarea"
                  :rows="3"
                  placeholder="例如: timestamp:btree,hostname:btree"
                />
                <span class="form-tip">格式: 字段名:索引类型，逗号分隔</span>
              </el-form-item>
            </el-form>
          </el-tab-pane>

          <!-- Elasticsearch 配置 -->
          <el-tab-pane label="Elasticsearch" name="elasticsearch">
            <el-form :model="elasticsearchConfig" label-width="140px" class="config-form">
              <el-form-item label="分片数">
                <el-input-number 
                  v-model.number="esShards" 
                  :min="1" 
                  :max="100"
                  :step="1"
                />
                <span class="form-tip">索引分片数量</span>
              </el-form-item>

              <el-form-item label="副本数">
                <el-input-number 
                  v-model.number="esReplicas" 
                  :min="0" 
                  :max="10"
                  :step="1"
                />
                <span class="form-tip">每个分片的副本数</span>
              </el-form-item>

              <el-form-item label="分词器">
                <el-select v-model="elasticsearchConfig['ddl.analyzer']" placeholder="选择分词器">
                  <el-option label="standard" value="standard" />
                  <el-option label="ik_max_word" value="ik_max_word" />
                  <el-option label="ik_smart" value="ik_smart" />
                </el-select>
                <span class="form-tip">文本分析器</span>
              </el-form-item>

              <el-form-item label="ILM 保留期">
                <el-input-number 
                  v-model.number="esIlmDays" 
                  :min="1" 
                  :max="3650"
                  :step="1"
                />
                <span class="form-tip">天（索引生命周期管理）</span>
              </el-form-item>
            </el-form>
          </el-tab-pane>
        </el-tabs>

        <!-- 配置说明 -->
        <el-card shadow="never" class="info-card">
          <template #header>
            <div class="card-header">
              <el-icon><InfoFilled /></el-icon>
              <span>配置说明</span>
            </div>
          </template>
          <el-alert type="info" :closable="false">
            <p>这些配置将作为智能向导生成 DDL 时的默认值，用户可以在创建表时进行调整。</p>
            <ul>
              <li><strong>ClickHouse</strong>: 适用于大规模日志存储，支持高性能查询和自动 TTL 清理</li>
              <li><strong>PostgreSQL</strong>: 适用于结构化数据存储，支持复杂查询和事务</li>
              <li><strong>Elasticsearch</strong>: 适用于全文搜索和日志分析，支持灵活的索引管理</li>
            </ul>
          </el-alert>
        </el-card>
      </div>
    </div>
  </AppLayout>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { Select, InfoFilled } from '@element-plus/icons-vue'
import { getAllConfigs, batchUpdateConfig } from '@/api/config'
import AppLayout from '@/components/layout/AppLayout.vue'

const activeTab = ref('clickhouse')
const saving = ref(false)

// ClickHouse 配置
const clickhouseConfig = ref<Record<string, string>>({
  'ddl.engine': 'MergeTree',
  'ddl.partition_by': 'toYYYYMM(timestamp)',
  'ddl.order_by': 'timestamp,hostname',
  'ddl.ttl_days': '30',
  'ddl.compression': 'LZ4',
  'ddl.keep_raw': 'true',
  'ddl.indexes': 'timestamp:minmax,hostname:set'
})

// PostgreSQL 配置
const postgresqlConfig = ref<Record<string, string>>({
  'ddl.primary_key_type': 'UUID',
  'ddl.indexes': 'timestamp:btree,hostname:btree'
})

// Elasticsearch 配置
const elasticsearchConfig = ref<Record<string, string>>({
  'ddl.number_of_shards': '3',
  'ddl.number_of_replicas': '1',
  'ddl.analyzer': 'standard',
  'ddl.ilm_retention_days': '30'
})

// 计算属性用于双向绑定数字类型
const ttlDays = computed({
  get: () => parseInt(clickhouseConfig.value['ddl.ttl_days'] || '30'),
  set: (val) => { clickhouseConfig.value['ddl.ttl_days'] = String(val) }
})

const keepRaw = computed({
  get: () => clickhouseConfig.value['ddl.keep_raw'] === 'true',
  set: (val) => { clickhouseConfig.value['ddl.keep_raw'] = String(val) }
})

const esShards = computed({
  get: () => parseInt(elasticsearchConfig.value['ddl.number_of_shards'] || '3'),
  set: (val) => { elasticsearchConfig.value['ddl.number_of_shards'] = String(val) }
})

const esReplicas = computed({
  get: () => parseInt(elasticsearchConfig.value['ddl.number_of_replicas'] || '1'),
  set: (val) => { elasticsearchConfig.value['ddl.number_of_replicas'] = String(val) }
})

const esIlmDays = computed({
  get: () => parseInt(elasticsearchConfig.value['ddl.ilm_retention_days'] || '30'),
  set: (val) => { elasticsearchConfig.value['ddl.ilm_retention_days'] = String(val) }
})

/**
 * 加载所有配置
 */
const loadConfigs = async () => {
  try {
    const response = await getAllConfigs()
    if (response.code === 200 && response.data) {
      const configs = response.data as Array<{
        configKey: string
        configValue: string
        configType: string
      }>

      // 分类配置
      configs.forEach(config => {
        if (config.configType === 'clickhouse') {
          clickhouseConfig.value[config.configKey] = config.configValue
        } else if (config.configType === 'postgresql') {
          postgresqlConfig.value[config.configKey] = config.configValue
        } else if (config.configType === 'elasticsearch') {
          elasticsearchConfig.value[config.configKey] = config.configValue
        }
      })
    }
  } catch (error) {
    console.error('加载配置失败:', error)
    ElMessage.error('加载配置失败')
  }
}

/**
 * 保存所有配置
 */
const saveAllConfigs = async () => {
  saving.value = true
  try {
    const allConfigs = [
      ...Object.entries(clickhouseConfig.value).map(([key, value]) => ({
        key,
        value
      })),
      ...Object.entries(postgresqlConfig.value).map(([key, value]) => ({
        key,
        value
      })),
      ...Object.entries(elasticsearchConfig.value).map(([key, value]) => ({
        key,
        value
      }))
    ]

    console.log('保存配置:', allConfigs)

    // 使用批量更新接口
    const response = await batchUpdateConfig(allConfigs)

    console.log('完整响应:', response)

    // response 本身就是 {code: 200, message: 'success', data: null}
    if (response.code === 200) {
      ElMessage.success('配置保存成功')
      // 重新加载配置
      await loadConfigs()
    } else {
      ElMessage.error(response.message || '配置保存失败')
    }
  } catch (error: any) {
    console.error('保存配置失败:', error)
    ElMessage.error(error.response?.data?.message || error.message || '配置保存失败')
  } finally {
    saving.value = false
  }
}

onMounted(() => {
  loadConfigs()
})
</script>

<style scoped lang="scss">
@use '@/assets/styles/macos.scss' as macos;

.database-config-container {
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

  .header-right {
    display: flex;
    gap: 12px;
  }
}

.content-area {
  padding: 20px;
}

.config-form {
  padding: 20px;
  max-width: 800px;

  .form-tip {
    margin-left: 12px;
    font-size: 12px;
    color: var(--macos-text-secondary);
  }
}

.info-card {
  margin-top: 20px;
  @include macos.macos-card;

  .card-header {
    display: flex;
    align-items: center;
    gap: 8px;
    font-weight: 600;
  }

  ul {
    margin: 12px 0 0 0;
    padding-left: 20px;

    li {
      margin: 8px 0;
      line-height: 1.6;
    }
  }
}
</style>
