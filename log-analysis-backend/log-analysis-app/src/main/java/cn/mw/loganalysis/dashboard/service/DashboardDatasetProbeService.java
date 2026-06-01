package cn.mw.loganalysis.dashboard.service;

import cn.mw.loganalysis.dashboard.dto.DashboardDatasetCandidateDTO;
import cn.mw.loganalysis.stats.service.DynamicLogQueryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatterBuilder;
import java.time.temporal.ChronoField;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 对候选数据集执行轻量探测。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DashboardDatasetProbeService {

    private static final DateTimeFormatter LENIENT_DATE_TIME_FORMATTER = new DateTimeFormatterBuilder()
            .appendPattern("yyyy-MM-dd HH:mm:ss")
            .optionalStart()
            .appendFraction(ChronoField.NANO_OF_SECOND, 1, 9, true)
            .optionalEnd()
            .toFormatter();

    private final DynamicLogQueryService dynamicLogQueryService;

    /**
     * 探测多个候选数据集。
     */
    public List<DashboardDatasetProbeResult> probeCandidates(List<DashboardDatasetCandidateDTO> candidates) {
        List<DashboardDatasetProbeResult> results = new ArrayList<>();
        if (CollectionUtils.isEmpty(candidates)) {
            return results;
        }
        for (DashboardDatasetCandidateDTO candidate : candidates) {
            results.add(probeCandidate(candidate));
        }
        return results;
    }

    /**
     * 仅验证表是否存在、核心字段是否齐全、行数与最近时间。
     */
    public DashboardDatasetProbeResult probeCandidate(DashboardDatasetCandidateDTO candidate) {
        try {
            Set<String> columns = loadTableColumns(candidate);
            Map<String, String> resolvedFieldMapping = resolveFieldMapping(candidate.getFieldMapping(), columns);
            String timeField = resolvedFieldMapping.get("timestamp");
            String messageField = resolvedFieldMapping.get("message");
            if (StringUtils.isAnyBlank(timeField, messageField)) {
                return buildProbeResult(candidate, resolvedFieldMapping, true, false, 0L, null);
            }

            String tableRef = sqlIdentifier(candidate.getDatabaseName()) + "." + sqlIdentifier(candidate.getTableName());
            String sql = """
                    SELECT
                      count() AS total_rows,
                      max(%s) AS latest_log_time
                    FROM %s
                    LIMIT 1
                    """.formatted(
                    sqlIdentifier(timeField),
                    tableRef
            );
            Object raw = dynamicLogQueryService.executeRawSQLJdbc(candidate.getDatasourceId(), sql);
            Map<String, Object> row = extractSingleRow(raw);
            long totalRows = asLong(row.get("total_rows"));
            LocalDateTime latestLogTime = asLocalDateTime(row.get("latest_log_time"));

            return buildProbeResult(candidate, resolvedFieldMapping, true, true, totalRows, latestLogTime);
        } catch (Exception ex) {
            log.warn("Dashboard 数据集探测失败, datasourceId={}, table={}: {}",
                    candidate.getDatasourceId(), candidate.getTableName(), ex.getMessage());
            return buildProbeResult(candidate, candidate.getFieldMapping(), false, false, 0L, null);
        }
    }

    /**
     * 查询真实表结构，为字段映射和容错提供依据。
     */
    private Set<String> loadTableColumns(DashboardDatasetCandidateDTO candidate) {
        String sql = """
                SELECT name
                FROM system.columns
                WHERE database = '%s'
                  AND table = '%s'
                """.formatted(
                escapeLiteral(candidate.getDatabaseName()),
                escapeLiteral(candidate.getTableName())
        );
        List<Map<String, Object>> rows = extractRows(dynamicLogQueryService.executeRawSQLJdbc(candidate.getDatasourceId(), sql));
        Set<String> columns = new LinkedHashSet<>();
        for (Map<String, Object> row : rows) {
            String name = StringUtils.trimToNull(String.valueOf(row.get("name")));
            if (name != null) {
                columns.add(name);
            }
        }
        return columns;
    }

    /**
     * 用真实列名回填逻辑字段，避免仅靠 visualData 猜字段导致探测误判。
     */
    private Map<String, String> resolveFieldMapping(Map<String, String> configuredFieldMapping, Set<String> columns) {
        Map<String, String> resolved = new HashMap<>();
        resolved.put("timestamp", resolveField(columns, configuredFieldMapping, "timestamp", List.of("timestamp", "time", "@timestamp")));
        resolved.put("severity", resolveField(columns, configuredFieldMapping, "severity", List.of("severity", "level", "log_level")));
        resolved.put("message", resolveField(columns, configuredFieldMapping, "message", List.of("message", "msg", "content")));
        resolved.put("raw", resolveField(columns, configuredFieldMapping, "raw", List.of("raw", "original", "log")));
        resolved.put("hostname", resolveField(columns, configuredFieldMapping, "hostname", List.of("hostname", "host", "host_name")));
        resolved.put("appname", resolveField(columns, configuredFieldMapping, "appname", List.of("appname", "app_name", "service")));
        return resolved;
    }

    /**
     * 优先尊重显式映射，其次用常见别名和真实列名做兜底。
     */
    private String resolveField(Set<String> columns,
                                Map<String, String> configuredFieldMapping,
                                String logicalKey,
                                List<String> aliases) {
        String configured = MapUtils.getString(configuredFieldMapping, logicalKey);
        if (StringUtils.isNotBlank(configured) && columns.contains(configured)) {
            return configured;
        }
        for (String alias : aliases) {
            if (columns.contains(alias)) {
                return alias;
            }
        }
        return null;
    }

    /**
     * 将探测后的真实字段映射带回上下文，后续聚合直接复用。
     */
    private DashboardDatasetProbeResult buildProbeResult(DashboardDatasetCandidateDTO candidate,
                                                         Map<String, String> resolvedFieldMapping,
                                                         boolean tableExists,
                                                         boolean hasCoreFields,
                                                         long totalRows,
                                                         LocalDateTime latestLogTime) {
        DashboardDatasetCandidateDTO resolvedCandidate = DashboardDatasetCandidateDTO.builder()
                .source(candidate.getSource())
                .datasourceId(candidate.getDatasourceId())
                .datasourceName(candidate.getDatasourceName())
                .databaseName(candidate.getDatabaseName())
                .tableName(candidate.getTableName())
                .componentType(candidate.getComponentType())
                .queryable(candidate.getQueryable())
                .fieldMapping(resolvedFieldMapping)
                .build();
        return DashboardDatasetProbeResult.builder()
                .candidate(resolvedCandidate)
                .tableExists(tableExists)
                .hasCoreFields(hasCoreFields)
                .totalRows(totalRows)
                .latestLogTime(latestLogTime)
                .build();
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> extractRows(Object raw) {
        if (raw instanceof List<?> list) {
            List<Map<String, Object>> rows = new ArrayList<>();
            for (Object item : list) {
                if (item instanceof Map<?, ?> map) {
                    rows.add((Map<String, Object>) map);
                }
            }
            return rows;
        }
        return List.of();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> extractSingleRow(Object raw) {
        if (raw instanceof List<?> list && !list.isEmpty() && list.get(0) instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        return Map.of();
    }

    private long asLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value == null) {
            return 0L;
        }
        return Long.parseLong(String.valueOf(value));
    }

    private LocalDateTime asLocalDateTime(Object value) {
        if (value instanceof LocalDateTime dateTime) {
            return dateTime;
        }
        if (value instanceof Timestamp timestamp) {
            return timestamp.toLocalDateTime();
        }
        if (value == null) {
            return null;
        }
        String normalized = String.valueOf(value).replace("T", " ");
        return LocalDateTime.parse(normalized, LENIENT_DATE_TIME_FORMATTER);
    }

    private String sqlIdentifier(String value) {
        return "`" + StringUtils.defaultString(value).replace("`", "") + "`";
    }

    private String escapeLiteral(String value) {
        return StringUtils.defaultString(value).replace("'", "''");
    }
}
