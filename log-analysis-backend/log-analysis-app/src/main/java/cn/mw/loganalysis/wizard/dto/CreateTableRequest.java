package cn.mw.loganalysis.wizard.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.List;

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
     * 正则表达式（parse_regex 时用于组件库可视化回显）
     */
    private String regexPattern;

    /**
     * Grok 模式（parse_grok 时用于组件库可视化回显）
     */
    private String grokPattern;

    /**
     * 日志样本（用于组件库再次测试解析）
     */
    private String logSample;

    /**
     * 已识别字段（用于组件库解析预览回显）
     */
    private List<VisualParsedField> parsedFields;

    /**
     * 是否自动创建组件
     */
    private Boolean autoCreateComponents = true;

    @Data
    public static class VisualParsedField {
        private String name;
        private String newName;
        private Boolean deleted;
        private String type;
        private Object value;
        private String comment;
    }
}
