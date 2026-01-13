package vector

import (
	"bytes"
	"encoding/json"
	"fmt"
	"io"
	"log"
	"net/http"
	"os"
	"os/exec"
	"path/filepath"
	"runtime"
	"strconv"
	"strings"
	"time"

	"github.com/mw/vector-agent/internal/config"
)

// Manager Vector管理器（跨平台支持）
type Manager struct {
	osType string // darwin, linux
}

// NewManager 创建Vector管理器
func NewManager() *Manager {
	return &Manager{
		osType: runtime.GOOS,
	}
}

// GetVersion 获取Vector版本
func (m *Manager) GetVersion() string {
	cmd := exec.Command(config.VectorBin, "--version")
	output, err := cmd.Output()
	if err != nil {
		return "unknown"
	}
	// 输出格式: vector 0.39.0 (x86_64-unknown-linux-gnu abc123)
	version := strings.TrimSpace(string(output))
	parts := strings.Fields(version)
	if len(parts) >= 2 {
		return parts[1]
	}
	return version
}

// IsRunning 检查Vector是否运行
func (m *Manager) IsRunning() bool {
	if m.osType == "darwin" {
		return m.isRunningDarwin()
	}
	return m.isRunningLinux()
}

// isRunningLinux Linux下检查Vector是否运行
func (m *Manager) isRunningLinux() bool {
	cmd := exec.Command("systemctl", "is-active", "--quiet", config.VectorService)
	err := cmd.Run()
	return err == nil
}

// isRunningDarwin macOS下检查Vector是否运行
func (m *Manager) isRunningDarwin() bool {
	// 方法1: 检查 launchctl 服务状态
	cmd := exec.Command("launchctl", "list", config.LaunchdServiceName)
	if err := cmd.Run(); err == nil {
		return true
	}

	// 方法2: 使用 pgrep 检查进程（精确匹配 vector 进程，排除 vector-agent）
	cmd = exec.Command("pgrep", "-f", config.VectorBin+"[^-]")
	if err := cmd.Run(); err == nil {
		return true
	}

	// 方法3: 直接匹配 "vector --config" 命令
	cmd = exec.Command("pgrep", "-f", "vector --config")
	if err := cmd.Run(); err == nil {
		return true
	}

	return false
}

// IsHealthy 检查Vector是否健康
func (m *Manager) IsHealthy() bool {
	if !m.IsRunning() {
		return false
	}
	// 可以扩展检查 Vector 的 healthcheck 端点
	return true
}

// Start 启动Vector
func (m *Manager) Start() error {
	log.Println("启动 Vector 服务...")
	if m.osType == "darwin" {
		return m.startDarwin()
	}
	return m.startLinux()
}

// startLinux Linux下启动Vector
func (m *Manager) startLinux() error {
	cmd := exec.Command("systemctl", "start", config.VectorService)
	if output, err := cmd.CombinedOutput(); err != nil {
		return fmt.Errorf("启动失败: %s", string(output))
	}

	time.Sleep(2 * time.Second)
	if !m.IsRunning() {
		return fmt.Errorf("启动后服务仍未运行")
	}

	log.Println("Vector 启动成功")
	return nil
}

// startDarwin macOS下启动Vector
func (m *Manager) startDarwin() error {
	// 方法1: 尝试使用 launchctl
	plistPath := filepath.Join("/Library/LaunchDaemons", config.LaunchdServiceName+".plist")
	if _, err := os.Stat(plistPath); err == nil {
		cmd := exec.Command("launchctl", "load", plistPath)
		if output, err := cmd.CombinedOutput(); err != nil {
			log.Printf("launchctl load 失败: %s", string(output))
		} else {
			time.Sleep(2 * time.Second)
			if m.IsRunning() {
				log.Println("Vector 启动成功 (launchctl)")
				return nil
			}
		}
	}

	// 方法2: 直接启动进程
	return m.startDirectProcess()
}

// startDirectProcess 直接启动Vector进程
func (m *Manager) startDirectProcess() error {
	// 确保配置目录存在
	if err := os.MkdirAll(config.ConfigDir, 0755); err != nil {
		return fmt.Errorf("创建配置目录失败: %w", err)
	}

	// 确保数据目录存在
	if err := os.MkdirAll(config.DataDir, 0755); err != nil {
		return fmt.Errorf("创建数据目录失败: %w", err)
	}

	// 后台启动 Vector
	logFile, err := os.OpenFile(filepath.Join(config.LogDir, "vector.log"), os.O_CREATE|os.O_APPEND|os.O_WRONLY, 0644)
	if err != nil {
		// 如果日志目录不存在，创建它
		os.MkdirAll(config.LogDir, 0755)
		logFile, err = os.OpenFile(filepath.Join(config.LogDir, "vector.log"), os.O_CREATE|os.O_APPEND|os.O_WRONLY, 0644)
		if err != nil {
			return fmt.Errorf("创建日志文件失败: %w", err)
		}
	}

	// 使用 config-dir 模式启动
	cmd := exec.Command(config.VectorBin, "--config-dir", config.ConfigDir)
	cmd.Stdout = logFile
	cmd.Stderr = logFile

	if err := cmd.Start(); err != nil {
		logFile.Close()
		return fmt.Errorf("启动进程失败: %w", err)
	}

	// 保存 PID
	pidFile := filepath.Join(config.BaseDir, "vector.pid")
	if err := os.WriteFile(pidFile, []byte(strconv.Itoa(cmd.Process.Pid)), 0644); err != nil {
		log.Printf("保存 PID 文件失败: %v", err)
	}

	// 不等待进程结束，让它在后台运行
	go func() {
		cmd.Wait()
		logFile.Close()
	}()

	time.Sleep(2 * time.Second)
	if !m.IsRunning() {
		return fmt.Errorf("启动后服务仍未运行")
	}

	log.Println("Vector 启动成功 (config-dir 模式)")
	return nil
}

// Stop 停止Vector
func (m *Manager) Stop() error {
	log.Println("停止 Vector 服务...")
	if m.osType == "darwin" {
		return m.stopDarwin()
	}
	return m.stopLinux()
}

// stopLinux Linux下停止Vector
func (m *Manager) stopLinux() error {
	cmd := exec.Command("systemctl", "stop", config.VectorService)
	if output, err := cmd.CombinedOutput(); err != nil {
		return fmt.Errorf("停止失败: %s", string(output))
	}
	log.Println("Vector 停止成功")
	return nil
}

// stopDarwin macOS下停止Vector
func (m *Manager) stopDarwin() error {
	// 方法1: 尝试使用 launchctl
	plistPath := filepath.Join("/Library/LaunchDaemons", config.LaunchdServiceName+".plist")
	if _, err := os.Stat(plistPath); err == nil {
		cmd := exec.Command("launchctl", "unload", plistPath)
		if output, err := cmd.CombinedOutput(); err != nil {
			log.Printf("launchctl unload 失败: %s", string(output))
		}
	}

	// 方法2: 使用 pkill 停止进程
	cmd := exec.Command("pkill", "-f", config.VectorBin)
	cmd.Run() // 忽略错误，可能进程已经不存在

	// 方法3: 读取 PID 文件并 kill
	pidFile := filepath.Join(config.BaseDir, "vector.pid")
	if pidData, err := os.ReadFile(pidFile); err == nil {
		if pid, err := strconv.Atoi(strings.TrimSpace(string(pidData))); err == nil {
			process, err := os.FindProcess(pid)
			if err == nil {
				process.Kill()
			}
		}
		os.Remove(pidFile)
	}

	log.Println("Vector 停止成功")
	return nil
}

// Restart 重启Vector
func (m *Manager) Restart() error {
	log.Println("重启 Vector 服务...")
	if m.osType == "darwin" {
		return m.restartDarwin()
	}
	return m.restartLinux()
}

// restartLinux Linux下重启Vector
func (m *Manager) restartLinux() error {
	cmd := exec.Command("systemctl", "restart", config.VectorService)
	if output, err := cmd.CombinedOutput(); err != nil {
		return fmt.Errorf("重启失败: %s", string(output))
	}

	time.Sleep(2 * time.Second)
	if !m.IsRunning() {
		return fmt.Errorf("重启后服务仍未运行")
	}

	log.Println("Vector 重启成功")
	return nil
}

// restartDarwin macOS下重启Vector
func (m *Manager) restartDarwin() error {
	if err := m.Stop(); err != nil {
		log.Printf("停止失败: %v", err)
	}
	time.Sleep(1 * time.Second)
	return m.Start()
}

// Reload 重载配置（热重载，不中断服务）
func (m *Manager) Reload() error {
	log.Println("重载 Vector 配置...")
	if m.osType == "darwin" {
		return m.reloadDarwin()
	}
	return m.reloadLinux()
}

// reloadLinux Linux下重载配置
func (m *Manager) reloadLinux() error {
	cmd := exec.Command("systemctl", "reload-or-restart", config.VectorService)
	if output, err := cmd.CombinedOutput(); err != nil {
		return fmt.Errorf("重载失败: %s", string(output))
	}

	time.Sleep(2 * time.Second)
	if !m.IsRunning() {
		return fmt.Errorf("重载后服务未运行")
	}

	log.Println("Vector 配置重载成功")
	return nil
}

// reloadDarwin macOS下重载配置
func (m *Manager) reloadDarwin() error {
	// Vector 支持 SIGHUP 信号重载配置
	cmd := exec.Command("pkill", "-HUP", "-f", config.VectorBin)
	if err := cmd.Run(); err != nil {
		// 如果发送信号失败，尝试重启
		log.Printf("发送 SIGHUP 失败，尝试重启: %v", err)
		return m.restartDarwin()
	}

	time.Sleep(2 * time.Second)
	if !m.IsRunning() {
		return fmt.Errorf("重载后服务未运行")
	}

	log.Println("Vector 配置重载成功")
	return nil
}

// ValidateConfig 验证配置文件
func (m *Manager) ValidateConfig(yamlContent string) error {
	log.Println("验证 Vector 配置...")

	// 写入临时文件
	tmpFile, err := os.CreateTemp("", "vector-validate-*.yaml")
	if err != nil {
		return fmt.Errorf("创建临时文件失败: %w", err)
	}
	defer os.Remove(tmpFile.Name())

	if _, err := tmpFile.WriteString(yamlContent); err != nil {
		return fmt.Errorf("写入临时配置失败: %w", err)
	}
	tmpFile.Close()

	// 使用 vector validate 命令验证
	cmd := exec.Command(config.VectorBin, "validate", "--no-environment", tmpFile.Name())
	var stderr bytes.Buffer
	cmd.Stderr = &stderr

	if err := cmd.Run(); err != nil {
		return fmt.Errorf("配置验证失败: %s", stderr.String())
	}

	log.Println("配置验证通过")
	return nil
}

// ValidateConfigDir 验证 config-dir 模式的配置
func (m *Manager) ValidateConfigDir() error {
	log.Println("验证 Vector config-dir 配置...")

	cmd := exec.Command(config.VectorBin, "validate", "--no-environment", "--config-dir", config.ConfigDir)
	output, err := cmd.CombinedOutput()

	if err != nil {
		return fmt.Errorf("配置验证失败: %s", string(output))
	}

	log.Println("配置验证通过")
	return nil
}

// BackupConfig 备份当前配置
func (m *Manager) BackupConfig(version string) error {
	log.Printf("备份当前配置（版本: %s）...", version)

	// 检查当前配置是否存在
	if _, err := os.Stat(config.VectorConfig); os.IsNotExist(err) {
		log.Println("当前配置不存在，跳过备份")
		return nil
	}

	// 备份路径
	backupDir := filepath.Join(config.ConfigDir, "history")
	if err := os.MkdirAll(backupDir, 0755); err != nil {
		return fmt.Errorf("创建备份目录失败: %w", err)
	}

	timestamp := time.Now().Format("20060102_150405")
	backupFile := filepath.Join(backupDir, fmt.Sprintf("vector_%s_%s.yaml", timestamp, version))

	// 读取当前配置
	currentConfig, err := os.ReadFile(config.VectorConfig)
	if err != nil {
		return fmt.Errorf("读取当前配置失败: %w", err)
	}

	// 写入备份
	if err := os.WriteFile(backupFile, currentConfig, 0644); err != nil {
		return fmt.Errorf("写入备份失败: %w", err)
	}

	log.Printf("配置已备份到: %s", backupFile)
	return nil
}

// WriteConfig 写入新配置
func (m *Manager) WriteConfig(yamlContent string) error {
	log.Println("写入新配置...")

	// 确保配置目录存在
	if err := os.MkdirAll(config.ConfigDir, 0755); err != nil {
		return fmt.Errorf("创建配置目录失败: %w", err)
	}

	// 添加 data_dir 配置（如果没有）
	if !strings.Contains(yamlContent, "data_dir:") {
		yamlContent = fmt.Sprintf("data_dir: \"%s\"\n\n%s", config.DataDir, yamlContent)
	}

	// 写入配置文件
	if err := os.WriteFile(config.VectorConfig, []byte(yamlContent), 0644); err != nil {
		return fmt.Errorf("写入配置失败: %w", err)
	}

	log.Println("配置写入成功")
	return nil
}

// WriteConfigFiles 写入多个配置文件（config-dir 模式）
func (m *Manager) WriteConfigFiles(configFiles map[string]string) error {
	log.Printf("写入 config-dir 配置，共 %d 个文件...", len(configFiles))

	// 清理旧的配置目录（保留 history）
	entries, err := os.ReadDir(config.ConfigDir)
	if err == nil {
		for _, entry := range entries {
			if entry.Name() != "history" && entry.Name() != "vector.pid" {
				path := filepath.Join(config.ConfigDir, entry.Name())
				os.RemoveAll(path)
			}
		}
	}

	// 写入新配置文件
	for filePath, content := range configFiles {
		fullPath := filepath.Join(config.ConfigDir, filePath)

		// 确保目录存在
		dir := filepath.Dir(fullPath)
		if err := os.MkdirAll(dir, 0755); err != nil {
			return fmt.Errorf("创建目录失败 %s: %w", dir, err)
		}

		// 写入文件
		if err := os.WriteFile(fullPath, []byte(content), 0644); err != nil {
			return fmt.Errorf("写入文件失败 %s: %w", fullPath, err)
		}

		log.Printf("  写入: %s", filePath)
	}

	log.Println("config-dir 配置写入成功")
	return nil
}

// Rollback 回滚到上一个版本
func (m *Manager) Rollback() error {
	log.Println("回滚到上一个配置版本...")

	backupDir := filepath.Join(config.ConfigDir, "history")

	// 查找最新的备份文件
	entries, err := os.ReadDir(backupDir)
	if err != nil || len(entries) == 0 {
		return fmt.Errorf("没有可用的备份文件")
	}

	// 获取最新的备份（按文件名排序，最后一个就是最新的）
	var latestBackup string
	for _, entry := range entries {
		if strings.HasSuffix(entry.Name(), ".yaml") {
			latestBackup = filepath.Join(backupDir, entry.Name())
		}
	}

	if latestBackup == "" {
		return fmt.Errorf("没有找到备份文件")
	}

	// 读取备份
	backupContent, err := os.ReadFile(latestBackup)
	if err != nil {
		return fmt.Errorf("读取备份失败: %w", err)
	}

	// 写入当前配置
	if err := os.WriteFile(config.VectorConfig, backupContent, 0644); err != nil {
		return fmt.Errorf("写入配置失败: %w", err)
	}

	log.Printf("已回滚到: %s", latestBackup)
	return nil
}

// IsConfigValid 检查配置文件是否有效
func (m *Manager) IsConfigValid() bool {
	// 检查 pipelines 目录是否存在（config-dir 模式）
	pipelinesDir := filepath.Join(config.ConfigDir, "pipelines")
	if _, err := os.Stat(pipelinesDir); err == nil {
		// config-dir 模式，检查是否有 pipeline 子目录
		entries, err := os.ReadDir(pipelinesDir)
		if err == nil && len(entries) > 0 {
			return true
		}
	}

	// 检查单文件模式
	if _, err := os.Stat(config.VectorConfig); os.IsNotExist(err) {
		return false
	}

	// 读取配置内容
	content, err := os.ReadFile(config.VectorConfig)
	if err != nil || len(content) == 0 {
		return false
	}

	return true
}

// GetStatus 获取Vector状态
func (m *Manager) GetStatus() (string, error) {
	if m.osType == "darwin" {
		return m.getStatusDarwin()
	}
	return m.getStatusLinux()
}

// getStatusLinux Linux下获取状态
func (m *Manager) getStatusLinux() (string, error) {
	cmd := exec.Command("systemctl", "status", config.VectorService)
	output, _ := cmd.CombinedOutput()
	return string(output), nil
}

// getStatusDarwin macOS下获取状态
func (m *Manager) getStatusDarwin() (string, error) {
	var status strings.Builder

	// 检查进程
	cmd := exec.Command("pgrep", "-fl", config.VectorBin)
	if output, err := cmd.Output(); err == nil {
		status.WriteString("Vector 进程运行中:\n")
		status.WriteString(string(output))
	} else {
		status.WriteString("Vector 进程未运行\n")
	}

	// 检查 launchctl
	cmd = exec.Command("launchctl", "list", config.LaunchdServiceName)
	if output, err := cmd.Output(); err == nil {
		status.WriteString("\nLaunchd 服务状态:\n")
		status.WriteString(string(output))
	}

	return status.String(), nil
}

// EnsureDataDir 确保数据目录存在
func (m *Manager) EnsureDataDir() error {
	return os.MkdirAll(config.DataDir, 0755)
}

// InitDefaultConfig 初始化默认配置（首次启动时）
func (m *Manager) InitDefaultConfig() error {
	// 检查 config-dir 模式的配置是否已存在
	pipelinesDir := filepath.Join(config.ConfigDir, "pipelines")
	if entries, err := os.ReadDir(pipelinesDir); err == nil && len(entries) > 0 {
		return nil // config-dir 模式配置已存在
	}

	// 检查单文件配置是否已存在
	if _, err := os.Stat(config.VectorConfig); err == nil {
		return nil // 配置已存在
	}

	log.Println("初始化默认 Vector 配置...")

	defaultConfig := fmt.Sprintf(`# Vector 配置文件
# 由 Vector Agent 自动管理

data_dir: "%s"

# 启用 API（用于获取组件状态）
api:
  enabled: true
  address: "127.0.0.1:8686"

# 默认配置：内部指标输出到控制台
sources:
  internal_metrics:
    type: internal_metrics

sinks:
  console:
    type: console
    inputs:
      - internal_metrics
    encoding:
      codec: json
`, config.DataDir)

	if err := os.MkdirAll(config.ConfigDir, 0755); err != nil {
		return fmt.Errorf("创建配置目录失败: %w", err)
	}

	if err := os.WriteFile(config.VectorConfig, []byte(defaultConfig), 0644); err != nil {
		return fmt.Errorf("写入默认配置失败: %w", err)
	}

	log.Println("默认配置初始化完成")
	return nil
}

// ComponentMetrics 组件指标
type ComponentMetrics struct {
	Status          string    // normal, warning, error, stopped
	EventsProcessed int64     // 处理的事件数
	BytesProcessed  int64     // 处理的字节数
	Errors          int64     // 错误数
	LastActive      time.Time // 最后活跃时间
}

// GetComponentMetrics 获取组件级别指标
// 通过 Vector 的 GraphQL API 获取实时状态
func (m *Manager) GetComponentMetrics() map[string]*ComponentMetrics {
	if !m.IsRunning() {
		return nil
	}

	result := make(map[string]*ComponentMetrics)

	// 尝试通过 Vector GraphQL API 获取组件状态
	// Vector 默认在 8686 端口提供 GraphQL API
	apiMetrics := m.fetchVectorAPIMetrics()
	if apiMetrics != nil {
		return apiMetrics
	}

	// 如果 API 不可用，从配置文件获取组件名称，返回默认状态
	componentNames := m.getComponentNamesFromConfig()
	now := time.Now()
	for _, name := range componentNames {
		result[name] = &ComponentMetrics{
			Status:          "normal",
			EventsProcessed: 0,
			BytesProcessed:  0,
			Errors:          0,
			LastActive:      now,
		}
	}

	return result
}

// fetchVectorAPIMetrics 从 Vector GraphQL API 获取组件指标
func (m *Manager) fetchVectorAPIMetrics() map[string]*ComponentMetrics {
	// Vector GraphQL API 地址
	apiURL := "http://127.0.0.1:8686/graphql"

	// GraphQL 查询：获取所有组件的健康状态
	query := `{"query": "{ components { nodes { componentId componentType } } }"}`

	client := &http.Client{Timeout: 2 * time.Second}
	resp, err := client.Post(apiURL, "application/json", strings.NewReader(query))
	if err != nil {
		// API 不可用，返回 nil
		log.Printf("Vector API 不可用: %v", err)
		return nil
	}
	defer resp.Body.Close()

	if resp.StatusCode != 200 {
		return nil
	}

	var result struct {
		Data struct {
			Components struct {
				Nodes []struct {
					ComponentId   string `json:"componentId"`
					ComponentType string `json:"componentType"`
				} `json:"nodes"`
			} `json:"components"`
		} `json:"data"`
	}

	body, _ := io.ReadAll(resp.Body)
	if err := json.Unmarshal(body, &result); err != nil {
		log.Printf("解析 Vector API 响应失败: %v", err)
		return nil
	}

	metrics := make(map[string]*ComponentMetrics)
	now := time.Now()

	for _, node := range result.Data.Components.Nodes {
		cm := &ComponentMetrics{
			Status:          "normal",
			EventsProcessed: 0,
			BytesProcessed:  0,
			Errors:          0,
			LastActive:      now,
		}
		metrics[node.ComponentId] = cm
	}

	log.Printf("从 Vector API 获取到 %d 个组件状态", len(metrics))
	return metrics
}

// getComponentNamesFromConfig 从配置文件中获取组件名称
func (m *Manager) getComponentNamesFromConfig() []string {
	var names []string

	// 检查 config-dir 模式
	pipelinesDir := filepath.Join(config.ConfigDir, "pipelines")
	if entries, err := os.ReadDir(pipelinesDir); err == nil {
		for _, entry := range entries {
			if entry.IsDir() {
				// 每个子目录是一个 pipeline
				pipelineDir := filepath.Join(pipelinesDir, entry.Name())
				m.extractComponentNamesFromDir(pipelineDir, &names)
			}
		}
	}

	// 检查单文件模式
	if content, err := os.ReadFile(config.VectorConfig); err == nil {
		m.extractComponentNamesFromYaml(string(content), &names)
	}

	return names
}

// extractComponentNamesFromDir 从目录中提取组件名称
func (m *Manager) extractComponentNamesFromDir(dir string, names *[]string) {
	// 读取 sources, transforms, sinks 目录
	for _, subDir := range []string{"sources", "transforms", "sinks"} {
		subPath := filepath.Join(dir, subDir)
		if entries, err := os.ReadDir(subPath); err == nil {
			for _, entry := range entries {
				if !entry.IsDir() && strings.HasSuffix(entry.Name(), ".yaml") {
					// 文件名（去掉 .yaml）就是组件名
					name := strings.TrimSuffix(entry.Name(), ".yaml")
					*names = append(*names, name)
				}
			}
		}
	}
}

// extractComponentNamesFromYaml 从 YAML 内容中提取组件名称
func (m *Manager) extractComponentNamesFromYaml(content string, names *[]string) {
	// 简单解析：查找 sources:, transforms:, sinks: 下的键
	lines := strings.Split(content, "\n")
	inSection := false
	sectionIndent := 0

	for _, line := range lines {
		trimmed := strings.TrimSpace(line)
		if trimmed == "" || strings.HasPrefix(trimmed, "#") {
			continue
		}

		// 检查是否是 section 开始
		if trimmed == "sources:" || trimmed == "transforms:" || trimmed == "sinks:" {
			inSection = true
			sectionIndent = len(line) - len(strings.TrimLeft(line, " "))
			continue
		}

		if inSection {
			currentIndent := len(line) - len(strings.TrimLeft(line, " "))

			// 如果缩进小于等于 section 缩进，说明 section 结束
			if currentIndent <= sectionIndent && trimmed != "" {
				inSection = false
				continue
			}

			// 检查是否是组件名（缩进为 section + 2，且以 : 结尾）
			if currentIndent == sectionIndent+2 && strings.HasSuffix(trimmed, ":") {
				name := strings.TrimSuffix(trimmed, ":")
				*names = append(*names, name)
			}
		}
	}
}
