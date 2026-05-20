#!/bin/bash
set -e

# ============================================
# Vector Agent Bundle 构建脚本
# 打包 vector-agent + vector 为一体化安装包
# 支持多平台构建: macOS (arm64/x86_64), Linux (arm64/x86_64)
# ============================================

VERSION="${1:-1.0.0}"

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"
OUTPUT_DIR="${PROJECT_DIR}/dist"
BIN_DIR="${PROJECT_DIR}/bin"
BUILD_TIME=$(date -u '+%Y-%m-%d_%H:%M:%S')

# 默认构建当前系统平台，可通过参数指定其他平台
OS="${2:-$(uname -s | tr '[:upper:]' '[:lower:]')}"
ARCH="${3:-$(uname -m)}"

# 标准化架构名称
case "$ARCH" in
    aarch64) ARCH="arm64" ;;
    amd64) ARCH="x86_64" ;;
esac

echo "=========================================="
echo "  构建 Vector Agent Bundle"
echo "=========================================="
echo "版本: ${VERSION}"
echo "平台: ${OS}/${ARCH}"
echo ""

# 检查 vector 二进制是否存在，不存在则自动下载
if [ ! -f "${BIN_DIR}/vector" ]; then
    echo "Vector 二进制不存在，开始下载..., 目录: ${BIN_DIR}/vector"
    echo ""

    # 调用下载脚本
    if [ -f "${SCRIPT_DIR}/download-vector.sh" ]; then
        bash "${SCRIPT_DIR}/download-vector.sh" latest "$OS" "$ARCH"
    else
        echo "错误: 下载脚本不存在: ${SCRIPT_DIR}/download-vector.sh"
        echo ""
        echo "请手动下载 Vector 并放置到: ${BIN_DIR}/vector"
        echo "下载地址: https://github.com/vectordotdev/vector/releases"
        exit 1
    fi

    echo ""
fi

# 再次检查 vector 是否存在
if [ ! -f "${BIN_DIR}/vector" ]; then
    echo "错误: Vector 下载失败或未找到"
    exit 1
fi

mkdir -p ${OUTPUT_DIR}

WORK_DIR="/tmp/vector-agent-bundle-$$"
rm -rf ${WORK_DIR}
mkdir -p ${WORK_DIR}/bin

# 1. 编译 Agent
echo "[1/4] 编译 vector-agent..."
cd ${PROJECT_DIR}
GOOS=${OS} GOARCH=${ARCH} go build \
    -ldflags "-X main.Version=${VERSION} -X main.BuildTime=${BUILD_TIME}" \
    -o ${WORK_DIR}/bin/vector-agent ./cmd/agent/main.go
echo "  -> vector-agent 编译完成"

# 2. 复制 Vector
echo "[2/4] 复制 vector..."
cp ${BIN_DIR}/vector ${WORK_DIR}/bin/
VECTOR_VERSION=$(${BIN_DIR}/vector --version 2>&1 | head -1 | awk '{print $2}')
echo "  -> vector ${VECTOR_VERSION}"

# 3. 复制安装脚本
echo "[3/4] 准备安装脚本..."
cp ${SCRIPT_DIR}/install.sh ${WORK_DIR}/
chmod +x ${WORK_DIR}/bin/* ${WORK_DIR}/install.sh

# 创建版本信息
cat > ${WORK_DIR}/VERSION <<EOF
BUNDLE_VERSION=${VERSION}
AGENT_VERSION=${VERSION}
VECTOR_VERSION=${VECTOR_VERSION}
BUILD_TIME=${BUILD_TIME}
OS=${OS}
ARCH=${ARCH}
EOF

# 4. 打包
echo "[4/4] 打包..."
BUNDLE_NAME="vector-agent-bundle-${VERSION}-${OS}-${ARCH}.tar.gz"
tar -czf ${OUTPUT_DIR}/${BUNDLE_NAME} -C ${WORK_DIR} .

# 清理
rm -rf ${WORK_DIR}

echo ""
echo "=========================================="
echo "  构建完成！"
echo "=========================================="
echo ""
echo "安装包: ${OUTPUT_DIR}/${BUNDLE_NAME}"
echo "大小: $(du -h ${OUTPUT_DIR}/${BUNDLE_NAME} | cut -f1)"
echo ""
echo "下一步:"
echo "  1. 上传到安装包管理页面"
echo "  2. 或手动安装:"
echo "     tar -xzf ${BUNDLE_NAME}"
echo "     sudo ./install.sh <TOKEN> <SERVER_URL>"
