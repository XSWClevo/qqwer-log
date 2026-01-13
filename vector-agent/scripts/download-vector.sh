#!/bin/bash
# ============================================
# Vector 下载脚本
# 自动检测系统架构并下载最新版本的 Vector
# ============================================

set -e

GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
BLUE='\033[0;34m'
NC='\033[0m'

# 默认使用最新版本
VECTOR_VERSION="${1:-latest}"
# 可选：指定目标平台（用于交叉编译场景）
TARGET_OS="${2:-}"
TARGET_ARCH="${3:-}"

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"
BIN_DIR="${PROJECT_DIR}/bin"
SYSTEM_BIN_DIR="/opt/vector-agent/bin"

echo -e "${GREEN}========================================${NC}"
echo -e "${GREEN}  下载 Vector${NC}"
echo -e "${GREEN}========================================${NC}"
echo ""

# 检查系统目录中是否已存在 vector
check_system_vector() {
    if [ -f "${SYSTEM_BIN_DIR}/vector" ] && [ -x "${SYSTEM_BIN_DIR}/vector" ]; then
        local existing_version=$(${SYSTEM_BIN_DIR}/vector --version 2>&1 | head -1 || echo "unknown")
        echo -e "${BLUE}检测到系统已安装 Vector: ${existing_version}${NC}"
        echo -e "${BLUE}位置: ${SYSTEM_BIN_DIR}/vector${NC}"
        echo ""

        # 询问是否跳过下载
        read -p "是否跳过下载并使用已安装的版本? (Y/n): " -n 1 -r
        echo
        if [[ $REPLY =~ ^[Yy]$ ]] || [[ -z $REPLY ]]; then
            # 复制到项目 bin 目录
            mkdir -p "$BIN_DIR"
            cp "${SYSTEM_BIN_DIR}/vector" "$BIN_DIR/vector"
            chmod +x "$BIN_DIR/vector"
            echo -e "${GREEN}已复制到: ${BIN_DIR}/vector${NC}"
            echo ""
            return 0
        else
            echo -e "${YELLOW}继续下载新版本...${NC}"
            echo ""
            return 1
        fi
    fi
    return 1
}

# 检测系统架构
detect_platform() {
    local os=""
    local arch=""

    # 检测操作系统
    if [ -n "$TARGET_OS" ]; then
        os="$TARGET_OS"
    else
        os=$(uname -s | tr '[:upper:]' '[:lower:]')
    fi

    # 检测 CPU 架构
    if [ -n "$TARGET_ARCH" ]; then
        arch="$TARGET_ARCH"
    else
        arch=$(uname -m)
    fi

    echo -e "${YELLOW}检测到系统: ${os} ${arch}${NC}"

    # 标准化架构名称
    case "$arch" in
        aarch64) arch="arm64" ;;
        amd64) arch="x86_64" ;;
    esac

    # 构建 Vector 文件名模式（用于匹配 GitHub assets）
    case "$os" in
        darwin)
            # macOS: vector-0.52.0-arm64-apple-darwin.tar.gz
            # 注意：Vector 只提供 arm64 版本的 macOS 二进制
            if [ "$arch" = "x86_64" ]; then
                echo -e "${YELLOW}注意: Vector 官方不提供 macOS x86_64 版本${NC}"
                echo -e "${YELLOW}建议使用 Rosetta 2 运行 arm64 版本，或使用 Homebrew 安装${NC}"
                echo ""
                # 尝试使用 arm64 版本
                VECTOR_PATTERN="arm64-apple-darwin.tar.gz"
            else
                VECTOR_PATTERN="${arch}-apple-darwin.tar.gz"
            fi
            ;;
        linux)
            # Linux: vector-0.52.0-aarch64-unknown-linux-musl.tar.gz
            # 注意：Vector 使用 aarch64 而不是 arm64
            if [ "$arch" = "arm64" ]; then
                VECTOR_PATTERN="aarch64-unknown-linux-musl.tar.gz"
            else
                VECTOR_PATTERN="${arch}-unknown-linux-musl.tar.gz"
            fi
            ;;
        *)
            echo -e "${RED}不支持的操作系统: ${os}${NC}"
            echo "支持的系统: darwin (macOS), linux"
            exit 1
            ;;
    esac

    echo "  -> 目标平台: ${os}/${arch}"
    echo "  -> 匹配模式: *${VECTOR_PATTERN}"
    echo ""

    PLATFORM_OS="$os"
    PLATFORM_ARCH="$arch"
}

# 获取最新版本号和下载链接
get_latest_release() {
    echo -e "${YELLOW}获取 Vector 最新版本信息...${NC}"

    # 从 GitHub API 获取最新 release
    local api_url="https://api.github.com/repos/vectordotdev/vector/releases/latest"
    local release_json=$(curl -s "$api_url")

    if [ -z "$release_json" ]; then
        echo -e "${RED}无法获取 release 信息${NC}"
        exit 1
    fi

    # 解析版本号
    local version=$(echo "$release_json" | grep '"tag_name":' | sed -E 's/.*"v([^"]+)".*/\1/' | head -1)

    if [ -z "$version" ]; then
        echo -e "${RED}无法解析版本号${NC}"
        exit 1
    fi

    echo "  -> 最新版本: v${version}"

    # 查找匹配的下载链接
    # 使用 grep 过滤出包含 browser_download_url 的行，然后匹配我们的模式
    local download_url=$(echo "$release_json" | grep '"browser_download_url":' | grep "${VECTOR_PATTERN}" | sed -E 's/.*"browser_download_url": "([^"]+)".*/\1/' | head -1)

    if [ -z "$download_url" ]; then
        echo -e "${RED}未找到匹配的下载链接${NC}"
        echo "  匹配模式: *${VECTOR_PATTERN}"
        echo ""
        echo "可用的下载文件:"
        echo "$release_json" | grep '"browser_download_url":' | sed -E 's/.*"browser_download_url": "([^"]+)".*/\1/' | sed 's|.*/||'
        exit 1
    fi

    echo "  -> 下载链接: ${download_url}"
    echo ""

    VECTOR_VERSION="$version"
    DOWNLOAD_URL="$download_url"
}

# 下载 Vector
download_vector() {
    local filename=$(basename "$DOWNLOAD_URL")
    local temp_file="/tmp/${filename}"
    local temp_dir="/tmp/vector-extract-$$"

    echo -e "${YELLOW}下载 Vector...${NC}"
    echo "  文件: ${filename}"
    echo ""

    # 下载
    if ! curl -L --progress-bar -o "$temp_file" "$DOWNLOAD_URL"; then
        echo -e "${RED}下载失败！${NC}"
        echo ""
        echo "请检查："
        echo "  1. 网络连接是否正常"
        echo "  2. 下载链接是否有效"
        echo ""
        echo "或手动下载并放置到: ${BIN_DIR}/vector"
        echo "下载地址: ${DOWNLOAD_URL}"
        rm -f "$temp_file"
        exit 1
    fi

    echo -e "${GREEN}  -> 下载完成${NC}"
    echo ""

    # 解压
    echo -e "${YELLOW}解压 Vector...${NC}"
    mkdir -p "$temp_dir"

    if ! tar -xzf "$temp_file" -C "$temp_dir"; then
        echo -e "${RED}解压失败！${NC}"
        rm -rf "$temp_dir" "$temp_file"
        exit 1
    fi

    # 查找 vector 二进制文件
    local vector_bin=$(find "$temp_dir" -name "vector" -type f -executable | head -1)

    if [ -z "$vector_bin" ]; then
        echo -e "${RED}错误: 在压缩包中未找到 vector 二进制文件${NC}"
        echo "压缩包内容:"
        ls -la "$temp_dir"
        rm -rf "$temp_dir" "$temp_file"
        exit 1
    fi

    # 复制到 bin 目录
    mkdir -p "$BIN_DIR"
    cp "$vector_bin" "$BIN_DIR/vector"
    chmod +x "$BIN_DIR/vector"

    echo -e "${GREEN}  -> 已安装到: ${BIN_DIR}/vector${NC}"
    echo ""

    # 验证
    if [ -x "$BIN_DIR/vector" ]; then
        local installed_version=$("$BIN_DIR/vector" --version 2>&1 | head -1)
        echo -e "${GREEN}  -> 验证成功: ${installed_version}${NC}"
    fi

    # 清理
    rm -rf "$temp_dir" "$temp_file"
}

# 主流程
main() {
    # 1. 检查系统目录中是否已存在 vector
    if check_system_vector; then
        echo -e "${GREEN}========================================${NC}"
        echo -e "${GREEN}  使用已安装的 Vector${NC}"
        echo -e "${GREEN}========================================${NC}"
        exit 0
    fi

    # 2. 检测平台
    detect_platform

    # 3. 获取最新版本和下载链接
    if [ "$VECTOR_VERSION" = "latest" ]; then
        get_latest_release
    else
        echo -e "${YELLOW}使用指定版本: v${VECTOR_VERSION}${NC}"
        # 构建下载链接
        DOWNLOAD_URL="https://github.com/vectordotdev/vector/releases/download/v${VECTOR_VERSION}/vector-${VECTOR_VERSION}-${VECTOR_PATTERN}"
        echo "  -> 下载链接: ${DOWNLOAD_URL}"
        echo ""
    fi

    # 4. 下载
    download_vector

    echo ""
    echo -e "${GREEN}========================================${NC}"
    echo -e "${GREEN}  Vector 下载完成！${NC}"
    echo -e "${GREEN}========================================${NC}"
    echo ""
    echo "位置: ${BIN_DIR}/vector"
    echo "版本: ${VECTOR_VERSION}"
    echo "平台: ${PLATFORM_OS}/${PLATFORM_ARCH}"
    echo ""
}

main
