package cn.mw.loganalysis.dashboard.service;

import cn.mw.loganalysis.dashboard.dto.DashboardDatasetCandidateDTO;
import cn.mw.loganalysis.dashboard.dto.DashboardDatasetContextDTO;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class DashboardDatasetSelectorTest {

    @Test
    void shouldPreferDatasetWithRecentData() {
        LocalDateTime now = LocalDateTime.of(2026, 5, 29, 12, 0, 0);
        DashboardDatasetSelector selector = new DashboardDatasetSelector();

        DashboardDatasetProbeResult staleDataset = probe(
                candidate("sink-stale", "stale_logs"),
                true,
                true,
                20L,
                now.minusDays(3)
        );
        DashboardDatasetProbeResult freshDataset = probe(
                candidate("sink-fresh", "fresh_logs"),
                true,
                true,
                120L,
                now.minusHours(1)
        );

        DashboardDatasetContextDTO selected = selector.selectDefault(List.of(staleDataset, freshDataset), now);

        assertThat(selected).isNotNull();
        assertThat(selected.getDatasourceId()).isEqualTo("sink-fresh");
        assertThat(selected.getTableName()).isEqualTo("fresh_logs");
        assertThat(selected.getHasData()).isTrue();
        assertThat(selected.getStatus()).isEqualTo("READY");
    }

    @Test
    void shouldFallbackToExistingDatasetWithoutRecentDataWhenNoFreshDatasetExists() {
        LocalDateTime now = LocalDateTime.of(2026, 5, 29, 12, 0, 0);
        DashboardDatasetSelector selector = new DashboardDatasetSelector();

        DashboardDatasetProbeResult missingCoreFields = probe(
                candidate("sink-invalid", "invalid_logs"),
                true,
                false,
                10L,
                now.minusHours(2)
        );
        DashboardDatasetProbeResult emptyButUsable = probe(
                candidate("sink-empty", "empty_logs"),
                true,
                true,
                0L,
                null
        );

        DashboardDatasetContextDTO selected = selector.selectDefault(List.of(missingCoreFields, emptyButUsable), now);

        assertThat(selected).isNotNull();
        assertThat(selected.getDatasourceId()).isEqualTo("sink-empty");
        assertThat(selected.getTableName()).isEqualTo("empty_logs");
        assertThat(selected.getHasData()).isFalse();
        assertThat(selected.getStatus()).isEqualTo("NO_DATA");
    }

    @Test
    void shouldPreferRealDatasetOverTemplateDataset() {
        LocalDateTime now = LocalDateTime.of(2026, 5, 31, 12, 0, 0);
        DashboardDatasetSelector selector = new DashboardDatasetSelector();

        DashboardDatasetProbeResult templateDataset = probe(
                candidate("template-sink-clickhouse", "syslog"),
                true,
                true,
                0L,
                LocalDateTime.of(1970, 1, 1, 0, 0, 0)
        );
        DashboardDatasetProbeResult realDataset = probe(
                candidate("syslog-sink", "syslog_logs"),
                true,
                true,
                120L,
                now.minusMinutes(5)
        );

        DashboardDatasetContextDTO selected = selector.selectDefault(List.of(templateDataset, realDataset), now);

        assertThat(selected).isNotNull();
        assertThat(selected.getDatasourceId()).isEqualTo("syslog-sink");
        assertThat(selected.getTableName()).isEqualTo("syslog_logs");
        assertThat(selected.getHasData()).isTrue();
    }

    @Test
    void shouldRespectRequestedDatasourceWhenItIsAvailable() {
        LocalDateTime now = LocalDateTime.of(2026, 5, 31, 12, 0, 0);
        DashboardDatasetSelector selector = new DashboardDatasetSelector();

        DashboardDatasetProbeResult syslogDataset = probe(
                candidate("syslog-sink", "syslog_logs"),
                true,
                true,
                300L,
                now.minusMinutes(5)
        );
        DashboardDatasetProbeResult regexDataset = probe(
                candidate("regex-sink", "regex_logs"),
                true,
                true,
                20L,
                now.minusMinutes(10)
        );

        DashboardDatasetContextDTO selected = selector.select(List.of(syslogDataset, regexDataset), "regex-sink", now);

        assertThat(selected).isNotNull();
        assertThat(selected.getDatasourceId()).isEqualTo("regex-sink");
        assertThat(selected.getTableName()).isEqualTo("regex_logs");
    }

    @Test
    void shouldFallbackToDefaultSelectionWhenRequestedDatasourceIsInvalid() {
        LocalDateTime now = LocalDateTime.of(2026, 5, 31, 12, 0, 0);
        DashboardDatasetSelector selector = new DashboardDatasetSelector();

        DashboardDatasetProbeResult syslogDataset = probe(
                candidate("syslog-sink", "syslog_logs"),
                true,
                true,
                300L,
                now.minusMinutes(5)
        );
        DashboardDatasetProbeResult invalidDataset = probe(
                candidate("invalid-sink", "invalid_logs"),
                true,
                false,
                999L,
                now.minusMinutes(1)
        );

        DashboardDatasetContextDTO selected = selector.select(List.of(invalidDataset, syslogDataset), "invalid-sink", now);

        assertThat(selected).isNotNull();
        assertThat(selected.getDatasourceId()).isEqualTo("syslog-sink");
        assertThat(selected.getTableName()).isEqualTo("syslog_logs");
    }

    private DashboardDatasetCandidateDTO candidate(String datasourceId, String tableName) {
        return DashboardDatasetCandidateDTO.builder()
                .source("queryable_sink")
                .datasourceId(datasourceId)
                .datasourceName(datasourceId)
                .databaseName("default")
                .tableName(tableName)
                .componentType("clickhouse")
                .queryable(true)
                .fieldMapping(Map.of(
                        "timestamp", "timestamp",
                        "severity", "severity",
                        "message", "message",
                        "hostname", "hostname",
                        "appname", "appname",
                        "raw", "raw"
                ))
                .build();
    }

    private DashboardDatasetProbeResult probe(
            DashboardDatasetCandidateDTO candidate,
            boolean tableExists,
            boolean hasCoreFields,
            long totalRows,
            LocalDateTime latestLogTime
    ) {
        return DashboardDatasetProbeResult.builder()
                .candidate(candidate)
                .tableExists(tableExists)
                .hasCoreFields(hasCoreFields)
                .totalRows(totalRows)
                .latestLogTime(latestLogTime)
                .build();
    }
}
