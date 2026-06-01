package cn.mw.loganalysis.dashboard.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 最近高风险日志面板。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DashboardRecentLogsDTO {

    private List<DashboardRecentLogItemDTO> items;
}
