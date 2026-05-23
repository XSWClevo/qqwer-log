package cn.mw.loganalysis.attack.dto;

import lombok.Data;

@Data
public class AttackClassificationQueryRequest {

    private String startTime;

    private String endTime;

    private String datasourceType;

    private String datasourceId;

    private String databaseName;

    private String tableName;

    private String indexName;

    private String attackType;

    private String attackSubType;

    private String severity;

    private String sourceIp;

    private String hostname;

    private String keyword;

    private Integer pageNum = 1;

    private Integer pageSize = 20;
}
