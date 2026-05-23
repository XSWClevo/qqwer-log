package cn.mw.loganalysis.attack.dto;

import lombok.Data;

import java.util.Map;

@Data
public class UpdateAttackDatasetRequest {

    private String name;

    private String datasourceType;

    private String datasourceId;

    private String databaseName;

    private String tableName;

    private String indexName;

    private Map<String, String> fieldMapping;

    private Map<String, Object> capabilities;

    private Boolean enabled;

    private Integer batchSize;
}
