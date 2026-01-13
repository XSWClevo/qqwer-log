package cn.mw.loganalysis.wizard.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

/**
 * 生成 DDL 请求
 */
@Data
public class GenerateDDLRequest {

    /**
     * 数据源 ID
     */
    @NotBlank(message = "数据源ID不能为空")
    private String datasourceId;

    /**
     * 表名
     */
    @NotBlank(message = "表名不能为空")
    private String tableName;

    /**
     * 字段列表
     */
    @NotEmpty(message = "字段列表不能为空")
    private List<FieldDefinition> fields;

    @Data
    public static class FieldDefinition {
        /**
         * 字段名
         */
        private String name;

        /**
         * 字段类型
         */
        private String type;

        /**
         * 是否可空
         */
        private Boolean nullable;

        /**
         * 注释
         */
        private String comment;
    }
}
