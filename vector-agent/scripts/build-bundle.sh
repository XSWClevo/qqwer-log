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
HOST_OS="$(uname -s | tr '[:upper:]' '[:lower:]')"

# 默认构建当前系统平台，可通过参数指定其他平台
OS="${2:-${HOST_OS}}"
RAW_ARCH="${3:-$(uname -m)}"
ARCH="${RAW_ARCH}"
GO_ARCH="${RAW_ARCH}"

# 标准化打包架构名称和 Go 架构名称
case "$RAW_ARCH" in
    x86_64|amd64)
        ARCH="amd64"
        GO_ARCH="amd64"
        ;;
    aarch64|arm64)
        ARCH="arm64"
        GO_ARCH="arm64"
        ;;
esac

vector_binary_matches_target() {
    if [ ! -f "${BIN_DIR}/vector" ]; then
        return 1
    fi

    if ! command -v file >/dev/null 2>&1; then
        return 1
    fi

    local description
    description="$(file -b "${BIN_DIR}/vector" 2>/dev/null || true)"

    case "${OS}/${ARCH}" in
        linux/amd64)
            [[ "${description}" == *"ELF"* && "${description}" == *"x86-64"* ]]
            ;;
        linux/arm64)
            [[ "${description}" == *"ELF"* && "${description}" == *"aarch64"* ]]
            ;;
        darwin/arm64)
            [[ "${description}" == *"Mach-O"* && "${description}" == *"arm64"* ]]
            ;;
        darwin/amd64)
            [[ "${description}" == *"Mach-O"* && ( "${description}" == *"x86_64"* || "${description}" == *"arm64"* ) ]]
            ;;
        *)
            return 0
            ;;
    esac
}

read_vector_version() {
    if [ -f "${BIN_DIR}/vector.version" ]; then
        head -1 "${BIN_DIR}/vector.version"
        return
    fi

    if [ "${OS}" = "${HOST_OS}" ]; then
        "${BIN_DIR}/vector" --version 2>&1 | head -1 | awk '{print $2}'
        return
    fi

    echo "unknown"
}

echo "=========================================="
echo "  构建 Vector Agent Bundle"
echo "=========================================="
echo "版本: ${VERSION}"
echo "平台: ${OS}/${ARCH}"
echo ""

# 检查 vector 二进制是否存在且与目标平台匹配，不匹配则自动下载
if ! vector_binary_matches_target; then
    if [ -f "${BIN_DIR}/vector" ]; then
        echo "现有 Vector 与目标平台不匹配，开始重新下载..."
    else
        echo "Vector 二进制不存在，开始下载..., 目录: ${BIN_DIR}/vector"
    fi
    echo ""

    # 调用下载脚本
    if [ -f "${SCRIPT_DIR}/download-vector.sh" ]; then
        VECTOR_SKIP_SYSTEM_VECTOR=1 bash "${SCRIPT_DIR}/download-vector.sh" latest "$OS" "$RAW_ARCH"
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
GOOS=${OS} GOARCH=${GO_ARCH} go build \
    -ldflags "-X main.Version=${VERSION} -X main.BuildTime=${BUILD_TIME}" \
    -o ${WORK_DIR}/bin/vector-agent ./cmd/agent/main.go
echo "  -> vector-agent 编译完成"

# 2. 复制 Vector
echo "[2/4] 复制 vector..."
cp ${BIN_DIR}/vector ${WORK_DIR}/bin/
VECTOR_VERSION=$(read_vector_version)
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
