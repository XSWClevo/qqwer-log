#!/usr/bin/env python3
import argparse
import socket
import time
from datetime import datetime, timezone
from itertools import cycle


def build_message(template: str, index: int, fake_ip: str) -> str:
    now = datetime.now(timezone.utc).isoformat(timespec="milliseconds").replace("+00:00", "Z")
    return (
        template.replace("{i}", str(index))
        .replace("{ts}", now)
        .replace("{ip}", fake_ip)
    )


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--host", default="127.0.0.1")
    parser.add_argument("--port", type=int, default=514)
    parser.add_argument("--count", type=int, default=0, help="0 means send forever")
    parser.add_argument("--rate", type=float, default=2.0, help="messages per second")
    parser.add_argument(
        "--ips",
        default="10.1.1.1,10.1.1.2,10.1.1.3,10.1.1.4,10.1.1.5",
        help="comma-separated fake source IPs used in message body",
    )
    parser.add_argument(
        "--bind",
        action="store_true",
        help="bind socket to each IP in the list (requires IPs configured on host)",
    )
    parser.add_argument(
        "--message",
        default="<13>1 {ts} vector-user.biz su 2666 ID389 - Something went wrong src_ip={ip} seq={i}.",
        help="message template, supports {i}, {ts}, {ip}",
    )
    args = parser.parse_args()

    ip_list = [ip.strip() for ip in args.ips.split(",") if ip.strip()]
    if not ip_list:
        raise SystemExit("ips list is empty")
    ip_iter = cycle(ip_list)
    interval = 1.0 / args.rate if args.rate > 0 else 0.5
    total = args.count

    addr = (args.host, args.port)
    sockets = []
    if args.bind:
        for ip in ip_list:
            s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
            s.bind((ip, 0))
            sockets.append(s)
    else:
        s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
        sockets.append(s)

    sock_iter = cycle(sockets)
    sent = 0
    while True:
        sent += 1
        fake_ip = next(ip_iter)
        payload = build_message(args.message, sent, fake_ip).encode("utf-8")
        next(sock_iter).sendto(payload, addr)
        if total > 0 and sent >= total:
            break
        if interval > 0:
            time.sleep(interval)

    for s in sockets:
        s.close()


if __name__ == "__main__":
    main()
