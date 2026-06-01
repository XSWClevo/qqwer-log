package cn.mw.loganalysis.dashboard.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 最近高风险日志项。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DashboardRecentLogItemDTO {

    private String id;

    private String timestamp;

    private String severity;

    private String hostname;

    private String appname;

    private String message;

    private String raw;
}
