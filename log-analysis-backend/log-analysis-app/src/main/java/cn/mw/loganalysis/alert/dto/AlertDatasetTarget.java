package cn.mw.loganalysis.alert.dto;

import lombok.Builder;
import lombok.Data;

import java.util.Map;

/**
 * 告警查询目标
 */
@Data
@Builder
public class AlertDatasetTarget {

    private String datasourceId;

    private String datasourceType;

    private String databaseName;

    private String tableName;

    private String timeField;

    private String messageField;

    private String rawField;

    private String severityField;

    private Map<String, String> fieldMapping;
}
