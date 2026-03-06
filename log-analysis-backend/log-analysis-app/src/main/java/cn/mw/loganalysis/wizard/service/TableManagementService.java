package cn.mw.loganalysis.wizard.service;

import cn.mw.loganalysis.datasource.entity.Datasource;
import cn.mw.loganalysis.datasource.mapper.DatasourceMapper;
import cn.mw.loganalysis.vector.dto.ConfigComponentRequest;
import cn.mw.loganalysis.vector.service.ConfigComponentService;
import cn.mw.loganalysis.wizard.dto.CreateTableRequest;
import cn.mw.loganalysis.wizard.dto.CreateTableResponse;
import com.clickhouse.jdbc.ClickHouseDataSource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
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
            if (!StringUtils.hasText(tableName)) {
                tableName = extractTableName(request.getDdl());
            }

            // 4. 创建 Remap Transform 组件
            String remapComponentId = null;
            if (StringUtils.hasText(request.getVrlScript())) {
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

        try {
            String url = buildClickHouseUrl(datasource);
            Properties properties = new Properties();
            properties.setProperty("user", datasource.getUsername());
            properties.setProperty("password", datasource.getPassword());

            ClickHouseDataSource dataSource = new ClickHouseDataSource(url, properties);

            try (Connection conn = dataSource.getConnection();
                 Statement stmt = conn.createStatement()) {
                
                stmt.execute(ddl);
                log.info("创建表成功，数据源：{}，DDL：{}", datasourceId, ddl);
            }

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
            String url = buildClickHouseUrl(datasource);
            Properties properties = new Properties();
            properties.setProperty("user", datasource.getUsername());
            properties.setProperty("password", datasource.getPassword());

            ClickHouseDataSource dataSource = new ClickHouseDataSource(url, properties);

            List<Map<String, Object>> tables = new ArrayList<>();

            try (Connection conn = dataSource.getConnection();
                 Statement stmt = conn.createStatement()) {
                
                String sql = String.format("SHOW TABLES FROM %s", datasource.getDatabaseName());
                ResultSet rs = stmt.executeQuery(sql);

                while (rs.next()) {
                    Map<String, Object> table = new HashMap<>();
                    table.put("name", rs.getString(1));
                    tables.add(table);
                }
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
            String url = buildClickHouseUrl(datasource);
            Properties properties = new Properties();
            properties.setProperty("user", datasource.getUsername());
            properties.setProperty("password", datasource.getPassword());

            ClickHouseDataSource dataSource = new ClickHouseDataSource(url, properties);

            List<Map<String, Object>> columns = new ArrayList<>();

            try (Connection conn = dataSource.getConnection();
                 Statement stmt = conn.createStatement()) {
                
                String sql = String.format("DESCRIBE TABLE %s.%s", 
                    datasource.getDatabaseName(), tableName);
                ResultSet rs = stmt.executeQuery(sql);

                while (rs.next()) {
                    Map<String, Object> column = new HashMap<>();
                    column.put("name", rs.getString("name"));
                    column.put("type", rs.getString("type"));
                    column.put("default_type", rs.getString("default_type"));
                    column.put("default_expression", rs.getString("default_expression"));
                    column.put("comment", rs.getString("comment"));
                    columns.add(column);
                }
            }

            return columns;

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

        try {
            String url = buildClickHouseUrl(datasource);
            Properties properties = new Properties();
            properties.setProperty("user", datasource.getUsername());
            properties.setProperty("password", datasource.getPassword());

            ClickHouseDataSource dataSource = new ClickHouseDataSource(url, properties);

            try (Connection conn = dataSource.getConnection();
                 Statement stmt = conn.createStatement()) {
                
                StringBuilder sql = new StringBuilder();
                sql.append("ALTER TABLE ").append(datasource.getDatabaseName())
                   .append(".").append(tableName)
                   .append(" ADD COLUMN IF NOT EXISTS ")
                   .append(columnName).append(" ").append(columnType);
                
                if (comment != null && !comment.isEmpty()) {
                    sql.append(" COMMENT '").append(comment.replace("'", "\\'")).append("'");
                }

                stmt.execute(sql.toString());
                log.info("添加字段成功，表：{}，字段：{}", tableName, columnName);
            }

        } catch (Exception e) {
            log.error("添加字段失败", e);
            throw new RuntimeException("添加字段失败: " + e.getMessage(), e);
        }
    }

    /**
     * 构建 ClickHouse URL
     */
    private String buildClickHouseUrl(Datasource datasource) {
        return String.format("jdbc:clickhouse://%s:%d/%s",
            datasource.getHost(),
            datasource.getPort(),
            datasource.getDatabaseName());
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

        // 保存可视化数据
        Map<String, Object> visualData = new HashMap<>();
        visualData.put("parseMethod", parseMethod);
        visualData.put("vrlScript", vrlScript);
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
        StringBuilder yaml = new StringBuilder();
        yaml.append("type: clickhouse\n");
//        yaml.append("inputs:\n");
//        if (StringUtils.hasText(remapComponentId)) {
//            yaml.append("  - ").append(remapComponentId).append("\n");
//        } else {
//            yaml.append("  - <source_id>\n");
//        }
        yaml.append("endpoint: http://").append(datasource.getHost()).append(":").append(datasource.getPort()).append("\n");
        yaml.append("database: ").append(datasource.getDatabaseName()).append("\n");
        yaml.append("table: ").append(tableName).append("\n");
        yaml.append("auth:\n");
        yaml.append("  strategy: basic\n");
        yaml.append("  user: ").append(datasource.getUsername()).append("\n");
        yaml.append("  password: ").append(datasource.getPassword()).append("\n");
        yaml.append("batch:\n");
        yaml.append("  max_events: 1000\n");
        yaml.append("  timeout_secs: 10\n");

        request.setConfigYaml(yaml.toString());

        // 保存可视化数据
        Map<String, Object> visualData = new HashMap<>();
        visualData.put("datasourceId", datasourceId);
        visualData.put("tableName", tableName);
        visualData.put("host", datasource.getHost());
        visualData.put("port", datasource.getPort());
        visualData.put("database", datasource.getDatabaseName());
        request.setVisualData(toJson(visualData));

        return componentService.create(request, "wizard").getId();
    }

    /**
     * 将 Map 转换为 JSON 字符串
     */
    private String toJson(Map<String, Object> map) {
        try {
            StringBuilder json = new StringBuilder("{");
            boolean first = true;
            for (Map.Entry<String, Object> entry : map.entrySet()) {
                if (!first) {
                    json.append(",");
                }
                json.append("\"").append(entry.getKey()).append("\":");
                Object value = entry.getValue();
                if (value instanceof String) {
                    json.append("\"").append(((String) value).replace("\"", "\\\"")).append("\"");
                } else if (value instanceof Number || value instanceof Boolean) {
                    json.append(value);
                } else {
                    json.append("\"").append(value).append("\"");
                }
                first = false;
            }
            json.append("}");
            return json.toString();
        } catch (Exception e) {
            log.error("转换 JSON 失败", e);
            return "{}";
        }
    }
}
