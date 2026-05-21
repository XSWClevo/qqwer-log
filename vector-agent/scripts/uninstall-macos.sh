#!/bin/bash
set -e

# ============================================
# Vector Agent 卸载脚本 (macOS)
# ============================================

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

INSTALL_DIR="/opt/vector-agent"
PLIST_DIR="/Library/LaunchDaemons"

echo -e "${YELLOW}========================================${NC}"
echo -e "${YELLOW}  Vector Agent 卸载 (macOS)${NC}"
echo -e "${YELLOW}========================================${NC}"
echo ""

# 确认
read -p "确定要卸载 Vector Agent 吗？(y/N) " -n 1 -r
echo
if [[ ! $REPLY =~ ^[Yy]$ ]]; then
    echo "取消卸载"
    exit 0
fi

# 1. 停止并卸载 launchd 服务
echo -e "${YELLOW}[1/4] 停止服务...${NC}"
launchctl unload "${PLIST_DIR}/com.vector.agent.plist" 2>/dev/null || true
launchctl unload "${PLIST_DIR}/com.vector.plist" 2>/dev/null || true

# 2. 删除 plist 文件
echo -e "${YELLOW}[2/4] 删除服务配置...${NC}"
rm -f "${PLIST_DIR}/com.vector.agent.plist"
rm -f "${PLIST_DIR}/com.vector.plist"

# 3. 删除软链接
echo -e "${YELLOW}[3/4] 删除软链接...${NC}"
rm -f /usr/local/bin/vector 2>/dev/null || true
rm -f /usr/local/bin/vector-agent 2>/dev/null || true

# 4. 删除安装目录
echo -e "${YELLOW}[4/4] 删除安装文件...${NC}"
sudo rm -rf ${INSTALL_DIR}

echo ""
echo -e "${GREEN}========================================${NC}"
echo -e "${GREEN}  卸载完成！${NC}"
echo -e "${GREEN}========================================${NC}"
