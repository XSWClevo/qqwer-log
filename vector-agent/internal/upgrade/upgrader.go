package upgrade

import (
	"archive/tar"
	"compress/gzip"
	"crypto/sha256"
	"encoding/hex"
	"fmt"
	"io"
	"log"
	"net/http"
	"os"
	"os/exec"
	"path/filepath"
	"runtime"
	"strings"
	"time"

	"github.com/mw/vector-agent/internal/config"
)

// Upgrader 升级管理器
type Upgrader struct {
	httpClient *http.Client
}

// NewUpgrader 创建升级管理器
func NewUpgrader() *Upgrader {
	return &Upgrader{
		httpClient: &http.Client{
			Timeout: 10 * time.Minute,
		},
	}
}

// UpgradeBundle 升级 Bundle（包含 Agent 和 Vector）
func (u *Upgrader) UpgradeBundle(downloadURL, checksum string, fileSize int64) error {
	log.Printf("开始升级 Bundle: url=%s", downloadURL)

	// 1. 下载 Bundle 包
	tmpFile := filepath.Join(os.TempDir(), "vector-agent-bundle.tar.gz")
	if err := u.downloadFile(downloadURL, tmpFile, checksum, fileSize); err != nil {
		return fmt.Errorf("下载失败: %w", err)
	}
	defer os.Remove(tmpFile)

	// 2. 解压到临时目录
	extractDir := filepath.Join(os.TempDir(), "vector-agent-bundle-extract")
	os.RemoveAll(extractDir)
	if err := u.extractTarGz(tmpFile, extractDir); err != nil {
		return fmt.Errorf("解压失败: %w", err)
	}
	defer os.RemoveAll(extractDir)

	// 3. 验证解压内容
	newAgentBin := filepath.Join(extractDir, "bin", "vector-agent")
	newVectorBin := filepath.Join(extractDir, "bin", "vector")

	if _, err := os.Stat(newAgentBin); os.IsNotExist(err) {
		return fmt.Errorf("Bundle 中缺少 vector-agent")
	}
	if _, err := os.Stat(newVectorBin); os.IsNotExist(err) {
		return fmt.Errorf("Bundle 中缺少 vector")
	}

	// 4. 备份当前版本
	vectorBackup := config.VectorBin + ".backup"

	if _, err := os.Stat(config.VectorBin); err == nil {
		os.Rename(config.VectorBin, vectorBackup)
	}

	// 5. 先升级 Vector（可以直接替换）
	if err := copyFile(newVectorBin, config.VectorBin); err != nil {
		os.Rename(vectorBackup, config.VectorBin)
		return fmt.Errorf("升级 Vector 失败: %w", err)
	}
	os.Chmod(config.VectorBin, 0755)

	// 验证 Vector
	cmd := exec.Command(config.VectorBin, "--version")
	if err := cmd.Run(); err != nil {
		os.Remove(config.VectorBin)
		os.Rename(vectorBackup, config.VectorBin)
		return fmt.Errorf("Vector 验证失败: %w", err)
	}
	os.Remove(vectorBackup)
	log.Println("Vector 升级成功")

	// 6. 升级 Agent（需要通过脚本，因为不能替换正在运行的程序）
	currentExe, err := os.Executable()
	if err != nil {
		return fmt.Errorf("获取当前路径失败: %w", err)
	}
	currentExe, _ = filepath.EvalSymlinks(currentExe)

	// 复制新 Agent 到临时位置
	tmpAgentFile := filepath.Join(os.TempDir(), "vector-agent-new")
	if err := copyFile(newAgentBin, tmpAgentFile); err != nil {
		return fmt.Errorf("复制 Agent 失败: %w", err)
	}
	os.Chmod(tmpAgentFile, 0755)

	// 创建升级脚本
	upgradeScript := filepath.Join(os.TempDir(), "upgrade-agent.sh")
	scriptContent := fmt.Sprintf(`#!/bin/bash
sleep 2
cp -f "%s" "%s"
chmod +x "%s"
`, tmpAgentFile, currentExe, currentExe)

	if runtime.GOOS == "darwin" {
		scriptContent += fmt.Sprintf(`
# macOS: 尝试多种方式重启
if [ -f /Library/LaunchDaemons/com.vector.agent.plist ]; then
    launchctl unload /Library/LaunchDaemons/com.vector.agent.plist 2>/dev/null
    sleep 1
    launchctl load /Library/LaunchDaemons/com.vector.agent.plist
elif [ -f ~/Library/LaunchAgents/com.vector.agent.plist ]; then
    launchctl unload ~/Library/LaunchAgents/com.vector.agent.plist 2>/dev/null
    sleep 1
    launchctl load ~/Library/LaunchAgents/com.vector.agent.plist
else
    # 直接启动
    nohup "%s" > /opt/vector-agent/logs/agent.log 2>&1 &
fi
rm -f "%s"
rm -f "%s"
`, currentExe, tmpAgentFile, upgradeScript)
	} else {
		scriptContent += fmt.Sprintf(`
systemctl restart vector-agent
rm -f "%s"
rm -f "%s"
`, tmpAgentFile, upgradeScript)
	}

	if err := os.WriteFile(upgradeScript, []byte(scriptContent), 0755); err != nil {
		os.Remove(tmpAgentFile)
		return fmt.Errorf("创建升级脚本失败: %w", err)
	}

	cmd = exec.Command("/bin/bash", upgradeScript)
	if err := cmd.Start(); err != nil {
		os.Remove(tmpAgentFile)
		os.Remove(upgradeScript)
		return fmt.Errorf("启动升级脚本失败: %w", err)
	}

	log.Println("Bundle 升级完成，Agent 即将重启...")
	return nil
}

// UpgradeVector 升级 Vector（兼容旧逻辑，但现在推荐用 UpgradeBundle）
func (u *Upgrader) UpgradeVector(downloadURL, checksum string, fileSize int64) error {
	// Bundle 包通常 > 20MB，单个二进制 < 15MB
	// 或者检查 URL 是否包含 bundle
	if fileSize > 20*1024*1024 || strings.Contains(downloadURL, "bundle") ||
		strings.HasSuffix(downloadURL, ".tar.gz") || strings.HasSuffix(downloadURL, ".tgz") {
		return u.upgradeFromTarGz(downloadURL, checksum, fileSize, "vector")
	}
	return u.upgradeVectorBinary(downloadURL, checksum, fileSize)
}

// UpgradeAgent 升级 Agent
func (u *Upgrader) UpgradeAgent(downloadURL, checksum string, fileSize int64) error {
	// Bundle 包通常 > 20MB
	if fileSize > 20*1024*1024 || strings.Contains(downloadURL, "bundle") ||
		strings.HasSuffix(downloadURL, ".tar.gz") || strings.HasSuffix(downloadURL, ".tgz") {
		return u.UpgradeBundle(downloadURL, checksum, fileSize)
	}
	return u.upgradeAgentBinary(downloadURL, checksum, fileSize)
}

// upgradeFromTarGz 从 tar.gz 包升级指定组件
func (u *Upgrader) upgradeFromTarGz(downloadURL, checksum string, fileSize int64, component string) error {
	log.Printf("从 tar.gz 升级 %s: url=%s", component, downloadURL)

	tmpFile := filepath.Join(os.TempDir(), "upgrade-package.tar.gz")
	if err := u.downloadFile(downloadURL, tmpFile, checksum, fileSize); err != nil {
		return fmt.Errorf("下载失败: %w", err)
	}
	defer os.Remove(tmpFile)

	extractDir := filepath.Join(os.TempDir(), "upgrade-extract")
	os.RemoveAll(extractDir)
	if err := u.extractTarGz(tmpFile, extractDir); err != nil {
		return fmt.Errorf("解压失败: %w", err)
	}
	defer os.RemoveAll(extractDir)

	// 查找目标二进制
	var targetBin, destBin string
	if component == "vector" {
		targetBin = filepath.Join(extractDir, "bin", "vector")
		destBin = config.VectorBin
	} else {
		targetBin = filepath.Join(extractDir, "bin", "vector-agent")
		destBin = config.AgentBin
	}

	if _, err := os.Stat(targetBin); os.IsNotExist(err) {
		return fmt.Errorf("包中缺少 %s", component)
	}

	// 备份并替换
	backupBin := destBin + ".backup"
	if _, err := os.Stat(destBin); err == nil {
		os.Rename(destBin, backupBin)
	}

	if err := copyFile(targetBin, destBin); err != nil {
		os.Rename(backupBin, destBin)
		return fmt.Errorf("安装失败: %w", err)
	}
	os.Chmod(destBin, 0755)

	// 验证
	var verifyCmd *exec.Cmd
	if component == "vector" {
		verifyCmd = exec.Command(destBin, "--version")
	} else {
		verifyCmd = exec.Command(destBin, "-version")
	}

	if err := verifyCmd.Run(); err != nil {
		os.Remove(destBin)
		os.Rename(backupBin, destBin)
		return fmt.Errorf("验证失败: %w", err)
	}

	os.Remove(backupBin)
	log.Printf("%s 升级成功", component)
	return nil
}

// upgradeVectorBinary 升级 Vector 二进制文件
func (u *Upgrader) upgradeVectorBinary(downloadURL, checksum string, fileSize int64) error {
	log.Printf("开始升级 Vector: url=%s", downloadURL)

	tmpFile := filepath.Join(os.TempDir(), "vector-new")
	if err := u.downloadFile(downloadURL, tmpFile, checksum, fileSize); err != nil {
		return fmt.Errorf("下载失败: %w", err)
	}
	defer os.Remove(tmpFile)

	backupFile := config.VectorBin + ".backup"
	if _, err := os.Stat(config.VectorBin); err == nil {
		if err := os.Rename(config.VectorBin, backupFile); err != nil {
			return fmt.Errorf("备份失败: %w", err)
		}
	}

	if err := os.Rename(tmpFile, config.VectorBin); err != nil {
		os.Rename(backupFile, config.VectorBin)
		return fmt.Errorf("安装失败: %w", err)
	}

	if err := os.Chmod(config.VectorBin, 0755); err != nil {
		os.Remove(config.VectorBin)
		os.Rename(backupFile, config.VectorBin)
		return fmt.Errorf("设置权限失败: %w", err)
	}

	cmd := exec.Command(config.VectorBin, "--version")
	if err := cmd.Run(); err != nil {
		os.Remove(config.VectorBin)
		os.Rename(backupFile, config.VectorBin)
		return fmt.Errorf("新版本验证失败: %w", err)
	}

	os.Remove(backupFile)
	log.Println("Vector 升级成功")
	return nil
}

// upgradeAgentBinary 升级 Agent 二进制文件
func (u *Upgrader) upgradeAgentBinary(downloadURL, checksum string, fileSize int64) error {
	log.Printf("开始升级 Agent: url=%s", downloadURL)

	currentExe, err := os.Executable()
	if err != nil {
		return fmt.Errorf("获取当前路径失败: %w", err)
	}
	currentExe, _ = filepath.EvalSymlinks(currentExe)

	tmpFile := filepath.Join(os.TempDir(), "vector-agent-new")
	if err := u.downloadFile(downloadURL, tmpFile, checksum, fileSize); err != nil {
		return fmt.Errorf("下载失败: %w", err)
	}

	if err := os.Chmod(tmpFile, 0755); err != nil {
		os.Remove(tmpFile)
		return fmt.Errorf("设置权限失败: %w", err)
	}

	upgradeScript := filepath.Join(os.TempDir(), "upgrade-agent.sh")
	scriptContent := fmt.Sprintf(`#!/bin/bash
sleep 2
cp -f "%s" "%s"
chmod +x "%s"
`, tmpFile, currentExe, currentExe)

	if runtime.GOOS == "darwin" {
		scriptContent += fmt.Sprintf(`
# macOS: 尝试多种方式重启
if [ -f /Library/LaunchDaemons/com.vector.agent.plist ]; then
    launchctl unload /Library/LaunchDaemons/com.vector.agent.plist 2>/dev/null
    sleep 1
    launchctl load /Library/LaunchDaemons/com.vector.agent.plist
elif [ -f ~/Library/LaunchAgents/com.vector.agent.plist ]; then
    launchctl unload ~/Library/LaunchAgents/com.vector.agent.plist 2>/dev/null
    sleep 1
    launchctl load ~/Library/LaunchAgents/com.vector.agent.plist
else
    # 直接启动
    nohup "%s" > /opt/vector-agent/logs/agent.log 2>&1 &
fi
rm -f "%s"
rm -f "%s"
`, currentExe, tmpFile, upgradeScript)
	} else {
		scriptContent += fmt.Sprintf(`
systemctl restart vector-agent
rm -f "%s"
rm -f "%s"
`, tmpFile, upgradeScript)
	}

	if err := os.WriteFile(upgradeScript, []byte(scriptContent), 0755); err != nil {
		os.Remove(tmpFile)
		return fmt.Errorf("创建升级脚本失败: %w", err)
	}

	cmd := exec.Command("/bin/bash", upgradeScript)
	if err := cmd.Start(); err != nil {
		os.Remove(tmpFile)
		os.Remove(upgradeScript)
		return fmt.Errorf("启动升级脚本失败: %w", err)
	}

	log.Println("Agent 升级脚本已启动，即将重启...")
	return nil
}

// extractTarGz 解压 tar.gz 文件
func (u *Upgrader) extractTarGz(src, dest string) error {
	file, err := os.Open(src)
	if err != nil {
		return err
	}
	defer file.Close()

	gzr, err := gzip.NewReader(file)
	if err != nil {
		return err
	}
	defer gzr.Close()

	tr := tar.NewReader(gzr)

	for {
		header, err := tr.Next()
		if err == io.EOF {
			break
		}
		if err != nil {
			return err
		}

		target := filepath.Join(dest, header.Name)

		switch header.Typeflag {
		case tar.TypeDir:
			if err := os.MkdirAll(target, 0755); err != nil {
				return err
			}
		case tar.TypeReg:
			if err := os.MkdirAll(filepath.Dir(target), 0755); err != nil {
				return err
			}
			outFile, err := os.Create(target)
			if err != nil {
				return err
			}
			if _, err := io.Copy(outFile, tr); err != nil {
				outFile.Close()
				return err
			}
			outFile.Close()
			os.Chmod(target, os.FileMode(header.Mode))
		}
	}

	return nil
}

// downloadFile 下载文件并验证校验和
func (u *Upgrader) downloadFile(url, destPath, expectedChecksum string, expectedSize int64) error {
	log.Printf("下载文件: %s -> %s", url, destPath)

	resp, err := u.httpClient.Get(url)
	if err != nil {
		return fmt.Errorf("请求失败: %w", err)
	}
	defer resp.Body.Close()

	if resp.StatusCode != http.StatusOK {
		return fmt.Errorf("HTTP错误: %d", resp.StatusCode)
	}

	out, err := os.Create(destPath)
	if err != nil {
		return fmt.Errorf("创建文件失败: %w", err)
	}
	defer out.Close()

	hash := sha256.New()
	writer := io.MultiWriter(out, hash)

	written, err := io.Copy(writer, resp.Body)
	if err != nil {
		return fmt.Errorf("写入失败: %w", err)
	}

	if expectedSize > 0 && written != expectedSize {
		return fmt.Errorf("文件大小不匹配: 期望 %d, 实际 %d", expectedSize, written)
	}

	if expectedChecksum != "" {
		actualChecksum := hex.EncodeToString(hash.Sum(nil))
		if actualChecksum != expectedChecksum {
			return fmt.Errorf("校验和不匹配: 期望 %s, 实际 %s", expectedChecksum, actualChecksum)
		}
		log.Println("校验和验证通过")
	}

	log.Printf("下载完成: %d 字节", written)
	return nil
}

// copyFile 复制文件
func copyFile(src, dst string) error {
	sourceFile, err := os.Open(src)
	if err != nil {
		return err
	}
	defer sourceFile.Close()

	destFile, err := os.Create(dst)
	if err != nil {
		return err
	}
	defer destFile.Close()

	_, err = io.Copy(destFile, sourceFile)
	return err
}
