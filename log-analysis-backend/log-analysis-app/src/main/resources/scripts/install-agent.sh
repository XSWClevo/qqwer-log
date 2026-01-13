#!/bin/bash
set -e

TOKEN="{{TOKEN}}"
SERVER_URL="{{SERVER_URL}}"

# 颜色
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m'

INSTALL_DIR="/opt/vector-agent"
BIN_DIR="${INSTALL_DIR}/bin"
CONFIG_DIR="${INSTALL_DIR}/config"
DATA_DIR="${INSTALL_DIR}/data"
LOG_DIR="${INSTALL_DIR}/logs"

echo -e "${GREEN}========================================${NC}"
echo -e "${GREEN}  Vector Agent 一体化安装${NC}"
echo -e "${GREEN}========================================${NC}"
echo ""
echo "服务器地址: ${SERVER_URL}"
echo "安装目录: ${INSTALL_DIR}"
echo ""

# 检查 root
if [ "$EUID" -ne 0 ]; then
    echo -e "${RED}请使用 root 权限运行此脚本${NC}"
    exit 1
fi

# 检测操作系统
OS_TYPE="linux"
if [[ "$OSTYPE" == "darwin"* ]]; then
    OS_TYPE="darwin"
    echo -e "${YELLOW}检测到 macOS 系统${NC}"
fi

# 创建目录
echo -e "${YELLOW}[1/5] 创建目录...${NC}"
mkdir -p ${BIN_DIR} ${CONFIG_DIR} ${DATA_DIR} ${LOG_DIR}
mkdir -p ${CONFIG_DIR}/history
# 设置目录权限，允许普通用户读写
chmod -R 755 ${INSTALL_DIR}
chmod 777 ${LOG_DIR} ${DATA_DIR} ${CONFIG_DIR}

# 下载安装包
echo -e "${YELLOW}[2/5] 下载安装包...${NC}"
DOWNLOAD_URL="${SERVER_URL}/api/vector/agents/download?os=${OS_TYPE}"

if curl -fsSL "${DOWNLOAD_URL}" -o /tmp/bundle.tar.gz 2>/dev/null; then
    tar -xzf /tmp/bundle.tar.gz -C ${INSTALL_DIR}
    rm -f /tmp/bundle.tar.gz
    echo "  -> 安装包下载成功"
else
    echo -e "${YELLOW}无法从服务器下载安装包，请手动放置${NC}"
    if [ ! -f "${BIN_DIR}/vector-agent" ]; then
        echo -e "${RED}错误: ${BIN_DIR}/vector-agent 不存在${NC}"
        echo "请手动下载安装包并解压到 ${INSTALL_DIR}"
        exit 1
    fi
fi

chmod +x ${BIN_DIR}/vector 2>/dev/null || true
chmod +x ${BIN_DIR}/vector-agent 2>/dev/null || true

# 创建配置
echo -e "${YELLOW}[3/5] 创建配置...${NC}"
cat > ${INSTALL_DIR}/agent.yaml <<EOF
server_url: "${SERVER_URL}"
agent_token: "${TOKEN}"
EOF

cat > ${CONFIG_DIR}/vector.yaml <<EOF
data_dir: "${DATA_DIR}"

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
EOF
echo "  -> 配置文件已创建"

# 创建软链接
ln -sf ${BIN_DIR}/vector /usr/local/bin/vector 2>/dev/null || true

# 根据系统类型创建服务
echo -e "${YELLOW}[4/5] 创建服务...${NC}"

if [ "${OS_TYPE}" = "darwin" ]; then
    # macOS: 使用 launchd
    cat > /Library/LaunchDaemons/com.vector.agent.plist <<EOF
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0">
<dict>
    <key>Label</key>
    <string>com.vector.agent</string>
    <key>ProgramArguments</key>
    <array>
        <string>${BIN_DIR}/vector-agent</string>
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
    echo "  -> launchd 服务已创建"
else
    # Linux: 使用 systemd
    cat > /etc/systemd/system/vector.service <<EOF
[Unit]
Description=Vector Log Collector
After=network-online.target

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
    echo "  -> systemd 服务已创建"
fi

# 启动服务
echo -e "${YELLOW}[5/5] 启动服务...${NC}"

if [ "${OS_TYPE}" = "darwin" ]; then
    launchctl load /Library/LaunchDaemons/com.vector.agent.plist 2>/dev/null || true
    AGENT_STATUS="已加载"
else
    systemctl daemon-reload
    systemctl enable vector-agent vector 2>/dev/null || true
    systemctl start vector-agent
    sleep 2
    systemctl start vector
    AGENT_STATUS=$(systemctl is-active vector-agent 2>/dev/null || echo "unknown")
    VECTOR_STATUS=$(systemctl is-active vector 2>/dev/null || echo "unknown")
fi

echo ""
echo -e "${GREEN}========================================${NC}"
echo -e "${GREEN}  安装完成！${NC}"
echo -e "${GREEN}========================================${NC}"
echo ""
echo "配置文件: ${CONFIG_DIR}/agent.yaml"
echo ""
if [ "${OS_TYPE}" = "darwin" ]; then
    echo "macOS 常用命令:"
    echo "  sudo launchctl list | grep vector"
    echo "  tail -f ${LOG_DIR}/agent.log"
else
    echo "服务状态:"
    echo "  Vector Agent: ${AGENT_STATUS}"
    echo "  Vector: ${VECTOR_STATUS}"
    echo ""
    echo "常用命令:"
    echo "  systemctl status vector-agent"
    echo "  journalctl -u vector-agent -f"
fi
echo ""
echo -e "${GREEN}Agent 已启动，正在向服务器注册...${NC}"
