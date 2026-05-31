package cn.mw.loganalysis.dashboard.service;

import cn.mw.loganalysis.dashboard.dto.DashboardDatasetContextDTO;
import cn.mw.loganalysis.dashboard.dto.DashboardQueryRequest;
import cn.mw.loganalysis.stats.service.DynamicLogQueryService;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DashboardMetricDrilldownTest {

    @Test
    void shouldExposeRequiredMetricDrilldownKeysAndEstimatedStorageVolume() {
        DynamicLogQueryService queryService = mock(DynamicLogQueryService.class);
        DashboardLogDatasetService service = new DashboardLogDatasetService(queryService);
        DashboardDatasetContextDTO datasetContext = DashboardDatasetContextDTO.builder()
                .datasourceId("sink-3")
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

        when(queryService.executeRawSQLJdbc(eq("sink-3"), anyString())).thenAnswer(invocation -> {
            String sql = invocation.getArgument(1, String.class);
            if (sql.contains("sum(lengthUTF8(ifNull(`raw`, ifNull(`message`, '')))) AS estimated_bytes")) {
                return List.of(Map.of(
                        "total_logs", 120L,
                        "error_count", 24L,
                        "critical_count", 6L,
                        "active_host_count", 4L,
                        "active_app_count", 3L,
                        "estimated_bytes", 2048L
                ));
            }
            if (sql.contains("SELECT count() AS total_logs")) {
                return List.of(Map.of("total_logs", 120L));
            }
            return List.of();
        });

        DashboardLogDatasetSnapshot snapshot = service.buildSnapshot(datasetContext, request, new ArrayList<>());

        assertThat(snapshot.getMetricDrilldowns()).isNotNull();
        assertThat(snapshot.getMetricDrilldowns())
                .extracting("metricKey")
                .containsExactly("total_logs", "storage_volume", "error_rate", "critical_count");
        assertThat(snapshot.getLogKpis()).isNotNull();
        assertThat(snapshot.getLogKpis().getStorageVolume()).isNotNull();
        assertThat(snapshot.getLogKpis().getStorageVolume().getValue()).isPositive();
        assertThat(snapshot.getLogKpis().getStorageVolume().getDisplayValue()).isNotBlank();
    }
}
