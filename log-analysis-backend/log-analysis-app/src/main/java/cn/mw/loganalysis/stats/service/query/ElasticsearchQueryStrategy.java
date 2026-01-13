package cn.mw.loganalysis.stats.service.query;

import cn.mw.loganalysis.stats.dto.LogContextRequest;
import cn.mw.loganalysis.stats.dto.LogQueryRequest;
import cn.mw.loganalysis.stats.dto.StatsQueryRequest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Elasticsearch 日志查询策略实现
 */
@Slf4j
@Component
public class ElasticsearchQueryStrategy implements LogQueryStrategy {

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final ObjectMapper objectMapper = new ObjectMapper();

    private final Map<String, RestTemplate> restTemplateCache = new ConcurrentHashMap<>();

    @Override
    public String getSupportedType() {
        return "elasticsearch";
    }

    @Override
    public List<FieldInfo> getTableSchema(DatasourceConnectionConfig config) {
        log.info("Elasticsearch getTableSchema: index={}", config.getTable());

        try {
            RestTemplate restTemplate = getRestTemplate(config);
            String indexName = config.getTable();
            String baseUrl = buildBaseUrl(config);

            // 获取索引映射
            String url = baseUrl + "/" + indexName + "/_mapping";
            HttpHeaders headers = createHeaders(config);
            HttpEntity<String> entity = new HttpEntity<>(headers);

            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);
            JsonNode responseJson = objectMapper.readTree(response.getBody());

            List<FieldInfo> fields = new ArrayList<>();
            
            // 解析映射，获取字段信息
            // ES 映射结构: { "index_name": { "mappings": { "properties": { ... } } } }
            Iterator<Map.Entry<String, JsonNode>> indices = responseJson.fields();
            if (indices.hasNext()) {
                JsonNode indexMapping = indices.next().getValue();
                JsonNode properties = indexMapping.path("mappings").path("properties");
                
                parseEsProperties(properties, "", fields);
            }

            log.info("Found {} fields in index {}", fields.size(), indexName);
            return fields;

        } catch (Exception e) {
            log.error("Failed to get Elasticsearch schema", e);
            // 返回默认的常见字段
            return getDefaultEsFields();
        }
    }

    private void parseEsProperties(JsonNode properties, String prefix, List<FieldInfo> fields) {
        Iterator<Map.Entry<String, JsonNode>> fieldIterator = properties.fields();
        while (fieldIterator.hasNext()) {
            Map.Entry<String, JsonNode> field = fieldIterator.next();
            String fieldName = prefix.isEmpty() ? field.getKey() : prefix + "." + field.getKey();
            JsonNode fieldDef = field.getValue();
            
            String type = fieldDef.path("type").asText("");
            
            // 如果有嵌套属性，递归解析（但限制深度）
            if (fieldDef.has("properties") && prefix.split("\\.").length < 2) {
                parseEsProperties(fieldDef.path("properties"), fieldName, fields);
            } else if (!type.isEmpty()) {
                fields.add(FieldInfo.builder()
                        .name(fieldName)
                        .type(type)
                        .label(fieldName)
                        .isTimestamp(isTimestampType(type))
                        .isStatsDimension(isStatsDimensionType(type, fieldDef))
                        .isContentField(isContentField(fieldName))
                        .build());
            }
        }
    }

    private boolean isTimestampType(String type) {
        return "date".equals(type);
    }

    private boolean isStatsDimensionType(String type, JsonNode fieldDef) {
        // keyword 类型或有 keyword 子字段的适合做统计
        if ("keyword".equals(type)) {
            return true;
        }
        // 检查是否有 keyword 子字段
        return fieldDef.has("fields") && fieldDef.path("fields").has("keyword");
    }

    private boolean isContentField(String name) {
        return name != null && (
            name.equalsIgnoreCase("message") ||
            name.equalsIgnoreCase("raw") ||
            name.equalsIgnoreCase("content") ||
            name.equalsIgnoreCase("body") ||
            name.equalsIgnoreCase("log") ||
            name.equalsIgnoreCase("text") ||
            name.endsWith(".message") ||
            name.endsWith(".raw")
        );
    }

    private List<FieldInfo> getDefaultEsFields() {
        return Arrays.asList(
            FieldInfo.builder().name("@timestamp").type("date").label("时间戳").isTimestamp(true).isStatsDimension(false).isContentField(false).build(),
            FieldInfo.builder().name("message").type("text").label("消息").isTimestamp(false).isStatsDimension(false).isContentField(true).build(),
            FieldInfo.builder().name("level").type("keyword").label("级别").isTimestamp(false).isStatsDimension(true).isContentField(false).build(),
            FieldInfo.builder().name("host.name").type("keyword").label("主机").isTimestamp(false).isStatsDimension(true).isContentField(false).build(),
            FieldInfo.builder().name("service.name").type("keyword").label("服务").isTimestamp(false).isStatsDimension(true).isContentField(false).build()
        );
    }

    @Override
    public Map<String, Object> queryLogs(LogQueryRequest request, DatasourceConnectionConfig config) {
        log.info("Elasticsearch queryLogs: index={}, endpoint={}", config.getTable(), config.getEndpoint());

        try {
            RestTemplate restTemplate = getRestTemplate(config);
            String indexName = config.getTable();
            String baseUrl = buildBaseUrl(config);

            // 构建 ES 查询
            ObjectNode query = buildSearchQuery(request);
            query.put("from", (request.getPageNum() - 1) * request.getPageSize());
            query.put("size", request.getPageSize());

            // 添加排序
            ArrayNode sort = objectMapper.createArrayNode();
            ObjectNode sortField = objectMapper.createObjectNode();
            sortField.put("@timestamp", "desc");
            sort.add(sortField);
            query.set("sort", sort);

            String url = baseUrl + "/" + indexName + "/_search";
            HttpHeaders headers = createHeaders(config);
            HttpEntity<String> entity = new HttpEntity<>(objectMapper.writeValueAsString(query), headers);

            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.POST, entity, String.class);
            JsonNode responseJson = objectMapper.readTree(response.getBody());

            // 解析结果
            List<Map<String, Object>> data = new ArrayList<>();
            JsonNode hits = responseJson.path("hits").path("hits");
            for (JsonNode hit : hits) {
                Map<String, Object> row = new HashMap<>();
                JsonNode source = hit.path("_source");
                row.put("id", hit.path("_id").asText());
                
                // 映射常见字段
                row.put("timestamp", source.path("@timestamp").asText());
                row.put("message", source.path("message").asText());
                row.put("severity", source.path("level").asText(source.path("severity").asText()));
                row.put("hostname", source.path("host").path("name").asText(source.path("hostname").asText()));
                row.put("source_type", source.path("source").asText(source.path("source_type").asText()));
                row.put("appname", source.path("service").path("name").asText(source.path("appname").asText()));
                
                // 保留原始数据
                row.put("raw", source.toString());
                data.add(row);
            }

            long total = responseJson.path("hits").path("total").path("value").asLong();

            Map<String, Object> result = new HashMap<>();
            result.put("total", total);
            result.put("pageNum", request.getPageNum());
            result.put("pageSize", request.getPageSize());
            result.put("data", data);

            return result;

        } catch (Exception e) {
            log.error("Elasticsearch query failed", e);
            throw new RuntimeException("Elasticsearch 查询失败: " + e.getMessage());
        }
    }

    @Override
    public Map<String, Object> queryLogContext(LogContextRequest request, DatasourceConnectionConfig config) {
        log.info("Elasticsearch queryLogContext: logId={}", request.getLogId());

        try {
            RestTemplate restTemplate = getRestTemplate(config);
            String indexName = config.getTable();
            String baseUrl = buildBaseUrl(config);

            List<Map<String, Object>> beforeLogs = new ArrayList<>();
            List<Map<String, Object>> afterLogs = new ArrayList<>();

            // 查询之前的日志
            if (request.getBeforeCount() != null && request.getBeforeCount() > 0) {
                ObjectNode beforeQuery = objectMapper.createObjectNode();
                ObjectNode boolQuery = objectMapper.createObjectNode();
                ArrayNode must = objectMapper.createArrayNode();
                
                ObjectNode rangeQuery = objectMapper.createObjectNode();
                ObjectNode range = objectMapper.createObjectNode();
                range.put("lt", request.getTimestamp().toString());
                rangeQuery.set("@timestamp", range);
                ObjectNode rangeWrapper = objectMapper.createObjectNode();
                rangeWrapper.set("range", rangeQuery);
                must.add(rangeWrapper);
                
                boolQuery.set("must", must);
                ObjectNode queryWrapper = objectMapper.createObjectNode();
                queryWrapper.set("bool", boolQuery);
                beforeQuery.set("query", queryWrapper);
                beforeQuery.put("size", request.getBeforeCount());
                
                ArrayNode sort = objectMapper.createArrayNode();
                ObjectNode sortField = objectMapper.createObjectNode();
                sortField.put("@timestamp", "desc");
                sort.add(sortField);
                beforeQuery.set("sort", sort);

                String url = baseUrl + "/" + indexName + "/_search";
                HttpHeaders headers = createHeaders(config);
                HttpEntity<String> entity = new HttpEntity<>(objectMapper.writeValueAsString(beforeQuery), headers);
                ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.POST, entity, String.class);
                
                beforeLogs = parseHits(objectMapper.readTree(response.getBody()));
                Collections.reverse(beforeLogs);
            }

            // 查询之后的日志
            if (request.getAfterCount() != null && request.getAfterCount() > 0) {
                ObjectNode afterQuery = objectMapper.createObjectNode();
                ObjectNode boolQuery = objectMapper.createObjectNode();
                ArrayNode must = objectMapper.createArrayNode();
                
                ObjectNode rangeQuery = objectMapper.createObjectNode();
                ObjectNode range = objectMapper.createObjectNode();
                range.put("gt", request.getTimestamp().toString());
                rangeQuery.set("@timestamp", range);
                ObjectNode rangeWrapper = objectMapper.createObjectNode();
                rangeWrapper.set("range", rangeQuery);
                must.add(rangeWrapper);
                
                boolQuery.set("must", must);
                ObjectNode queryWrapper = objectMapper.createObjectNode();
                queryWrapper.set("bool", boolQuery);
                afterQuery.set("query", queryWrapper);
                afterQuery.put("size", request.getAfterCount());
                
                ArrayNode sort = objectMapper.createArrayNode();
                ObjectNode sortField = objectMapper.createObjectNode();
                sortField.put("@timestamp", "asc");
                sort.add(sortField);
                afterQuery.set("sort", sort);

                String url = baseUrl + "/" + indexName + "/_search";
                HttpHeaders headers = createHeaders(config);
                HttpEntity<String> entity = new HttpEntity<>(objectMapper.writeValueAsString(afterQuery), headers);
                ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.POST, entity, String.class);
                
                afterLogs = parseHits(objectMapper.readTree(response.getBody()));
            }

            Map<String, Object> result = new HashMap<>();
            result.put("beforeLogs", beforeLogs);
            result.put("afterLogs", afterLogs);
            result.put("totalBefore", beforeLogs.size());
            result.put("totalAfter", afterLogs.size());

            return result;

        } catch (Exception e) {
            log.error("Elasticsearch context query failed", e);
            throw new RuntimeException("Elasticsearch 上下文查询失败: " + e.getMessage());
        }
    }

    @Override
    public Map<String, Object> queryStats(StatsQueryRequest request, DatasourceConnectionConfig config) {
        log.info("Elasticsearch queryStats: dimensions={}", request.getDimensions());

        try {
            RestTemplate restTemplate = getRestTemplate(config);
            String indexName = config.getTable();
            String baseUrl = buildBaseUrl(config);

            Map<String, Object> result = new HashMap<>();

            if (request.getDimensions() == null || request.getDimensions().isEmpty()) {
                result.put("dimensions", Collections.emptyList());
                result.put("data", Collections.emptyList());
                return result;
            }

            Map<String, List<Map<String, Object>>> statsData = new HashMap<>();

            for (String dimension : request.getDimensions()) {
                ObjectNode query = objectMapper.createObjectNode();
                query.put("size", 0);

                // 时间范围过滤
                ObjectNode boolQuery = objectMapper.createObjectNode();
                ArrayNode filter = objectMapper.createArrayNode();
                ObjectNode rangeFilter = objectMapper.createObjectNode();
                ObjectNode range = objectMapper.createObjectNode();
                range.put("gte", request.getStartTime().toString());
                range.put("lte", request.getEndTime().toString());
                rangeFilter.set("@timestamp", range);
                ObjectNode rangeWrapper = objectMapper.createObjectNode();
                rangeWrapper.set("range", rangeFilter);
                filter.add(rangeWrapper);
                boolQuery.set("filter", filter);
                ObjectNode queryWrapper = objectMapper.createObjectNode();
                queryWrapper.set("bool", boolQuery);
                query.set("query", queryWrapper);

                // 聚合
                ObjectNode aggs = objectMapper.createObjectNode();
                ObjectNode dimAgg = objectMapper.createObjectNode();
                ObjectNode terms = objectMapper.createObjectNode();
                terms.put("field", mapDimensionField(dimension));
                terms.put("size", 10);
                dimAgg.set("terms", terms);
                aggs.set("dim_agg", dimAgg);
                query.set("aggs", aggs);

                String url = baseUrl + "/" + indexName + "/_search";
                HttpHeaders headers = createHeaders(config);
                HttpEntity<String> entity = new HttpEntity<>(objectMapper.writeValueAsString(query), headers);
                ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.POST, entity, String.class);

                JsonNode responseJson = objectMapper.readTree(response.getBody());
                JsonNode buckets = responseJson.path("aggregations").path("dim_agg").path("buckets");

                List<Map<String, Object>> dimensionData = new ArrayList<>();
                for (JsonNode bucket : buckets) {
                    Map<String, Object> item = new HashMap<>();
                    item.put("value", bucket.path("key").asText());
                    item.put("count", bucket.path("doc_count").asLong());
                    dimensionData.add(item);
                }
                statsData.put(dimension, dimensionData);
            }

            result.put("dimensions", request.getDimensions());
            result.put("metrics", request.getMetrics());
            result.put("data", statsData);

            return result;

        } catch (Exception e) {
            log.error("Elasticsearch stats query failed", e);
            throw new RuntimeException("Elasticsearch 统计查询失败: " + e.getMessage());
        }
    }

    @Override
    public Map<String, Object> queryTimeSeries(StatsQueryRequest request, DatasourceConnectionConfig config) {
        log.info("Elasticsearch queryTimeSeries: granularity={}", request.getGranularity());

        try {
            RestTemplate restTemplate = getRestTemplate(config);
            String indexName = config.getTable();
            String baseUrl = buildBaseUrl(config);

            ObjectNode query = objectMapper.createObjectNode();
            query.put("size", 0);

            // 时间范围过滤
            ObjectNode boolQuery = objectMapper.createObjectNode();
            ArrayNode filter = objectMapper.createArrayNode();
            ObjectNode rangeFilter = objectMapper.createObjectNode();
            ObjectNode range = objectMapper.createObjectNode();
            range.put("gte", request.getStartTime().toString());
            range.put("lte", request.getEndTime().toString());
            rangeFilter.set("@timestamp", range);
            ObjectNode rangeWrapper = objectMapper.createObjectNode();
            rangeWrapper.set("range", rangeFilter);
            filter.add(rangeWrapper);
            boolQuery.set("filter", filter);
            ObjectNode queryWrapper = objectMapper.createObjectNode();
            queryWrapper.set("bool", boolQuery);
            query.set("query", queryWrapper);

            // 时间聚合
            ObjectNode aggs = objectMapper.createObjectNode();
            ObjectNode timeAgg = objectMapper.createObjectNode();
            ObjectNode dateHistogram = objectMapper.createObjectNode();
            dateHistogram.put("field", "@timestamp");
            dateHistogram.put("fixed_interval", getEsInterval(request.getGranularity()));
            timeAgg.set("date_histogram", dateHistogram);
            aggs.set("time_agg", timeAgg);
            query.set("aggs", aggs);

            String url = baseUrl + "/" + indexName + "/_search";
            HttpHeaders headers = createHeaders(config);
            HttpEntity<String> entity = new HttpEntity<>(objectMapper.writeValueAsString(query), headers);
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.POST, entity, String.class);

            JsonNode responseJson = objectMapper.readTree(response.getBody());
            JsonNode buckets = responseJson.path("aggregations").path("time_agg").path("buckets");

            List<Map<String, Object>> series = new ArrayList<>();
            for (JsonNode bucket : buckets) {
                Map<String, Object> point = new HashMap<>();
                point.put("timestamp", bucket.path("key_as_string").asText());
                point.put("count", bucket.path("doc_count").asLong());
                series.add(point);
            }

            Map<String, Object> result = new HashMap<>();
            result.put("granularity", request.getGranularity());
            result.put("series", series);

            return result;

        } catch (Exception e) {
            log.error("Elasticsearch time series query failed", e);
            throw new RuntimeException("Elasticsearch 时序查询失败: " + e.getMessage());
        }
    }

    // ==================== 私有方法 ====================

    private RestTemplate getRestTemplate(DatasourceConnectionConfig config) {
        String cacheKey = config.getComponentId() + "_" + config.getEndpoint();
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

    private ObjectNode buildSearchQuery(LogQueryRequest request) {
        ObjectNode query = objectMapper.createObjectNode();
        ObjectNode boolQuery = objectMapper.createObjectNode();
        ArrayNode must = objectMapper.createArrayNode();
        ArrayNode mustNot = objectMapper.createArrayNode();

        // 时间范围
        ObjectNode rangeQuery = objectMapper.createObjectNode();
        ObjectNode range = objectMapper.createObjectNode();
        range.put("gte", request.getStartTime().toString());
        range.put("lte", request.getEndTime().toString());
        rangeQuery.set("@timestamp", range);
        ObjectNode rangeWrapper = objectMapper.createObjectNode();
        rangeWrapper.set("range", rangeQuery);
        must.add(rangeWrapper);

        // 字段过滤
        if (request.getFieldFilters() != null) {
            for (LogQueryRequest.FieldFilter filter : request.getFieldFilters()) {
                if (filter.getValues() == null || filter.getValues().isEmpty()) continue;
                
                String field = mapDimensionField(filter.getField());
                ObjectNode termsQuery = objectMapper.createObjectNode();
                ArrayNode values = objectMapper.createArrayNode();
                filter.getValues().forEach(values::add);
                termsQuery.set(field, values);
                ObjectNode termsWrapper = objectMapper.createObjectNode();
                termsWrapper.set("terms", termsQuery);
                
                if ("include".equalsIgnoreCase(filter.getType())) {
                    must.add(termsWrapper);
                } else {
                    mustNot.add(termsWrapper);
                }
            }
        }

        // Message 条件
        addMessageConditionsToQuery(must, mustNot, request.getMessageConditions(), "message");
        addMessageConditionsToQuery(must, mustNot, request.getRawConditions(), "raw");

        boolQuery.set("must", must);
        if (mustNot.size() > 0) {
            boolQuery.set("must_not", mustNot);
        }
        
        ObjectNode queryWrapper = objectMapper.createObjectNode();
        queryWrapper.set("bool", boolQuery);
        query.set("query", queryWrapper);

        return query;
    }

    private void addMessageConditionsToQuery(ArrayNode must, ArrayNode mustNot,
                                              List<LogQueryRequest.MessageCondition> conditions, String field) {
        if (conditions == null) return;
        
        for (LogQueryRequest.MessageCondition condition : conditions) {
            if (condition == null || !StringUtils.hasText(condition.getValue())) continue;
            
            ObjectNode matchQuery = objectMapper.createObjectNode();
            ObjectNode match = objectMapper.createObjectNode();
            match.put(field, condition.getValue());
            matchQuery.set("match_phrase", match);
            
            switch (condition.getOperator()) {
                case "contains":
                case "equals":
                    must.add(matchQuery);
                    break;
                case "notContains":
                case "notEquals":
                    mustNot.add(matchQuery);
                    break;
            }
        }
    }

    private List<Map<String, Object>> parseHits(JsonNode responseJson) {
        List<Map<String, Object>> data = new ArrayList<>();
        JsonNode hits = responseJson.path("hits").path("hits");
        for (JsonNode hit : hits) {
            Map<String, Object> row = new HashMap<>();
            JsonNode source = hit.path("_source");
            row.put("id", hit.path("_id").asText());
            row.put("timestamp", source.path("@timestamp").asText());
            row.put("message", source.path("message").asText());
            row.put("severity", source.path("level").asText(source.path("severity").asText()));
            row.put("hostname", source.path("host").path("name").asText(source.path("hostname").asText()));
            row.put("source_type", source.path("source").asText(source.path("source_type").asText()));
            row.put("appname", source.path("service").path("name").asText(source.path("appname").asText()));
            row.put("raw", source.toString());
            data.add(row);
        }
        return data;
    }

    private String mapDimensionField(String field) {
        if (field == null) return field;
        switch (field) {
            case "severity": case "levels": return "level.keyword";
            case "source_type": case "sources": case "source_types": return "source.keyword";
            case "hostname": case "hosts": case "hostnames": return "host.name.keyword";
            case "appname": case "services": case "appnames": return "service.name.keyword";
            default: return field + ".keyword";
        }
    }

    private String getEsInterval(String granularity) {
        if (granularity == null || "auto".equals(granularity)) {
            return "1h";
        }
        switch (granularity) {
            case "1m": return "1m";
            case "5m": return "5m";
            case "1h": return "1h";
            case "1d": return "1d";
            default: return "1h";
        }
    }
}
