package cn.mw.loganalysis.stats.service.query;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * ClickHouse 数据源操作策略
 */
@Slf4j
@Component
public class ClickHouseOperationStrategy implements DatasourceOperationStrategy {

    // 连接池缓存：按 endpoint + database 缓存，支持多个表共用连接池
    private final Map<String, HikariDataSource> dataSourceCache = new ConcurrentHashMap<>();

    @Override
    public String getSupportedType() {
        return "clickhouse";
    }

    @Override
    public ConnectionTestResult testConnection(DatasourceConnectionConfig config) {
        long startTime = System.currentTimeMillis();
        try {
            JdbcTemplate jdbcTemplate = getJdbcTemplate(config);
            
            // 测试连接并获取版本
            String version = jdbcTemplate.queryForObject("SELECT version()", String.class);
            
            long responseTime = System.currentTimeMillis() - startTime;
            
            return ConnectionTestResult.builder()
                    .success(true)
                    .message("连接成功")
                    .version(version)
                    .responseTimeMs(responseTime)
                    .build();
        } catch (Exception e) {
            log.error("ClickHouse 连接测试失败: {}", e.getMessage());
            return ConnectionTestResult.builder()
                    .success(false)
                    .message("连接失败: " + e.getMessage())
                    .responseTimeMs(System.currentTimeMillis() - startTime)
                    .build();
        }
    }

    @Override
    public TableCheckResult checkTable(DatasourceConnectionConfig config) {
        try {
            JdbcTemplate jdbcTemplate = getJdbcTemplate(config);
            String tableName = config.getTable();
            String database = config.getDatabase() != null ? config.getDatabase() : "default";

            // 检查表是否存在
            String checkSql = "SELECT count(*) FROM system.tables WHERE database = ? AND name = ?";
            Long count = jdbcTemplate.queryForObject(checkSql, Long.class, database, tableName);

            if (count == null || count == 0) {
                return TableCheckResult.builder()
                        .exists(false)
                        .tableName(tableName)
                        .message("表不存在")
                        .build();
            }

            // 获取字段信息
            String columnsSql = "SELECT name, type FROM system.columns WHERE database = ? AND table = ?";
            List<FieldInfo> fields = jdbcTemplate.query(columnsSql, 
                new Object[]{database, tableName},
                (rs, rowNum) -> FieldInfo.builder()
                        .name(rs.getString("name"))
                        .type(rs.getString("type"))
                        .label(rs.getString("name"))
                        .build()
            );

            // 获取行数
            String countSql = "SELECT count(*) FROM " + database + "." + tableName;
            Long rowCount = jdbcTemplate.queryForObject(countSql, Long.class);

            return TableCheckResult.builder()
                    .exists(true)
                    .tableName(tableName)
                    .message("表存在")
                    .fields(fields)
                    .rowCount(rowCount)
                    .build();

        } catch (Exception e) {
            log.error("检查表失败: {}", e.getMessage());
            return TableCheckResult.builder()
                    .exists(false)
                    .message("检查失败: " + e.getMessage())
                    .build();
        }
    }

    @Override
    public String generateCreateTableSQL(DatasourceConnectionConfig config, TableSchema schema) {
        StringBuilder sql = new StringBuilder();
        String database = config.getDatabase() != null ? config.getDatabase() : "default";
        String tableName = schema.getTableName() != null ? schema.getTableName() : config.getTable();

        sql.append("CREATE TABLE IF NOT EXISTS ").append(database).append(".").append(tableName).append(" (\n");

        // 列定义
        List<String> columnDefs = schema.getColumns().stream()
                .map(col -> {
                    StringBuilder colDef = new StringBuilder();
                    colDef.append("    ").append(col.getName()).append(" ");
                    
                    // 处理 Nullable
                    if (Boolean.TRUE.equals(col.getNullable())) {
                        colDef.append("Nullable(").append(col.getType()).append(")");
                    } else {
                        colDef.append(col.getType());
                    }
                    
                    // 默认值
                    if (StringUtils.hasText(col.getDefaultValue())) {
                        colDef.append(" DEFAULT ").append(col.getDefaultValue());
                    }
                    
                    // 注释
                    if (StringUtils.hasText(col.getComment())) {
                        colDef.append(" COMMENT '").append(col.getComment().replace("'", "''")).append("'");
                    }
                    
                    return colDef.toString();
                })
                .collect(Collectors.toList());

        sql.append(String.join(",\n", columnDefs));
        sql.append("\n)");

        // 引擎
        String engine = schema.getEngine() != null ? schema.getEngine() : "MergeTree()";
        sql.append("\nENGINE = ").append(engine);

        // 分区
        if (StringUtils.hasText(schema.getPartitionBy())) {
            sql.append("\nPARTITION BY ").append(schema.getPartitionBy());
        }

        // 排序键
        if (StringUtils.hasText(schema.getOrderBy())) {
            sql.append("\nORDER BY ").append(schema.getOrderBy());
        } else {
            sql.append("\nORDER BY tuple()");
        }

        // TTL
        if (StringUtils.hasText(schema.getTtl())) {
            sql.append("\nTTL ").append(schema.getTtl());
        }

        return sql.toString();
    }

    @Override
    public CreateTableResult createTable(DatasourceConnectionConfig config, TableSchema schema) {
        try {
            String sql = generateCreateTableSQL(config, schema);
            JdbcTemplate jdbcTemplate = getJdbcTemplate(config);
            
            jdbcTemplate.execute(sql);
            
            return CreateTableResult.builder()
                    .success(true)
                    .message("建表成功")
                    .executedSQL(sql)
                    .build();
        } catch (Exception e) {
            log.error("建表失败: {}", e.getMessage());
            return CreateTableResult.builder()
                    .success(false)
                    .message("建表失败: " + e.getMessage())
                    .build();
        }
    }

    @Override
    public TableSchema getRecommendedSchema(DatasourceConnectionConfig config) {
        // 推荐的日志表结构
        List<TableSchema.ColumnDefinition> columns = Arrays.asList(
            TableSchema.ColumnDefinition.builder()
                    .name("id").type("String").defaultValue("generateUUIDv4()").comment("唯一标识").build(),
            TableSchema.ColumnDefinition.builder()
                    .name("timestamp").type("DateTime").defaultValue("now()").comment("日志时间").build(),
            TableSchema.ColumnDefinition.builder()
                    .name("severity").type("LowCardinality(String)").comment("日志级别").build(),
            TableSchema.ColumnDefinition.builder()
                    .name("hostname").type("LowCardinality(String)").comment("主机名").build(),
            TableSchema.ColumnDefinition.builder()
                    .name("appname").type("LowCardinality(String)").comment("应用名").build(),
            TableSchema.ColumnDefinition.builder()
                    .name("source_type").type("LowCardinality(String)").comment("来源类型").build(),
            TableSchema.ColumnDefinition.builder()
                    .name("facility").type("LowCardinality(String)").nullable(true).comment("设施").build(),
            TableSchema.ColumnDefinition.builder()
                    .name("procid").type("String").nullable(true).comment("进程ID").build(),
            TableSchema.ColumnDefinition.builder()
                    .name("source_ip").type("String").nullable(true).comment("来源IP").build(),
            TableSchema.ColumnDefinition.builder()
                    .name("message").type("String").comment("日志消息").build(),
            TableSchema.ColumnDefinition.builder()
                    .name("raw").type("String").nullable(true).comment("原始日志").build()
        );

        return TableSchema.builder()
                .tableName(config.getTable())
                .columns(columns)
                .engine("MergeTree()")
                .partitionBy("toYYYYMM(timestamp)")
                .orderBy("(timestamp, hostname)")
                .ttl("timestamp + INTERVAL 90 DAY")
                .build();
    }

    @Override
    public List<String> listTables(DatasourceConnectionConfig config) {
        try {
            JdbcTemplate jdbcTemplate = getJdbcTemplate(config);
            String database = config.getDatabase() != null ? config.getDatabase() : "default";
            
            String sql = "SELECT name FROM system.tables WHERE database = ? ORDER BY name";
            return jdbcTemplate.queryForList(sql, String.class, database);
        } catch (Exception e) {
            log.error("获取表列表失败: {}", e.getMessage());
            return new ArrayList<>();
        }
    }

    /**
     * 获取 JdbcTemplate（按 endpoint + database 缓存连接池）
     */
    public JdbcTemplate getJdbcTemplate(DatasourceConnectionConfig config) {
        // 缓存键：endpoint + database（不包含 table）
        String cacheKey = config.getEndpoint() + "_" + (config.getDatabase() != null ? config.getDatabase() : "default");

        HikariDataSource dataSource = dataSourceCache.computeIfAbsent(cacheKey, k -> {
            HikariConfig hikariConfig = new HikariConfig();
            
            String jdbcUrl = buildJdbcUrl(config);
            hikariConfig.setJdbcUrl(jdbcUrl);
            hikariConfig.setDriverClassName("com.clickhouse.jdbc.ClickHouseDriver");
            
            if (StringUtils.hasText(config.getUsername())) {
                hikariConfig.setUsername(config.getUsername());
            }
            if (StringUtils.hasText(config.getPassword())) {
                hikariConfig.setPassword(config.getPassword());
            }

            hikariConfig.setMaximumPoolSize(5);
            hikariConfig.setMinimumIdle(1);
            hikariConfig.setConnectionTimeout(30000);
            hikariConfig.setIdleTimeout(600000);
            hikariConfig.setMaxLifetime(1800000);
            hikariConfig.setPoolName("ClickHouse-" + cacheKey.hashCode());

            log.info("Creating ClickHouse connection pool: {}", jdbcUrl);
            return new HikariDataSource(hikariConfig);
        });

        return new JdbcTemplate(dataSource);
    }

    private String buildJdbcUrl(DatasourceConnectionConfig config) {
        String endpoint = config.getEndpoint();
        String database = config.getDatabase();

        if (endpoint.startsWith("jdbc:")) {
            return endpoint;
        }

        StringBuilder url = new StringBuilder("jdbc:clickhouse://");
        url.append(endpoint);
        if (StringUtils.hasText(database)) {
            url.append("/").append(database);
        }

        return url.toString();
    }

    /**
     * 关闭所有连接池
     */
    public void closeAllConnections() {
        dataSourceCache.values().forEach(HikariDataSource::close);
        dataSourceCache.clear();
    }
}
