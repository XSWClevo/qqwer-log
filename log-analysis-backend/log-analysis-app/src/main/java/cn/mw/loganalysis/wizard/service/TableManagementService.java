package cn.mw.loganalysis.wizard.service;

import cn.mw.loganalysis.datasource.entity.Datasource;
import cn.mw.loganalysis.datasource.mapper.DatasourceMapper;
import cn.mw.loganalysis.stats.service.query.support.DynamicMyBatisUtils;
import cn.mw.loganalysis.stats.service.query.support.StatsQueryMapperUtils;
import cn.mw.loganalysis.vector.dto.ConfigComponentRequest;
import cn.mw.loganalysis.vector.service.ConfigComponentService;
import cn.mw.loganalysis.wizard.dto.CreateTableRequest;
import cn.mw.loganalysis.wizard.dto.CreateTableResponse;
import cn.mw.loganalysis.wizard.mapper.ClickHouseTableMapper;
import com.clickhouse.jdbc.ClickHouseDataSource;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.ibatis.session.SqlSessionFactory;
import org.springframework.stereotype.Service;

import java.sql.Connection;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 表管理服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TableManagementService {

    private final DatasourceMapper datasourceMapper;
    private final ConfigComponentService componentService;
    private final ObjectMapper objectMapper;

    /**
     * 创建表并自动创建对应的 Vector 组件
     */
    public CreateTableResponse createTableWithComponents(CreateTableRequest request) {
        try {
            // 1. 创建表
            createTable(request.getDatasourceId(), request.getDdl());

            // 2. 如果不需要自动创建组件，直接返回
            if (Boolean.FALSE.equals(request.getAutoCreateComponents())) {
                return CreateTableResponse.success(request.getTableName(), null, null);
            }

            // 3. 提取表名（如果没有提供）
            String tableName = request.getTableName();
            if (StringUtils.isBlank(tableName)) {
                tableName = extractTableName(request.getDdl());
            }

            // 4. 创建 Remap Transform 组件
            String remapComponentId = null;
            if (StringUtils.isNotBlank(request.getVrlScript())) {
                remapComponentId = createRemapComponent(tableName, request.getVrlScript(), request.getParseMethod());
            }

            // 5. 创建 ClickHouse Sink 组件
            String sinkComponentId = createClickHouseSinkComponent(
                request.getDatasourceId(),
                tableName,
                remapComponentId
            );

            return CreateTableResponse.success(tableName, remapComponentId, sinkComponentId);

        } catch (Exception e) {
            log.error("创建表和组件失败", e);
            return CreateTableResponse.error(e.getMessage());
        }
    }

    /**
     * 创建表
     */
    public void createTable(String datasourceId, String ddl) {
        Datasource datasource = datasourceMapper.selectById(datasourceId);
        if (datasource == null) {
            throw new RuntimeException("数据源不存在");
        }

        if (!"clickhouse".equalsIgnoreCase(datasource.getType())) {
            throw new RuntimeException("仅支持 ClickHouse 数据源");
        }

        try (Connection conn = buildClickHouseDataSource(datasource).getConnection();
                 Statement stmt = conn.createStatement()) {
            stmt.execute(ddl);
            log.info("创建表成功，数据源：{}，DDL：{}", datasourceId, ddl);
        } catch (Exception e) {
            log.error("创建表失败", e);
            throw new RuntimeException("创建表失败: " + e.getMessage(), e);
        }
    }

    /**
     * 查询表列表
     */
    public List<Map<String, Object>> listTables(String datasourceId) {
        Datasource datasource = datasourceMapper.selectById(datasourceId);
        if (datasource == null) {
            throw new RuntimeException("数据源不存在");
        }

        if (!"clickhouse".equalsIgnoreCase(datasource.getType())) {
            throw new RuntimeException("仅支持 ClickHouse 数据源");
        }

        try {
            String databaseExpression = StatsQueryMapperUtils.quoteClickHouseIdentifier(
                    StringUtils.defaultIfBlank(datasource.getDatabaseName(), "default"));
            List<String> tableNames = DynamicMyBatisUtils.execute(
                    buildSqlSessionFactory(datasource),
                    ClickHouseTableMapper.class,
                    mapper -> mapper.selectTables(databaseExpression)
            );
            List<Map<String, Object>> tables = new ArrayList<>(tableNames.size());
            for (String tableName : tableNames) {
                Map<String, Object> table = new HashMap<>();
                table.put("name", tableName);
                tables.add(table);
            }
            return tables;
        } catch (Exception e) {
            log.error("查询表列表失败", e);
            throw new RuntimeException("查询表列表失败: " + e.getMessage(), e);
        }
    }

    /**
     * 查询表结构
     */
    public List<Map<String, Object>> describeTable(String datasourceId, String tableName) {
        Datasource datasource = datasourceMapper.selectById(datasourceId);
        if (datasource == null) {
            throw new RuntimeException("数据源不存在");
        }

        if (!"clickhouse".equalsIgnoreCase(datasource.getType())) {
            throw new RuntimeException("仅支持 ClickHouse 数据源");
        }

        try {
            String databaseExpression = StatsQueryMapperUtils.quoteClickHouseIdentifier(
                    StringUtils.defaultIfBlank(datasource.getDatabaseName(), "default"));
            String tableExpression = StatsQueryMapperUtils.quoteClickHouseIdentifier(tableName);
            return DynamicMyBatisUtils.execute(
                    buildSqlSessionFactory(datasource),
                    ClickHouseTableMapper.class,
                    mapper -> mapper.selectTableColumns(databaseExpression, tableExpression)
            );
        } catch (Exception e) {
            log.error("查询表结构失败", e);
            throw new RuntimeException("查询表结构失败: " + e.getMessage(), e);
        }
    }

    /**
     * 添加字段
     */
    public void addColumn(String datasourceId, String tableName, String columnName, 
                         String columnType, String comment) {
        Datasource datasource = datasourceMapper.selectById(datasourceId);
        if (datasource == null) {
            throw new RuntimeException("数据源不存在");
        }

        if (!"clickhouse".equalsIgnoreCase(datasource.getType())) {
            throw new RuntimeException("仅支持 ClickHouse 数据源");
        }

        try (Connection conn = buildClickHouseDataSource(datasource).getConnection();
                 Statement stmt = conn.createStatement()) {
            StringBuilder sql = new StringBuilder();
            sql.append("ALTER TABLE ").append(datasource.getDatabaseName())
               .append(".").append(tableName)
               .append(" ADD COLUMN IF NOT EXISTS ")
               .append(columnName).append(" ").append(columnType);

            if (StringUtils.isNotBlank(comment)) {
                sql.append(" COMMENT '").append(comment.replace("'", "\\'")).append("'");
            }

            stmt.execute(sql.toString());
            log.info("添加字段成功，表：{}，字段：{}", tableName, columnName);
        } catch (Exception e) {
            log.error("添加字段失败", e);
            throw new RuntimeException("添加字段失败: " + e.getMessage(), e);
        }
    }

    private SqlSessionFactory buildSqlSessionFactory(Datasource datasource) throws Exception {
        return DynamicMyBatisUtils.buildSqlSessionFactory(buildClickHouseDataSource(datasource), ClickHouseTableMapper.class);
    }

    private ClickHouseDataSource buildClickHouseDataSource(Datasource datasource) throws Exception {
        Properties properties = new Properties();
        properties.setProperty("user", StringUtils.defaultIfBlank(datasource.getUsername(), "default"));
        properties.setProperty("password", StringUtils.defaultString(datasource.getPassword()));
        return new ClickHouseDataSource(buildClickHouseUrl(datasource), properties);
    }

    /**
     * 构建 ClickHouse URL
     */
    private String buildClickHouseUrl(Datasource datasource) {
        return String.format("jdbc:clickhouse://%s:%d/%s",
            datasource.getHost(),
            datasource.getPort(),
            StringUtils.defaultIfBlank(datasource.getDatabaseName(), "default"));
    }

    /**
     * 从 DDL 中提取表名
     */
    private String extractTableName(String ddl) {
        // 匹配 CREATE TABLE ... database.table_name 或 CREATE TABLE ... table_name
        Pattern pattern = Pattern.compile("CREATE\\s+TABLE\\s+(?:IF\\s+NOT\\s+EXISTS\\s+)?(?:\\w+\\.)?([\\w`]+)",
            Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(ddl);
        if (matcher.find()) {
            String tableName = matcher.group(1);
            // 移除反引号
            return tableName.replace("`", "");
        }
        return "unknown_table";
    }

    /**
     * 创建 Remap Transform 组件
     */
    private String createRemapComponent(String tableName, String vrlScript, String parseMethod) {
        ConfigComponentRequest request = new ConfigComponentRequest();
        request.setName(tableName + "_remap");
        request.setComponentType("transform");
        request.setVectorType("remap");
        request.setDescription("智能向导自动创建 - " + tableName + " 日志解析");
        request.setIsTemplate(false);

        // 生成 YAML 配置
        StringBuilder yaml = new StringBuilder();
        yaml.append("type: remap\n");
        yaml.append("inputs:\n");
//        yaml.append("  - <source_id>\n");
        yaml.append("source: |\n");

        // 添加 VRL 脚本（每行缩进 2 个空格）
        for (String line : vrlScript.split("\n")) {
            yaml.append("  ").append(line).append("\n");
        }

        request.setConfigYaml(yaml.toString());

        // 保存可视化数据（字段名与前端 visualConfig 完全一致）
        Map<String, Object> visualData = new LinkedHashMap<>();
        visualData.put("parse_method", StringUtils.defaultString(parseMethod, "custom"));
        visualData.put("regex_pattern", "");
        visualData.put("grok_pattern", "");
        visualData.put("vrl_source", StringUtils.defaultString(vrlScript, ""));
        visualData.put("generate_uuid", false);
        visualData.put("keep_raw", false);
        visualData.put("extract_source_ip", false);
        visualData.put("convert_procid", false);
        visualData.put("add_fields", Collections.emptyList());
        visualData.put("remove_fields", Collections.emptyList());
        visualData.put("log_sample", "");
        visualData.put("parsed_fields", Collections.emptyList());
        request.setVisualData(toJson(visualData));

        return componentService.create(request, "wizard").getId();
    }

    /**
     * 创建 ClickHouse Sink 组件
     */
    private String createClickHouseSinkComponent(String datasourceId, String tableName, String remapComponentId) {
        Datasource datasource = datasourceMapper.selectById(datasourceId);
        if (datasource == null) {
            throw new RuntimeException("数据源不存在");
        }

        ConfigComponentRequest request = new ConfigComponentRequest();
        request.setName(tableName + "_sink");
        request.setComponentType("sink");
        request.setVectorType("clickhouse");
        request.setDescription("智能向导自动创建 - " + tableName + " 数据写入");
        request.setIsTemplate(false);
        request.setQueryable(true);
        request.setDisplayName(tableName);

        // 生成 YAML 配置
        String endpoint = "http://" + datasource.getHost() + ":" + datasource.getPort();
        String username = ObjectUtils.defaultIfNull(datasource.getUsername(), "default");
        String password = ObjectUtils.defaultIfNull(datasource.getPassword(), "");
        String database = StringUtils.defaultIfBlank(datasource.getDatabaseName(), "default");

        StringBuilder yaml = new StringBuilder();
        yaml.append("type: clickhouse\n");
        yaml.append("endpoint: ").append(endpoint).append("\n");
        yaml.append("database: ").append(database).append("\n");
        yaml.append("table: ").append(tableName).append("\n");
        yaml.append("skip_unknown_fields: true\n");
        yaml.append("encoding:\n");
        yaml.append("  timestamp_format: unix\n");
        yaml.append("auth:\n");
        yaml.append("  strategy: basic\n");
        yaml.append("  user: ").append(username).append("\n");
        yaml.append("  password: \"").append(password).append("\"\n");
        yaml.append("batch:\n");
        yaml.append("  max_bytes: 10000000\n");
        yaml.append("  timeout_secs: 10\n");
        yaml.append("buffer:\n");
        yaml.append("  type: memory\n");
        yaml.append("  max_events: 500000\n");

        request.setConfigYaml(yaml.toString());

        // 保存可视化数据（字段名与前端 visualConfig 保持一致）
        Map<String, Object> visualData = new LinkedHashMap<>();
        visualData.put("endpoint", endpoint);
        visualData.put("database", database);
        visualData.put("table", tableName);
        visualData.put("clickhouse_user", username);
        visualData.put("clickhouse_password", password);
        visualData.put("clickhouse_format", "json_each_row");
        visualData.put("clickhouse_compression", "gzip");
        visualData.put("clickhouse_skip_unknown", true);
        visualData.put("clickhouse_timestamp_format", "unix");
        visualData.put("clickhouse_batch_max_bytes", "10000000");
        visualData.put("clickhouse_batch_timeout", "10");
        visualData.put("clickhouse_buffer_type", "memory");
        visualData.put("clickhouse_buffer_max_events", "500000");
        request.setVisualData(toJson(visualData));
        request.setDatasourceId(datasourceId);

        return componentService.create(request, "wizard").getId();
    }

    /**
     * 将 Map 转换为 JSON 字符串（支持数组、嵌套对象、布尔值等复杂类型）
     */
    private String toJson(Map<String, Object> map) {
        try {
            return objectMapper.writeValueAsString(map);
        } catch (JsonProcessingException e) {
            log.error("转换 JSON 失败", e);
            return "{}";
        }
    }
}
