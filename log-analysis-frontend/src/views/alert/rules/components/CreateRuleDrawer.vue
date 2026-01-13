<template>
  <el-drawer
    v-model="visible"
    :title="isEditMode ? '编辑规则' : '创建新规则'"
    size="720px"
    :before-close="handleClose"
  >
    <div class="create-rule-form">
      <el-steps :active="currentStep" finish-status="success" align-center>
        <el-step title="定义查询" description="What" />
        <el-step title="设置触发条件" description="When" />
        <el-step title="配置动作" description="Then" />
      </el-steps>

      <div class="step-content">
        <!-- Step 1: 定义查询 -->
        <div v-show="currentStep === 0" class="step-panel">
          <h3 class="step-title">定义查询条件</h3>
          <el-form :model="formData" label-position="top">
            <el-form-item label="规则名称" required>
              <el-input v-model="formData.name" placeholder="例如: Payment Service Error Logs" />
            </el-form-item>
            <el-form-item label="描述">
              <el-input v-model="formData.description" type="textarea" :rows="2" />
            </el-form-item>
            
            <!-- 查询构建器 -->
            <el-form-item label="日志查询条件" required>
              <QueryBuilder v-model="formData.query" />
            </el-form-item>
          </el-form>
        </div>

        <!-- Step 2: 设置触发条件 -->
        <div v-show="currentStep === 1" class="step-panel">
          <h3 class="step-title">设置触发条件</h3>
          <el-form :model="formData" label-position="top">
            <el-row :gutter="16">
              <el-col :span="12">
                <el-form-item label="评估频率" required>
                  <el-select v-model="formData.evaluateEvery" style="width: 100%">
                    <el-option label="每 1 分钟" value="1m" />
                    <el-option label="每 5 分钟" value="5m" />
                    <el-option label="每 10 分钟" value="10m" />
                    <el-option label="每 30 分钟" value="30m" />
                    <el-option label="每 1 小时" value="1h" />
                  </el-select>
                </el-form-item>
              </el-col>
              <el-col :span="12">
                <el-form-item label="时间窗口" required>
                  <el-select v-model="formData.timeWindow" style="width: 100%">
                    <el-option label="最近 1 分钟" value="1m" />
                    <el-option label="最近 5 分钟" value="5m" />
                    <el-option label="最近 10 分钟" value="10m" />
                    <el-option label="最近 30 分钟" value="30m" />
                    <el-option label="最近 1 小时" value="1h" />
                  </el-select>
                </el-form-item>
              </el-col>
            </el-row>

            <el-form-item label="触发条件" required>
              <div class="trigger-builder">
                <el-select v-model="formData.triggerMetric" style="width: 150px">
                  <el-option label="结果数量" value="count" />
                  <el-option label="唯一值数量" value="unique" />
                  <el-option label="平均值" value="avg" />
                  <el-option label="最大值" value="max" />
                </el-select>
                <el-select v-model="formData.triggerOperator" style="width: 120px">
                  <el-option label="大于" value="gt" />
                  <el-option label="大于等于" value="gte" />
                  <el-option label="小于" value="lt" />
                  <el-option label="小于等于" value="lte" />
                  <el-option label="等于" value="eq" />
                </el-select>
                <el-input-number v-model="formData.triggerValue" :min="0" style="width: 150px" />
              </div>
            </el-form-item>

            <el-form-item label="分组字段 (可选)">
              <el-input v-model="formData.groupBy" placeholder="例如: hostname" />
              <div class="form-tip">按指定字段分组统计，每个分组独立触发告警</div>
            </el-form-item>
          </el-form>
        </div>

        <!-- Step 3: 配置动作 -->
        <div v-show="currentStep === 2" class="step-panel">
          <h3 class="step-title">配置告警动作</h3>
          <el-form :model="formData" label-position="top">
            <el-form-item label="严重程度" required>
              <el-radio-group v-model="formData.severity">
                <el-radio-button label="critical">严重</el-radio-button>
                <el-radio-button label="warning">警告</el-radio-button>
                <el-radio-button label="info">信息</el-radio-button>
              </el-radio-group>
            </el-form-item>

            <el-form-item label="通知渠道" required>
              <el-checkbox-group v-model="formData.channels">
                <el-checkbox label="slack">
                  <el-icon><ChatDotRound /></el-icon>
                  Slack
                </el-checkbox>
                <el-checkbox label="email">
                  <el-icon><Message /></el-icon>
                  邮件
                </el-checkbox>
                <el-checkbox label="webhook">
                  <el-icon><Link /></el-icon>
                  Webhook
                </el-checkbox>
              </el-checkbox-group>
            </el-form-item>

            <el-form-item label="消息模板">
              <el-input
                v-model="formData.messageTemplate"
                type="textarea"
                :rows="4"
                placeholder="告警: {{rule_name}} 触发&#10;数量: {{count}}&#10;服务: {{service_name}}"
              />
              <div class="form-tip">
                支持变量: &#123;&#123;count&#125;&#125;, &#123;&#123;rule_name&#125;&#125;, &#123;&#123;service_name&#125;&#125;, &#123;&#123;hostname&#125;&#125;
              </div>
            </el-form-item>
          </el-form>
        </div>
      </div>

      <div class="drawer-footer">
        <el-button v-if="currentStep > 0" @click="currentStep--">上一步</el-button>
        <el-button v-if="currentStep < 2" type="primary" @click="currentStep++">下一步</el-button>
        <el-button v-if="currentStep === 2" type="primary" @click="handleSubmit">保存规则</el-button>
        <el-button @click="handleClose">取消</el-button>
      </div>
    </div>
  </el-drawer>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { ChatDotRound, Message, Link } from '@element-plus/icons-vue'
import { createAlertRule, updateAlertRule, getAlertRuleById } from '@/api/alert'
import QueryBuilder from './QueryBuilder.vue'

const props = defineProps<{
  modelValue: boolean
  ruleId?: number | null
}>()

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  success: []
}>()

const visible = ref(false)
const currentStep = ref(0)
const isEditMode = ref(false)
const loading = ref(false)

const formData = ref({
  name: '',
  description: '',
  query: '',
  evaluateEvery: '5m',
  timeWindow: '5m',
  triggerMetric: 'count',
  triggerOperator: 'gt',
  triggerValue: 10,
  groupBy: '',
  severity: 'warning',
  channels: ['slack'],
  messageTemplate: '告警: {{rule_name}} 触发\n数量: {{count}}\n时间: {{timestamp}}'
})

watch(() => props.modelValue, async (val) => {
  visible.value = val
  if (val) {
    currentStep.value = 0
    isEditMode.value = !!props.ruleId
    
    if (isEditMode.value && props.ruleId) {
      await loadRuleData(props.ruleId)
    } else {
      resetForm()
    }
  }
})

watch(visible, (val) => {
  emit('update:modelValue', val)
})

// 加载规则数据（编辑模式）
const loadRuleData = async (ruleId: number) => {
  loading.value = true
  try {
    const response = await getAlertRuleById(ruleId)
    const rule = response.data || response
    
    if (rule) {
      const condition = rule.condition || {}
      
      formData.value = {
        name: rule.name || '',
        description: rule.description || '',
        query: condition.query || '',
        evaluateEvery: '5m', // 暂时固定，后续可以从规则中读取
        timeWindow: condition.timeWindow || '5m',
        triggerMetric: condition.metric || 'count',
        triggerOperator: condition.operator || 'gt',
        triggerValue: condition.value || 10,
        groupBy: condition.groupBy || '',
        severity: (rule.severity || 'warning').toLowerCase(),
        channels: rule.notificationChannels || ['slack'],
        messageTemplate: '告警: {{rule_name}} 触发\n数量: {{count}}\n时间: {{timestamp}}'
      }
    }
  } catch (error: any) {
    ElMessage.error('加载规则数据失败: ' + (error.message || '未知错误'))
    visible.value = false
  } finally {
    loading.value = false
  }
}

const resetForm = () => {
  formData.value = {
    name: '',
    description: '',
    query: '',
    evaluateEvery: '5m',
    timeWindow: '5m',
    triggerMetric: 'count',
    triggerOperator: 'gt',
    triggerValue: 10,
    groupBy: '',
    severity: 'warning',
    channels: ['slack'],
    messageTemplate: '告警: {{rule_name}} 触发\n数量: {{count}}\n时间: {{timestamp}}'
  }
}

const handleRunQuery = () => {
  ElMessage.info('查询预览功能开发中...')
}

const handleSubmit = async () => {
  if (!formData.value.name || !formData.value.query) {
    ElMessage.warning('请填写必填项')
    return
  }

  try {
    const condition = {
      type: 'log_query',
      query: formData.value.query,
      metric: formData.value.triggerMetric,
      operator: formData.value.triggerOperator,
      value: formData.value.triggerValue,
      timeWindow: formData.value.timeWindow,
      groupBy: formData.value.groupBy || undefined
    }

    const payload = {
      name: formData.value.name,
      description: formData.value.description,
      condition,
      severity: formData.value.severity.toUpperCase(),
      notificationChannels: formData.value.channels,
      silencePeriod: 300,
      enabled: true
    }

    if (isEditMode.value && props.ruleId) {
      await updateAlertRule(props.ruleId, payload)
      ElMessage.success('规则更新成功')
    } else {
      await createAlertRule(payload)
      ElMessage.success('规则创建成功')
    }

    emit('success')
    visible.value = false
  } catch (error: any) {
    ElMessage.error(`${isEditMode.value ? '更新' : '创建'}规则失败: ` + (error.message || '未知错误'))
  }
}

const handleClose = () => {
  visible.value = false
}
</script>

<style scoped lang="scss">
.create-rule-form {
  display: flex;
  flex-direction: column;
  height: 100%;

  :deep(.el-steps) {
    padding: 0 40px 32px;
  }

  .step-content {
    flex: 1;
    overflow-y: auto;
    padding: 0 24px;
  }

  .step-panel {
    .step-title {
      font-size: 18px;
      font-weight: 600;
      color: #262626;
      margin: 0 0 24px 0;
    }

    .query-input {
      font-family: 'Monaco', 'Courier New', monospace;
    }

    .trigger-builder {
      display: flex;
      gap: 12px;
      align-items: center;
    }

    .form-tip {
      font-size: 12px;
      color: #8C8C8C;
      margin-top: 8px;
    }

    :deep(.el-checkbox) {
      display: flex;
      align-items: center;
      margin-right: 24px;

      .el-icon {
        margin-right: 4px;
      }
    }
  }

  .drawer-footer {
    padding: 16px 24px;
    border-top: 1px solid #E8E8E8;
    display: flex;
    justify-content: flex-end;
    gap: 12px;
  }
}
</style>
