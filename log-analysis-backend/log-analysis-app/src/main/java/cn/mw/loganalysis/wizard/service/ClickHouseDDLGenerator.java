package cn.mw.loganalysis.wizard.service;

import cn.mw.loganalysis.config.service.SystemConfigService;
import cn.mw.loganalysis.datasource.entity.Datasource;
import cn.mw.loganalysis.datasource.mapper.DatasourceMapper;
import cn.mw.loganalysis.wizard.dto.GenerateDDLRequest;
import cn.mw.loganalysis.wizard.dto.GenerateDDLRequest.FieldDefinition;
import cn.mw.loganalysis.wizard.dto.GenerateDDLResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * ClickHouse DDL 生成器
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ClickHouseDDLGenerator {

    private final SystemConfigService systemConfigService;
    private final DatasourceMapper datasourceMapper;

    // ClickHouse 保留字
    private static final Set<String> RESERVED_WORDS = Set.of(
        "select", "from", "where", "group", "order", "by", "having", "limit",
        "offset", "union", "all", "distinct", "as", "join", "on", "using",
        "table", "database", "create", "drop", "alter", "insert", "update",
        "delete", "truncate", "show", "describe", "explain", "use"
    );

    /**
     * 生成 ClickHouse DDL
     */
    public GenerateDDLResponse generate(GenerateDDLRequest request) {
        try {
            // 查询数据源
            Datasource datasource = datasourceMapper.selectById(request.getDatasourceId());
            if (datasource == null) {
                return GenerateDDLResponse.error("数据源不存在");
            }

            if (!"clickhouse".equalsIgnoreCase(datasource.getType())) {
                return GenerateDDLResponse.error("数据源类型不是 ClickHouse");
            }

            // 获取系统配置
            Map<String, String> config = systemConfigService.getConfigByType("clickhouse");

            // 生成 DDL
            String ddl = buildDDL(request, datasource, config);

            return GenerateDDLResponse.success(ddl, config);

        } catch (Exception e) {
            log.error("生成 ClickHouse DDL 失败", e);
            return GenerateDDLResponse.error("生成失败: " + e.getMessage());
        }
    }

    /**
     * 构建 DDL
     */
    private String buildDDL(GenerateDDLRequest request, Datasource datasource, Map<String, String> config) {
        StringBuilder ddl = new StringBuilder();

        String database = datasource.getDatabaseName();
        String tableName = escapeIdentifier(request.getTableName());
        FieldDefinition timestampField = findFieldIgnoreCase(request.getFields(), "timestamp");
        FieldDefinition rawField = findFieldIgnoreCase(request.getFields(), "raw");
        boolean hasTimestampField = timestampField != null;
        boolean hasRawField = rawField != null;

        // 获取所有字段名
        Set<String> fieldNames = new HashSet<>();
        fieldNames.add("id"); // id 字段总是存在
        for (FieldDefinition field : request.getFields()) {
            fieldNames.add(field.getName().toLowerCase());
        }

        // 检查是否需要添加 timestamp 字段
        boolean needTimestamp = false;
        String orderBy = config.getOrDefault("ddl.order_by", "timestamp,hostname");
        String partitionBy = config.getOrDefault("ddl.partition_by", "toYYYYMM(timestamp)");
        String ttlDays = config.getOrDefault("ddl.ttl_days", "30");

        // 检查 ORDER BY 中的字段
        String[] orderFields = orderBy.split(",");
        for (String field : orderFields) {
            String fieldName = field.trim().toLowerCase();
            if (fieldName.equals("timestamp") && !fieldNames.contains("timestamp")) {
                needTimestamp = true;
                break;
            }
        }

        // 检查 PARTITION BY 是否使用了 timestamp
        if (partitionBy.contains("timestamp") && !fieldNames.contains("timestamp")) {
            needTimestamp = true;
        }

        // 检查 TTL 是否使用了 timestamp
        if (!ttlDays.equals("0") && !fieldNames.contains("timestamp")) {
            needTimestamp = true;
        }

        // 如果需要 timestamp 但字段中没有，添加默认的 timestamp 字段
        if (needTimestamp) {
            fieldNames.add("timestamp");
        }

        // 验证 ORDER BY 中的所有字段都存在
        List<String> validOrderFields = new ArrayList<>();
        for (String field : orderFields) {
            String fieldName = field.trim().toLowerCase();
            if (fieldNames.contains(fieldName)) {
                validOrderFields.add(field.trim());
            }
        }

        // 如果没有有效的排序字段，使用 tuple()
        String finalOrderBy;
        if (validOrderFields.isEmpty()) {
            finalOrderBy = "tuple()";
        } else {
            finalOrderBy = String.join(",", validOrderFields);
        }

        // CREATE TABLE
        ddl.append("CREATE TABLE IF NOT EXISTS ").append(database).append(".").append(tableName).append("\n");
        ddl.append("(\n");

        // 添加 id 字段
        ddl.append("    id String DEFAULT generateUUIDv4(),\n");

        // 添加 timestamp 字段。存在显式字段时，保留用户选择的类型；否则补默认字段。
        if (hasTimestampField) {
            ddl.append("    ").append(buildTimestampColumn(timestampField)).append(",\n");
        } else if (needTimestamp) {
            ddl.append("    ").append(buildTimestampColumn(null)).append(",\n");
        }

        // 添加用户字段
        for (FieldDefinition field : request.getFields()) {
            String fieldName = escapeIdentifier(field.getName());

            if ("timestamp".equalsIgnoreCase(field.getName()) || "id".equalsIgnoreCase(field.getName())) {
                // 上面已经定义好了 id , timestamp 字段
                continue;
            }

            String fieldType = field.getType();

            ddl.append("    ").append(fieldName).append(" ").append(fieldType);

            if (field.getComment() != null && !field.getComment().isEmpty()) {
                ddl.append(" COMMENT '").append(escapeString(field.getComment())).append("'");
            }

            ddl.append(",\n");
        }

        // 添加 raw 字段（如果配置了保留原始日志）
        boolean keepRaw = Boolean.parseBoolean(config.getOrDefault("ddl.keep_raw", "true"));
        if (keepRaw && !hasRawField) {
            ddl.append("    raw String COMMENT '原始日志',\n");
        }

        // 添加索引
        String indexesConfig = config.getOrDefault("ddl.indexes", "timestamp:minmax,hostname:set");
        List<String> indexes = parseIndexes(indexesConfig, request.getFields(), needTimestamp);
        for (String index : indexes) {
            ddl.append("    ").append(index).append(",\n");
        }

        // 移除最后一个逗号
        if (ddl.charAt(ddl.length() - 2) == ',') {
            ddl.setLength(ddl.length() - 2);
            ddl.append("\n");
        }

        ddl.append(")\n");

        // ENGINE
        String engine = config.getOrDefault("ddl.engine", "MergeTree");
        ddl.append("ENGINE = ").append(engine).append("()\n");

        // PARTITION BY（如果使用了 timestamp 但字段不存在，使用 tuple()）
        if (partitionBy.contains("timestamp") && !needTimestamp && !hasTimestampField) {
            ddl.append("PARTITION BY tuple()\n");
        } else {
            ddl.append("PARTITION BY ").append(partitionBy).append("\n");
        }

        // ORDER BY
        ddl.append("ORDER BY (").append(finalOrderBy).append(")\n");

        // TTL（只有在有 timestamp 字段时才添加）
        if (!ttlDays.equals("0") && (needTimestamp || hasTimestampField)) {
            ddl.append("TTL timestamp + INTERVAL ").append(ttlDays).append(" DAY\n");
        }

        // SETTINGS
        ddl.append("SETTINGS index_granularity = 8192;");

        return ddl.toString();
    }

    private FieldDefinition findFieldIgnoreCase(List<FieldDefinition> fields, String fieldName) {
        for (FieldDefinition field : fields) {
            if (fieldName.equalsIgnoreCase(field.getName())) {
                return field;
            }
        }
        return null;
    }

    private String buildTimestampColumn(FieldDefinition timestampField) {
        String type = "DateTime";
        String comment = "时间戳";

        if (timestampField != null) {
            if (timestampField.getType() != null && !timestampField.getType().isBlank()) {
                type = timestampField.getType();
            }
            if (timestampField.getComment() != null && !timestampField.getComment().isBlank()) {
                comment = timestampField.getComment();
            }
        }

        StringBuilder column = new StringBuilder();
        column.append("timestamp ").append(type);

        String defaultExpression = getTimestampDefaultExpression(type);
        if (defaultExpression != null) {
            column.append(" DEFAULT ").append(defaultExpression);
        }

        column.append(" COMMENT '").append(escapeString(comment)).append("'");
        return column.toString();
    }

    private String getTimestampDefaultExpression(String type) {
        if (type == null || type.isBlank()) {
            return "now()";
        }

        String normalized = type.trim().toLowerCase();
        if (normalized.startsWith("datetime64")) {
            int open = normalized.indexOf('(');
            int close = normalized.indexOf(')', open + 1);
            if (open >= 0 && close > open) {
                String precision = normalized.substring(open + 1, close).split(",")[0].trim();
                if (precision.matches("\\d+")) {
                    return "now64(" + precision + ")";
                }
            }
            return "now64()";
        }
        if (normalized.startsWith("datetime")) {
            return "now()";
        }
        if (normalized.startsWith("date")) {
            return "today()";
        }
        return null;
    }

    /**
     * 解析索引配置
     */
    private List<String> parseIndexes(String indexesConfig, List<FieldDefinition> fields, boolean needTimestamp) {
        List<String> indexes = new ArrayList<>();

        if (indexesConfig == null || indexesConfig.isEmpty()) {
            return indexes;
        }

        // 获取所有字段名
        Set<String> fieldNames = new HashSet<>();
        for (FieldDefinition field : fields) {
            fieldNames.add(field.getName().toLowerCase());
        }

        // 如果需要 timestamp 字段，添加到字段列表中
        if (needTimestamp) {
            fieldNames.add("timestamp");
        }

        String[] indexConfigs = indexesConfig.split(",");
        for (String indexConfig : indexConfigs) {
            String[] parts = indexConfig.trim().split(":");
            if (parts.length != 2) {
                continue;
            }

            String fieldName = parts[0].trim();
            String indexType = parts[1].trim();

            // 检查字段是否存在
            if (!fieldNames.contains(fieldName.toLowerCase())) {
                continue;
            }

            String escapedFieldName = escapeIdentifier(fieldName);
            String indexName = "idx_" + fieldName;

            switch (indexType.toLowerCase()) {
                case "minmax":
                    indexes.add(String.format("INDEX %s %s TYPE minmax GRANULARITY 3", indexName, escapedFieldName));
                    break;
                case "set":
                    indexes.add(String.format("INDEX %s %s TYPE set(100) GRANULARITY 4", indexName, escapedFieldName));
                    break;
                case "tokenbf_v1":
                    indexes.add(String.format("INDEX %s %s TYPE tokenbf_v1(32768, 3, 0) GRANULARITY 4", indexName, escapedFieldName));
                    break;
                default:
                    log.warn("不支持的索引类型: {}", indexType);
            }
        }

        return indexes;
    }

    /**
     * 转义标识符（字段名、表名）
     */
    private String escapeIdentifier(String identifier) {
        if (identifier == null || identifier.isEmpty()) {
            return identifier;
        }

        // 如果是保留字或包含特殊字符，使用反引号
        if (RESERVED_WORDS.contains(identifier.toLowerCase()) || 
            !identifier.matches("^[a-zA-Z_][a-zA-Z0-9_]*$")) {
            return "`" + identifier + "`";
        }

        return identifier;
    }

    /**
     * 转义字符串（用于注释等）
     */
    private String escapeString(String str) {
        if (str == null) {
            return "";
        }
        return str.replace("'", "\\'").replace("\n", "\\n");
    }
}
