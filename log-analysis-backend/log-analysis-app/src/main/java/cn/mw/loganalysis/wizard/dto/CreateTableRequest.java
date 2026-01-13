package cn.mw.loganalysis.wizard.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 创建表请求
 */
@Data
public class CreateTableRequest {

    /**
     * 数据源 ID
     */
    @NotBlank(message = "数据源ID不能为空")
    private String datasourceId;

    /**
     * DDL 语句
     */
    @NotBlank(message = "DDL不能为空")
    private String ddl;

    /**
     * 表名
     */
    private String tableName;

    /**
     * VRL 脚本（用于创建 remap transform 组件）
     */
    private String vrlScript;

    /**
     * 解析方法（parse_json, parse_syslog, parse_kv, parse_regex, custom）
     */
    private String parseMethod;

    /**
     * 是否自动创建组件
     */
    private Boolean autoCreateComponents = true;
}
