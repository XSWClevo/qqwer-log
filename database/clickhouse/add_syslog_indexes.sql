-- 为 syslog 表添加跳数索引以优化统计查询性能
-- 执行前请确保连接到正确的数据库：MWLOGDB_ANALYSIS

-- 1. severity 字段索引（set 类型，适合低基数字段）
-- 用于加速 GROUP BY severity 查询
ALTER TABLE MWLOGDB_ANALYSIS.syslog
ADD INDEX IF NOT EXISTS idx_severity severity TYPE set(0) GRANULARITY 4;

-- 2. hostname 字段索引（bloom_filter 类型，适合中等基数字段）
-- 用于加速 GROUP BY hostname 查询
ALTER TABLE MWLOGDB_ANALYSIS.syslog
ADD INDEX IF NOT EXISTS idx_hostname hostname TYPE bloom_filter(0.01) GRANULARITY 4;

-- 3. appname 字段索引（bloom_filter 类型）
-- 用于加速 GROUP BY appname 查询
ALTER TABLE MWLOGDB_ANALYSIS.syslog
ADD INDEX IF NOT EXISTS idx_appname appname TYPE bloom_filter(0.01) GRANULARITY 4;

-- 4. source_type 字段索引（set 类型）
-- 用于加速 GROUP BY source_type 查询
ALTER TABLE MWLOGDB_ANALYSIS.syslog
ADD INDEX IF NOT EXISTS idx_source_type source_type TYPE set(0) GRANULARITY 4;

-- 5. facility 字段索引（set 类型）
-- 用于加速 GROUP BY facility 查询
ALTER TABLE MWLOGDB_ANALYSIS.syslog
ADD INDEX IF NOT EXISTS idx_facility facility TYPE set(0) GRANULARITY 4;

-- 6. source_ip 字段索引（bloom_filter 类型）
-- 用于加速 GROUP BY source_ip 查询
ALTER TABLE MWLOGDB_ANALYSIS.syslog
ADD INDEX IF NOT EXISTS idx_source_ip source_ip TYPE bloom_filter(0.01) GRANULARITY 4;

-- 查看索引创建情况
SELECT
    table,
    name,
    type,
    expr
FROM system.data_skipping_indices
WHERE database = 'MWLOGDB_ANALYSIS' AND table = 'syslog';

-- 注意事项：
-- 1. 索引只对新写入的数据生效
-- 2. 如果需要对已有数据生效，需要执行：
--    ALTER TABLE MWLOGDB_ANALYSIS.syslog MATERIALIZE INDEX idx_severity;
--    ALTER TABLE MWLOGDB_ANALYSIS.syslog MATERIALIZE INDEX idx_hostname;
--    ALTER TABLE MWLOGDB_ANALYSIS.syslog MATERIALIZE INDEX idx_appname;
--    ALTER TABLE MWLOGDB_ANALYSIS.syslog MATERIALIZE INDEX idx_source_type;
--    ALTER TABLE MWLOGDB_ANALYSIS.syslog MATERIALIZE INDEX idx_facility;
--    ALTER TABLE MWLOGDB_ANALYSIS.syslog MATERIALIZE INDEX idx_source_ip;
-- 3. MATERIALIZE 操作会重写数据，可能需要较长时间
