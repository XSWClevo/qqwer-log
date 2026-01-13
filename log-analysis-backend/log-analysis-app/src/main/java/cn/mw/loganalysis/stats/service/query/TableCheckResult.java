package cn.mw.loganalysis.stats.service.query;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * 表检查结果
 */
@Data
@Builder
public class TableCheckResult {

    /**
     * 表是否存在
     */
    private boolean exists;

    /**
     * 消息
     */
    private String message;

    /**
     * 表名
     */
    private String tableName;

    /**
     * 字段列表（如果表存在）
     */
    private List<FieldInfo> fields;

    /**
     * 行数（如果表存在）
     */
    private Long rowCount;
}
