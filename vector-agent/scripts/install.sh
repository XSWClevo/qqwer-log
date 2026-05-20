#!/bin/bash
set -e

# ============================================
# Vector Agent Bundle 安装脚本
# 支持 Linux (systemd) 和 macOS (launchd)
# ============================================

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

# 参数
TOKEN="${1:?请提供 Agent Token，用法: $0 <TOKEN> <SERVER_URL>}"
SERVER_URL="${2:?请提供服务器地址，用法: $0 <TOKEN> <SERVER_URL>}"

# 安装目录
INSTALL_DIR="/opt/vector-agent"
BIN_DIR="${INSTALL_DIR}/bin"
CONFIG_DIR="${INSTALL_DIR}"
DATA_DIR="${INSTALL_DIR}/data"
LOG_DIR="${INSTALL_DIR}/logs"

# 检测系统
OS=$(uname -s | tr '[:upper:]' '[:lower:]')
ARCH=$(uname -m)
case $ARCH in
    x86_64) ARCH="amd64" ;;
    aarch64|arm64) ARCH="arm64" ;;
esac

echo -e "${GREEN}========================================${NC}"
echo -e "${GREEN}  Vector Agent Bundle 安装${NC}"
echo -e "${GREEN}========================================${NC}"
echo ""
echo "系统: ${OS}/${ARCH}"
echo "服务器: ${SERVER_URL}"
echo "安装目录: ${INSTALL_DIR}"
echo ""

# 检查权限
if [ "$EUID" -ne 0 ] && [ "$OS" = "linux" ]; then
    echo -e "${RED}Linux 系统请使用 root 权限运行${NC}"
    exit 1
fi

# 获取脚本所在目录（判断是从 bundle 安装还是远程安装）
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
BUNDLE_MODE=false
if [ -f "${SCRIPT_DIR}/bin/vector-agent" ] && [ -f "${SCRIPT_DIR}/bin/vector" ]; then
    BUNDLE_MODE=true
    echo "安装模式: Bundle 本地安装"
else
    echo "安装模式: 远程下载安装"
fi

# 1. 创建目录
echo -e "${YELLOW}[1/6] 创建目录...${NC}"
mkdir -p ${BIN_DIR} ${CONFIG_DIR} ${DATA_DIR} ${LOG_DIR}
mkdir -p ${CONFIG_DIR}/history

# 2. 安装二进制文件
echo -e "${YELLOW}[2/6] 安装二进制文件...${NC}"
if [ "$BUNDLE_MODE" = true ]; then
    cp ${SCRIPT_DIR}/bin/vector-agent ${BIN_DIR}/
    cp ${SCRIPT_DIR}/bin/vector ${BIN_DIR}/
    if [ -f "${SCRIPT_DIR}/VERSION" ]; then
        cp ${SCRIPT_DIR}/VERSION ${INSTALL_DIR}/
    fi
else
    # 从服务器下载
    DOWNLOAD_URL="${SERVER_URL}/api/vector/packages/download-bundle?os=${OS}&arch=${ARCH}"
    echo "下载地址: ${DOWNLOAD_URL}"
    
    if curl -fsSL "${DOWNLOAD_URL}" -o /tmp/vector-agent-bundle.tar.gz 2>/dev/null; then
        tar -xzf /tmp/vector-agent-bundle.tar.gz -C /tmp/vector-bundle-extract
        cp /tmp/vector-bundle-extract/bin/* ${BIN_DIR}/
        [ -f /tmp/vector-bundle-extract/VERSION ] && cp /tmp/vector-bundle-extract/VERSION ${INSTALL_DIR}/
        rm -rf /tmp/vector-agent-bundle.tar.gz /tmp/vector-bundle-extract
    else
        echo -e "${RED}下载失败，请检查服务器地址或手动安装${NC}"
        exit 1
    fi
fi

chmod +x ${BIN_DIR}/vector ${BIN_DIR}/vector-agent

# 3. 创建配置
echo -e "${YELLOW}[3/6] 创建配置文件...${NC}"
cat > ${CONFIG_DIR}/agent.yaml <<EOF
server_url: "${SERVER_URL}"
agent_token: "${TOKEN}"
EOF

cat > ${CONFIG_DIR}/vector.yaml <<EOF
data_dir: "${DATA_DIR}"

sources:
  internal_metrics:
    type: internal_metrics
    scrape_interval_secs: 60

sinks:
  blackhole:
    type: blackhole
    inputs:
      - internal_metrics
    print_interval_secs: 0
EOF

# 4. 创建软链接
echo -e "${YELLOW}[4/6] 创建软链接...${NC}"
ln -sf ${BIN_DIR}/vector /usr/local/bin/vector 2>/dev/null || true
ln -sf ${BIN_DIR}/vector-agent /usr/local/bin/vector-agent 2>/dev/null || true

# 5. 创建服务
echo -e "${YELLOW}[5/6] 创建系统服务...${NC}"

if [ "$OS" = "darwin" ]; then
    # macOS: 使用 launchd
    PLIST_DIR="$HOME/Library/LaunchAgents"
    mkdir -p ${PLIST_DIR}
    
    # Vector Agent plist
    cat > ${PLIST_DIR}/com.vector.agent.plist <<EOF
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0">
<dict>
    <key>Label</key>
    <string>com.vector.agent</string>
    <key>ProgramArguments</key>
    <array>
        <string>${BIN_DIR}/vector-agent</string>
        <string>-config</string>
        <string>${CONFIG_DIR}/agent.yaml</string>
    </array>
    <key>RunAtLoad</key>
    <true/>
    <key>KeepAlive</key>
    <true/>
    <key>StandardOutPath</key>
    <string>${LOG_DIR}/agent.log</string>
    <key>StandardErrorPath</key>
    <string>${LOG_DIR}/agent.error.log</string>
</dict>
</plist>
EOF

    # Vector plist
    cat > ${PLIST_DIR}/com.vector.plist <<EOF
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0">
<dict>
    <key>Label</key>
    <string>com.vector</string>
    <key>ProgramArguments</key>
    <array>
        <string>${BIN_DIR}/vector</string>
        <string>--config</string>
        <string>${CONFIG_DIR}/vector.yaml</string>
    </array>
    <key>RunAtLoad</key>
    <true/>
    <key>KeepAlive</key>
    <true/>
    <key>StandardOutPath</key>
    <string>${LOG_DIR}/vector.log</string>
    <key>StandardErrorPath</key>
    <string>${LOG_DIR}/vector.error.log</string>
    <key>EnvironmentVariables</key>
    <dict>
        <key>VECTOR_DATA_DIR</key>
        <string>${DATA_DIR}</string>
    </dict>
</dict>
</plist>
EOF

else
    # Linux: 使用 systemd
    cat > /etc/systemd/system/vector.service <<EOF
[Unit]
Description=Vector Log Collector
After=network-online.target
Wants=network-online.target

[Service]
Type=simple
ExecStart=${BIN_DIR}/vector --config ${CONFIG_DIR}/vector.yaml
ExecReload=/bin/kill -HUP \$MAINPID
Restart=on-failure
RestartSec=5s
Environment=VECTOR_DATA_DIR=${DATA_DIR}
LimitNOFILE=65536

[Install]
WantedBy=multi-user.target
EOF

    cat > /etc/systemd/system/vector-agent.service <<EOF
[Unit]
Description=Vector Agent
After=network-online.target
Before=vector.service

[Service]
Type=simple
ExecStart=${BIN_DIR}/vector-agent -config ${CONFIG_DIR}/agent.yaml
Restart=on-failure
RestartSec=10s

[Install]
WantedBy=multi-user.target
EOF
fi

# 6. 启动服务
echo -e "${YELLOW}[6/6] 启动服务...${NC}"

if [ "$OS" = "darwin" ]; then
    launchctl unload ${PLIST_DIR}/com.vector.agent.plist 2>/dev/null || true
    launchctl unload ${PLIST_DIR}/com.vector.plist 2>/dev/null || true
    launchctl load ${PLIST_DIR}/com.vector.agent.plist
    sleep 2
    launchctl load ${PLIST_DIR}/com.vector.plist
    
    AGENT_STATUS=$(launchctl list | grep com.vector.agent > /dev/null && echo "running" || echo "stopped")
    VECTOR_STATUS=$(launchctl list | grep com.vector > /dev/null && echo "running" || echo "stopped")
else
    systemctl daemon-reload
    systemctl enable vector-agent vector
    systemctl start vector-agent
    sleep 2
    systemctl start vector
    
    AGENT_STATUS=$(systemctl is-active vector-agent)
    VECTOR_STATUS=$(systemctl is-active vector)
fi

echo ""
echo -e "${GREEN}========================================${NC}"
echo -e "${GREEN}  安装完成！${NC}"
echo -e "${GREEN}========================================${NC}"
echo ""
echo "服务状态:"
echo "  Vector Agent: ${AGENT_STATUS}"
echo "  Vector:       ${VECTOR_STATUS}"
echo ""

if [ "$OS" = "darwin" ]; then
    echo "常用命令 (macOS):"
    echo "  launchctl list | grep vector     # 查看服务状态"
    echo "  tail -f ${LOG_DIR}/agent.log     # 查看 Agent 日志"
    echo "  tail -f ${LOG_DIR}/vector.log    # 查看 Vector 日志"
else
    echo "常用命令 (Linux):"
    echo "  systemctl status vector-agent    # 查看 Agent 状态"
    echo "  systemctl status vector          # 查看 Vector 状态"
    echo "  journalctl -u vector-agent -f    # 查看 Agent 日志"
    echo "  journalctl -u vector -f          # 查看 Vector 日志"
fi
echo ""
echo "配置文件: ${CONFIG_DIR}/"
echo ""
echo -e "${GREEN}Agent 已启动，正在向服务器注册...${NC}"
