#!/bin/bash

# Vector Agent 停止脚本

AGENT_DIR="/opt/vector-agent"
PID_FILE="$AGENT_DIR/agent.pid"

if [ ! -f "$PID_FILE" ]; then
    echo "PID 文件不存在，尝试查找进程..."
    PIDS=$(ps aux | grep vector-agent | grep -v grep | awk '{print $2}')
    if [ -z "$PIDS" ]; then
        echo "没有找到运行中的 Vector Agent 进程"
        exit 0
    fi
    echo "找到进程: $PIDS"
    for PID in $PIDS; do
        echo "停止进程 $PID..."
        kill "$PID"
    done
    sleep 2
    echo "✓ Vector Agent 已停止"
    exit 0
fi

PID=$(cat "$PID_FILE")

if ! ps -p "$PID" > /dev/null 2>&1; then
    echo "进程 $PID 不存在，清理 PID 文件..."
    rm -f "$PID_FILE"
    exit 0
fi

echo "停止 Vector Agent (PID: $PID)..."
kill "$PID"

# 等待进程退出
for i in {1..10}; do
    if ! ps -p "$PID" > /dev/null 2>&1; then
        echo "✓ Vector Agent 已停止"
        rm -f "$PID_FILE"
        exit 0
    fi
    sleep 1
done

# 强制停止
echo "进程未响应，强制停止..."
kill -9 "$PID"
rm -f "$PID_FILE"
echo "✓ Vector Agent 已强制停止"
