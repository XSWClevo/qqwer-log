package cn.mw.loganalysis.attack.mapper.param;

import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AttackClassificationQuerySqlParam {

    private LocalDateTime startTime;

    private LocalDateTime endTime;

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

    private int pageSize;
}
