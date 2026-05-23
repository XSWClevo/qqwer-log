package cn.mw.loganalysis.attack.reader;

import cn.mw.loganalysis.attack.entity.AttackLogDataset;
import cn.mw.loganalysis.attack.model.NormalizedLogRecord;
import cn.mw.loganalysis.common.util.DateTimeUtils;
import cn.mw.loganalysis.stats.service.query.DatasourceConnectionConfig;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class ElasticsearchAttackLogDatasetReader implements AttackLogDatasetReader {

    private static final DateTimeFormatter[] SUPPORTED_TIME_FORMATTERS = new DateTimeFormatter[]{
            DateTimeFormatter.ISO_DATE_TIME,
            DateTimeFormatter.ofPattern(DateTimeUtils.PATTERN_DATETIME_MILLIS),
            DateTimeFormatter.ofPattern(DateTimeUtils.PATTERN_DATETIME)
    };

    private final AttackDatasourceConfigResolver datasourceConfigResolver;
    private final ObjectMapper objectMapper;

    @Override
    public boolean supports(String datasourceType) {
        return StringUtils.equalsIgnoreCase(datasourceType, "elasticsearch");
    }

    @Override
    public List<NormalizedLogRecord> read(AttackLogDataset dataset,
                                          LocalDateTime startTime,
                                          LocalDateTime endTime,
                                          int limit) {
        Map<String, String> mapping = dataset.getFieldMapping();
        if (MapUtils.isEmpty(mapping)) {
            log.warn("攻击分类 ES 数据集字段映射为空: id={}, name={}", dataset.getId(), dataset.getName());
            return Collections.emptyList();
        }

        String timeField = requiredMapping(mapping, "timestamp");
        String messageField = requiredMapping(mapping, "message");
        DatasourceConnectionConfig datasourceConfig = datasourceConfigResolver.resolve(dataset);
        String indexName = StringUtils.defaultIfBlank(datasourceConfig.getTable(), dataset.getIndexName());

        try {
            ObjectNode query = buildSearchQuery(timeField, startTime, endTime, limit);
            ResponseEntity<String> response = new RestTemplate().exchange(
                    buildBaseUrl(datasourceConfig) + "/" + indexName + "/_search",
                    HttpMethod.POST,
                    new HttpEntity<>(objectMapper.writeValueAsString(query), buildHeaders(datasourceConfig)),
                    String.class
            );
            return parseRecords(dataset, mapping, messageField, response.getBody());
        } catch (Exception ex) {
            log.error("读取 ES 攻击分类数据集失败: datasetId={}, index={}, error={}",
                    dataset.getId(), indexName, ex.getMessage(), ex);
            return Collections.emptyList();
        }
    }

    private ObjectNode buildSearchQuery(String timeField,
                                        LocalDateTime startTime,
                                        LocalDateTime endTime,
                                        int limit) {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("size", limit);

        ObjectNode bool = objectMapper.createObjectNode();
        ArrayNode filter = objectMapper.createArrayNode();
        ObjectNode rangeWrapper = objectMapper.createObjectNode();
        ObjectNode rangeFields = objectMapper.createObjectNode();
        ObjectNode range = objectMapper.createObjectNode();
        range.put("gt", startTime.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        range.put("lte", endTime.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        rangeFields.set(timeField, range);
        rangeWrapper.set("range", rangeFields);
        filter.add(rangeWrapper);
        bool.set("filter", filter);

        ObjectNode query = objectMapper.createObjectNode();
        query.set("bool", bool);
        root.set("query", query);

        ArrayNode sort = objectMapper.createArrayNode();
        ObjectNode sortField = objectMapper.createObjectNode();
        ObjectNode sortOrder = objectMapper.createObjectNode();
        sortOrder.put("order", "asc");
        sortField.set(timeField, sortOrder);
        sort.add(sortField);
        root.set("sort", sort);
        return root;
    }

    private List<NormalizedLogRecord> parseRecords(AttackLogDataset dataset,
                                                   Map<String, String> mapping,
                                                   String messageField,
                                                   String responseBody) throws Exception {
        JsonNode hits = objectMapper.readTree(responseBody).path("hits").path("hits");
        List<NormalizedLogRecord> records = new ArrayList<>();
        for (JsonNode hit : hits) {
            JsonNode source = hit.path("_source");
            LocalDateTime timestamp = toLocalDateTime(readField(source, mapping.get("timestamp")));
            String sourceIp = readField(source, mapping.get("sourceIp"));
            String hostname = readField(source, mapping.get("hostname"));
            String message = readField(source, messageField);
            String raw = StringUtils.defaultIfBlank(readField(source, mapping.get("raw")), source.toString());
            String severity = readField(source, mapping.get("severity"));

            records.add(NormalizedLogRecord.builder()
                    .timestamp(timestamp)
                    .sourceIp(sourceIp)
                    .hostname(hostname)
                    .message(message)
                    .raw(raw)
                    .severity(severity)
                    .fingerprint(FingerprintUtils.logFingerprint(dataset, timestamp, sourceIp, hostname, message, raw))
                    .build());
        }
        return records;
    }

    private String requiredMapping(Map<String, String> mapping, String standardField) {
        String field = mapping.get(standardField);
        if (StringUtils.isBlank(field)) {
            throw new IllegalArgumentException("数据集缺少必需字段映射: " + standardField);
        }
        return StringUtils.trim(field);
    }

    private String readField(JsonNode source, String fieldPath) {
        if (StringUtils.isBlank(fieldPath) || ObjectUtils.isEmpty(source)) {
            return "";
        }

        JsonNode current = source;
        for (String part : StringUtils.split(StringUtils.trim(fieldPath), '.')) {
            current = current.path(part);
        }
        if (current.isMissingNode() || current.isNull()) {
            return "";
        }
        return current.isValueNode() ? current.asText("") : current.toString();
    }

    private LocalDateTime toLocalDateTime(Object value) {
        if (value instanceof LocalDateTime localDateTime) {
            return localDateTime;
        }
        if (value instanceof Timestamp timestamp) {
            return timestamp.toLocalDateTime();
        }
        if (ObjectUtils.isEmpty(value)) {
            return null;
        }

        String text = StringUtils.trim(String.valueOf(value));
        try {
            return OffsetDateTime.parse(text).toLocalDateTime();
        } catch (Exception ignored) {
            // try local formatters
        }
        String normalized = StringUtils.removeEnd(text.replace('T', ' '), "Z");
        for (DateTimeFormatter formatter : SUPPORTED_TIME_FORMATTERS) {
            try {
                return LocalDateTime.parse(normalized, formatter);
            } catch (Exception ignored) {
                // try next formatter
            }
        }
        return LocalDateTime.parse(normalized, SUPPORTED_TIME_FORMATTERS[1]);
    }

    private HttpHeaders buildHeaders(DatasourceConnectionConfig datasourceConfig) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (StringUtils.isNotBlank(datasourceConfig.getUsername())) {
            String auth = datasourceConfig.getUsername() + ":" + StringUtils.defaultString(datasourceConfig.getPassword());
            headers.set("Authorization", "Basic " + Base64.getEncoder()
                    .encodeToString(auth.getBytes(StandardCharsets.UTF_8)));
        }
        return headers;
    }

    private String buildBaseUrl(DatasourceConnectionConfig datasourceConfig) {
        String endpoint = StringUtils.trimToEmpty(datasourceConfig.getEndpoint());
        if (StringUtils.startsWithAny(endpoint, "http://", "https://")) {
            return StringUtils.removeEnd(endpoint, "/");
        }
        String protocol = Boolean.TRUE.equals(datasourceConfig.getTls()) ? "https://" : "http://";
        return protocol + StringUtils.removeEnd(endpoint, "/");
    }
}
