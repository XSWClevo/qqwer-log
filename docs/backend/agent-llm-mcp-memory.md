# 智能助手 LLM / MCP / Memory 接入说明

最后更新：2026-03-19

## 1. 这套方案到底解决什么问题

当前“智能助手”不是一个纯聊天机器人，而是一套有明确职责边界的查询编排层。

目标分成四块：

1. 让用户在前端用自然语言提问
2. 让 LLM 判断应该调用什么能力
3. 对 ClickHouse 场景优先复用既有 `text2sql`，而不是在 agent 里手写 SQL
4. 用 `session + memory` 支撑多轮上下文理解，而不是每次都当成单轮问题

这次改造后的核心原则是：

- `LLM` 负责理解问题、选择工具、组织回答
- `text2sql` 负责把自然语言转成 SQL
- `MCP` 负责执行 ClickHouse 查询
- `Java 后端` 负责安全约束、工具封装、结果归一化、会话记忆

不要把这四层混在一起理解。

---

## 2. 当前整体架构

### 2.1 标准日志查询链路

适用场景：

- 查看字段结构
- 查某个时间范围的日志
- 看最近 24 小时趋势

链路：

```text
前端 /agent
  -> /api/agent/chat
  -> LogAnalysisAgentService
  -> LangChain4jLogAnalysisAgentExecutor
  -> @Tool(get_schema / query_logs / query_timeseries)
  -> DynamicLogQueryService
  -> 各数据源 QueryStrategy
  -> 返回结构化结果
```

### 2.2 ClickHouse 自然语言统计链路

适用场景：

- 最近 1 天的数据有多少条
- 按 severity 统计最近 24 小时数量
- 做临时聚合、排行、分组、图表

链路：

```text
前端 /agent
  -> /api/agent/chat
  -> LLM 或规则回退识别为 text2sql_query
  -> LogAnalysisAgentTools.text2SqlQuery()
  -> AiQueryService
  -> 外部 /text-to-sql
  -> DynamicLogQueryService.executeRawSQL()
  -> ClickHouseQueryStrategy.executeRawSQL()
  -> ClickHouse MCP 或 JDBC
  -> 结果归一化为 metric/category/timeseries/list
  -> 前端展示 SQL + 图表 + 表格
```

这里要注意：

- agent 不直接自己拼 SQL
- SQL 生成交给现有 `AiQueryService`
- SQL 执行阶段如果启用了 ClickHouse MCP，会自动走 MCP

也就是说，当前真正的链路是：

`自然语言 -> text2sql -> Java 执行层 -> ClickHouse MCP/JDBC`

---

## 3. LLM 在这套方案里的角色

LLM 不是数据库客户端，也不是 MCP client。

LLM 当前只做三件事：

1. 理解用户问题
2. 决定该调用哪个工具
3. 基于工具结果生成中文回答

### 3.1 LLM 入口

核心类：

- `log-analysis-backend/log-analysis-app/src/main/java/cn/mw/loganalysis/agent/service/LangChain4jLogAnalysisAgentExecutor.java`

这个类负责：

- 组装系统提示词
- 把当前问题和历史消息拼成 prompt
- 调用 LangChain4j 的 AiService 代理
- 把 tool execution 转成前端能直接渲染的 `toolCalls + result`

### 3.2 为什么 `assistant.chat(...)` 不是普通方法

`assistant.chat(...)` 对应的是 LangChain4j 生成的动态代理，不是手写实现类。

当前是通过 `AiServices.builder(...).build()` 创建的。

它做的事是：

1. 把 prompt 发给模型
2. 如果模型决定调工具，就执行 `@Tool`
3. 把工具结果继续喂回模型
4. 拿到最终回答文本和工具调用轨迹

### 3.3 系统提示词怎么控制行为

当前系统提示词写在：

- `log-analysis-backend/log-analysis-app/src/main/java/cn/mw/loganalysis/agent/service/LangChain4jLogAnalysisAgentExecutor.java`

重点约束有这些：

- 字段问题优先调用 `get_schema`
- 日志列表问题优先调用 `query_logs`
- 趋势问题优先调用 `query_timeseries`
- ClickHouse 的开放式统计问题优先调用 `text2sql_query`
- 不允许编造字段、数量、时间范围

这部分非常重要。后续你调 LLM 行为，优先看系统提示词，不要第一反应去改前端。

### 3.4 `@Tool` 和 `@P` 的作用

工具类：

- `log-analysis-backend/log-analysis-app/src/main/java/cn/mw/loganalysis/agent/service/LogAnalysisAgentTools.java`

`@Tool`

- 表示这个方法会暴露给模型当成“可调用工具”

`@P`

- 不是 Spring 注解
- 是 LangChain4j 的参数描述注解
- 作用是给模型解释“这个参数是什么、应该传什么格式”

如果 `@P` 写得差，模型工具调用质量会明显下降。

### 3.5 为什么有时 `llmExecutorProvider.getIfAvailable()` 会是空

这不是“接口没有动态实现”的问题。

真正原因通常只有几类：

- `agent.llm.enabled=false`
- 当前协议对应的模型 Bean 没创建出来
- 执行器条件不满足，没有注册到 Spring 容器

现在这块已经做成可选能力：

- 有 LLM 就走 LangChain4j
- 没 LLM 就回退规则版

这样服务不会因为模型没配好直接启动失败。

---

## 4. 为什么 ClickHouse 场景要复用 text2sql，而不是在 agent 里写死 SQL

这是本次改造里最重要的决策之一。

原因很直接：

1. 项目里已经有现成的 `AiQueryService`
2. 它已经支持把 `自然语言 + 表名 + 表结构 + 数据源类型` 发给 `/text-to-sql`
3. 再自己在 agent 里写一套 SQL 模板，只会制造第二份逻辑

相关类：

- `log-analysis-backend/log-analysis-app/src/main/java/cn/mw/loganalysis/stats/service/AiQueryService.java`
- `log-analysis-backend/log-analysis-app/src/main/java/cn/mw/loganalysis/stats/controller/StatsController.java`

所以当前 ClickHouse 的开放式统计查询，不再走“agent 自己写 SQL”，而是走：

- `LogAnalysisAgentTools.text2SqlQuery()`
- `AiQueryService.query()`

这条链的好处：

- schema 已经会传给 text2sql
- 数据源类型已知
- 生成 SQL 的逻辑只有一份
- 日志搜索页和智能助手页可以共享同一套 text2sql 能力

---

## 5. MCP 在这套方案里的角色

MCP 不是自然语言理解层，它只是工具执行层。

### 5.1 当前用的是官方 Java SDK

核心类：

- `log-analysis-backend/log-analysis-app/src/main/java/cn/mw/loganalysis/stats/service/query/ClickHouseMcpQueryService.java`

现在这里已经不是手写 JSON-RPC 了，而是：

- 官方 Java SDK
- `STDIO` transport
- `StdioClientTransport`
- `McpClient.sync(...)`

### 5.2 为什么仍然要传 SQL 给 MCP

因为官方 `mcp-clickhouse` 暴露的是数据库工具，例如：

- `run_select_query`
- `run_query`

它接收的本质还是 SQL。

MCP 解决的是：

- 怎么调用工具
- 怎么管理协议和进程通信

它不负责：

- 理解自然语言
- 自动决定业务字段
- 自动替你构造安全 SQL

所以现在必须是：

- 业务层或 text2sql 先产出 SQL
- MCP 再执行 SQL

### 5.3 为什么项目里用 STDIO 而不是固定 HTTP MCP 服务

因为你的数据源是动态的。

同一个后端实例下，不同会话可能会选不同 ClickHouse Sink。

所以当前设计是：

- 每次查询按当前数据源动态拉起一个 `mcp-clickhouse` 子进程
- 通过环境变量把当前 datasource 的 host/user/password/database 注进去

这样不需要为每个数据源维护一个长期在线的 MCP 连接。

---

## 6. ClickHouse 自然语言统计为什么能自动走 MCP

关键点在这里：

- `AiQueryService` 只负责生成 SQL 和发起执行
- 真正执行是在 `DynamicLogQueryService.executeRawSQL()`
- ClickHouse 的 `executeRawSQL()` 已经做了 MCP 分流

相关代码：

- `log-analysis-backend/log-analysis-app/src/main/java/cn/mw/loganalysis/stats/service/DynamicLogQueryService.java`
- `log-analysis-backend/log-analysis-app/src/main/java/cn/mw/loganalysis/stats/service/query/ClickHouseQueryStrategy.java`

所以只要满足：

- 当前数据源类型是 `clickhouse`
- `agent.mcp.clickhouse.enabled=true`
- MCP 可执行命令配置正确

那么 text2sql 生成出来的只读 SQL，执行时就会自动走 MCP。

这也是为什么这次不用重新发明第二套“agent 专用 ClickHouse 查询执行器”。

---

## 7. Session / Memory 现在是怎么做的

这次已经接入了真正的 `session + memory`。

核心类：

- `log-analysis-backend/log-analysis-app/src/main/java/cn/mw/loganalysis/agent/service/AgentConversationMemoryService.java`

### 7.1 设计目标

目标不是“永久存聊天记录”，而是：

- 让当前会话内的后续问题能理解上文
- 不要求前端每次都把整段对话重新发回来
- 保持实现足够轻，不先上数据库

### 7.2 当前实现方式

后端现在分两层：

- `Caffeine` 只负责按 `sessionId` 缓存当前会话对象
- 官方 `TokenWindowChatMemory` 负责真正的上下文窗口裁剪
- 完整历史仍然单独落 PostgreSQL
- 12 小时无访问自动过期

对应代码：

- `AgentConversationMemoryService.prepare(...)`
- `AgentConversationMemoryService.remember(...)`
- `AgentChatRequest.sessionId`
- `AgentChatResponse.sessionId`

需要特别注意：

- 现在已经不是“固定保留最近 N 条消息”
- 窗口大小改成 `agent.llm.memory.max-tokens`
- 裁剪维度是 token，不是消息条数

这意味着：

- 短消息可以保留更多轮
- 长消息会更早被淘汰
- prompt 体积更接近模型真实输入成本

前端做的事很少：

- 首次进入页面生成一个 `sessionId`
- 每次 `/api/agent/chat` 带上这个 `sessionId`
- 切换数据源时重置 session，避免跨数据源污染上下文

### 7.3 memory 如何影响 LLM

现在每次请求前，后端会先用 `sessionId` 找回当前 memory，把它回填到请求的 `history` 中。

然后 LLM executor 在构造 prompt 时，会把：

- 当前数据源
- 当前问题
- memory 输出的历史窗口

一起发给模型。

这里还有一个关键变化：

- 旧版本 executor 会再按“最近 6 条”额外截一次 history
- 现在这层重复裁剪已经去掉
- 也就是说，真正控制上下文大小的是 `TokenWindowChatMemory`

这意味着像下面这种多轮问题，LLM 能理解：

- 第一轮：搜索包含 "timeout" 的日志
- 第二轮：那最近24小时呢

### 7.4 memory 如何影响规则回退

规则版也做了最小上下文增强。

如果当前问题明显是跟进式表达，比如：

- 那最近7天呢
- 再看看这个
- 改成按 severity 呢

后端会把它和最近一条 user 消息合并后再做规则解析。

这样即使 LLM 暂时不可用，基础的多轮跟进也还能工作。

### 7.5 `sessionId` 生命周期

这里要分清楚：

- `history` 不是长期存储协议
- `sessionId` 才是服务端持续记忆的主键

当前请求流程是：

1. 前端首次打开智能助手页面时生成一个 `sessionId`
2. 后端收到请求后，先按 `sessionId` 取最近几轮消息
3. 如果这是一个新 session，才会用前端传入的 `history` 做一次 bootstrap
4. 当前轮执行完成后，后端把 `user message + assistant answer` 记回 `TokenWindowChatMemory`
5. 后续同一个 `sessionId` 的请求，不再依赖前端每次回放整段历史

这也是为什么现在切换数据源时，前端会主动重置 `sessionId`：

- 避免把 A 数据源的上下文带到 B 数据源
- 避免 LLM 在错误的 schema 语义上继续推理

### 7.6 为什么 memory 不直接保存完整结果对象

当前 memory 只保存轻量文本，不保存完整的 `toolCalls/result/rawResult`，这是刻意设计。

原因有三个：

1. 工具结果可能很大，尤其是 `text2sql` 的行数据和图表数据
2. 真正需要给模型做上下文理解的，通常是“上一轮问了什么、回答了什么”，不是整份原始结果 JSON
3. 先把 memory 做成轻量窗口缓存，后面再升级成“摘要记忆 + 最近消息”会更稳

所以现在 memory 的目标是：

- 支撑跟进式问题理解
- 控制 prompt 体积
- 不把服务端缓存变成结果仓库

---

## 8. 当前 agent 支持哪些工具

定义位置：

- `log-analysis-backend/log-analysis-app/src/main/java/cn/mw/loganalysis/agent/service/LogAnalysisAgentTools.java`

当前有四个主要工具：

### 8.1 `get_schema`

用途：

- 读字段结构
- 识别时间字段、统计维度、内容字段

### 8.2 `query_logs`

用途：

- 查日志列表
- 支持时间范围、关键词、日志级别

### 8.3 `query_timeseries`

用途：

- 查整体趋势
- 返回前端可直接画图的时序数据

### 8.4 `text2sql_query`

用途：

- 仅限 ClickHouse
- 处理开放式统计、聚合、排行、分组、图表类问题
- 复用既有 text2sql 服务生成 SQL
- 执行时自动走 MCP 或 JDBC

---

## 9. 前端现在是怎么渲染这些结果的

主要页面：

- `log-analysis-frontend/src/views/agent/Index.vue`

当前结果类型分两层：

### 9.1 顶层 agent result type

- `schema`
- `logs`
- `timeseries`
- `text2sql`

### 9.2 `text2sql` 下的 query result type

- `metric`
- `category`
- `timeseries`
- `list`

这样前端能同时做到：

- 普通 agent 结果按固定区域展示
- text2sql 结果继续复用已有的 `AiQueryResultCard`
- 同时补一个动态表格展示原始结果行

相关前端类型：

- `log-analysis-frontend/src/api/agent.ts`

---

## 10. 配置项怎么理解

### 10.1 LLM 配置

核心配置前缀：

- `agent.llm.*`
- `langchain4j.open-ai.chat-model.*`

常见配置：

- `agent.llm.enabled`
- `agent.llm.fallback-on-error`
- `agent.llm.memory.max-tokens`
- `langchain4j.open-ai.chat-model.api-key`
- `langchain4j.open-ai.chat-model.base-url`
- `langchain4j.open-ai.chat-model.model-name`
- `langchain4j.open-ai.chat-model.wire-api`

### 10.2 ClickHouse MCP 配置

核心配置前缀：

- `agent.mcp.clickhouse.*`

常见配置：

- `enabled`
- `fallback-to-jdbc-on-error`
- `executable`
- `arguments`
- `request-timeout`
- `startup-timeout`

---

## 11. 你后面学习这块代码，推荐从哪里开始读

建议顺序：

1. `LangChain4jLogAnalysisAgentExecutor`
   先理解 LLM 到底怎么接入
2. `LogAnalysisAgentTools`
   再理解工具层是什么、哪些地方是模型可调用边界
3. `AiQueryService`
   再看 text2sql 为什么能复用
4. `ClickHouseMcpQueryService`
   再看官方 MCP Java SDK 是怎么接的
5. `AgentConversationMemoryService`
   最后看 session/memory 怎么把多轮对话接起来

不要反过来先看前端，否则很容易把“展示逻辑”和“执行逻辑”混掉。

---

## 12. 常见误区

### 误区 1：用了 MCP，就不用自己传 SQL

不对。

MCP 只负责工具调用协议，不负责自然语言理解。

### 误区 2：LLM 就是直接查数据库

不对。

LLM 只负责理解问题和调用工具，真正查数仍然在 Java 后端和 MCP。

### 误区 3：text2sql 和 MCP 是二选一

不对。

在当前方案里它们是串联关系：

- `text2sql` 负责生成 SQL
- `MCP` 负责执行 SQL

### 误区 4：session memory 等于永久聊天记录

不对。

当前只是服务端会话记忆，目标是支撑多轮理解，不是做长期归档。

---

## 13. 后续可继续演进的方向

### 13.1 更强的 memory

当前是“最近几轮窗口记忆”，后面可以继续加：

- session summary
- topic slots
- 用户偏好记忆
- datasource 级语义记忆

### 13.2 邮件/报表能力

你前面提到“查完数据生成图表然后发邮箱”，下一步建议做成：

- `prepare_email_report`
- 用户确认
- `send_email`

不要让模型直接发。

### 13.3 更细粒度的 ClickHouse MCP 工具

后面如果需要，可以把 ClickHouse MCP 再细化成：

- `list_tables`
- `describe_table`
- `run_select_query`

然后让 LLM 在 agent 内部更自由地规划查询。

但当前这版先复用 `text2sql`，是更稳的选择。

---

## 14. 一句话总结

当前智能助手的正确理解方式是：

`LLM 负责理解和编排，text2sql 负责生成 SQL，MCP 负责执行 ClickHouse 查询，session memory 负责让多轮对话有上下文。`

这四层现在已经分开了，后面维护时尽量不要再把职责重新搅在一起。
