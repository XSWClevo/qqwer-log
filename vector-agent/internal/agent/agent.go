package agent

import (
	"context"
	"fmt"
	"log"
	"sync"
	"time"

	"github.com/mw/vector-agent/internal/collector"
	"github.com/mw/vector-agent/internal/config"
	"github.com/mw/vector-agent/internal/upgrade"
	"github.com/mw/vector-agent/internal/vector"
	"github.com/mw/vector-agent/pkg/api"
)

// Agent 核心结构
type Agent struct {
	config    *config.Config
	client    *api.Client
	vector    *vector.Manager
	upgrader  *upgrade.Upgrader
	collector *collector.MetricsCollector

	ctx    context.Context
	cancel context.CancelFunc
	wg     sync.WaitGroup

	startTime       time.Time
	maintenanceMode bool   // 维护模式：禁用自愈
	version         string // Agent 版本号
}

// New 创建Agent实例（带版本号）
func New(cfg *config.Config, version string) (*Agent, error) {
	ctx, cancel := context.WithCancel(context.Background())

	// 创建API客户端
	client := api.NewClient(cfg.ServerURL, cfg.AgentToken)

	// 创建Vector管理器（使用固定路径）
	vectorMgr := vector.NewManager()

	// 创建指标采集器
	metricsCollector := collector.NewMetricsCollector(vectorMgr)

	// 创建升级管理器
	upgrader := upgrade.NewUpgrader()

	return &Agent{
		config:    cfg,
		client:    client,
		vector:    vectorMgr,
		upgrader:  upgrader,
		collector: metricsCollector,
		ctx:       ctx,
		cancel:    cancel,
		startTime: time.Now(),
		version:   version,
	}, nil
}

// Start 启动Agent
func (a *Agent) Start() error {
	log.Println("Agent 启动中...")

	// 0. 确保数据目录存在
	if err := a.vector.EnsureDataDir(); err != nil {
		log.Printf("创建数据目录失败: %v", err)
	}

	// 1. 初始化默认配置（如果不存在）
	if err := a.vector.InitDefaultConfig(); err != nil {
		log.Printf("初始化默认配置失败: %v", err)
	}

	// 2. 注册到服务器
	if err := a.register(); err != nil {
		return fmt.Errorf("注册失败: %w", err)
	}

	// 3. 启动心跳协程
	a.wg.Add(1)
	go a.heartbeatLoop()

	// 4. 启动配置监听协程
	a.wg.Add(1)
	go a.configWatchLoop()

	// 5. 启动命令监听协程
	a.wg.Add(1)
	go a.commandWatchLoop()

	// 6. 启动指标采集协程
	a.wg.Add(1)
	go a.metricsCollectLoop()

	// 7. 启动自愈协程
	a.wg.Add(1)
	go a.selfHealLoop()

	return nil
}

// Stop 停止Agent
func (a *Agent) Stop() {
	log.Println("Agent 停止中...")
	a.cancel()
	a.wg.Wait()
	log.Println("Agent 已停止")
}

// register 注册到服务器
func (a *Agent) register() error {
	log.Println("正在注册到服务器...")

	sysInfo := a.collector.CollectSystemInfo()

	req := api.RegisterRequest{
		Hostname:      sysInfo.Hostname,
		IPAddress:     sysInfo.IPAddress,
		AgentVersion:  a.version,
		VectorVersion: a.vector.GetVersion(),
		OSType:        sysInfo.OSType,
		OSVersion:     sysInfo.OSVersion,
		CPUCores:      sysInfo.CPUCores,
		TotalMemoryMB: sysInfo.TotalMemoryMB,
	}

	resp, err := a.client.Register(&req)
	if err != nil {
		return err
	}

	log.Printf("注册成功，Host ID: %s", resp.HostID)
	return nil
}

// heartbeatLoop 心跳循环
func (a *Agent) heartbeatLoop() {
	defer a.wg.Done()

	ticker := time.NewTicker(time.Duration(a.config.HeartbeatInterval) * time.Second)
	defer ticker.Stop()

	log.Printf("心跳协程已启动，间隔: %d秒", a.config.HeartbeatInterval)

	// 立即发送一次心跳
	a.sendHeartbeat()

	for {
		select {
		case <-a.ctx.Done():
			log.Println("心跳协程已停止")
			return
		case <-ticker.C:
			if err := a.sendHeartbeat(); err != nil {
				log.Printf("发送心跳失败: %v", err)
			}
		}
	}
}

// sendHeartbeat 发送心跳
func (a *Agent) sendHeartbeat() error {
	status := "online"
	if !a.vector.IsRunning() {
		status = "error"
	}

	req := api.HeartbeatRequest{
		AgentUptime:   int64(time.Since(a.startTime).Seconds()),
		VectorRunning: a.vector.IsRunning(),
		Status:        status,
	}

	return a.client.Heartbeat(&req)
}

// configWatchLoop 配置监听循环
func (a *Agent) configWatchLoop() {
	defer a.wg.Done()

	ticker := time.NewTicker(time.Duration(a.config.ConfigPollInterval) * time.Second)
	defer ticker.Stop()

	log.Printf("配置监听协程已启动，间隔: %d秒", a.config.ConfigPollInterval)

	var lastConfigVersion string

	for {
		select {
		case <-a.ctx.Done():
			log.Println("配置监听协程已停止")
			return
		case <-ticker.C:
			configResp, err := a.client.FetchConfig()
			if err != nil {
				log.Printf("拉取配置失败: %v", err)
				continue
			}

			// 检查配置是否有更新
			if configResp != nil && configResp.Version != "" && configResp.Version != lastConfigVersion {
				log.Printf("检测到新配置版本: %s -> %s", lastConfigVersion, configResp.Version)

				// 上报开始部署
				a.reportConfigDeployStatus(configResp.DeploymentID, configResp.Version, "deploying", "")

				if err := a.applyConfig(configResp); err != nil {
					log.Printf("应用配置失败: %v", err)
					a.reportConfigDeployStatus(configResp.DeploymentID, configResp.Version, "failed", err.Error())
				} else {
					log.Printf("配置应用成功: %s", configResp.Version)
					lastConfigVersion = configResp.Version
					a.reportConfigDeployStatus(configResp.DeploymentID, configResp.Version, "success", "")
				}
			}
		}
	}
}

// applyConfig 应用配置
func (a *Agent) applyConfig(configResp *api.ConfigResponse) error {
	log.Printf("应用配置: %s", configResp.Version)

	// 判断使用哪种模式
	useConfigDir := len(configResp.ConfigFiles) > 0

	if useConfigDir {
		// config-dir 模式：多文件
		log.Println("使用 config-dir 模式")

		// 1. 写入配置文件
		if err := a.vector.WriteConfigFiles(configResp.ConfigFiles); err != nil {
			return fmt.Errorf("写入配置文件失败: %w", err)
		}

		// 2. 验证配置
		if err := a.vector.ValidateConfigDir(); err != nil {
			return fmt.Errorf("配置验证失败: %w", err)
		}
	} else {
		// 单文件模式（兼容旧版本）
		log.Println("使用单文件模式")

		// 1. 验证配置
		if err := a.vector.ValidateConfig(configResp.YAMLContent); err != nil {
			return fmt.Errorf("配置验证失败: %w", err)
		}

		// 2. 备份当前配置
		if err := a.vector.BackupConfig(configResp.Version); err != nil {
			log.Printf("备份配置失败（继续执行）: %v", err)
		}

		// 3. 写入新配置
		if err := a.vector.WriteConfig(configResp.YAMLContent); err != nil {
			return fmt.Errorf("写入配置失败: %w", err)
		}
	}

	// 4. 根据部署模式重载或重启
	var err error
	if configResp.DeployMode == "reload" {
		err = a.vector.Reload()
	} else {
		err = a.vector.Restart()
	}

	if err != nil {
		// 回滚（仅单文件模式支持）
		if !useConfigDir {
			log.Println("重载/重启失败，正在回滚...")
			a.vector.Rollback()
			a.vector.Restart()
		}
		return fmt.Errorf("重载Vector失败: %w", err)
	}

	// 5. 健康检查
	time.Sleep(5 * time.Second)
	if !a.vector.IsHealthy() {
		if !useConfigDir {
			log.Println("健康检查失败，正在回滚...")
			a.vector.Rollback()
			a.vector.Restart()
		}
		return fmt.Errorf("配置导致Vector异常，已回滚")
	}

	return nil
}

// reportConfigDeployStatus 上报配置部署状态
func (a *Agent) reportConfigDeployStatus(deploymentID, version, status, errorMsg string) {
	req := api.ConfigDeployStatusRequest{
		DeploymentID:  deploymentID,
		ConfigVersion: version,
		Status:        status,
		ErrorMessage:  errorMsg,
	}

	if err := a.client.ReportConfigDeployStatus(&req); err != nil {
		log.Printf("上报配置部署状态失败: %v", err)
	}
}

// metricsCollectLoop 指标采集循环
func (a *Agent) metricsCollectLoop() {
	defer a.wg.Done()

	ticker := time.NewTicker(60 * time.Second)
	defer ticker.Stop()

	log.Println("指标采集协程已启动，间隔: 60秒")

	for {
		select {
		case <-a.ctx.Done():
			log.Println("指标采集协程已停止")
			return
		case <-ticker.C:
			metrics := a.collector.Collect()

			req := api.MetricsReportRequest{
				CollectedAt:         api.LocalTime(time.Now()),
				CPUUsagePercent:     metrics.CPUUsagePercent,
				MemoryUsagePercent:  metrics.MemoryUsagePercent,
				MemoryUsedMB:        metrics.MemoryUsedMB,
				DiskUsagePercent:    metrics.DiskUsagePercent,
				DiskUsedGB:          metrics.DiskUsedGB,
				AgentUptimeSeconds:  int64(time.Since(a.startTime).Seconds()),
				AgentMemoryMB:       metrics.AgentMemoryMB,
				VectorRunning:       metrics.VectorRunning,
				VectorUptimeSeconds: metrics.VectorUptimeSeconds,
			}

			// 添加网卡指标
			if len(metrics.NetworkInterfaces) > 0 {
				req.NetworkInterfaces = make([]api.NetworkInterface, len(metrics.NetworkInterfaces))
				for i, ni := range metrics.NetworkInterfaces {
					req.NetworkInterfaces[i] = api.NetworkInterface{
						Name:        ni.Name,
						BytesSent:   ni.BytesSent,
						BytesRecv:   ni.BytesRecv,
						PacketsSent: ni.PacketsSent,
						PacketsRecv: ni.PacketsRecv,
						Errin:       ni.Errin,
						Errout:      ni.Errout,
					}
				}
			}

			// 添加组件级别指标
			if metrics.ComponentMetrics != nil && len(metrics.ComponentMetrics) > 0 {
				req.ComponentMetrics = make(map[string]*api.ComponentMetrics)
				for name, cm := range metrics.ComponentMetrics {
					req.ComponentMetrics[name] = &api.ComponentMetrics{
						Status:          cm.Status,
						EventsProcessed: cm.EventsProcessed,
						BytesProcessed:  cm.BytesProcessed,
						Errors:          cm.Errors,
						LastActive:      api.LocalTime(cm.LastActive),
					}
				}
			}

			if err := a.client.ReportMetrics(&req); err != nil {
				log.Printf("上报指标失败: %v", err)
			}
		}
	}
}

// selfHealLoop 自愈循环
func (a *Agent) selfHealLoop() {
	defer a.wg.Done()

	ticker := time.NewTicker(60 * time.Second)
	defer ticker.Stop()

	log.Println("自愈协程已启动，间隔: 60秒")

	for {
		select {
		case <-a.ctx.Done():
			log.Println("自愈协程已停止")
			return
		case <-ticker.C:
			a.performHealthCheck()
		}
	}
}

// performHealthCheck 执行健康检查
func (a *Agent) performHealthCheck() {
	// 维护模式下跳过自愈
	if a.maintenanceMode {
		log.Println("维护模式，跳过自愈检查")
		return
	}

	// 1. 检查配置文件完整性
	if !a.vector.IsConfigValid() {
		log.Println("配置文件损坏或不存在，尝试初始化...")
		a.vector.InitDefaultConfig()
	}

	// 2. 检查Vector进程
	if !a.vector.IsRunning() {
		log.Println("Vector进程未运行，尝试启动...")
		if err := a.vector.Start(); err != nil {
			log.Printf("启动Vector失败: %v", err)
			a.client.ReportLog("ERROR", fmt.Sprintf("自愈失败: 启动Vector失败: %v", err))
		} else {
			log.Println("Vector已成功启动")
			a.client.ReportLog("INFO", "自愈成功: Vector已启动")
		}
	}

	// 3. 检查Vector健康状态
	if a.vector.IsRunning() && !a.vector.IsHealthy() {
		log.Println("Vector运行异常，尝试重启...")
		if err := a.vector.Restart(); err != nil {
			log.Printf("重启Vector失败: %v", err)
		} else {
			log.Println("Vector已成功重启")
		}
	}
}

// commandWatchLoop 命令监听循环
func (a *Agent) commandWatchLoop() {
	defer a.wg.Done()

	ticker := time.NewTicker(5 * time.Second) // 每5秒检查一次命令
	defer ticker.Stop()

	log.Println("命令监听协程已启动，间隔: 5秒")

	for {
		select {
		case <-a.ctx.Done():
			log.Println("命令监听协程已停止")
			return
		case <-ticker.C:
			cmd, err := a.client.FetchCommand()
			if err != nil {
				log.Printf("拉取命令失败: %v", err)
				continue
			}

			if cmd != nil {
				log.Printf("收到命令: %s (ID: %s)", cmd.CommandType, cmd.CommandID)
				a.executeCommand(cmd)
			}
		}
	}
}

// executeCommand 执行命令
func (a *Agent) executeCommand(cmd *api.CommandResponse) {
	var err error
	var errMsg string

	switch cmd.CommandType {
	case "start_vector":
		log.Println("执行命令: 启动 Vector")
		a.maintenanceMode = false // 退出维护模式
		err = a.vector.Start()
	case "stop_vector":
		log.Println("执行命令: 停止 Vector")
		a.maintenanceMode = true // 进入维护模式，禁用自愈
		err = a.vector.Stop()
	case "restart_vector":
		log.Println("执行命令: 重启 Vector")
		a.maintenanceMode = false // 退出维护模式
		err = a.vector.Restart()
	case "reload_vector":
		log.Println("执行命令: 重载 Vector 配置")
		err = a.vector.Reload()
	case "upgrade_vector":
		log.Printf("执行命令: 升级 Vector 到 %s", cmd.TargetVersion)
		a.maintenanceMode = true // 升级期间进入维护模式
		err = a.upgrader.UpgradeVector(cmd.DownloadURL, cmd.Checksum, cmd.FileSize)
		if err == nil {
			// 升级成功后重启 Vector
			a.vector.Restart()
			a.maintenanceMode = false
		}
	case "upgrade_agent":
		log.Printf("执行命令: 升级 Agent 到 %s", cmd.TargetVersion)
		err = a.upgrader.UpgradeAgent(cmd.DownloadURL, cmd.Checksum, cmd.FileSize)
		// Agent 升级会触发重启，先上报状态
		if err == nil {
			req := &api.CommandStatusRequest{
				CommandID:    cmd.CommandID,
				Status:       "success",
				ErrorMessage: "",
			}
			a.client.ReportCommandStatus(req)
			log.Println("Agent 即将重启...")
			return // 不再继续执行
		}
	default:
		err = fmt.Errorf("未知命令类型: %s", cmd.CommandType)
	}

	status := "success"
	if err != nil {
		status = "failed"
		errMsg = err.Error()
		log.Printf("命令执行失败: %v", err)
	} else {
		log.Printf("命令执行成功: %s", cmd.CommandType)
	}

	// 上报执行结果
	req := &api.CommandStatusRequest{
		CommandID:    cmd.CommandID,
		Status:       status,
		ErrorMessage: errMsg,
	}
	if err := a.client.ReportCommandStatus(req); err != nil {
		log.Printf("上报命令状态失败: %v", err)
	}
}
