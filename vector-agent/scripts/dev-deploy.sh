#!/bin/bash
# ============================================
# Vector Agent 本地开发部署脚本
# 编译并部署 vector-agent 和 vector 到 /opt/vector-agent/bin
# ============================================

set -e

GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m'

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"
BIN_DIR="${PROJECT_DIR}/bin"
TARGET_DIR="/opt/vector-agent/bin"

echo -e "${GREEN}========================================${NC}"
echo -e "${GREEN}  Vector Agent 本地开发部署${NC}"
echo -e "${GREEN}========================================${NC}"
echo ""

# 进入项目目录
cd "$PROJECT_DIR"

# 1. 下载 Vector（如果不存在）
echo -e "${YELLOW}[1/4] 检查 Vector...${NC}"

# 检查系统目录
if [ -f "/opt/vector-agent/bin/vector" ] && [ -x "/opt/vector-agent/bin/vector" ]; then
    VECTOR_VERSION=$(/opt/vector-agent/bin/vector --version 2>&1 | head -1)
    echo "  -> 系统已安装: ${VECTOR_VERSION}"

    # 复制到项目 bin 目录（如果不存在）
    if [ ! -f "${BIN_DIR}/vector" ]; then
        mkdir -p "${BIN_DIR}"
        cp /opt/vector-agent/bin/vector "${BIN_DIR}/vector"
        chmod +x "${BIN_DIR}/vector"
        echo "  -> 已复制到项目 bin 目录"
    fi
elif [ -f "${BIN_DIR}/vector" ]; then
    VECTOR_VERSION=$(${BIN_DIR}/vector --version 2>&1 | head -1)
    echo "  -> 项目已存在: ${VECTOR_VERSION}"
else
    echo "  -> Vector 不存在，开始下载..."
    echo ""

    # 调用下载脚本（非交互模式）
    if [ -f "${SCRIPT_DIR}/download-vector.sh" ]; then
        # 使用 yes 命令自动回答 'n'（不使用系统已安装的版本，强制下载）
        yes n | bash "${SCRIPT_DIR}/download-vector.sh" || true
    else
        echo -e "${RED}错误: 下载脚本不存在: ${SCRIPT_DIR}/download-vector.sh${NC}"
        echo ""
        echo "请手动下载 Vector 并放置到: ${BIN_DIR}/vector"
        echo "下载地址: https://github.com/vectordotdev/vector/releases"
        exit 1
    fi

    echo ""
fi

# 2. 编译 vector-agent
echo -e "${YELLOW}[2/4] 编译 vector-agent...${NC}"
go build -o bin/vector-agent ./cmd/agent
echo "  -> 编译完成: bin/vector-agent"

# 3. 检查目标目录
echo -e "${YELLOW}[3/4] 检查目标目录...${NC}"
if [ ! -d "$TARGET_DIR" ]; then
    echo -e "${RED}目标目录不存在: ${TARGET_DIR}${NC}"
    echo "正在创建目录..."

    # 创建完整的目录结构
    if [ -w "/opt" ]; then
        mkdir -p "$TARGET_DIR"
        mkdir -p "/opt/vector-agent/config"
        mkdir -p "/opt/vector-agent/data"
        mkdir -p "/opt/vector-agent/logs"
    else
        sudo mkdir -p "$TARGET_DIR"
        sudo mkdir -p "/opt/vector-agent/config"
        sudo mkdir -p "/opt/vector-agent/data"
        sudo mkdir -p "/opt/vector-agent/logs"
    fi

    echo "  -> 目录已创建"
fi

# 4. 部署
echo -e "${YELLOW}[4/4] 部署到 ${TARGET_DIR}...${NC}"

# 检查是否需要 sudo
if [ -w "$TARGET_DIR" ]; then
    cp bin/vector-agent "$TARGET_DIR/"
    cp bin/vector "$TARGET_DIR/"
    echo "  -> 已复制 vector-agent 到 ${TARGET_DIR}/"
    echo "  -> 已复制 vector 到 ${TARGET_DIR}/"
else
    echo "  -> 需要 sudo 权限..."
    sudo cp bin/vector-agent "$TARGET_DIR/"
    sudo cp bin/vector "$TARGET_DIR/"
    echo "  -> 已复制 vector-agent 到 ${TARGET_DIR}/ (sudo)"
    echo "  -> 已复制 vector 到 ${TARGET_DIR}/ (sudo)"
fi

echo ""
echo -e "${GREEN}========================================${NC}"
echo -e "${GREEN}  部署完成！${NC}"
echo -e "${GREEN}========================================${NC}"
echo ""
echo "重启 Agent 服务使更改生效:"

# 检测系统类型
OS=$(uname -s | tr '[:upper:]' '[:lower:]')
if [ "$OS" = "darwin" ]; then
    echo "  sudo launchctl bootout system /Library/LaunchDaemons/com.vector.agent.plist"
else
    echo "  sudo systemctl restart vector-agent"
fi
