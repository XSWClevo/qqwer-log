package cn.mw.loganalysis.dashboard.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Dashboard 日志体积信息。value + unit 为展示口径原始值，displayValue 为格式化后的展示字符串。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DashboardStorageVolumeDTO {

    private Long value;

    private String unit;

    private String displayValue;
}
