#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SENDER="${SENDER:-${SCRIPT_DIR}/send_attack_syslog_10514.py}"

HOST="${HOST:-127.0.0.1}"
PORT="${PORT:-10514}"
COUNT="${COUNT:-0}"
RATE="${RATE:-1}"
SYSLOG_HOSTNAME="${SYSLOG_HOSTNAME:-attack-simulator.local}"
ATTACKER_IPS="${ATTACKER_IPS:-10.77.10.11,10.77.10.12,10.77.10.13,10.77.10.14,10.77.10.15}"
SOURCE_IPS="${SOURCE_IPS:-${ATTACKER_IPS}}"
BIND_SOURCE_IPS="${BIND_SOURCE_IPS:-1}"
SETUP_IP_ALIASES="${SETUP_IP_ALIASES:-1}"
CLEANUP_IP_ALIASES="${CLEANUP_IP_ALIASES:-0}"
SHUFFLE="${SHUFFLE:-0}"
LOOP="${LOOP:-0}"
TYPES="${TYPES:-}"

print_usage() {
  cat <<EOF
Usage:
  $(basename "$0")
  HOST=127.0.0.1 PORT=10514 COUNT=50 RATE=1 SYSLOG_HOSTNAME=attack01 SHUFFLE=1 $(basename "$0")
  ATTACKER_IPS=10.77.10.11,10.77.10.12 COUNT=100 $(basename "$0")
  LOOP=1 RATE=1 TYPES=ssh_bruteforce,sql_injection $(basename "$0")
  $(basename "$0") --count 50 --rate 2 --shuffle
  $(basename "$0") --loop --types ssh_bruteforce,webshell_upload

Environment variables:
  HOST       Target syslog host. Default: ${HOST}
  PORT       Target syslog UDP port. Default: ${PORT}
  COUNT      Number of messages to send; 0 means send forever. Default: ${COUNT}
  RATE       Messages per second. Default: ${RATE}
  SYSLOG_HOSTNAME
             Syslog hostname field. Supports {ip} and {seq}. Default: ${SYSLOG_HOSTNAME}
  ATTACKER_IPS
             Comma-separated attacker IPs written into the message body. Default: ${ATTACKER_IPS}
  SOURCE_IPS Local IP aliases used as UDP source IPs. Default: ATTACKER_IPS
  BIND_SOURCE_IPS
             Bind UDP sockets to SOURCE_IPS, so Vector receives different source_ip values. Default: ${BIND_SOURCE_IPS}
  SETUP_IP_ALIASES
             Add SOURCE_IPS as local loopback aliases before sending. Requires sudo. Default: ${SETUP_IP_ALIASES}
  CLEANUP_IP_ALIASES
             Remove SOURCE_IPS aliases on exit. Default: ${CLEANUP_IP_ALIASES}
  SHUFFLE    Shuffle sample attacks when set to 1/true/yes/on. Default: ${SHUFFLE}
  LOOP       Send forever when set to 1/true/yes/on. Default: ${LOOP}
  TYPES      Comma-separated attack types. Empty means all sample types.
  SENDER     Python sender path. Default: ${SENDER}

Extra command line args are passed to send_attack_syslog_10514.py and override defaults.
EOF
}

is_truthy() {
  case "$(printf '%s' "${1:-}" | tr '[:upper:]' '[:lower:]')" in
    1|true|yes|on) return 0 ;;
    *) return 1 ;;
  esac
}

split_csv() {
  printf '%s' "$1" | tr ',' '\n' | awk '{$1=$1; if ($0 != "") print $0}'
}

setup_ip_aliases() {
  local os_name
  os_name="$(uname -s)"
  while IFS= read -r ip; do
    [[ -z "${ip}" ]] && continue
    case "${os_name}" in
      Darwin)
        if ifconfig lo0 | grep -q "inet ${ip} "; then
          continue
        fi
        sudo ifconfig lo0 alias "${ip}" 255.255.255.255
        ;;
      Linux)
        if ip addr show dev lo | grep -q " ${ip}/32 "; then
          continue
        fi
        sudo ip addr add "${ip}/32" dev lo
        ;;
      *)
        echo "Unsupported OS for automatic alias setup: ${os_name}. Please configure SOURCE_IPS manually." >&2
        return 1
        ;;
    esac
  done < <(split_csv "${SOURCE_IPS}")
}

cleanup_ip_aliases() {
  local os_name
  os_name="$(uname -s)"
  while IFS= read -r ip; do
    [[ -z "${ip}" ]] && continue
    case "${os_name}" in
      Darwin)
        sudo ifconfig lo0 -alias "${ip}" >/dev/null 2>&1 || true
        ;;
      Linux)
        sudo ip addr del "${ip}/32" dev lo >/dev/null 2>&1 || true
        ;;
    esac
  done < <(split_csv "${SOURCE_IPS}")
}

if [[ "${1:-}" == "-h" || "${1:-}" == "--help" ]]; then
  print_usage
  echo
  python3 "${SENDER}" --help
  exit 0
fi

if is_truthy "${SETUP_IP_ALIASES}"; then
  echo "Ensuring local IP aliases on loopback: ${SOURCE_IPS}"
  setup_ip_aliases
fi

if is_truthy "${CLEANUP_IP_ALIASES}"; then
  trap cleanup_ip_aliases EXIT
fi

cmd=(
  python3 "${SENDER}"
  --host "${HOST}"
  --port "${PORT}"
  --count "${COUNT}"
  --rate "${RATE}"
  --hostname "${SYSLOG_HOSTNAME}"
  --attacker-ips "${ATTACKER_IPS}"
  --source-ips "${SOURCE_IPS}"
)

if is_truthy "${BIND_SOURCE_IPS}"; then
  cmd+=(--bind-source-ips)
fi

if is_truthy "${SHUFFLE}"; then
  cmd+=(--shuffle)
fi

if is_truthy "${LOOP}"; then
  cmd+=(--loop)
fi

if [[ -n "${TYPES}" ]]; then
  cmd+=(--types "${TYPES}")
fi

cmd+=("$@")

echo "Sending attack syslog: host=${HOST}, port=${PORT}, count=${COUNT}, rate=${RATE}, hostname=${SYSLOG_HOSTNAME}, attacker_ips=${ATTACKER_IPS}, source_ips=${SOURCE_IPS}, bind_source_ips=${BIND_SOURCE_IPS}, shuffle=${SHUFFLE}, loop=${LOOP}, types=${TYPES:-all}"
exec "${cmd[@]}"
