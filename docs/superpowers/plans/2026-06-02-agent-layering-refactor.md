# Agent 分层重构 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将 `cn.mw.loganalysis.agent.service` 下混杂的 Agent 实现按职责迁移到清晰的子包，同时保持现有接口、DTO 和业务行为不变。

**Architecture:** 第一轮只做包级分层和应用入口收口，不重写算法。`application` 保持 Controller 面向入口，`execution` 承载本地执行工作流，`llm` 承载 LangChain4j function/tool calling，`nlu` 承载 JSON NLU 和意图，`tool` 承载统一工具门面和工具处理器，`text2sql` 和 `vectorplan` 分别承载复杂业务能力。

**Tech Stack:** Java 21, Spring Boot 3.5.11, LangChain4j, Maven, MyBatis Plus, JUnit 5, Mockito.

---

## 文件结构和职责

### 新包

- `log-analysis-backend/log-analysis-app/src/main/java/cn/mw/loganalysis/agent/application`
  - `LogAnalysisAgentService`: Controller 面向的智能助手应用入口。
- `log-analysis-backend/log-analysis-app/src/main/java/cn/mw/loganalysis/agent/conversation`
  - `AgentSessionService`, `AgentConversationMemoryService`, `AgentConversationHistoryService`: 会话准备、短期记忆、历史记录。
- `log-analysis-backend/log-analysis-app/src/main/java/cn/mw/loganalysis/agent/execution`
  - runtime context、execution context、本地 fallback workflow、flow observer、context enhancer。
- `log-analysis-backend/log-analysis-app/src/main/java/cn/mw/loganalysis/agent/llm`
  - LangChain4j executor、assistant interfaces、`@Tool` 壳子。
- `log-analysis-backend/log-analysis-app/src/main/java/cn/mw/loganalysis/agent/nlu`
  - intent enum、intent decision、JSON NLU、intent matcher、slot model。
- `log-analysis-backend/log-analysis-app/src/main/java/cn/mw/loganalysis/agent/tool`
  - tool facade、tool payload、response assembler、schema/logs/timeseries/text2sql/vector tool handlers 和 tool intent executors。
- `log-analysis-backend/log-analysis-app/src/main/java/cn/mw/loganalysis/agent/text2sql`
  - SQL candidate provider、race、validator、template/history/LLM candidate source。
- `log-analysis-backend/log-analysis-app/src/main/java/cn/mw/loganalysis/agent/vectorplan`
  - Vector component plan、preview、commit、slot/source extractor。
- `log-analysis-backend/log-analysis-app/src/main/java/cn/mw/loganalysis/agent/notification`
  - `AgentEmailService`。
- `log-analysis-backend/log-analysis-app/src/main/java/cn/mw/loganalysis/agent/support`
  - `AgentToolSupport`, `AgentTimeWindow`, `AgentStreamEventEmitter`, `AgentStreamWriter`, `SlotResult`。

### 保持原位的包

- `log-analysis-backend/log-analysis-app/src/main/java/cn/mw/loganalysis/agent/config`
- `log-analysis-backend/log-analysis-app/src/main/java/cn/mw/loganalysis/agent/controller`
- `log-analysis-backend/log-analysis-app/src/main/java/cn/mw/loganalysis/agent/dto`
- `log-analysis-backend/log-analysis-app/src/main/java/cn/mw/loganalysis/agent/entity`
- `log-analysis-backend/log-analysis-app/src/main/java/cn/mw/loganalysis/agent/mapper`
- `log-analysis-backend/log-analysis-app/src/main/java/cn/mw/loganalysis/agent/repository`

### 测试包

同步把对应测试移动到新包：

- `src/test/java/cn/mw/loganalysis/agent/text2sql`
- `src/test/java/cn/mw/loganalysis/agent/tool`
- 保持 `src/test/java/cn/mw/loganalysis/agent/config` 和 `src/test/java/cn/mw/loganalysis/agent/repository` 不变。

---

## 迁移映射

### application

- `service/LogAnalysisAgentService.java` -> `application/LogAnalysisAgentService.java`

### conversation

- `service/AgentSessionService.java` -> `conversation/AgentSessionService.java`
- `service/AgentConversationMemoryService.java` -> `conversation/AgentConversationMemoryService.java`
- `service/AgentConversationHistoryService.java` -> `conversation/AgentConversationHistoryService.java`

### execution

- `service/AgentRuntimeContext.java` -> `execution/AgentRuntimeContext.java`
- `service/AgentExecutionContext.java` -> `execution/AgentExecutionContext.java`
- `service/AgentExecutionContextHolder.java` -> `execution/AgentExecutionContextHolder.java`
- `service/FallbackAgentExecutor.java` -> `execution/FallbackAgentExecutor.java`
- `service/AgentFallbackWorkflow.java` -> `execution/AgentFallbackWorkflow.java`
- `service/AgentFallbackToolExecutor.java` -> `execution/AgentFallbackToolExecutor.java`
- `service/AgentFallbackToolExecutorRegistry.java` -> `execution/AgentFallbackToolExecutorRegistry.java`
- `service/AgentContextEnhancer.java` -> `execution/AgentContextEnhancer.java`
- `service/AgentContextEnhancerChain.java` -> `execution/AgentContextEnhancerChain.java`
- `service/DatasourceContextEnhancer.java` -> `execution/DatasourceContextEnhancer.java`
- `service/ExecutionContextEnhancer.java` -> `execution/ExecutionContextEnhancer.java`
- `service/MessageHistoryContextEnhancer.java` -> `execution/MessageHistoryContextEnhancer.java`
- `service/Decision.java` -> `execution/Decision.java`
- `service/AgentFlowEventPublisher.java` -> `execution/AgentFlowEventPublisher.java`
- `service/AgentFlowEventType.java` -> `execution/AgentFlowEventType.java`
- `service/AgentFlowObserver.java` -> `execution/AgentFlowObserver.java`
- `service/LoggingAgentFlowObserver.java` -> `execution/LoggingAgentFlowObserver.java`

### llm

- `service/LangChain4jLogAnalysisAgentExecutor.java` -> `llm/LangChain4jLogAnalysisAgentExecutor.java`
- `service/LangChain4jLogAnalysisAssistant.java` -> `llm/LangChain4jLogAnalysisAssistant.java`
- `service/LangChain4jStreamingLogAnalysisAssistant.java` -> `llm/LangChain4jStreamingLogAnalysisAssistant.java`
- `service/LogAnalysisAgentTools.java` -> `llm/LogAnalysisAgentTools.java`

### nlu

- `service/AgentIntent.java` -> `nlu/AgentIntent.java`
- `service/AgentIntentDecision.java` -> `nlu/AgentIntentDecision.java`
- `service/AgentIntentMatcher.java` -> `nlu/AgentIntentMatcher.java`
- `service/AgentIntentRecognitionService.java` -> `nlu/AgentIntentRecognitionService.java`
- `service/IntentDecision.java` -> `nlu/IntentDecision.java`
- `service/NluSlotsHandler.java` -> `nlu/NluSlotsHandler.java`
- `service/IntentNode.java` -> `nlu/IntentNode.java`
- `service/IntentSlotsEntity.java` -> `nlu/IntentSlotsEntity.java`
- `service/AgentNluSlots.java` -> `nlu/AgentNluSlots.java`
- `service/AgentIntentTextSupport.java` -> `nlu/AgentIntentTextSupport.java`
- `service/CreateLogParserIntentMatcher.java` -> `nlu/CreateLogParserIntentMatcher.java`
- `service/SchemaIntentMatcher.java` -> `nlu/SchemaIntentMatcher.java`
- `service/LogsIntentMatcher.java` -> `nlu/LogsIntentMatcher.java`
- `service/TimeseriesIntentMatcher.java` -> `nlu/TimeseriesIntentMatcher.java`
- `service/Text2SqlIntentMatcher.java` -> `nlu/Text2SqlIntentMatcher.java`
- `service/VectorComponentPlanIntentMatcher.java` -> `nlu/VectorComponentPlanIntentMatcher.java`

### tool

- `service/AgentToolFacade.java` -> `tool/AgentToolFacade.java`
- `service/AgentToolPayload.java` -> `tool/AgentToolPayload.java`
- `service/AgentResponseAssembler.java` -> `tool/AgentResponseAssembler.java`
- `service/SchemaToolHandler.java` -> `tool/SchemaToolHandler.java`
- `service/SchemaToolIntentExecutor.java` -> `tool/SchemaToolIntentExecutor.java`
- `service/LogQueryToolHandler.java` -> `tool/LogQueryToolHandler.java`
- `service/LogsToolIntentExecutor.java` -> `tool/LogsToolIntentExecutor.java`
- `service/TimeSeriesToolHandler.java` -> `tool/TimeSeriesToolHandler.java`
- `service/TimeseriesToolIntentExecutor.java` -> `tool/TimeseriesToolIntentExecutor.java`
- `service/Text2SqlToolHandler.java` -> `tool/Text2SqlToolHandler.java`
- `service/Text2SqlToolIntentExecutor.java` -> `tool/Text2SqlToolIntentExecutor.java`
- `service/VectorComponentPlanToolHandler.java` -> `tool/VectorComponentPlanToolHandler.java`
- `service/VectorComponentPlanToolIntentExecutor.java` -> `tool/VectorComponentPlanToolIntentExecutor.java`
- `service/CreateLogParserToolIntentExecutor.java` -> `tool/CreateLogParserToolIntentExecutor.java`

### text2sql

- `service/SqlCandidate.java` -> `text2sql/SqlCandidate.java`
- `service/SqlCandidateProvider.java` -> `text2sql/SqlCandidateProvider.java`
- `service/SqlCandidateRaceService.java` -> `text2sql/SqlCandidateRaceService.java`
- `service/SqlCandidateResult.java` -> `text2sql/SqlCandidateResult.java`
- `service/SqlCandidateValidationResult.java` -> `text2sql/SqlCandidateValidationResult.java`
- `service/SqlCandidateValidator.java` -> `text2sql/SqlCandidateValidator.java`
- `service/SqlQuestionNormalizer.java` -> `text2sql/SqlQuestionNormalizer.java`
- `service/SqlTemplateSupport.java` -> `text2sql/SqlTemplateSupport.java`
- `service/Text2SqlQueryShape.java` -> `text2sql/Text2SqlQueryShape.java`
- `service/HistorySqlCandidateProvider.java` -> `text2sql/HistorySqlCandidateProvider.java`
- `service/TemplateSqlCandidateProvider.java` -> `text2sql/TemplateSqlCandidateProvider.java`
- `service/LlmSqlCandidateProvider.java` -> `text2sql/LlmSqlCandidateProvider.java`

### vectorplan

- `service/VectorComponentPlan.java` -> `vectorplan/VectorComponentPlan.java`
- `service/VectorComponentPlanStore.java` -> `vectorplan/VectorComponentPlanStore.java`
- `service/VectorComponentPreviewPlanner.java` -> `vectorplan/VectorComponentPreviewPlanner.java`
- `service/VectorComponentCommitArtifacts.java` -> `vectorplan/VectorComponentCommitArtifacts.java`
- `service/VectorComponentCommitService.java` -> `vectorplan/VectorComponentCommitService.java`
- `service/FieldPlan.java` -> `vectorplan/FieldPlan.java`
- `service/AgentTaskFrame.java` -> `vectorplan/AgentTaskFrame.java`
- `service/AgentTaskStatus.java` -> `vectorplan/AgentTaskStatus.java`
- `service/CreateLogParserTaskFrameStore.java` -> `vectorplan/CreateLogParserTaskFrameStore.java`
- `service/CreateLogParserTaskService.java` -> `vectorplan/CreateLogParserTaskService.java`
- `service/CreateLogParserRequirementPresenter.java` -> `vectorplan/CreateLogParserRequirementPresenter.java`
- `service/CreateLogParserSlotContext.java` -> `vectorplan/CreateLogParserSlotContext.java`
- `service/CreateLogParserSlotExtractor.java` -> `vectorplan/CreateLogParserSlotExtractor.java`
- `service/CreateLogParserSlotPolicy.java` -> `vectorplan/CreateLogParserSlotPolicy.java`
- `service/CreateLogParserSlotHandler.java` -> `vectorplan/CreateLogParserSlotHandler.java`
- `service/CreateLogParserNluSlotsMerger.java` -> `vectorplan/CreateLogParserNluSlotsMerger.java`
- `service/CreateLogParserSlotTextSupport.java` -> `vectorplan/CreateLogParserSlotTextSupport.java`
- `service/CreateLogParserSourceConfigExtractor.java` -> `vectorplan/CreateLogParserSourceConfigExtractor.java`
- `service/CurrentSinkSlotHandler.java` -> `vectorplan/CurrentSinkSlotHandler.java`
- `service/SourceTypeSlotHandler.java` -> `vectorplan/SourceTypeSlotHandler.java`
- `service/SourceConfigSlotHandler.java` -> `vectorplan/SourceConfigSlotHandler.java`
- `service/ComponentPrefixSlotHandler.java` -> `vectorplan/ComponentPrefixSlotHandler.java`
- `service/TableNameSlotHandler.java` -> `vectorplan/TableNameSlotHandler.java`
- `service/LogSampleSlotHandler.java` -> `vectorplan/LogSampleSlotHandler.java`
- `service/RegexSlotHandler.java` -> `vectorplan/RegexSlotHandler.java`
- `service/FileSourceConfigExtractor.java` -> `vectorplan/FileSourceConfigExtractor.java`
- `service/SocketLikeSourceConfigExtractor.java` -> `vectorplan/SocketLikeSourceConfigExtractor.java`
- `service/KafkaSourceConfigExtractor.java` -> `vectorplan/KafkaSourceConfigExtractor.java`

### notification

- `service/AgentEmailService.java` -> `notification/AgentEmailService.java`

### support

- `service/AgentToolSupport.java` -> `support/AgentToolSupport.java`
- `service/AgentTimeWindow.java` -> `support/AgentTimeWindow.java`
- `service/AgentStreamEventEmitter.java` -> `support/AgentStreamEventEmitter.java`
- `service/AgentStreamWriter.java` -> `support/AgentStreamWriter.java`
- `service/SlotResult.java` -> `support/SlotResult.java`

---

## Task 1: 建立基线和保护用户改动

**Files:**
- Read: `/Users/xsw/custom_idea_project/qqwer/log-analysis-backend/log-analysis-app/src/main/java/cn/mw/loganalysis/agent/service/AgentRuntimeContext.java`
- No production edits in this task.

- [ ] **Step 1: 查看工作区状态**

```bash
git status --short
```

Expected: 看到已有用户改动，例如 `AgentRuntimeContext.java` 可能已经是 modified；不要执行 `git reset` 或 `git checkout --`。

- [ ] **Step 2: 确认 `AgentRuntimeContext` 当前 public 改动**

```bash
git diff -- log-analysis-backend/log-analysis-app/src/main/java/cn/mw/loganalysis/agent/service/AgentRuntimeContext.java
```

Expected: 如果 diff 显示 `class AgentRuntimeContext` 改为 `public class AgentRuntimeContext`，后续迁移必须保留该改动。

- [ ] **Step 3: 运行迁移前聚焦测试**

```bash
cd log-analysis-backend
mvn -pl log-analysis-app test -Dtest='*Agent*,*SqlCandidate*,*Text2Sql*'
```

Expected: PASS。若因为本地环境依赖缺失失败，记录具体失败原因，不修改业务代码绕过测试。

---

## Task 2: 迁移 execution 包

**Files:**
- Move: execution 映射表里的 18 个文件。
- Modify imports in: all Java files under `log-analysis-backend/log-analysis-app/src/main/java/cn/mw/loganalysis/agent`.
- Modify tests only if compile errors reference moved execution classes.

- [ ] **Step 1: 创建目标目录并移动文件**

```bash
cd /Users/xsw/custom_idea_project/qqwer
mkdir -p log-analysis-backend/log-analysis-app/src/main/java/cn/mw/loganalysis/agent/execution
git mv log-analysis-backend/log-analysis-app/src/main/java/cn/mw/loganalysis/agent/service/AgentRuntimeContext.java log-analysis-backend/log-analysis-app/src/main/java/cn/mw/loganalysis/agent/execution/AgentRuntimeContext.java
git mv log-analysis-backend/log-analysis-app/src/main/java/cn/mw/loganalysis/agent/service/AgentExecutionContext.java log-analysis-backend/log-analysis-app/src/main/java/cn/mw/loganalysis/agent/execution/AgentExecutionContext.java
git mv log-analysis-backend/log-analysis-app/src/main/java/cn/mw/loganalysis/agent/service/AgentExecutionContextHolder.java log-analysis-backend/log-analysis-app/src/main/java/cn/mw/loganalysis/agent/execution/AgentExecutionContextHolder.java
git mv log-analysis-backend/log-analysis-app/src/main/java/cn/mw/loganalysis/agent/service/FallbackAgentExecutor.java log-analysis-backend/log-analysis-app/src/main/java/cn/mw/loganalysis/agent/execution/FallbackAgentExecutor.java
git mv log-analysis-backend/log-analysis-app/src/main/java/cn/mw/loganalysis/agent/service/AgentFallbackWorkflow.java log-analysis-backend/log-analysis-app/src/main/java/cn/mw/loganalysis/agent/execution/AgentFallbackWorkflow.java
git mv log-analysis-backend/log-analysis-app/src/main/java/cn/mw/loganalysis/agent/service/AgentFallbackToolExecutor.java log-analysis-backend/log-analysis-app/src/main/java/cn/mw/loganalysis/agent/execution/AgentFallbackToolExecutor.java
git mv log-analysis-backend/log-analysis-app/src/main/java/cn/mw/loganalysis/agent/service/AgentFallbackToolExecutorRegistry.java log-analysis-backend/log-analysis-app/src/main/java/cn/mw/loganalysis/agent/execution/AgentFallbackToolExecutorRegistry.java
git mv log-analysis-backend/log-analysis-app/src/main/java/cn/mw/loganalysis/agent/service/AgentContextEnhancer.java log-analysis-backend/log-analysis-app/src/main/java/cn/mw/loganalysis/agent/execution/AgentContextEnhancer.java
git mv log-analysis-backend/log-analysis-app/src/main/java/cn/mw/loganalysis/agent/service/AgentContextEnhancerChain.java log-analysis-backend/log-analysis-app/src/main/java/cn/mw/loganalysis/agent/execution/AgentContextEnhancerChain.java
git mv log-analysis-backend/log-analysis-app/src/main/java/cn/mw/loganalysis/agent/service/DatasourceContextEnhancer.java log-analysis-backend/log-analysis-app/src/main/java/cn/mw/loganalysis/agent/execution/DatasourceContextEnhancer.java
git mv log-analysis-backend/log-analysis-app/src/main/java/cn/mw/loganalysis/agent/service/ExecutionContextEnhancer.java log-analysis-backend/log-analysis-app/src/main/java/cn/mw/loganalysis/agent/execution/ExecutionContextEnhancer.java
git mv log-analysis-backend/log-analysis-app/src/main/java/cn/mw/loganalysis/agent/service/MessageHistoryContextEnhancer.java log-analysis-backend/log-analysis-app/src/main/java/cn/mw/loganalysis/agent/execution/MessageHistoryContextEnhancer.java
git mv log-analysis-backend/log-analysis-app/src/main/java/cn/mw/loganalysis/agent/service/Decision.java log-analysis-backend/log-analysis-app/src/main/java/cn/mw/loganalysis/agent/execution/Decision.java
git mv log-analysis-backend/log-analysis-app/src/main/java/cn/mw/loganalysis/agent/service/AgentFlowEventPublisher.java log-analysis-backend/log-analysis-app/src/main/java/cn/mw/loganalysis/agent/execution/AgentFlowEventPublisher.java
git mv log-analysis-backend/log-analysis-app/src/main/java/cn/mw/loganalysis/agent/service/AgentFlowEventType.java log-analysis-backend/log-analysis-app/src/main/java/cn/mw/loganalysis/agent/execution/AgentFlowEventType.java
git mv log-analysis-backend/log-analysis-app/src/main/java/cn/mw/loganalysis/agent/service/AgentFlowObserver.java log-analysis-backend/log-analysis-app/src/main/java/cn/mw/loganalysis/agent/execution/AgentFlowObserver.java
git mv log-analysis-backend/log-analysis-app/src/main/java/cn/mw/loganalysis/agent/service/LoggingAgentFlowObserver.java log-analysis-backend/log-analysis-app/src/main/java/cn/mw/loganalysis/agent/execution/LoggingAgentFlowObserver.java
```

- [ ] **Step 2: 更新 package 声明**

```bash
perl -0pi -e 's/package cn\.mw\.loganalysis\.agent\.service;/package cn.mw.loganalysis.agent.execution;/' log-analysis-backend/log-analysis-app/src/main/java/cn/mw/loganalysis/agent/execution/*.java
```

- [ ] **Step 3: 调整 execution 跨包可见性**

Edit these declarations:

```java
public interface AgentFallbackToolExecutor extends Ordered
public abstract class Decision
```

In this task, make sure `AgentRuntimeContext`, `AgentExecutionContext`, `AgentExecutionContextHolder`, `FallbackAgentExecutor`, `AgentFallbackWorkflow`, `AgentFallbackToolExecutor`, `AgentFallbackToolExecutorRegistry`, and `Decision` are public where used outside `execution`.

- [ ] **Step 4: 更新 imports**

Replace old explicit imports for moved execution classes:

```bash
rg -l 'cn\.mw\.loganalysis\.agent\.service\.(AgentRuntimeContext|AgentExecutionContext|AgentExecutionContextHolder|FallbackAgentExecutor|AgentFallbackWorkflow|AgentFallbackToolExecutor|AgentFallbackToolExecutorRegistry|AgentContextEnhancer|AgentContextEnhancerChain|DatasourceContextEnhancer|ExecutionContextEnhancer|MessageHistoryContextEnhancer|Decision|AgentFlowEventPublisher|AgentFlowEventType|AgentFlowObserver|LoggingAgentFlowObserver)' log-analysis-backend/log-analysis-app/src/main/java log-analysis-backend/log-analysis-app/src/test/java | xargs perl -0pi -e 's/cn\.mw\.loganalysis\.agent\.service\.(AgentRuntimeContext|AgentExecutionContext|AgentExecutionContextHolder|FallbackAgentExecutor|AgentFallbackWorkflow|AgentFallbackToolExecutor|AgentFallbackToolExecutorRegistry|AgentContextEnhancer|AgentContextEnhancerChain|DatasourceContextEnhancer|ExecutionContextEnhancer|MessageHistoryContextEnhancer|Decision|AgentFlowEventPublisher|AgentFlowEventType|AgentFlowObserver|LoggingAgentFlowObserver)/cn.mw.loganalysis.agent.execution.$1/g'
```

- [ ] **Step 5: 编译并修复缺失 imports**

```bash
cd log-analysis-backend
mvn -pl log-analysis-app -DskipTests compile
```

Expected: PASS for execution move. If compile reports unresolved moved execution types in files that previously shared the `service` package, add explicit imports from `cn.mw.loganalysis.agent.execution`.

- [ ] **Step 6: Commit**

```bash
git add log-analysis-backend/log-analysis-app/src/main/java/cn/mw/loganalysis/agent
git commit -m "refactor: move agent execution components"
```

---

## Task 3: 迁移 nlu 包

**Files:**
- Move: nlu 映射表里的 17 个文件。
- Modify: `execution/AgentFallbackWorkflow.java`, `execution/AgentRuntimeContext.java`, `tool/*`, `vectorplan/*`, and any test imports that reference intent types.

- [ ] **Step 1: 创建目录并移动文件**

```bash
cd /Users/xsw/custom_idea_project/qqwer
mkdir -p log-analysis-backend/log-analysis-app/src/main/java/cn/mw/loganalysis/agent/nlu
for f in AgentIntent AgentIntentDecision AgentIntentMatcher AgentIntentRecognitionService IntentDecision NluSlotsHandler IntentNode IntentSlotsEntity AgentNluSlots AgentIntentTextSupport CreateLogParserIntentMatcher SchemaIntentMatcher LogsIntentMatcher TimeseriesIntentMatcher Text2SqlIntentMatcher VectorComponentPlanIntentMatcher; do git mv log-analysis-backend/log-analysis-app/src/main/java/cn/mw/loganalysis/agent/service/${f}.java log-analysis-backend/log-analysis-app/src/main/java/cn/mw/loganalysis/agent/nlu/${f}.java; done
```

- [ ] **Step 2: 更新 package 声明**

```bash
perl -0pi -e 's/package cn\.mw\.loganalysis\.agent\.service;/package cn.mw.loganalysis.agent.nlu;/' log-analysis-backend/log-analysis-app/src/main/java/cn/mw/loganalysis/agent/nlu/*.java
```

- [ ] **Step 3: 调整 nlu 跨包可见性**

Edit declarations to make these public:

```java
public enum AgentIntent
public record AgentIntentDecision(...)
public interface AgentIntentMatcher extends Ordered
public class IntentNode extends IntentSlotsEntity
public class IntentSlotsEntity
public class AgentNluSlots
```

Also change `AgentIntentRecognitionService.recognize`, `AgentIntentRecognitionService.decide`, and `AgentIntentRecognitionService.apply` to public only when they are called from `execution`.

- [ ] **Step 4: 更新 imports**

```bash
rg -l 'cn\.mw\.loganalysis\.agent\.service\.(AgentIntent|AgentIntentDecision|AgentIntentMatcher|AgentIntentRecognitionService|IntentDecision|NluSlotsHandler|IntentNode|IntentSlotsEntity|AgentNluSlots|AgentIntentTextSupport|CreateLogParserIntentMatcher|SchemaIntentMatcher|LogsIntentMatcher|TimeseriesIntentMatcher|Text2SqlIntentMatcher|VectorComponentPlanIntentMatcher)' log-analysis-backend/log-analysis-app/src/main/java log-analysis-backend/log-analysis-app/src/test/java | xargs perl -0pi -e 's/cn\.mw\.loganalysis\.agent\.service\.(AgentIntent|AgentIntentDecision|AgentIntentMatcher|AgentIntentRecognitionService|IntentDecision|NluSlotsHandler|IntentNode|IntentSlotsEntity|AgentNluSlots|AgentIntentTextSupport|CreateLogParserIntentMatcher|SchemaIntentMatcher|LogsIntentMatcher|TimeseriesIntentMatcher|Text2SqlIntentMatcher|VectorComponentPlanIntentMatcher)/cn.mw.loganalysis.agent.nlu.$1/g'
```

- [ ] **Step 5: 编译并补 explicit imports**

```bash
cd log-analysis-backend
mvn -pl log-analysis-app -DskipTests compile
```

Expected: PASS. Add explicit imports for `cn.mw.loganalysis.agent.nlu.*` classes in moved or still-unmoved classes that previously referenced same-package NLU types.

- [ ] **Step 6: Commit**

```bash
git add log-analysis-backend/log-analysis-app/src/main/java/cn/mw/loganalysis/agent
git commit -m "refactor: move agent nlu components"
```

---

## Task 4: 迁移 support、conversation、notification 包

**Files:**
- Move support/conversation/notification 映射表里的 9 个文件.
- Modify: `controller/AgentController.java`, execution/llm/tool/vectorplan imports.

- [ ] **Step 1: 移动 support 文件**

```bash
cd /Users/xsw/custom_idea_project/qqwer
mkdir -p log-analysis-backend/log-analysis-app/src/main/java/cn/mw/loganalysis/agent/support
for f in AgentToolSupport AgentTimeWindow AgentStreamEventEmitter AgentStreamWriter SlotResult; do git mv log-analysis-backend/log-analysis-app/src/main/java/cn/mw/loganalysis/agent/service/${f}.java log-analysis-backend/log-analysis-app/src/main/java/cn/mw/loganalysis/agent/support/${f}.java; done
perl -0pi -e 's/package cn\.mw\.loganalysis\.agent\.service;/package cn.mw.loganalysis.agent.support;/' log-analysis-backend/log-analysis-app/src/main/java/cn/mw/loganalysis/agent/support/*.java
```

- [ ] **Step 2: 移动 conversation 文件**

```bash
mkdir -p log-analysis-backend/log-analysis-app/src/main/java/cn/mw/loganalysis/agent/conversation
for f in AgentSessionService AgentConversationMemoryService AgentConversationHistoryService; do git mv log-analysis-backend/log-analysis-app/src/main/java/cn/mw/loganalysis/agent/service/${f}.java log-analysis-backend/log-analysis-app/src/main/java/cn/mw/loganalysis/agent/conversation/${f}.java; done
perl -0pi -e 's/package cn\.mw\.loganalysis\.agent\.service;/package cn.mw.loganalysis.agent.conversation;/' log-analysis-backend/log-analysis-app/src/main/java/cn/mw/loganalysis/agent/conversation/*.java
```

- [ ] **Step 3: 移动 notification 文件**

```bash
mkdir -p log-analysis-backend/log-analysis-app/src/main/java/cn/mw/loganalysis/agent/notification
git mv log-analysis-backend/log-analysis-app/src/main/java/cn/mw/loganalysis/agent/service/AgentEmailService.java log-analysis-backend/log-analysis-app/src/main/java/cn/mw/loganalysis/agent/notification/AgentEmailService.java
perl -0pi -e 's/package cn\.mw\.loganalysis\.agent\.service;/package cn.mw.loganalysis.agent.notification;/' log-analysis-backend/log-analysis-app/src/main/java/cn/mw/loganalysis/agent/notification/AgentEmailService.java
```

- [ ] **Step 4: 调整 support 可见性**

Make these declarations public:

```java
public interface AgentStreamEventEmitter
public interface SlotResult
```

`AgentToolSupport`, `AgentTimeWindow`, and `AgentStreamWriter` should already be public or should be made public when used outside `support`.

- [ ] **Step 5: 更新 imports**

```bash
rg -l 'cn\.mw\.loganalysis\.agent\.service\.(AgentToolSupport|AgentTimeWindow|AgentStreamEventEmitter|AgentStreamWriter|SlotResult|AgentSessionService|AgentConversationMemoryService|AgentConversationHistoryService|AgentEmailService)' log-analysis-backend/log-analysis-app/src/main/java log-analysis-backend/log-analysis-app/src/test/java | xargs perl -0pi -e 's/cn\.mw\.loganalysis\.agent\.service\.(AgentToolSupport|AgentTimeWindow|AgentStreamEventEmitter|AgentStreamWriter|SlotResult)/cn.mw.loganalysis.agent.support.$1/g; s/cn\.mw\.loganalysis\.agent\.service\.(AgentSessionService|AgentConversationMemoryService|AgentConversationHistoryService)/cn.mw.loganalysis.agent.conversation.$1/g; s/cn\.mw\.loganalysis\.agent\.service\.AgentEmailService/cn.mw.loganalysis.agent.notification.AgentEmailService/g'
```

- [ ] **Step 6: 编译并补 imports**

```bash
cd log-analysis-backend
mvn -pl log-analysis-app -DskipTests compile
```

Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add log-analysis-backend/log-analysis-app/src/main/java/cn/mw/loganalysis/agent
git commit -m "refactor: move agent support and conversation components"
```

---

## Task 5: 迁移 text2sql 包和测试

**Files:**
- Move: text2sql 映射表里的 12 个生产文件。
- Move tests:
  - `HistorySqlCandidateProviderTest.java`
  - `LlmSqlCandidateProviderTest.java`
  - `SqlCandidateRaceServiceTest.java`
  - `SqlCandidateValidatorTest.java`
  - `TemplateSqlCandidateProviderTest.java`

- [ ] **Step 1: 移动生产文件**

```bash
cd /Users/xsw/custom_idea_project/qqwer
mkdir -p log-analysis-backend/log-analysis-app/src/main/java/cn/mw/loganalysis/agent/text2sql
for f in SqlCandidate SqlCandidateProvider SqlCandidateRaceService SqlCandidateResult SqlCandidateValidationResult SqlCandidateValidator SqlQuestionNormalizer SqlTemplateSupport Text2SqlQueryShape HistorySqlCandidateProvider TemplateSqlCandidateProvider LlmSqlCandidateProvider; do git mv log-analysis-backend/log-analysis-app/src/main/java/cn/mw/loganalysis/agent/service/${f}.java log-analysis-backend/log-analysis-app/src/main/java/cn/mw/loganalysis/agent/text2sql/${f}.java; done
perl -0pi -e 's/package cn\.mw\.loganalysis\.agent\.service;/package cn.mw.loganalysis.agent.text2sql;/' log-analysis-backend/log-analysis-app/src/main/java/cn/mw/loganalysis/agent/text2sql/*.java
```

- [ ] **Step 2: 移动测试文件**

```bash
mkdir -p log-analysis-backend/log-analysis-app/src/test/java/cn/mw/loganalysis/agent/text2sql
for f in HistorySqlCandidateProviderTest LlmSqlCandidateProviderTest SqlCandidateRaceServiceTest SqlCandidateValidatorTest TemplateSqlCandidateProviderTest; do git mv log-analysis-backend/log-analysis-app/src/test/java/cn/mw/loganalysis/agent/service/${f}.java log-analysis-backend/log-analysis-app/src/test/java/cn/mw/loganalysis/agent/text2sql/${f}.java; done
perl -0pi -e 's/package cn\.mw\.loganalysis\.agent\.service;/package cn.mw.loganalysis.agent.text2sql;/' log-analysis-backend/log-analysis-app/src/test/java/cn/mw/loganalysis/agent/text2sql/*.java
```

- [ ] **Step 3: 调整 text2sql 可见性**

Make these declarations public because `tool.Text2SqlToolHandler` and tests cross package boundaries:

```java
public class SqlCandidateRaceService
public class SqlCandidateValidator
public class SqlQuestionNormalizer
public class SqlTemplateSupport
public class Text2SqlQueryShape
public interface SqlCandidateProvider extends Ordered
public class SqlCandidate
public class SqlCandidateResult
public class SqlCandidateValidationResult
```

- [ ] **Step 4: 更新 imports**

```bash
rg -l 'cn\.mw\.loganalysis\.agent\.service\.(SqlCandidate|SqlCandidateProvider|SqlCandidateRaceService|SqlCandidateResult|SqlCandidateValidationResult|SqlCandidateValidator|SqlQuestionNormalizer|SqlTemplateSupport|Text2SqlQueryShape|HistorySqlCandidateProvider|TemplateSqlCandidateProvider|LlmSqlCandidateProvider)' log-analysis-backend/log-analysis-app/src/main/java log-analysis-backend/log-analysis-app/src/test/java | xargs perl -0pi -e 's/cn\.mw\.loganalysis\.agent\.service\.(SqlCandidate|SqlCandidateProvider|SqlCandidateRaceService|SqlCandidateResult|SqlCandidateValidationResult|SqlCandidateValidator|SqlQuestionNormalizer|SqlTemplateSupport|Text2SqlQueryShape|HistorySqlCandidateProvider|TemplateSqlCandidateProvider|LlmSqlCandidateProvider)/cn.mw.loganalysis.agent.text2sql.$1/g'
```

- [ ] **Step 5: 运行 Text2SQL 测试**

```bash
cd log-analysis-backend
mvn -pl log-analysis-app test -Dtest='*SqlCandidate*,*Text2Sql*,LlmSqlCandidateProviderTest,HistorySqlCandidateProviderTest,TemplateSqlCandidateProviderTest'
```

Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add log-analysis-backend/log-analysis-app/src/main/java/cn/mw/loganalysis/agent log-analysis-backend/log-analysis-app/src/test/java/cn/mw/loganalysis/agent
git commit -m "refactor: move agent text2sql components"
```

---

## Task 6: 迁移 vectorplan 包

**Files:**
- Move: vectorplan 映射表里的 30 个生产文件。

- [ ] **Step 1: 移动文件**

```bash
cd /Users/xsw/custom_idea_project/qqwer
mkdir -p log-analysis-backend/log-analysis-app/src/main/java/cn/mw/loganalysis/agent/vectorplan
for f in VectorComponentPlan VectorComponentPlanStore VectorComponentPreviewPlanner VectorComponentCommitArtifacts VectorComponentCommitService FieldPlan AgentTaskFrame AgentTaskStatus CreateLogParserTaskFrameStore CreateLogParserTaskService CreateLogParserRequirementPresenter CreateLogParserSlotContext CreateLogParserSlotExtractor CreateLogParserSlotPolicy CreateLogParserSlotHandler CreateLogParserNluSlotsMerger CreateLogParserSlotTextSupport CreateLogParserSourceConfigExtractor CurrentSinkSlotHandler SourceTypeSlotHandler SourceConfigSlotHandler ComponentPrefixSlotHandler TableNameSlotHandler LogSampleSlotHandler RegexSlotHandler FileSourceConfigExtractor SocketLikeSourceConfigExtractor KafkaSourceConfigExtractor; do git mv log-analysis-backend/log-analysis-app/src/main/java/cn/mw/loganalysis/agent/service/${f}.java log-analysis-backend/log-analysis-app/src/main/java/cn/mw/loganalysis/agent/vectorplan/${f}.java; done
perl -0pi -e 's/package cn\.mw\.loganalysis\.agent\.service;/package cn.mw.loganalysis.agent.vectorplan;/' log-analysis-backend/log-analysis-app/src/main/java/cn/mw/loganalysis/agent/vectorplan/*.java
```

- [ ] **Step 2: 调整 vectorplan 可见性**

Make these declarations public because `tool` and controller-facing code use them:

```java
public class VectorComponentPlan
public class VectorComponentPlanStore
public class VectorComponentPreviewPlanner
public class VectorComponentCommitArtifacts
public class VectorComponentCommitService
public class FieldPlan
public class AgentTaskFrame
public enum AgentTaskStatus
```

Keep `CreateLogParserSlotHandler` and `CreateLogParserSourceConfigExtractor` package-private because their implementations and consumers stay in `vectorplan`.

- [ ] **Step 3: 更新 imports**

```bash
rg -l 'cn\.mw\.loganalysis\.agent\.service\.(VectorComponentPlan|VectorComponentPlanStore|VectorComponentPreviewPlanner|VectorComponentCommitArtifacts|VectorComponentCommitService|FieldPlan|AgentTaskFrame|AgentTaskStatus|CreateLogParserTaskFrameStore|CreateLogParserTaskService|CreateLogParserRequirementPresenter|CreateLogParserSlotContext|CreateLogParserSlotExtractor|CreateLogParserSlotPolicy|CreateLogParserSlotHandler|CreateLogParserNluSlotsMerger|CreateLogParserSlotTextSupport|CreateLogParserSourceConfigExtractor|CurrentSinkSlotHandler|SourceTypeSlotHandler|SourceConfigSlotHandler|ComponentPrefixSlotHandler|TableNameSlotHandler|LogSampleSlotHandler|RegexSlotHandler|FileSourceConfigExtractor|SocketLikeSourceConfigExtractor|KafkaSourceConfigExtractor)' log-analysis-backend/log-analysis-app/src/main/java log-analysis-backend/log-analysis-app/src/test/java | xargs perl -0pi -e 's/cn\.mw\.loganalysis\.agent\.service\.(VectorComponentPlan|VectorComponentPlanStore|VectorComponentPreviewPlanner|VectorComponentCommitArtifacts|VectorComponentCommitService|FieldPlan|AgentTaskFrame|AgentTaskStatus|CreateLogParserTaskFrameStore|CreateLogParserTaskService|CreateLogParserRequirementPresenter|CreateLogParserSlotContext|CreateLogParserSlotExtractor|CreateLogParserSlotPolicy|CreateLogParserSlotHandler|CreateLogParserNluSlotsMerger|CreateLogParserSlotTextSupport|CreateLogParserSourceConfigExtractor|CurrentSinkSlotHandler|SourceTypeSlotHandler|SourceConfigSlotHandler|ComponentPrefixSlotHandler|TableNameSlotHandler|LogSampleSlotHandler|RegexSlotHandler|FileSourceConfigExtractor|SocketLikeSourceConfigExtractor|KafkaSourceConfigExtractor)/cn.mw.loganalysis.agent.vectorplan.$1/g'
```

- [ ] **Step 4: 编译并补 imports**

```bash
cd log-analysis-backend
mvn -pl log-analysis-app -DskipTests compile
```

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add log-analysis-backend/log-analysis-app/src/main/java/cn/mw/loganalysis/agent
git commit -m "refactor: move agent vector planning components"
```

---

## Task 7: 迁移 tool 包和 tool 测试

**Files:**
- Move: tool 映射表里的 14 个生产文件。
- Move: `Text2SqlToolHandlerTest.java` to `src/test/java/cn/mw/loganalysis/agent/tool/Text2SqlToolHandlerTest.java`.

- [ ] **Step 1: 移动生产文件**

```bash
cd /Users/xsw/custom_idea_project/qqwer
mkdir -p log-analysis-backend/log-analysis-app/src/main/java/cn/mw/loganalysis/agent/tool
for f in AgentToolFacade AgentToolPayload AgentResponseAssembler SchemaToolHandler SchemaToolIntentExecutor LogQueryToolHandler LogsToolIntentExecutor TimeSeriesToolHandler TimeseriesToolIntentExecutor Text2SqlToolHandler Text2SqlToolIntentExecutor VectorComponentPlanToolHandler VectorComponentPlanToolIntentExecutor CreateLogParserToolIntentExecutor; do git mv log-analysis-backend/log-analysis-app/src/main/java/cn/mw/loganalysis/agent/service/${f}.java log-analysis-backend/log-analysis-app/src/main/java/cn/mw/loganalysis/agent/tool/${f}.java; done
perl -0pi -e 's/package cn\.mw\.loganalysis\.agent\.service;/package cn.mw.loganalysis.agent.tool;/' log-analysis-backend/log-analysis-app/src/main/java/cn/mw/loganalysis/agent/tool/*.java
```

- [ ] **Step 2: 移动 tool 测试**

```bash
mkdir -p log-analysis-backend/log-analysis-app/src/test/java/cn/mw/loganalysis/agent/tool
git mv log-analysis-backend/log-analysis-app/src/test/java/cn/mw/loganalysis/agent/service/Text2SqlToolHandlerTest.java log-analysis-backend/log-analysis-app/src/test/java/cn/mw/loganalysis/agent/tool/Text2SqlToolHandlerTest.java
perl -0pi -e 's/package cn\.mw\.loganalysis\.agent\.service;/package cn.mw.loganalysis.agent.tool;/' log-analysis-backend/log-analysis-app/src/test/java/cn/mw/loganalysis/agent/tool/Text2SqlToolHandlerTest.java
```

- [ ] **Step 3: 调整 tool 可见性**

Make all moved tool classes public. These are Spring components or DTO-style payload/assembler classes used across `llm`, `execution`, `application`, and tests.

- [ ] **Step 4: 更新 imports**

```bash
rg -l 'cn\.mw\.loganalysis\.agent\.service\.(AgentToolFacade|AgentToolPayload|AgentResponseAssembler|SchemaToolHandler|SchemaToolIntentExecutor|LogQueryToolHandler|LogsToolIntentExecutor|TimeSeriesToolHandler|TimeseriesToolIntentExecutor|Text2SqlToolHandler|Text2SqlToolIntentExecutor|VectorComponentPlanToolHandler|VectorComponentPlanToolIntentExecutor|CreateLogParserToolIntentExecutor)' log-analysis-backend/log-analysis-app/src/main/java log-analysis-backend/log-analysis-app/src/test/java | xargs perl -0pi -e 's/cn\.mw\.loganalysis\.agent\.service\.(AgentToolFacade|AgentToolPayload|AgentResponseAssembler|SchemaToolHandler|SchemaToolIntentExecutor|LogQueryToolHandler|LogsToolIntentExecutor|TimeSeriesToolHandler|TimeseriesToolIntentExecutor|Text2SqlToolHandler|Text2SqlToolIntentExecutor|VectorComponentPlanToolHandler|VectorComponentPlanToolIntentExecutor|CreateLogParserToolIntentExecutor)/cn.mw.loganalysis.agent.tool.$1/g'
```

- [ ] **Step 5: 运行 tool 和 text2sql 测试**

```bash
cd log-analysis-backend
mvn -pl log-analysis-app test -Dtest='Text2SqlToolHandlerTest,*SqlCandidate*,*Text2Sql*'
```

Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add log-analysis-backend/log-analysis-app/src/main/java/cn/mw/loganalysis/agent log-analysis-backend/log-analysis-app/src/test/java/cn/mw/loganalysis/agent
git commit -m "refactor: move agent tool components"
```

---

## Task 8: 迁移 llm、application，并更新 controller/config imports

**Files:**
- Move: llm 映射表里的 4 个生产文件。
- Move: `LogAnalysisAgentService.java` to application.
- Modify:
  - `agent/controller/AgentController.java`
  - `agent/config/LangChain4jAgentConfiguration.java`

- [ ] **Step 1: 移动 llm 文件**

```bash
cd /Users/xsw/custom_idea_project/qqwer
mkdir -p log-analysis-backend/log-analysis-app/src/main/java/cn/mw/loganalysis/agent/llm
for f in LangChain4jLogAnalysisAgentExecutor LangChain4jLogAnalysisAssistant LangChain4jStreamingLogAnalysisAssistant LogAnalysisAgentTools; do git mv log-analysis-backend/log-analysis-app/src/main/java/cn/mw/loganalysis/agent/service/${f}.java log-analysis-backend/log-analysis-app/src/main/java/cn/mw/loganalysis/agent/llm/${f}.java; done
perl -0pi -e 's/package cn\.mw\.loganalysis\.agent\.service;/package cn.mw.loganalysis.agent.llm;/' log-analysis-backend/log-analysis-app/src/main/java/cn/mw/loganalysis/agent/llm/*.java
```

- [ ] **Step 2: 移动 application 文件**

```bash
mkdir -p log-analysis-backend/log-analysis-app/src/main/java/cn/mw/loganalysis/agent/application
git mv log-analysis-backend/log-analysis-app/src/main/java/cn/mw/loganalysis/agent/service/LogAnalysisAgentService.java log-analysis-backend/log-analysis-app/src/main/java/cn/mw/loganalysis/agent/application/LogAnalysisAgentService.java
perl -0pi -e 's/package cn\.mw\.loganalysis\.agent\.service;/package cn.mw.loganalysis.agent.application;/' log-analysis-backend/log-analysis-app/src/main/java/cn/mw/loganalysis/agent/application/LogAnalysisAgentService.java
```

- [ ] **Step 3: 更新 imports**

```bash
rg -l 'cn\.mw\.loganalysis\.agent\.service\.(LangChain4jLogAnalysisAgentExecutor|LangChain4jLogAnalysisAssistant|LangChain4jStreamingLogAnalysisAssistant|LogAnalysisAgentTools|LogAnalysisAgentService)' log-analysis-backend/log-analysis-app/src/main/java log-analysis-backend/log-analysis-app/src/test/java | xargs perl -0pi -e 's/cn\.mw\.loganalysis\.agent\.service\.(LangChain4jLogAnalysisAgentExecutor|LangChain4jLogAnalysisAssistant|LangChain4jStreamingLogAnalysisAssistant|LogAnalysisAgentTools)/cn.mw.loganalysis.agent.llm.$1/g; s/cn\.mw\.loganalysis\.agent\.service\.LogAnalysisAgentService/cn.mw.loganalysis.agent.application.LogAnalysisAgentService/g'
```

- [ ] **Step 4: 手动修正 controller/config import**

Ensure imports are exactly:

```java
import cn.mw.loganalysis.agent.application.LogAnalysisAgentService;
import cn.mw.loganalysis.agent.notification.AgentEmailService;
import cn.mw.loganalysis.agent.support.AgentStreamWriter;
import cn.mw.loganalysis.agent.tool.VectorComponentPlanToolHandler;
```

in `AgentController.java`, and:

```java
import cn.mw.loganalysis.agent.llm.LangChain4jLogAnalysisAgentExecutor;
```

in `LangChain4jAgentConfiguration.java`.

- [ ] **Step 5: 编译**

```bash
cd log-analysis-backend
mvn -pl log-analysis-app -DskipTests compile
```

Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add log-analysis-backend/log-analysis-app/src/main/java/cn/mw/loganalysis/agent
git commit -m "refactor: move agent application and llm components"
```

---

## Task 9: 清理旧 service 包和旧引用

**Files:**
- Modify: any remaining Java files that still reference `cn.mw.loganalysis.agent.service`.
- Remove empty directory if Git has no tracked files under old package.

- [ ] **Step 1: 查找旧 service 引用**

```bash
cd /Users/xsw/custom_idea_project/qqwer
rg -n 'cn\.mw\.loganalysis\.agent\.service|package cn\.mw\.loganalysis\.agent\.service' log-analysis-backend/log-analysis-app/src/main/java log-analysis-backend/log-analysis-app/src/test/java
```

Expected: no output. If output exists, replace with the target package from the migration mapping.

- [ ] **Step 2: 查找旧 service 目录剩余文件**

```bash
find log-analysis-backend/log-analysis-app/src/main/java/cn/mw/loganalysis/agent/service -type f 2>/dev/null | sort
find log-analysis-backend/log-analysis-app/src/test/java/cn/mw/loganalysis/agent/service -type f 2>/dev/null | sort
```

Expected: no output. If files remain, move them according to the mapping above or document why they intentionally remain. This refactor expects no production files to remain in `agent/service`.

- [ ] **Step 3: 移除空目录**

```bash
rmdir log-analysis-backend/log-analysis-app/src/main/java/cn/mw/loganalysis/agent/service 2>/dev/null || true
rmdir log-analysis-backend/log-analysis-app/src/test/java/cn/mw/loganalysis/agent/service 2>/dev/null || true
```

- [ ] **Step 4: 编译**

```bash
cd log-analysis-backend
mvn -pl log-analysis-app -DskipTests compile
```

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add log-analysis-backend/log-analysis-app/src/main/java/cn/mw/loganalysis/agent log-analysis-backend/log-analysis-app/src/test/java/cn/mw/loganalysis/agent
git commit -m "refactor: remove legacy agent service package"
```

---

## Task 10: 完整验证

**Files:**
- No planned source edits unless verification exposes migration mistakes.

- [ ] **Step 1: 运行聚焦测试**

```bash
cd log-analysis-backend
mvn -pl log-analysis-app test -Dtest='*Agent*,*SqlCandidate*,*Text2Sql*'
```

Expected: PASS.

- [ ] **Step 2: 运行后端模块测试**

```bash
cd log-analysis-backend
mvn -pl log-analysis-app test
```

Expected: PASS. If tests unrelated to `agent` fail because local infrastructure is unavailable, record the failing class and exact failure, then run the focused tests from Step 1 again to confirm this refactor’s scope remains healthy.

- [ ] **Step 3: 检查无旧包引用**

```bash
cd /Users/xsw/custom_idea_project/qqwer
rg -n 'cn\.mw\.loganalysis\.agent\.service|package cn\.mw\.loganalysis\.agent\.service' log-analysis-backend/log-analysis-app/src/main/java log-analysis-backend/log-analysis-app/src/test/java
```

Expected: no output.

- [ ] **Step 4: 查看最终 diff**

```bash
git status --short
git diff --stat
```

Expected: only agent package moves/import changes and test package moves related to this refactor. Existing user changes that predated the refactor must not be reverted.

- [ ] **Step 5: Final commit if needed**

If Task 10 required small compile/test fixups, commit them:

```bash
git add log-analysis-backend/log-analysis-app/src/main/java/cn/mw/loganalysis/agent log-analysis-backend/log-analysis-app/src/test/java/cn/mw/loganalysis/agent
git commit -m "test: verify agent package refactor"
```

If no files changed during Task 10, do not create an empty commit.

---

## 自检清单

- 设计 spec 的每个包都在任务里有对应迁移步骤。
- 每个迁移任务都有明确文件列表、命令、编译或测试验证。
- 没有改变 Controller 路由、DTO、entity、mapper、repository。
- 没有要求重写算法。
- 没有要求重置或覆盖用户已有改动。
- 最终应无 `cn.mw.loganalysis.agent.service` 旧包引用。
