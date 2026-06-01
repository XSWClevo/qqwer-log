package cn.mw.loganalysis.dashboard.service;

import cn.mw.loganalysis.dashboard.dto.DashboardWarningDTO;
import cn.mw.loganalysis.dashboard.dto.PlatformHealthDTO;
import cn.mw.loganalysis.vector.entity.VectorMachine;
import cn.mw.loganalysis.vector.entity.VectorPipelineMetric;
import cn.mw.loganalysis.vector.mapper.VectorPipelineMetricMapper;
import cn.mw.loganalysis.vector.service.ComponentStatusService;
import cn.mw.loganalysis.vector.service.ConfigComponentService;
import cn.mw.loganalysis.vector.service.VectorMachineService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;

/**
 * 汇总平台级健康指标。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DashboardPlatformHealthService {

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final VectorMachineService vectorMachineService;
    private final VectorPipelineMetricMapper vectorPipelineMetricMapper;
    private final ConfigComponentService configComponentService;
    private final ComponentStatusService componentStatusService;

    /**
     * 构造首页平台健康指标。
     */
    public PlatformHealthDTO getPlatformHealth(List<DashboardWarningDTO> warnings) {
        List<VectorMachine> machines = vectorMachineService.list();
        int totalHosts = CollectionUtils.size(machines);
        int onlineHosts = (int) machines.stream()
                .filter(machine -> "online".equalsIgnoreCase(machine.getStatus()))
                .count();

        LocalDateTime latestHeartbeat = machines.stream()
                .map(VectorMachine::getLastHeartbeat)
                .filter(java.util.Objects::nonNull)
                .max(Comparator.naturalOrder())
                .orElse(null);

        List<VectorPipelineMetric> metrics = vectorPipelineMetricMapper.selectByMachineAndTimeRange(
                null,
                LocalDateTime.now().minusMinutes(5),
                LocalDateTime.now(),
                500
        );
        long componentErrors = metrics.stream()
                .map(VectorPipelineMetric::getErrors)
                .filter(java.util.Objects::nonNull)
                .mapToLong(Integer::longValue)
                .sum();
        double throughput = metrics.stream()
                .map(VectorPipelineMetric::getEventsOut)
                .filter(java.util.Objects::nonNull)
                .mapToLong(Long::longValue)
                .sum() / 300.0D;

        try {
            machines.stream().findFirst().ifPresent(machine -> componentStatusService.getComponentStatus(machine.getId()));
        } catch (Exception ex) {
            warnings.add(DashboardWarningDTO.builder()
                    .scope("platform")
                    .level("warning")
                    .message("组件状态缓存读取失败，已降级为基础平台指标。")
                    .build());
        }

        return PlatformHealthDTO.builder()
                .onlineVectorHosts(onlineHosts)
                .totalVectorHosts(totalHosts)
                .componentErrorsLast5m(componentErrors)
                .pipelineThroughputLast5m(throughput)
                .queryableDatasetCount(configComponentService.getQueryableClickHouseSinks().size())
                .clickHouseStatus("UP")
                .lastHeartbeatTime(latestHeartbeat == null ? null : latestHeartbeat.format(FORMATTER))
                .build();
    }
}
