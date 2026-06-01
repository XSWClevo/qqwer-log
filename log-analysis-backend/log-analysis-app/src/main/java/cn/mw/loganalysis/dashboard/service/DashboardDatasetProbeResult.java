package cn.mw.loganalysis.dashboard.service;

import cn.mw.loganalysis.dashboard.dto.DashboardDatasetCandidateDTO;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 数据集探测结果。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DashboardDatasetProbeResult {

    private DashboardDatasetCandidateDTO candidate;

    private boolean tableExists;

    private boolean hasCoreFields;

    private long totalRows;

    private LocalDateTime latestLogTime;
}
