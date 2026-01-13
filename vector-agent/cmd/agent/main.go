package main

import (
	"flag"
	"fmt"
	"log"
	"os"
	"os/signal"
	"syscall"
	"time"

	"github.com/mw/vector-agent/internal/agent"
	"github.com/mw/vector-agent/internal/config"
)

const (
	Version   = "1.0.0"
	BuildTime = "2025-12-28"
)

// GetVersion 获取版本号（供其他包使用）
func GetVersion() string {
	return Version
}

func main() {
	// 解析命令行参数
	configFile := flag.String("config", config.AgentConfig, "配置文件路径")
	showVersion := flag.Bool("version", false, "显示版本信息")
	genConfig := flag.Bool("gen-config", false, "生成配置文件示例")
	serverURL := flag.String("server", "", "服务器地址（用于生成配置）")
	token := flag.String("token", "", "Agent Token（用于生成配置）")
	flag.Parse()

	// 显示版本
	if *showVersion {
		fmt.Printf("Vector Agent v%s (built: %s)\n", Version, BuildTime)
		fmt.Printf("安装目录: %s\n", config.InstallDir)
		fmt.Printf("Vector 路径: %s\n", config.VectorBin)
		fmt.Printf("配置路径: %s\n", config.VectorConfig)
		os.Exit(0)
	}

	// 生成配置文件
	if *genConfig {
		if *serverURL == "" || *token == "" {
			fmt.Println("用法: vector-agent -gen-config -server <服务器地址> -token <Token>")
			fmt.Println("示例: vector-agent -gen-config -server http://192.168.1.100:8080 -token abc123")
			os.Exit(1)
		}
		configContent := config.DefaultConfigExample(*serverURL, *token)
		fmt.Println(configContent)
		os.Exit(0)
	}

	// 检查配置文件是否存在
	if _, err := os.Stat(*configFile); os.IsNotExist(err) {
		log.Fatalf("配置文件不存在: %s\n请先创建配置文件或使用 -gen-config 生成", *configFile)
	}

	// 加载配置
	cfg, err := config.Load(*configFile)
	if err != nil {
		log.Fatalf("加载配置失败: %v", err)
	}

	// 初始化日志
	setupLogger(cfg.LogLevel)

	log.Printf("========================================")
	log.Printf("Vector Agent v%s 启动中...", Version)
	log.Printf("========================================")
	log.Printf("配置文件: %s", *configFile)
	log.Printf("服务器地址: %s", cfg.ServerURL)
	log.Printf("Vector 路径: %s", config.VectorBin)
	log.Printf("Vector 配置: %s", config.VectorConfig)

	// 创建Agent实例
	ag, err := agent.New(cfg, Version)
	if err != nil {
		log.Fatalf("创建Agent失败: %v", err)
	}

	// 启动Agent
	if err := ag.Start(); err != nil {
		log.Fatalf("启动Agent失败: %v", err)
	}

	log.Println("Vector Agent 启动成功，等待服务器下发配置...")

	// 等待退出信号
	sigChan := make(chan os.Signal, 1)
	signal.Notify(sigChan, syscall.SIGINT, syscall.SIGTERM, syscall.SIGHUP)

	sig := <-sigChan
	log.Printf("接收到信号: %v, 准备退出...", sig)

	// 优雅关闭
	shutdownTimeout := 10 * time.Second
	shutdownDone := make(chan struct{})

	go func() {
		ag.Stop()
		close(shutdownDone)
	}()

	select {
	case <-shutdownDone:
		log.Println("Agent 已优雅关闭")
	case <-time.After(shutdownTimeout):
		log.Println("关闭超时，强制退出")
	}
}

func setupLogger(level string) {
	log.SetFlags(log.LstdFlags | log.Lshortfile)

	// 确保日志目录存在
	if err := os.MkdirAll(config.LogDir, 0755); err == nil {
		logFile := config.LogDir + "/agent.log"
		f, err := os.OpenFile(logFile, os.O_APPEND|os.O_CREATE|os.O_WRONLY, 0644)
		if err == nil {
			// 同时输出到控制台和文件
			log.SetOutput(f)
		}
	}
}
