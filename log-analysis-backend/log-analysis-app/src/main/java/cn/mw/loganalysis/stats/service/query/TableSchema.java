package cn.mw.loganalysis.stats.service.query;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * 表结构定义
 */
@Data
@Builder
public class TableSchema {

    /**
     * 表名
     */
    private String tableName;

    /**
     * 字段列表
     */
    private List<ColumnDefinition> columns;

    /**
     * 引擎（ClickHouse 特有）
     */
    private String engine;

    /**
     * 分区键（ClickHouse 特有）
     */
    private String partitionBy;

    /**
     * 排序键（ClickHouse 特有）
     */
    private String orderBy;

    /**
     * 主键
     */
    private String primaryKey;

    /**
     * TTL（数据过期时间）
     */
    private String ttl;

    /**
     * 列定义
     */
    @Data
    @Builder
    public static class ColumnDefinition {
        /**
         * 列名
         */
        private String name;

        /**
         * 数据类型
         */
        private String type;

        /**
         * 是否可为空
         */
        private Boolean nullable;

        /**
         * 默认值
         */
        private String defaultValue;

        /**
         * 注释
         */
        private String comment;
    }
}
