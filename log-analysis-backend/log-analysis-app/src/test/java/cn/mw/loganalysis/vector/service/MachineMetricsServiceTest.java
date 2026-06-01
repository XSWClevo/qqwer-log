package cn.mw.loganalysis.vector.service;

import cn.mw.loganalysis.vector.dto.AgentMetricsRequest;
import cn.mw.loganalysis.vector.entity.VectorPipelineMetric;
import cn.mw.loganalysis.vector.mapper.MachineMetricsMapper;
import cn.mw.loganalysis.vector.mapper.VectorPipelineMetricMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class MachineMetricsServiceTest {

    @Test
    void shouldPersistPipelineMetricsFromAgentComponentMetrics() {
        MachineMetricsMapper machineMetricsMapper = mock(MachineMetricsMapper.class);
        VectorPipelineMetricMapper pipelineMetricMapper = mock(VectorPipelineMetricMapper.class);
        MachineMetricsService service = new MachineMetricsService(machineMetricsMapper, pipelineMetricMapper, new ObjectMapper());

        AgentMetricsRequest request = new AgentMetricsRequest();
        request.setCollectedAt(LocalDateTime.of(2026, 6, 1, 10, 15));

        AgentMetricsRequest.ComponentMetrics sourceMetrics = new AgentMetricsRequest.ComponentMetrics();
        sourceMetrics.setEventsProcessed(12_500L);
        sourceMetrics.setBytesProcessed(4_096_000L);
        sourceMetrics.setErrors(7L);
        sourceMetrics.setStatus("warning");
        request.setComponentMetrics(Map.of("syslog_source", sourceMetrics));

        service.recordMetrics("machine-1", request);

        ArgumentCaptor<VectorPipelineMetric> captor = ArgumentCaptor.forClass(VectorPipelineMetric.class);
        verify(pipelineMetricMapper).insert(captor.capture());
        verify(machineMetricsMapper).insert(any());

        VectorPipelineMetric metric = captor.getValue();
        assertThat(metric.getMachineId()).isEqualTo("machine-1");
        assertThat(metric.getSourceName()).isEqualTo("syslog_source");
        assertThat(metric.getEventsIn()).isEqualTo(12_500L);
        assertThat(metric.getEventsOut()).isEqualTo(12_500L);
        assertThat(metric.getBytesIn()).isEqualTo(4_096_000L);
        assertThat(metric.getBytesOut()).isEqualTo(4_096_000L);
        assertThat(metric.getErrors()).isEqualTo(7);
        assertThat(metric.getRecordedAt()).isEqualTo(request.getCollectedAt());
    }

    @Test
    void shouldSkipInternalMetricsHelperComponentsWhenPersistingPipelineMetrics() {
        MachineMetricsMapper machineMetricsMapper = mock(MachineMetricsMapper.class);
        VectorPipelineMetricMapper pipelineMetricMapper = mock(VectorPipelineMetricMapper.class);
        MachineMetricsService service = new MachineMetricsService(machineMetricsMapper, pipelineMetricMapper, new ObjectMapper());

        AgentMetricsRequest request = new AgentMetricsRequest();
        AgentMetricsRequest.ComponentMetrics internalSink = new AgentMetricsRequest.ComponentMetrics();
        internalSink.setEventsProcessed(99L);
        internalSink.setBytesProcessed(2048L);
        request.setComponentMetrics(Map.of("_vector_internal_metrics_file", internalSink));

        service.recordMetrics("machine-1", request);

        verify(pipelineMetricMapper, never()).insert(any());
        verify(machineMetricsMapper).insert(any());
    }
}
