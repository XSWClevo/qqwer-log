#!/bin/bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"

create_fake_go() {
    local fakebin_dir="$1"

    cat > "${fakebin_dir}/go" <<'EOF'
#!/bin/bash
set -euo pipefail

if [ -z "${CAPTURE_FILE:-}" ]; then
    echo "CAPTURE_FILE is required" >&2
    exit 1
fi

printf '%s:%s\n' "${GOOS:-}" "${GOARCH:-}" > "${CAPTURE_FILE}"

OUTPUT_FILE=""
while [ "$#" -gt 0 ]; do
    if [ "$1" = "-o" ]; then
        OUTPUT_FILE="$2"
        shift 2
        continue
    fi
    shift
done

if [ -z "${OUTPUT_FILE}" ]; then
    echo "missing -o output path" >&2
    exit 1
fi

mkdir -p "$(dirname "${OUTPUT_FILE}")"
touch "${OUTPUT_FILE}"
EOF
    chmod +x "${fakebin_dir}/go"
}

run_arch_mapping_test() {
    local tmp_dir
    tmp_dir="$(mktemp -d)"
    trap 'rm -rf "${tmp_dir}"' RETURN

    mkdir -p "${tmp_dir}/scripts" "${tmp_dir}/bin" "${tmp_dir}/fakebin"
    cp "${SCRIPT_DIR}/build-bundle.sh" "${tmp_dir}/scripts/"
    cp "${SCRIPT_DIR}/install.sh" "${tmp_dir}/scripts/"
    create_fake_go "${tmp_dir}/fakebin"

    cat > "${tmp_dir}/scripts/download-vector.sh" <<'EOF'
#!/bin/bash
set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_DIR="$(dirname "${SCRIPT_DIR}")"
BIN_DIR="${PROJECT_DIR}/bin"
printf '%s:%s\n' "${2:-}" "${3:-}" > "${PROJECT_DIR}/download-args.txt"
cat > "${BIN_DIR}/vector" <<'INNER'
#!/bin/bash
exit 99
INNER
chmod +x "${BIN_DIR}/vector"
printf '0.55.0\n' > "${BIN_DIR}/vector.version"
EOF
    chmod +x "${tmp_dir}/scripts/download-vector.sh"

    CAPTURE_FILE="${tmp_dir}/go-env.txt" PATH="${tmp_dir}/fakebin:${PATH}" \
        bash "${tmp_dir}/scripts/build-bundle.sh" 9.9.9 linux x86_64 > "${tmp_dir}/build.log" 2>&1

    if ! grep -qx 'linux:amd64' "${tmp_dir}/go-env.txt"; then
        echo "expected GOOS/GOARCH to be linux:amd64" >&2
        cat "${tmp_dir}/go-env.txt" >&2
        exit 1
    fi

    local expected_bundle="${tmp_dir}/dist/vector-agent-bundle-9.9.9-linux-amd64.tar.gz"
    if [ ! -f "${expected_bundle}" ]; then
        echo "expected bundle archive ${expected_bundle}" >&2
        ls -la "${tmp_dir}/dist" >&2 || true
        exit 1
    fi

    if ! grep -qx 'linux:x86_64' "${tmp_dir}/download-args.txt"; then
        echo "expected download-vector.sh to receive linux:x86_64" >&2
        cat "${tmp_dir}/download-args.txt" >&2
        exit 1
    fi
}

run_cross_platform_vector_refresh_test() {
    local tmp_dir
    tmp_dir="$(mktemp -d)"
    trap 'rm -rf "${tmp_dir}"' RETURN

    mkdir -p "${tmp_dir}/scripts" "${tmp_dir}/bin" "${tmp_dir}/fakebin"
    cp "${SCRIPT_DIR}/build-bundle.sh" "${tmp_dir}/scripts/"
    cp "${SCRIPT_DIR}/install.sh" "${tmp_dir}/scripts/"
    create_fake_go "${tmp_dir}/fakebin"

    cat > "${tmp_dir}/bin/vector" <<'EOF'
not-a-linux-binary
EOF
    chmod +x "${tmp_dir}/bin/vector"

    cat > "${tmp_dir}/scripts/download-vector.sh" <<'EOF'
#!/bin/bash
set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_DIR="$(dirname "${SCRIPT_DIR}")"
BIN_DIR="${PROJECT_DIR}/bin"
printf '%s:%s\n' "${2:-}" "${3:-}" > "${PROJECT_DIR}/download-args.txt"
cat > "${BIN_DIR}/vector" <<'INNER'
#!/bin/bash
exit 99
INNER
chmod +x "${BIN_DIR}/vector"
printf '0.66.0\n' > "${BIN_DIR}/vector.version"
EOF
    chmod +x "${tmp_dir}/scripts/download-vector.sh"

    CAPTURE_FILE="${tmp_dir}/go-env.txt" PATH="${tmp_dir}/fakebin:${PATH}" \
        bash "${tmp_dir}/scripts/build-bundle.sh" 8.8.8 linux x86_64 > "${tmp_dir}/build.log" 2>&1

    if ! grep -qx 'linux:x86_64' "${tmp_dir}/download-args.txt"; then
        echo "expected download-vector.sh to receive linux:x86_64" >&2
        cat "${tmp_dir}/download-args.txt" >&2
        exit 1
    fi

    if ! grep -q '^VECTOR_VERSION=0.66.0$' <(tar -xOf "${tmp_dir}/dist/vector-agent-bundle-8.8.8-linux-amd64.tar.gz" ./VERSION); then
        echo "expected VERSION file to include downloaded vector version" >&2
        tar -xOf "${tmp_dir}/dist/vector-agent-bundle-8.8.8-linux-amd64.tar.gz" ./VERSION >&2
        exit 1
    fi
}

run_arch_mapping_test
run_cross_platform_vector_refresh_test

echo "test-build-bundle: ok"
