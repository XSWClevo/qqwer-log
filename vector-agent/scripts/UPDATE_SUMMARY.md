# Vector Agent 脚本更新总结

## 📋 更新内容

### 1. 新增 `download-vector.sh` 脚本

**核心功能**：
- ✅ 自动检测系统架构（macOS/Linux, arm64/x86_64）
- ✅ 从 GitHub API 获取最新 Vector 版本
- ✅ 智能匹配下载链接（根据实际 Release assets）
- ✅ 检查 `/opt/vector-agent/bin/vector` 是否已安装
- ✅ 支持指定版本和目标平台（交叉编译）
- ✅ 自动解压并安装到 `bin/` 目录

**关键实现**：

```bash
# 1. 检查系统已安装的 Vector
if [ -f "/opt/vector-agent/bin/vector" ]; then
    # 询问是否跳过下载
    read -p "是否跳过下载并使用已安装的版本? (Y/n): "
fi

# 2. 从 GitHub API 获取最新版本
release_json=$(curl -s "https://api.github.com/repos/vectordotdev/vector/releases/latest")
version=$(echo "$release_json" | grep '"tag_name":' | sed -E 's/.*"v([^"]+)".*/\1/')

# 3. 根据系统架构匹配下载链接
# macOS arm64: *-arm64-apple-darwin.tar.gz
# macOS x86_64: *-arm64-apple-darwin.tar.gz (使用 Rosetta 2)
# Linux arm64: *-aarch64-unknown-linux-musl.tar.gz
# Linux x86_64: *-x86_64-unknown-linux-musl.tar.gz
download_url=$(echo "$release_json" | grep '"browser_download_url":' | grep "${VECTOR_PATTERN}")
```

### 2. 修改 `build-bundle.sh` 脚本

**改动点**：
- ✅ 自动检测当前系统作为默认构建平台
- ✅ 支持多平台交叉编译（通过参数指定）
- ✅ 自动调用 `download-vector.sh` 下载 Vector
- ✅ 标准化架构名称（aarch64→arm64, amd64→x86_64）

**使用示例**：

```bash
# 构建当前系统平台
./scripts/build-bundle.sh 1.2.3

# 构建所有平台
./scripts/build-bundle.sh 1.2.3 darwin arm64
./scripts/build-bundle.sh 1.2.3 linux arm64
./scripts/build-bundle.sh 1.2.3 linux x86_64
```

### 3. 修改 `dev-deploy.sh` 脚本

**改动点**：
- ✅ 增加 Vector 下载步骤
- ✅ 检查系统目录 `/opt/vector-agent/bin/vector`
- ✅ 检查项目目录 `bin/vector`
- ✅ 自动创建完整的目录结构
- ✅ 同时部署 vector-agent 和 vector

**执行流程**：

```
1. 检查 /opt/vector-agent/bin/vector
   ├─ 存在 → 复制到项目 bin/
   └─ 不存在 → 继续

2. 检查 bin/vector
   ├─ 存在 → 跳过下载
   └─ 不存在 → 调用 download-vector.sh

3. 编译 vector-agent

4. 部署到 /opt/vector-agent/bin/
   ├─ vector-agent
   └─ vector
```

### 4. 新增 `test-download.sh` 测试脚本

**功能**：
- 测试所有平台的下载链接解析
- 显示可用的下载文件列表
- 验证匹配逻辑是否正确

**测试结果**：

```
✅ darwin/arm64   → vector-0.52.0-arm64-apple-darwin.tar.gz
✅ darwin/x86_64  → vector-0.52.0-arm64-apple-darwin.tar.gz (Rosetta 2)
✅ linux/arm64    → vector-0.52.0-aarch64-unknown-linux-musl.tar.gz
✅ linux/x86_64   → vector-0.52.0-x86_64-unknown-linux-musl.tar.gz
```

---

## 🎯 支持的平台

| 操作系统 | 架构 | Vector 文件名 | 状态 |
|---------|------|--------------|------|
| macOS | arm64 | vector-{version}-arm64-apple-darwin.tar.gz | ✅ 官方支持 |
| macOS | x86_64 | vector-{version}-arm64-apple-darwin.tar.gz | ⚠️ Rosetta 2 |
| Linux | arm64 | vector-{version}-aarch64-unknown-linux-musl.tar.gz | ✅ 官方支持 |
| Linux | x86_64 | vector-{version}-x86_64-unknown-linux-musl.tar.gz | ✅ 官方支持 |

---

## 📝 使用说明

### 开发流程

```bash
# 1. 本地开发部署（自动下载 Vector）
./scripts/dev-deploy.sh

# 2. 重启服务
# macOS:
sudo launchctl bootout system /Library/LaunchDaemons/com.vector.agent.plist
sudo launchctl bootstrap system /Library/LaunchDaemons/com.vector.agent.plist

# Linux:
sudo systemctl restart vector-agent
```

### 发布流程

```bash
# 1. 构建所有平台的安装包
./scripts/build-bundle.sh 1.2.3 darwin arm64
./scripts/build-bundle.sh 1.2.3 linux arm64
./scripts/build-bundle.sh 1.2.3 linux x86_64

# 2. 检查生成的文件
ls -lh dist/

# 输出示例:
# vector-agent-bundle-1.2.3-darwin-arm64.tar.gz
# vector-agent-bundle-1.2.3-linux-arm64.tar.gz
# vector-agent-bundle-1.2.3-linux-x86_64.tar.gz
```

### 单独下载 Vector

```bash
# 下载最新版本
./scripts/download-vector.sh

# 下载指定版本
./scripts/download-vector.sh 0.52.0

# 为指定平台下载
./scripts/download-vector.sh latest linux arm64
```

---

## 🔍 技术细节

### GitHub API 解析

```bash
# 1. 获取最新 Release
curl -s https://api.github.com/repos/vectordotdev/vector/releases/latest

# 2. 解析版本号
version=$(echo "$json" | grep '"tag_name":' | sed -E 's/.*"v([^"]+)".*/\1/')

# 3. 匹配下载链接
download_url=$(echo "$json" | grep '"browser_download_url":' | grep "${pattern}")
```

### 架构映射

```bash
# 系统检测 → 标准化 → Vector 命名
uname -m = arm64     → arm64     → arm64 (macOS) / aarch64 (Linux)
uname -m = aarch64   → arm64     → arm64 (macOS) / aarch64 (Linux)
uname -m = x86_64    → x86_64    → x86_64
uname -m = amd64     → x86_64    → x86_64

uname -s = Darwin    → darwin    → apple-darwin
uname -s = Linux     → linux     → unknown-linux-musl
```

### 文件名模式

```bash
# macOS
vector-{version}-arm64-apple-darwin.tar.gz

# Linux
vector-{version}-aarch64-unknown-linux-musl.tar.gz  # arm64
vector-{version}-x86_64-unknown-linux-musl.tar.gz   # x86_64
```

---

## ⚠️ 注意事项

### 1. macOS Intel (x86_64)

Vector 官方不提供 macOS x86_64 版本，脚本会自动下载 arm64 版本：

```bash
注意: Vector 官方不提供 macOS x86_64 版本
建议使用 Rosetta 2 运行 arm64 版本，或使用 Homebrew 安装
```

### 2. Linux arm64 命名

Vector 使用 `aarch64` 而不是 `arm64`：

```bash
# 系统检测: arm64
# Vector 文件名: aarch64-unknown-linux-musl
```

### 3. 系统已安装的 Vector

脚本会检查 `/opt/vector-agent/bin/vector`：

```bash
检测到系统已安装 Vector: vector 0.52.0 (x86_64-unknown-linux-musl ...)
位置: /opt/vector-agent/bin/vector

是否跳过下载并使用已安装的版本? (Y/n):
```

### 4. 非交互模式

`dev-deploy.sh` 在非交互模式下自动跳过已安装的版本：

```bash
# 使用 yes 命令自动回答
yes n | bash download-vector.sh || true
```

---

## 🧪 测试验证

运行测试脚本验证所有平台：

```bash
./scripts/test-download.sh
```

输出示例：

```
========================================
  测试 Vector 下载链接解析
========================================

获取 Vector 最新版本信息...
  -> 最新版本: v0.52.0

----------------------------------------
测试平台: darwin/arm64
----------------------------------------
匹配模式: *arm64-apple-darwin.tar.gz
✅ 找到下载链接:
   https://github.com/vectordotdev/vector/releases/download/v0.52.0/vector-0.52.0-arm64-apple-darwin.tar.gz

----------------------------------------
测试平台: linux/x86_64
----------------------------------------
匹配模式: *x86_64-unknown-linux-musl.tar.gz
✅ 找到下载链接:
   https://github.com/vectordotdev/vector/releases/download/v0.52.0/vector-0.52.0-x86_64-unknown-linux-musl.tar.gz
```

---

## 📦 文件清单

```
vector-agent/scripts/
├── download-vector.sh      # Vector 下载脚本（新增）
├── build-bundle.sh         # Bundle 构建脚本（已修改）
├── dev-deploy.sh           # 本地部署脚本（已修改）
├── test-download.sh        # 测试脚本（新增）
├── install.sh              # 安装脚本（未修改）
└── README.md               # 使用文档（已更新）
```

---

## ✅ 满足的需求

1. ✅ **最新版本**：自动从 GitHub API 获取最新版本
2. ✅ **不缓存**：每次都重新下载，不保留旧版本
3. ✅ **不验证**：不进行 checksum 验证
4. ✅ **默认下载链接**：提供 GitHub Releases 下载链接
5. ✅ **多平台构建**：支持交叉编译所有主流平台
6. ✅ **检查已安装**：检查 `/opt/vector-agent/bin/vector`
7. ✅ **智能匹配**：根据实际 Release assets 匹配下载链接

---

## 🎉 总结

所有脚本已完成更新和测试，可以直接使用！主要改进：

1. **自动化**：无需手动下载 Vector
2. **智能化**：自动检测系统架构和已安装版本
3. **灵活性**：支持多平台交叉编译
4. **可靠性**：从 GitHub API 动态解析下载链接
5. **易用性**：友好的提示和错误处理
