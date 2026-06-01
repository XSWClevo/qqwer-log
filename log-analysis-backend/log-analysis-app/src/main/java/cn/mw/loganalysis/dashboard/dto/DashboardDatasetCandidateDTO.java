package cn.mw.loganalysis.dashboard.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * Dashboard 可选日志数据集候选。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DashboardDatasetCandidateDTO {

    private String source;

    private String datasourceId;

    private String datasourceName;

    private String databaseName;

    private String tableName;

    private String componentType;

    private Boolean queryable;

    /**
     * 逻辑字段到真实列名的映射。
     * 例如 timestamp -> event_time。
     */
    private Map<String, String> fieldMapping;
}
