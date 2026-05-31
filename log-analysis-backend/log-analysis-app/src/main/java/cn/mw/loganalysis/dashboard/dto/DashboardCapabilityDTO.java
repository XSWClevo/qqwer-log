package cn.mw.loganalysis.dashboard.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Dashboard 数据集能力定义。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DashboardCapabilityDTO {

    private String key;

    private boolean supported;

    private String reason;

    private String fallbackView;
}
