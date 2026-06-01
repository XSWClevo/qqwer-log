package cn.mw.loganalysis.stats.service;

import cn.mw.loganalysis.stats.dto.LogContextRequest;
import cn.mw.loganalysis.stats.dto.LogQueryRequest;
import cn.mw.loganalysis.stats.dto.StatsQueryRequest;
import cn.mw.loganalysis.stats.service.query.DatasourceConnectionConfig;
import cn.mw.loganalysis.stats.service.query.ClickHouseQueryStrategy;
import cn.mw.loganalysis.stats.service.query.FieldInfo;
import cn.mw.loganalysis.stats.service.query.LogQueryStrategy;
import cn.mw.loganalysis.vector.entity.ConfigComponent;
import cn.mw.loganalysis.vector.service.ConfigComponentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;
import org.yaml.snakeyaml.Yaml;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 动态日志查询服务
 * 根据数据源类型路由到对应的查询策略
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DynamicLogQueryService {

    private static final Pattern SHELL_STYLE_PLACEHOLDER =
            Pattern.compile("\\$\\{([A-Za-z0-9_.-]+)(?::-([^}]*))?}");

    private final ConfigComponentService configComponentService;
    private final List<LogQueryStrategy> queryStrategies;
    private final Environment environment;

    /**
     * 获取数据源的表结构（字段列表）
     */
    public List<FieldInfo> getTableSchema(String datasourceId) {
        DatasourceConnectionConfig config = getDatasourceConfig(datasourceId);
        LogQueryStrategy strategy = getStrategy(config.getType());
        return strategy.getTableSchema(config);
    }

    /**
     * 查询日志
     */
    public Map<String, Object> queryLogs(String datasourceId, LogQueryRequest request) {
        DatasourceConnectionConfig config = getDatasourceConfig(datasourceId);
        LogQueryStrategy strategy = getStrategy(config.getType());
        return strategy.queryLogs(request, config);
    }

    /**
     * 查询日志上下文
     */
    public Map<String, Object> queryLogContext(String datasourceId, LogContextRequest request) {
        DatasourceConnectionConfig config = getDatasourceConfig(datasourceId);
        LogQueryStrategy strategy = getStrategy(config.getType());
        return strategy.queryLogContext(request, config);
    }

    /**
     * 统计查询（带缓存）
     * 缓存 key: datasourceId_dimensions_startTime_endTime
     * 缓存时间: 5 分钟
     */
    @Cacheable(
        value = "statsCache",
        key = "#datasourceId + '_' + #request.dimensions + '_' + #request.startTime + '_' + #request.endTime"
    )
    public Map<String, Object> queryStats(String datasourceId, StatsQueryRequest request) {
        log.info("查询统计数据（缓存未命中）: datasourceId={}, dimensions={}", datasourceId, request.getDimensions());
        DatasourceConnectionConfig config = getDatasourceConfig(datasourceId);
        LogQueryStrategy strategy = getStrategy(config.getType());
        return strategy.queryStats(request, config);
    }

    /**
     * 时间序列查询
     */
    public Map<String, Object> queryTimeSeries(String datasourceId, StatsQueryRequest request) {
        DatasourceConnectionConfig config = getDatasourceConfig(datasourceId);
        LogQueryStrategy strategy = getStrategy(config.getType());
        return strategy.queryTimeSeries(request, config);
    }

    /**
     * 公开的获取数据源配置方法（供 DatasourceManagementService 使用）
     */
    public DatasourceConnectionConfig getDatasourceConfigPublic(String datasourceId) {
        return getDatasourceConfig(datasourceId);
    }

    /**
     * 获取 queryable 数据源的连接配置。
     * 供 Dashboard 等只读场景复用，避免重复解析 YAML。
     */
    public DatasourceConnectionConfig getQueryableDatasourceConfig(String datasourceId) {
        return getDatasourceConfig(datasourceId);
    }

    /**
     * 获取数据源配置
     */
    private DatasourceConnectionConfig getDatasourceConfig(String datasourceId) {
        if (StringUtils.isBlank(datasourceId)) {
            throw new IllegalArgumentException("数据源ID不能为空");
        }

        ConfigComponent component = configComponentService.getById(datasourceId);
        if (component == null) {
            throw new IllegalArgumentException("数据源不存在: " + datasourceId);
        }

        if (!"sink".equalsIgnoreCase(component.getComponentType())) {
            throw new IllegalArgumentException("组件不是 Sink 类型: " + datasourceId);
        }

        if (!Boolean.TRUE.equals(component.getQueryable())) {
            throw new IllegalArgumentException("数据源未启用查询功能: " + datasourceId);
        }

        return parseYamlConfig(component);
    }

    /**
     * 解析 YAML 配置
     */
    private DatasourceConnectionConfig parseYamlConfig(ConfigComponent component) {
        String configYaml = component.getConfigYaml();
        String vectorType = component.getVectorType();

        DatasourceConnectionConfig.DatasourceConnectionConfigBuilder builder = DatasourceConnectionConfig.builder()
                .componentId(component.getId())
                .componentName(component.getName())
                .type(vectorType)
                .rawYaml(configYaml);

        if (StringUtils.isBlank(configYaml)) {
            log.warn("组件 {} 的 configYaml 为空", component.getId());
            return builder.build();
        }

        try {
            Yaml yaml = new Yaml();
            Map<String, Object> config = yaml.load(configYaml);

            if (config == null) {
                return builder.build();
            }

            // 根据不同的 Vector Sink 类型解析配置
            switch (vectorType.toLowerCase()) {
                case "clickhouse":
                    parseClickHouseConfig(builder, config);
                    break;
                case "elasticsearch":
                    parseElasticsearchConfig(builder, config);
                    break;
                case "postgresql":
                    parsePostgreSQLConfig(builder, config);
                    break;
                case "mysql":
                    parseMySQLConfig(builder, config);
                    break;
                case "kafka":
                    parseKafkaConfig(builder, config);
                    break;
                case "loki":
                    parseLokiConfig(builder, config);
                    break;
                default:
                    log.warn("未知的数据源类型: {}", vectorType);
                    parseGenericConfig(builder, config);
            }

        } catch (Exception e) {
            log.error("解析 YAML 配置失败: {}", e.getMessage(), e);
        }

        return builder.build();
    }

    private void parseClickHouseConfig(DatasourceConnectionConfig.DatasourceConnectionConfigBuilder builder, 
                                        Map<String, Object> config) {
        // Vector ClickHouse Sink 配置格式
        // endpoint: "http://localhost:8123"
        // database: "default"
        // table: "logs"
        // auth.user: "default"
        // auth.password: ""
        
        builder.endpoint(getStringValue(config, "endpoint", "localhost:8123"));
        builder.database(getStringValue(config, "database", "default"));
        builder.table(getStringValue(config, "table", "logs"));
        
        // 解析认证信息
        String username = null;
        String password = null;
        Object auth = config.get("auth");
        if (auth instanceof Map) {
            Map<String, Object> authMap = (Map<String, Object>) auth;
            username = getStringValue(authMap, "user", null);
            password = getStringValue(authMap, "password", null);
        }
        builder.username(firstNonBlank(username, getConfiguredProperty(
                "spring.datasource.dynamic.datasource.clickhouse.username", "default")));
        builder.password(firstNonBlank(password, getConfiguredProperty(
                "spring.datasource.dynamic.datasource.clickhouse.password", "")));
        
        // 检查 TLS
        Object tls = config.get("tls");
        builder.tls(tls != null);
    }

    private void parseElasticsearchConfig(DatasourceConnectionConfig.DatasourceConnectionConfigBuilder builder,
                                           Map<String, Object> config) {
        // Vector Elasticsearch Sink 配置格式
        // endpoints: ["http://localhost:9200"]
        // index: "logs-%Y-%m-%d"
        // auth.user: "elastic"
        // auth.password: ""
        
        Object endpoints = config.get("endpoints");
        if (endpoints instanceof List && !((List<?>) endpoints).isEmpty()) {
            builder.endpoint(((List<?>) endpoints).get(0).toString());
        } else if (endpoints instanceof String) {
            builder.endpoint((String) endpoints);
        } else {
            builder.endpoint(getStringValue(config, "endpoint", "localhost:9200"));
        }
        
        // index 可能包含日期模板，需要处理
        String index = getStringValue(config, "index", "logs");
        // 简化处理：移除日期模板部分
        if (index.contains("%")) {
            index = index.replaceAll("-%[YmdHMS]+", "*");
        }
        builder.table(index);
        
        // 解析认证信息
        Object auth = config.get("auth");
        if (auth instanceof Map) {
            Map<String, Object> authMap = (Map<String, Object>) auth;
            builder.username(getStringValue(authMap, "user", null));
            builder.password(getStringValue(authMap, "password", null));
        }
        
        // 检查 TLS
        Object tls = config.get("tls");
        builder.tls(tls != null);
    }

    private void parsePostgreSQLConfig(DatasourceConnectionConfig.DatasourceConnectionConfigBuilder builder,
                                        Map<String, Object> config) {
        // Vector PostgreSQL Sink 配置格式
        // endpoint: "postgresql://localhost:5432/logs"
        // table: "logs"
        
        String endpoint = getStringValue(config, "endpoint", "localhost:5432");
        
        // 解析 PostgreSQL 连接字符串
        if (endpoint.startsWith("postgresql://") || endpoint.startsWith("postgres://")) {
            // 格式: postgresql://user:password@host:port/database
            try {
                String connStr = endpoint.replace("postgresql://", "").replace("postgres://", "");
                
                // 提取用户名密码
                if (connStr.contains("@")) {
                    String[] parts = connStr.split("@");
                    String authPart = parts[0];
                    String hostPart = parts[1];
                    
                    if (authPart.contains(":")) {
                        String[] authParts = authPart.split(":");
                        builder.username(authParts[0]);
                        builder.password(authParts[1]);
                    } else {
                        builder.username(authPart);
                    }
                    
                    // 解析 host:port/database
                    if (hostPart.contains("/")) {
                        String[] hostDbParts = hostPart.split("/");
                        builder.endpoint(hostDbParts[0]);
                        builder.database(hostDbParts[1]);
                    } else {
                        builder.endpoint(hostPart);
                    }
                } else {
                    // 没有认证信息
                    if (connStr.contains("/")) {
                        String[] parts = connStr.split("/");
                        builder.endpoint(parts[0]);
                        builder.database(parts[1]);
                    } else {
                        builder.endpoint(connStr);
                    }
                }
            } catch (Exception e) {
                log.warn("解析 PostgreSQL 连接字符串失败: {}", endpoint);
                builder.endpoint(endpoint);
            }
        } else {
            builder.endpoint(endpoint);
            builder.database(getStringValue(config, "database", "logs"));
        }
        
        builder.table(getStringValue(config, "table", "logs"));
    }

    private void parseMySQLConfig(DatasourceConnectionConfig.DatasourceConnectionConfigBuilder builder,
                                   Map<String, Object> config) {
        // 类似 PostgreSQL 的处理
        builder.endpoint(getStringValue(config, "endpoint", "localhost:3306"));
        builder.database(getStringValue(config, "database", "logs"));
        builder.table(getStringValue(config, "table", "logs"));
        
        Object auth = config.get("auth");
        if (auth instanceof Map) {
            Map<String, Object> authMap = (Map<String, Object>) auth;
            builder.username(getStringValue(authMap, "user", null));
            builder.password(getStringValue(authMap, "password", null));
        }
    }

    private void parseKafkaConfig(DatasourceConnectionConfig.DatasourceConnectionConfigBuilder builder,
                                   Map<String, Object> config) {
        // Vector Kafka Sink 配置格式
        // bootstrap_servers: "localhost:9092"
        // topic: "logs"
        
        builder.endpoint(getStringValue(config, "bootstrap_servers", "localhost:9092"));
        builder.table(getStringValue(config, "topic", "logs"));
    }

    private void parseLokiConfig(DatasourceConnectionConfig.DatasourceConnectionConfigBuilder builder,
                                  Map<String, Object> config) {
        // Vector Loki Sink 配置格式
        // endpoint: "http://localhost:3100"
        
        builder.endpoint(getStringValue(config, "endpoint", "localhost:3100"));
        
        Object auth = config.get("auth");
        if (auth instanceof Map) {
            Map<String, Object> authMap = (Map<String, Object>) auth;
            builder.username(getStringValue(authMap, "user", null));
            builder.password(getStringValue(authMap, "password", null));
        }
    }

    private void parseGenericConfig(DatasourceConnectionConfig.DatasourceConnectionConfigBuilder builder,
                                     Map<String, Object> config) {
        builder.endpoint(getStringValue(config, "endpoint", 
                         getStringValue(config, "host", "localhost")));
        builder.database(getStringValue(config, "database", null));
        builder.table(getStringValue(config, "table", 
                      getStringValue(config, "index", 
                      getStringValue(config, "topic", "logs"))));
    }

    private String getStringValue(Map<String, Object> map, String key, String defaultValue) {
        Object value = map.get(key);
        if (value == null) {
            return defaultValue;
        }
        String resolved = resolveTemplateValue(value.toString());
        return StringUtils.isNotBlank(resolved) ? resolved : defaultValue;
    }

    private String resolveTemplateValue(String rawValue) {
        if (StringUtils.isBlank(rawValue) || !rawValue.contains("${")) {
            return rawValue;
        }
        Matcher matcher = SHELL_STYLE_PLACEHOLDER.matcher(rawValue);
        StringBuffer resolved = new StringBuffer();
        while (matcher.find()) {
            String key = matcher.group(1);
            String defaultValue = matcher.group(2);
            String replacement = getConfiguredProperty(key, defaultValue);
            matcher.appendReplacement(resolved, Matcher.quoteReplacement(replacement != null ? replacement : ""));
        }
        matcher.appendTail(resolved);
        return resolved.toString();
    }

    private String getConfiguredProperty(String key, String defaultValue) {
        String configuredValue = environment.getProperty(key);
        return StringUtils.isNotBlank(configuredValue) ? configuredValue : defaultValue;
    }

    private String firstNonBlank(String primaryValue, String fallbackValue) {
        return StringUtils.isNotBlank(primaryValue) ? primaryValue : fallbackValue;
    }

    /**
     * 获取对应类型的查询策略
     */
    private LogQueryStrategy getStrategy(String type) {
        if (StringUtils.isBlank(type)) {
            throw new IllegalArgumentException("数据源类型不能为空");
        }

        return queryStrategies.stream()
                .filter(s -> s.getSupportedType().equalsIgnoreCase(type))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("不支持的数据源类型: " + type + 
                        "，当前支持: clickhouse, elasticsearch, postgresql"));
    }

    /**
     * 获取支持的数据源类型列表
     */
    public List<String> getSupportedTypes() {
        return queryStrategies.stream()
                .map(LogQueryStrategy::getSupportedType)
                .toList();
    }

    /**
     * 获取数据源的表名
     */
    public String getTableName(String datasourceId) {
        DatasourceConnectionConfig config = getDatasourceConfig(datasourceId);
        return config.getTable();
    }

    /**
     * 获取数据源类型
     */
    public String getDatasourceType(String datasourceId) {
        DatasourceConnectionConfig config = getDatasourceConfig(datasourceId);
        return config.getType();
    }

    /**
     * 执行原始SQL查询
     *
     * @param datasourceId 数据源ID（可为null，使用默认数据源）
     * @param sql SQL语句
     * @return 查询结果
     */
    public Object executeRawSQL(String datasourceId, String sql) {
        if (StringUtils.isNotBlank(datasourceId)) {
            DatasourceConnectionConfig config = getDatasourceConfig(datasourceId);
            LogQueryStrategy strategy = getStrategy(config.getType());
            return strategy.executeRawSQL(sql, config);
        } else {
            DatasourceConnectionConfig defaultConfig = buildDefaultClickHouseConfig();
            LogQueryStrategy strategy = getStrategy("clickhouse");
            return strategy.executeRawSQL(sql, defaultConfig);
        }
    }

    /**
     * 直接通过底层驱动执行原始 SQL，绕过 MCP。
     * 用于 Dashboard 首页等固定聚合场景，避免多条短查询反复拉起 MCP 进程。
     */
    public Object executeRawSQLJdbc(String datasourceId, String sql) {
        DatasourceConnectionConfig config = StringUtils.isNotBlank(datasourceId)
                ? getDatasourceConfig(datasourceId)
                : buildDefaultClickHouseConfig();
        LogQueryStrategy strategy = getStrategy(config.getType());
        if (strategy instanceof ClickHouseQueryStrategy clickHouseQueryStrategy) {
            return clickHouseQueryStrategy.executeRawSQLViaJdbc(sql, config);
        }
        return strategy.executeRawSQL(sql, config);
    }

    private DatasourceConnectionConfig buildDefaultClickHouseConfig() {
        String jdbcUrl = getConfiguredProperty(
                "spring.datasource.dynamic.datasource.clickhouse.url",
                "jdbc:clickhouse://localhost:8123/default"
        );
        DefaultClickHouseEndpoint endpoint = parseDefaultClickHouseEndpoint(jdbcUrl);
        return DatasourceConnectionConfig.builder()
                .type("clickhouse")
                .endpoint(endpoint.endpoint())
                .database(endpoint.database())
                .table("syslog")
                .username(getConfiguredProperty("spring.datasource.dynamic.datasource.clickhouse.username", "default"))
                .password(getConfiguredProperty("spring.datasource.dynamic.datasource.clickhouse.password", ""))
                .tls(endpoint.secure())
                .build();
    }

    private DefaultClickHouseEndpoint parseDefaultClickHouseEndpoint(String jdbcUrl) {
        String rawUrl = StringUtils.defaultIfBlank(jdbcUrl, "jdbc:clickhouse://localhost:8123/default").trim();
        boolean secure = rawUrl.startsWith("jdbc:clickhouses://") || rawUrl.startsWith("https://");
        String remainder = rawUrl
                .replaceFirst("^jdbc:clickhouses?://", "")
                .replaceFirst("^https?://", "");

        int queryIndex = remainder.indexOf('?');
        if (queryIndex >= 0) {
            remainder = remainder.substring(0, queryIndex);
        }

        String endpoint = remainder;
        String database = "default";
        int slashIndex = remainder.indexOf('/');
        if (slashIndex >= 0) {
            endpoint = remainder.substring(0, slashIndex);
            database = StringUtils.defaultIfBlank(remainder.substring(slashIndex + 1), "default");
        }

        return new DefaultClickHouseEndpoint(StringUtils.defaultIfBlank(endpoint, "localhost:8123"), database, secure);
    }

    private record DefaultClickHouseEndpoint(String endpoint, String database, boolean secure) {
    }
}
