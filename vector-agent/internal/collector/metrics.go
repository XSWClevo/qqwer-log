package collector

import (
	"log"
	"net"
	"os"
	"runtime"
	"time"

	"github.com/mw/vector-agent/internal/vector"
	"github.com/shirou/gopsutil/v3/cpu"
	"github.com/shirou/gopsutil/v3/disk"
	"github.com/shirou/gopsutil/v3/host"
	"github.com/shirou/gopsutil/v3/mem"
	gopsutilnet "github.com/shirou/gopsutil/v3/net"
)

// SystemInfo 系统信息
type SystemInfo struct {
	Hostname      string
	IPAddress     string
	OSType        string
	OSVersion     string
	CPUCores      int
	TotalMemoryMB int64
}

// ComponentMetrics 组件指标
type ComponentMetrics struct {
	Status          string    `json:"status"`          // normal, warning, error, stopped
	EventsProcessed int64     `json:"eventsProcessed"` // 处理的事件数
	BytesProcessed  int64     `json:"bytesProcessed"`  // 处理的字节数
	Errors          int64     `json:"errors"`          // 错误数
	LastActive      time.Time `json:"lastActive"`      // 最后活跃时间
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

// Metrics 指标数据
type Metrics struct {
	// 系统指标
	CPUUsagePercent    float64
	MemoryUsagePercent float64
	MemoryUsedMB       int64
	DiskUsagePercent   float64
	DiskUsedGB         int64

	// 网络指标
	NetworkInterfaces []NetworkInterface

	// Agent指标
	AgentMemoryMB int

	// Vector指标
	VectorRunning       bool
	VectorUptimeSeconds int64

	// 组件级别指标
	ComponentMetrics map[string]*ComponentMetrics
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

// CollectSystemInfo 采集系统信息（注册时使用）
func (c *MetricsCollector) CollectSystemInfo() SystemInfo {
	hostname, _ := os.Hostname()

	hostInfo, _ := host.Info()

	vmStat, _ := mem.VirtualMemory()
	totalMemoryMB := int64(0)
	if vmStat != nil {
		totalMemoryMB = int64(vmStat.Total / 1024 / 1024)
	}

	cpuCores := runtime.NumCPU()

	ipAddress := detectPrimaryIPAddress()

	return SystemInfo{
		Hostname:      hostname,
		IPAddress:     ipAddress,
		OSType:        hostInfo.OS,
		OSVersion:     hostInfo.Platform + " " + hostInfo.PlatformVersion,
		CPUCores:      cpuCores,
		TotalMemoryMB: totalMemoryMB,
	}
}

func detectPrimaryIPAddress() string {
	interfaces, err := net.Interfaces()
	if err != nil {
		return "127.0.0.1"
	}

	candidates := make([]net.IP, 0)
	for _, iface := range interfaces {
		if iface.Flags&net.FlagUp == 0 {
			continue
		}

		addresses, err := iface.Addrs()
		if err != nil {
			continue
		}

		for _, addr := range addresses {
			var ip net.IP
			switch value := addr.(type) {
			case *net.IPNet:
				ip = value.IP
			case *net.IPAddr:
				ip = value.IP
			}

			if ip == nil {
				continue
			}

			candidates = append(candidates, ip)
		}
	}

	return selectPreferredIPAddress(candidates)
}

func selectPreferredIPAddress(candidates []net.IP) string {
	loopbackIPv4 := ""

	for _, ip := range candidates {
		if ip == nil {
			continue
		}

		ipv4 := ip.To4()
		if ipv4 == nil {
			continue
		}

		ipText := ipv4.String()
		if ipv4.IsLoopback() {
			if loopbackIPv4 == "" {
				loopbackIPv4 = ipText
			}
			continue
		}

		return ipText
	}

	if loopbackIPv4 != "" {
		return loopbackIPv4
	}

	return "127.0.0.1"
}

// Collect 采集指标
func (c *MetricsCollector) Collect() Metrics {
	metrics := Metrics{
		ComponentMetrics:  make(map[string]*ComponentMetrics),
		NetworkInterfaces: make([]NetworkInterface, 0),
	}

	// 1. CPU使用率
	if cpuPercent, err := cpu.Percent(0, false); err == nil && len(cpuPercent) > 0 {
		metrics.CPUUsagePercent = cpuPercent[0]
	}

	// 2. 内存使用率
	if vmStat, err := mem.VirtualMemory(); err == nil {
		metrics.MemoryUsagePercent = vmStat.UsedPercent
		metrics.MemoryUsedMB = int64(vmStat.Used / 1024 / 1024)
	}

	// 3. 磁盘使用率
	if diskStat, err := disk.Usage("/"); err == nil {
		metrics.DiskUsagePercent = diskStat.UsedPercent
		metrics.DiskUsedGB = int64(diskStat.Used / 1024 / 1024 / 1024)
	}

	// 4. 网络接口指标
	if netStats, err := gopsutilnet.IOCounters(true); err == nil {
		for _, stat := range netStats {
			// 过滤掉 lo 回环接口和虚拟接口
			if stat.Name == "lo" || stat.Name == "lo0" {
				continue
			}
			// 只保留有流量的接口
			if stat.BytesSent == 0 && stat.BytesRecv == 0 {
				continue
			}
			metrics.NetworkInterfaces = append(metrics.NetworkInterfaces, NetworkInterface{
				Name:        stat.Name,
				BytesSent:   stat.BytesSent,
				BytesRecv:   stat.BytesRecv,
				PacketsSent: stat.PacketsSent,
				PacketsRecv: stat.PacketsRecv,
				Errin:       stat.Errin,
				Errout:      stat.Errout,
			})
		}
	}

	// 5. Agent内存占用
	var m runtime.MemStats
	runtime.ReadMemStats(&m)
	metrics.AgentMemoryMB = int(m.Alloc / 1024 / 1024)

	// 6. Vector状态
	metrics.VectorRunning = c.vectorMgr.IsRunning()
	// Vector运行时长（简化处理，实际可以从systemd获取）
	metrics.VectorUptimeSeconds = 0

	// 7. 收集组件级别指标
	if metrics.VectorRunning {
		componentMetrics := c.vectorMgr.GetComponentMetrics()
		for name, cm := range componentMetrics {
			metrics.ComponentMetrics[name] = &ComponentMetrics{
				Status:          cm.Status,
				EventsProcessed: cm.EventsProcessed,
				BytesProcessed:  cm.BytesProcessed,
				Errors:          cm.Errors,
				LastActive:      cm.LastActive,
			}
		}
	}

	return metrics
}

// LogMetrics 打印指标（用于调试）
func (m *Metrics) LogMetrics() {
	log.Printf("指标: CPU=%.2f%%, 内存=%.2f%% (%dMB), 磁盘=%.2f%% (%dGB), Agent内存=%dMB, Vector运行=%v",
		m.CPUUsagePercent,
		m.MemoryUsagePercent,
		m.MemoryUsedMB,
		m.DiskUsagePercent,
		m.DiskUsedGB,
		m.AgentMemoryMB,
		m.VectorRunning,
	)
}
