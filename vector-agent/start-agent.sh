#!/bin/bash

# Vector Agent 启动脚本

AGENT_DIR="/opt/vector-agent"
AGENT_BIN="$AGENT_DIR/bin/vector-agent"
LOG_FILE="$AGENT_DIR/logs/agent.log"
PID_FILE="$AGENT_DIR/agent.pid"

# 检查是否已经在运行
if [ -f "$PID_FILE" ]; then
    OLD_PID=$(cat "$PID_FILE")
    if ps -p "$OLD_PID" > /dev/null 2>&1; then
        echo "Vector Agent 已经在运行 (PID: $OLD_PID)"
        exit 1
    else
        echo "清理旧的 PID 文件..."
        rm -f "$PID_FILE"
    fi
fi

# 确保日志目录存在
mkdir -p "$AGENT_DIR/logs"

# 启动 Agent
echo "启动 Vector Agent..."
cd "$AGENT_DIR"

# 方式1：使用 nohup（推荐）
nohup "$AGENT_BIN" >> "$LOG_FILE" 2>&1 &
AGENT_PID=$!

# 保存 PID
echo "$AGENT_PID" > "$PID_FILE"

# 等待启动
sleep 2

# 检查是否启动成功
if ps -p "$AGENT_PID" > /dev/null 2>&1; then
    echo "✓ Vector Agent 启动成功 (PID: $AGENT_PID)"
    echo "  日志文件: $LOG_FILE"
    echo "  查看日志: tail -f $LOG_FILE"
else
    echo "✗ Vector Agent 启动失败"
    echo "  查看错误: tail -20 $LOG_FILE"
    rm -f "$PID_FILE"
    exit 1
fi
