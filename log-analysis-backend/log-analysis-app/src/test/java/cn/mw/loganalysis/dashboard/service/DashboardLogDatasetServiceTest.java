package cn.mw.loganalysis.dashboard.service;

import cn.mw.loganalysis.dashboard.dto.DashboardDatasetContextDTO;
import cn.mw.loganalysis.dashboard.dto.DashboardQueryRequest;
import cn.mw.loganalysis.dashboard.dto.DashboardWarningDTO;
import cn.mw.loganalysis.stats.service.DynamicLogQueryService;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class DashboardLogDatasetServiceTest {

    @Test
    void shouldExposeCapabilitiesAndMetricDrilldownsWhenDatasetContextIsNull() {
        DynamicLogQueryService queryService = mock(DynamicLogQueryService.class);
        DashboardLogDatasetService service = new DashboardLogDatasetService(queryService);
        DashboardQueryRequest request = DashboardQueryRequest.builder()
                .startTime("2026-05-31 00:00:00")
                .endTime("2026-05-31 23:59:59")
                .granularity("auto")
                .build();

        DashboardLogDatasetSnapshot snapshot = service.buildSnapshot(null, request, new ArrayList<>());

        assertThat(snapshot.getCapabilities()).isEmpty();
        assertThat(snapshot.getMetricDrilldowns()).isNotEmpty();
    }

    @Test
    void shouldBuildSnapshotWithTopErrorsQueryUsingNonConflictingSeverityAlias() {
        DynamicLogQueryService queryService = mock(DynamicLogQueryService.class);
        DashboardLogDatasetService service = new DashboardLogDatasetService(queryService);
        DashboardDatasetContextDTO datasetContext = DashboardDatasetContextDTO.builder()
                .datasourceId("sink-1")
                .datasourceName("syslog_logs")
                .databaseName("default")
                .tableName("syslog_logs")
                .hasData(true)
                .fieldMapping(Map.of(
                        "timestamp", "timestamp",
                        "severity", "severity",
                        "hostname", "hostname",
                        "appname", "appname",
                        "message", "message",
                        "raw", "raw"
                ))
                .build();
        DashboardQueryRequest request = DashboardQueryRequest.builder()
                .startTime("2026-05-31 00:00:00")
                .endTime("2026-05-31 23:59:59")
                .granularity("auto")
                .build();

        when(queryService.executeRawSQLJdbc(eq("sink-1"), eq("""
                SELECT
                  substring(ifNull(`message`, ''), 1, 120) AS name,
                  count() AS cnt,
                  any(upperUTF8(ifNull(`severity`, 'UNKNOWN'))) AS severity_label
                FROM `default`.`syslog_logs`
                WHERE `timestamp` >= toDateTime('2026-05-31 00:00:00')
                  AND `timestamp` <= toDateTime('2026-05-31 23:59:59')
                  AND upperUTF8(ifNull(`severity`, 'UNKNOWN')) IN ('ERROR','FATAL','CRITICAL')
                  AND ifNull(`message`, '') != ''
                GROUP BY name
                ORDER BY cnt DESC
                LIMIT 8
                """))).thenReturn(List.of(Map.of("name", "NullPointerException", "cnt", 5L, "severity_label", "ERROR")));

        when(queryService.executeRawSQLJdbc(eq("sink-1"), eq("""
                SELECT
                  count() AS total_logs,
                  countIf(upperUTF8(`severity`) IN ('ERROR','FATAL','CRITICAL')) AS error_count,
                  countIf(upperUTF8(`severity`) IN ('FATAL','CRITICAL')) AS critical_count,
                  uniqExact(ifNull(`hostname`, '')) AS active_host_count,
                  uniqExact(ifNull(`appname`, '')) AS active_app_count,
                  sum(lengthUTF8(ifNull(`raw`, ifNull(`message`, '')))) AS estimated_bytes
                FROM `default`.`syslog_logs`
                WHERE `timestamp` >= toDateTime('2026-05-31 00:00:00')
                  AND `timestamp` <= toDateTime('2026-05-31 23:59:59')
                """))).thenReturn(List.of(Map.of(
                "total_logs", 100L,
                "error_count", 10L,
                "critical_count", 2L,
                "active_host_count", 3L,
                "active_app_count", 4L,
                "estimated_bytes", 4096L
        )));
        when(queryService.executeRawSQLJdbc(eq("sink-1"), eq("""
                SELECT count() AS total_logs
                FROM `default`.`syslog_logs`
                WHERE `timestamp` >= toDateTime('2026-05-31 00:00:00')
                  AND `timestamp` <= toDateTime('2026-05-31 23:59:59')
                """))).thenReturn(List.of(Map.of("total_logs", 100L)));
        when(queryService.executeRawSQLJdbc(eq("sink-1"), eq("""
                SELECT
                  toStartOfHour(`timestamp`) AS time_bucket,
                  upperUTF8(ifNull(`severity`, 'UNKNOWN')) AS severity,
                  count() AS cnt
                FROM `default`.`syslog_logs`
                WHERE `timestamp` >= toDateTime('2026-05-31 00:00:00')
                  AND `timestamp` <= toDateTime('2026-05-31 23:59:59')
                GROUP BY time_bucket, severity
                ORDER BY time_bucket ASC, severity ASC
                """))).thenReturn(List.of());
        when(queryService.executeRawSQLJdbc(eq("sink-1"), eq("""
                SELECT
                  upperUTF8(ifNull(`severity`, 'UNKNOWN')) AS severity,
                  count() AS cnt
                FROM `default`.`syslog_logs`
                WHERE `timestamp` >= toDateTime('2026-05-31 00:00:00')
                  AND `timestamp` <= toDateTime('2026-05-31 23:59:59')
                GROUP BY severity
                ORDER BY cnt DESC
                """))).thenReturn(List.of());
        when(queryService.executeRawSQLJdbc(eq("sink-1"), eq("""
                SELECT
                  ifNull(`hostname`, '') AS name,
                  count() AS cnt
                FROM `default`.`syslog_logs`
                WHERE `timestamp` >= toDateTime('2026-05-31 00:00:00')
                  AND `timestamp` <= toDateTime('2026-05-31 23:59:59')
                  AND ifNull(`hostname`, '') != ''
                GROUP BY name
                ORDER BY cnt DESC
                LIMIT 8
                """))).thenReturn(List.of());
        when(queryService.executeRawSQLJdbc(eq("sink-1"), eq("""
                SELECT
                  ifNull(`appname`, '') AS name,
                  count() AS cnt
                FROM `default`.`syslog_logs`
                WHERE `timestamp` >= toDateTime('2026-05-31 00:00:00')
                  AND `timestamp` <= toDateTime('2026-05-31 23:59:59')
                  AND ifNull(`appname`, '') != ''
                GROUP BY name
                ORDER BY cnt DESC
                LIMIT 8
                """))).thenReturn(List.of());
        when(queryService.executeRawSQLJdbc(eq("sink-1"), eq("""
                SELECT
                  toString(`timestamp`) AS log_time_text,
                  upperUTF8(ifNull(`severity`, 'UNKNOWN')) AS severity,
                  ifNull(`hostname`, '') AS hostname,
                  ifNull(`appname`, '') AS appname,
                  ifNull(`message`, '') AS message,
                  ifNull(`raw`, '') AS raw
                FROM `default`.`syslog_logs`
                WHERE `timestamp` >= toDateTime('2026-05-31 00:00:00')
                  AND `timestamp` <= toDateTime('2026-05-31 23:59:59')
                  AND upperUTF8(ifNull(`severity`, 'UNKNOWN')) IN ('ERROR','FATAL','CRITICAL','WARN','WARNING')
                ORDER BY `timestamp` DESC
                LIMIT 20
                """))).thenReturn(List.of(Map.of(
                "log_time_text", "2026-05-31 12:00:00.000",
                "severity", "ERROR",
                "hostname", "host-1",
                "appname", "app-1",
                "message", "boom",
                "raw", "raw-line"
        )));

        DashboardLogDatasetSnapshot snapshot = service.buildSnapshot(datasetContext, request, new ArrayList<DashboardWarningDTO>());

        assertThat(snapshot.getEmptyState()).isNull();
        assertThat(snapshot.getLogKpis()).isNotNull();
        assertThat(snapshot.getLogKpis().getStorageVolume()).isNotNull();
        assertThat(snapshot.getCapabilities()).isNotEmpty();
        assertThat(snapshot.getMetricDrilldowns()).isNotEmpty();
        assertThat(snapshot.getTopErrorMessages()).isNotNull();
        assertThat(snapshot.getTopErrorMessages().getItems()).hasSize(1);
        assertThat(snapshot.getTopErrorMessages().getItems().get(0).getMeta()).isEqualTo("ERROR");
        assertThat(snapshot.getRecentHighRiskLogs()).isNotNull();
        assertThat(snapshot.getRecentHighRiskLogs().getItems()).hasSize(1);
        assertThat(snapshot.getRecentHighRiskLogs().getItems().get(0).getTimestamp()).isEqualTo("2026-05-31 12:00:00.000");
        verify(queryService).executeRawSQLJdbc(eq("sink-1"), eq("""
                SELECT
                  substring(ifNull(`message`, ''), 1, 120) AS name,
                  count() AS cnt,
                  any(upperUTF8(ifNull(`severity`, 'UNKNOWN'))) AS severity_label
                FROM `default`.`syslog_logs`
                WHERE `timestamp` >= toDateTime('2026-05-31 00:00:00')
                  AND `timestamp` <= toDateTime('2026-05-31 23:59:59')
                  AND upperUTF8(ifNull(`severity`, 'UNKNOWN')) IN ('ERROR','FATAL','CRITICAL')
                  AND ifNull(`message`, '') != ''
                GROUP BY name
                ORDER BY cnt DESC
                LIMIT 8
                """));
        verify(queryService).executeRawSQLJdbc(eq("sink-1"), eq("""
                SELECT
                  toString(`timestamp`) AS log_time_text,
                  upperUTF8(ifNull(`severity`, 'UNKNOWN')) AS severity,
                  ifNull(`hostname`, '') AS hostname,
                  ifNull(`appname`, '') AS appname,
                  ifNull(`message`, '') AS message,
                  ifNull(`raw`, '') AS raw
                FROM `default`.`syslog_logs`
                WHERE `timestamp` >= toDateTime('2026-05-31 00:00:00')
                  AND `timestamp` <= toDateTime('2026-05-31 23:59:59')
                  AND upperUTF8(ifNull(`severity`, 'UNKNOWN')) IN ('ERROR','FATAL','CRITICAL','WARN','WARNING')
                ORDER BY `timestamp` DESC
                LIMIT 20
                """));
    }

    @Test
    void shouldMarkHostAndAppCapabilitiesUnsupportedWhenMappingsMissing() {
        DynamicLogQueryService queryService = mock(DynamicLogQueryService.class);
        DashboardLogDatasetService service = new DashboardLogDatasetService(queryService);
        DashboardDatasetContextDTO datasetContext = DashboardDatasetContextDTO.builder()
                .datasourceId("sink-2")
                .datasourceName("app_logs")
                .databaseName("default")
                .tableName("app_logs")
                .hasData(true)
                .fieldMapping(Map.of(
                        "timestamp", "timestamp",
                        "severity", "severity",
                        "message", "message"
                ))
                .build();
        DashboardQueryRequest request = DashboardQueryRequest.builder()
                .startTime("2026-05-31 00:00:00")
                .endTime("2026-05-31 23:59:59")
                .granularity("auto")
                .build();

        when(queryService.executeRawSQLJdbc(eq("sink-2"), anyString())).thenReturn(List.of());

        DashboardLogDatasetSnapshot snapshot = service.buildSnapshot(datasetContext, request, new ArrayList<>());

        assertThat(snapshot.getEmptyState()).isNull();
        assertThat(snapshot.getCapabilities()).isNotNull();
        assertThat(snapshot.getCapabilities())
                .extracting("key", "supported", "fallbackView")
                .contains(
                        org.assertj.core.groups.Tuple.tuple("top_hosts", false, "severity_distribution"),
                        org.assertj.core.groups.Tuple.tuple("top_apps", false, "top_hosts")
                );
        assertThat(snapshot.getCapabilities().stream()
                .filter(item -> "top_hosts".equals(item.getKey()))
                .findFirst()
                .orElseThrow()
                .getReason()).contains("hostname");
        assertThat(snapshot.getCapabilities().stream()
                .filter(item -> "top_apps".equals(item.getKey()))
                .findFirst()
                .orElseThrow()
                .getReason()).contains("appname");
    }

    @Test
    void shouldDegradeSeverityDependentPanelsWhenSeverityMappingMissing() {
        DynamicLogQueryService queryService = mock(DynamicLogQueryService.class);
        DashboardLogDatasetService service = new DashboardLogDatasetService(queryService);
        DashboardDatasetContextDTO datasetContext = DashboardDatasetContextDTO.builder()
                .datasourceId("sink-4")
                .datasourceName("partial_logs")
                .databaseName("default")
                .tableName("partial_logs")
                .hasData(true)
                .fieldMapping(Map.of(
                        "timestamp", "event_time",
                        "hostname", "host_name",
                        "appname", "service_name",
                        "message", "message_body"
                ))
                .build();
        DashboardQueryRequest request = DashboardQueryRequest.builder()
                .startTime("2026-05-31 00:00:00")
                .endTime("2026-05-31 23:59:59")
                .granularity("auto")
                .build();

        when(queryService.executeRawSQLJdbc(eq("sink-4"), eq("""
                SELECT
                  count() AS total_logs,
                  toUInt64(0) AS error_count,
                  toUInt64(0) AS critical_count,
                  uniqExact(ifNull(`host_name`, '')) AS active_host_count,
                  uniqExact(ifNull(`service_name`, '')) AS active_app_count,
                  sum(lengthUTF8(ifNull(`message_body`, ''))) AS estimated_bytes
                FROM `default`.`partial_logs`
                WHERE `event_time` >= toDateTime('2026-05-31 00:00:00')
                  AND `event_time` <= toDateTime('2026-05-31 23:59:59')
                """))).thenReturn(List.of(Map.of(
                "total_logs", 60L,
                "error_count", 0L,
                "critical_count", 0L,
                "active_host_count", 2L,
                "active_app_count", 2L,
                "estimated_bytes", 1024L
        )));
        when(queryService.executeRawSQLJdbc(eq("sink-4"), eq("""
                SELECT count() AS total_logs
                FROM `default`.`partial_logs`
                WHERE `event_time` >= toDateTime('2026-05-31 00:00:00')
                  AND `event_time` <= toDateTime('2026-05-31 23:59:59')
                """))).thenReturn(List.of(Map.of("total_logs", 60L)));
        when(queryService.executeRawSQLJdbc(eq("sink-4"), eq("""
                SELECT
                  ifNull(`host_name`, '') AS name,
                  count() AS cnt
                FROM `default`.`partial_logs`
                WHERE `event_time` >= toDateTime('2026-05-31 00:00:00')
                  AND `event_time` <= toDateTime('2026-05-31 23:59:59')
                  AND ifNull(`host_name`, '') != ''
                GROUP BY name
                ORDER BY cnt DESC
                LIMIT 8
                """))).thenReturn(List.of());
        when(queryService.executeRawSQLJdbc(eq("sink-4"), eq("""
                SELECT
                  ifNull(`service_name`, '') AS name,
                  count() AS cnt
                FROM `default`.`partial_logs`
                WHERE `event_time` >= toDateTime('2026-05-31 00:00:00')
                  AND `event_time` <= toDateTime('2026-05-31 23:59:59')
                  AND ifNull(`service_name`, '') != ''
                GROUP BY name
                ORDER BY cnt DESC
                LIMIT 8
                """))).thenReturn(List.of());

        DashboardLogDatasetSnapshot snapshot = service.buildSnapshot(datasetContext, request, new ArrayList<>());

        assertThat(snapshot.getEmptyState()).isNull();
        assertThat(snapshot.getLogKpis()).isNotNull();
        assertThat(snapshot.getLogKpis().getErrorCount()).isZero();
        assertThat(snapshot.getCapabilities())
                .extracting("key", "supported")
                .contains(
                        org.assertj.core.groups.Tuple.tuple("trend", false),
                        org.assertj.core.groups.Tuple.tuple("severity_distribution", false)
                );
        assertThat(snapshot.getLogTrend().getTimestamps()).isEmpty();
        assertThat(snapshot.getSeverityDistribution().getItems()).isEmpty();
        assertThat(snapshot.getTopErrorMessages().getItems()).isEmpty();
        assertThat(snapshot.getRecentHighRiskLogs().getItems()).isEmpty();
        verify(queryService, never()).executeRawSQLJdbc(eq("sink-4"), contains("GROUP BY time_bucket, severity"));
        verify(queryService, never()).executeRawSQLJdbc(eq("sink-4"), contains("GROUP BY severity"));
        verify(queryService, never()).executeRawSQLJdbc(eq("sink-4"), contains("severity_label"));
        verify(queryService, never()).executeRawSQLJdbc(eq("sink-4"), contains("ORDER BY `event_time` DESC"));
    }

    @Test
    void shouldFallbackToRawFieldWhenMessageMappingMissing() {
        DynamicLogQueryService queryService = mock(DynamicLogQueryService.class);
        DashboardLogDatasetService service = new DashboardLogDatasetService(queryService);
        DashboardDatasetContextDTO datasetContext = DashboardDatasetContextDTO.builder()
                .datasourceId("sink-5")
                .datasourceName("raw_logs")
                .databaseName("default")
                .tableName("raw_logs")
                .hasData(true)
                .fieldMapping(Map.of(
                        "timestamp", "log_time",
                        "severity", "level",
                        "raw", "raw_content"
                ))
                .build();
        DashboardQueryRequest request = DashboardQueryRequest.builder()
                .startTime("2026-05-31 00:00:00")
                .endTime("2026-05-31 23:59:59")
                .granularity("auto")
                .build();

        when(queryService.executeRawSQLJdbc(eq("sink-5"), anyString())).thenAnswer(invocation -> {
            String sql = invocation.getArgument(1, String.class);
            if (sql.contains("substring(ifNull(`raw_content`, ''), 1, 120) AS name")) {
                return List.of(Map.of("name", "stacktrace line", "cnt", 3L, "severity_label", "ERROR"));
            }
            if (sql.contains("toString(`log_time`) AS log_time_text")) {
                return List.of(Map.of(
                        "log_time_text", "2026-05-31 08:00:00",
                        "severity", "WARN",
                        "hostname", "",
                        "appname", "",
                        "message", "stacktrace line",
                        "raw", "stacktrace line"
                ));
            }
            if (sql.contains("count() AS total_logs")) {
                return List.of(Map.of(
                        "total_logs", 8L,
                        "error_count", 2L,
                        "critical_count", 0L,
                        "active_host_count", 0L,
                        "active_app_count", 0L,
                        "estimated_bytes", 512L
                ));
            }
            return List.of();
        });

        DashboardLogDatasetSnapshot snapshot = service.buildSnapshot(datasetContext, request, new ArrayList<>());

        assertThat(snapshot.getTopErrorMessages().getItems()).hasSize(1);
        assertThat(snapshot.getRecentHighRiskLogs().getItems()).hasSize(1);
        assertThat(snapshot.getRecentHighRiskLogs().getItems().get(0).getMessage()).isEqualTo("stacktrace line");
        assertThat(snapshot.getRecentHighRiskLogs().getItems().get(0).getRaw()).isEqualTo("stacktrace line");
    }

    @Test
    void shouldFallbackToMessageFieldWhenRawMappingMissing() {
        DynamicLogQueryService queryService = mock(DynamicLogQueryService.class);
        DashboardLogDatasetService service = new DashboardLogDatasetService(queryService);
        DashboardDatasetContextDTO datasetContext = DashboardDatasetContextDTO.builder()
                .datasourceId("sink-6")
                .datasourceName("message_logs")
                .databaseName("default")
                .tableName("message_logs")
                .hasData(true)
                .fieldMapping(Map.of(
                        "timestamp", "log_time",
                        "severity", "level",
                        "message", "message_text"
                ))
                .build();
        DashboardQueryRequest request = DashboardQueryRequest.builder()
                .startTime("2026-05-31 00:00:00")
                .endTime("2026-05-31 23:59:59")
                .granularity("auto")
                .build();

        when(queryService.executeRawSQLJdbc(eq("sink-6"), anyString())).thenAnswer(invocation -> {
            String sql = invocation.getArgument(1, String.class);
            if (sql.contains("toString(`log_time`) AS log_time_text")) {
                return List.of(Map.of(
                        "log_time_text", "2026-05-31 09:00:00",
                        "severity", "ERROR",
                        "hostname", "",
                        "appname", "",
                        "message", "message only",
                        "raw", "message only"
                ));
            }
            if (sql.contains("substring(ifNull(`message_text`, ''), 1, 120) AS name")) {
                return List.of(Map.of("name", "message only", "cnt", 1L, "severity_label", "ERROR"));
            }
            if (sql.contains("count() AS total_logs")) {
                return List.of(Map.of(
                        "total_logs", 5L,
                        "error_count", 1L,
                        "critical_count", 0L,
                        "active_host_count", 0L,
                        "active_app_count", 0L,
                        "estimated_bytes", 128L
                ));
            }
            return List.of();
        });

        DashboardLogDatasetSnapshot snapshot = service.buildSnapshot(datasetContext, request, new ArrayList<>());

        assertThat(snapshot.getTopErrorMessages().getItems()).hasSize(1);
        assertThat(snapshot.getRecentHighRiskLogs().getItems()).hasSize(1);
        assertThat(snapshot.getRecentHighRiskLogs().getItems().get(0).getMessage()).isEqualTo("message only");
        assertThat(snapshot.getRecentHighRiskLogs().getItems().get(0).getRaw()).isEqualTo("message only");
    }

    @Test
    void shouldReturnEmptyTextPanelsWhenMessageAndRawMappingsMissing() {
        DynamicLogQueryService queryService = mock(DynamicLogQueryService.class);
        DashboardLogDatasetService service = new DashboardLogDatasetService(queryService);
        DashboardDatasetContextDTO datasetContext = DashboardDatasetContextDTO.builder()
                .datasourceId("sink-7")
                .datasourceName("thin_logs")
                .databaseName("default")
                .tableName("thin_logs")
                .hasData(true)
                .fieldMapping(Map.of(
                        "timestamp", "log_time",
                        "severity", "level"
                ))
                .build();
        DashboardQueryRequest request = DashboardQueryRequest.builder()
                .startTime("2026-05-31 00:00:00")
                .endTime("2026-05-31 23:59:59")
                .granularity("auto")
                .build();

        when(queryService.executeRawSQLJdbc(eq("sink-7"), anyString())).thenReturn(List.of());

        DashboardLogDatasetSnapshot snapshot = service.buildSnapshot(datasetContext, request, new ArrayList<>());

        assertThat(snapshot.getTopErrorMessages().getItems()).isEmpty();
        assertThat(snapshot.getRecentHighRiskLogs().getItems()).isEmpty();
        verify(queryService, never()).executeRawSQLJdbc(eq("sink-7"), contains("severity_label"));
        verify(queryService, never()).executeRawSQLJdbc(eq("sink-7"), contains("AS raw"));
    }

    @Test
    void shouldReturnEmptyStateWhenTimestampMappingMissing() {
        DynamicLogQueryService queryService = mock(DynamicLogQueryService.class);
        DashboardLogDatasetService service = new DashboardLogDatasetService(queryService);
        DashboardDatasetContextDTO datasetContext = DashboardDatasetContextDTO.builder()
                .datasourceId("sink-8")
                .datasourceName("broken_logs")
                .databaseName("default")
                .tableName("broken_logs")
                .hasData(true)
                .fieldMapping(Map.of(
                        "severity", "level",
                        "message", "message"
                ))
                .build();
        DashboardQueryRequest request = DashboardQueryRequest.builder()
                .startTime("2026-05-31 00:00:00")
                .endTime("2026-05-31 23:59:59")
                .granularity("auto")
                .build();
        List<DashboardWarningDTO> warnings = new ArrayList<>();

        DashboardLogDatasetSnapshot snapshot = service.buildSnapshot(datasetContext, request, warnings);

        assertThat(snapshot.getEmptyState()).isNotNull();
        assertThat(snapshot.getEmptyState().getTitle()).contains("时间字段");
        assertThat(snapshot.getCapabilities())
                .extracting("supported")
                .containsOnly(false);
        assertThat(warnings).hasSize(1);
        assertThat(warnings.get(0).getMessage()).contains("timestamp");
        verifyNoInteractions(queryService);
    }
}
