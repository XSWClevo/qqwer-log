package cn.mw.loganalysis.stats.service.query;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Elasticsearch 数据源操作策略
 */
@Slf4j
@Component
public class ElasticsearchOperationStrategy implements DatasourceOperationStrategy {

    private static final ObjectMapper objectMapper = new ObjectMapper();
    private final Map<String, RestTemplate> restTemplateCache = new ConcurrentHashMap<>();

    @Override
    public String getSupportedType() {
        return "elasticsearch";
    }

    @Override
    public ConnectionTestResult testConnection(DatasourceConnectionConfig config) {
        long startTime = System.currentTimeMillis();
        try {
            RestTemplate restTemplate = getRestTemplate(config);
            String baseUrl = buildBaseUrl(config);
            HttpHeaders headers = createHeaders(config);
            HttpEntity<String> entity = new HttpEntity<>(headers);

            ResponseEntity<String> response = restTemplate.exchange(
                baseUrl, HttpMethod.GET, entity, String.class
            );

            JsonNode responseJson = objectMapper.readTree(response.getBody());
            String version = responseJson.path("version").path("number").asText("unknown");
            String clusterName = responseJson.path("cluster_name").asText("unknown");

            long responseTime = System.currentTimeMillis() - startTime;

            return ConnectionTestResult.builder()
                    .success(true)
                    .message("连接成功，集群: " + clusterName)
                    .version(version)
                    .responseTimeMs(responseTime)
                    .build();
        } catch (Exception e) {
            log.error("Elasticsearch 连接测试失败: {}", e.getMessage());
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
            RestTemplate restTemplate = getRestTemplate(config);
            String baseUrl = buildBaseUrl(config);
            String indexName = config.getTable();
            HttpHeaders headers = createHeaders(config);
            HttpEntity<String> entity = new HttpEntity<>(headers);

            // 检查索引是否存在
            try {
                ResponseEntity<String> response = restTemplate.exchange(
                    baseUrl + "/" + indexName, HttpMethod.HEAD, entity, String.class
                );
                
                if (response.getStatusCode() != HttpStatus.OK) {
                    return TableCheckResult.builder()
                            .exists(false)
                            .tableName(indexName)
                            .message("索引不存在")
                            .build();
                }
            } catch (Exception e) {
                return TableCheckResult.builder()
                        .exists(false)
                        .tableName(indexName)
                        .message("索引不存在")
                        .build();
            }

            // 获取映射信息
            ResponseEntity<String> mappingResponse = restTemplate.exchange(
                baseUrl + "/" + indexName + "/_mapping", HttpMethod.GET, entity, String.class
            );

            List<FieldInfo> fields = new ArrayList<>();
            JsonNode responseJson = objectMapper.readTree(mappingResponse.getBody());
            Iterator<Map.Entry<String, JsonNode>> indices = responseJson.fields();
            if (indices.hasNext()) {
                JsonNode indexMapping = indices.next().getValue();
                JsonNode properties = indexMapping.path("mappings").path("properties");
                parseEsProperties(properties, "", fields);
            }

            // 获取文档数
            ResponseEntity<String> countResponse = restTemplate.exchange(
                baseUrl + "/" + indexName + "/_count", HttpMethod.GET, entity, String.class
            );
            JsonNode countJson = objectMapper.readTree(countResponse.getBody());
            Long rowCount = countJson.path("count").asLong(0);

            return TableCheckResult.builder()
                    .exists(true)
                    .tableName(indexName)
                    .message("索引存在")
                    .fields(fields)
                    .rowCount(rowCount)
                    .build();

        } catch (Exception e) {
            log.error("检查索引失败: {}", e.getMessage());
            return TableCheckResult.builder()
                    .exists(false)
                    .message("检查失败: " + e.getMessage())
                    .build();
        }
    }

    private void parseEsProperties(JsonNode properties, String prefix, List<FieldInfo> fields) {
        Iterator<Map.Entry<String, JsonNode>> fieldIterator = properties.fields();
        while (fieldIterator.hasNext()) {
            Map.Entry<String, JsonNode> field = fieldIterator.next();
            String fieldName = prefix.isEmpty() ? field.getKey() : prefix + "." + field.getKey();
            JsonNode fieldDef = field.getValue();
            
            String type = fieldDef.path("type").asText("");
            
            if (fieldDef.has("properties") && prefix.split("\\.").length < 2) {
                parseEsProperties(fieldDef.path("properties"), fieldName, fields);
            } else if (!type.isEmpty()) {
                fields.add(FieldInfo.builder()
                        .name(fieldName)
                        .type(type)
                        .label(fieldName)
                        .isTimestamp("date".equals(type))
                        .isStatsDimension("keyword".equals(type))
                        .build());
            }
        }
    }

    @Override
    public String generateCreateTableSQL(DatasourceConnectionConfig config, TableSchema schema) {
        // ES 使用 JSON 格式的映射
        try {
            ObjectNode mapping = objectMapper.createObjectNode();
            ObjectNode mappings = objectMapper.createObjectNode();
            ObjectNode properties = objectMapper.createObjectNode();

            for (TableSchema.ColumnDefinition col : schema.getColumns()) {
                ObjectNode fieldDef = objectMapper.createObjectNode();
                fieldDef.put("type", mapToEsType(col.getType()));
                
                // 对于 text 类型，添加 keyword 子字段
                if ("text".equals(mapToEsType(col.getType()))) {
                    ObjectNode fields = objectMapper.createObjectNode();
                    ObjectNode keyword = objectMapper.createObjectNode();
                    keyword.put("type", "keyword");
                    keyword.put("ignore_above", 256);
                    fields.set("keyword", keyword);
                    fieldDef.set("fields", fields);
                }
                
                properties.set(col.getName(), fieldDef);
            }

            mappings.set("properties", properties);
            mapping.set("mappings", mappings);

            // 添加设置
            ObjectNode settings = objectMapper.createObjectNode();
            settings.put("number_of_shards", 1);
            settings.put("number_of_replicas", 1);
            mapping.set("settings", settings);

            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(mapping);
        } catch (Exception e) {
            throw new RuntimeException("生成 ES 映射失败: " + e.getMessage());
        }
    }

    private String mapToEsType(String type) {
        if (type == null) return "text";
        String lowerType = type.toLowerCase();
        if (lowerType.contains("timestamp") || lowerType.contains("datetime") || lowerType.contains("date")) {
            return "date";
        }
        if (lowerType.contains("int") || lowerType.contains("long")) {
            return "long";
        }
        if (lowerType.contains("float") || lowerType.contains("double") || lowerType.contains("decimal")) {
            return "double";
        }
        if (lowerType.contains("bool")) {
            return "boolean";
        }
        if (lowerType.contains("uuid") || lowerType.contains("keyword") || lowerType.contains("varchar(")) {
            return "keyword";
        }
        return "text";
    }

    @Override
    public CreateTableResult createTable(DatasourceConnectionConfig config, TableSchema schema) {
        try {
            String mappingJson = generateCreateTableSQL(config, schema);
            RestTemplate restTemplate = getRestTemplate(config);
            String baseUrl = buildBaseUrl(config);
            String indexName = schema.getTableName() != null ? schema.getTableName() : config.getTable();
            
            HttpHeaders headers = createHeaders(config);
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<String> entity = new HttpEntity<>(mappingJson, headers);

            restTemplate.exchange(
                baseUrl + "/" + indexName, HttpMethod.PUT, entity, String.class
            );

            return CreateTableResult.builder()
                    .success(true)
                    .message("索引创建成功")
                    .executedSQL(mappingJson)
                    .build();
        } catch (Exception e) {
            log.error("创建索引失败: {}", e.getMessage());
            return CreateTableResult.builder()
                    .success(false)
                    .message("创建索引失败: " + e.getMessage())
                    .build();
        }
    }

    @Override
    public TableSchema getRecommendedSchema(DatasourceConnectionConfig config) {
        List<TableSchema.ColumnDefinition> columns = Arrays.asList(
            TableSchema.ColumnDefinition.builder()
                    .name("@timestamp").type("date").comment("日志时间").build(),
            TableSchema.ColumnDefinition.builder()
                    .name("level").type("keyword").comment("日志级别").build(),
            TableSchema.ColumnDefinition.builder()
                    .name("host").type("keyword").comment("主机名").build(),
            TableSchema.ColumnDefinition.builder()
                    .name("service").type("keyword").comment("服务名").build(),
            TableSchema.ColumnDefinition.builder()
                    .name("source").type("keyword").comment("来源").build(),
            TableSchema.ColumnDefinition.builder()
                    .name("message").type("text").comment("日志消息").build()
        );

        return TableSchema.builder()
                .tableName(config.getTable())
                .columns(columns)
                .build();
    }

    @Override
    public List<String> listTables(DatasourceConnectionConfig config) {
        try {
            RestTemplate restTemplate = getRestTemplate(config);
            String baseUrl = buildBaseUrl(config);
            HttpHeaders headers = createHeaders(config);
            HttpEntity<String> entity = new HttpEntity<>(headers);

            ResponseEntity<String> response = restTemplate.exchange(
                baseUrl + "/_cat/indices?format=json", HttpMethod.GET, entity, String.class
            );

            List<String> indices = new ArrayList<>();
            JsonNode responseJson = objectMapper.readTree(response.getBody());
            for (JsonNode index : responseJson) {
                String indexName = index.path("index").asText();
                if (!indexName.startsWith(".")) { // 排除系统索引
                    indices.add(indexName);
                }
            }
            return indices;
        } catch (Exception e) {
            log.error("获取索引列表失败: {}", e.getMessage());
            return new ArrayList<>();
        }
    }

    private RestTemplate getRestTemplate(DatasourceConnectionConfig config) {
        String cacheKey = config.getEndpoint();
        return restTemplateCache.computeIfAbsent(cacheKey, k -> new RestTemplate());
    }

    private String buildBaseUrl(DatasourceConnectionConfig config) {
        String endpoint = config.getEndpoint();
        if (endpoint.startsWith("http://") || endpoint.startsWith("https://")) {
            return endpoint;
        }
        String protocol = Boolean.TRUE.equals(config.getTls()) ? "https" : "http";
        return protocol + "://" + endpoint;
    }

    private HttpHeaders createHeaders(DatasourceConnectionConfig config) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        
        if (StringUtils.hasText(config.getUsername()) && StringUtils.hasText(config.getPassword())) {
            String auth = config.getUsername() + ":" + config.getPassword();
            String encodedAuth = Base64.getEncoder().encodeToString(auth.getBytes());
            headers.set("Authorization", "Basic " + encodedAuth);
        }
        
        return headers;
    }
}
