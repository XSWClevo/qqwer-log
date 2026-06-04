# Vector Package Platform Compatibility Design

## Background

当前安装包链路存在两类平台兼容问题：

1. 前端安装包管理页只能上传 macOS 包，Linux 和 amd64 选项被禁用，导致无法在平台中登记 Ubuntu 安装包。
2. 后端安装包下载和“最新包”查询链路对平台标识不统一，`x86_64`、`amd64`、`arm64`、`aarch64` 混用；当 macOS、Linux 包同时存在时，部分接口会查不到包或命中错误默认值。

这些问题直接影响：

- Ubuntu Agent 的一键安装
- Bundle 升级包查询
- macOS / Linux 共存时的安装包分发正确性

## Goals

- 允许前端上传 Linux / macOS、amd64 / arm64 的 Bundle 包。
- 统一后端平台查询键，保证 `packageType + osType + arch` 维度各自独立维护“最新包”。
- 修复 Agent 下载接口和升级查询中的错误默认平台，避免多平台共存时查错包。
- 为关键平台映射和多平台共存场景补回归验证。

## Non-Goals

- 不调整安装包数据库表结构。
- 不引入新的安装包类型模型。
- 不处理机器架构自动探测的完整改造，本次仅修复现有默认值和归一化逻辑。

## Options Considered

### Option A: 最小修复现有模型

- 保留现有 `VectorPackage` 表和 `is_latest` 模型。
- 前后端统一使用归一化后的 `osType` / `arch` 查询。
- 对外接口兼容 `x86_64`、`aarch64` 输入，但服务层统一转换成 `amd64`、`arm64`。

优点：

- 改动小，风险最低
- 不需要数据库变更
- 能直接修复当前上传、下载、升级链路

缺点：

- 仍然依赖调用方显式传入平台参数

### Option B: 仅修前端上传枚举

- 开放 Linux / amd64 选项
- 不改后端归一化和默认值

优点：

- 前端改动最少

缺点：

- Linux 包上传后仍可能被下载接口查不到
- 无法解决 macOS / Linux 共存时的错误查包问题

### Option C: 重构安装包版本选择模型

- 增加更复杂的平台版本索引或单独最新版本表
- 重新设计升级和下载流程

优点：

- 长期扩展性更强

缺点：

- 超出当前问题范围
- 风险和改动量过大

## Chosen Approach

采用 Option A。

这是当前最稳妥的修复方式：在不改变数据库模型的前提下，统一平台枚举和查询键，修复上传、下载、升级三条链路的行为一致性。

## Design

### 1. Frontend Package Upload

修改安装包管理页：

- 开放 `Linux` 和 `amd64` 选项，不再禁用。
- OS 展示文案显式区分 `darwin -> macOS`、`linux -> Linux`。
- 架构展示文案统一使用 `amd64`、`arm64`。

目标文件：

- `log-analysis-frontend/src/views/vector/PackageManager.vue`

### 2. Backend Platform Normalization

在安装包服务层引入平台归一化逻辑：

- `x86_64`、`amd64` 统一映射到 `amd64`
- `aarch64`、`arm64` 统一映射到 `arm64`
- `macos` 统一映射到 `darwin`（如果当前链路存在该输入）
- 空值使用安全默认值：`osType=linux`、`arch=amd64`

归一化逻辑应用到：

- 上传安装包
- 查询最新安装包
- Agent 下载 Bundle
- 升级命令查包

目标文件：

- `log-analysis-backend/log-analysis-app/src/main/java/cn/mw/loganalysis/vector/service/VectorPackageService.java`
- `log-analysis-backend/log-analysis-app/src/main/java/cn/mw/loganalysis/vector/controller/VectorPackageController.java`
- `log-analysis-backend/log-analysis-app/src/main/java/cn/mw/loganalysis/vector/controller/VectorAgentController.java`

### 3. Download And Latest Package Resolution

统一 `/api/vector/packages/latest`、`/api/vector/packages/download-bundle`、`/api/vector/agents/download` 的查包行为：

- 总是按 `packageType + normalized osType + normalized arch + isLatest=true` 查询
- 不再依赖错误默认值，例如 `darwin/arm64` 或 `linux/arm64`
- `/api/vector/agents/download` 保留 fallback 逻辑，但优先使用包管理系统中匹配平台的最新 Bundle

这样在同时存在：

- `vector-agent-bundle / darwin / arm64`
- `vector-agent-bundle / linux / amd64`

时，两条下载链路会各自返回正确平台的最新包，不会互相冲突。

### 4. Install Script Compatibility

修正安装脚本中的架构映射：

- `uname -m = x86_64` 时向后端传 `amd64`
- `uname -m = aarch64` 时向后端传 `arm64`

目标文件：

- `log-analysis-backend/log-analysis-app/src/main/resources/scripts/install-agent.sh`

### 5. Upgrade Path Consistency

修复机器升级查询中写死平台默认值的问题：

- 不再默认 `machine.osType || 'darwin'`
- 不再写死 `arch = 'arm64'`
- 至少改为与当前安装下载逻辑一致的默认值 `linux/amd64`
- 优先复用服务层归一化逻辑，减少前端重复判断

目标文件：

- `log-analysis-frontend/src/views/vector/MachineList.vue`
- `log-analysis-backend/log-analysis-app/src/main/java/cn/mw/loganalysis/vector/controller/VectorPackageController.java`

## Testing Strategy

### Backend

- 为平台归一化增加单元测试：
  - `x86_64 -> amd64`
  - `aarch64 -> arm64`
  - `amd64 -> amd64`
  - `arm64 -> arm64`
- 为“最新包查询”增加回归测试：
  - macOS arm64 和 Linux amd64 同时存在时，各自查询命中正确包

### Frontend

- 验证上传弹窗可选择：
  - macOS / Linux
  - arm64 / amd64
- 验证安装包列表正确显示系统和架构

### End-to-End Spot Check

- 上传一份 macOS Bundle 和一份 Linux Bundle
- 访问：
  - `/api/vector/packages/latest?packageType=vector-agent-bundle&osType=darwin&arch=arm64`
  - `/api/vector/packages/latest?packageType=vector-agent-bundle&osType=linux&arch=amd64`
- 确认两者返回不同且正确的最新包

## Risks

- 如果数据库中已存在历史记录使用 `x86_64` 作为 `arch`，归一化后查询可能需要兼容旧数据读取。
- 当前机器实体未完整保存 CPU 架构，升级链路仍可能依赖默认值；本次只做兼容性修复，不彻底解决机器架构建模问题。

## Implementation Scope

本次只做以下范围：

- 上传页开放 Linux / amd64
- 服务端平台归一化
- 下载接口和升级链路默认值修复
- 回归测试补充

不扩展到新的表结构、安装包类型或机器硬件建模。
