#!/bin/bash

# ===================================
# 快速启动脚本（Python 3.14 兼容版本）
# ===================================

set -e

# 颜色定义
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

echo -e "${GREEN}========================================${NC}"
echo -e "${GREEN}Log Analysis AI Service 快速启动${NC}"
echo -e "${GREEN}========================================${NC}"
echo ""

# 检查 .env 文件
if [ ! -f ".env" ]; then
    echo -e "${RED}错误: .env 文件不存在${NC}"
    echo -e "${YELLOW}请复制 .env.example 并配置：${NC}"
    echo "  cp .env.example .env"
    exit 1
fi

# 检查 Rust（Python 3.14 需要）
if ! command -v cargo &> /dev/null; then
    echo -e "${YELLOW}警告: 未检测到 Rust 工具链${NC}"
    echo -e "${YELLOW}Python 3.14 需要 Rust 来编译某些依赖${NC}"
    echo ""
    echo -e "${BLUE}请选择解决方案：${NC}"
    echo "  1) 安装 Rust 工具链（推荐，约 5 分钟）"
    echo "  2) 安装 Python 3.12（推荐，更稳定）"
    echo "  3) 继续尝试（可能失败）"
    echo ""
    read -p "请选择 (1/2/3): " -n 1 -r
    echo ""

    case $REPLY in
        1)
            echo -e "${BLUE}正在安装 Rust...${NC}"
            curl --proto '=https' --tlsv1.2 -sSf https://sh.rustup.rs | sh -s -- -y
            source "$HOME/.cargo/env"
            echo -e "${GREEN}✓ Rust 安装成功${NC}"
            ;;
        2)
            echo -e "${YELLOW}请运行以下命令安装 Python 3.12：${NC}"
            echo "  brew install python@3.12"
            echo ""
            echo "然后删除虚拟环境并重新运行："
            echo "  rm -rf venv"
            echo "  /opt/homebrew/bin/python3.12 -m venv venv"
            exit 0
            ;;
        3)
            echo -e "${YELLOW}继续尝试...${NC}"
            ;;
        *)
            echo -e "${RED}无效选择${NC}"
            exit 1
            ;;
    esac
fi

# 创建虚拟环境
if [ ! -d "venv" ]; then
    echo -e "${YELLOW}创建虚拟环境...${NC}"
    python3 -m venv venv
    echo -e "${GREEN}✓ 虚拟环境创建成功${NC}"
fi

# 激活虚拟环境
echo -e "${YELLOW}激活虚拟环境...${NC}"
source venv/bin/activate

# 升级 pip 和构建工具
echo -e "${YELLOW}升级 pip 和构建工具...${NC}"
pip install --upgrade pip setuptools wheel

# 安装依赖（使用更宽松的策略）
echo -e "${YELLOW}安装依赖...${NC}"
echo -e "${BLUE}提示: 如果编译失败，请按 Ctrl+C 中断并选择安装 Rust${NC}"

# 先尝试安装预编译的包
pip install --only-binary :all: fastapi uvicorn pydantic 2>/dev/null || {
    echo -e "${YELLOW}预编译包不可用，尝试从源码安装...${NC}"
    pip install -r requirements.txt
}

# 读取配置
source .env
SERVICE_HOST=${SERVICE_HOST:-0.0.0.0}
SERVICE_PORT=${SERVICE_PORT:-8001}
LOG_LEVEL=${LOG_LEVEL:-INFO}

echo ""
echo -e "${GREEN}========================================${NC}"
echo -e "${GREEN}服务配置信息${NC}"
echo -e "${GREEN}========================================${NC}"
echo -e "Python:      ${YELLOW}$(python --version)${NC}"
echo -e "Base URL:    ${YELLOW}${ANTHROPIC_BASE_URL}${NC}"
echo -e "Model:       ${YELLOW}${CLAUDE_MODEL}${NC}"
echo -e "Host:        ${YELLOW}${SERVICE_HOST}${NC}"
echo -e "Port:        ${YELLOW}${SERVICE_PORT}${NC}"
echo -e "Log Level:   ${YELLOW}${LOG_LEVEL}${NC}"
echo -e "${GREEN}========================================${NC}"
echo ""

# 启动服务
echo -e "${GREEN}正在启动服务...${NC}"
echo -e "${BLUE}访问 http://localhost:${SERVICE_PORT}/docs 查看 API 文档${NC}"
echo ""

python -m uvicorn app.main:app \
    --host "$SERVICE_HOST" \
    --port "$SERVICE_PORT" \
    --reload \
    --log-level "$(echo $LOG_LEVEL | tr '[:upper:]' '[:lower:]')"
