package cn.mw.loganalysis.dashboard.service;

import cn.mw.loganalysis.dashboard.dto.DashboardDatasetContextDTO;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;

/**
 * 为 Dashboard 选择默认数据集。
 */
@Slf4j
@Component
public class DashboardDatasetSelector {

    private static final Duration FRESH_DATA_THRESHOLD = Duration.ofHours(24);
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /**
     * 在探测结果中选择默认数据集。
     */
    public DashboardDatasetContextDTO selectDefault(List<DashboardDatasetProbeResult> probes, LocalDateTime now) {
        if (CollectionUtils.isEmpty(probes)) {
            return null;
        }

        return validProbes(probes).stream()
                .sorted(buildComparator(now))
                .findFirst()
                .map(this::toContext)
                .orElse(null);
    }

    /**
     * 用户显式指定数据集时优先命中，否则按默认策略回退。
     */
    public DashboardDatasetContextDTO select(List<DashboardDatasetProbeResult> probes,
                                             String datasourceId,
                                             LocalDateTime now) {
        if (CollectionUtils.isEmpty(probes)) {
            return null;
        }
        if (StringUtils.isNotBlank(datasourceId)) {
            DashboardDatasetContextDTO matched = validProbes(probes).stream()
                    .filter(probe -> StringUtils.equals(datasourceId, probe.getCandidate().getDatasourceId()))
                    .findFirst()
                    .map(this::toContext)
                    .orElse(null);
            if (matched != null) {
                return matched;
            }
        }
        return selectDefault(probes, now);
    }

    /**
     * 将可用探测结果转换为可切换数据集列表。
     */
    public List<DashboardDatasetContextDTO> toContexts(List<DashboardDatasetProbeResult> probes, LocalDateTime now) {
        if (CollectionUtils.isEmpty(probes)) {
            return List.of();
        }
        return validProbes(probes).stream()
                .sorted(buildComparator(now))
                .map(this::toContext)
                .toList();
    }

    private List<DashboardDatasetProbeResult> validProbes(List<DashboardDatasetProbeResult> probes) {
        return probes.stream()
                .filter(DashboardDatasetProbeResult::isTableExists)
                .filter(DashboardDatasetProbeResult::isHasCoreFields)
                .toList();
    }

    private Comparator<DashboardDatasetProbeResult> buildComparator(LocalDateTime now) {
        return Comparator
                .comparing((DashboardDatasetProbeResult probe) -> isFresh(probe.getLatestLogTime(), now)).reversed()
                .thenComparing(DashboardDatasetProbeResult::getTotalRows, Comparator.reverseOrder())
                .thenComparing(probe -> ObjectUtils.defaultIfNull(probe.getCandidate().getDatasourceId(), ""));
    }

    private boolean isFresh(LocalDateTime latestLogTime, LocalDateTime now) {
        if (latestLogTime == null || now == null) {
            return false;
        }
        return !latestLogTime.isBefore(now.minus(FRESH_DATA_THRESHOLD));
    }

    private DashboardDatasetContextDTO toContext(DashboardDatasetProbeResult probe) {
        String status = probe.getTotalRows() > 0 ? "READY" : "NO_DATA";
        return DashboardDatasetContextDTO.builder()
                .datasourceId(probe.getCandidate().getDatasourceId())
                .datasourceName(probe.getCandidate().getDatasourceName())
                .databaseName(probe.getCandidate().getDatabaseName())
                .tableName(probe.getCandidate().getTableName())
                .source(probe.getCandidate().getSource())
                .status(status)
                .totalRows(probe.getTotalRows())
                .latestLogTime(probe.getLatestLogTime() == null ? null : probe.getLatestLogTime().format(FORMATTER))
                .hasData(probe.getTotalRows() > 0)
                .fieldMapping(probe.getCandidate().getFieldMapping())
                .build();
    }
}
