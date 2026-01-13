-- 系统配置表
CREATE TABLE IF NOT EXISTS system_config (
    id VARCHAR(32) PRIMARY KEY,
    config_key VARCHAR(255) NOT NULL,
    config_value TEXT NOT NULL,
    config_type VARCHAR(50) NOT NULL,
    description TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(config_key, config_type)
);

COMMENT ON TABLE system_config IS '系统配置表';
COMMENT ON COLUMN system_config.id IS '主键ID';
COMMENT ON COLUMN system_config.config_key IS '配置键';
COMMENT ON COLUMN system_config.config_value IS '配置值';
COMMENT ON COLUMN system_config.config_type IS '配置类型（clickhouse/postgresql/elasticsearch）';
COMMENT ON COLUMN system_config.description IS '配置描述';
COMMENT ON COLUMN system_config.created_at IS '创建时间';
COMMENT ON COLUMN system_config.updated_at IS '更新时间';

-- 插入 ClickHouse 默认配置
INSERT INTO system_config (id, config_key, config_value, config_type, description) VALUES
('ch_engine', 'ddl.engine', 'MergeTree', 'clickhouse', '表引擎'),
('ch_partition', 'ddl.partition_by', 'toYYYYMM(timestamp)', 'clickhouse', '分区策略'),
('ch_order', 'ddl.order_by', 'timestamp,hostname', 'clickhouse', '排序键'),
('ch_ttl', 'ddl.ttl_days', '30', 'clickhouse', '数据保留期（天）'),
('ch_compression', 'ddl.compression', 'LZ4', 'clickhouse', '压缩编码'),
('ch_keep_raw', 'ddl.keep_raw', 'true', 'clickhouse', '是否保留原始日志'),
('ch_indexes', 'ddl.indexes', 'timestamp:minmax,hostname:set', 'clickhouse', '索引配置');

-- 插入 PostgreSQL 默认配置
INSERT INTO system_config (id, config_key, config_value, config_type, description) VALUES
('pg_pk_type', 'ddl.primary_key_type', 'UUID', 'postgresql', '主键类型'),
('pg_indexes', 'ddl.indexes', 'timestamp:btree,hostname:btree', 'postgresql', '索引配置');

-- 插入 Elasticsearch 默认配置
INSERT INTO system_config (id, config_key, config_value, config_type, description) VALUES
('es_shards', 'ddl.number_of_shards', '3', 'elasticsearch', '分片数'),
('es_replicas', 'ddl.number_of_replicas', '1', 'elasticsearch', '副本数'),
('es_analyzer', 'ddl.analyzer', 'standard', 'elasticsearch', '分词器'),
('es_ilm_days', 'ddl.ilm_retention_days', '30', 'elasticsearch', 'ILM保留期（天）');
