package cn.mw.loganalysis.dashboard.service;

import cn.mw.loganalysis.dashboard.dto.DashboardCapabilityDTO;
import cn.mw.loganalysis.dashboard.dto.DashboardDatasetCandidateDTO;
import cn.mw.loganalysis.dashboard.dto.DashboardDatasetContextDTO;
import cn.mw.loganalysis.dashboard.dto.DashboardLogKpisDTO;
import cn.mw.loganalysis.dashboard.dto.DashboardMetricDrilldownDTO;
import cn.mw.loganalysis.dashboard.dto.DashboardOverviewDTO;
import cn.mw.loganalysis.dashboard.dto.DashboardQueryRequest;
import cn.mw.loganalysis.dashboard.dto.DashboardStorageVolumeDTO;
import cn.mw.loganalysis.dashboard.dto.PlatformHealthDTO;
import cn.mw.loganalysis.dashboard.service.DashboardDatasetProbeResult;
import cn.mw.loganalysis.dashboard.mapper.DashboardMapper;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DashboardServiceOverviewAssemblyTest {

    @Test
    void shouldPassThroughSnapshotCapabilitiesDrilldownsAndStorageVolume() {
        DashboardMapper dashboardMapper = mock(DashboardMapper.class);
        DashboardDatasetDiscoveryService datasetDiscoveryService = mock(DashboardDatasetDiscoveryService.class);
        DashboardDatasetProbeService datasetProbeService = mock(DashboardDatasetProbeService.class);
        DashboardDatasetSelector datasetSelector = mock(DashboardDatasetSelector.class);
        DashboardPlatformHealthService platformHealthService = mock(DashboardPlatformHealthService.class);
        DashboardLogDatasetService logDatasetService = mock(DashboardLogDatasetService.class);
        DashboardService dashboardService = new DashboardService(
                dashboardMapper,
                datasetDiscoveryService,
                datasetProbeService,
                datasetSelector,
                platformHealthService,
                logDatasetService
        );

        DashboardQueryRequest request = DashboardQueryRequest.builder()
                .startTime("2026-06-01 00:00:00")
                .endTime("2026-06-01 23:59:59")
                .granularity("auto")
                .datasourceId("sink-1")
                .build();
        DashboardDatasetCandidateDTO candidate = DashboardDatasetCandidateDTO.builder()
                .datasourceId("sink-1")
                .datasourceName("syslog_logs")
                .databaseName("default")
                .tableName("syslog_logs")
                .source("vector")
                .build();
        DashboardDatasetProbeResult probe = DashboardDatasetProbeResult.builder()
                .candidate(candidate)
                .tableExists(true)
                .hasCoreFields(true)
                .totalRows(10L)
                .latestLogTime(LocalDateTime.of(2026, 6, 1, 12, 0, 0))
                .build();
        DashboardDatasetContextDTO datasetContext = DashboardDatasetContextDTO.builder()
                .datasourceId("sink-1")
                .datasourceName("syslog_logs")
                .databaseName("default")
                .tableName("syslog_logs")
                .source("vector")
                .hasData(true)
                .build();
        DashboardStorageVolumeDTO storageVolume = DashboardStorageVolumeDTO.builder()
                .value(842L)
                .unit("MB")
                .displayValue("842 MB")
                .build();
        DashboardCapabilityDTO capability = DashboardCapabilityDTO.builder()
                .key("host_topn")
                .supported(true)
                .reason("hostname field is available")
                .fallbackView("message_rank")
                .build();
        DashboardMetricDrilldownDTO metricDrilldown = DashboardMetricDrilldownDTO.builder()
                .metricKey("errorCount")
                .title("Error Trend")
                .description("Shows error count changes over time")
                .unit("count")
                .build();
        DashboardLogDatasetSnapshot snapshot = DashboardLogDatasetSnapshot.builder()
                .logKpis(DashboardLogKpisDTO.builder().storageVolume(storageVolume).build())
                .capabilities(List.of(capability))
                .metricDrilldowns(List.of(metricDrilldown))
                .build();
        PlatformHealthDTO platformHealth = PlatformHealthDTO.builder()
                .clickHouseStatus("UP")
                .build();

        when(datasetDiscoveryService.discoverCandidates()).thenReturn(List.of(candidate));
        when(datasetProbeService.probeCandidates(List.of(candidate))).thenReturn(List.of(probe));
        when(datasetSelector.toContexts(eq(List.of(probe)), any(LocalDateTime.class))).thenReturn(List.of(datasetContext));
        when(datasetSelector.select(eq(List.of(probe)), eq("sink-1"), any(LocalDateTime.class))).thenReturn(datasetContext);
        when(platformHealthService.getPlatformHealth(any())).thenReturn(platformHealth);
        when(logDatasetService.buildSnapshot(eq(datasetContext), eq(request), any())).thenReturn(snapshot);

        DashboardOverviewDTO overview = dashboardService.getOverview(request);

        assertThat(overview.getCapabilities()).containsExactly(capability);
        assertThat(overview.getMetricDrilldowns()).containsExactly(metricDrilldown);
        assertThat(overview.getLogKpis()).isNotNull();
        assertThat(overview.getLogKpis().getStorageVolume()).isEqualTo(storageVolume);
    }
}
