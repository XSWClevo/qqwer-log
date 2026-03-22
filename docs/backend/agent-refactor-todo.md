# Agent Refactor Todo

## Done

- [x] 收敛智能助手主流程，保留 `prepare -> choose executor -> finalize` 单一主线
- [x] 新增 `AgentSessionService`，统一处理 history hydrate、memory prepare、remember、history persist
- [x] 新增 `FallbackIntentDetector`，把规则回退的上下文补全和意图识别独立出来
- [x] 新增 `FallbackAgentExecutor`，让规则回退不再堆在 `LogAnalysisAgentService`
- [x] 新增 `AgentToolFacade`，让规则回退和 LangChain4j `@Tool` 共用一套工具实现
- [x] 拆分 schema / logs / timeseries / text2sql 四个 handler
- [x] 新增 `AgentResponseAssembler`，统一组装 `AgentChatResponse` 和 tool call 展示结构
- [x] 瘦身 `LogAnalysisAgentTools`，只保留 LangChain4j 所需的 `@Tool` 壳子
- [x] 瘦身 `LangChain4jLogAnalysisAgentExecutor`，只保留 prompt、模型调用、流式事件透传
- [x] 瘦身 `LogAnalysisAgentService`，去掉规则查询、时间解析、响应拼装等混杂职责
- [x] 后端编译验证通过：`mvn -q -pl log-analysis-app -am -DskipTests compile`

## New Structure

- `LogAnalysisAgentService`
  - 只做总入口编排
- `AgentSessionService`
  - 负责 session 生命周期
- `LangChain4jLogAnalysisAgentExecutor`
  - 负责 LLM 调用和流式事件
- `FallbackAgentExecutor`
  - 负责规则回退
- `AgentToolFacade`
  - 负责统一工具入口
- `SchemaToolHandler`
- `LogQueryToolHandler`
- `TimeSeriesToolHandler`
- `Text2SqlToolHandler`
- `AgentResponseAssembler`
  - 负责响应 DTO 组装

## Follow-up

- [ ] 为 `FallbackIntentDetector` 增加单元测试，覆盖上下文补全、关键词提取、意图判断
- [ ] 为 `AgentResponseAssembler` 增加单元测试，覆盖 tool execution -> response 映射
- [ ] 为 `AgentToolFacade` 四个 handler 增加最小回归测试
- [ ] 继续把 agent 包内零散常量和建议文案收口成独立配置
