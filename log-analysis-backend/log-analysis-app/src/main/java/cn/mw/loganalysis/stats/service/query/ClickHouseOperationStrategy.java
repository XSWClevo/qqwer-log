package cn.mw.loganalysis.stats.service.query;

import cn.mw.loganalysis.stats.mapper.ClickHouseQueryMapper;
import cn.mw.loganalysis.stats.service.query.support.DynamicMyBatisUtils;
import cn.mw.loganalysis.stats.service.query.support.StatsQueryMapperUtils;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.session.SqlSessionFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.sql.Connection;
import java.sql.Statement;
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
    private final Map<String, SqlSessionFactory> sqlSessionFactoryCache = new ConcurrentHashMap<>();

    @Override
    public String getSupportedType() {
        return "clickhouse";
    }

    @Override
    public ConnectionTestResult testConnection(DatasourceConnectionConfig config) {
        long startTime = System.currentTimeMillis();
        try {
            String version = DynamicMyBatisUtils.execute(
                    getSqlSessionFactory(config),
                    ClickHouseQueryMapper.class,
                    ClickHouseQueryMapper::selectVersion
            );
            
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
            String tableName = config.getTable();
            String database = config.getDatabase() != null ? config.getDatabase() : "default";

            return DynamicMyBatisUtils.execute(getSqlSessionFactory(config), ClickHouseQueryMapper.class, mapper -> {
                Long count = mapper.countTables(database, tableName);

                if (count == null || count == 0) {
                    return TableCheckResult.builder()
                            .exists(false)
                            .tableName(tableName)
                            .message("表不存在")
                            .build();
                }

                List<FieldInfo> fields = mapper.selectTableSchemaRows(database, tableName).stream()
                        .map(row -> {
                            String name = String.valueOf(row.get("name"));
                            String type = String.valueOf(row.get("type"));
                            return FieldInfo.builder()
                                    .name(name)
                                    .type(type)
                                    .label(name)
                                    .build();
                        })
                        .toList();

                Long rowCount = mapper.countTableRows(StatsQueryMapperUtils.qualifyClickHouseTable(database, tableName));

                return TableCheckResult.builder()
                        .exists(true)
                        .tableName(tableName)
                        .message("表存在")
                        .fields(fields)
                        .rowCount(rowCount)
                        .build();
            });

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
            try (Connection connection = getDataSource(config).getConnection();
                 Statement statement = connection.createStatement()) {
                statement.execute(sql);

                return CreateTableResult.builder()
                        .success(true)
                        .message("建表成功")
                        .executedSQL(sql)
                        .build();
            }
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
            String database = config.getDatabase() != null ? config.getDatabase() : "default";
            return DynamicMyBatisUtils.execute(
                    getSqlSessionFactory(config),
                    ClickHouseQueryMapper.class,
                    mapper -> mapper.selectTables(database)
            );
        } catch (Exception e) {
            log.error("获取表列表失败: {}", e.getMessage());
            return new ArrayList<>();
        }
    }

    public SqlSessionFactory getSqlSessionFactory(DatasourceConnectionConfig config) {
        String cacheKey = getCacheKey(config);
        return sqlSessionFactoryCache.computeIfAbsent(cacheKey,
                key -> DynamicMyBatisUtils.buildSqlSessionFactory(getDataSource(config), ClickHouseQueryMapper.class));
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
        sqlSessionFactoryCache.clear();
    }

    private String getCacheKey(DatasourceConnectionConfig config) {
        return config.getEndpoint() + "_" + (config.getDatabase() != null ? config.getDatabase() : "default");
    }

    private HikariDataSource getDataSource(DatasourceConnectionConfig config) {
        String cacheKey = getCacheKey(config);
        return dataSourceCache.computeIfAbsent(cacheKey, k -> {
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
    }
}
