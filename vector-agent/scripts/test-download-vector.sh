#!/bin/bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"

create_fake_curl() {
    local fakebin_dir="$1"

    cat > "${fakebin_dir}/curl" <<'EOF'
#!/bin/bash
set -euo pipefail

if [[ "$*" == *"/releases?per_page=20"* ]]; then
    cat <<'JSON'
[
  {
    "tag_name": "vdev-v0.3.3",
    "draft": false,
    "prerelease": false
  },
  {
    "tag_name": "v0.55.0",
    "draft": false,
    "prerelease": false
  }
]
JSON
    exit 0
fi

output_file=""
url=""
while [ "$#" -gt 0 ]; do
    case "$1" in
        -o)
            output_file="$2"
            shift 2
            ;;
        http*)
            url="$1"
            shift
            ;;
        *)
            shift
            ;;
    esac
done

if [ -z "${CAPTURE_URL_FILE:-}" ]; then
    echo "CAPTURE_URL_FILE is required" >&2
    exit 1
fi

printf '%s\n' "${url}" > "${CAPTURE_URL_FILE}"
if [ -n "${output_file}" ]; then
    : > "${output_file}"
fi
EOF
    chmod +x "${fakebin_dir}/curl"
}

create_fake_tar() {
    local fakebin_dir="$1"

    cat > "${fakebin_dir}/tar" <<'EOF'
#!/bin/bash
set -euo pipefail

target_dir=""
while [ "$#" -gt 0 ]; do
    case "$1" in
        -C)
            target_dir="$2"
            shift 2
            ;;
        *)
            shift
            ;;
    esac
done

mkdir -p "${target_dir}/vector-dist/bin"
cat > "${target_dir}/vector-dist/bin/vector" <<'INNER'
#!/bin/bash
echo "vector 0.55.0"
INNER
chmod +x "${target_dir}/vector-dist/bin/vector"
EOF
    chmod +x "${fakebin_dir}/tar"
}

run_latest_stable_release_test() {
    local tmp_dir
    tmp_dir="$(mktemp -d)"
    trap 'rm -rf "${tmp_dir}"' RETURN

    mkdir -p "${tmp_dir}/scripts" "${tmp_dir}/bin" "${tmp_dir}/fakebin"
    cp "${SCRIPT_DIR}/download-vector.sh" "${tmp_dir}/scripts/"
    create_fake_curl "${tmp_dir}/fakebin"
    create_fake_tar "${tmp_dir}/fakebin"

    CAPTURE_URL_FILE="${tmp_dir}/download-url.txt" PATH="${tmp_dir}/fakebin:${PATH}" \
        VECTOR_SKIP_SYSTEM_VECTOR=1 bash "${tmp_dir}/scripts/download-vector.sh" latest linux x86_64 > "${tmp_dir}/download.log" 2>&1

    local expected_url="https://github.com/vectordotdev/vector/releases/download/v0.55.0/vector-0.55.0-x86_64-unknown-linux-gnu.tar.gz"
    if ! grep -qx "${expected_url}" "${tmp_dir}/download-url.txt"; then
        echo "expected download url ${expected_url}" >&2
        cat "${tmp_dir}/download-url.txt" >&2
        exit 1
    fi

    if [ ! -x "${tmp_dir}/bin/vector" ]; then
        echo "expected vector binary to be installed" >&2
        ls -la "${tmp_dir}/bin" >&2 || true
        exit 1
    fi

    if ! grep -qx '0.55.0' "${tmp_dir}/bin/vector.version"; then
        echo "expected vector.version to contain stable version" >&2
        cat "${tmp_dir}/bin/vector.version" >&2
        exit 1
    fi
}

run_latest_stable_release_test

echo "test-download-vector: ok"
