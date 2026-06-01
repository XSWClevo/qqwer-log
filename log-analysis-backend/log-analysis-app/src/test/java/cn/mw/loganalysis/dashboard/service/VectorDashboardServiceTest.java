package cn.mw.loganalysis.dashboard.service;

import cn.mw.loganalysis.dashboard.dto.VectorDashboardOverviewDTO;
import cn.mw.loganalysis.vector.dto.MachineMetricsDTO;
import cn.mw.loganalysis.vector.entity.VectorMachine;
import cn.mw.loganalysis.vector.entity.VectorPipelineMetric;
import cn.mw.loganalysis.vector.mapper.VectorPipelineMetricMapper;
import cn.mw.loganalysis.vector.service.MachineMetricsService;
import cn.mw.loganalysis.vector.service.VectorMachineService;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class VectorDashboardServiceTest {

    @Test
    void shouldBuildVectorHostOverviewFromMachinesMetricsAndPipelineSamples() {
        VectorMachineService machineService = mock(VectorMachineService.class);
        MachineMetricsService metricsService = mock(MachineMetricsService.class);
        VectorPipelineMetricMapper pipelineMetricMapper = mock(VectorPipelineMetricMapper.class);
        VectorDashboardService service = new VectorDashboardServiceImpl(machineService, metricsService, pipelineMetricMapper);

        VectorMachine host = new VectorMachine();
        host.setId("host-1");
        host.setName("vector-01");
        host.setHostname("vector-01");
        host.setIpAddress("10.0.1.10");
        host.setStatus("online");
        host.setVectorVersion("0.36.0");
        host.setOsType("Ubuntu 22.04");
        host.setCreatedAt(LocalDateTime.now().minusDays(12).minusHours(3));
        host.setLastHeartbeat(LocalDateTime.now().minusSeconds(20));

        MachineMetricsDTO metrics = new MachineMetricsDTO();
        MachineMetricsDTO.MetricsPoint first = metricsPoint(20D, 40D, 1_000_000L, 2_000_000L, LocalDateTime.now().minusMinutes(20));
        MachineMetricsDTO.MetricsPoint latest = metricsPoint(24D, 45D, 5_000_000L, 8_000_000L, LocalDateTime.now().minusMinutes(1));
        metrics.setLatest(latest);
        metrics.setHistory(List.of(first, latest));

        VectorPipelineMetric sample = new VectorPipelineMetric();
        sample.setMachineId("host-1");
        sample.setSourceName("kafka-input");
        sample.setEventsIn(15_000L);
        sample.setEventsOut(13_560L);
        sample.setBytesIn(8_400_000L);
        sample.setBytesOut(7_900_000L);
        sample.setErrors(12);
        sample.setLatencyMs(BigDecimal.valueOf(14.5D));
        sample.setRecordedAt(LocalDateTime.now().minusMinutes(1));

        when(machineService.list()).thenReturn(List.of(host));
        when(metricsService.getMachineMetrics(eq("host-1"), anyInt())).thenReturn(metrics);
        when(pipelineMetricMapper.selectByMachineAndTimeRange(isNull(), any(LocalDateTime.class), any(LocalDateTime.class), anyInt()))
                .thenReturn(List.of(sample));

        VectorDashboardOverviewDTO overview = service.getOverview("1h", null);

        assertThat(overview.getHosts()).hasSize(1);
        VectorDashboardOverviewDTO.VectorHostCardDTO hostCard = overview.getHosts().get(0);
        assertThat(hostCard.getName()).isEqualTo("vector-01");
        assertThat(hostCard.getCpuPercent()).isEqualTo(24D);
        assertThat(hostCard.getMemoryPercent()).isEqualTo(45D);
        assertThat(hostCard.getEventsPerSecond()).isGreaterThan(0D);
        assertThat(hostCard.getDataInBytes()).isEqualTo(8_400_000L);
        assertThat(hostCard.getDataOutBytes()).isEqualTo(7_900_000L);
        assertThat(hostCard.getDroppedEvents()).isEqualTo(12L);
        assertThat(hostCard.getBufferUsedBytes()).isEqualTo(500_000L);
        assertThat(overview.getMetrics().getEventsPerSecond()).isGreaterThan(0D);
        assertThat(overview.getMetrics().getDataInBytes()).isEqualTo(8_400_000L);
        assertThat(overview.getMetrics().getDroppedEvents()).isEqualTo(12L);
        assertThat(overview.getTopSources().get(0).getName()).isEqualTo("kafka-input");
        assertThat(overview.getHostSummary().get(0).getHost()).isEqualTo("vector-01");
        assertThat(overview.getSelectedHost().getId()).isEqualTo("host-1");
    }

    @Test
    void shouldReturnTimeBucketsWhenInternalMetricsHaveNoBusinessSamplesYet() {
        VectorMachineService machineService = mock(VectorMachineService.class);
        MachineMetricsService metricsService = mock(MachineMetricsService.class);
        VectorPipelineMetricMapper pipelineMetricMapper = mock(VectorPipelineMetricMapper.class);
        VectorDashboardService service = new VectorDashboardServiceImpl(machineService, metricsService, pipelineMetricMapper);

        VectorMachine host = new VectorMachine();
        host.setId("host-1");
        host.setName("vector-01");
        host.setHostname("vector-01");
        host.setIpAddress("10.0.1.10");
        host.setStatus("online");
        host.setCreatedAt(LocalDateTime.now().minusHours(3));
        host.setLastHeartbeat(LocalDateTime.now().minusSeconds(20));

        MachineMetricsDTO metrics = new MachineMetricsDTO();
        metrics.setLatest(metricsPoint(31D, 56D, 4_000_000L, 5_000_000L, LocalDateTime.now().minusMinutes(1)));
        metrics.setHistory(List.of(
                metricsPoint(29D, 52D, 1_000_000L, 2_000_000L, LocalDateTime.now().minusMinutes(12)),
                metricsPoint(31D, 56D, 4_000_000L, 5_000_000L, LocalDateTime.now().minusMinutes(1))
        ));

        when(machineService.list()).thenReturn(List.of(host));
        when(metricsService.getMachineMetrics(eq("host-1"), anyInt())).thenReturn(metrics);
        when(pipelineMetricMapper.selectByMachineAndTimeRange(isNull(), any(LocalDateTime.class), any(LocalDateTime.class), anyInt()))
                .thenReturn(List.of());

        VectorDashboardOverviewDTO overview = service.getOverview("1h", null);

        assertThat(overview.getHosts()).hasSize(1);
        assertThat(overview.getEventsOverTime()).hasSize(1);
        assertThat(overview.getEventsOverTime().get(0).getPoints()).hasSize(12);
        assertThat(overview.getDataInSeries().get(0).getPoints()).hasSize(12);
        assertThat(overview.getDataOutSeries().get(0).getPoints()).hasSize(12);
        assertThat(overview.getDroppedSeries().get(0).getPoints()).hasSize(12);
        assertThat(overview.getDataInSeries().get(0).getPoints()).allSatisfy(point -> assertThat(point.getValue()).isZero());
        assertThat(overview.getWarnings()).anyMatch(warning -> warning.contains("internal_metrics"));
    }

    private MachineMetricsDTO.MetricsPoint metricsPoint(Double cpu,
                                                        Double memory,
                                                        long bytesSent,
                                                        long bytesRecv,
                                                        LocalDateTime timestamp) {
        MachineMetricsDTO.MetricsPoint point = new MachineMetricsDTO.MetricsPoint();
        point.setTimestamp(timestamp);
        point.setCpuUsagePercent(cpu);
        point.setMemoryUsagePercent(memory);
        MachineMetricsDTO.NetworkInterfaceInfo network = new MachineMetricsDTO.NetworkInterfaceInfo();
        network.setName("eth0");
        network.setBytesSent(bytesSent);
        network.setBytesRecv(bytesRecv);
        point.setNetworkInterfaces(List.of(network));
        return point;
    }
}
