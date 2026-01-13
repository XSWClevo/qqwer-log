package config

import (
	"fmt"
	"os"

	"gopkg.in/yaml.v3"
)

// 内置路径常量（固定，用户无需配置）
const (
	BaseDir    = "/opt/vector-agent"
	InstallDir = BaseDir
	BinDir     = InstallDir + "/bin"
	ConfigDir  = InstallDir + "/config"
	DataDir    = InstallDir + "/data"
	LogDir     = InstallDir + "/logs"

	VectorBin     = BinDir + "/vector"
	AgentBin      = BinDir + "/vector-agent"
	VectorConfig  = ConfigDir + "/vector.yaml"
	AgentConfig   = BaseDir + "/agent.yaml" // Agent 配置放在根目录，避免被 Vector 读取
	VectorService = "vector"
	AgentService  = "vector-agent"

	// macOS launchd 服务名
	LaunchdServiceName = "com.mw.vector"
)

// Config Agent配置结构（极简）
type Config struct {
	// 必填项（用户配置）
	ServerURL  string `yaml:"server_url"`  // 后端API地址
	AgentToken string `yaml:"agent_token"` // Agent认证Token

	// 可选项（有默认值）
	HeartbeatInterval  int    `yaml:"heartbeat_interval,omitempty"`   // 心跳间隔（秒），默认30
	ConfigPollInterval int    `yaml:"config_poll_interval,omitempty"` // 配置轮询间隔（秒），默认30
	LogLevel           string `yaml:"log_level,omitempty"`            // 日志级别，默认info
}

// Load 加载配置文件
func Load(configFile string) (*Config, error) {
	// 如果未指定配置文件，使用默认路径
	if configFile == "" {
		configFile = AgentConfig
	}

	data, err := os.ReadFile(configFile)
	if err != nil {
		return nil, fmt.Errorf("读取配置文件失败: %w", err)
	}

	cfg := &Config{}
	if err := yaml.Unmarshal(data, cfg); err != nil {
		return nil, fmt.Errorf("解析配置文件失败: %w", err)
	}

	// 设置默认值
	cfg.SetDefaults()

	// 验证必填字段
	if cfg.ServerURL == "" {
		return nil, fmt.Errorf("server_url 不能为空")
	}
	if cfg.AgentToken == "" {
		return nil, fmt.Errorf("agent_token 不能为空")
	}

	return cfg, nil
}

// SetDefaults 设置默认值
func (c *Config) SetDefaults() {
	if c.HeartbeatInterval <= 0 {
		c.HeartbeatInterval = 30
	}
	if c.ConfigPollInterval <= 0 {
		c.ConfigPollInterval = 30
	}
	if c.LogLevel == "" {
		c.LogLevel = "info"
	}
}

// GetVectorBin 获取 Vector 二进制路径
func GetVectorBin() string {
	return VectorBin
}

// GetVectorConfig 获取 Vector 配置路径
func GetVectorConfig() string {
	return VectorConfig
}

// GetVectorService 获取 Vector 服务名
func GetVectorService() string {
	return VectorService
}

// GetDataDir 获取数据目录
func GetDataDir() string {
	return DataDir
}

// GetLogDir 获取日志目录
func GetLogDir() string {
	return LogDir
}

// DefaultConfigExample 生成默认配置示例（极简版）
func DefaultConfigExample(serverURL, token string) string {
	return fmt.Sprintf(`# Vector Agent 配置文件
# 只需配置以下两项即可

server_url: "%s"
agent_token: "%s"

# 以下为可选配置，使用默认值即可
# heartbeat_interval: 30
# config_poll_interval: 30
# log_level: "info"
`, serverURL, token)
}
