package cn.mw.loganalysis.dashboard.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * Dashboard 当前使用的数据集上下文。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DashboardDatasetContextDTO {

    private String datasourceId;

    private String datasourceName;

    private String databaseName;

    private String tableName;

    private String source;

    private String status;

    private Long totalRows;

    private String latestLogTime;

    private Boolean hasData;

    /**
     * 当前数据集的字段映射，供服务层继续拼接查询。
     */
    private Map<String, String> fieldMapping;
}
