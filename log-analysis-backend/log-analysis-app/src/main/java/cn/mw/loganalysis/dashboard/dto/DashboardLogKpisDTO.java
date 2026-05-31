package cn.mw.loganalysis.dashboard.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 当前日志数据集 KPI。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DashboardLogKpisDTO {

    private Long totalLogs;

    private Double currentEps;

    private Long errorCount;

    private Long criticalCount;

    private Double errorRate;

    private Long activeHostCount;

    private Long activeAppCount;

    private DashboardStorageVolumeDTO storageVolume;
}
