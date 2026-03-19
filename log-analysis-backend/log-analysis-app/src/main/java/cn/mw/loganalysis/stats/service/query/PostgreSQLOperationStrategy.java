package cn.mw.loganalysis.stats.service.query;

import cn.mw.loganalysis.stats.mapper.PostgreSqlQueryMapper;
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
 * PostgreSQL 数据源操作策略
 */
@Slf4j
@Component
public class PostgreSQLOperationStrategy implements DatasourceOperationStrategy {

    private final Map<String, HikariDataSource> dataSourceCache = new ConcurrentHashMap<>();
    private final Map<String, SqlSessionFactory> sqlSessionFactoryCache = new ConcurrentHashMap<>();

    @Override
    public String getSupportedType() {
        return "postgresql";
    }

    @Override
    public ConnectionTestResult testConnection(DatasourceConnectionConfig config) {
        long startTime = System.currentTimeMillis();
        try {
            String version = DynamicMyBatisUtils.execute(
                    getSqlSessionFactory(config),
                    PostgreSqlQueryMapper.class,
                    PostgreSqlQueryMapper::selectVersion
            );
            
            long responseTime = System.currentTimeMillis() - startTime;
            
            return ConnectionTestResult.builder()
                    .success(true)
                    .message("连接成功")
                    .version(version)
                    .responseTimeMs(responseTime)
                    .build();
        } catch (Exception e) {
            log.error("PostgreSQL 连接测试失败: {}", e.getMessage());
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
            return DynamicMyBatisUtils.execute(getSqlSessionFactory(config), PostgreSqlQueryMapper.class, mapper -> {
                Long count = mapper.countTables(tableName);

                if (count == null || count == 0) {
                    return TableCheckResult.builder()
                            .exists(false)
                            .tableName(tableName)
                            .message("表不存在")
                            .build();
                }

                List<FieldInfo> fields = mapper.selectTableSchemaRows(tableName).stream()
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

                Long rowCount = mapper.countTableRows(StatsQueryMapperUtils.quotePostgreSqlIdentifier(tableName));

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
        String tableName = schema.getTableName() != null ? schema.getTableName() : config.getTable();

        sql.append("CREATE TABLE IF NOT EXISTS ").append(tableName).append(" (\n");

        // 列定义
        List<String> columnDefs = schema.getColumns().stream()
                .map(col -> {
                    StringBuilder colDef = new StringBuilder();
                    colDef.append("    ").append(col.getName()).append(" ").append(col.getType());
                    
                    if (!Boolean.TRUE.equals(col.getNullable())) {
                        colDef.append(" NOT NULL");
                    }
                    
                    if (StringUtils.hasText(col.getDefaultValue())) {
                        colDef.append(" DEFAULT ").append(col.getDefaultValue());
                    }
                    
                    return colDef.toString();
                })
                .collect(Collectors.toList());

        // 主键
        if (StringUtils.hasText(schema.getPrimaryKey())) {
            columnDefs.add("    PRIMARY KEY (" + schema.getPrimaryKey() + ")");
        }

        sql.append(String.join(",\n", columnDefs));
        sql.append("\n)");

        return sql.toString();
    }

    @Override
    public CreateTableResult createTable(DatasourceConnectionConfig config, TableSchema schema) {
        try {
            String sql = generateCreateTableSQL(config, schema);
            try (Connection connection = getDataSource(config).getConnection();
                 Statement statement = connection.createStatement()) {
                statement.execute(sql);

                String tableName = schema.getTableName() != null ? schema.getTableName() : config.getTable();
                String indexSql = "CREATE INDEX IF NOT EXISTS idx_" + tableName + "_timestamp ON " + tableName + " (timestamp)";
                statement.execute(indexSql);

                return CreateTableResult.builder()
                        .success(true)
                        .message("建表成功")
                        .executedSQL(sql + ";\n" + indexSql)
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
        List<TableSchema.ColumnDefinition> columns = Arrays.asList(
            TableSchema.ColumnDefinition.builder()
                    .name("id").type("UUID").defaultValue("gen_random_uuid()").comment("唯一标识").build(),
            TableSchema.ColumnDefinition.builder()
                    .name("timestamp").type("TIMESTAMP WITH TIME ZONE").defaultValue("NOW()").comment("日志时间").build(),
            TableSchema.ColumnDefinition.builder()
                    .name("severity").type("VARCHAR(20)").comment("日志级别").build(),
            TableSchema.ColumnDefinition.builder()
                    .name("hostname").type("VARCHAR(255)").comment("主机名").build(),
            TableSchema.ColumnDefinition.builder()
                    .name("appname").type("VARCHAR(255)").comment("应用名").build(),
            TableSchema.ColumnDefinition.builder()
                    .name("source_type").type("VARCHAR(100)").comment("来源类型").build(),
            TableSchema.ColumnDefinition.builder()
                    .name("facility").type("VARCHAR(50)").nullable(true).comment("设施").build(),
            TableSchema.ColumnDefinition.builder()
                    .name("procid").type("VARCHAR(50)").nullable(true).comment("进程ID").build(),
            TableSchema.ColumnDefinition.builder()
                    .name("source_ip").type("VARCHAR(45)").nullable(true).comment("来源IP").build(),
            TableSchema.ColumnDefinition.builder()
                    .name("message").type("TEXT").comment("日志消息").build(),
            TableSchema.ColumnDefinition.builder()
                    .name("raw").type("TEXT").nullable(true).comment("原始日志").build()
        );

        return TableSchema.builder()
                .tableName(config.getTable())
                .columns(columns)
                .primaryKey("id")
                .build();
    }

    @Override
    public List<String> listTables(DatasourceConnectionConfig config) {
        try {
            return DynamicMyBatisUtils.execute(
                    getSqlSessionFactory(config),
                    PostgreSqlQueryMapper.class,
                    PostgreSqlQueryMapper::selectTables
            );
        } catch (Exception e) {
            log.error("获取表列表失败: {}", e.getMessage());
            return new ArrayList<>();
        }
    }

    public SqlSessionFactory getSqlSessionFactory(DatasourceConnectionConfig config) {
        String cacheKey = getCacheKey(config);
        return sqlSessionFactoryCache.computeIfAbsent(cacheKey,
                key -> DynamicMyBatisUtils.buildSqlSessionFactory(getDataSource(config), PostgreSqlQueryMapper.class));
    }

    private String buildJdbcUrl(DatasourceConnectionConfig config) {
        String endpoint = config.getEndpoint();
        String database = config.getDatabase();

        if (endpoint.startsWith("jdbc:")) {
            return endpoint;
        }

        StringBuilder url = new StringBuilder("jdbc:postgresql://");
        url.append(endpoint);
        if (StringUtils.hasText(database)) {
            url.append("/").append(database);
        }

        return url.toString();
    }

    public void closeAllConnections() {
        dataSourceCache.values().forEach(HikariDataSource::close);
        dataSourceCache.clear();
        sqlSessionFactoryCache.clear();
    }

    private String getCacheKey(DatasourceConnectionConfig config) {
        return config.getEndpoint() + "_" + (config.getDatabase() != null ? config.getDatabase() : "postgres");
    }

    private HikariDataSource getDataSource(DatasourceConnectionConfig config) {
        String cacheKey = getCacheKey(config);
        return dataSourceCache.computeIfAbsent(cacheKey, k -> {
            HikariConfig hikariConfig = new HikariConfig();

            String jdbcUrl = buildJdbcUrl(config);
            hikariConfig.setJdbcUrl(jdbcUrl);
            hikariConfig.setDriverClassName("org.postgresql.Driver");

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
            hikariConfig.setPoolName("PostgreSQL-" + cacheKey.hashCode());

            log.info("Creating PostgreSQL connection pool: {}", jdbcUrl);
            return new HikariDataSource(hikariConfig);
        });
    }
}
