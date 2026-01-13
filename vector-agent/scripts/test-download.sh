#!/bin/bash
# ============================================
# Vector 下载脚本测试
# 测试不同平台的下载链接解析
# ============================================

set -e

GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
BLUE='\033[0;34m'
NC='\033[0m'

echo -e "${GREEN}========================================${NC}"
echo -e "${GREEN}  测试 Vector 下载链接解析${NC}"
echo -e "${GREEN}========================================${NC}"
echo ""

# 测试平台列表
platforms=(
    "darwin:arm64"
    "darwin:x86_64"
    "linux:arm64"
    "linux:x86_64"
)

# 获取最新 release 信息
echo -e "${YELLOW}获取最新 Release 信息...${NC}"
api_url="https://api.github.com/repos/vectordotdev/vector/releases/latest"
release_json=$(curl -s "$api_url")

if [ -z "$release_json" ]; then
    echo -e "${RED}无法获取 release 信息${NC}"
    exit 1
fi

# 解析版本号
version=$(echo "$release_json" | grep '"tag_name":' | sed -E 's/.*"v([^"]+)".*/\1/' | head -1)
echo -e "${GREEN}最新版本: v${version}${NC}"
echo ""

# 测试每个平台
for platform in "${platforms[@]}"; do
    IFS=':' read -r os arch <<< "$platform"

    echo -e "${BLUE}----------------------------------------${NC}"
    echo -e "${BLUE}测试平台: ${os}/${arch}${NC}"
    echo -e "${BLUE}----------------------------------------${NC}"

    # 构建匹配模式
    case "$os" in
        darwin)
            if [ "$arch" = "x86_64" ]; then
                pattern="arm64-apple-darwin.tar.gz"
                echo "注意: Vector 不提供 macOS x86_64，使用 arm64 版本"
            else
                pattern="${arch}-apple-darwin.tar.gz"
            fi
            ;;
        linux)
            if [ "$arch" = "arm64" ]; then
                pattern="aarch64-unknown-linux-musl.tar.gz"
            else
                pattern="${arch}-unknown-linux-musl.tar.gz"
            fi
            ;;
    esac

    echo "匹配模式: *${pattern}"

    # 查找下载链接
    download_url=$(echo "$release_json" | grep '"browser_download_url":' | grep "${pattern}" | sed -E 's/.*"browser_download_url": "([^"]+)".*/\1/' | head -1)

    if [ -z "$download_url" ]; then
        echo -e "${RED}❌ 未找到匹配的下载链接${NC}"
    else
        echo -e "${GREEN}✅ 找到下载链接:${NC}"
        echo "   ${download_url}"
    fi
    echo ""
done

echo -e "${GREEN}========================================${NC}"
echo -e "${GREEN}  测试完成${NC}"
echo -e "${GREEN}========================================${NC}"
echo ""
echo "所有可用的下载文件:"
echo "$release_json" | grep '"browser_download_url":' | sed -E 's/.*"browser_download_url": "([^"]+)".*/\1/' | sed 's|.*/||' | sort
