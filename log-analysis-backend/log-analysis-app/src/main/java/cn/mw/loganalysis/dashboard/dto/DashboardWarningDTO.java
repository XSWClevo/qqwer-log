package cn.mw.loganalysis.dashboard.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Dashboard 局部告警/降级提示。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DashboardWarningDTO {

    /**
     * 告警作用域，例如 dataset/platform/query。
     */
    private String scope;

    /**
     * 告警级别：info/warning/error。
     */
    private String level;

    /**
     * 面向前端展示的提示文案。
     */
    private String message;
}
