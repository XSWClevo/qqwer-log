package cn.mw.loganalysis.attack.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.Map;

@Data
public class CreateAttackDatasetRequest {

    @NotBlank(message = "数据集名称不能为空")
    private String name;

    @NotBlank(message = "数据源类型不能为空")
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
