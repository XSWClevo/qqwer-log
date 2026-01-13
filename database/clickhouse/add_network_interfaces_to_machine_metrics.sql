-- 为 machine_metrics 表添加网卡信息字段
-- 使用 JSON 类型存储网卡信息数组

USE MWLOGDB_ANALYSIS;

-- 添加网卡信息字段（JSON 格式）
-- ClickHouse 支持 String 类型存储 JSON，查询时使用 JSON 函数解析
ALTER TABLE machine_metrics
ADD COLUMN IF NOT EXISTS network_interfaces String DEFAULT '[]' COMMENT '网卡信息(JSON数组)';

-- 说明：
-- network_interfaces 字段存储格式示例：
-- [
--   {
--     "name": "eth0",
--     "bytesSent": 1234567890,
--     "bytesRecv": 9876543210,
--     "packetsSent": 123456,
--     "packetsRecv": 654321,
--     "errin": 0,
--     "errout": 0
--   },
--   {
--     "name": "lo",
--     "bytesSent": 1000,
--     "bytesRecv": 1000,
--     "packetsSent": 10,
--     "packetsRecv": 10,
--     "errin": 0,
--     "errout": 0
--   }
-- ]
