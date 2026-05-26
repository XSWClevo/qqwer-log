#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SENDER="${SCRIPT_DIR}/send_udp_10514.py"

BACKEND_URL="${BACKEND_URL:-http://localhost:8080}"
MACHINE_ID="${MACHINE_ID:-d8d54a9684cb35b2b0862ba6253ba597}"
CONFIG_NAME="${CONFIG_NAME:-tmp-alias-udp-syslog-test}"
SOURCE_NAME="${SOURCE_NAME:-source_syslog_alias_test}"
TRANSFORM_NAME="${TRANSFORM_NAME:-${SOURCE_NAME}_parse}"
SINK_NAME="${SINK_NAME:-${SOURCE_NAME}_clickhouse}"
HOST="${HOST:-127.0.0.1}"
PORT="${PORT:-10514}"
COUNT="${COUNT:-0}"
RATE="${RATE:-2}"
DEPLOY_CONFIG="${DEPLOY_CONFIG:-1}"
CLEANUP_CONFIG="${CLEANUP_CONFIG:-0}"
CLICKHOUSE_ENDPOINT="${CLICKHOUSE_ENDPOINT:-http://localhost:8123}"
CLICKHOUSE_DATABASE="${CLICKHOUSE_DATABASE:-default}"
CLICKHOUSE_TABLE="${CLICKHOUSE_TABLE:-syslog_logs}"
CLICKHOUSE_USER="${CLICKHOUSE_USER:-default}"
CLICKHOUSE_PASSWORD="${CLICKHOUSE_PASSWORD:-12345678}"

detect_bind_ip() {
  ifconfig | awk '
    /^[a-z0-9]+:/ { iface=$1 }
    /inet / && $2 !~ /^127\./ && $2 != "0.0.0.0" {
      print $2
      exit
    }
  '
}

IPS="${IPS:-$(detect_bind_ip)}"
if [[ -z "${IPS}" ]]; then
  echo "No bind IP found. Set IPS=your.local.ip before running." >&2
  exit 1
fi

json_field() {
  python3 -c '
import json
import sys

path = sys.argv[1].split(".")
data = json.load(sys.stdin)
for item in path:
    if isinstance(data, list):
        data = data[int(item)]
    else:
        data = data[item]
print(data)
' "$1"
}

find_config_id() {
  curl -fsS "${BACKEND_URL}/api/vector/visual-configs" | python3 -c '
import json
import sys

target = sys.argv[1]
payload = json.load(sys.stdin)
for item in payload.get("data", []):
    if item.get("name") == target:
        print(item.get("id"))
        break
' "${CONFIG_NAME}"
}

ensure_udp_config() {
  local config_id
  config_id="$(find_config_id || true)"

  if [[ -z "${config_id}" ]]; then
    config_id="$(
      curl -fsS -X POST "${BACKEND_URL}/api/vector/visual-configs" \
        -H "Content-Type: application/json" \
        -d "{\"name\":\"${CONFIG_NAME}\",\"description\":\"temporary alias udp syslog test\",\"format\":\"namespace_yaml\"}" |
      json_field "data.id"
    )"
  fi

  local update_json
  update_json="$(mktemp)"
  python3 - \
    "${SOURCE_NAME}" \
    "${TRANSFORM_NAME}" \
    "${SINK_NAME}" \
    "${PORT}" \
    "${CLICKHOUSE_ENDPOINT}" \
    "${CLICKHOUSE_DATABASE}" \
    "${CLICKHOUSE_TABLE}" \
    "${CLICKHOUSE_USER}" \
    "${CLICKHOUSE_PASSWORD}" > "${update_json}" <<'PY'
import json
import sys

source_name = sys.argv[1]
transform_name = sys.argv[2]
sink_name = sys.argv[3]
port = sys.argv[4]
clickhouse_endpoint = sys.argv[5]
clickhouse_database = sys.argv[6]
clickhouse_table = sys.argv[7]
clickhouse_user = sys.argv[8]
clickhouse_password = sys.argv[9]

content = f"""sources:
  {source_name}:
    type: syslog
    address: 0.0.0.0:{port}
    mode: udp
transforms:
  {transform_name}:
    type: remap
    inputs:
      - {source_name}
    source: |-
      network_source_ip = to_string(.source_ip) ?? to_string(.host) ?? ""
      .raw = to_string(.message) ?? ""
      syslog_result = parse_syslog(.raw) ?? null
      if syslog_result != null {{
        . = merge!(., syslog_result)
      }} else {{
        .parse_error = "syslog 解析失败"
      }}
      .appname = to_string(.appname) ?? ""
      .facility = to_string(.facility) ?? ""
      .hostname = to_string(.hostname) ?? ""
      .message = to_string(.message) ?? ""
      .msgid = to_string(.msgid) ?? ""
      .procid = to_int(.procid) ?? 0
      .severity = to_string(.severity) ?? ""
      .version = to_int(.version) ?? 0
      if network_source_ip != "" {{
        .source_ip = replace(network_source_ip, r':\\d+$', "")
      }}
sinks:
  {sink_name}:
    type: clickhouse
    inputs:
      - {transform_name}
    endpoint: {clickhouse_endpoint}
    database: {clickhouse_database}
    table: {clickhouse_table}
    skip_unknown_fields: true
    encoding:
      timestamp_format: unix_ms
    auth:
      strategy: basic
      user: {clickhouse_user}
      password: "{clickhouse_password}"
    batch:
      max_events: 100
      timeout_secs: 5
    buffer:
      type: memory
      max_events: 10000
"""
print(json.dumps({"content": content, "nodeCount": 3}))
PY

  curl -fsS -X PUT "${BACKEND_URL}/api/vector/visual-configs/${config_id}" \
    -H "Content-Type: application/json" \
    --data-binary @"${update_json}" >/dev/null
  rm -f "${update_json}"

  curl -fsS -X POST "${BACKEND_URL}/api/vector/deployments" \
    -H "Content-Type: application/json" \
    -d "{\"hostIds\":[\"${MACHINE_ID}\"],\"configId\":\"${config_id}\",\"deployMode\":\"restart\"}" >/dev/null

  echo "${config_id}"
}

wait_for_vector_config() {
  local source_file="/opt/vector-agent/config/sources/${SOURCE_NAME}.yaml"
  local sink_file="/opt/vector-agent/config/sinks/${SINK_NAME}.yaml"
  for _ in $(seq 1 24); do
    if [[ -f "${source_file}" ]] && [[ -f "${sink_file}" ]] && grep -q "mode: udp" "${source_file}"; then
      /opt/vector-agent/bin/vector validate --no-environment --config-dir /opt/vector-agent/config >/dev/null
      return 0
    fi
    sleep 5
  done

  echo "Timed out waiting for Vector UDP config: ${source_file}" >&2
  return 1
}

CONFIG_ID=""
if [[ "${DEPLOY_CONFIG}" == "1" ]]; then
  echo "Ensuring UDP syslog config on ${HOST}:${PORT} for machine ${MACHINE_ID}..."
  CONFIG_ID="$(ensure_udp_config)"
  wait_for_vector_config
fi

echo "Sending UDP syslog: host=${HOST}, port=${PORT}, ips=${IPS}, count=${COUNT}, rate=${RATE}"
python3 "${SENDER}" \
  --host "${HOST}" \
  --port "${PORT}" \
  --count "${COUNT}" \
  --rate "${RATE}" \
  --ips "${IPS}" \
  --bind

if [[ "${COUNT}" != "0" ]]; then
  echo "Waiting for Vector HTTP sink batch flush..."
  sleep 36
  curl -fsS -X POST "${BACKEND_URL}/api/log-sources/pending" \
    -H "Content-Type: application/json" \
    -d "{}"
  echo
fi

if [[ "${CLEANUP_CONFIG}" == "1" && -n "${CONFIG_ID}" ]]; then
  curl -fsS -X DELETE "${BACKEND_URL}/api/vector/visual-configs/${CONFIG_ID}" >/dev/null
  echo "Deleted temporary config ${CONFIG_ID}"
fi
