# Vector Agent

企业级 Vector 日志收集代理管理程序 - **一体化安装包**。

## 特性

- ✅ **一键安装**: 包含 Vector 和 Agent，一条命令完成安装
- ✅ **极简配置**: 只需配置 Token 和服务器地址
- ✅ **自动注册**: Agent 启动后自动向服务器注册
- ✅ **配置拉取**: 定期从服务器拉取最新配置
- ✅ **配置验证**: 应用前自动验证配置有效性
- ✅ **自动回滚**: 配置错误时自动回滚到上一版本
- ✅ **心跳上报**: 定期上报运行状态
- ✅ **自愈机制**: 自动检测和修复 Vector 异常
- ✅ **systemd 管理**: 支持 systemctl 管理服务

## 快速开始

### 一键安装

在管理页面生成 Token 后，在目标机器上执行：

```bash
curl -fsSL "http://YOUR_SERVER:8080/api/vector/agents/install-script?token=YOUR_TOKEN" | sudo bash
```

安装完成后：
- Vector 和 Agent 自动启动
- 机器自动注册到管理页面
- 等待服务器下发配置即可

### 验证安装

```bash
# 查看服务状态
systemctl status vector-agent
systemctl status vector

# 查看 Vector 版本
vector --version

# 查看日志
journalctl -u vector-agent -f
```

## 目录结构

```
/opt/vector-agent/
├── bin/
│   ├── vector-agent    # Agent 主程序
│   └── vector          # Vector 二进制
├── config/
│   ├── agent.yaml      # Agent 配置（只需 token + server_url）
│   ├── vector.yaml     # Vector 配置（由 Agent 管理）
│   └── history/        # 配置备份
├── data/               # Vector 数据目录
└── logs/               # 日志目录
```

## 配置说明

Agent 配置极简，只需两项：

```yaml
# /opt/vector-agent/config/agent.yaml
server_url: "http://192.168.1.100:8080"
agent_token: "your-token-here"
```

可选配置（使用默认值即可）：
```yaml
heartbeat_interval: 30      # 心跳间隔（秒）
config_poll_interval: 30    # 配置轮询间隔（秒）
log_level: "info"           # 日志级别
```

## 常用命令

```bash
# 服务管理
systemctl start vector-agent    # 启动 Agent
systemctl stop vector-agent     # 停止 Agent
systemctl restart vector-agent  # 重启 Agent
systemctl status vector-agent   # 查看状态

systemctl start vector          # 启动 Vector
systemctl stop vector           # 停止 Vector
systemctl restart vector        # 重启 Vector
systemctl status vector         # 查看状态

# 查看日志
journalctl -u vector-agent -f   # Agent 日志
journalctl -u vector -f         # Vector 日志

# Vector 命令
vector --version                # 查看版本
vector validate /opt/vector-agent/config/vector.yaml  # 验证配置
```

## 工作原理

```
┌─────────────────────────────────────────────────┐
│            Vector Agent                          │
│  ┌──────────────────────────────────────────┐  │
│  │   心跳协程 (30s)                          │  │
│  │   └─> 发送心跳到服务器                    │  │
│  ├──────────────────────────────────────────┤  │
│  │   配置监听协程 (30s)                      │  │
│  │   ├─> 拉取配置                            │  │
│  │   ├─> 验证配置                            │  │
│  │   ├─> 备份旧配置                          │  │
│  │   ├─> 应用新配置                          │  │
│  │   └─> 回滚（如果失败）                    │  │
│  ├──────────────────────────────────────────┤  │
│  │   自愈协程 (60s)                          │  │
│  │   ├─> 检查 Vector 进程                    │  │
│  │   └─> 自动重启（如果异常）                │  │
│  └──────────────────────────────────────────┘  │
└─────────────────────────────────────────────────┘
          │                          │
          │ HTTP API                 │ systemctl
          ▼                          ▼
   ┌─────────────┐          ┌───────────────┐
   │  管理服务器  │          │    Vector     │
   └─────────────┘          └───────────────┘
```

## 卸载

```bash
curl -fsSL "http://YOUR_SERVER:8080/api/vector/agents/uninstall-script" | sudo bash
```

或手动卸载：

```bash
# 停止服务
sudo systemctl stop vector-agent vector
sudo systemctl disable vector-agent vector

# 删除文件
sudo rm -rf /opt/vector-agent
sudo rm -f /etc/systemd/system/vector-agent.service
sudo rm -f /etc/systemd/system/vector.service
sudo rm -f /usr/local/bin/vector
sudo systemctl daemon-reload
```

## 开发

### 构建 Bundle 安装包（推荐）

Bundle 包含 vector-agent + vector，适合内网环境一键部署：

```bash
cd vector-agent

# 构建所有平台的 Bundle（linux/darwin, amd64/arm64）
./scripts/build-bundle.sh 1.0.0 0.34.0

# 生成的安装包在 dist/ 目录：
# - vector-agent-bundle-1.0.0-linux-amd64.tar.gz
# - vector-agent-bundle-1.0.0-linux-arm64.tar.gz
# - vector-agent-bundle-1.0.0-darwin-amd64.tar.gz
# - vector-agent-bundle-1.0.0-darwin-arm64.tar.gz
```

### 上传到安装包管理

1. 打开管理页面 -> 安装包管理
2. 点击"上传安装包"
3. 选择类型为 "Bundle (Agent+Vector)"
4. 填写版本号、系统类型、架构
5. 选择对应的 .tar.gz 文件上传

### 仅构建 Agent（不含 Vector）

```bash
# 构建所有平台的 Agent 二进制
./scripts/build-all.sh 1.0.0
```

### 本地测试

```bash
# 编译
go build -o vector-agent cmd/agent/main.go

# 生成配置
./vector-agent -gen-config -server http://localhost:8080 -token test123

# 运行
./vector-agent -config /path/to/agent.yaml
```

## 许可证

MIT License
