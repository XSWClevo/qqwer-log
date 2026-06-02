# Agent 分层重构设计

## 背景

当前 `cn.mw.loganalysis.agent.service` 包承载了 Agent 模块的大部分实现。这个包里混合了编排、LLM 接入、JSON NLU、意图匹配、规则回退执行、工具处理、Text2SQL 候选生成、Vector 组件计划、会话记忆、流式输出、邮件报表等职责。

这种结构会让 Agent 模块难以阅读，也会提高修改某个能力时影响其它能力的风险。本次重构的目标是建立清晰的包边界和应用入口边界，同时保持现有接口行为不变。

## 目标

- 将 Agent 实现类移动到 `cn.mw.loganalysis.agent` 下按职责划分的包中。
- 保持 Controller 路由、请求/响应 DTO、数据库实体、Mapper、Repository 和外部行为不变。
- 保持现有 LLM、规则回退、Text2SQL、Vector 组件计划、会话记忆、邮件和流式输出行为不变。
- 让主流程可以通过包名看清楚，不需要先读完整个 `service` 包。
- 在当前代码已经隐含出不同职责的地方，收口出更清晰的应用层服务边界。

## 非目标

- 不重写 LLM prompt 策略。
- 不替换 LangChain4j function/tool calling。
- 不替换 JSON NLU 路径。
- 不重新设计 Text2SQL 候选竞争逻辑。
- 第一轮不拆分 `VectorComponentPreviewPlanner`、`SqlCandidateValidator`、`AgentConversationHistoryService` 这类较大的算法类。
- 不改变前端 API 返回结构或 Controller 路径。

## 建议包结构

```text
cn.mw.loganalysis.agent
├── application
├── conversation
├── execution
├── llm
├── nlu
├── notification
├── support
├── text2sql
├── tool
├── vectorplan
├── config
├── controller
├── dto
├── entity
├── mapper
└── repository
```

### `application`

承载 Controller 面向的应用用例入口。

包含类：

- `LogAnalysisAgentService`

职责：

- 通过会话服务准备聊天请求。
- 选择确定性规则链路或 LangChain4j 链路。
- 应用 LLM 失败后的回退策略。
- 完成本轮会话记忆和历史记录收尾。

### `conversation`

承载 session、memory 和持久化对话历史。

包含类：

- `AgentSessionService`
- `AgentConversationMemoryService`
- `AgentConversationHistoryService`

职责：

- 规范化和准备 sessionId。
- 维护短期进程内记忆。
- 持久化和读取对话历史。

### `execution`

承载运行时上下文、规则回退编排、执行上下文、流程事件和本地执行器注册表。

包含类：

- `AgentRuntimeContext`
- `AgentExecutionContext`
- `AgentExecutionContextHolder`
- `FallbackAgentExecutor`
- `AgentFallbackWorkflow`
- `AgentFallbackToolExecutor`
- `AgentFallbackToolExecutorRegistry`
- `AgentContextEnhancer`
- `AgentContextEnhancerChain`
- `DatasourceContextEnhancer`
- `ExecutionContextEnhancer`
- `MessageHistoryContextEnhancer`
- `Decision`
- `AgentFlowEventPublisher`
- `AgentFlowEventType`
- `AgentFlowObserver`
- `LoggingAgentFlowObserver`

职责：

- 构造单次请求的运行时上下文。
- 执行本地规则回退工作流。
- 发布生命周期事件。
- 将已识别的意图路由到具体的本地工具执行器。

### `llm`

承载 LangChain4j 接入和模型驱动的工具调用。

包含类：

- `LangChain4jLogAnalysisAgentExecutor`
- `LangChain4jLogAnalysisAssistant`
- `LangChain4jStreamingLogAnalysisAssistant`
- `LogAnalysisAgentTools`

职责：

- 构造 LLM system prompt。
- 注册 LangChain4j `@Tool` 方法。
- 执行同步和流式模型调用。
- 通过共享的响应组装器，将模型工具调用事件转换为前端流式事件。

### `nlu`

承载意图定义、JSON NLU、规则意图匹配和槽位模型。

包含类：

- `AgentIntent`
- `AgentIntentDecision`
- `AgentIntentMatcher`
- `AgentIntentRecognitionService`
- `IntentDecision`
- `NluSlotsHandler`
- `IntentNode`
- `IntentSlotsEntity`
- `AgentNluSlots`
- `AgentIntentTextSupport`
- `CreateLogParserIntentMatcher`
- `SchemaIntentMatcher`
- `LogsIntentMatcher`
- `TimeseriesIntentMatcher`
- `Text2SqlIntentMatcher`
- `VectorComponentPlanIntentMatcher`

职责：

- 将用户文本转换为可执行的内部意图。
- 解析模型返回的 JSON NLU 结果。
- 应用规则兜底和安全覆盖。

### `tool`

承载统一工具门面、工具载荷、响应组装，以及具体的非 LLM 工具处理器和意图执行器。

包含类：

- `AgentToolFacade`
- `AgentToolPayload`
- `AgentResponseAssembler`
- `SchemaToolHandler`
- `SchemaToolIntentExecutor`
- `LogQueryToolHandler`
- `LogsToolIntentExecutor`
- `TimeSeriesToolHandler`
- `TimeseriesToolIntentExecutor`
- `Text2SqlToolHandler`
- `Text2SqlToolIntentExecutor`
- `VectorComponentPlanToolHandler`
- `VectorComponentPlanToolIntentExecutor`
- `CreateLogParserToolIntentExecutor`

职责：

- 为规则回退执行和 LangChain4j `@Tool` 执行提供统一内部入口。
- 将工具结果和 LangChain4j 工具执行轨迹转换为前端响应 DTO。
- 执行 schema、logs、timeseries、text2sql、vector-plan 等基础工具。

### `text2sql`

承载 Text2SQL 候选生成、校验和竞争。

包含类：

- `SqlCandidate`
- `SqlCandidateProvider`
- `SqlCandidateRaceService`
- `SqlCandidateResult`
- `SqlCandidateValidationResult`
- `SqlCandidateValidator`
- `SqlQuestionNormalizer`
- `SqlTemplateSupport`
- `Text2SqlQueryShape`
- `HistorySqlCandidateProvider`
- `TemplateSqlCandidateProvider`
- `LlmSqlCandidateProvider`

职责：

- 从历史记录、模板和外部 AI service 生成 SQL 候选。
- 在执行前校验生成的 SQL。
- 并发竞争候选 SQL，选择第一个安全且可执行的结果。

### `vectorplan`

承载 Vector 组件计划、槽位提取、source 配置提取和提交产物。

包含类：

- `VectorComponentPlan`
- `VectorComponentPlanStore`
- `VectorComponentPreviewPlanner`
- `VectorComponentCommitArtifacts`
- `VectorComponentCommitService`
- `FieldPlan`
- `AgentTaskFrame`
- `AgentTaskStatus`
- `CreateLogParserTaskFrameStore`
- `CreateLogParserTaskService`
- `CreateLogParserRequirementPresenter`
- `CreateLogParserSlotContext`
- `CreateLogParserSlotExtractor`
- `CreateLogParserSlotPolicy`
- `CreateLogParserSlotHandler`
- `CreateLogParserNluSlotsMerger`
- `CreateLogParserSlotTextSupport`
- `CreateLogParserSourceConfigExtractor`
- `CurrentSinkSlotHandler`
- `SourceTypeSlotHandler`
- `SourceConfigSlotHandler`
- `ComponentPrefixSlotHandler`
- `TableNameSlotHandler`
- `LogSampleSlotHandler`
- `RegexSlotHandler`
- `FileSourceConfigExtractor`
- `SocketLikeSourceConfigExtractor`
- `KafkaSourceConfigExtractor`

职责：

- 收集创建日志解析和组件计划所需的槽位。
- 预览 Vector Remap/Sink 组件计划。
- 提交用户确认后的 Vector 组件计划。

### `notification`

承载 Agent 触发的邮件和报表通知能力。

包含类：

- `AgentEmailService`

职责：

- 渲染并发送当前用户的邮件报表。
- 将报表/邮件格式化逻辑从 Agent 编排中分离出来。

### `support`

承载不属于单一能力包的通用辅助类。

包含类：

- `AgentToolSupport`
- `AgentTimeWindow`
- `AgentStreamEventEmitter`
- `AgentStreamWriter`
- `SlotResult`

职责：

- 提供通用的解析、截断、时间窗口和流式输出工具。
- 避免把通用 helper 放进具体业务能力包中。

## 数据流

### LLM 工具调用链路

```text
AgentController
  -> application.LogAnalysisAgentService
  -> llm.LangChain4jLogAnalysisAgentExecutor
  -> llm.LogAnalysisAgentTools
  -> tool.AgentToolFacade
  -> 具体 tool handler
  -> 下游 stats/vector 服务
  -> tool.AgentResponseAssembler
```

### 确定性/规则回退链路

```text
AgentController
  -> application.LogAnalysisAgentService
  -> execution.FallbackAgentExecutor
  -> execution.AgentFallbackWorkflow
  -> nlu.IntentDecision 或 nlu.AgentIntentRecognitionService
  -> execution.AgentFallbackToolExecutorRegistry
  -> tool.*ToolIntentExecutor
  -> tool.AgentToolFacade
  -> 具体 tool handler
  -> tool.AgentResponseAssembler
```

### Text2SQL 链路

```text
tool.Text2SqlToolHandler
  -> text2sql.SqlCandidateRaceService
  -> text2sql.HistorySqlCandidateProvider
  -> text2sql.TemplateSqlCandidateProvider
  -> text2sql.LlmSqlCandidateProvider
  -> stats.AiQueryService
  -> stats.DynamicLogQueryService
```

### Vector 组件计划链路

```text
tool.VectorComponentPlanToolHandler
  -> vectorplan.VectorComponentPreviewPlanner
  -> vectorplan.CreateLogParser* slot/source 辅助类
  -> vectorplan.VectorComponentPlanStore
  -> vectorplan.VectorComponentCommitService
```

## 可见性规则

- Controller 面向的服务保持 `public`。
- 跨包使用的类必须是 `public`。
- 只在同一个包内使用的策略接口可以保持 package-private。
- 从 `service` 包移出类后，部分当前 package-private 的 record/interface 需要调整为 `public`，尤其是跨包传递的意图和执行模型。
- 保留现有用户改动，包括当前工作区里 `AgentRuntimeContext` 被调整为 `public` 的改动。

## 迁移策略

1. 测试文件和生产文件同步迁移，尽早暴露 package-private 依赖问题。
2. 按能力组逐步搬迁：
   - execution/context/fallback
   - nlu/intent
   - tool facade 和 handlers
   - text2sql
   - vectorplan
   - conversation
   - llm
   - notification/support
3. 每迁移一组后编译一次。
4. 保持类名稳定，降低行为回归风险。
5. 本轮只做分层和入口边界整理，不引入新算法。

## 测试策略

包迁移后运行聚焦测试：

```bash
mvn -pl log-analysis-app test -Dtest='*Agent*,*SqlCandidate*,*Text2Sql*'
```

如果 Maven 模块解析需要从后端父目录执行，则使用：

```bash
cd log-analysis-backend
mvn -pl log-analysis-app test -Dtest='*Agent*,*SqlCandidate*,*Text2Sql*'
```

预期覆盖：

- Text2SQL 候选竞争行为保持不变。
- SQL 校验行为保持不变。
- Text2SQL tool handler 响应结构保持不变。
- LLM SQL candidate provider 仍然调用 `AiQueryService.generateSqlOnly`。
- Agent 包在 Controller 和 config imports 更新后可以正常编译。

## 风险

- package-private 接口跨包移动后需要调整可见性。
- 依赖 package-private 访问的测试可能需要随包移动，或改为通过 public API 测试。
- Spring 组件扫描应保持可用，因为目标包仍在 `cn.mw.loganalysis` 下。
- 即使行为不变，本轮也会产生较多 import 变更。

## 验收标准

- `agent/service` 包不再承载几乎所有 Agent 实现类。
- 新包名能清晰表达职责。
- `AgentController` 仍依赖稳定的应用层服务。
- 现有公开路由和 DTO 不变。
- 聚焦的 Agent/Text2SQL 测试通过。
- 不修改无关文件或生成物。
