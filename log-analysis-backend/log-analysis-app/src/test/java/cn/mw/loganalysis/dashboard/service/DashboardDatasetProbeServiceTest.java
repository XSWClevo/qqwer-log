package cn.mw.loganalysis.dashboard.service;

import cn.mw.loganalysis.dashboard.dto.DashboardDatasetCandidateDTO;
import cn.mw.loganalysis.stats.service.DynamicLogQueryService;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DashboardDatasetProbeServiceTest {

    @Test
    void shouldAcceptMillisecondTimestampFromClickHouseProbe() {
        DynamicLogQueryService queryService = mock(DynamicLogQueryService.class);
        DashboardDatasetProbeService service = new DashboardDatasetProbeService(queryService);
        DashboardDatasetCandidateDTO candidate = DashboardDatasetCandidateDTO.builder()
                .datasourceId("sink-1")
                .datasourceName("syslog_logs")
                .databaseName("default")
                .tableName("syslog_logs")
                .fieldMapping(Map.of(
                        "timestamp", "timestamp",
                        "severity", "severity",
                        "message", "message"
                ))
                .build();

        when(queryService.executeRawSQLJdbc(eq("sink-1"), anyString()))
                .thenAnswer(invocation -> {
                    String sql = invocation.getArgument(1, String.class);
                    if (sql.contains("FROM system.columns")) {
                        return List.of(
                                Map.of("name", "timestamp"),
                                Map.of("name", "severity"),
                                Map.of("name", "message"),
                                Map.of("name", "raw")
                        );
                    }
                    return List.of(Map.of(
                            "total_rows", 10,
                            "latest_log_time", "2026-05-31 10:00:00.123"
                    ));
                });

        DashboardDatasetProbeResult result = service.probeCandidate(candidate);

        assertThat(result.isTableExists()).isTrue();
        assertThat(result.isHasCoreFields()).isTrue();
        assertThat(result.getTotalRows()).isEqualTo(10L);
        assertThat(result.getLatestLogTime()).isEqualTo(LocalDateTime.of(2026, 5, 31, 10, 0, 0, 123_000_000));
        verify(queryService).executeRawSQLJdbc(eq("sink-1"), eq("""
                SELECT
                  count() AS total_rows,
                  max(`timestamp`) AS latest_log_time
                FROM `default`.`syslog_logs`
                LIMIT 1
                """));
    }

    @Test
    void shouldAcceptMicrosecondTimestampAndResolveLevelAsSeverity() {
        DynamicLogQueryService queryService = mock(DynamicLogQueryService.class);
        DashboardDatasetProbeService service = new DashboardDatasetProbeService(queryService);
        DashboardDatasetCandidateDTO candidate = DashboardDatasetCandidateDTO.builder()
                .datasourceId("sink-2")
                .datasourceName("regex_logs")
                .databaseName("default")
                .tableName("regex_logs")
                .fieldMapping(Map.of(
                        "timestamp", "timestamp",
                        "severity", "severity",
                        "message", "message"
                ))
                .build();

        when(queryService.executeRawSQLJdbc(eq("sink-2"), anyString()))
                .thenAnswer(invocation -> {
                    String sql = invocation.getArgument(1, String.class);
                    if (sql.contains("FROM system.columns")) {
                        return List.of(
                                Map.of("name", "timestamp"),
                                Map.of("name", "level"),
                                Map.of("name", "message"),
                                Map.of("name", "raw"),
                                Map.of("name", "thread")
                        );
                    }
                    return List.of(Map.of(
                            "total_rows", 25,
                            "latest_log_time", "2026-05-31 10:00:00.123456"
                    ));
                });

        DashboardDatasetProbeResult result = service.probeCandidate(candidate);

        assertThat(result.isTableExists()).isTrue();
        assertThat(result.isHasCoreFields()).isTrue();
        assertThat(result.getTotalRows()).isEqualTo(25L);
        assertThat(result.getLatestLogTime()).isEqualTo(LocalDateTime.of(2026, 5, 31, 10, 0, 0, 123_456_000));
        assertThat(result.getCandidate().getFieldMapping())
                .containsEntry("severity", "level")
                .containsEntry("timestamp", "timestamp")
                .containsEntry("message", "message")
                .containsEntry("raw", "raw");
    }

    @Test
    void shouldMarkCandidateInvalidWhenTimestampOrMessageColumnMissing() {
        DynamicLogQueryService queryService = mock(DynamicLogQueryService.class);
        DashboardDatasetProbeService service = new DashboardDatasetProbeService(queryService);
        DashboardDatasetCandidateDTO candidate = DashboardDatasetCandidateDTO.builder()
                .datasourceId("sink-3")
                .datasourceName("broken_logs")
                .databaseName("default")
                .tableName("broken_logs")
                .fieldMapping(Map.of(
                        "timestamp", "timestamp",
                        "severity", "severity",
                        "message", "message"
                ))
                .build();

        when(queryService.executeRawSQLJdbc(eq("sink-3"), anyString()))
                .thenReturn(List.of(Map.of("name", "level")));

        DashboardDatasetProbeResult result = service.probeCandidate(candidate);

        assertThat(result.isTableExists()).isTrue();
        assertThat(result.isHasCoreFields()).isFalse();
        assertThat(result.getTotalRows()).isZero();
        assertThat(result.getLatestLogTime()).isNull();
    }
}
