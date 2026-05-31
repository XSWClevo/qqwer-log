package cn.mw.loganalysis.dashboard.service;

import cn.mw.loganalysis.dashboard.dto.*;
import cn.mw.loganalysis.stats.service.DynamicLogQueryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 围绕单个日志数据集构建首页展示快照。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DashboardLogDatasetService {

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final Map<String, String> LEVEL_COLORS = Map.of(
            "INFO", "#1890FF",
            "WARN", "#FAAD14",
            "WARNING", "#FAAD14",
            "ERROR", "#FF4D4F",
            "FATAL", "#722ED1",
            "CRITICAL", "#722ED1"
    );

    private final DynamicLogQueryService dynamicLogQueryService;

    /**
     * 组装当前数据集的所有首页面板。
     */
    public DashboardLogDatasetSnapshot buildSnapshot(DashboardDatasetContextDTO datasetContext,
                                                     DashboardQueryRequest request,
                                                     List<DashboardWarningDTO> warnings) {
        if (datasetContext == null) {
            return DashboardLogDatasetSnapshot.builder()
                    .capabilities(List.of())
                    .metricDrilldowns(buildMetricDrilldowns())
                    .emptyState(DashboardEmptyStateDTO.builder()
                            .title("暂无可统计日志数据集")
                            .description("当前平台还没有可查询的 ClickHouse 日志数据集，请先创建或开启 queryable Sink。")
                            .actionLabel("前往组件库")
                            .actionRoute("/vector/components")
                            .build())
                    .build();
        }

        if (!Boolean.TRUE.equals(datasetContext.getHasData())) {
            return DashboardLogDatasetSnapshot.builder()
                    .capabilities(buildCapabilities(datasetContext))
                    .metricDrilldowns(buildMetricDrilldowns())
                    .logTrend(emptyTrend(request.getGranularity()))
                    .severityDistribution(DashboardDistributionDTO.builder().items(List.of()).build())
                    .topHosts(DashboardTopListDTO.builder().items(List.of()).build())
                    .topApps(DashboardTopListDTO.builder().items(List.of()).build())
                    .topErrorMessages(DashboardTopListDTO.builder().items(List.of()).build())
                    .recentHighRiskLogs(DashboardRecentLogsDTO.builder().items(List.of()).build())
                    .emptyState(DashboardEmptyStateDTO.builder()
                            .title("当前数据集暂无日志")
                            .description("数据集已经可查询，但在当前时间范围内没有日志写入。")
                            .actionLabel("调整时间范围")
                            .actionRoute(null)
                            .build())
                    .build();
        }

        if (!hasColumnMapping(datasetContext.getFieldMapping(), "timestamp")) {
            warnings.add(DashboardWarningDTO.builder()
                    .scope("dataset")
                    .level("warning")
                    .message("当前数据集缺少 timestamp 字段映射，首页暂不支持时间范围统计。")
                    .build());
            return DashboardLogDatasetSnapshot.builder()
                    .capabilities(buildCapabilities(datasetContext))
                    .metricDrilldowns(buildMetricDrilldowns())
                    .emptyState(DashboardEmptyStateDTO.builder()
                            .title("当前数据集缺少时间字段映射")
                            .description("请先为该日志数据集配置 timestamp 字段映射，再查看趋势和统计指标。")
                            .actionLabel("前往组件库")
                            .actionRoute("/vector/components")
                            .build())
                    .build();
        }

        try {
            return DashboardLogDatasetSnapshot.builder()
                    .logKpis(buildLogKpis(datasetContext, request))
                    .capabilities(buildCapabilities(datasetContext))
                    .metricDrilldowns(buildMetricDrilldowns())
                    .logTrend(buildTrend(datasetContext, request))
                    .severityDistribution(buildSeverityDistribution(datasetContext, request))
                    .topHosts(buildRankList(datasetContext, request, "hostname", "Top 主机"))
                    .topApps(buildRankList(datasetContext, request, "appname", "Top 应用"))
                    .topErrorMessages(buildTopErrors(datasetContext, request))
                    .recentHighRiskLogs(buildRecentHighRiskLogs(datasetContext, request))
                    .build();
        } catch (Exception ex) {
            log.error("构建 Dashboard 数据集快照失败, datasourceId={}", datasetContext.getDatasourceId(), ex);
            warnings.add(DashboardWarningDTO.builder()
                    .scope("dataset")
                    .level("error")
                    .message("日志数据集聚合失败，首页已降级展示空状态。")
                    .build());
            return DashboardLogDatasetSnapshot.builder()
                    .capabilities(buildCapabilities(datasetContext))
                    .metricDrilldowns(buildMetricDrilldowns())
                    .emptyState(DashboardEmptyStateDTO.builder()
                            .title("当前数据集暂不可统计")
                            .description("日志聚合请求失败，请检查 ClickHouse 表结构和字段映射。")
                            .actionLabel("前往组件库")
                            .actionRoute("/vector/components")
                            .build())
                    .build();
        }
    }

    /**
     * 聚合 KPI 指标。
     */
    private DashboardLogKpisDTO buildLogKpis(DashboardDatasetContextDTO datasetContext, DashboardQueryRequest request) {
        String tableRef = tableRef(datasetContext);
        Map<String, String> fieldMapping = datasetContext.getFieldMapping();
        String timeField = column(fieldMapping, "timestamp");
        String severityField = hasColumnMapping(fieldMapping, "severity") ? column(fieldMapping, "severity") : null;
        String activeHostExpr = distinctCountExpr(fieldMapping, "hostname", "active_host_count");
        String activeAppExpr = distinctCountExpr(fieldMapping, "appname", "active_app_count");
        String storageEstimateExpr = storageEstimateExpr(fieldMapping);
        String errorCountExpr = severityCountExpr(severityField, "('ERROR','FATAL','CRITICAL')", "error_count");
        String criticalCountExpr = severityCountExpr(severityField, "('FATAL','CRITICAL')", "critical_count");

        String sql = """
                SELECT
                  count() AS total_logs,
                  %s,
                  %s,
                  %s,
                  %s,
                  %s
                FROM %s
                WHERE %s >= toDateTime('%s')
                  AND %s <= toDateTime('%s')
                """.formatted(
                errorCountExpr,
                criticalCountExpr,
                activeHostExpr,
                activeAppExpr,
                storageEstimateExpr,
                tableRef,
                sqlIdentifier(timeField),
                request.getStartTime(),
                sqlIdentifier(timeField),
                request.getEndTime()
        );

        Map<String, Object> row = extractSingleRow(dynamicLogQueryService.executeRawSQLJdbc(datasetContext.getDatasourceId(), sql));
        long totalLogs = asLong(row.get("total_logs"));
        long errorCount = asLong(row.get("error_count"));
        long criticalCount = asLong(row.get("critical_count"));

        return DashboardLogKpisDTO.builder()
                .totalLogs(totalLogs)
                .currentEps(calculateCurrentEps(datasetContext, request))
                .errorCount(errorCount)
                .criticalCount(criticalCount)
                .errorRate(totalLogs == 0 ? 0D : (errorCount * 100.0D / totalLogs))
                .activeHostCount(asLong(row.get("active_host_count")))
                .activeAppCount(asLong(row.get("active_app_count")))
                .storageVolume(toStorageVolume(asLong(row.get("estimated_bytes"))))
                .build();
    }

    /**
     * 仅按日志文本长度估算展示体积，不触碰物理存储统计。
     */
    private DashboardStorageVolumeDTO toStorageVolume(long estimatedBytes) {
        long normalizedBytes = Math.max(0L, estimatedBytes);
        if (normalizedBytes >= 1024L * 1024L * 1024L) {
            long value = Math.max(1L, normalizedBytes / (1024L * 1024L * 1024L));
            return DashboardStorageVolumeDTO.builder().value(value).unit("GB").displayValue(value + " GB").build();
        }
        if (normalizedBytes >= 1024L * 1024L) {
            long value = Math.max(1L, normalizedBytes / (1024L * 1024L));
            return DashboardStorageVolumeDTO.builder().value(value).unit("MB").displayValue(value + " MB").build();
        }
        if (normalizedBytes >= 1024L) {
            long value = Math.max(1L, normalizedBytes / 1024L);
            return DashboardStorageVolumeDTO.builder().value(value).unit("KB").displayValue(value + " KB").build();
        }
        return DashboardStorageVolumeDTO.builder().value(normalizedBytes).unit("B").displayValue(normalizedBytes + " B").build();
    }

    /**
     * 聚合趋势序列。
     */
    private LogTrendDTO buildTrend(DashboardDatasetContextDTO datasetContext, DashboardQueryRequest request) {
        if (!hasRequiredColumns(datasetContext.getFieldMapping(), "timestamp", "severity")) {
            return emptyTrend(request.getGranularity());
        }
        String granularity = resolveGranularity(request.getGranularity(), request.getStartTime(), request.getEndTime());
        String bucketFn = intervalFunction(granularity, column(datasetContext.getFieldMapping(), "timestamp"));
        String timeField = column(datasetContext.getFieldMapping(), "timestamp");
        String severityField = column(datasetContext.getFieldMapping(), "severity");
        String sql = """
                SELECT
                  %s AS time_bucket,
                  upperUTF8(ifNull(%s, 'UNKNOWN')) AS severity,
                  count() AS cnt
                FROM %s
                WHERE %s >= toDateTime('%s')
                  AND %s <= toDateTime('%s')
                GROUP BY time_bucket, severity
                ORDER BY time_bucket ASC, severity ASC
                """.formatted(
                bucketFn,
                sqlIdentifier(severityField),
                tableRef(datasetContext),
                sqlIdentifier(timeField),
                request.getStartTime(),
                sqlIdentifier(timeField),
                request.getEndTime()
        );
        List<Map<String, Object>> rows = extractRows(dynamicLogQueryService.executeRawSQLJdbc(datasetContext.getDatasourceId(), sql));
        return toTrendDto(rows, granularity);
    }

    /**
     * 聚合级别分布。
     */
    private DashboardDistributionDTO buildSeverityDistribution(DashboardDatasetContextDTO datasetContext, DashboardQueryRequest request) {
        if (!hasRequiredColumns(datasetContext.getFieldMapping(), "timestamp", "severity")) {
            return DashboardDistributionDTO.builder().items(List.of()).build();
        }
        String timeField = column(datasetContext.getFieldMapping(), "timestamp");
        String severityField = column(datasetContext.getFieldMapping(), "severity");
        String sql = """
                SELECT
                  upperUTF8(ifNull(%s, 'UNKNOWN')) AS severity,
                  count() AS cnt
                FROM %s
                WHERE %s >= toDateTime('%s')
                  AND %s <= toDateTime('%s')
                GROUP BY severity
                ORDER BY cnt DESC
                """.formatted(
                sqlIdentifier(severityField),
                tableRef(datasetContext),
                sqlIdentifier(timeField),
                request.getStartTime(),
                sqlIdentifier(timeField),
                request.getEndTime()
        );
        List<DashboardSeverityDistributionItemDTO> items = extractRows(dynamicLogQueryService.executeRawSQLJdbc(datasetContext.getDatasourceId(), sql)).stream()
                .map(row -> DashboardSeverityDistributionItemDTO.builder()
                        .severity(String.valueOf(row.get("severity")))
                        .count(asLong(row.get("cnt")))
                        .color(LEVEL_COLORS.getOrDefault(String.valueOf(row.get("severity")), "#909399"))
                        .build())
                .collect(Collectors.toList());
        return DashboardDistributionDTO.builder().items(items).build();
    }

    /**
     * 聚合通用 Top 列表。
     */
    private DashboardTopListDTO buildRankList(DashboardDatasetContextDTO datasetContext,
                                              DashboardQueryRequest request,
                                              String logicalField,
                                              String fallbackName) {
        if (!hasColumnMapping(datasetContext.getFieldMapping(), logicalField)) {
            return DashboardTopListDTO.builder().items(List.of()).build();
        }
        String timeField = column(datasetContext.getFieldMapping(), "timestamp");
        String actualField = column(datasetContext.getFieldMapping(), logicalField);
        String sql = """
                SELECT
                  ifNull(%s, '') AS name,
                  count() AS cnt
                FROM %s
                WHERE %s >= toDateTime('%s')
                  AND %s <= toDateTime('%s')
                  AND ifNull(%s, '') != ''
                GROUP BY name
                ORDER BY cnt DESC
                LIMIT 8
                """.formatted(
                sqlIdentifier(actualField),
                tableRef(datasetContext),
                sqlIdentifier(timeField),
                request.getStartTime(),
                sqlIdentifier(timeField),
                request.getEndTime(),
                sqlIdentifier(actualField)
        );
        List<DashboardListItemDTO> items = extractRows(dynamicLogQueryService.executeRawSQLJdbc(datasetContext.getDatasourceId(), sql)).stream()
                .map(row -> DashboardListItemDTO.builder()
                        .name(StringUtils.defaultIfBlank(String.valueOf(row.get("name")), fallbackName))
                        .count(asLong(row.get("cnt")))
                        .build())
                .collect(Collectors.toList());
        return DashboardTopListDTO.builder().items(items).build();
    }

    /**
     * 聚合高频错误消息。
     */
    private DashboardTopListDTO buildTopErrors(DashboardDatasetContextDTO datasetContext, DashboardQueryRequest request) {
        if (!hasRequiredColumns(datasetContext.getFieldMapping(), "timestamp", "severity")) {
            return DashboardTopListDTO.builder().items(List.of()).build();
        }
        String timeField = column(datasetContext.getFieldMapping(), "timestamp");
        String severityField = column(datasetContext.getFieldMapping(), "severity");
        String messageField = resolvePrimaryTextField(datasetContext.getFieldMapping());
        if (StringUtils.isBlank(messageField)) {
            return DashboardTopListDTO.builder().items(List.of()).build();
        }
        String sql = """
                SELECT
                  substring(ifNull(%s, ''), 1, 120) AS name,
                  count() AS cnt,
                  any(upperUTF8(ifNull(%s, 'UNKNOWN'))) AS severity_label
                FROM %s
                WHERE %s >= toDateTime('%s')
                  AND %s <= toDateTime('%s')
                  AND upperUTF8(ifNull(%s, 'UNKNOWN')) IN ('ERROR','FATAL','CRITICAL')
                  AND ifNull(%s, '') != ''
                GROUP BY name
                ORDER BY cnt DESC
                LIMIT 8
                """.formatted(
                sqlIdentifier(messageField),
                sqlIdentifier(severityField),
                tableRef(datasetContext),
                sqlIdentifier(timeField),
                request.getStartTime(),
                sqlIdentifier(timeField),
                request.getEndTime(),
                sqlIdentifier(severityField),
                sqlIdentifier(messageField)
        );
        List<DashboardListItemDTO> items = extractRows(dynamicLogQueryService.executeRawSQLJdbc(datasetContext.getDatasourceId(), sql)).stream()
                .map(row -> DashboardListItemDTO.builder()
                        .name(String.valueOf(row.get("name")))
                        .count(asLong(row.get("cnt")))
                        .meta(String.valueOf(row.get("severity_label")))
                        .build())
                .collect(Collectors.toList());
        return DashboardTopListDTO.builder().items(items).build();
    }

    /**
     * 聚合最近高风险日志。
     */
    private DashboardRecentLogsDTO buildRecentHighRiskLogs(DashboardDatasetContextDTO datasetContext, DashboardQueryRequest request) {
        Map<String, String> fieldMapping = datasetContext.getFieldMapping();
        if (!hasRequiredColumns(fieldMapping, "timestamp", "severity")) {
            return DashboardRecentLogsDTO.builder().items(List.of()).build();
        }
        String timeField = column(fieldMapping, "timestamp");
        String severityField = column(fieldMapping, "severity");
        String messageField = resolvePrimaryTextField(fieldMapping);
        if (StringUtils.isBlank(messageField)) {
            return DashboardRecentLogsDTO.builder().items(List.of()).build();
        }
        String rawField = resolveRawField(fieldMapping, messageField);
        String sql = """
                SELECT
                  toString(%s) AS log_time_text,
                  upperUTF8(ifNull(%s, 'UNKNOWN')) AS severity,
                  %s,
                  %s,
                  ifNull(%s, '') AS message,
                  ifNull(%s, '') AS raw
                FROM %s
                WHERE %s >= toDateTime('%s')
                  AND %s <= toDateTime('%s')
                  AND upperUTF8(ifNull(%s, 'UNKNOWN')) IN ('ERROR','FATAL','CRITICAL','WARN','WARNING')
                ORDER BY %s DESC
                LIMIT 20
                """.formatted(
                sqlIdentifier(timeField),
                sqlIdentifier(severityField),
                optionalStringSelect(fieldMapping, "hostname", "hostname"),
                optionalStringSelect(fieldMapping, "appname", "appname"),
                sqlIdentifier(messageField),
                sqlIdentifier(rawField),
                tableRef(datasetContext),
                sqlIdentifier(timeField),
                request.getStartTime(),
                sqlIdentifier(timeField),
                request.getEndTime(),
                sqlIdentifier(severityField),
                sqlIdentifier(timeField)
        );
        List<DashboardRecentLogItemDTO> items = new ArrayList<>();
        int[] index = {0};
        extractRows(dynamicLogQueryService.executeRawSQLJdbc(datasetContext.getDatasourceId(), sql)).forEach(row -> {
            index[0]++;
            items.add(DashboardRecentLogItemDTO.builder()
                    .id(datasetContext.getDatasourceId() + "-" + index[0])
                    .timestamp(String.valueOf(row.get("log_time_text")))
                    .severity(String.valueOf(row.get("severity")))
                    .hostname(String.valueOf(row.get("hostname")))
                    .appname(String.valueOf(row.get("appname")))
                    .message(String.valueOf(row.get("message")))
                    .raw(String.valueOf(row.get("raw")))
                    .build());
        });
        return DashboardRecentLogsDTO.builder().items(items).build();
    }

    private Double calculateCurrentEps(DashboardDatasetContextDTO datasetContext, DashboardQueryRequest request) {
        String timeField = column(datasetContext.getFieldMapping(), "timestamp");
        String sql = """
                SELECT count() AS total_logs
                FROM %s
                WHERE %s >= toDateTime('%s')
                  AND %s <= toDateTime('%s')
                """.formatted(
                tableRef(datasetContext),
                sqlIdentifier(timeField),
                request.getStartTime(),
                sqlIdentifier(timeField),
                request.getEndTime()
        );
        Map<String, Object> row = extractSingleRow(dynamicLogQueryService.executeRawSQLJdbc(datasetContext.getDatasourceId(), sql));
        long totalLogs = asLong(row.get("total_logs"));
        LocalDateTime start = LocalDateTime.parse(request.getStartTime(), FORMATTER);
        LocalDateTime end = LocalDateTime.parse(request.getEndTime(), FORMATTER);
        long seconds = Math.max(1L, java.time.Duration.between(start, end).getSeconds());
        return BigDecimal.valueOf(totalLogs * 1.0D / seconds)
                .setScale(2, java.math.RoundingMode.HALF_UP)
                .doubleValue();
    }

    private LogTrendDTO emptyTrend(String granularity) {
        return LogTrendDTO.builder()
                .granularity(StringUtils.defaultIfBlank(granularity, "auto"))
                .timestamps(List.of())
                .series(List.of())
                .totalCount(0L)
                .build();
    }

    /**
     * 为首页卡片生成稳定的能力识别结果。
     */
    private List<DashboardCapabilityDTO> buildCapabilities(DashboardDatasetContextDTO datasetContext) {
        Map<String, String> fieldMapping = datasetContext == null ? Map.of() : datasetContext.getFieldMapping();
        return List.of(
                capability("trend", hasRequiredColumns(fieldMapping, "timestamp", "severity"), "timestamp + severity", "severity_distribution"),
                capability("severity_distribution", hasRequiredColumns(fieldMapping, "timestamp", "severity"), "timestamp + severity", "trend"),
                capability("top_hosts", hasRequiredColumns(fieldMapping, "timestamp", "hostname"), "timestamp + hostname", "severity_distribution"),
                capability("top_apps", hasRequiredColumns(fieldMapping, "timestamp", "appname"), "timestamp + appname", "top_hosts")
        );
    }

    /**
     * 为核心指标卡提供固定下钻定义。
     */
    private List<DashboardMetricDrilldownDTO> buildMetricDrilldowns() {
        return List.of(
                DashboardMetricDrilldownDTO.builder()
                        .metricKey("total_logs")
                        .title("日志总量趋势")
                        .description("查看当前时间范围内的日志总量变化。")
                        .unit("count")
                        .build(),
                DashboardMetricDrilldownDTO.builder()
                        .metricKey("storage_volume")
                        .title("日志体积估算")
                        .description("按 raw 或 message 长度估算日志写入体积。")
                        .unit("bytes")
                        .build(),
                DashboardMetricDrilldownDTO.builder()
                        .metricKey("error_rate")
                        .title("错误率走势")
                        .description("查看 ERROR、FATAL、CRITICAL 日志占比变化。")
                        .unit("percent")
                        .build(),
                DashboardMetricDrilldownDTO.builder()
                        .metricKey("critical_count")
                        .title("严重日志趋势")
                        .description("查看 FATAL、CRITICAL 日志数量变化。")
                        .unit("count")
                        .build()
        );
    }

    /**
     * 缺少字段时返回 unsupported，同时给出稳定回退视图。
     */
    private DashboardCapabilityDTO capability(String key, boolean supported, String requiredField, String fallbackView) {
        return DashboardCapabilityDTO.builder()
                .key(key)
                .supported(supported)
                .reason(supported
                        ? requiredField + " field is available"
                        : requiredField + " field is missing from dataset mapping")
                .fallbackView(fallbackView)
                .build();
    }

    /**
     * 多个查询复用的必需字段判断，避免缺映射时继续拼接无效 SQL。
     */
    private boolean hasRequiredColumns(Map<String, String> fieldMapping, String... keys) {
        for (String key : keys) {
            if (!hasColumnMapping(fieldMapping, key)) {
                return false;
            }
        }
        return true;
    }

    private LogTrendDTO toTrendDto(List<Map<String, Object>> rows, String granularity) {
        Set<String> timestamps = new LinkedHashSet<>();
        Set<String> severities = new LinkedHashSet<>();
        Map<String, Map<String, Long>> matrix = new HashMap<>();

        for (Map<String, Object> row : rows) {
            String timeBucket = String.valueOf(row.get("time_bucket"));
            String severity = String.valueOf(row.get("severity"));
            long count = asLong(row.get("cnt"));
            timestamps.add(timeBucket);
            severities.add(severity);
            matrix.computeIfAbsent(severity, key -> new HashMap<>()).put(timeBucket, count);
        }

        List<String> timestampList = new ArrayList<>(timestamps);
        List<LogTrendDTO.LevelSeries> series = new ArrayList<>();
        long total = 0L;
        for (String severity : severities) {
            List<Long> values = new ArrayList<>();
            long levelTotal = 0L;
            for (String timestamp : timestampList) {
                long value = matrix.getOrDefault(severity, Map.of()).getOrDefault(timestamp, 0L);
                values.add(value);
                levelTotal += value;
            }
            series.add(LogTrendDTO.LevelSeries.builder()
                    .severity(severity)
                    .data(values)
                    .total(levelTotal)
                    .build());
            total += levelTotal;
        }

        return LogTrendDTO.builder()
                .granularity(granularity)
                .timestamps(timestampList)
                .series(series)
                .totalCount(total)
                .build();
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> extractRows(Object raw) {
        if (raw instanceof List<?> list) {
            return list.stream()
                    .filter(Map.class::isInstance)
                    .map(item -> (Map<String, Object>) item)
                    .collect(Collectors.toList());
        }
        return List.of();
    }

    private Map<String, Object> extractSingleRow(Object raw) {
        List<Map<String, Object>> rows = extractRows(raw);
        return CollectionUtils.isEmpty(rows) ? Map.of() : rows.get(0);
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

    private String tableRef(DashboardDatasetContextDTO datasetContext) {
        return sqlIdentifier(datasetContext.getDatabaseName()) + "." + sqlIdentifier(datasetContext.getTableName());
    }

    /**
     * 核心字段允许按约定回退，扩展字段缺失时由调用方决定是否降级。
     */
    private String column(Map<String, String> fieldMapping, String key) {
        return StringUtils.defaultIfBlank(fieldMapping == null ? null : fieldMapping.get(key), key);
    }

    /**
     * 判断某个逻辑字段是否存在有效映射，避免对不存在的列直接生成 SQL。
     */
    private boolean hasColumnMapping(Map<String, String> fieldMapping, String key) {
        return StringUtils.isNotBlank(fieldMapping == null ? null : fieldMapping.get(key));
    }

    /**
     * 可选维度字段缺失时返回常量 0，保证 KPI 聚合仍可执行。
     */
    private String distinctCountExpr(Map<String, String> fieldMapping, String key, String alias) {
        if (!hasColumnMapping(fieldMapping, key)) {
            return "toUInt64(0) AS " + alias;
        }
        return "uniqExact(ifNull(" + sqlIdentifier(column(fieldMapping, key)) + ", '')) AS " + alias;
    }

    /**
     * 缺少 severity 时将错误类统计降为 0，避免影响总量、主机数等其余 KPI。
     */
    private String severityCountExpr(String severityField, String values, String alias) {
        if (StringUtils.isBlank(severityField)) {
            return "toUInt64(0) AS " + alias;
        }
        return "countIf(upperUTF8(" + sqlIdentifier(severityField) + ") IN " + values + ") AS " + alias;
    }

    /**
     * 优先按 raw 字段长度估算，不存在时回退到 message。
     */
    private String storageEstimateExpr(Map<String, String> fieldMapping) {
        if (hasColumnMapping(fieldMapping, "raw")) {
            String rawColumn = sqlIdentifier(column(fieldMapping, "raw"));
            if (hasColumnMapping(fieldMapping, "message")) {
                return "sum(lengthUTF8(ifNull(" + rawColumn + ", ifNull(" + sqlIdentifier(column(fieldMapping, "message")) + ", '')))) AS estimated_bytes";
            }
            return "sum(lengthUTF8(ifNull(" + rawColumn + ", ''))) AS estimated_bytes";
        }
        if (hasColumnMapping(fieldMapping, "message")) {
            return "sum(lengthUTF8(ifNull(" + sqlIdentifier(column(fieldMapping, "message")) + ", ''))) AS estimated_bytes";
        }
        return "toUInt64(0) AS estimated_bytes";
    }

    /**
     * 可选字符串列缺失时用空串占位，避免最近日志列表因为扩展字段缺失而失败。
     */
    private String optionalStringSelect(Map<String, String> fieldMapping, String key, String alias) {
        if (!hasColumnMapping(fieldMapping, key)) {
            return "'' AS " + alias;
        }
        return "ifNull(" + sqlIdentifier(column(fieldMapping, key)) + ", '') AS " + alias;
    }

    /**
     * 文本类卡片优先使用 message，缺失时回退到 raw。
     */
    private String resolvePrimaryTextField(Map<String, String> fieldMapping) {
        if (hasColumnMapping(fieldMapping, "message")) {
            return column(fieldMapping, "message");
        }
        if (hasColumnMapping(fieldMapping, "raw")) {
            return column(fieldMapping, "raw");
        }
        return null;
    }

    /**
     * 最近日志卡片优先展示 raw，缺失时回退到已确认存在的文本字段。
     */
    private String resolveRawField(Map<String, String> fieldMapping, String fallbackField) {
        if (hasColumnMapping(fieldMapping, "raw")) {
            return column(fieldMapping, "raw");
        }
        return fallbackField;
    }

    private String sqlIdentifier(String value) {
        return "`" + StringUtils.defaultString(value).replace("`", "") + "`";
    }

    private String resolveGranularity(String granularity, String startTime, String endTime) {
        if (StringUtils.isNotBlank(granularity) && !"auto".equalsIgnoreCase(granularity)) {
            return granularity;
        }
        LocalDateTime start = LocalDateTime.parse(startTime, FORMATTER);
        LocalDateTime end = LocalDateTime.parse(endTime, FORMATTER);
        long hours = java.time.Duration.between(start, end).toHours();
        if (hours <= 1) {
            return "1m";
        }
        if (hours <= 6) {
            return "5m";
        }
        return "1h";
    }

    private String intervalFunction(String granularity, String timeField) {
        return switch (granularity) {
            case "1m" -> "toStartOfMinute(" + sqlIdentifier(timeField) + ")";
            case "5m" -> "toStartOfFiveMinutes(" + sqlIdentifier(timeField) + ")";
            case "1d" -> "toStartOfDay(" + sqlIdentifier(timeField) + ")";
            default -> "toStartOfHour(" + sqlIdentifier(timeField) + ")";
        };
    }
}
