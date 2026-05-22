#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

IPS="${IPS:-192.168.31.164,192.168.139.3,192.168.107.0}" \
COUNT="${COUNT:-0}" \
RATE="${RATE:-1}" \
CLEANUP_CONFIG="${CLEANUP_CONFIG:-0}" \
"${SCRIPT_DIR}/start_send_udp_10514.sh"
