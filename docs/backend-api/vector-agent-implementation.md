# Vector Agent 改造实施指南

## 版本信息
- 版本: v1.0.0
- 最后更新: 2025-12-26
- 基于现有 vector-agent Go代码

---

## 一、Agent 架构概览

### 1.1 当前架构(已实现)

```
vector-agent/
├── cmd/agent/main.go                # 主程序入口
├── internal/
│   ├── agent/agent.go              # 核心Agent逻辑
│   ├── vector/manager.go           # Vector管理器
│   ├── collector/metrics.go        # 指标采集器
│   └── config/config.go            # 配置管理
├── pkg/api/client.go                # API客户端
└── scripts/install-agent.sh         # 安装脚本
```

### 1.2 工作流程

```
┌─────────────────────────────────────────────┐
│               Vector Agent                  │
│                                              │
│  ┌────────────────────────────────────┐   │
│  │  Main Goroutines:                  │   │
│  │  1. 心跳协程 (30s)                 │   │
│  │  2. 配置监听协程 (30s)             │   │
│  │  3. 指标采集协程 (60s)             │   │
│  │  4. 自愈协程 (60s)                 │   │
│  └────────────────────────────────────┘   │
│                                              │
└─────────────────────────────────────────────┘
         ↓                    ↓
    HTTP REST API        systemctl
    (与后端通信)        (管理Vector)
```

---

## 二、需要补充的 API Client 代码

### 2.1 完善 pkg/api/client.go

```go
package api

import (
	"bytes"
	"encoding/json"
	"fmt"
	"io"
	"net/http"
	"time"
)

// Client API客户端
type Client struct {
	baseURL    string
	token      string
	httpClient *http.Client
	tlsEnabled bool
}

// NewClient 创建API客户端
func NewClient(baseURL, token string, tlsEnabled bool) *Client {
	return &Client{
		baseURL:    baseURL,
		token:      token,
		tlsEnabled: tlsEnabled,
		httpClient: &http.Client{
			Timeout: 30 * time.Second,
		},
	}
}

// ========== 请求/响应结构体 ==========

type RegisterRequest struct {
	Hostname      string `json:"hostname"`
	IPAddress     string `json:"ipAddress"`
	AgentToken    string `json:"agentToken"`
	AgentVersion  string `json:"agentVersion"`
	VectorVersion string `json:"vectorVersion"`
	OSType        string `json:"osType"`
	OSVersion     string `json:"osVersion"`
	CPUCores      int    `json:"cpuCores"`
	TotalMemoryMB int64  `json:"totalMemoryMb"`
}

type RegisterResponse struct {
	HostID  int64  `json:"hostId"`
	Message string `json:"message"`
}

type HeartbeatRequest struct {
	AgentUptime   int64  `json:"agentUptimeSeconds"`
	VectorRunning bool   `json:"vectorRunning"`
	Status        string `json:"status"`
}

type HeartbeatResponse struct {
	HasNewConfig         bool   `json:"hasNewConfig"`
	LatestConfigVersion  string `json:"latestConfigVersion"`
}

type ConfigResponse struct {
	ID          int64  `json:"id"`
	Version     string `json:"version"`
	Name        string `json:"name"`
	YAMLContent string `json:"yamlContent"`
}

type ConfigDeployStatusRequest struct {
	ConfigVersion string `json:"configVersion"`
	Status        string `json:"status"` // success/failed
	ErrorMessage  string `json:"errorMessage,omitempty"`
}

type MetricsReportRequest struct {
	CollectedAt              time.Time `json:"collectedAt"`
	CPUUsagePercent          float64   `json:"cpuUsagePercent"`
	MemoryUsagePercent       float64   `json:"memoryUsagePercent"`
	MemoryUsedMB             int64     `json:"memoryUsedMb"`
	DiskUsagePercent         float64   `json:"diskUsagePercent"`
	DiskUsedGB               int64     `json:"diskUsedGb"`
	AgentUptimeSeconds       int64     `json:"agentUptimeSeconds"`
	AgentMemoryMB            int       `json:"agentMemoryMb"`
	VectorRunning            bool      `json:"vectorRunning"`
	VectorUptimeSeconds      int64     `json:"vectorUptimeSeconds"`
	VectorConfigReloadCount  int       `json:"vectorConfigReloadCount"`
	VectorErrorCount         int       `json:"vectorErrorCount"`
	EventsInTotal            int64     `json:"eventsInTotal"`
	EventsOutTotal           int64     `json:"eventsOutTotal"`
	EventsInRate             float64   `json:"eventsInRate"`
	EventsOutRate            float64   `json:"eventsOutRate"`
}

type APIResponse struct {
	Code    int             `json:"code"`
	Message string          `json:"message"`
	Data    json.RawMessage `json:"data"`
}

// ========== HTTP辅助方法 ==========

func (c *Client) doRequest(method, path string, body interface{}) (*APIResponse, error) {
	url := c.baseURL + path

	var reqBody io.Reader
	if body != nil {
		jsonData, err := json.Marshal(body)
		if err != nil {
			return nil, fmt.Errorf("marshal request body: %w", err)
		}
		reqBody = bytes.NewReader(jsonData)
	}

	req, err := http.NewRequest(method, url, reqBody)
	if err != nil {
		return nil, fmt.Errorf("create request: %w", err)
	}

	req.Header.Set("Content-Type", "application/json")
	req.Header.Set("Authorization", "Bearer "+c.token)

	resp, err := c.httpClient.Do(req)
	if err != nil {
		return nil, fmt.Errorf("do request: %w", err)
	}
	defer resp.Body.Close()

	respBody, err := io.ReadAll(resp.Body)
	if err != nil {
		return nil, fmt.Errorf("read response: %w", err)
	}

	var apiResp APIResponse
	if err := json.Unmarshal(respBody, &apiResp); err != nil {
		return nil, fmt.Errorf("unmarshal response: %w", err)
	}

	if apiResp.Code != 200 {
		return nil, fmt.Errorf("API error: %s", apiResp.Message)
	}

	return &apiResp, nil
}

// ========== API方法 ==========

// Register Agent注册
func (c *Client) Register(req *RegisterRequest) (*RegisterResponse, error) {
	apiResp, err := c.doRequest("POST", "/api/vector/hosts/register", req)
	if err != nil {
		return nil, err
	}

	var resp RegisterResponse
	if err := json.Unmarshal(apiResp.Data, &resp); err != nil {
		return nil, fmt.Errorf("unmarshal register response: %w", err)
	}

	return &resp, nil
}

// Heartbeat 发送心跳
func (c *Client) Heartbeat(req *HeartbeatRequest) (*HeartbeatResponse, error) {
	apiResp, err := c.doRequest("POST", "/api/vector/hosts/heartbeat", req)
	if err != nil {
		return nil, err
	}

	var resp HeartbeatResponse
	if err := json.Unmarshal(apiResp.Data, &resp); err != nil {
		return nil, fmt.Errorf("unmarshal heartbeat response: %w", err)
	}

	return &resp, nil
}

// FetchConfig 拉取最新配置
func (c *Client) FetchConfig() (*ConfigResponse, error) {
	apiResp, err := c.doRequest("GET", "/api/vector/configs/latest", nil)
	if err != nil {
		return nil, err
	}

	// 如果没有配置,data为null
	if len(apiResp.Data) == 0 || string(apiResp.Data) == "null" {
		return nil, nil
	}

	var resp ConfigResponse
	if err := json.Unmarshal(apiResp.Data, &resp); err != nil {
		return nil, fmt.Errorf("unmarshal config response: %w", err)
	}

	return &resp, nil
}

// ReportConfigDeployStatus 上报配置部署状态
func (c *Client) ReportConfigDeployStatus(req *ConfigDeployStatusRequest) error {
	_, err := c.doRequest("POST", "/api/vector/deployments/report", req)
	return err
}

// ReportMetrics 上报指标
func (c *Client) ReportMetrics(req *MetricsReportRequest) error {
	_, err := c.doRequest("POST", "/api/vector/metrics", req)
	return err
}

// ReportLog 上报日志
func (c *Client) ReportLog(level, message string) error {
	body := map[string]interface{}{
		"logLevel": level,
		"message":  message,
		"loggedAt": time.Now(),
	}
	_, err := c.doRequest("POST", "/api/vector/logs", body)
	return err
}
```

---

### 2.2 补充 internal/collector/metrics.go

```go
package collector

import (
	"runtime"
	"time"

	"github.com/mw/vector-agent/internal/vector"
	"github.com/shirou/gopsutil/v3/cpu"
	"github.com/shirou/gopsutil/v3/disk"
	"github.com/shirou/gopsutil/v3/host"
	"github.com/shirou/gopsutil/v3/mem"
	"github.com/shirou/gopsutil/v3/process"
)

// Metrics 指标数据
type Metrics struct {
	// 系统指标
	CPUUsagePercent    float64
	MemoryUsagePercent float64
	MemoryUsedMB       int64
	DiskUsagePercent   float64
	DiskUsedGB         int64

	// Agent指标
	AgentMemoryMB int

	// Vector指标
	VectorRunning           bool
	VectorUptimeSeconds     int64
	VectorConfigReloadCount int
	VectorErrorCount        int
	EventsInTotal           int64
	EventsOutTotal          int64
	EventsInRate            float64
	EventsOutRate           float64
}

// SystemInfo 系统信息
type SystemInfo struct {
	Hostname      string
	IPAddress     string
	OSType        string
	OSVersion     string
	CPUCores      int
	TotalMemoryMB int64
}

// MetricsCollector 指标采集器
type MetricsCollector struct {
	vectorMgr *vector.Manager
}

// NewMetricsCollector 创建指标采集器
func NewMetricsCollector(vectorMgr *vector.Manager) *MetricsCollector {
	return &MetricsCollector{
		vectorMgr: vectorMgr,
	}
}

// Collect 采集指标
func (c *MetricsCollector) Collect() *Metrics {
	m := &Metrics{}

	// CPU使用率
	if cpuPercent, err := cpu.Percent(time.Second, false); err == nil && len(cpuPercent) > 0 {
		m.CPUUsagePercent = cpuPercent[0]
	}

	// 内存使用率
	if memStat, err := mem.VirtualMemory(); err == nil {
		m.MemoryUsagePercent = memStat.UsedPercent
		m.MemoryUsedMB = int64(memStat.Used / 1024 / 1024)
	}

	// 磁盘使用率
	if diskStat, err := disk.Usage("/"); err == nil {
		m.DiskUsagePercent = diskStat.UsedPercent
		m.DiskUsedGB = int64(diskStat.Used / 1024 / 1024 / 1024)
	}

	// Agent内存使用
	var memStats runtime.MemStats
	runtime.ReadMemStats(&memStats)
	m.AgentMemoryMB = int(memStats.Alloc / 1024 / 1024)

	// Vector状态
	m.VectorRunning = c.vectorMgr.IsRunning()

	// TODO: 从Vector API获取更详细的指标
	// 这需要Vector开启API端点(默认http://localhost:9090)
	// 可以通过HTTP请求获取metrics

	return m
}

// CollectSystemInfo 采集系统信息
func (c *MetricsCollector) CollectSystemInfo() *SystemInfo {
	info := &SystemInfo{}

	// 主机名
	if hostname, err := host.HostName(); err == nil {
		info.Hostname = hostname
	}

	// IP地址
	info.IPAddress = getLocalIP()

	// 操作系统
	if hostInfo, err := host.Info(); err == nil {
		info.OSType = hostInfo.OS
		info.OSVersion = hostInfo.PlatformVersion
	}

	// CPU核心数
	if cpuCount, err := cpu.Counts(true); err == nil {
		info.CPUCores = cpuCount
	}

	// 总内存
	if memStat, err := mem.VirtualMemory(); err == nil {
		info.TotalMemoryMB = int64(memStat.Total / 1024 / 1024)
	}

	return info
}

// getLocalIP 获取本机IP
func getLocalIP() string {
	// 简化实现,实际应获取外网IP或管理IP
	return "127.0.0.1"
}
```

---

### 2.3 添加依赖

在 `vector-agent/go.mod` 中添加:

```go
require (
	github.com/shirou/gopsutil/v3 v3.23.12  // 系统指标采集
)
```

运行:
```bash
cd vector-agent
go mod tidy
```

---

## 三、编译和部署

### 3.1 编译Agent

```bash
cd vector-agent

# 编译当前平台
go build -o vector-agent cmd/agent/main.go

# 交叉编译Linux amd64
GOOS=linux GOARCH=amd64 go build -o vector-agent-linux-amd64 cmd/agent/main.go

# 编译带版本信息
VERSION=1.0.0
BUILD_TIME=$(date +%Y-%m-%d_%H:%M:%S)
go build -ldflags "-X main.Version=${VERSION} -X main.BuildTime=${BUILD_TIME}" \
    -o vector-agent cmd/agent/main.go
```

---

### 3.2 安装脚本改进

基于现有的 `scripts/install-agent.sh`,需要补充:

```bash
# 在 download_agent() 函数中添加:

# 从服务器下载Agent二进制
download_agent() {
    log_info "下载 Vector Agent 二进制文件..."

    DOWNLOAD_URL="${SERVER_URL}/downloads/vector-agent-${AGENT_VERSION}-linux-amd64"

    wget -O "$INSTALL_DIR/bin/vector-agent" "${DOWNLOAD_URL}" || {
        log_error "下载失败,请检查服务器地址"
        exit 1
    }

    chmod +x "$INSTALL_DIR/bin/vector-agent"
    chown "$AGENT_USER:$AGENT_USER" "$INSTALL_DIR/bin/vector-agent"

    log_info "Agent 二进制文件下载成功"
}
```

---

### 3.3 后端提供下载接口

在后端添加Controller:

```java
@RestController
@RequestMapping("/downloads")
public class DownloadController {

    @GetMapping("/vector-agent-{version}-linux-amd64")
    public void downloadAgent(@PathVariable String version, HttpServletResponse response) {
        String filePath = "/opt/downloads/vector-agent-" + version + "-linux-amd64";
        File file = new File(filePath);

        if (!file.exists()) {
            response.setStatus(404);
            return;
        }

        response.setContentType("application/octet-stream");
        response.setHeader("Content-Disposition",
            "attachment; filename=vector-agent-" + version + "-linux-amd64");

        try (FileInputStream fis = new FileInputStream(file);
             OutputStream os = response.getOutputStream()) {
            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = fis.read(buffer)) != -1) {
                os.write(buffer, 0, bytesRead);
            }
            os.flush();
        } catch (IOException e) {
            log.error("下载失败", e);
        }
    }

    @GetMapping("/install.sh")
    public void downloadInstallScript(HttpServletResponse response) {
        // 返回安装脚本
        response.setContentType("text/plain");
        // ...
    }
}
```

---

## 四、配置示例

### 4.1 Agent配置文件

`/etc/vector-agent/agent.yaml`:

```yaml
# Vector Agent 配置文件

# 后端服务器配置
server_url: "http://192.168.1.100:8080"
agent_token: "a1b2c3d4e5f6g7h8i9j0k1l2m3n4o5p6"

# 心跳和配置轮询
heartbeat_interval: 30        # 心跳间隔(秒)
config_poll_interval: 30      # 配置轮询间隔(秒)

# Vector 路径配置
vector_bin_path: "/opt/vector/bin/vector"
vector_config_path: "/opt/vector/config/active/vector.yaml"
vector_service_name: "vector"

# 日志配置
log_level: "info"             # debug, info, warn, error
log_file: "/var/log/vector-agent/agent.log"

# 指标采集
metrics_enabled: true
metrics_interval: 60          # 指标采集间隔(秒)

# 自愈功能
self_heal_enabled: true
health_check_interval: 60     # 健康检查间隔(秒)

# TLS配置(可选)
tls_enabled: false
```

---

### 4.2 Vector systemd服务

`/etc/systemd/system/vector.service`:

```ini
[Unit]
Description=Vector - High-Performance Logs and Metrics Router
After=network.target

[Service]
Type=notify
ExecStart=/opt/vector/bin/vector --config /opt/vector/config/active/vector.yaml
ExecReload=/bin/kill -HUP $MAINPID
Restart=on-failure
RestartSec=5s
StandardOutput=journal
StandardError=journal

# 资源限制
LimitNOFILE=65536

[Install]
WantedBy=multi-user.target
```

---

## 五、测试和验证

### 5.1 本地测试

```bash
# 1. 启动后端服务
cd log-analysis-backend
mvn spring-boot:run

# 2. 生成Token(通过前端或API)
curl -X POST http://localhost:8080/api/vector/hosts/generate-token

# 3. 运行Agent(开发模式)
cd vector-agent
export SERVER_URL="http://localhost:8080"
export AGENT_TOKEN="your-token-here"
go run cmd/agent/main.go -config /path/to/agent.yaml

# 4. 查看日志
tail -f /var/log/vector-agent/agent.log
```

---

### 5.2 集成测试

```bash
# 1. 检查注册
curl http://localhost:8080/api/vector/hosts

# 2. 模拟心跳
curl -X POST http://localhost:8080/api/vector/hosts/heartbeat \
  -H "Authorization: Bearer your-token" \
  -H "Content-Type: application/json" \
  -d '{"agentUptimeSeconds":100,"vectorRunning":true,"status":"online"}'

# 3. 检查指标
curl http://localhost:8080/api/vector/hosts/1/metrics/latest
```

---

## 六、常见问题处理

### 6.1 Agent无法注册

**问题**: Token无效或已过期

**解决**:
1. 检查Token是否正确
2. 重新生成Token
3. 检查服务器URL是否正确
4. 查看后端日志确认请求是否到达

---

### 6.2 配置应用失败

**问题**: Vector配置校验失败

**解决**:
1. 查看Agent日志中的错误信息
2. 手动验证配置: `/opt/vector/bin/vector validate /path/to/config.yaml`
3. 检查配置备份: `ls /opt/vector/config/history/`
4. 手动回滚: Agent会自动回滚,也可手动执行

---

### 6.3 心跳超时

**问题**: 机器显示离线

**解决**:
1. 检查Agent进程: `systemctl status vector-agent`
2. 检查网络连接: `ping server-ip`
3. 查看防火墙规则
4. 检查Agent日志

---

## 七、升级和维护

### 7.1 Agent升级

```bash
# 1. 下载新版本
wget http://server/downloads/vector-agent-1.1.0-linux-amd64

# 2. 停止Agent
systemctl stop vector-agent

# 3. 替换二进制
mv vector-agent-1.1.0-linux-amd64 /opt/vector-agent/bin/vector-agent
chmod +x /opt/vector-agent/bin/vector-agent

# 4. 启动Agent
systemctl start vector-agent

# 5. 验证
systemctl status vector-agent
```

---

### 7.2 批量升级

通过管理平台创建批量任务:
1. 上传新版本Agent到服务器
2. 创建升级任务
3. 选择目标机器
4. 执行分批升级

---

## 八、监控和告警

### 8.1 Agent健康监控

- **心跳超时**: 60秒未收到心跳视为离线
- **指标异常**: CPU/内存/磁盘超过阈值告警
- **Vector异常**: Vector进程崩溃自动重启

### 8.2 告警通知

集成现有告警系统,当出现以下情况时发送告警:
- Agent离线
- 配置应用失败
- Vector进程崩溃
- 系统资源超限

---

**文档更新日期**: 2025-12-26
**维护者**: Claude Code
