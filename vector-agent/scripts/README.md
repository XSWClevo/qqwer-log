# Vector Agent 构建和部署脚本

## 脚本说明

### 1. `download-vector.sh` - Vector 下载脚本

自动检测系统架构并下载最新版本的 Vector。

**功能特性**：
- ✅ 自动检测系统架构（macOS arm64/x86_64, Linux arm64/x86_64）
- ✅ 自动获取最新版本（从 GitHub API）
- ✅ 智能匹配下载链接（根据实际 Release assets）
- ✅ 检查系统已安装的 Vector（`/opt/vector-agent/bin/vector`）
- ✅ 支持指定版本和目标平台（用于交叉编译）
- ✅ 自动解压并安装到 `bin/` 目录

**使用方法**：

```bash
# 下载最新版本（自动检测当前系统）
./scripts/download-vector.sh

# 如果系统已安装 Vector，会提示是否跳过下载
# 输入 Y 或直接回车：使用已安装的版本
# 输入 n：下载新版本

# 下载指定版本
./scripts/download-vector.sh 0.52.0

# 为指定平台下载（用于交叉编译）
./scripts/download-vector.sh latest darwin arm64
./scripts/download-vector.sh latest linux x86_64
```

**支持的平台**：

| 操作系统 | 架构 | Vector 文件名模式 | 说明 |
|---------|------|------------------|------|
| macOS | arm64 (Apple Silicon) | *-arm64-apple-darwin.tar.gz | ✅ 官方支持 |
| macOS | x86_64 (Intel) | *-arm64-apple-darwin.tar.gz | ⚠️ 使用 arm64 版本（Rosetta 2） |
| Linux | arm64 | *-aarch64-unknown-linux-musl.tar.gz | ✅ 官方支持 |
| Linux | x86_64 | *-x86_64-unknown-linux-musl.tar.gz | ✅ 官方支持 |

**注意事项**：
- macOS Intel (x86_64) 机器会自动下载 arm64 版本，通过 Rosetta 2 运行
- Linux arm64 使用 `aarch64` 命名（Vector 官方命名规范）

**下载链接解析**：

脚本会从 GitHub API 获取最新 Release 信息，并根据系统架构自动匹配正确的下载链接：

```json
{
  "tag_name": "v0.52.0",
  "assets": [
    {
      "name": "vector-0.52.0-arm64-apple-darwin.tar.gz",
      "browser_download_url": "https://github.com/vectordotdev/vector/releases/download/v0.52.0/vector-0.52.0-arm64-apple-darwin.tar.gz"
    },
    ...
  ]
}
```

---

### 2. `build-bundle.sh` - Bundle 构建脚本

打包 vector-agent + vector 为一体化安装包，支持多平台构建。

**功能特性**：
- ✅ 自动下载 Vector（如果不存在）
- ✅ 编译 vector-agent
- ✅ 打包为 tar.gz 格式
- ✅ 支持多平台交叉编译
- ✅ 生成版本信息文件

**使用方法**：

```bash
# 构建当前系统平台（默认版本 1.0.0）
./scripts/build-bundle.sh

# 指定版本号
./scripts/build-bundle.sh 1.2.3

# 为指定平台构建
./scripts/build-bundle.sh 1.2.3 darwin arm64    # macOS Apple Silicon
./scripts/build-bundle.sh 1.2.3 darwin x86_64   # macOS Intel
./scripts/build-bundle.sh 1.2.3 linux arm64     # Linux ARM64
./scripts/build-bundle.sh 1.2.3 linux x86_64    # Linux x86_64
```

**输出文件**：

```
dist/vector-agent-bundle-{VERSION}-{OS}-{ARCH}.tar.gz
```

**Bundle 内容**：

```
vector-agent-bundle/
├── bin/
│   ├── vector-agent    # Agent 二进制
│   └── vector          # Vector 二进制
├── install.sh          # 安装脚本
└── VERSION             # 版本信息
```

---

### 3. `dev-deploy.sh` - 本地开发部署脚本

编译并部署 vector-agent 和 vector 到本地 `/opt/vector-agent/bin`，用于开发调试。

**功能特性**：
- ✅ 自动下载 Vector（如果不存在）
- ✅ 编译 vector-agent
- ✅ 自动创建目录结构
- ✅ 部署到系统目录
- ✅ 自动处理权限问题

**使用方法**：

```bash
# 编译并部署
./scripts/dev-deploy.sh

# 重启服务使更改生效
# macOS:
sudo launchctl bootout system /Library/LaunchDaemons/com.vector.agent.plist
sudo launchctl bootstrap system /Library/LaunchDaemons/com.vector.agent.plist

# Linux:
sudo systemctl restart vector-agent
```

**部署目录结构**：

```
/opt/vector-agent/
├── bin/
│   ├── vector-agent    # Agent 二进制
│   └── vector          # Vector 二进制
├── config/             # 配置目录
├── data/               # 数据目录
└── logs/               # 日志目录
```

---

## 完整工作流程

### 开发流程

```bash
# 1. 修改代码
vim cmd/agent/main.go

# 2. 本地部署测试
./scripts/dev-deploy.sh

# 3. 重启服务
sudo launchctl bootout system /Library/LaunchDaemons/com.vector.agent.plist
sudo launchctl bootstrap system /Library/LaunchDaemons/com.vector.agent.plist

# 4. 查看日志
tail -f /opt/vector-agent/logs/agent.log
```

### 发布流程

```bash
# 1. 构建所有平台的安装包
./scripts/build-bundle.sh 1.2.3 darwin arm64
./scripts/build-bundle.sh 1.2.3 darwin x86_64
./scripts/build-bundle.sh 1.2.3 linux arm64
./scripts/build-bundle.sh 1.2.3 linux x86_64

# 2. 检查生成的文件
ls -lh dist/

# 3. 上传到安装包管理页面或分发服务器
```

---

## 常见问题

### Q: 下载 Vector 失败怎么办？

**A**: 可以手动下载并放置到 `bin/` 目录：

```bash
# 1. 访问 Vector 官方 Releases 页面
open https://github.com/vectordotdev/vector/releases

# 2. 下载对应平台的版本
# 例如 macOS arm64: vector-0.35.0-aarch64-apple-darwin.tar.gz

# 3. 解压并复制到 bin/ 目录
tar -xzf vector-*.tar.gz
cp vector-*/bin/vector ./bin/

# 4. 验证
./bin/vector --version
```

### Q: 如何构建特定版本的 Vector？

**A**: 修改 `download-vector.sh` 脚本，或手动下载指定版本：

```bash
# 下载指定版本
./scripts/download-vector.sh 0.35.0
```

### Q: 交叉编译需要注意什么？

**A**:
- Go 支持交叉编译，无需额外配置
- Vector 二进制需要下载目标平台的版本
- 使用 `build-bundle.sh` 的第 2、3 个参数指定目标平台

### Q: 如何清理下载的文件？

**A**:

```bash
# 清理 bin 目录
rm -rf bin/vector

# 清理构建产物
rm -rf dist/

# 重新下载
./scripts/download-vector.sh
```

---

## 脚本依赖

### 系统要求

- **操作系统**: macOS 或 Linux
- **Go**: 1.21 或更高版本
- **工具**: curl, tar, bash

### 网络要求

- 需要访问 GitHub API: `https://api.github.com`
- 需要下载 Vector: `https://github.com/vectordotdev/vector/releases`

### 权限要求

- `dev-deploy.sh` 需要 sudo 权限（部署到 `/opt` 目录）
- 其他脚本无需特殊权限

---

## 更新日志

### v1.1.0 (2024-01-13)

- ✨ 新增 `download-vector.sh` 自动下载脚本
- ✨ `build-bundle.sh` 支持多平台构建
- ✨ `dev-deploy.sh` 自动下载和部署 Vector
- 🔧 自动检测系统架构
- 🔧 支持交叉编译
- 📝 完善文档和使用说明

### v1.0.0

- 🎉 初始版本
- ✅ 基础构建和部署功能
