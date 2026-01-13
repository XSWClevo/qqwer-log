#!/bin/bash

# Vector Agent 权限修复脚本

echo "修复 Vector Agent 权限..."

# 修复日志文件权限
sudo chown -R $USER:wheel /opt/vector-agent/logs/
sudo chmod -R 755 /opt/vector-agent/logs/

# 修复 PID 文件权限
sudo touch /opt/vector-agent/vector.pid
sudo chown $USER:wheel /opt/vector-agent/vector.pid
sudo chmod 644 /opt/vector-agent/vector.pid

# 修复数据目录权限
sudo chown -R $USER:wheel /opt/vector-agent/data/
sudo chmod -R 755 /opt/vector-agent/data/

# 修复配置目录权限
sudo chown -R $USER:wheel /opt/vector-agent/config/
sudo chmod -R 755 /opt/vector-agent/config/

echo "权限修复完成！"
echo ""
echo "现在可以运行："
echo "  cd /opt/vector-agent"
echo "  ./bin/vector-agent &"
