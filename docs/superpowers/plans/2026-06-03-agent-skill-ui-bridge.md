# Agent Skill UI Bridge Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在组件库页接入一个轻量 Agent 入口，并把页面上下文透传到后端，让 skill registry 可以区分“组件库里的创建解析组件”与普通智能助手对话。

**Architecture:** 保留现有 `SmartWizard` 作为旧向导路径，新增组件库页右侧助手抽屉作为 Agent 路径。前端只负责发送 `pageContext`、`routePath` 和可选 `skillId`，后端在进入现有意图识别前补齐并保留这些字段，默认缺省时继续走原逻辑。

**Tech Stack:** Vue 3, Element Plus, TypeScript, Spring Boot, Lombok, existing Agent / wizard services

---

### Task 1: Extend Frontend Agent Request and Component Library Entry

**Files:**
- Modify: `log-analysis-frontend/src/api/agent.ts`
- Modify: `log-analysis-frontend/src/views/vector/ComponentLibrary.vue:1-1722`

- [ ] **Step 1: Add the failing shape at the API boundary**

Make `AgentChatRequest` accept `pageContext`, `routePath`, `skillId`, and optional `surfaceContext`, then update the call sites in `ComponentLibrary.vue` so the new assistant entry can send those fields.

- [ ] **Step 2: Wire a new assistant trigger in the component library**

Add a second action next to `智能向导`, open a lightweight drawer/panel, and keep the old `SmartWizard` untouched.

- [ ] **Step 3: Send component-library context with chat requests**

When the drawer submits a message, send `pageContext: 'COMPONENT_LIBRARY'`, `routePath: '/vector/components'`, and a stable `skillId` for log-parser creation.

- [ ] **Step 4: Verify the page still compiles**

Run: `cd log-analysis-frontend && npm run build`
Expected: build succeeds and the component library still renders.

### Task 2: Preserve New Context in Backend Agent Request Flow

**Files:**
- Modify: `log-analysis-backend/log-analysis-app/src/main/java/cn/mw/loganalysis/agent/dto/AgentChatRequest.java`
- Modify: `log-analysis-backend/log-analysis-app/src/main/java/cn/mw/loganalysis/agent/conversation/AgentConversationMemoryService.java:68-92`
- Modify: `log-analysis-backend/log-analysis-app/src/main/java/cn/mw/loganalysis/agent/execution/AgentRuntimeContext.java`
- Modify: `log-analysis-backend/log-analysis-app/src/main/java/cn/mw/loganalysis/agent/execution/AgentFallbackWorkflow.java:41-121`

- [ ] **Step 1: Add the failing request fields**

Extend `AgentChatRequest` with the same front-end context fields so the controller can deserialize them.

- [ ] **Step 2: Preserve the fields while preparing session memory**

Copy the new fields into the `effectiveRequest` created inside `AgentConversationMemoryService.prepare(...)`.

- [ ] **Step 3: Make runtime context carry the new metadata**

Add the fields to `AgentRuntimeContext` and populate them in `AgentFallbackWorkflow.prepareContext(...)` so downstream skill matching can read them.

- [ ] **Step 4: Keep the old paths unchanged when context is absent**

Do not change the existing intent or tool execution behavior unless the new fields are present.

- [ ] **Step 5: Verify backend compile and focused agent tests**

Run: `mvn -pl log-analysis-app -DskipTests compile`
Then run: `mvn -pl log-analysis-app test -Dtest='*Agent*,*SqlCandidate*,*Text2Sql*'`
Expected: both pass.

### Task 3: Final Visual and Behavior Check

**Files:**
- Verify: `log-analysis-frontend/src/views/vector/ComponentLibrary.vue`
- Verify: `log-analysis-frontend/src/views/agent/Index.vue`

- [ ] **Step 1: Confirm the new entry feels native**

Open the component library and confirm the new assistant entry reads as a task tool, not a marketing banner.

- [ ] **Step 2: Confirm fallback behavior**

Submit a normal message from `/agent` without any skill context and verify it still follows the old intent flow.

- [ ] **Step 3: Confirm context-aware behavior**

Submit from the component library entry and verify the request carries the component-library context fields.

