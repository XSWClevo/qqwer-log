package cn.mw.loganalysis.dashboard.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Dashboard 排行列表。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DashboardTopListDTO {

    private List<DashboardListItemDTO> items;
}
