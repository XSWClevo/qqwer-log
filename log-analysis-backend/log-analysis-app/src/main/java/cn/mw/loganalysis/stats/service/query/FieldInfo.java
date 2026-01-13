package cn.mw.loganalysis.stats.service.query;

import lombok.Builder;
import lombok.Data;

/**
 * 数据源字段信息
 */
@Data
@Builder
public class FieldInfo {

    /**
     * 字段名
     */
    private String name;

    /**
     * 字段类型（如 String, DateTime, Int64 等）
     */
    private String type;

    /**
     * 字段显示名称（用于前端展示）
     */
    private String label;

    /**
     * 是否为时间字段
     */
    private Boolean isTimestamp;

    /**
     * 是否适合做统计维度（通常是字符串类型的字段）
     */
    private Boolean isStatsDimension;

    /**
     * 是否为主要内容字段（如 message, raw 等）
     */
    private Boolean isContentField;
}
