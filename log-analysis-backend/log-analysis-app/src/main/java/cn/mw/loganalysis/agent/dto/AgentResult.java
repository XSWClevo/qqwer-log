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
     * 结果类型：schema / logs / timeseries / text2sql / vector_component_requirements / vector_component_plan / vector_component_commit
     */
    private String type;

    /**
     * 通用成功状态。主要用于确认创建类结果。
     */
    private Boolean success;

    /**
     * 通用错误信息。主要用于确认创建类结果。
     */
    private String error;

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
     * Vector 组件预览计划 ID。
     */
    private String planId;

    /**
     * 日志样本。
     */
    private String logSample;

    /**
     * 目标 ClickHouse 数据源 ID。
     */
    private String datasourceId;

    /**
     * 目标 ClickHouse 数据源名称。
     */
    private String datasourceName;

    /**
     * 计划创建的 ClickHouse 表名。
     */
    private String tableName;

    /**
     * 命名捕获正则。
     */
    private String regexPattern;

    /**
     * 可复用 VRL 脚本。
     */
    private String vrlScript;

    /**
     * Vector 组件预览字段。
     */
    private List<Map<String, Object>> fields;

    /**
     * 生成的 ClickHouse DDL。
     */
    private String ddl;

    /**
     * 预览或确认阶段的警告信息。
     */
    private List<String> warnings;

    /**
     * 日志来源类型：file / syslog / socket / kafka。
     */
    private String sourceType;

    /**
     * 日志来源配置，例如 include、syslog_address、bootstrap_servers。
     */
    private Map<String, Object> sourceConfig;

    /**
     * 创建后的 Source 组件 ID。
     */
    private String sourceComponentId;

    /**
     * 创建后的 Remap Transform 组件 ID。
     */
    private String remapComponentId;

    /**
     * 创建后的 ClickHouse Sink 组件 ID。
     */
    private String sinkComponentId;

    /**
     * 创建后的可部署 Vector 可视化配置 ID。
     */
    private String visualConfigId;

    /**
     * 创建后的可部署 Vector 可视化配置名称。
     */
    private String visualConfigName;

    /**
     * 部署提示/部署前置条件摘要。
     */
    private Map<String, Object> deployment;

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
