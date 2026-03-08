package cn.mw.loganalysis.agent.dto;

import cn.mw.loganalysis.stats.service.query.FieldInfo;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * 智能助手结果载荷
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentResult {

    /**
     * 结果类型：schema / logs / timeseries / text2sql
     */
    private String type;

    /**
     * 时间范围说明
     */
    private String timeRangeLabel;

    /**
     * 字段结构
     */
    private List<FieldInfo> schema;

    /**
     * 日志列表
     */
    private List<Map<String, Object>> logs;

    /**
     * 日志总数
     */
    private Long total;

    /**
     * 页码
     */
    private Integer pageNum;

    /**
     * 页大小
     */
    private Integer pageSize;

    /**
     * 时序粒度
     */
    private String granularity;

    /**
     * 时序序列
     */
    private List<Map<String, Object>> series;

    /**
     * 摘要数据
     */
    private Map<String, Object> summary;

    /**
     * text2sql 生成的 SQL。
     * 只有 type=text2sql 时才会返回。
     */
    private String sql;

    /**
     * 通用 SQL 查询结果类型：metric / category / timeseries / list。
     * 只有 type=text2sql 时才会返回。
     */
    private String queryResultType;

    /**
     * text2sql 的原始结果对象，直接给前端图表卡片使用。
     * 可能是单值、单对象或对象数组。
     */
    private Object rawResult;

    /**
     * 把原始结果归一化为行列表，方便前端统一用表格展示。
     */
    private List<Map<String, Object>> rows;

    /**
     * SQL 生成耗时（秒）。
     */
    private Double sqlGenerationTime;

    /**
     * SQL 执行耗时（秒）。
     */
    private Double sqlExecutionTime;

    /**
     * 总耗时（秒）。
     */
    private Double totalExecutionTime;
}
