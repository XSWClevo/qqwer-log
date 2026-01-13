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
}

// NewClient 创建API客户端
func NewClient(baseURL, token string) *Client {
	return &Client{
		baseURL: baseURL,
		token:   token,
		httpClient: &http.Client{
			Timeout: 30 * time.Second,
		},
	}
}

// doRequest 执行HTTP请求
func (c *Client) doRequest(method, path string, reqBody interface{}, respBody interface{}) error {
	var body io.Reader
	if reqBody != nil {
		jsonData, err := json.Marshal(reqBody)
		if err != nil {
			return fmt.Errorf("序列化请求失败: %w", err)
		}
		body = bytes.NewReader(jsonData)
	}

	url := c.baseURL + path
	req, err := http.NewRequest(method, url, body)
	if err != nil {
		return fmt.Errorf("创建请求失败: %w", err)
	}

	// 设置请求头
	req.Header.Set("Content-Type", "application/json")
	req.Header.Set("Authorization", "Bearer "+c.token)
	req.Header.Set("User-Agent", "VectorAgent/1.0.0")

	// 发送请求
	resp, err := c.httpClient.Do(req)
	if err != nil {
		return fmt.Errorf("请求失败: %w", err)
	}
	defer resp.Body.Close()

	// 读取响应体
	bodyBytes, err := io.ReadAll(resp.Body)
	if err != nil {
		return fmt.Errorf("读取响应失败: %w", err)
	}

	// 检查状态码
	if resp.StatusCode < 200 || resp.StatusCode >= 300 {
		return fmt.Errorf("HTTP错误 %d: %s", resp.StatusCode, string(bodyBytes))
	}

	// 解析响应
	if respBody != nil && len(bodyBytes) > 0 {
		// 尝试解析为标准响应格式
		var apiResp struct {
			Code    int             `json:"code"`
			Message string          `json:"message"`
			Data    json.RawMessage `json:"data"`
		}
		if err := json.Unmarshal(bodyBytes, &apiResp); err == nil && apiResp.Code == 200 {
			// 标准格式，解析 data 字段
			if len(apiResp.Data) > 0 && string(apiResp.Data) != "null" {
				if err := json.Unmarshal(apiResp.Data, respBody); err != nil {
					return fmt.Errorf("解析响应数据失败: %w", err)
				}
			}
		} else {
			// 非标准格式，直接解析
			if err := json.Unmarshal(bodyBytes, respBody); err != nil {
				return fmt.Errorf("解析响应失败: %w", err)
			}
		}
	}

	return nil
}

// ==================== 请求/响应结构 ====================

// RegisterRequest 注册请求
type RegisterRequest struct {
	Hostname      string `json:"hostname"`
	IPAddress     string `json:"ipAddress"`
	AgentVersion  string `json:"agentVersion"`
	VectorVersion string `json:"vectorVersion"`
	OSType        string `json:"osType"`
	OSVersion     string `json:"osVersion"`
	CPUCores      int    `json:"cpuCores"`
	TotalMemoryMB int64  `json:"totalMemoryMb"`
}

// RegisterResponse 注册响应
type RegisterResponse struct {
	HostID string `json:"hostId"`
	Token  string `json:"token"`
}

// HeartbeatRequest 心跳请求
type HeartbeatRequest struct {
	AgentUptime   int64  `json:"agentUptime"`
	VectorRunning bool   `json:"vectorRunning"`
	Status        string `json:"status"` // online, offline, error
}

// ConfigResponse 配置响应
type ConfigResponse struct {
	DeploymentID string            `json:"deploymentId"`
	Version      string            `json:"version"`
	YAMLContent  string            `json:"yamlContent"` // 兼容旧模式（单文件）
	ConfigFiles  map[string]string `json:"configFiles"` // config-dir 模式（多文件）
	DeployMode   string            `json:"deployMode"`  // restart, reload
}

// ConfigDeployStatusRequest 配置部署状态请求
type ConfigDeployStatusRequest struct {
	DeploymentID  string `json:"deploymentId"`
	ConfigVersion string `json:"configVersion"`
	Status        string `json:"status"` // deploying, success, failed
	ErrorMessage  string `json:"errorMessage,omitempty"`
}

// LocalTime 自定义时间类型，序列化为不带时区的格式（兼容 Java LocalDateTime）
type LocalTime time.Time

func (t LocalTime) MarshalJSON() ([]byte, error) {
	// 格式化为 Java LocalDateTime 兼容的格式：2006-01-02T15:04:05.000
	// 注意：不能带时区信息，否则 Java LocalDateTime 无法解析
	formatted := time.Time(t).Format("2006-01-02T15:04:05.000")
	return []byte(`"` + formatted + `"`), nil
}

// 确保零值也能正确序列化
func (t LocalTime) IsZero() bool {
	return time.Time(t).IsZero()
}

// MetricsReportRequest 指标上报请求
type MetricsReportRequest struct {
	CollectedAt         LocalTime                    `json:"collectedAt"`
	CPUUsagePercent     float64                      `json:"cpuUsagePercent"`
	MemoryUsagePercent  float64                      `json:"memoryUsagePercent"`
	MemoryUsedMB        int64                        `json:"memoryUsedMb"`
	DiskUsagePercent    float64                      `json:"diskUsagePercent"`
	DiskUsedGB          int64                        `json:"diskUsedGb"`
	AgentUptimeSeconds  int64                        `json:"agentUptimeSeconds"`
	AgentMemoryMB       int                          `json:"agentMemoryMb"`
	VectorRunning       bool                         `json:"vectorRunning"`
	VectorUptimeSeconds int64                        `json:"vectorUptimeSeconds"`
	NetworkInterfaces   []NetworkInterface           `json:"networkInterfaces,omitempty"`
	ComponentMetrics    map[string]*ComponentMetrics `json:"componentMetrics,omitempty"`
}

// NetworkInterface 网卡信息
type NetworkInterface struct {
	Name        string `json:"name"`        // 网卡名称
	BytesSent   uint64 `json:"bytesSent"`   // 发送字节数
	BytesRecv   uint64 `json:"bytesRecv"`   // 接收字节数
	PacketsSent uint64 `json:"packetsSent"` // 发送包数
	PacketsRecv uint64 `json:"packetsRecv"` // 接收包数
	Errin       uint64 `json:"errin"`       // 接收错误数
	Errout      uint64 `json:"errout"`      // 发送错误数
}

// ComponentMetrics 组件级别指标
type ComponentMetrics struct {
	Status          string    `json:"status"`          // normal, warning, error, stopped
	EventsProcessed int64     `json:"eventsProcessed"` // 处理的事件数
	BytesProcessed  int64     `json:"bytesProcessed"`  // 处理的字节数
	Errors          int64     `json:"errors"`          // 错误数
	LastActive      LocalTime `json:"lastActive"`      // 最后活跃时间
}

// LogReportRequest 日志上报请求
type LogReportRequest struct {
	Level   string `json:"level"`
	Message string `json:"message"`
	Source  string `json:"source"`
}

// CommandResponse 命令响应
type CommandResponse struct {
	CommandID   string `json:"commandId"`
	CommandType string `json:"commandType"` // start_vector, stop_vector, restart_vector, reload_vector, upgrade_agent, upgrade_vector

	// 升级命令专用字段
	TargetVersion string `json:"targetVersion,omitempty"`
	DownloadURL   string `json:"downloadUrl,omitempty"`
	Checksum      string `json:"checksum,omitempty"`
	FileSize      int64  `json:"fileSize,omitempty"`
}

// CommandStatusRequest 命令状态上报请求
type CommandStatusRequest struct {
	CommandID    string `json:"commandId"`
	Status       string `json:"status"` // executing, success, failed
	ErrorMessage string `json:"errorMessage,omitempty"`
}

// ==================== API方法 ====================

// Register 注册Agent
func (c *Client) Register(req *RegisterRequest) (*RegisterResponse, error) {
	var resp RegisterResponse
	if err := c.doRequest("POST", "/api/vector/agents/register", req, &resp); err != nil {
		return nil, err
	}
	return &resp, nil
}

// Heartbeat 发送心跳
func (c *Client) Heartbeat(req *HeartbeatRequest) error {
	return c.doRequest("POST", "/api/vector/agents/heartbeat", req, nil)
}

// FetchConfig 拉取配置
func (c *Client) FetchConfig() (*ConfigResponse, error) {
	var resp ConfigResponse
	if err := c.doRequest("GET", "/api/vector/agents/config", nil, &resp); err != nil {
		return nil, err
	}

	// 如果没有配置，返回nil
	if resp.Version == "" {
		return nil, nil
	}

	return &resp, nil
}

// ReportConfigDeployStatus 上报配置部署状态
func (c *Client) ReportConfigDeployStatus(req *ConfigDeployStatusRequest) error {
	return c.doRequest("POST", "/api/vector/agents/config/deploy-status", req, nil)
}

// ReportMetrics 上报指标
func (c *Client) ReportMetrics(req *MetricsReportRequest) error {
	return c.doRequest("POST", "/api/vector/agents/metrics", req, nil)
}

// ReportLog 上报日志
func (c *Client) ReportLog(level, message string) error {
	req := LogReportRequest{
		Level:   level,
		Message: message,
		Source:  "agent",
	}
	return c.doRequest("POST", "/api/vector/agents/logs", req, nil)
}

// FetchCommand 拉取待执行命令
func (c *Client) FetchCommand() (*CommandResponse, error) {
	var resp CommandResponse
	if err := c.doRequest("GET", "/api/vector/agents/command", nil, &resp); err != nil {
		return nil, err
	}

	// 如果没有命令，返回nil
	if resp.CommandID == "" {
		return nil, nil
	}

	return &resp, nil
}

// ReportCommandStatus 上报命令执行状态
func (c *Client) ReportCommandStatus(req *CommandStatusRequest) error {
	return c.doRequest("POST", "/api/vector/agents/command/status", req, nil)
}
