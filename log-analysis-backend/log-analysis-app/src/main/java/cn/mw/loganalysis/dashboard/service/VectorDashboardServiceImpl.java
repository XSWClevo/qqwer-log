package cn.mw.loganalysis.dashboard.service;

import cn.mw.loganalysis.dashboard.dto.VectorDashboardOverviewDTO;
import cn.mw.loganalysis.vector.dto.MachineMetricsDTO;
import cn.mw.loganalysis.vector.entity.VectorMachine;
import cn.mw.loganalysis.vector.entity.VectorPipelineMetric;
import cn.mw.loganalysis.vector.mapper.VectorPipelineMetricMapper;
import cn.mw.loganalysis.vector.service.MachineMetricsService;
import cn.mw.loganalysis.vector.service.VectorMachineService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 面向 Vector Dashboard 的主机监控聚合服务。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VectorDashboardServiceImpl implements VectorDashboardService {

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter LABEL_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");
    private static final String[] SOURCE_COLORS = {"#0a84ff", "#30d158", "#bf5af2", "#ff9f0a", "#64d2ff"};

    private final VectorMachineService vectorMachineService;
    private final MachineMetricsService machineMetricsService;
    private final VectorPipelineMetricMapper vectorPipelineMetricMapper;

    @Override
    public VectorDashboardOverviewDTO getOverview(String range, String selectedHostId) {
        int minutes = resolveMinutes(range);
        LocalDateTime endTime = LocalDateTime.now();
        LocalDateTime startTime = endTime.minusMinutes(minutes);
        List<String> warnings = new ArrayList<>();

        List<VectorMachine> machines = loadMachines(warnings);
        Map<String, MachineMetricsDTO> machineMetrics = loadMachineMetrics(machines, minutes, warnings);
        List<VectorPipelineMetric> pipelineMetrics = loadPipelineMetrics(startTime, endTime, warnings);
        if (CollectionUtils.isNotEmpty(machines) && CollectionUtils.isEmpty(pipelineMetrics)) {
            warnings.add("未收到 Vector internal_metrics 上报的业务组件指标，事件、吞吐和缓冲区图表已显示为 0 值基线。");
        }
        Map<String, List<VectorPipelineMetric>> pipelineByMachine = pipelineMetrics.stream()
                .filter(metric -> StringUtils.isNotBlank(metric.getMachineId()))
                .collect(Collectors.groupingBy(VectorPipelineMetric::getMachineId));

        List<VectorDashboardOverviewDTO.VectorHostCardDTO> hosts = machines.stream()
                .sorted(Comparator.comparing(machine -> StringUtils.defaultIfBlank(machine.getName(), machine.getHostname()),
                        String.CASE_INSENSITIVE_ORDER))
                .map(machine -> buildHostCard(machine, machineMetrics.get(machine.getId()), pipelineByMachine.get(machine.getId()), minutes, endTime))
                .collect(Collectors.toList());

        VectorDashboardOverviewDTO.VectorHostCardDTO selectedHost = selectHost(hosts, selectedHostId);
        VectorDashboardOverviewDTO.VectorDashboardMetricsDTO metrics = buildMetrics(pipelineMetrics, minutes);
        VectorDashboardOverviewDTO.VectorBufferSummaryDTO buffer = buildBuffer(metrics);

        return VectorDashboardOverviewDTO.builder()
                .generatedAt(endTime.format(FORMATTER))
                .range(StringUtils.defaultIfBlank(range, "1h"))
                .selectedHostId(selectedHost == null ? null : selectedHost.getId())
                .selectedHost(selectedHost)
                .hosts(hosts)
                .metrics(metrics)
                .buffer(buffer)
                .eventsOverTime(buildEventsOverTime(hosts, pipelineByMachine, minutes))
                .dataInSeries(buildAggregateSeries("data-in", "输入数据", "#30d158", pipelineMetrics, minutes, VectorPipelineMetric::getBytesIn))
                .dataOutSeries(buildAggregateSeries("data-out", "输出数据", "#64d2ff", pipelineMetrics, minutes, VectorPipelineMetric::getBytesOut))
                .droppedSeries(buildAggregateSeries("dropped", "丢弃事件", "#ff453a", pipelineMetrics, minutes, metric -> asLong(metric.getErrors())))
                .eventsByType(buildEventTypes(pipelineMetrics))
                .topSources(buildTopSources(pipelineMetrics, minutes))
                .hostSummary(buildHostSummary(hosts, pipelineByMachine, minutes))
                .warnings(warnings)
                .build();
    }

    private List<VectorMachine> loadMachines(List<String> warnings) {
        try {
            return vectorMachineService.list();
        } catch (Exception ex) {
            log.warn("加载 Vector 主机失败", ex);
            warnings.add("Vector 主机列表读取失败，Dashboard 已降级为空主机视图。");
            return List.of();
        }
    }

    private Map<String, MachineMetricsDTO> loadMachineMetrics(List<VectorMachine> machines, int minutes, List<String> warnings) {
        Map<String, MachineMetricsDTO> result = new HashMap<>();
        for (VectorMachine machine : machines) {
            try {
                result.put(machine.getId(), machineMetricsService.getMachineMetrics(machine.getId(), minutes));
            } catch (Exception ex) {
                log.warn("加载机器指标失败, machineId={}", machine.getId(), ex);
                warnings.add(StringUtils.defaultIfBlank(machine.getName(), machine.getHostname()) + " 的机器指标读取失败。");
            }
        }
        return result;
    }

    private List<VectorPipelineMetric> loadPipelineMetrics(LocalDateTime startTime,
                                                           LocalDateTime endTime,
                                                           List<String> warnings) {
        try {
            return vectorPipelineMetricMapper.selectByMachineAndTimeRange(null, startTime, endTime, 2000);
        } catch (Exception ex) {
            log.warn("加载 Vector pipeline 指标失败", ex);
            warnings.add("Vector pipeline 指标读取失败，吞吐与 buffer 数据已降级为 0。");
            return List.of();
        }
    }

    private VectorDashboardOverviewDTO.VectorHostCardDTO buildHostCard(VectorMachine machine,
                                                                       MachineMetricsDTO metrics,
                                                                       List<VectorPipelineMetric> pipelineMetrics,
                                                                       int minutes,
                                                                       LocalDateTime now) {
        MachineMetricsDTO.MetricsPoint latest = metrics == null ? null : metrics.getLatest();
        List<MachineMetricsDTO.MetricsPoint> history = metrics == null || CollectionUtils.isEmpty(metrics.getHistory())
                ? List.of()
                : metrics.getHistory();
        NetworkRate networkRate = calculateNetworkRate(history);
        List<VectorPipelineMetric> hostPipelineMetrics = CollectionUtils.emptyIfNull(pipelineMetrics).stream().toList();
        long eventsOut = sumLong(hostPipelineMetrics, VectorPipelineMetric::getEventsOut);
        long bytesIn = sumLong(hostPipelineMetrics, VectorPipelineMetric::getBytesIn);
        long bytesOut = sumLong(hostPipelineMetrics, VectorPipelineMetric::getBytesOut);
        long errors = hostPipelineMetrics.stream()
                .map(VectorPipelineMetric::getErrors)
                .filter(Objects::nonNull)
                .mapToLong(Integer::longValue)
                .sum();
        long bufferUsed = Math.max(0L, bytesIn - bytesOut);
        long bufferTotal = Math.max(bytesIn, bufferUsed);
        double seconds = Math.max(1D, minutes * 60D);

        return VectorDashboardOverviewDTO.VectorHostCardDTO.builder()
                .id(machine.getId())
                .name(StringUtils.defaultIfBlank(machine.getName(), StringUtils.defaultIfBlank(machine.getHostname(), machine.getId())))
                .hostname(machine.getHostname())
                .ipAddress(StringUtils.defaultIfBlank(machine.getIpAddress(), "-"))
                .environment(resolveEnvironment(machine))
                .status(resolveStatus(machine, pipelineMetrics, now))
                .statusLabel(resolveStatusLabel(machine, pipelineMetrics, now))
                .cpuPercent(round(percent(latest == null ? null : latest.getCpuUsagePercent())))
                .memoryPercent(round(percent(latest == null ? null : latest.getMemoryUsagePercent())))
                .networkInMbps(round(networkRate.inMbps()))
                .networkOutMbps(round(networkRate.outMbps()))
                .eventsPerSecond(round(eventsOut / seconds))
                .dataInBytes(bytesIn)
                .dataOutBytes(bytesOut)
                .droppedEvents(errors)
                .bufferUsedBytes(bufferUsed)
                .bufferTotalBytes(bufferTotal)
                .bufferUsedPercent(round(bufferTotal == 0 ? 0D : bufferUsed * 100D / bufferTotal))
                .uptime(formatUptime(machine.getCreatedAt(), now))
                .vectorVersion(StringUtils.defaultIfBlank(machine.getVectorVersion(), "-"))
                .osType(StringUtils.defaultIfBlank(machine.getOsType(), "-"))
                .cpuSeries(toMetricPoints(history, point -> percent(point.getCpuUsagePercent())))
                .memorySeries(toMetricPoints(history, point -> percent(point.getMemoryUsagePercent())))
                .networkInSeries(toNetworkPoints(history, true))
                .networkOutSeries(toNetworkPoints(history, false))
                .build();
    }

    private VectorDashboardOverviewDTO.VectorHostCardDTO selectHost(List<VectorDashboardOverviewDTO.VectorHostCardDTO> hosts,
                                                                    String selectedHostId) {
        if (CollectionUtils.isEmpty(hosts)) {
            return null;
        }
        if (StringUtils.isNotBlank(selectedHostId)) {
            return hosts.stream()
                    .filter(host -> selectedHostId.equals(host.getId()))
                    .findFirst()
                    .orElse(hosts.get(0));
        }
        return hosts.get(0);
    }

    private VectorDashboardOverviewDTO.VectorDashboardMetricsDTO buildMetrics(List<VectorPipelineMetric> metrics, int minutes) {
        long eventsOut = sumLong(metrics, VectorPipelineMetric::getEventsOut);
        long bytesIn = sumLong(metrics, VectorPipelineMetric::getBytesIn);
        long bytesOut = sumLong(metrics, VectorPipelineMetric::getBytesOut);
        long errors = metrics.stream().map(VectorPipelineMetric::getErrors).filter(Objects::nonNull).mapToLong(Integer::longValue).sum();
        long bufferUsed = Math.max(0L, bytesIn - bytesOut);
        long bufferTotal = Math.max(bytesIn, bufferUsed);
        double seconds = Math.max(1D, minutes * 60D);

        return VectorDashboardOverviewDTO.VectorDashboardMetricsDTO.builder()
                .eventsPerSecond(round(eventsOut / seconds))
                .dataInBytes(bytesIn)
                .dataOutBytes(bytesOut)
                .droppedEvents(errors)
                .bufferUsedBytes(bufferUsed)
                .bufferTotalBytes(bufferTotal)
                .eventsChangePercent(0D)
                .dataInChangePercent(0D)
                .dataOutChangePercent(0D)
                .droppedChangePercent(0D)
                .build();
    }

    private VectorDashboardOverviewDTO.VectorBufferSummaryDTO buildBuffer(VectorDashboardOverviewDTO.VectorDashboardMetricsDTO metrics) {
        long used = asLong(metrics.getBufferUsedBytes());
        long total = Math.max(asLong(metrics.getBufferTotalBytes()), used);
        long available = Math.max(0L, total - used);
        double usedPercent = total == 0 ? 0D : used * 100D / total;
        return VectorDashboardOverviewDTO.VectorBufferSummaryDTO.builder()
                .usedBytes(used)
                .availableBytes(available)
                .totalBytes(total)
                .usedPercent(round(usedPercent))
                .build();
    }

    private List<VectorDashboardOverviewDTO.VectorSeriesDTO> buildEventsOverTime(
            List<VectorDashboardOverviewDTO.VectorHostCardDTO> hosts,
            Map<String, List<VectorPipelineMetric>> pipelineByMachine,
            int minutes) {
        return hosts.stream()
                .limit(4)
                .map(host -> VectorDashboardOverviewDTO.VectorSeriesDTO.builder()
                        .key(host.getId())
                        .name(host.getName())
                        .color(SOURCE_COLORS[Math.floorMod(hosts.indexOf(host), SOURCE_COLORS.length)])
                        .points(toPipelinePoints(pipelineByMachine.get(host.getId()), minutes, VectorPipelineMetric::getEventsOut))
                        .build())
                .collect(Collectors.toList());
    }

    private List<VectorDashboardOverviewDTO.VectorSeriesDTO> buildAggregateSeries(String key,
                                                                                  String name,
                                                                                  String color,
                                                                                  List<VectorPipelineMetric> metrics,
                                                                                  int minutes,
                                                                                  Function<VectorPipelineMetric, Long> extractor) {
        return List.of(VectorDashboardOverviewDTO.VectorSeriesDTO.builder()
                .key(key)
                .name(name)
                .color(color)
                .points(toPipelinePoints(metrics, minutes, extractor))
                .build());
    }

    private List<VectorDashboardOverviewDTO.VectorTopSourceDTO> buildTopSources(List<VectorPipelineMetric> metrics, int minutes) {
        long total = sumLong(metrics, VectorPipelineMetric::getEventsOut);
        double seconds = Math.max(1D, minutes * 60D);
        Map<String, Long> bySource = metrics.stream()
                .collect(Collectors.groupingBy(
                        metric -> StringUtils.defaultIfBlank(metric.getSourceName(), "other"),
                        Collectors.summingLong(metric -> asLong(metric.getEventsOut()))));
        List<Map.Entry<String, Long>> entries = bySource.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(5)
                .toList();
        List<VectorDashboardOverviewDTO.VectorTopSourceDTO> sources = new ArrayList<>();
        for (int i = 0; i < entries.size(); i++) {
            Map.Entry<String, Long> entry = entries.get(i);
            sources.add(VectorDashboardOverviewDTO.VectorTopSourceDTO.builder()
                    .name(entry.getKey())
                    .events(entry.getValue())
                    .eventsPerSecond(round(entry.getValue() / seconds))
                    .percentage(round(total == 0 ? 0D : entry.getValue() * 100D / total))
                    .color(SOURCE_COLORS[Math.floorMod(i, SOURCE_COLORS.length)])
                    .build());
        }
        return sources;
    }

    private List<VectorDashboardOverviewDTO.VectorEventTypeDTO> buildEventTypes(List<VectorPipelineMetric> metrics) {
        long total = sumLong(metrics, VectorPipelineMetric::getEventsOut);
        Map<String, Long> grouped = metrics.stream()
                .collect(Collectors.groupingBy(
                        metric -> classifySource(metric.getSourceName()),
                        Collectors.summingLong(metric -> asLong(metric.getEventsOut()))));
        String[] order = {"log", "metric", "trace", "other"};
        List<VectorDashboardOverviewDTO.VectorEventTypeDTO> types = new ArrayList<>();
        for (int i = 0; i < order.length; i++) {
            String type = order[i];
            long events = grouped.getOrDefault(type, 0L);
            types.add(VectorDashboardOverviewDTO.VectorEventTypeDTO.builder()
                    .type(type)
                    .events(events)
                    .percentage(round(total == 0 ? 0D : events * 100D / total))
                    .color(SOURCE_COLORS[Math.floorMod(i, SOURCE_COLORS.length)])
                    .build());
        }
        return types;
    }

    private List<VectorDashboardOverviewDTO.VectorHostSummaryDTO> buildHostSummary(
            List<VectorDashboardOverviewDTO.VectorHostCardDTO> hosts,
            Map<String, List<VectorPipelineMetric>> pipelineByMachine,
            int minutes) {
        double seconds = Math.max(1D, minutes * 60D);
        return hosts.stream()
                .map(host -> {
                    List<VectorPipelineMetric> metrics = pipelineByMachine.getOrDefault(host.getId(), List.of());
                    long events = sumLong(metrics, VectorPipelineMetric::getEventsOut);
                    return VectorDashboardOverviewDTO.VectorHostSummaryDTO.builder()
                            .host(host.getName())
                            .status(host.getStatus())
                            .eventsPerSecond(round(events / seconds))
                            .dataInBytes(sumLong(metrics, VectorPipelineMetric::getBytesIn))
                            .cpuPercent(host.getCpuPercent())
                            .memoryPercent(host.getMemoryPercent())
                            .build();
                })
                .collect(Collectors.toList());
    }

    private List<VectorDashboardOverviewDTO.VectorPointDTO> toMetricPoints(List<MachineMetricsDTO.MetricsPoint> history,
                                                                           Function<MachineMetricsDTO.MetricsPoint, Double> extractor) {
        if (CollectionUtils.isEmpty(history)) {
            return List.of();
        }
        return history.stream()
                .filter(point -> point.getTimestamp() != null)
                .sorted(Comparator.comparing(MachineMetricsDTO.MetricsPoint::getTimestamp))
                .map(point -> VectorDashboardOverviewDTO.VectorPointDTO.builder()
                        .timestamp(point.getTimestamp().format(FORMATTER))
                        .label(point.getTimestamp().format(LABEL_FORMATTER))
                        .value(round(extractor.apply(point)))
                        .build())
                .collect(Collectors.toList());
    }

    private List<VectorDashboardOverviewDTO.VectorPointDTO> toNetworkPoints(List<MachineMetricsDTO.MetricsPoint> history,
                                                                            boolean inbound) {
        if (CollectionUtils.size(history) < 2) {
            return List.of();
        }
        List<VectorDashboardOverviewDTO.VectorPointDTO> points = new ArrayList<>();
        MachineMetricsDTO.MetricsPoint previous = null;
        for (MachineMetricsDTO.MetricsPoint current : history.stream()
                .filter(point -> point.getTimestamp() != null)
                .sorted(Comparator.comparing(MachineMetricsDTO.MetricsPoint::getTimestamp))
                .toList()) {
            if (previous != null) {
                double seconds = Math.max(1D, Duration.between(previous.getTimestamp(), current.getTimestamp()).toSeconds());
                long diff = Math.max(0L, networkBytes(current, inbound) - networkBytes(previous, inbound));
                points.add(VectorDashboardOverviewDTO.VectorPointDTO.builder()
                        .timestamp(current.getTimestamp().format(FORMATTER))
                        .label(current.getTimestamp().format(LABEL_FORMATTER))
                        .value(round(diff * 8D / seconds / 1_000_000D))
                        .build());
            }
            previous = current;
        }
        return points;
    }

    private List<VectorDashboardOverviewDTO.VectorPointDTO> toPipelinePoints(List<VectorPipelineMetric> metrics,
                                                                             int minutes,
                                                                             Function<VectorPipelineMetric, Long> extractor) {
        if (CollectionUtils.isEmpty(metrics)) {
            metrics = List.of();
        }
        int bucketCount = minutes <= 60 ? 12 : 24;
        LocalDateTime end = LocalDateTime.now();
        LocalDateTime start = end.minusMinutes(minutes);
        long bucketSeconds = Math.max(60L, Duration.between(start, end).getSeconds() / bucketCount);
        Map<Integer, Long> buckets = new TreeMap<>();
        for (VectorPipelineMetric metric : metrics) {
            if (metric.getRecordedAt() == null) {
                continue;
            }
            long elapsed = Math.max(0L, Duration.between(start, metric.getRecordedAt()).getSeconds());
            int bucket = (int) Math.min(bucketCount - 1, elapsed / bucketSeconds);
            buckets.merge(bucket, asLong(extractor.apply(metric)), Long::sum);
        }
        List<VectorDashboardOverviewDTO.VectorPointDTO> points = new ArrayList<>();
        for (int i = 0; i < bucketCount; i++) {
            LocalDateTime bucketTime = start.plusSeconds(bucketSeconds * i);
            points.add(VectorDashboardOverviewDTO.VectorPointDTO.builder()
                    .timestamp(bucketTime.format(FORMATTER))
                    .label(bucketTime.format(LABEL_FORMATTER))
                    .value(round(buckets.getOrDefault(i, 0L).doubleValue()))
                    .build());
        }
        return points;
    }

    private NetworkRate calculateNetworkRate(List<MachineMetricsDTO.MetricsPoint> history) {
        if (CollectionUtils.size(history) < 2) {
            return new NetworkRate(0D, 0D);
        }
        List<MachineMetricsDTO.MetricsPoint> sorted = history.stream()
                .filter(point -> point.getTimestamp() != null)
                .sorted(Comparator.comparing(MachineMetricsDTO.MetricsPoint::getTimestamp))
                .toList();
        if (sorted.size() < 2) {
            return new NetworkRate(0D, 0D);
        }
        MachineMetricsDTO.MetricsPoint first = sorted.get(0);
        MachineMetricsDTO.MetricsPoint last = sorted.get(sorted.size() - 1);
        double seconds = Math.max(1D, Duration.between(first.getTimestamp(), last.getTimestamp()).toSeconds());
        double in = Math.max(0L, networkBytes(last, true) - networkBytes(first, true)) * 8D / seconds / 1_000_000D;
        double out = Math.max(0L, networkBytes(last, false) - networkBytes(first, false)) * 8D / seconds / 1_000_000D;
        return new NetworkRate(in, out);
    }

    private long networkBytes(MachineMetricsDTO.MetricsPoint point, boolean inbound) {
        if (point == null || CollectionUtils.isEmpty(point.getNetworkInterfaces())) {
            return 0L;
        }
        return point.getNetworkInterfaces().stream()
                .map(inbound ? MachineMetricsDTO.NetworkInterfaceInfo::getBytesRecv : MachineMetricsDTO.NetworkInterfaceInfo::getBytesSent)
                .filter(Objects::nonNull)
                .mapToLong(Long::longValue)
                .sum();
    }

    private String resolveStatus(VectorMachine machine, List<VectorPipelineMetric> metrics, LocalDateTime now) {
        if (!"online".equalsIgnoreCase(machine.getStatus())) {
            return "critical";
        }
        if (machine.getLastHeartbeat() != null && Duration.between(machine.getLastHeartbeat(), now).toMinutes() > 5) {
            return "warning";
        }
        long errors = metrics == null ? 0L : metrics.stream()
                .map(VectorPipelineMetric::getErrors)
                .filter(Objects::nonNull)
                .mapToLong(Integer::longValue)
                .sum();
        return errors > 0 ? "warning" : "healthy";
    }

    private String resolveStatusLabel(VectorMachine machine, List<VectorPipelineMetric> metrics, LocalDateTime now) {
        return switch (resolveStatus(machine, metrics, now)) {
            case "healthy" -> "健康";
            case "warning" -> "告警";
            default -> "严重";
        };
    }

    private String resolveEnvironment(VectorMachine machine) {
        String createdBy = StringUtils.defaultString(machine.getCreatedBy()).toLowerCase(Locale.ROOT);
        if (createdBy.contains("stage")) {
            return "staging";
        }
        if (createdBy.contains("dev")) {
            return "dev";
        }
        return "prod";
    }

    private String formatUptime(LocalDateTime createdAt, LocalDateTime now) {
        if (createdAt == null) {
            return "-";
        }
        Duration duration = Duration.between(createdAt, now);
        long days = Math.max(0L, duration.toDays());
        long hours = Math.max(0L, duration.minusDays(days).toHours());
        return days > 0 ? days + "d " + hours + "h" : hours + "h";
    }

    private String classifySource(String sourceName) {
        String value = StringUtils.defaultString(sourceName).toLowerCase(Locale.ROOT);
        if (value.contains("metric")) {
            return "metric";
        }
        if (value.contains("trace")) {
            return "trace";
        }
        if (value.contains("log") || value.contains("file") || value.contains("kafka") || value.contains("http")) {
            return "log";
        }
        return "other";
    }

    private int resolveMinutes(String range) {
        return switch (StringUtils.defaultIfBlank(range, "1h")) {
            case "6h" -> 360;
            case "24h" -> 1440;
            case "7d" -> 10080;
            default -> 60;
        };
    }

    private double percent(Double value) {
        if (value == null || value.isNaN() || value.isInfinite()) {
            return 0D;
        }
        return Math.max(0D, Math.min(100D, value));
    }

    private long sumLong(List<VectorPipelineMetric> metrics, Function<VectorPipelineMetric, Long> extractor) {
        if (CollectionUtils.isEmpty(metrics)) {
            return 0L;
        }
        return metrics.stream().map(extractor).filter(Objects::nonNull).mapToLong(Long::longValue).sum();
    }

    private long asLong(Long value) {
        return value == null ? 0L : value;
    }

    private long asLong(Integer value) {
        return value == null ? 0L : value.longValue();
    }

    private double round(double value) {
        return Math.round(value * 100D) / 100D;
    }

    private record NetworkRate(double inMbps, double outMbps) {
    }
}
