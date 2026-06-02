# Agent Layering Refactor Design

## Context

The `cn.mw.loganalysis.agent.service` package currently contains most of the agent implementation. It mixes orchestration, LLM integration, JSON NLU, intent matchers, fallback execution, tool handlers, Text2SQL candidate generation, Vector component planning, session memory, streaming, and email/reporting support in one package.

This makes the agent module hard to navigate and raises the cost of changing one capability without disturbing unrelated behavior. The refactor should introduce clear package boundaries and service entry points while preserving the current API behavior.

## Goals

- Move agent implementation classes into purpose-based packages under `cn.mw.loganalysis.agent`.
- Keep controller routes, request/response DTOs, database entities, mappers, repositories, and external behavior unchanged.
- Keep the existing LLM, fallback, Text2SQL, Vector component plan, memory, email, and stream behavior intact.
- Make the main application flow readable from package names without reading every class.
- Introduce clear application-facing service boundaries where the current service package already implies separate responsibilities.

## Non-Goals

- Do not rewrite the LLM prompt strategy.
- Do not replace LangChain4j function/tool calling.
- Do not replace the JSON NLU path.
- Do not redesign Text2SQL candidate racing.
- Do not split large algorithmic classes such as `VectorComponentPreviewPlanner`, `SqlCandidateValidator`, or `AgentConversationHistoryService` in this first pass.
- Do not change frontend API response shapes or controller paths.

## Proposed Package Structure

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

Owns application-level use cases exposed to controllers.

Classes:

- `LogAnalysisAgentService`

Responsibilities:

- Prepare a chat request through conversation/session services.
- Select deterministic fallback execution or LangChain4j execution.
- Apply fallback-on-error behavior.
- Finalize conversation memory/history.

### `conversation`

Owns session, memory, and persisted conversation history.

Classes:

- `AgentSessionService`
- `AgentConversationMemoryService`
- `AgentConversationHistoryService`

Responsibilities:

- Normalize and prepare session IDs.
- Maintain short-lived in-process memory.
- Persist and retrieve conversation history.

### `execution`

Owns runtime context, fallback orchestration, execution context, flow events, and local execution registry.

Classes:

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

Responsibilities:

- Build the per-request runtime context.
- Run local fallback workflow.
- Publish lifecycle events.
- Route resolved intents to concrete local tool executors.

### `llm`

Owns LangChain4j integration and model-driven tool calling.

Classes:

- `LangChain4jLogAnalysisAgentExecutor`
- `LangChain4jLogAnalysisAssistant`
- `LangChain4jStreamingLogAnalysisAssistant`
- `LogAnalysisAgentTools`

Responsibilities:

- Build the LLM system prompt.
- Register LangChain4j `@Tool` methods.
- Execute synchronous and streaming model calls.
- Convert model tool-call events into stream events through the shared response assembler.

### `nlu`

Owns intent definitions, JSON NLU, rule-based intent matching, and slot models.

Classes:

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

Responsibilities:

- Convert user text into an executable internal intent.
- Parse model-provided JSON NLU results.
- Apply rule-based fallbacks and safety overrides.

### `tool`

Owns the unified tool facade, shared tool payload, response assembly, and concrete non-LLM tool handlers/executors.

Classes:

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

Responsibilities:

- Provide one internal surface for both fallback execution and LangChain4j `@Tool` execution.
- Convert tool payloads and LangChain4j tool executions into frontend response DTOs.
- Execute basic schema/logs/timeseries/text2sql/vector-plan tools.

### `text2sql`

Owns Text2SQL candidate generation, validation, and racing.

Classes:

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

Responsibilities:

- Generate SQL candidates from history, templates, and the external AI service.
- Validate generated SQL before execution.
- Race candidates and select the first safe executable result.

### `vectorplan`

Owns Vector component planning, slot extraction, source config extraction, and commit artifacts.

Classes:

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

Responsibilities:

- Collect required slots for parser/component creation.
- Preview Vector Remap/Sink plans.
- Commit approved Vector component plans.

### `notification`

Owns agent-triggered email/report notifications.

Classes:

- `AgentEmailService`

Responsibilities:

- Render and send current-user email reports.
- Keep report/email formatting separate from agent orchestration.

### `support`

Owns cross-cutting helpers that do not belong to a specific capability package.

Classes:

- `AgentToolSupport`
- `AgentTimeWindow`
- `AgentStreamEventEmitter`
- `AgentStreamWriter`
- `SlotResult`

Responsibilities:

- Provide shared parsing/truncation/time-window/stream utilities.
- Avoid placing generic helpers in domain-specific packages.

## Data Flow

### LLM Tool-Calling Flow

```text
AgentController
  -> application.LogAnalysisAgentService
  -> llm.LangChain4jLogAnalysisAgentExecutor
  -> llm.LogAnalysisAgentTools
  -> tool.AgentToolFacade
  -> concrete tool handler
  -> downstream stats/vector services
  -> tool.AgentResponseAssembler
```

### Deterministic/Fallback Flow

```text
AgentController
  -> application.LogAnalysisAgentService
  -> execution.FallbackAgentExecutor
  -> execution.AgentFallbackWorkflow
  -> nlu.IntentDecision or nlu.AgentIntentRecognitionService
  -> execution.AgentFallbackToolExecutorRegistry
  -> tool.*ToolIntentExecutor
  -> tool.AgentToolFacade
  -> concrete tool handler
  -> tool.AgentResponseAssembler
```

### Text2SQL Flow

```text
tool.Text2SqlToolHandler
  -> text2sql.SqlCandidateRaceService
  -> text2sql.HistorySqlCandidateProvider
  -> text2sql.TemplateSqlCandidateProvider
  -> text2sql.LlmSqlCandidateProvider
  -> stats.AiQueryService
  -> stats.DynamicLogQueryService
```

### Vector Plan Flow

```text
tool.VectorComponentPlanToolHandler
  -> vectorplan.VectorComponentPreviewPlanner
  -> vectorplan.CreateLogParser* slot/source helpers
  -> vectorplan.VectorComponentPlanStore
  -> vectorplan.VectorComponentCommitService
```

## Visibility Rules

- Controller-facing services should be `public`.
- Classes used across package boundaries must be `public`.
- Internal strategy interfaces can stay package-private only when all implementations and consumers remain in the same package.
- Moving classes out of `service` will require making several currently package-private records/interfaces public, including intent and execution model types where they cross package boundaries.
- Existing user changes must be preserved, including the current public visibility change to `AgentRuntimeContext`.

## Migration Strategy

1. Move tests first or alongside production packages so package-private assumptions are visible immediately.
2. Move one capability group at a time:
   - execution/context/fallback
   - nlu/intent
   - tool facade and handlers
   - text2sql
   - vectorplan
   - conversation
   - llm
   - notification/support
3. Compile after each major group move.
4. Keep class names stable to reduce behavioral risk.
5. Do not introduce new algorithms during this pass.

## Test Strategy

Run focused tests after package migration:

```bash
mvn -pl log-analysis-app test -Dtest='*Agent*,*SqlCandidate*,*Text2Sql*'
```

If Maven module resolution requires running from the backend parent, use:

```bash
cd log-analysis-backend
mvn -pl log-analysis-app test -Dtest='*Agent*,*SqlCandidate*,*Text2Sql*'
```

Expected coverage:

- Text2SQL candidate race behavior remains unchanged.
- SQL validation behavior remains unchanged.
- Text2SQL tool handler response shape remains unchanged.
- LLM SQL candidate provider still calls `AiQueryService.generateSqlOnly`.
- Agent package compiles with controller/config imports updated.

## Risks

- Moving package-private interfaces across packages can force visibility changes.
- Tests that relied on package-private access may need to move with the package or use public APIs.
- Spring component scanning should still work because all target packages remain under `cn.mw.loganalysis`.
- Import churn is large even though behavior should not change.

## Acceptance Criteria

- The `agent/service` package no longer contains all agent implementation classes.
- New package names reflect responsibilities clearly.
- `AgentController` still depends on stable application-level services.
- Existing public routes and DTOs are unchanged.
- Focused agent/Text2SQL tests pass.
- No unrelated files or generated artifacts are modified.
