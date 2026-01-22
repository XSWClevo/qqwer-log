#!/bin/bash

# ===================================
# Log Analysis AI Service 启动脚本
# ===================================

set -e  # 遇到错误立即退出

# 颜色定义
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# 获取脚本所在目录
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

echo -e "${GREEN}========================================${NC}"
echo -e "${GREEN}Log Analysis AI Service 启动脚本${NC}"
echo -e "${GREEN}========================================${NC}"
echo ""

# 检查 .env 文件
if [ ! -f ".env" ]; then
    echo -e "${RED}错误: .env 文件不存在${NC}"
    echo -e "${YELLOW}请复制 .env.example 并配置：${NC}"
    echo "  cp .env.example .env"
    echo "  然后编辑 .env 文件，填入你的配置"
    exit 1
fi

# 选择 Python 版本（优先使用 3.12, 3.11, 3.10）
PYTHON_CMD=""
for py_version in python3.12 python3.11 python3.10 python3; do
    if command -v "$py_version" &> /dev/null; then
        PYTHON_CMD="$py_version"
        PYTHON_VERSION=$($PYTHON_CMD --version 2>&1 | awk '{print $2}')
        echo -e "${BLUE}找到 Python: $PYTHON_CMD ($PYTHON_VERSION)${NC}"

        # 检查是否是 Python 3.14+
        MAJOR_VERSION=$(echo $PYTHON_VERSION | cut -d. -f1)
        MINOR_VERSION=$(echo $PYTHON_VERSION | cut -d. -f2)

        if [ "$MAJOR_VERSION" -eq 3 ] && [ "$MINOR_VERSION" -ge 14 ]; then
            echo -e "${YELLOW}警告: Python 3.14+ 可能需要从源码编译某些依赖${NC}"
            echo -e "${YELLOW}建议安装 Python 3.12: brew install python@3.12${NC}"
            echo -e "${YELLOW}或安装 Rust 工具链: curl --proto '=https' --tlsv1.2 -sSf https://sh.rustup.rs | sh${NC}"
            echo ""
            read -p "是否继续使用 Python $PYTHON_VERSION? (y/n) " -n 1 -r
            echo
            if [[ ! $REPLY =~ ^[Yy]$ ]]; then
                exit 1
            fi
        fi
        break
    fi
done

if [ -z "$PYTHON_CMD" ]; then
    echo -e "${RED}错误: 未找到 Python 3${NC}"
    exit 1
fi

# 检查虚拟环境
if [ ! -d "venv" ]; then
    echo -e "${YELLOW}虚拟环境不存在，正在创建...${NC}"
    $PYTHON_CMD -m venv venv
    echo -e "${GREEN}✓ 虚拟环境创建成功${NC}"
fi

# 激活虚拟环境
echo -e "${YELLOW}激活虚拟环境...${NC}"
source venv/bin/activate

# 显示 Python 版本
PYTHON_VERSION=$(python --version 2>&1)
echo -e "${GREEN}✓ 使用 Python: $PYTHON_VERSION${NC}"

# 检查依赖
echo -e "${YELLOW}检查依赖...${NC}"
if ! pip show fastapi > /dev/null 2>&1; then
    echo -e "${YELLOW}依赖未安装，正在安装...${NC}"
    pip install --upgrade pip
    pip install -r requirements.txt
    echo -e "${GREEN}✓ 依赖安装成功${NC}"
else
    echo -e "${GREEN}✓ 依赖已安装${NC}"
fi

# 读取配置
source .env
SERVICE_HOST=${SERVICE_HOST:-0.0.0.0}
SERVICE_PORT=${SERVICE_PORT:-8001}
LOG_LEVEL=${LOG_LEVEL:-INFO}

echo ""
echo -e "${GREEN}========================================${NC}"
echo -e "${GREEN}服务配置信息${NC}"
echo -e "${GREEN}========================================${NC}"
echo -e "Base URL:    ${YELLOW}${ANTHROPIC_BASE_URL}${NC}"
echo -e "Model:       ${YELLOW}${CLAUDE_MODEL}${NC}"
echo -e "Host:        ${YELLOW}${SERVICE_HOST}${NC}"
echo -e "Port:        ${YELLOW}${SERVICE_PORT}${NC}"
echo -e "Log Level:   ${YELLOW}${LOG_LEVEL}${NC}"
echo -e "${GREEN}========================================${NC}"
echo ""

# 启动服务
echo -e "${GREEN}正在启动服务...${NC}"
echo ""

# 使用 uvicorn 启动，支持热重载
python -m uvicorn app.main:app \
    --host "$SERVICE_HOST" \
    --port "$SERVICE_PORT" \
    --reload \
    --log-level "$(echo $LOG_LEVEL | tr '[:upper:]' '[:lower:]')"
