package cn.mw.loganalysis.dashboard.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 通用排行项。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DashboardListItemDTO {

    private String name;

    private Long count;

    private String meta;
}
