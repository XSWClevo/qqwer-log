#!/bin/bash

# 测试服务健康检查

echo "测试服务健康检查..."
echo ""

# 等待服务启动
sleep 2

# 测试健康检查接口
echo "1. 测试根路径 (/)..."
curl -s http://localhost:8001/ | python3 -m json.tool
echo ""
echo ""

echo "2. 测试健康检查 (/health)..."
curl -s http://localhost:8001/health | python3 -m json.tool
echo ""
echo ""

echo "3. 查看 API 文档..."
echo "请访问: http://localhost:8001/docs"
echo ""
