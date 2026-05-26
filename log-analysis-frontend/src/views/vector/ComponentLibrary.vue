<template>
  <AppLayout>
    <div class="component-library">
      <el-card shadow="never" class="page-header">
        <div class="header-content">
          <div>
            <h2>组件库</h2>
            <p class="subtitle">管理可复用的 Vector 配置组件模板</p>
          </div>
          <div class="header-actions">
            <el-button type="success" @click="openSmartWizard">
              <el-icon><MagicStick /></el-icon>
              智能向导
            </el-button>
            <el-button type="primary" @click="openDialog()">
              <el-icon><Plus /></el-icon>
              新建组件
            </el-button>
          </div>
        </div>
      </el-card>

      <!-- 筛选 -->
      <el-card shadow="never" class="filter-card">
        <div class="filter-content">
          <!-- 类型标签筛选 -->
          <div class="type-filter">
            <span class="filter-label">类型</span>
            <div class="type-tags">
              <div 
                class="type-tag" 
                :class="{ active: filters.componentTypes.length === 0 }"
                @click="selectAll"
              >
                <el-icon><Grid /></el-icon>
                <span>全部</span>
                <span class="count">{{ totalCount }}</span>
              </div>
              <div 
                class="type-tag source" 
                :class="{ active: filters.componentTypes.includes('source') }"
                @click="toggleType('source')"
              >
                <el-icon><Upload /></el-icon>
                <span>Source</span>
                <span class="count">{{ sourceCount }}</span>
              </div>
              <div 
                class="type-tag transform" 
                :class="{ active: filters.componentTypes.includes('transform') }"
                @click="toggleType('transform')"
              >
                <el-icon><Operation /></el-icon>
                <span>Transform</span>
                <span class="count">{{ transformCount }}</span>
              </div>
              <div 
                class="type-tag sink" 
                :class="{ active: filters.componentTypes.includes('sink') }"
                @click="toggleType('sink')"
              >
                <el-icon><Download /></el-icon>
                <span>Sink</span>
                <span class="count">{{ sinkCount }}</span>
              </div>
            </div>
          </div>
          <!-- 关键词搜索 -->
          <div class="keyword-filter">
            <el-input 
              v-model="filters.keyword" 
              placeholder="搜索组件名称..." 
              clearable 
              @clear="fetchList"
              @keyup.enter="fetchList"
              :prefix-icon="Search"
            />
          </div>
        </div>
      </el-card>

      <!-- 组件列表 -->
      <div v-if="loading" class="loading-container">
        <el-skeleton :rows="5" animated />
      </div>
      <div v-else-if="filteredComponents.length === 0" class="empty-container">
        <el-empty :description="filters.componentTypes.length > 0 || filters.keyword ? '没有匹配的组件' : '暂无组件'">
          <el-button type="primary" @click="openDialog()">创建第一个组件</el-button>
        </el-empty>
      </div>
      <div v-else class="component-grid">
        <el-card v-for="comp in filteredComponents" :key="comp.id" shadow="hover" class="component-card">
          <div class="card-header">
            <el-icon :size="24" :class="'type-' + comp.componentType">
              <Upload v-if="comp.componentType === 'source'" />
              <Operation v-else-if="comp.componentType === 'transform'" />
              <Download v-else />
            </el-icon>
            <div class="card-info">
              <span class="card-name">{{ comp.name }}</span>
              <div class="card-tags">
                <el-tag size="small" :type="getTypeColor(comp.componentType)">{{ comp.componentType }}</el-tag>
                <el-tag size="small" type="info">{{ comp.vectorType }}</el-tag>
                <el-tag v-if="comp.isTemplate" size="small" type="warning">内置</el-tag>
                <el-tag v-if="comp.componentType === 'sink' && comp.queryable" size="small" type="success">
                  <el-icon style="margin-right: 2px"><Search /></el-icon>可查询
                </el-tag>
              </div>
            </div>
          </div>
          <p class="card-desc">{{ comp.description || '无描述' }}</p>
          <!-- Sink 组件显示表名信息 -->
          <div v-if="comp.componentType === 'sink'" class="card-datasource-info">
            <span class="info-label">表名:</span>
            <span class="info-value">{{ getTableName(comp.configYaml) || '-' }}</span>
          </div>
          <pre class="card-yaml">{{ getPreview(comp.configYaml) }}</pre>
          <div class="card-actions">
            <el-button size="small" type="primary" @click="openDialog(comp)">
              <el-icon><Edit /></el-icon>编辑
            </el-button>
            <el-button size="small" type="danger" @click="deleteComponent(comp)" :disabled="comp.isTemplate">
              <el-icon><Delete /></el-icon>删除
            </el-button>
          </div>
          <!-- Sink 组件的数据源操作 -->
          <div v-if="comp.componentType === 'sink'" class="card-datasource-actions">
            <el-switch
              v-model="comp.queryable"
              size="small"
              active-text="可查询"
              inactive-text=""
              @change="(val: boolean) => toggleQueryable(comp, val)"
            />
            <div class="datasource-btns">
              <el-button 
                size="small" 
                type="info" 
                text
                @click="openDatasourceManage(comp)"
              >
                <el-icon><Setting /></el-icon>管理
              </el-button>
              <el-button 
                v-if="comp.queryable" 
                size="small" 
                type="success" 
                text
                @click="goToLogSearch(comp)"
              >
                <el-icon><Search /></el-icon>查询日志
              </el-button>
            </div>
          </div>
        </el-card>
      </div>

      <!-- 新建/编辑对话框 -->
      <el-dialog v-model="showDialog" :title="editingId ? '编辑组件' : '新建组件'" width="750px" destroy-on-close>
        <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
          <el-form-item label="组件名称" prop="name">
            <el-input v-model="form.name" placeholder="如：生产环境Kafka源" />
          </el-form-item>
          <el-form-item label="组件类型" prop="componentType">
            <el-select v-model="form.componentType" placeholder="选择类型" @change="onComponentTypeChange">
              <el-option label="Source (输入)" value="source" />
              <el-option label="Transform (处理)" value="transform" />
              <el-option label="Sink (输出)" value="sink" />
            </el-select>
          </el-form-item>
          <el-form-item label="Vector类型" prop="vectorType">
            <el-select v-model="form.vectorType" filterable placeholder="选择类型" @change="onVectorTypeChange">
              <el-option-group v-if="form.componentType === 'source'" label="Sources">
                <el-option v-for="t in sourceTypes" :key="t.value" :label="t.label" :value="t.value" />
              </el-option-group>
              <el-option-group v-else-if="form.componentType === 'transform'" label="Transforms">
                <el-option v-for="t in transformTypes" :key="t.value" :label="t.label" :value="t.value" />
              </el-option-group>
              <el-option-group v-else-if="form.componentType === 'sink'" label="Sinks">
                <el-option v-for="t in sinkTypes" :key="t.value" :label="t.label" :value="t.value" />
              </el-option-group>
            </el-select>
          </el-form-item>
          <el-form-item label="描述">
            <el-input v-model="form.description" type="textarea" :rows="2" placeholder="组件用途说明" />
          </el-form-item>
          <el-form-item label="配置方式">
            <el-radio-group v-model="configMode" @change="onConfigModeChange">
              <el-radio-button value="visual">可视化配置</el-radio-button>
              <el-radio-button value="yaml">YAML 编辑</el-radio-button>
            </el-radio-group>
          </el-form-item>

          <!-- 可视化配置 -->
          <template v-if="configMode === 'visual'">
            <!-- Source 可视化配置 -->
            <template v-if="form.componentType === 'source'">
              <template v-if="form.vectorType === 'file'">
                <el-form-item label="文件路径">
                  <el-input v-model="visualConfig.include" placeholder="/var/log/**/*.log" />
                  <div class="field-hint">支持 glob 模式，如 /var/log/*.log</div>
                </el-form-item>
                <el-form-item label="排除路径">
                  <el-input v-model="visualConfig.exclude" placeholder="/var/log/debug/**" />
                </el-form-item>
                <el-form-item label="读取位置">
                  <el-select v-model="visualConfig.read_from">
                    <el-option label="从头读取 (beginning)" value="beginning" />
                    <el-option label="从末尾读取 (end)" value="end" />
                  </el-select>
                </el-form-item>
              </template>
              <template v-else-if="form.vectorType === 'kafka'">
                <el-form-item label="Bootstrap">
                  <el-input v-model="visualConfig.bootstrap_servers" placeholder="localhost:9092" />
                </el-form-item>
                <el-form-item label="Topic">
                  <el-input v-model="visualConfig.topics" placeholder="logs" />
                </el-form-item>
                <el-form-item label="Group ID">
                  <el-input v-model="visualConfig.group_id" placeholder="vector-consumer" />
                </el-form-item>
              </template>
              <template v-else-if="form.vectorType === 'http_server'">
                <el-form-item label="监听地址">
                  <el-input v-model="visualConfig.address" placeholder="0.0.0.0:8080" />
                </el-form-item>
                <el-form-item label="路径">
                  <el-input v-model="visualConfig.path" placeholder="/" />
                </el-form-item>
              </template>
              <template v-else-if="form.vectorType === 'syslog'">
                <el-form-item label="协议">
                  <el-select v-model="visualConfig.syslog_mode" placeholder="选择协议">
                    <el-option label="TCP" value="tcp" />
                    <el-option label="UDP" value="udp" />
                  </el-select>
                </el-form-item>
                <el-form-item label="监听地址">
                  <el-input v-model="visualConfig.syslog_address" placeholder="0.0.0.0:514" />
                </el-form-item>
                <el-form-item label="接收地址">
                  <el-select v-model="visualConfig.syslog_receive_addresses" multiple filterable allow-create placeholder="可选，输入后回车添加">
                  </el-select>
                  <div class="field-hint">可选配置，限制接收的源地址</div>
                </el-form-item>
              </template>
              <template v-else-if="form.vectorType === 'socket'">
                <el-form-item label="协议">
                  <el-select v-model="visualConfig.syslog_mode" placeholder="选择协议">
                    <el-option label="TCP" value="tcp" />
                    <el-option label="UDP" value="udp" />
                  </el-select>
                </el-form-item>
                <el-form-item label="监听地址">
                  <el-input v-model="visualConfig.syslog_address" placeholder="0.0.0.0:514" />
                </el-form-item>
                <el-form-item label="接收地址">
                  <el-select v-model="visualConfig.syslog_receive_addresses" multiple filterable allow-create placeholder="可选，输入后回车添加">
                  </el-select>
                  <div class="field-hint">可选配置，限制接收的源地址</div>
                </el-form-item>
              </template>
              <template v-else-if="form.vectorType === 'demo_logs'">
                <el-form-item label="日志格式">
                  <el-select v-model="visualConfig.demo_format" placeholder="选择日志格式">
                    <el-option label="Syslog (RFC 5424)" value="syslog" />
                    <el-option label="Apache Common" value="apache_common" />
                    <el-option label="Apache Error" value="apache_error" />
                    <el-option label="JSON" value="json" />
                    <el-option label="Shuffle (随机混合)" value="shuffle" />
                  </el-select>
                  <div class="field-hint">选择生成的测试日志格式</div>
                </el-form-item>
                <el-form-item label="生成间隔">
                  <el-input v-model="visualConfig.demo_interval" placeholder="1.0">
                    <template #append>秒</template>
                  </el-input>
                  <div class="field-hint">每条日志的生成间隔，默认 1 秒</div>
                </el-form-item>
                <el-form-item label="日志数量">
                  <el-input v-model="visualConfig.demo_count" placeholder="无限制" type="number">
                    <template #append>条</template>
                  </el-input>
                  <div class="field-hint">可选，不填则持续生成</div>
                </el-form-item>
              </template>
              <template v-else>
                <el-alert type="info" :closable="false">该类型暂不支持可视化配置，请切换到 YAML 编辑模式</el-alert>
              </template>
            </template>

            <!-- Transform 可视化配置 -->
            <template v-if="form.componentType === 'transform'">
              <template v-if="form.vectorType === 'remap'">
                <!-- 日志解析预览区域 -->
                <el-divider content-position="left">日志解析预览</el-divider>
                <el-form-item label="日志样本">
                  <el-input 
                    v-model="parsePreview.logSample" 
                    type="textarea" 
                    :rows="3" 
                    placeholder="粘贴一条日志样本，用于测试解析效果"
                  />
                </el-form-item>
                <el-form-item label="解析方式">
                  <el-select v-model="visualConfig.parse_method" placeholder="选择解析方式">
                    <el-option label="JSON 解析" value="parse_json" />
                    <el-option label="Syslog 解析" value="parse_syslog" />
                    <el-option label="正则解析" value="parse_regex" />
                    <el-option label="Key-Value 解析" value="parse_key_value" />
                    <el-option label="Grok 解析" value="parse_grok" />
                    <el-option label="自定义 VRL" value="custom" />
                  </el-select>
                </el-form-item>
                <template v-if="visualConfig.parse_method === 'parse_regex'">
                  <el-form-item label="正则表达式">
                    <el-input v-model="visualConfig.regex_pattern" placeholder="^(?P<timestamp>\S+) (?P<level>\S+) (?P<message>.*)$" />
                    <div class="field-hint">使用命名捕获组 (?P&lt;name&gt;...) 提取字段</div>
                  </el-form-item>
                </template>
                <template v-if="visualConfig.parse_method === 'parse_grok'">
                  <el-form-item label="Grok 模式">
                    <el-input v-model="visualConfig.grok_pattern" placeholder="SYSLOGBASE2 %{GREEDYDATA:message}" />
                    <div class="field-hint">支持标准 Grok 模式，如 SYSLOGBASE2, COMMONAPACHELOG 等</div>
                  </el-form-item>
                </template>
                <template v-if="visualConfig.parse_method === 'custom'">
                  <el-form-item label="VRL 脚本">
                    <el-input v-model="visualConfig.vrl_source" type="textarea" :rows="4" placeholder=". = parse_json!(.message)" />
                  </el-form-item>
                </template>
                <el-form-item>
                  <el-button type="primary" @click="testParsing" :loading="parsePreview.loading">
                    <el-icon><Search /></el-icon>测试解析
                  </el-button>
                </el-form-item>
                
                <!-- 解析结果展示 -->
                <template v-if="parsePreview.error">
                  <el-alert type="error" :closable="false" show-icon>
                    <template #title>解析失败</template>
                    {{ parsePreview.error }}
                  </el-alert>
                </template>
                <template v-else-if="parsePreview.fields.length > 0">
                  <el-alert type="success" :closable="false" show-icon style="margin-bottom: 12px">
                    解析成功，共提取 {{ parsePreview.fields.length }} 个字段
                    <template v-if="parsePreview.fields.some(f => isSyslogField(f.name))">
                      （含 syslog 元数据）
                    </template>
                  </el-alert>
                  <el-table :data="parsePreview.fields" border size="small" class="parse-result-table" :row-class-name="getRowClassName">
                    <el-table-column label="字段名" width="200">
                      <template #default="{ row }">
                        <el-input 
                          v-if="row.editing" 
                          v-model="row.newName" 
                          size="small"
                          @blur="finishRename(row)"
                          @keyup.enter="finishRename(row)"
                        />
                        <span v-else>
                          <el-tag v-if="isSyslogField(row.name)" size="small" type="info" style="margin-right: 4px">syslog</el-tag>
                          {{ row.newName || row.name }}
                        </span>
                      </template>
                    </el-table-column>
                    <el-table-column prop="value" label="值" show-overflow-tooltip />
                    <el-table-column prop="type" label="类型" width="100">
                      <template #default="{ row }">
                        <el-tag size="small" :type="getFieldTypeColor(row.type)">{{ row.type }}</el-tag>
                      </template>
                    </el-table-column>
                    <el-table-column label="操作" width="180" fixed="right">
                      <template #default="{ row }">
                        <el-button 
                          size="small" 
                          text 
                          type="primary" 
                          @click="startRename(row)"
                          :disabled="row.deleted"
                        >
                          <el-icon><Edit /></el-icon>重命名
                        </el-button>
                        <el-button 
                          size="small" 
                          text 
                          :type="row.deleted ? 'success' : 'danger'" 
                          @click="toggleFieldDelete(row)"
                        >
                          <el-icon><RefreshRight v-if="row.deleted" /><Delete v-else /></el-icon>
                          {{ row.deleted ? '恢复' : '删除' }}
                        </el-button>
                      </template>
                    </el-table-column>
                  </el-table>
                  <div class="parse-actions">
                    <el-button type="success" @click="applyParsedFields">
                      <el-icon><Check /></el-icon>应用字段配置
                    </el-button>
                    <span class="field-hint">将根据字段配置自动生成 VRL 代码</span>
                  </div>
                </template>
                
                <el-divider content-position="left">基础配置</el-divider>
                <!-- 增强选项 -->
                <el-divider content-position="left">增强选项</el-divider>
                <el-form-item label="生成 UUID">
                  <el-switch v-model="visualConfig.generate_uuid" />
                  <span class="field-hint" style="margin-left: 12px">为每条日志生成唯一 ID (.id = uuid_v7())</span>
                </el-form-item>
                <el-form-item label="保留原始消息">
                  <el-switch v-model="visualConfig.keep_raw" />
                  <span class="field-hint" style="margin-left: 12px">保存原始消息到 .raw 字段</span>
                </el-form-item>
                <el-form-item label="提取源 IP">
                  <el-switch v-model="visualConfig.extract_source_ip" />
                  <span class="field-hint" style="margin-left: 12px">从 .host 提取到 .source_ip</span>
                </el-form-item>
                <el-form-item label="procid 转整数">
                  <el-switch v-model="visualConfig.convert_procid" />
                  <span class="field-hint" style="margin-left: 12px">将 procid 字段从字符串转为整数</span>
                </el-form-item>
                <el-divider content-position="left">字段操作</el-divider>
                <el-form-item label="添加字段">
                  <div v-for="(field, idx) in visualConfig.add_fields" :key="idx" class="field-row">
                    <el-input v-model="field.key" placeholder="字段名" style="width: 150px" />
                    <el-input v-model="field.value" placeholder="值" style="flex: 1" />
                    <el-button type="danger" text @click="visualConfig.add_fields.splice(idx, 1)">
                      <el-icon><Delete /></el-icon>
                    </el-button>
                  </div>
                  <el-button size="small" @click="visualConfig.add_fields.push({ key: '', value: '' })">
                    <el-icon><Plus /></el-icon> 添加字段
                  </el-button>
                </el-form-item>
                <el-form-item label="删除字段">
                  <el-select v-model="visualConfig.remove_fields" multiple filterable allow-create placeholder="输入要删除的字段名">
                  </el-select>
                </el-form-item>
              </template>
              <template v-else-if="form.vectorType === 'filter'">
                <el-form-item label="过滤条件">
                  <el-select v-model="visualConfig.filter_type" placeholder="选择过滤方式">
                    <el-option label="按日志级别" value="level" />
                    <el-option label="按字段值" value="field" />
                    <el-option label="自定义条件" value="custom" />
                  </el-select>
                </el-form-item>
                <template v-if="visualConfig.filter_type === 'level'">
                  <el-form-item label="保留级别">
                    <el-checkbox-group v-model="visualConfig.levels">
                      <el-checkbox label="error">Error</el-checkbox>
                      <el-checkbox label="warn">Warn</el-checkbox>
                      <el-checkbox label="info">Info</el-checkbox>
                      <el-checkbox label="debug">Debug</el-checkbox>
                    </el-checkbox-group>
                  </el-form-item>
                </template>
                <template v-if="visualConfig.filter_type === 'field'">
                  <el-form-item label="字段名">
                    <el-input v-model="visualConfig.field_name" placeholder="如 service" />
                  </el-form-item>
                  <el-form-item label="匹配值">
                    <el-input v-model="visualConfig.field_value" placeholder="如 api-gateway" />
                  </el-form-item>
                </template>
                <template v-if="visualConfig.filter_type === 'custom'">
                  <el-form-item label="VRL 条件">
                    <el-input v-model="visualConfig.condition" type="textarea" :rows="3" placeholder='.level == "error"' />
                  </el-form-item>
                </template>
              </template>
              <template v-else>
                <el-alert type="info" :closable="false">该类型暂不支持可视化配置，请切换到 YAML 编辑模式</el-alert>
              </template>
            </template>

            <!-- Sink 可视化配置 -->
            <template v-if="form.componentType === 'sink'">
              <!-- 数据源选择（所有 Sink 类型通用） -->
              <el-divider content-position="left">数据源配置</el-divider>
              <el-form-item label="选择数据源">
                <el-select
                  v-model="form.datasourceId"
                  placeholder="选择已配置的数据源"
                  clearable
                  filterable
                  @change="onDatasourceChange"
                >
                  <el-option
                    v-for="ds in availableDatasources"
                    :key="ds.id"
                    :label="`${ds.name} (${ds.type})`"
                    :value="ds.id"
                  >
                    <div style="display: flex; justify-content: space-between; align-items: center;">
                      <span>{{ ds.name }}</span>
                      <el-tag size="small" :type="getDatasourceTypeColor(ds.type)">{{ ds.type }}</el-tag>
                    </div>
                  </el-option>
                </el-select>
                <div class="field-hint">
                  选择数据源后，连接信息将自动填充。如需配置新数据源，请前往"数据源管理"页面。
                </div>
              </el-form-item>

              <el-divider content-position="left">表配置</el-divider>

              <template v-if="form.vectorType === 'elasticsearch'">
                <el-form-item label="Endpoints">
                  <el-input v-model="visualConfig.endpoints" placeholder="http://localhost:9200" />
                </el-form-item>
                <el-form-item label="索引名称">
                  <el-input v-model="visualConfig.index" placeholder="logs-%Y-%m-%d" />
                </el-form-item>
                <el-form-item label="用户名">
                  <el-input v-model="visualConfig.auth_user" placeholder="elastic" />
                </el-form-item>
                <el-form-item label="密码">
                  <el-input v-model="visualConfig.auth_password" type="password" show-password />
                </el-form-item>
              </template>
              <template v-else-if="form.vectorType === 'clickhouse'">
                <!-- 基础配置 -->
                <el-form-item label="Endpoint">
                  <el-input v-model="visualConfig.endpoint" placeholder="http://localhost:8123" />
                </el-form-item>
                <el-form-item label="数据库">
                  <el-input v-model="visualConfig.database" placeholder="default" />
                </el-form-item>
                <el-form-item label="表名">
                  <el-input v-model="visualConfig.table" placeholder="logs" />
                  <div class="field-hint">数据将写入此表，请确保表已创建或配置自动创建</div>
                </el-form-item>
                
                <!-- 批处理配置 -->
                <el-divider content-position="left">批处理</el-divider>
                <el-form-item label="最大字节数">
                  <el-input v-model="visualConfig.clickhouse_batch_max_bytes" placeholder="10000000">
                    <template #append>bytes</template>
                  </el-input>
                </el-form-item>
                <el-form-item label="超时时间">
                  <el-input v-model="visualConfig.clickhouse_batch_timeout" placeholder="10">
                    <template #append>秒</template>
                  </el-input>
                </el-form-item>
                
                <!-- 缓冲配置 -->
                <el-divider content-position="left">缓冲</el-divider>
                <el-form-item label="缓冲类型">
                  <el-select v-model="visualConfig.clickhouse_buffer_type">
                    <el-option value="memory" label="内存" />
                    <el-option value="disk" label="磁盘" />
                  </el-select>
                </el-form-item>
                <el-form-item label="最大事件数">
                  <el-input v-model="visualConfig.clickhouse_buffer_max_events" placeholder="500000" />
                </el-form-item>
                
                <!-- 认证配置 -->
                <el-divider content-position="left">认证</el-divider>
                <el-form-item label="用户名">
                  <el-input v-model="visualConfig.clickhouse_user" placeholder="可选" />
                </el-form-item>
                <el-form-item label="密码">
                  <el-input v-model="visualConfig.clickhouse_password" type="password" show-password placeholder="可选" />
                </el-form-item>
              </template>
              <template v-else-if="form.vectorType === 'kafka'">
                <el-form-item label="Bootstrap">
                  <el-input v-model="visualConfig.bootstrap_servers" placeholder="localhost:9092" />
                </el-form-item>
                <el-form-item label="Topic">
                  <el-input v-model="visualConfig.topic" placeholder="logs-output" />
                </el-form-item>
              </template>
              <template v-else-if="form.vectorType === 'console'">
                <el-form-item label="输出目标">
                  <el-select v-model="visualConfig.target">
                    <el-option label="stdout" value="stdout" />
                    <el-option label="stderr" value="stderr" />
                  </el-select>
                </el-form-item>
                <el-form-item label="编码格式">
                  <el-select v-model="visualConfig.encoding">
                    <el-option label="JSON" value="json" />
                    <el-option label="Text" value="text" />
                  </el-select>
                </el-form-item>
              </template>
              <template v-else>
                <el-alert type="info" :closable="false">该类型暂不支持可视化配置，请切换到 YAML 编辑模式</el-alert>
              </template>
            </template>
          </template>

          <!-- YAML 编辑模式 -->
          <el-form-item v-if="configMode === 'yaml'" label="配置内容" prop="configYaml">
            <div ref="editorRef" class="codemirror-wrapper"></div>
            <div class="yaml-actions">
              <el-button size="small" @click="formatYaml">
                <el-icon><Tools /></el-icon>格式化
              </el-button>
              <span v-if="yamlError" class="yaml-error-text">
                <el-icon><WarningFilled /></el-icon>{{ yamlError }}
              </span>
              <span v-else-if="yamlValid && form.configYaml" class="yaml-success-text">
                <el-icon><SuccessFilled /></el-icon>YAML 格式正确
              </span>
            </div>
          </el-form-item>
        </el-form>
        <template #footer>
          <el-button @click="showDialog = false">取消</el-button>
          <el-button type="primary" :loading="submitting" @click="submitForm">
            {{ editingId ? '更新' : '创建' }}
          </el-button>
        </template>
      </el-dialog>

      <!-- 数据源管理对话框 -->
      <DatasourceManageDialog
        v-model="showDatasourceManage"
        :component="currentDatasourceComponent"
      />

      <!-- 智能向导 -->
      <SmartWizard v-model="showSmartWizard" @created="handleWizardCreated" />
    </div>
  </AppLayout>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted, watch, nextTick, computed } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { Plus, Upload, Operation, Download, Edit, Delete, Tools, WarningFilled, SuccessFilled, Search, Check, RefreshRight, Setting, Grid, MagicStick } from '@element-plus/icons-vue'
import * as yaml from 'js-yaml'
import AppLayout from '@/components/layout/AppLayout.vue'
import DatasourceManageDialog from './components/DatasourceManageDialog.vue'
import SmartWizard from './SmartWizard.vue'
import { configComponentApi, vrlApi, type ConfigComponent, type ParsedField } from '@/api/vector'
import { listActiveDatasources, type Datasource } from '@/api/datasource'
import { EditorView, basicSetup } from 'codemirror'
import { EditorState } from '@codemirror/state'
import { yaml as yamlLang } from '@codemirror/lang-yaml'
import { oneDark } from '@codemirror/theme-one-dark'

const router = useRouter()
const loading = ref(false)
const submitting = ref(false)
const showDialog = ref(false)
const editingId = ref<string | null>(null)
const components = ref<ConfigComponent[]>([])
const formRef = ref<FormInstance>()
const editorRef = ref<HTMLElement>()
const configMode = ref<'visual' | 'yaml'>('visual')

const yamlError = ref('')
const yamlValid = ref(true)
let editorView: EditorView | null = null

// 智能向导状态
const showSmartWizard = ref(false)

// 数据源管理对话框状态
const showDatasourceManage = ref(false)
const currentDatasourceComponent = ref<ConfigComponent | null>(null)

// 数据源列表
const availableDatasources = ref<Datasource[]>([])

// 日志解析预览相关
interface ParseFieldItem extends ParsedField {
  editing?: boolean
  newName?: string
  deleted?: boolean
}

interface WizardCreatedPayload {
  tableName: string
  remapComponentId?: string
  sinkComponentId?: string
}

const parsePreview = reactive({
  logSample: '',
  loading: false,
  error: '',
  fields: [] as ParseFieldItem[]
})

const filters = reactive({ keyword: '', componentTypes: [] as string[] })

// 计算属性：各类型组件数量
const totalCount = computed(() => components.value.length)
const sourceCount = computed(() => components.value.filter(c => c.componentType === 'source').length)
const transformCount = computed(() => components.value.filter(c => c.componentType === 'transform').length)
const sinkCount = computed(() => components.value.filter(c => c.componentType === 'sink').length)

// 计算属性：筛选后的组件列表
const filteredComponents = computed(() => {
  let result = components.value
  
  // 按类型筛选（多选）
  if (filters.componentTypes.length > 0) {
    result = result.filter(c => filters.componentTypes.includes(c.componentType))
  }
  
  // 按关键词筛选
  if (filters.keyword) {
    const keyword = filters.keyword.toLowerCase()
    result = result.filter(c => 
      c.name.toLowerCase().includes(keyword) ||
      c.vectorType.toLowerCase().includes(keyword) ||
      (c.description && c.description.toLowerCase().includes(keyword))
    )
  }
  
  return result
})

// 切换类型选择（多选）
const toggleType = (type: string) => {
  const index = filters.componentTypes.indexOf(type)
  if (index === -1) {
    filters.componentTypes.push(type)
  } else {
    filters.componentTypes.splice(index, 1)
  }
}

// 选择全部（清空筛选）
const selectAll = () => {
  filters.componentTypes = []
}

const form = reactive({
  name: '',
  componentType: '',
  vectorType: '',
  description: '',
  configYaml: '',
  visualData: '',
  datasourceId: '' // 关联的数据源ID
})

const visualConfig = reactive<Record<string, any>>({
  // Source - file
  include: '', exclude: '', read_from: 'beginning',
  // Source - kafka
  bootstrap_servers: '', topics: '', group_id: '',
  // Source - http_server
  address: '0.0.0.0:8080', path: '/',
  // Source - syslog
  syslog_mode: 'tcp', syslog_address: '0.0.0.0:514', syslog_receive_addresses: [] as string[],
  // Source - demo_logs
  demo_format: 'syslog', demo_interval: '1.0', demo_count: '',
  // Transform - remap
  parse_method: 'parse_json', regex_pattern: '', grok_pattern: '', vrl_source: '',
  add_fields: [] as { key: string; value: string }[],
  remove_fields: [] as string[],
  generate_uuid: false, keep_raw: false, extract_source_ip: false, convert_procid: false,
  // Transform - remap 日志解析预览数据
  log_sample: '',
  parsed_fields: [] as { name: string; newName: string; deleted: boolean; type: string }[],
  // Transform - filter
  filter_type: 'level', levels: ['error', 'warn'], field_name: '', field_value: '', condition: '',
  // Sink - elasticsearch
  endpoints: '', index: 'logs-%Y-%m-%d', auth_user: '', auth_password: '',
  // Sink - clickhouse
  endpoint: '', database: 'default', table: '', clickhouse_user: '', clickhouse_password: '',
  clickhouse_format: 'json_each_row', clickhouse_compression: 'gzip', clickhouse_skip_unknown: true,
  clickhouse_timestamp_format: 'unix',
  clickhouse_batch_max_bytes: '10000000', clickhouse_batch_timeout: '10',
  clickhouse_buffer_type: 'memory', clickhouse_buffer_max_events: '500000',
  // Sink - kafka (output)
  topic: '',
  // Sink - console
  target: 'stdout', encoding: 'json'
})

const rules: FormRules = {
  name: [{ required: true, message: '请输入组件名称', trigger: 'blur' }],
  componentType: [{ required: true, message: '请选择组件类型', trigger: 'change' }],
  vectorType: [{ required: true, message: '请选择Vector类型', trigger: 'change' }]
}

const sourceTypes = [
  { value: 'file', label: 'File (文件)' },
  { value: 'kafka', label: 'Kafka' },
  { value: 'http_server', label: 'HTTP Server' },
  { value: 'syslog', label: 'Syslog' },
  { value: 'socket', label: 'Socket' },
  { value: 'docker_logs', label: 'Docker Logs' },
  { value: 'demo_logs', label: 'Demo Logs (测试日志)' }
]
const transformTypes = [
  { value: 'remap', label: 'Remap (数据转换)' },
  { value: 'filter', label: 'Filter (过滤)' },
  { value: 'route', label: 'Route (路由)' },
  { value: 'sample', label: 'Sample (采样)' },
  { value: 'dedupe', label: 'Dedupe (去重)' }
]
const sinkTypes = [
  { value: 'elasticsearch', label: 'Elasticsearch' },
  { value: 'clickhouse', label: 'ClickHouse' },
  { value: 'kafka', label: 'Kafka' },
  { value: 'console', label: 'Console (控制台)' },
  { value: 'file', label: 'File (文件)' },
  { value: 'http', label: 'HTTP' },
  { value: 'loki', label: 'Loki' }
]

const getTypeColor = (type: string) => {
  const map: Record<string, string> = { source: 'success', transform: '', sink: 'warning' }
  return map[type] || 'info'
}

const getPreview = (yamlStr: string) => {
  const lines = yamlStr?.split('\n') || []
  return lines.length <= 5 ? yamlStr : lines.slice(0, 5).join('\n') + '\n...'
}

// 从 configYaml 中解析表名
const getTableName = (configYaml: string): string => {
  if (!configYaml) return ''
  try {
    const parsed = yaml.load(configYaml) as Record<string, any>
    // ClickHouse: table 字段
    if (parsed?.table) return parsed.table
    // Elasticsearch: index 字段
    if (parsed?.index) return parsed.index
    // Kafka: topic 字段
    if (parsed?.topic) return parsed.topic
    // PostgreSQL/MySQL: table 字段
    return parsed?.table || ''
  } catch {
    return ''
  }
}

// 切换组件的可查询状态
const toggleQueryable = async (comp: ConfigComponent, queryable: boolean) => {
  try {
    await configComponentApi.updateQueryable(comp.id, queryable)
    ElMessage.success(queryable ? '已设为可查询数据源' : '已取消可查询状态')
  } catch (error: any) {
    // 恢复原状态
    comp.queryable = !queryable
    ElMessage.error(error.message || '操作失败')
  }
}

// 跳转到日志搜索页面
const goToLogSearch = (comp: ConfigComponent) => {
  router.push({
    path: '/log-search',
    query: { datasource: comp.id }
  })
}

// 打开数据源管理对话框
const openDatasourceManage = (comp: ConfigComponent) => {
  currentDatasourceComponent.value = comp
  showDatasourceManage.value = true
}

// 从后端获取生成的 YAML 配置
const fetchGeneratedYaml = async (): Promise<string> => {
  if (!form.componentType || !form.vectorType) {
    return ''
  }
  
  // 同步解析预览数据到 visualConfig
  visualConfig.log_sample = parsePreview.logSample
  visualConfig.parsed_fields = parsePreview.fields.map(f => ({
    name: f.name,
    newName: f.newName,
    deleted: f.deleted,
    type: f.type,
    value: f.value
  }))
  
  try {
    const res = await configComponentApi.generateYaml(
      form.componentType,
      form.vectorType,
      JSON.stringify(visualConfig)
    )
    return res?.yaml || ''
  } catch (e: any) {
    console.error('生成 YAML 失败:', e)
    ElMessage.error('生成配置失败: ' + (e.message || '未知错误'))
    return ''
  }
}

const initEditor = () => {
  if (!editorRef.value || editorView) return
  const state = EditorState.create({
    doc: form.configYaml,
    extensions: [
      basicSetup, yamlLang(), oneDark,
      EditorView.updateListener.of((update) => {
        if (update.docChanged) {
          form.configYaml = update.state.doc.toString()
          validateYamlSyntax()
        }
      }),
      EditorView.theme({ '&': { height: '300px' }, '.cm-scroller': { overflow: 'auto' } })
    ]
  })
  editorView = new EditorView({ state, parent: editorRef.value })
}

const destroyEditor = () => {
  editorView?.destroy()
  editorView = null
}

const validateYamlSyntax = () => {
  const content = form.configYaml.trim()
  if (!content) { yamlError.value = ''; yamlValid.value = true; return }
  try {
    yaml.load(content)
    yamlError.value = ''; yamlValid.value = true
  } catch (e: any) {
    yamlValid.value = false
    const match = e.message?.match(/at line (\d+)/)
    yamlError.value = match ? `第 ${match[1]} 行: ${e.reason || e.message}` : e.reason || e.message
  }
}

const formatYaml = () => {
  const content = form.configYaml.trim()
  if (!content) return
  try {
    const parsed = yaml.load(content)
    const formatted = yaml.dump(parsed, { indent: 2, lineWidth: -1, noRefs: true })
    form.configYaml = formatted
    editorView?.dispatch({ changes: { from: 0, to: editorView.state.doc.length, insert: formatted } })
    yamlError.value = ''; yamlValid.value = true
    ElMessage.success('格式化成功')
  } catch (e: any) {
    yamlValid.value = false
    const match = e.message?.match(/at line (\d+)/)
    yamlError.value = match ? `第 ${match[1]} 行: ${e.reason || e.message}` : e.reason || e.message
  }
}

const onComponentTypeChange = () => {
  form.vectorType = ''
  resetVisualConfig()
}

const onVectorTypeChange = () => {
  resetVisualConfig()
}

const onConfigModeChange = async () => {
  if (configMode.value === 'yaml') {
    // 从可视化切换到 YAML，从后端获取生成的配置
    form.configYaml = await fetchGeneratedYaml()
    await nextTick()
    initEditor()
  } else {
    destroyEditor()
  }
}

const resetVisualConfig = () => {
  Object.assign(visualConfig, {
    include: '', exclude: '', read_from: 'beginning',
    bootstrap_servers: '', topics: '', group_id: '',
    address: '0.0.0.0:8080', path: '/',
    syslog_mode: 'tcp', syslog_address: '0.0.0.0:514', syslog_receive_addresses: [],
    parse_method: 'parse_json', regex_pattern: '', grok_pattern: '', vrl_source: '',
    add_fields: [], remove_fields: [],
    generate_uuid: false, keep_raw: false, extract_source_ip: false, convert_procid: false,
    log_sample: '', parsed_fields: [],
    filter_type: 'level', levels: ['error', 'warn'], field_name: '', field_value: '', condition: '',
    endpoints: '', index: 'logs-%Y-%m-%d', auth_user: '', auth_password: '',
    endpoint: '', database: 'default', table: '', clickhouse_user: '', clickhouse_password: '',
    clickhouse_format: 'json_each_row', clickhouse_compression: 'gzip', clickhouse_skip_unknown: true,
    clickhouse_timestamp_format: 'unix',
    clickhouse_batch_max_bytes: '10000000', clickhouse_batch_timeout: '10',
    clickhouse_buffer_type: 'memory', clickhouse_buffer_max_events: '500000',
    topic: '', target: 'stdout', encoding: 'json'
  })
  // 重置解析预览
  resetParsePreview()
}

const normalizeParseMethodForVisual = (value?: string) => {
  const method = value || ''
  if (method === 'parse_kv') return 'parse_key_value'
  if (method === 'auto') return 'custom'
  if (['parse_json', 'parse_syslog', 'parse_regex', 'parse_key_value', 'parse_grok', 'custom'].includes(method)) {
    return method
  }
  return ''
}

const normalizeVisualData = (rawVisual: Record<string, any>) => {
  const normalized: Record<string, any> = { ...rawVisual }

  const aliasMap: Record<string, string> = {
    parseMethod: 'parse_method',
    regexPattern: 'regex_pattern',
    grokPattern: 'grok_pattern',
    customVrl: 'vrl_source',
    vrlScript: 'vrl_source',
    logSample: 'log_sample',
    parsedFields: 'parsed_fields'
  }

  Object.entries(aliasMap).forEach(([oldKey, newKey]) => {
    if (normalized[oldKey] !== undefined && normalized[newKey] === undefined) {
      normalized[newKey] = normalized[oldKey]
    }
  })

  const normalizedMethod = normalizeParseMethodForVisual(normalized.parse_method)
  if (normalizedMethod) {
    normalized.parse_method = normalizedMethod
  } else {
    delete normalized.parse_method
  }

  if (Array.isArray(normalized.parsed_fields)) {
    normalized.parsed_fields = normalized.parsed_fields.map((field: any) => ({
      ...field,
      newName: field.newName || field.new_name || field.name,
      deleted: Boolean(field.deleted)
    }))
  }

  return normalized
}

const extractRemapSourceFromYaml = (configYaml?: string) => {
  if (!configYaml) return ''

  try {
    const parsed = yaml.load(configYaml) as Record<string, any>
    if (typeof parsed?.source === 'string') {
      return parsed.source
    }
  } catch {
    // 下方使用文本兜底解析，避免一个历史 YAML 格式问题导致无法回显。
  }

  const sourceMatch = configYaml.match(/(?:^|\n)source:\s*\|\s*\n([\s\S]*)$/)
  if (!sourceMatch) return ''

  const sourceBody = sourceMatch[1] || ''
  return sourceBody
    .split('\n')
    .map(line => line.replace(/^ {2}/, ''))
    .join('\n')
    .trimEnd()
}

const inferVisualDataFromYaml = (comp: ConfigComponent) => {
  if (comp.componentType !== 'transform' || comp.vectorType !== 'remap') {
    return {}
  }

  const source = extractRemapSourceFromYaml(comp.configYaml)
  if (!source.trim()) return {}

  const inferred: Record<string, any> = {
    vrl_source: source
  }

  const regexMatch = source.match(/parse_regex!?\([\s\S]*?,\s*r'([^']*)'/)
  if (regexMatch?.[1]) {
    inferred.parse_method = 'parse_regex'
    inferred.regex_pattern = regexMatch[1]
    return inferred
  }

  const grokMatch = source.match(/parse_grok!?\([\s\S]*?,\s*"([^"]*)"/)
  if (grokMatch?.[1]) {
    inferred.parse_method = 'parse_grok'
    inferred.grok_pattern = grokMatch[1].replace(/^%\{/, '').replace(/\}$/, '')
    return inferred
  }

  const hasJson = source.includes('parse_json')
  const hasKeyValue = source.includes('parse_key_value')
  const hasSyslog = source.includes('parse_syslog')
  const methodCount = [hasJson, hasKeyValue, hasSyslog].filter(Boolean).length

  if (methodCount > 1) {
    inferred.parse_method = 'custom'
  } else if (hasJson) {
    inferred.parse_method = 'parse_json'
  } else if (hasKeyValue) {
    inferred.parse_method = 'parse_key_value'
  } else if (hasSyslog) {
    inferred.parse_method = 'parse_syslog'
  } else {
    inferred.parse_method = 'custom'
  }

  return inferred
}

const mergeVisualData = (savedVisual: Record<string, any>, inferredVisual: Record<string, any>) => {
  const saved = normalizeVisualData(savedVisual)
  const inferred = normalizeVisualData(inferredVisual)
  const merged = { ...inferred, ...saved }

  const inferredMethod = inferred.parse_method
  const savedMethod = saved.parse_method
  if ((!savedMethod || (savedMethod === 'parse_json' && inferredMethod && inferredMethod !== 'parse_json')) && inferredMethod) {
    merged.parse_method = inferredMethod
  }

  if (!merged.regex_pattern && inferred.regex_pattern) {
    merged.regex_pattern = inferred.regex_pattern
  }
  if (!merged.grok_pattern && inferred.grok_pattern) {
    merged.grok_pattern = inferred.grok_pattern
  }
  if (!merged.vrl_source && inferred.vrl_source) {
    merged.vrl_source = inferred.vrl_source
  }

  return merged
}

const restoreParsePreview = (restoredVisual: Record<string, any>) => {
  if (restoredVisual.log_sample) {
    parsePreview.logSample = restoredVisual.log_sample
  }
  if (Array.isArray(restoredVisual.parsed_fields) && restoredVisual.parsed_fields.length > 0) {
    parsePreview.fields = restoredVisual.parsed_fields.map((field: any) => ({
      ...field,
      newName: field.newName || field.new_name || field.name,
      deleted: Boolean(field.deleted),
      editing: false
    }))
  }
}

watch(showDialog, async (val) => {
  if (val && configMode.value === 'yaml') {
    await nextTick()
    initEditor()
  } else if (!val) {
    destroyEditor()
  }
})

watch(() => form.configYaml, (newVal) => {
  if (editorView && editorView.state.doc.toString() !== newVal) {
    editorView.dispatch({ changes: { from: 0, to: editorView.state.doc.length, insert: newVal } })
  }
})

const fetchList = async () => {
  loading.value = true
  let loaded = false
  try {
    const res = await configComponentApi.getList(filters.keyword)
    components.value = Array.isArray((res as any)?.data)
      ? (res as any).data
      : Array.isArray(res)
        ? res
        : []
    loaded = true
  } catch { ElMessage.error('加载组件列表失败') }
  finally { loading.value = false }
  return loaded
}

const handleWizardCreated = async (payload: WizardCreatedPayload) => {
  filters.keyword = ''
  filters.componentTypes = []
  const loaded = await fetchList()

  const createdIds = [payload.remapComponentId, payload.sinkComponentId].filter(Boolean)
  if (loaded && createdIds.length > 0) {
    ElMessage.success(`组件库已刷新，${payload.tableName} 的新组件已出现在列表顶部`)
  }
}

// 加载数据源列表
const loadDatasources = async () => {
  try {
    const res = await listActiveDatasources()
    availableDatasources.value = res.data || []
  } catch (error) {
    console.error('加载数据源列表失败:', error)
  }
}

// 数据源变化时的处理
const onDatasourceChange = (datasourceId: string) => {
  if (!datasourceId) {
    return
  }

  const datasource = availableDatasources.value.find(ds => ds.id === datasourceId)
  if (!datasource) {
    return
  }

  // 根据数据源类型自动填充连接信息
  if (form.vectorType === 'clickhouse' && datasource.type === 'clickhouse') {
    visualConfig.endpoint = `http://${datasource.host}:${datasource.port}`
    visualConfig.database = datasource.databaseName || 'default'
    visualConfig.clickhouse_user = datasource.username || ''
    visualConfig.clickhouse_password = datasource.password || ''
    ElMessage.success('已自动填充 ClickHouse 连接信息')
  } else if (form.vectorType === 'elasticsearch' && datasource.type === 'elasticsearch') {
    visualConfig.endpoints = `http://${datasource.host}:${datasource.port}`
    visualConfig.auth_user = datasource.username || ''
    visualConfig.auth_password = datasource.password || ''
    ElMessage.success('已自动填充 Elasticsearch 连接信息')
  }
}

// 获取数据源类型的标签颜色
const getDatasourceTypeColor = (type: string) => {
  const colors: Record<string, string> = {
    clickhouse: 'primary',
    postgresql: 'success',
    mysql: 'warning',
    elasticsearch: 'info',
    loki: 'danger'
  }
  return colors[type] || ''
}

/**
 * 打开智能向导
 */
const openSmartWizard = () => {
  showSmartWizard.value = true
}

const openDialog = (comp?: ConfigComponent) => {
  yamlError.value = ''; yamlValid.value = true
  resetVisualConfig()

  // 加载数据源列表
  loadDatasources()

  if (comp) {
    editingId.value = comp.id
    Object.assign(form, {
      name: comp.name, componentType: comp.componentType, vectorType: comp.vectorType,
      description: comp.description || '', configYaml: comp.configYaml, visualData: comp.visualData || '',
      datasourceId: comp.datasourceId || '' // 恢复数据源ID
    })
    // 从 visualData 恢复可视化配置；历史数据不完整时，再从 YAML 兜底推断。
    let savedVisual: Record<string, any> = {}
    if (comp.visualData) {
      try {
        savedVisual = JSON.parse(comp.visualData)
      } catch (e) {
        console.warn('解析 visualData 失败:', e)
      }
    }
    const restoredVisual = mergeVisualData(savedVisual, inferVisualDataFromYaml(comp))
    Object.assign(visualConfig, restoredVisual)
    restoreParsePreview(restoredVisual)
    configMode.value = 'visual'
  } else {
    editingId.value = null
    Object.assign(form, { name: '', componentType: '', vectorType: '', description: '', configYaml: '', visualData: '' })
    configMode.value = 'visual'
  }
  showDialog.value = true
}

const submitForm = async () => {
  await formRef.value?.validate(async (valid) => {
    if (!valid) return

    // 如果是可视化模式，从后端获取生成的 YAML 并保存 visualData
    if (configMode.value === 'visual') {
      // 验证 Sink 组件的表名必填
      if (form.componentType === 'sink') {
        const needsTableName = ['clickhouse', 'postgresql', 'mysql', 'elasticsearch']
        if (needsTableName.includes(form.vectorType)) {
          if (form.vectorType === 'clickhouse' && !visualConfig.table?.trim()) {
            ElMessage.error('请输入表名')
            return
          }
          if (form.vectorType === 'postgresql' && !visualConfig.table?.trim()) {
            ElMessage.error('请输入表名')
            return
          }
          if (form.vectorType === 'mysql' && !visualConfig.table?.trim()) {
            ElMessage.error('请输入表名')
            return
          }
          if (form.vectorType === 'elasticsearch' && !visualConfig.index?.trim()) {
            ElMessage.error('请输入索引名称')
            return
          }
        }
      }

      // 同步解析预览数据到 visualConfig
      visualConfig.log_sample = parsePreview.logSample
      visualConfig.parsed_fields = parsePreview.fields.map(f => ({
        name: f.name,
        newName: f.newName,
        deleted: f.deleted,
        type: f.type,
        value: f.value
      }))

      form.configYaml = await fetchGeneratedYaml()
      form.visualData = JSON.stringify(visualConfig)
    }
    
    if (!form.configYaml.trim()) {
      ElMessage.error('请配置组件内容')
      return
    }
    
    if (configMode.value === 'yaml' && !yamlValid.value) {
      ElMessage.error('请修正 YAML 格式错误')
      return
    }
    
    submitting.value = true
    try {
      if (editingId.value) {
        await configComponentApi.update(editingId.value, form)
        ElMessage.success('更新成功')
      } else {
        await configComponentApi.create(form)
        ElMessage.success('创建成功')
      }
      showDialog.value = false
      fetchList()
    } catch (e: any) { ElMessage.error(e.message || '操作失败') }
    finally { submitting.value = false }
  })
}

const deleteComponent = (comp: ConfigComponent) => {
  ElMessageBox.confirm(`确定删除组件"${comp.name}"吗？`, '确认删除', { type: 'warning' })
    .then(async () => {
      await configComponentApi.delete(comp.id)
      ElMessage.success('删除成功')
      fetchList()
    })
}

// ==================== 日志解析预览功能 ====================

const getFieldTypeColor = (type: string) => {
  const map: Record<string, string> = {
    string: 'info',
    integer: 'success',
    float: 'success',
    boolean: 'warning',
    array: 'danger',
    object: 'danger',
    null: ''
  }
  return map[type] || 'info'
}

// syslog 元数据字段
const syslogMetaFields = ['syslog_facility', 'syslog_severity', 'syslog_timestamp', 'syslog_hostname', 'syslog_appname', 'syslog_procid']

const isSyslogField = (name: string) => syslogMetaFields.includes(name)

const testParsing = async () => {
  if (!parsePreview.logSample.trim()) {
    ElMessage.warning('请输入日志样本')
    return
  }
  
  parsePreview.loading = true
  parsePreview.error = ''
  parsePreview.fields = []
  
  try {
    const res = await vrlApi.execute({
      logSample: parsePreview.logSample,
      parseMethod: visualConfig.parse_method,
      regexPattern: visualConfig.regex_pattern,
      grokPattern: visualConfig.grok_pattern,
      customVrl: visualConfig.vrl_source
    })
    
    const data = res
    if (data.success && data.fields) {
      parsePreview.fields = data.fields.map((f: ParsedField) => ({
        ...f,
        editing: false,
        newName: f.name,
        deleted: false
      }))
    } else {
      parsePreview.error = data.error || '解析失败'
    }
  } catch (e: any) {
    parsePreview.error = e.message || '请求失败'
  } finally {
    parsePreview.loading = false
  }
}

const startRename = (row: ParseFieldItem) => {
  row.editing = true
}

const finishRename = (row: ParseFieldItem) => {
  row.editing = false
  if (!row.newName?.trim()) {
    row.newName = row.name
  }
}

const toggleFieldDelete = (row: ParseFieldItem) => {
  row.deleted = !row.deleted
}

const getRowClassName = ({ row }: { row: ParseFieldItem }) => {
  if (row.deleted) return 'deleted-row'
  if (isSyslogField(row.name)) return 'syslog-row'
  return ''
}

const applyParsedFields = () => {
  // 根据解析结果和用户配置生成 VRL 代码
  const sourceLines: string[] = []
  
  // 1. 添加解析语句
  if (visualConfig.parse_method === 'parse_json') {
    sourceLines.push('parsed = parse_json!(.message)')
  } else if (visualConfig.parse_method === 'parse_syslog') {
    sourceLines.push('parsed, err = parse_syslog(.message)')
    sourceLines.push('if err != null {')
    sourceLines.push('  log("Syslog parse error: " + to_string(err), level: "warn")')
    sourceLines.push('}')
  } else if (visualConfig.parse_method === 'parse_regex' && visualConfig.regex_pattern) {
    sourceLines.push(`parsed = parse_regex!(.message, r'${visualConfig.regex_pattern}')`)
  } else if (visualConfig.parse_method === 'parse_key_value') {
    sourceLines.push('parsed = parse_key_value!(.message)')
  } else if (visualConfig.parse_method === 'parse_grok' && visualConfig.grok_pattern) {
    sourceLines.push(`parsed = parse_grok!(.message, "%{${visualConfig.grok_pattern}}")`)
  } else if (visualConfig.parse_method === 'custom' && visualConfig.vrl_source) {
    sourceLines.push(visualConfig.vrl_source)
    // 自定义模式直接使用用户脚本，不做字段映射
    visualConfig.vrl_source = sourceLines.join('\n')
    ElMessage.success('已应用自定义 VRL 脚本')
    return
  }
  
  // 2. 处理字段映射（重命名和删除）
  const activeFields = parsePreview.fields.filter(f => !f.deleted)
  const deletedFields = parsePreview.fields.filter(f => f.deleted)
  
  // 3. 生成字段赋值语句
  sourceLines.push('')
  sourceLines.push('# 提取字段')
  for (const field of activeFields) {
    const srcName = field.name
    const dstName = field.newName || field.name
    if (srcName === dstName) {
      sourceLines.push(`.${dstName} = parsed.${srcName}`)
    } else {
      sourceLines.push(`.${dstName} = parsed.${srcName}  # 重命名自 ${srcName}`)
    }
  }
  
  // 4. 如果有删除的字段，添加注释说明
  if (deletedFields.length > 0) {
    sourceLines.push('')
    sourceLines.push(`# 已忽略字段: ${deletedFields.map(f => f.name).join(', ')}`)
  }
  
  // 更新 VRL 脚本
  visualConfig.vrl_source = sourceLines.join('\n')
  visualConfig.parse_method = 'custom'
  
  ElMessage.success(`已生成 VRL 代码，包含 ${activeFields.length} 个字段`)
}

const resetParsePreview = () => {
  parsePreview.logSample = ''
  parsePreview.error = ''
  parsePreview.fields = []
}

onMounted(fetchList)
</script>

<style scoped lang="scss">
.component-library { padding: 20px; }

.page-header {
  margin-bottom: 20px;
  .header-content {
    display: flex; justify-content: space-between; align-items: center;
    h2 { margin: 0 0 8px; font-size: 24px; }
    .subtitle { margin: 0; color: var(--el-text-color-secondary); font-size: 14px; }
    .header-actions {
      display: flex;
      gap: 12px;
    }
  }
}

.filter-card { 
  margin-bottom: 20px;
  
  .filter-content {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 24px;
  }
  
  .type-filter {
    display: flex;
    align-items: center;
    gap: 12px;
    flex: 1;
    
    .filter-label {
      color: var(--el-text-color-secondary);
      font-size: 14px;
      white-space: nowrap;
    }
    
    .type-tags {
      display: flex;
      gap: 8px;
    }
    
    .type-tag {
      display: flex;
      align-items: center;
      gap: 6px;
      padding: 8px 16px;
      border-radius: 8px;
      cursor: pointer;
      transition: all 0.2s;
      background: var(--el-fill-color-light);
      border: 2px solid transparent;
      
      .el-icon {
        font-size: 16px;
      }
      
      span {
        font-size: 14px;
      }
      
      .count {
        background: var(--el-fill-color-darker);
        padding: 2px 8px;
        border-radius: 10px;
        font-size: 12px;
        min-width: 20px;
        text-align: center;
      }
      
      &:hover {
        background: var(--el-fill-color);
      }
      
      &.active {
        border-color: var(--el-color-primary);
        background: var(--el-color-primary-light-9);
        
        .count {
          background: var(--el-color-primary);
          color: #fff;
        }
      }
      
      &.source {
        &.active {
          border-color: #67c23a;
          background: rgba(103, 194, 58, 0.1);
          .el-icon { color: #67c23a; }
          .count { background: #67c23a; }
        }
        &:hover:not(.active) {
          .el-icon { color: #67c23a; }
        }
      }
      
      &.transform {
        &.active {
          border-color: var(--macos-blue);
          background: rgba(64, 158, 255, 0.1);
          .el-icon { color: var(--macos-blue); }
          .count { background: #409eff; }
        }
        &:hover:not(.active) {
          .el-icon { color: var(--macos-blue); }
        }
      }
      
      &.sink {
        &.active {
          border-color: #e6a23c;
          background: rgba(230, 162, 60, 0.1);
          .el-icon { color: #e6a23c; }
          .count { background: #e6a23c; }
        }
        &:hover:not(.active) {
          .el-icon { color: #e6a23c; }
        }
      }
    }
  }
  
  .keyword-filter {
    width: 240px;
  }
}

.loading-container, .empty-container { padding: 60px; text-align: center; }

.component-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(350px, 1fr));
  gap: 16px;
}

.component-card {
  .card-header {
    display: flex; gap: 12px; margin-bottom: 12px;
    .type-source { color: #67c23a; }
    .type-transform { color: var(--macos-blue); }
    .type-sink { color: #e6a23c; }
    .card-info { flex: 1; }
    .card-name { font-size: 16px; font-weight: 600; display: block; margin-bottom: 6px; }
    .card-tags { display: flex; gap: 6px; flex-wrap: wrap; }
  }
  .card-desc { color: var(--el-text-color-secondary); font-size: 13px; margin: 0 0 12px; }
  .card-datasource-info {
    display: flex;
    align-items: center;
    gap: 8px;
    margin-bottom: 8px;
    padding: 6px 10px;
    background: var(--el-fill-color-lighter);
    border-radius: 4px;
    font-size: 13px;
    .info-label {
      color: var(--el-text-color-secondary);
    }
    .info-value {
      font-family: 'Monaco', 'Consolas', monospace;
      color: var(--el-color-primary);
    }
  }
  .card-yaml {
    background: var(--el-fill-color-light); padding: 10px; border-radius: 4px;
    font-size: 12px; font-family: 'Monaco', 'Consolas', monospace;
    margin: 0 0 12px; max-height: 120px; overflow: auto;
  }
  .card-actions {
    display: flex; gap: 8px;
    border-top: 1px solid var(--el-border-color-lighter); padding-top: 12px;
  }
  .card-datasource-actions {
    display: flex;
    align-items: center;
    justify-content: space-between;
    margin-top: 12px;
    padding-top: 12px;
    border-top: 1px solid var(--el-border-color-lighter);
  }
}

.codemirror-wrapper {
  width: 100%; border: 1px solid var(--el-border-color); border-radius: 4px; overflow: hidden;
  :deep(.cm-editor) {
    font-size: 14px; width: 100%;
    .cm-scroller { font-family: 'Monaco', 'Consolas', 'Courier New', monospace; }
    .cm-content { min-width: 100%; }
  }
}

.yaml-actions { display: flex; align-items: center; gap: 12px; margin-top: 8px; }
.yaml-error-text { display: flex; align-items: center; gap: 4px; color: var(--el-color-danger); font-size: 13px; }
.yaml-success-text { display: flex; align-items: center; gap: 4px; color: var(--el-color-success); font-size: 13px; }

.field-hint { font-size: 12px; color: var(--el-text-color-placeholder); margin-top: 4px; }
.field-row { display: flex; gap: 8px; margin-bottom: 8px; align-items: center; }

.parse-result-table {
  margin-bottom: 12px;
  :deep(.deleted-row) {
    background-color: var(--el-fill-color-light);
    text-decoration: line-through;
    color: var(--el-text-color-placeholder);
  }
  :deep(.syslog-row) {
    background-color: var(--el-color-info-light-9);
  }
}

.parse-actions {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-top: 12px;
}
</style>
