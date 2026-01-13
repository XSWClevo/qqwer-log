#!/bin/bash
set -e

# ============================================
# Vector Agent 卸载脚本
# ============================================

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

INSTALL_DIR="/opt/vector-agent"

echo -e "${YELLOW}========================================${NC}"
echo -e "${YELLOW}  Vector Agent 卸载${NC}"
echo -e "${YELLOW}========================================${NC}"
echo ""

# 检查是否为 root
if [ "$EUID" -ne 0 ]; then
    echo -e "${RED}请使用 root 权限运行此脚本${NC}"
    exit 1
fi

# 确认
read -p "确定要卸载 Vector Agent 吗？(y/N) " -n 1 -r
echo
if [[ ! $REPLY =~ ^[Yy]$ ]]; then
    echo "取消卸载"
    exit 0
fi

# 1. 停止服务
echo -e "${YELLOW}[1/4] 停止服务...${NC}"
systemctl stop vector-agent 2>/dev/null || true
systemctl stop vector 2>/dev/null || true

# 2. 禁用服务
echo -e "${YELLOW}[2/4] 禁用服务...${NC}"
systemctl disable vector-agent 2>/dev/null || true
systemctl disable vector 2>/dev/null || true

# 3. 删除服务文件
echo -e "${YELLOW}[3/4] 删除服务文件...${NC}"
rm -f /etc/systemd/system/vector-agent.service
rm -f /etc/systemd/system/vector.service
systemctl daemon-reload

# 4. 删除文件
echo -e "${YELLOW}[4/4] 删除安装文件...${NC}"
rm -rf ${INSTALL_DIR}
rm -f /usr/local/bin/vector
rm -f /usr/local/bin/vector-agent

echo ""
echo -e "${GREEN}========================================${NC}"
echo -e "${GREEN}  卸载完成！${NC}"
echo -e "${GREEN}========================================${NC}"
