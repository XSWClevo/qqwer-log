# Agent 内置 Skill Registry 设计

## 背景

当前 Agent 的意图识别主要依赖 `nlu` 里的关键词和正则匹配。对用户而言，页面上出现的词是“组件库”“日志解析”“生成组件”“确认创建”，但后端内部的实现概念是 `VECTOR_COMPONENT_PLAN`、`CREATE_LOG_PARSER`、`Remap/Sink`、`Vector`。

这会导致两个问题：

1. 用户只说“组件库配置”时，后端很容易靠泛词误判。
2. 意图识别和页面文案耦合，前端改词后，后端 matcher 容易失效。

## 目标

- 用用户能理解的业务能力名来描述 Agent 能做什么。
- 让“页面上下文 + 用户输入 + 当前会话状态”一起参与意图识别。
- 保持现有 `CREATE_LOG_PARSER`、`VECTOR_COMPONENT_PLAN`、现有工具和工作流不变。
- 先做一个可维护的能力注册层，不引入新的 LLM 调用链。

## 非目标

- 不重写现有 Vector 预览/创建逻辑。
- 不改变 LangChain4j 工具调用方式。
- 不把所有意图都交给模型自由判断。

## 核心方案

新增一个“内置 Skill Registry”层，放在 `nlu` 和工作流之间。

### 三层结构

1. 用户可见 Skill
   - 用业务词描述能力，例如“创建日志解析组件”“预览组件配置”“生成解析规则”。
2. 内部 Intent
   - Skill 再映射到现有内部意图，例如 `CREATE_LOG_PARSER`、`VECTOR_COMPONENT_PLAN`。
3. Tool / Workflow
   - 最终仍然落到现有 handler、service、tool executor。

### Skill 定义

每个 skill 至少包含：

- `id`
- `displayName`
- `aliases`
- `positiveSignals`
- `negativeSignals`
- `requiredContexts`
- `targetIntent`
- `deterministicToolRequest`
- `clarificationMessage`

### 首批内置 Skill

#### 1. 创建日志解析组件

- `id`: `create_log_parser_component`
- `displayName`: `创建日志解析组件`
- `aliases`: `组件库配置`、`创建组件`、`创建日志解析`、`生成解析组件`、`日志解析组件`、`生成正则`、`入库`、`建表`、`采集日志`
- `requiredContexts`: `日志`、`解析`、`样本`、`正则`、`入库`、`建表`
- `targetIntent`: `CREATE_LOG_PARSER`
- `deterministicToolRequest`: `true`

#### 2. 预览组件配置

- `id`: `preview_vector_component_plan`
- `displayName`: `预览组件配置`
- `aliases`: `生成组件预览`、`预览组件`、`vector 组件`、`remap`、`sink`、`组件预览`
- `requiredContexts`: `日志样本`、`组件库`、`vector`、`remap`、`sink`
- `targetIntent`: `VECTOR_COMPONENT_PLAN`
- `deterministicToolRequest`: `true`

#### 3. 兜底澄清

- 当只有“组件”“配置”这类泛词，没有页面上下文和业务上下文时，返回澄清问题，而不是直接命中 Vector 技能。

## 上下文输入

新增前端到后端的上下文字段：

- `routePath`
- `pageContext`
- 可选 `surfaceContext`

这些字段从前端当前页面直接带给 Agent，请求里作为低成本上下文，不依赖模型猜测。

### 推荐 pageContext

- `COMPONENT_LIBRARY`
- `LOG_PARSER_WIZARD`
- `AGENT_CHAT`
- `VECTOR_EDITOR`
- `UNKNOWN`

## 后端设计

### 新包

建议新增：

- `cn.mw.loganalysis.agent.skill`

### 新类

- `AgentSkillDefinition`
- `AgentSkillRegistry`
- `AgentSkillMatcher`
- `AgentSkillDecision`
- `AgentSkillContext`

### 识别流程

1. `AgentChatRequest` 带入 `pageContext` / `routePath`。
2. `AgentContextEnhancerChain` 把页面上下文写进 `AgentRuntimeContext`。
3. `AgentSkillMatcher` 用技能定义做匹配和打分。
4. 命中的 skill 映射成现有内部 intent。
5. `AgentFallbackWorkflow` 继续沿用现有执行链路，不改工具层。

## 匹配规则

建议从“布尔命中”改成“弱打分 + 明确阈值”。

### 打分来源

- 用户关键词命中
- 页面上下文命中
- 当前会话是否处于补槽状态
- 是否出现排除词

### 规则示例

- “帮我创建组件库配置”
  - `COMPONENT_LIBRARY` 页面下：命中 `create_log_parser_component`
  - 无页面上下文：进入澄清
- “根据这条日志样本生成组件”
  - 命中 `preview_vector_component_plan`
- “创建组件”
  - 单独出现时不直接命中 Vector，必须再看上下文

## 前端配合

Agent 页面、组件库页面、日志解析向导页面发请求时，统一带上当前路由和页面类型。

这样后端不需要从一句“组件库配置”推断用户在哪个页面，也不用让用户先学 Vector 术语。

## 测试

至少补这些测试：

- `组件库配置 + COMPONENT_LIBRARY` -> `CREATE_LOG_PARSER`
- `组件库配置 + 无上下文` -> 澄清
- `日志样本 + 生成组件` -> `VECTOR_COMPONENT_PLAN`
- `创建组件` 单独出现 -> 不直接误判
- 现有 `CREATE_LOG_PARSER`、`VECTOR_COMPONENT_PLAN` 回归测试继续通过

## 迁移顺序

1. 新增 skill 包和数据结构
2. 给 `AgentChatRequest` / `AgentRuntimeContext` 加页面上下文
3. 改造 matcher，把硬编码关键词收进 skill registry
4. 接通前端 route/pageContext
5. 补测试

## 风险

- 如果页面上下文不传，仍然可能出现泛词误判。
- 如果 skill 定义过宽，会把“前端组件库”和“日志解析组件”混淆。
- 如果 skill 定义过窄，用户会频繁看到澄清问题。

## 结论

这套方案的核心不是“增加一个新意图”，而是把用户语言层和内部实现层分开。  
这样“组件库配置”这种说法可以根据页面和上下文，稳定映射到真正的日志解析/组件预览能力。
